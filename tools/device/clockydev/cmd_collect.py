"""collect: one-shot evidence bundle (tolerates partial failure)."""
import json
import os
import subprocess

from . import constants as C
from . import devstate, evidence, ui
from .cli import Out, command
from .cmd_misc import LOG_BUFFERS, gather_logs, write_log_files
from .cmd_session import device_epoch
from .cmd_widget import collect_widget_state
from .errors import CdevError
from .session import load_state, next_step_dir


def git_info(root):
    def g(*a):
        try:
            return subprocess.run(["git", *a], cwd=root, capture_output=True, timeout=15).stdout.decode("utf-8", "replace").strip()
        except Exception:
            return None
    head = g("rev-parse", "HEAD")
    return {"head": head, "branch": g("rev-parse", "--abbrev-ref", "HEAD"),
            "dirty": bool(g("status", "--porcelain"))} if head else None


def _setup(p):
    p.add_argument("label")
    p.add_argument("--no-launcher-dump", action="store_true")
    p.add_argument("--since", default="prepare")


@command("collect", _setup)
def cmd_collect(ctx, args):
    """Save screenshot, UI dump, widget state, dumpsys, logs, meta.json and summary.md."""
    dev = ctx.device()
    adb = dev.adb
    state = load_state(ctx.build_dir)
    d = next_step_dir(ctx, "collect-" + args.label)
    errors, written = [], []

    def put(name, data, binary=False):
        p = os.path.join(d, name)
        with open(p, "wb" if binary else "w", **({} if binary else {"encoding": "utf-8"})) as f:
            f.write(data)
        written.append(name)

    def part(name, fn):
        try:
            return fn()
        except CdevError as e:
            errors.append({"part": name, "error": "%s: %s" % (e.code, e.message)})
        except Exception as e:  # keep collecting what we can
            errors.append({"part": name, "error": "%s: %s" % (type(e).__name__, e)})
        return None

    status = part("status", lambda: devstate.snapshot(adb))
    if status:
        put("status.json", json.dumps(status, indent=2, ensure_ascii=True))

    def screen():
        png = ui.screenshot(adb)
        put("screen.png", png, True)
        small = ui.save_small(os.path.join(d, "screen.png"))
        if small:
            written.append(os.path.basename(small))
        else:
            errors.append({"part": "screen_small", "error": "Pillow not installed (optional)"})
    part("screenshot", screen)

    def uidump():
        xml = ui.dump_xml(adb)
        nodes, _s = ui.parse_hierarchy(xml)
        put("ui.xml", xml)
        put("ui.txt", ui.render_text(nodes))
    part("ui_dump", uidump)

    wstate = {"widgets": [], "checks": [], "host_note": None}

    def widget():
        nonlocal wstate
        ws, raw, prefs_raw = collect_widget_state(dev, locate=True, density=(status or {}).get("density"))
        wstate = ws
        put("widget.json", json.dumps(ws, indent=2, ensure_ascii=True))
        put("dumpsys_appwidget.txt", raw)
        put("clocky_widget_settings.xml", prefs_raw or "")
    part("widget", widget)

    part("dumpsys_window", lambda: put("dumpsys_window.txt", adb.shell("dumpsys window", timeout=30).text))
    part("dumpsys_activity", lambda: put("dumpsys_activity_top.txt", adb.shell("dumpsys activity activities", timeout=30).text))

    launcher_pkg = ((status or {}).get("launcher") or "").split("/")[0] or None
    launcher = {"package": launcher_pkg}
    if launcher_pkg:
        pk = part("launcher_version", lambda: devstate.parse_package(adb.shell("dumpsys package " + launcher_pkg, timeout=30).text))
        if pk:
            launcher.update({"versionName": pk.get("versionName"), "versionCode": pk.get("versionCode")})
        if not args.no_launcher_dump:
            part("launcher_dumpsys", lambda: put("launcher_dumpsys.txt",
                                                 adb.shell("dumpsys activity " + launcher_pkg, timeout=40).text))

    log_lines = ["(logs unavailable)"]

    def logs_part():
        nonlocal log_lines
        g = gather_logs(dev, state, args.since, list(LOG_BUFFERS))
        for n in write_log_files(d, g):
            if n not in written:
                written.append(n)
        put("logs_summary.txt", "\n".join(g["lines"]) + "\n")
        log_lines = g["lines"]
    part("logs", logs_part)

    epoch = part("device_epoch", lambda: device_epoch(adb))
    meta = evidence.build_meta(args.label, status, state, (state.get("session") or {}).get("id"), epoch,
                               git_info(ctx.root), launcher, errors, dev.identity)
    put("meta.json", json.dumps(meta, indent=2, ensure_ascii=True))
    put("summary.md", evidence.build_summary_md(meta, wstate, log_lines, sorted(set(written + ["meta.json", "summary.md"]))))
    files = [ctx.rel(os.path.join(d, n)) for n in sorted(set(written))]
    lines = ["collected %d file(s) into %s" % (len(files), ctx.rel(d))]
    if errors:
        lines += ["partial: %s - %s" % (e["part"], e["error"]) for e in errors]
    lines.append("PR-safe summary: %s (attach ONLY this file; others may contain personal data)" % ctx.rel(os.path.join(d, "summary.md")))
    return Out({"dir": ctx.rel(d), "errors": errors, "files": files}, lines,
               dev.warnings + (["%d part(s) failed; see meta.json errors[]" % len(errors)] if errors else []), files)
