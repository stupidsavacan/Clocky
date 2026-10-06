"""logs, install, launch, adb passthrough."""
import hashlib
import os
import time

from . import constants as C
from . import devstate, logs as L
from .adb import check_raw
from .cli import Out, command
from .cmd_session import device_epoch
from .errors import CdevError, adb_err, usage
from .session import load_state, next_step_dir

LOG_BUFFERS = ("main", "system", "events", "crash")


def clocky_uid(adb):
    p = devstate.parse_package(adb.shell("dumpsys package " + C.PACKAGE, timeout=30).text)
    return (p or {}).get("uid")


def fetch_buffers(adb, buffers):
    """-> ({buffer: entries}, {buffer: raw_text}, notes). Falls back to `-v epoch` where `uid` is unsupported."""
    ents, raws, notes = {}, {}, []
    for b in buffers:
        r = adb.shell("logcat -d -v epoch,uid -b %s" % b, timeout=90)
        text = r.text
        if r.rc != 0 or "Unknown" in text[:200] or "Invalid" in text[:200]:
            r = adb.shell("logcat -d -v epoch -b %s" % b, timeout=90)
            text = r.text
            notes.append("buffer %s: -v uid unsupported, uid filter disabled" % b)
        raws[b] = text
        ents[b] = L.parse_log(text)
    return ents, raws, notes


def gather_logs(dev, state, since_spec, buffers):
    """Shared by `logs` and `collect`. Returns dict with items/summary/lines/raws."""
    adb = dev.adb
    now = device_epoch(adb)
    try:
        since = L.resolve_since(since_spec, state.get("marks") or {}, now)
    except ValueError as e:
        raise usage("BAD_SINCE", str(e))
    ents, raws, notes = fetch_buffers(adb, buffers)
    uid = clocky_uid(adb)
    items = L.filter_clocky(ents, uid, since)
    gaps = [g for g in (L.gap_warning(b, ents[b], since) for b in buffers if since) if g]
    summary, lines = L.summarize(items, gaps, since, now)
    return {"items": items, "summary": summary, "lines": lines + ["WARNING " + n for n in notes],
            "raws": raws, "uid": uid, "since": since, "now": now, "notes": notes}


