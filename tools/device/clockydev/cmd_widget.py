"""widget: integrated state of Clocky widgets (dumpsys appwidget + prefs + optional UI location)."""
import json
import os
import time

from . import constants as C
from . import devstate, ui, widgets as W
from .cli import Out, command
from .errors import adb_err, check_failed, ui_err, usage
from .session import next_step_dir


def read_prefs(adb):
    """Return (prefs dict, status) where status is 'ok' | 'unavailable: reason'."""
    r = adb.shell("run-as %s cat %s" % (C.PACKAGE, C.PREFS_FILE), timeout=15)
    text = r.text
    low = (text + r.err_text).lower()
    if "not debuggable" in low or "unknown package" in low:
        return {}, "unavailable: " + (text + r.err_text).strip()[:100], ""
    if "no such file" in low:
        return {}, "ok", ""
    if r.rc != 0 and not text.strip():
        return {}, "unavailable: rc=%d %s" % (r.rc, r.err_text.strip()[:100]), ""
    try:
        return W.parse_prefs(text), "ok", text
    except Exception as e:  # malformed XML
        return {}, "unavailable: prefs XML unreadable (%s)" % e, text


def collect_widget_state(dev, locate=False, want_id=None, density=None):
    """Full parsed state dict (shared with `collect`)."""
    adb = dev.adb
    raw_appwidget = adb.shell("dumpsys appwidget", timeout=30).text
    parsed = W.parse_appwidget(raw_appwidget)
    provs, ws = W.clocky_view(parsed)
    prefs, prefs_status, prefs_raw = read_prefs(adb)
    prefs_ok = prefs_status == "ok"
    hosts, host_note = [], None
    if locate:
        xml = ui.dump_xml(adb)
        nodes, screen = ui.parse_hierarchy(xml)
        dens = density or devstate.parse_wm(adb.shell("wm density", timeout=10).text).get("physical")
        hosts = W.locate_hosts(ui.visible(nodes, screen), dens)
        if not hosts:
            host_note = ("no Clocky host view on the current screen (not on this launcher page, or the launcher is "
                         "in resize mode: press back/tap empty space first)")
    out_w = []
    for w in ws:
        if want_id is not None and w["id"] != want_id:
            continue
        opts = w.get("options")
        minh = opts.get("appWidgetMinHeight") if isinstance(opts, dict) else None
        e = {"id": w["id"], "kind": w["kind"], "host": w["host"],
             "options": opts if opts is not None else {"unavailable": "dumpsys appwidget has no options line"},
             "size_class": W.size_class(minh), "size_source": "options" if minh is not None else None,
             "size_rule": W.SIZE_RULE}
        if prefs_ok:
            ent = prefs.get(w["id"])
            e["settings"] = W.summarize_settings(ent) if ent else None
        else:
            e["settings"] = {"unavailable": prefs_status}
        out_w.append(e)
    # a single placed widget + a single located host view is an unambiguous pairing
    if locate and len(ws) == 1 and len(hosts) == 1 and out_w and out_w[0]["size_class"] == "unknown":
        inner = hosts[0].get("inner_size_dp")
        if inner:
            out_w[0]["size_class"] = W.size_class(inner[1])
            out_w[0]["size_source"] = "estimated from launcher host-view bounds (%.0fx%.0f dp)" % tuple(inner)
    checks = W.build_checks(ws, prefs, prefs_ok)
    return {"providers": provs, "widgets": out_w, "hosts_on_screen": hosts, "host_note": host_note,
            "prefs_status": prefs_status, "orphan_settings": sorted(set(prefs) - {w["id"] for w in ws}) if prefs_ok else [],
            "checks": checks}, raw_appwidget, prefs_raw


def _setup(p):
    p.add_argument("--id", type=int, help="only this appWidgetId")
    p.add_argument("--locate", action="store_true", help="find Clocky host views on screen (UI dump)")
    p.add_argument("--raw", action="store_true", help="include raw dumpsys block in JSON")
    p.add_argument("--ticking", action="store_true", help="prove the on-screen widget clock advances (exit 7 if not)")
    p.add_argument("--timeout", type=float, default=75.0, help="--ticking: max seconds to wait for a change (default 75)")
    p.add_argument("--index", type=int, help="--ticking: which host view when several are on screen (0-based, top-left order)")
    p.add_argument("--no-process", action="store_true", dest="no_process",
                   help="--ticking: also require that no Clocky process exists in any sample")


