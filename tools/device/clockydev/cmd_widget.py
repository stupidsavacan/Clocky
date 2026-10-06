"""widget: integrated state of Clocky widgets (dumpsys appwidget + prefs + optional UI location)."""
from . import constants as C
from . import devstate, ui, widgets as W
from .cli import Out, command
from .errors import adb_err


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


@command("widget", _setup)
def cmd_widget(ctx, args):
    """Clocky widget state: ids, host, options, size class, settings, consistency checks."""
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