def write_log_files(d, g, raw=False):
    files = {}
    with open(os.path.join(d, "summary.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(g["lines"]) + "\n")
    files["summary.txt"] = True
    with open(os.path.join(d, "logcat_clocky.txt"), "w", encoding="utf-8") as f:
        for b, e, c in g["items"]:
            f.write(L.format_entry(b, e, c) + "\n")
    crash = [(b, e, c) for b, e, c in g["items"] if c in ("CRASH", "ANR")]
    with open(os.path.join(d, "logcat_crash.txt"), "w", encoding="utf-8") as f:
        for b, e, c in crash:
            f.write(L.format_entry(b, e, c) + "\n")
    if raw:
        for b, text in g["raws"].items():
            with open(os.path.join(d, "logcat_%s.txt" % b), "w", encoding="utf-8") as f:
                f.write(text)
    return sorted(os.listdir(d))


def _logs_setup(p):
    p.add_argument("--since", default="prepare", help="prepare | mark:NAME | <N>s | all-buffer (default prepare)")
    p.add_argument("--raw", action="store_true", help="also save full per-buffer logcat text locally")
    p.add_argument("--buffers", default=",".join(LOG_BUFFERS))


@command("logs", _logs_setup)
def cmd_logs(ctx, args):
    """Clocky-relevant log lines (crash/ANR/process/activity/cancel) since a mark."""
    dev = ctx.device()
    state = load_state(ctx.build_dir)
    bufs = [b for b in args.buffers.split(",") if b]
    for b in bufs:
        if b not in LOG_BUFFERS:
            raise usage("BAD_BUFFER", "unknown buffer %r" % b, "Use any of: %s" % ", ".join(LOG_BUFFERS))
    g = gather_logs(dev, state, args.since, bufs)
    d = next_step_dir(ctx, "logs")
    write_log_files(d, g, args.raw)
    arts = [ctx.rel(os.path.join(d, n)) for n in sorted(os.listdir(d))]
    return Out({"summary": g["summary"], "since_epoch": g["since"], "clocky_uid": g["uid"]},
               g["lines"] + ["saved " + ctx.rel(d)], dev.warnings, arts)


# ------------------------------------------------------------------ install

def _install_setup(p):
    p.add_argument("apk", nargs="?", default=C.DEFAULT_APK)


@command("install", _install_setup)
def cmd_install(ctx, args):
    """Overwrite-install a debug APK (adb install -r). Never uninstalls."""
    apk = args.apk if os.path.isabs(args.apk) else os.path.join(ctx.root, args.apk)
    if not os.path.isfile(apk):
        raise usage("APK_NOT_FOUND", "APK not found: %s" % args.apk,
                    "Build it first (./gradlew :app:assembleDebug) or pass the APK path.")
    dev = ctx.device()
    h = hashlib.sha256()
    with open(apk, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    r = dev.adb.run(["install", "-r", apk], timeout=300)
    out = (r.text + r.err_text).strip()
    if r.rc != 0 or "Success" not in out:
        hint = "Do NOT uninstall (it deletes widgets and settings). Ask the user how to proceed."
        if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in out or "SIGNATURE" in out:
            hint = "Signature mismatch with the installed build. Uninstalling would delete widgets/settings: ask the user."
        elif "VERSION_DOWNGRADE" in out:
            hint = "Installed versionCode is newer. cdev never downgrades; ask the user."
        raise adb_err("INSTALL_FAILED", "install failed: %s" % out[:300], hint)
    pkg = devstate.parse_package(dev.adb.shell("dumpsys package " + C.PACKAGE, timeout=30).text) or {}
    state = load_state(ctx.build_dir)
    info = {"sha256": h.hexdigest(), "apk": ctx.rel(apk), "versionCode": pkg.get("versionCode"),
            "versionName": pkg.get("versionName"), "lastUpdateTime": pkg.get("lastUpdateTime"),
            "time": time.strftime("%Y-%m-%dT%H:%M:%S")}
    state["install"] = info
    from .session import save_state
    save_state(ctx.build_dir, state)
    return Out(info, ["installed %s" % info["apk"], "sha256 %s" % info["sha256"],
                      "versionCode=%s versionName=%s updated %s" % (info["versionCode"], info["versionName"],
                                                                    info["lastUpdateTime"])], dev.warnings)


# ------------------------------------------------------------------- launch

def _launch_setup(p):
    p.add_argument("what", choices=["app", "config"])
    p.add_argument("--widget-id", type=int)


@command("launch", _launch_setup)
def cmd_launch(ctx, args):
    """Start Clocky (app) or its widget config Activity for an existing widget id."""
    dev = ctx.device()
    if args.what == "app":
        cmd = "am start -n %s/%s" % (C.PACKAGE, C.LAUNCH_ACTIVITY)
    else:
        if args.widget_id is None:
            raise usage("NO_WIDGET_ID", "launch config needs --widget-id N", "Find ids with `cdev widget`.")
        cmd = "am start -a android.appwidget.action.APPWIDGET_CONFIGURE -n %s/%s --ei appWidgetId %d" % (
            C.PACKAGE, C.CONFIG_ACTIVITY, args.widget_id)
    r = dev.adb.shell(cmd, timeout=20)
    txt = (r.text + r.err_text).strip()
    if r.rc != 0 or "Error" in txt:
        raise adb_err("LAUNCH_FAILED", "am start failed: %s" % txt[:200])
    time.sleep(1.0)
    focus = devstate.parse_window(dev.adb.shell("dumpsys window", timeout=30).text).get("focus")
    return Out({"focus": focus}, ["started %s" % args.what, "focus %s" % focus], dev.warnings)


# ---------------------------------------------------------------------- adb

@command("adb")
def cmd_adb(ctx, args):
    """Raw adb passthrough with serial + safety guard: cdev adb -- <adb args...>"""
    tail = args.raw_tail
    if tail is None:
        raise usage("NO_DASHDASH", "usage: cdev adb -- <adb args...>", "Example: cdev adb -- shell getprop ro.build.version.sdk")
    check_raw(tail)
    dev = ctx.device()
    r = dev.adb.run(tail, timeout=120)
    lines = [l for l in (r.text + r.err_text).splitlines()]
    return Out({"rc": r.rc, "stdout": r.text, "stderr": r.err_text}, lines, dev.warnings,
               exit_code=0 if r.rc == 0 else C.EXIT_ADB)