@command("widget", _setup)
def cmd_widget(ctx, args):
    """Clocky widget state: ids, host, options, size class, settings, consistency checks."""
    if args.ticking:
        if args.locate or args.raw or args.id is not None:
            raise usage("TICKING_EXCLUSIVE", "--ticking cannot be combined with --locate/--raw/--id")
        return ticking(ctx, args)
    if args.no_process or args.index is not None:
        raise usage("NEEDS_TICKING", "--no-process/--index only apply to --ticking")
    dev = ctx.device()
    state, raw, _prefs = collect_widget_state(dev, args.locate, args.id)
    if args.raw:
        state["raw_dumpsys_appwidget"] = raw
    lines = []
    for w in state["widgets"]:
        s = w["settings"] or {}
        sch = s.get("schema") if isinstance(s, dict) else None
        lines.append("id=%d host=%s %s %s settings=%s" % (
            w["id"], w["host"], w["kind"], w["size_class"] +
            ("" if w["size_source"] in (None, "options") else " (est.)"),
            "none" if w["settings"] is None else ("schema %s" % sch if sch is not None else "present")))
        if isinstance(w["options"], dict) and "unavailable" in w["options"]:
            lines.append("   options: %s" % w["options"]["unavailable"])
        elif isinstance(w["options"], dict):
            lines.append("   options: %s" % w["options"])
    if not state["widgets"]:
        lines.append("no Clocky widgets placed")
    for h in state["hosts_on_screen"]:
        lines.append("host view %s bounds=%s size=%sdp inner=%sdp center=%s" % (
            h["label"], h["bounds"], h["size_dp"], h["inner_size_dp"], h["center"]))
    if state["host_note"]:
        lines.append(state["host_note"])
    lines.append("prefs: %s%s" % (state["prefs_status"],
                                  "; orphan ids %s" % state["orphan_settings"] if state["orphan_settings"] else ""))
    lines.append("checks: none" if not state["checks"] else "checks:")
    for c in state["checks"]:
        lines.append("  CHECK %s id=%s %s" % (c["check"], c.get("id"), c.get("note", "")))
    return Out(state, lines, dev.warnings)


# ------------------------------------------------------------------ ticking

TICK_INTERVAL = 5.0


def _sample(adb, index):
    """One launcher dump -> (xml, entry dict for the chosen host). Raises on no host / ambiguity / no time text."""
    xml = ui.dump_xml(adb)
    nodes, screen = ui.parse_hierarchy(xml)
    entries = W.find_time_nodes(ui.visible(nodes, screen))
    return xml, entries


def pick_entry(entries, index):
    if not entries:
        raise ui_err("NO_HOST_ON_SCREEN", "no Clocky widget host view on the current screen", "Run `cdev key home`.")
    if index is None and len(entries) > 1:
        raise usage("MULTIPLE_HOSTS", "%d Clocky host views on screen" % len(entries), "Pass --index N.",
                    [{"index": i, "bounds": list(e["host"].bounds)} for i, e in enumerate(entries)])
    i = index or 0
    if i >= len(entries):
        raise usage("BAD_INDEX", "--index %d out of range (%d host views)" % (i, len(entries)))
    e = entries[i]
    if e["time"] is None:
        raise ui_err("NO_TIME_TEXT", "host view has no H:MM clock text", "Is the widget showing a time? Check `cdev inspect`.")
    return e


def run_ticking(read, pids_fn, timeout, no_process, sleep=time.sleep, clock=time.monotonic, interval=TICK_INTERVAL):
    """Core loop. read() -> (xml, entry); pids_fn() -> [pid]. Returns (result dict, xml_before, xml_after)."""
    t0 = clock()
    xml0, e0 = read()
    first = e0["text"]
    samples = [{"t": 0.0, "text": first, "pids": list(pids_fn())}]
    xml_after, last = xml0, first
    while True:
        el = clock() - t0
        if last != first or el >= timeout:
            break
        sleep(interval)
        xml_after, e = read()
        last = e["text"]
        samples.append({"t": round(clock() - t0, 1), "text": last, "pids": list(pids_fn())})
    appeared = next((s for s in samples if s["pids"]), None)
    changed = last != first
    absent = appeared is None
    passed = changed and (absent or not no_process)
    return {"before": first, "after": last, "elapsed": round(clock() - t0, 1), "samples": samples,
            "process_absent_throughout": absent, "no_process_required": no_process, "passed": passed,
            "time_node_rule": "largest-area Clocky TextView under the host whose label matches H:MM[:SS]"}, xml0, xml_after


def ticking(ctx, args):
    from .cmd_misc import clocky_pids
    dev = ctx.device()
    adb = dev.adb

    def read():
        xml, entries = _sample(adb, args.index)
        return xml, pick_entry(entries, args.index)
    res, xml0, xml1 = run_ticking(read, lambda: clocky_pids(adb)[0], args.timeout, args.no_process)
    d = next_step_dir(ctx, "widget-ticking")
    arts = []
    for name, data in (("ui_before.xml", xml0), ("ui_after.xml", xml1), ("ticking.json", json.dumps(res, indent=2, ensure_ascii=True))):
        with open(os.path.join(d, name), "w", encoding="utf-8") as f:
            f.write(data)
        arts.append(ctx.rel(os.path.join(d, name)))
    try:
        with open(os.path.join(d, "screen_after.png"), "wb") as f:
            f.write(ui.screenshot(adb))
        arts.append(ctx.rel(os.path.join(d, "screen_after.png")))
    except Exception:
        pass
    n = len(res["samples"])
    absent_n = sum(1 for s in res["samples"] if not s["pids"])
    proc = "clocky process absent in %d/%d samples" % (absent_n, n)
    if not res["passed"]:
        if res["before"] == res["after"]:
            msg = "NOT TICKING: %s unchanged for %.0fs (%s)" % (res["before"], res["elapsed"], proc)
        else:
            first = next(s for s in res["samples"] if s["pids"])
            msg = "ticking ok (%s -> %s) but a Clocky process appeared at t=%.1fs pid %s" % (
                res["before"], res["after"], first["t"], first["pids"])
        raise check_failed("NOT_TICKING", msg, "Evidence: " + ctx.rel(d), [])
    return Out(res, ["TICKING ok: %s -> %s after %.1fs; %s" % (res["before"], res["after"], res["elapsed"], proc)],
               dev.warnings, arts)
