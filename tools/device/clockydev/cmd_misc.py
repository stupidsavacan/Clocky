"""logs, install, launch, adb passthrough."""
import hashlib
import os
import re
import time

from . import constants as C
from . import devstate, logs as L
from .adb import check_raw
from .cli import Out, command
from .cmd_session import device_epoch
from .errors import CdevError, adb_err, refused, usage
from .session import load_state, next_step_dir, save_state

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
    p.add_argument("--source-commit", help="exact commit that produced this APK (required under broker)")


@command("install", _install_setup)
def cmd_install(ctx, args):
    """Overwrite-install a debug APK (adb install -r). Never uninstalls."""
    apk = args.apk if os.path.isabs(args.apk) else os.path.join(ctx.root, args.apk)
    if not os.path.isfile(apk):
        raise usage("APK_NOT_FOUND", "APK not found: %s" % args.apk,
                    "Build it first (./gradlew :app:assembleDebug) or pass the APK path.")
    dev = ctx.device()
    source_commit = getattr(args, "source_commit", None)
    if getattr(ctx, "broker", None) and (not source_commit or not re.fullmatch(r"[0-9a-fA-F]{40}", source_commit)):
        raise usage("SOURCE_COMMIT_REQUIRED", "Broker install requires --source-commit with the full tested source commit")
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
    if source_commit:
        info["source_commit"] = source_commit
    if getattr(ctx, "broker", None):
        from .verification import installed_apk
        actual = installed_apk(dev.adb, ctx.broker.status(dev.identity).get("installed"))
        if actual["sha256"] != info["sha256"]:
            raise adb_err("INSTALLED_APK_MISMATCH", "Installed APK differs from the supplied APK; recovery required")
        actual["source_commit"] = source_commit
        ctx.broker.update(dev.identity, ctx.env["CLOCKY_LEASE_SESSION"], ctx.env["CLOCKY_LEASE_TOKEN"], installed=actual)
    from .session import save_state
    save_state(ctx.build_dir, state)
    return Out(info, ["installed %s" % info["apk"], "sha256 %s" % info["sha256"],
                      "versionCode=%s versionName=%s updated %s" % (info["versionCode"], info["versionName"],
                                                                    info["lastUpdateTime"])], dev.warnings)


# ------------------------------------------------------------------- launch

_CLASS_RE = re.compile(r"^\.?[A-Za-z_]\w*(\.[A-Za-z_]\w*)*$")
_KEY_RE = re.compile(r"^[A-Za-z0-9_.]{1,64}$")
_ES_RE = re.compile(r"^[A-Za-z0-9_.:-]{0,128}$")
_INT_RE = re.compile(r"^-?\d{1,10}$")
APK_PACKAGES = (C.PACKAGE, "com.android.deskclock")


def activity_component(cls):
    """Validate a class name for `launch activity` -> fully qualified class inside the Clocky APK."""
    if "/" in cls or ":" in cls:
        raise refused("OTHER_PACKAGE", "launch activity only starts classes inside the Clocky package, got %r" % cls,
                      "Pass .rel.Class or a fully qualified com.stupidsavacan.clocky.* / com.android.deskclock.* name.")
    if not _CLASS_RE.match(cls):
        raise usage("BAD_CLASS", "invalid class name %r" % cls)
    if cls.startswith("."):
        return C.PACKAGE + cls
    if not any(cls.startswith(p + ".") for p in APK_PACKAGES):
        raise refused("OTHER_PACKAGE", "%r is not inside the Clocky APK" % cls,
                      "Use .rel.Class or a com.stupidsavacan.clocky.* / com.android.deskclock.* name.")
    return cls


def build_extras(ei, es, ez):
    """Validated `am start` extras (values only ever contain shell-safe characters)."""
    out = []
    for flag, items in (("--ei", ei), ("--es", es), ("--ez", ez)):
        for k, v in items or []:
            if not _KEY_RE.match(k):
                raise usage("BAD_EXTRA_KEY", "extra key %r must match %s" % (k, _KEY_RE.pattern))
            if flag == "--ei" and not _INT_RE.match(v):
                raise usage("BAD_EXTRA_VALUE", "--ei %s needs an integer, got %r" % (k, v))
            if flag == "--ez" and v not in ("true", "false"):
                raise usage("BAD_EXTRA_VALUE", "--ez %s needs true|false, got %r" % (k, v))
            if flag == "--es" and not _ES_RE.match(v):
                raise usage("BAD_EXTRA_VALUE", "--es %s value %r must match %s" % (k, v, _ES_RE.pattern))
            out.append("%s %s %s" % (flag, k, v if v != "" else "''"))
    return " ".join(out)


def wait_for_focus(adb, needle, wait, interval=0.5):
    """Poll window focus until it contains `needle` or `wait` seconds pass. -> (focus, matched, waited)."""
    t0 = time.monotonic()
    while True:
        focus = devstate.parse_window(adb.shell("dumpsys window", timeout=30).text).get("focus")
        waited = time.monotonic() - t0
        if focus and needle in focus:
            return focus, True, waited
        if waited >= wait:
            return focus, False, waited
        time.sleep(interval)


def _launch_setup(p):
    p.add_argument("what", choices=["app", "config", "activity"])
    p.add_argument("cls", nargs="?", help="activity: class inside the Clocky APK (.widget.digital.Foo or fully qualified)")
    p.add_argument("--widget-id", type=int)
    p.add_argument("--ei", nargs=2, action="append", metavar=("KEY", "INT"), help="activity: int extra")
    p.add_argument("--es", nargs=2, action="append", metavar=("KEY", "STR"), help="activity: string extra ([A-Za-z0-9_.:-] only)")
    p.add_argument("--ez", nargs=2, action="append", metavar=("KEY", "BOOL"), help="activity: boolean extra (true|false)")
    p.add_argument("--wait", type=float, default=5.0, help="seconds to wait for the expected Activity to take focus")


@command("launch", _launch_setup)
def cmd_launch(ctx, args):
    """Start Clocky (app), its widget config Activity, or an Activity class inside the Clocky APK."""
    if args.what != "activity" and (args.cls or args.ei or args.es or args.ez):
        raise usage("EXTRAS_NEED_ACTIVITY", "class and --ei/--es/--ez only apply to `launch activity`")
    if args.what == "app":
        cmd, needle = "am start -n %s/%s" % (C.PACKAGE, C.LAUNCH_ACTIVITY), "DeskClock"
    elif args.what == "config":
        if args.widget_id is None:
            raise usage("NO_WIDGET_ID", "launch config needs --widget-id N", "Find ids with `cdev widget`.")
        cmd = "am start -a android.appwidget.action.APPWIDGET_CONFIGURE -n %s/%s --ei appWidgetId %d" % (
            C.PACKAGE, C.CONFIG_ACTIVITY, args.widget_id)
        needle = "DigitalWidgetConfigActivity"
    else:
        if not args.cls:
            raise usage("NO_CLASS", "launch activity needs a class", "Example: cdev launch activity .widget.digital.Foo --ei widgetId 23")
        fq = activity_component(args.cls)
        extras = build_extras(args.ei, args.es, args.ez)
        cmd = ("am start -n %s/%s %s" % (C.PACKAGE, fq, extras)).strip()
        needle = fq.rsplit(".", 1)[-1]
    dev = ctx.device()
    r = dev.adb.shell(cmd, timeout=20)
    txt = (r.text + r.err_text).strip()
    if r.rc != 0 or "Error" in txt or "does not exist" in txt:
        raise adb_err("LAUNCH_FAILED", "am start failed: %s" % txt[:200],
                      "Is the class in the installed build? Run `cdev install` first." if args.what == "activity" else None)
    focus, matched, waited = wait_for_focus(dev.adb, needle, args.wait)
    warnings = list(dev.warnings)
    if not matched:
        warnings.append("focus is %s after %.0fs (expected %s)" % (focus, args.wait, needle))
    return Out({"focus": focus, "waited": round(waited, 2)}, ["started %s" % args.what, "focus %s" % focus], warnings)


# ---------------------------------------------------------------------- adb

@command("adb")
def cmd_adb(ctx, args):
    """Raw adb passthrough with serial + safety guard: cdev adb -- <adb args...>"""
    tail = args.raw_tail
    if tail is None:
        raise usage("NO_DASHDASH", "usage: cdev adb -- <adb args...>", "Example: cdev adb -- shell getprop ro.build.version.sdk")
    check_raw(tail)
    if getattr(ctx, "broker", None) and tail and tail[0] == "install":
        raise refused("BROKER_INSTALL", "Use cdev install --source-commit so installed provenance is recorded")
    dev = ctx.device()
    r = dev.adb.run(tail, timeout=120)
    lines = [l for l in (r.text + r.err_text).splitlines()]
    return Out({"rc": r.rc, "stdout": r.text, "stderr": r.err_text}, lines, dev.warnings,
               exit_code=0 if r.rc == 0 else C.EXIT_ADB)


# --------------------------------------------------------------------- proc

def clocky_pids(adb):
    """-> (pids, source). pidof first; `ps -A` / `ps` when pidof is unavailable."""
    r = adb.shell("pidof " + C.PACKAGE, timeout=10)
    pids = devstate.parse_pidof(r.text + r.err_text, r.rc)
    if pids is not None:
        return pids, "pidof"
    for cmd in ("ps -A", "ps"):
        r = adb.shell(cmd, timeout=20)
        if r.rc == 0 and "PID" in r.text.split("\n", 1)[0]:
            return devstate.parse_ps(r.text, C.PACKAGE), "ps"
    raise adb_err("PROC_LIST_FAILED", "could not list processes (pidof and ps both unusable)")


def clocky_foreground(adb):
    focus = devstate.parse_window(adb.shell("dumpsys window", timeout=30).text).get("focus") or ""
    return (C.PACKAGE + "/") in focus


def wait_pids_gone(adb, timeout, interval=0.5):
    """Poll until no Clocky pid remains. -> (gone, remaining pids, elapsed)."""
    t0 = time.monotonic()
    while True:
        pids, _src = clocky_pids(adb)
        el = time.monotonic() - t0
        if not pids:
            return True, [], el
        if el >= timeout:
            return False, pids, el
        time.sleep(interval)


def _proc_setup(p):
    p.add_argument("action", nargs="?", choices=["status", "kill"], default="status")
    p.add_argument("--hard", action="store_true", help="kill: also `run-as kill -9` when am kill is ineffective (debuggable only)")
    p.add_argument("--timeout", type=float, default=5.0, help="kill: seconds to wait for the process to disappear")


@command("proc", _proc_setup)
def cmd_proc(ctx, args):
    """Clocky process: status (pid list) or kill (am kill; --hard adds run-as kill -9). Never force-stop."""
    dev = ctx.device()
    adb = dev.adb
    pids, source = clocky_pids(adb)
    fg = clocky_foreground(adb)
    if args.action == "status":
        line = "clocky process: running pid %s" % " ".join(map(str, pids)) if pids else "clocky process: absent"
        return Out({"running": bool(pids), "pids": pids, "source": source, "foreground": fg},
                   [line + (" (foreground)" if fg else "")], dev.warnings)
    if not pids:
        return Out({"killed": False, "before": [], "after": [], "method": None},
                   ["clocky process: already absent"], dev.warnings)
    if fg:
        raise usage("CLOCKY_FOREGROUND", "Clocky is in the foreground; killing it would discard unsaved edits",
                    "Run `cdev key home` first.")
    state = load_state(ctx.build_dir)
    epoch = device_epoch(adb)
    adb.shell("am kill " + C.PACKAGE, timeout=15)
    gone, left, el = wait_pids_gone(adb, args.timeout)
    method = "am-kill"
    if gone:
        lines = ["am kill: pid %s gone after %.1fs" % (" ".join(map(str, pids)), el)]
    else:
        if not args.hard:
            raise adb_err("KILL_INEFFECTIVE", "am kill did not remove pid %s within %.0fs" % (" ".join(map(str, left)), args.timeout),
                          "Re-run with --hard (debuggable builds only: run-as kill -9). `am force-stop` is deliberately not used.")
        pk = devstate.parse_package(adb.shell("dumpsys package " + C.PACKAGE, timeout=30).text) or {}
        if not pk.get("debuggable"):
            raise refused("HARD_KILL_REFUSED", "Clocky is not a debuggable build; run-as is unavailable",
                          "Wait for the process to die by itself or ask the user.")
        for pid in left:
            adb.shell("run-as %s kill -9 %d" % (C.PACKAGE, int(pid)), timeout=15)
        gone, left2, el2 = wait_pids_gone(adb, args.timeout)
        if not gone:
            raise adb_err("KILL_INEFFECTIVE", "run-as kill -9 did not remove pid %s" % " ".join(map(str, left2)))
        method = "run-as-kill-9"
        lines = ["am kill ineffective; run-as kill -9 %s: gone after %.1fs" % (" ".join(map(str, left)), el + el2)]
        el += el2
    state.setdefault("marks", {})[C.PROC_KILL_MARK] = epoch
    save_state(ctx.build_dir, state)
    lines.append("mark %s recorded (cdev logs --since mark:%s)" % (C.PROC_KILL_MARK, C.PROC_KILL_MARK))
    return Out({"killed": True, "before": pids, "method": method, "after": [], "elapsed": round(el, 2),
                "mark": C.PROC_KILL_MARK}, lines, dev.warnings)
