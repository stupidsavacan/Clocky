"""status, prepare, mark, rotate, restore, finish, cleanup."""
import json
import os
import time

from . import constants as C
from . import devstate
from .cli import Out, command
from .errors import CdevError, adb_err, usage
from .session import (Device, get_setting, load_state, new_session_id, next_step_dir, pending_restore,
                      put_setting, record_original, require_identity, restore_all, save_state)


def pending_lines(adb, st):
    lines = []
    for item, orig in pending_restore(st).items():
        cur = get_setting(adb, item) if adb else "?"
        lines.append("PENDING RESTORE: %s (current %s -> will be set back to %s)"
                     % (item, cur, "<unset>" if orig is None else orig))
    return lines


def device_epoch(adb):
    r = adb.shell("date +%s.%N", timeout=10)
    e = devstate.parse_device_epoch(r.text)
    if e is None:
        raise adb_err("DEVICE_CLOCK", "could not read the device clock: " + r.text.strip()[:80])
    return e


def _status_out(ctx, dev, st, extra_lines=None):
    state = load_state(ctx.build_dir)
    lines = devstate.human_lines(dev.serial, dev.transport, st)
    lines += pending_lines(dev.adb, state)
    if extra_lines:
        lines += extra_lines
    warnings = list(dev.warnings)
    if st.get("clocky") is None:
        warnings.append("Clocky (%s) is not installed on this device" % C.PACKAGE)
    elif not st["clocky"].get("debuggable"):
        warnings.append("Clocky is not debuggable: settings via run-as will be unavailable")
    return lines, warnings


@command("status")
def cmd_status(ctx, args):
    """One-screen device state summary."""
    dev = ctx.device()
    st = devstate.snapshot(dev.adb)
    lines, warnings = _status_out(ctx, dev, st)
    state = load_state(ctx.build_dir)
    return Out({"status": st, "pending_restore": pending_restore(state),
                "session": (state.get("session") or {}).get("id")}, lines, warnings)


def _prepare_setup(p):
    p.add_argument("--stay-awake", action="store_true",
                   help="set stay_on_while_plugged_in=3 (restored by `restore`/`finish`)")
    p.add_argument("--home", action="store_true", help="press HOME after waking")


@command("prepare", _prepare_setup)
def cmd_prepare(ctx, args):
    """Start a session: wake the screen, pin the device, record the log mark."""
    dev = ctx.device()
    adb = dev.adb
    state = load_state(ctx.build_dir)
    require_identity(state, dev.identity)
    notes = []
    st = devstate.snapshot(adb, with_logs=False)
    if st.get("wakefulness") != "Awake":
        adb.shell("input keyevent KEYCODE_WAKEUP", timeout=10)
        time.sleep(1.0)
        st = devstate.snapshot(adb, with_logs=False)
        notes.append("woke the screen (KEYCODE_WAKEUP)")
    if st.get("wakefulness") != "Awake":
        raise usage("DEVICE_ASLEEP", "device did not wake (wakefulness=%s)" % st.get("wakefulness"),
                    "Press the power button on the device, then retry.")
    if st.get("keyguard"):
        raise usage("KEYGUARD_SHOWING", "the keyguard (lock screen) is showing; cdev never unlocks devices",
                    "Unlock the device manually, then re-run `cdev prepare`.")
    if args.stay_awake:
        item = "global/stay_on_while_plugged_in"
        cur = get_setting(adb, item)
        record_original(state, dev.identity, item, cur)
        put_setting(adb, item, 3)
        notes.append("stay_on_while_plugged_in %s -> 3 (pending restore)" % cur)
    if args.home:
        adb.shell("input keyevent KEYCODE_HOME", timeout=10)
        time.sleep(0.8)
        notes.append("pressed HOME")
    epoch = device_epoch(adb)
    sid = new_session_id(dev.identity)
    state["pinned"] = {"identity": dev.identity, "transport": dev.transport, "serial": dev.serial}
    state["session"] = {"id": sid, "step": 0, "started": time.strftime("%Y-%m-%dT%H:%M:%S")}
    state["marks"] = {"prepare": epoch}
    save_state(ctx.build_dir, state)
    st = devstate.snapshot(adb)
    d = next_step_dir(ctx, "prepare")
    with open(os.path.join(d, "status.json"), "w", encoding="utf-8") as f:
        json.dump(st, f, indent=2, ensure_ascii=True)
    state = load_state(ctx.build_dir)
    lines = ["session  %s" % sid] + devstate.human_lines(dev.serial, dev.transport, st)
    lines += pending_lines(adb, state)
    lines += notes
    warn = list(dev.warnings)
    if st.get("clocky") is None:
        warn.append("Clocky (%s) is not installed on this device; run `cdev install`" % C.PACKAGE)
    return Out({"session": sid, "status": st, "mark_prepare": epoch, "notes": notes,
                "pending_restore": pending_restore(state)}, lines, warn, [ctx.rel(os.path.join(d, "status.json"))])


def _mark_setup(p):
    p.add_argument("name", help="mark name")


@command("mark", _mark_setup)
def cmd_mark(ctx, args):
    """Record a named device-time boundary for `logs --since mark:NAME`."""
    dev = ctx.device()
    state = load_state(ctx.build_dir)
    e = device_epoch(dev.adb)
    state.setdefault("marks", {})[args.name] = e
    save_state(ctx.build_dir, state)
    return Out({"name": args.name, "epoch": e}, ["mark %s = %.3f" % (args.name, e)], dev.warnings)


ROTATIONS = {"portrait": (0, 0), "landscape": (0, 1), "reverse-portrait": (0, 2), "reverse-landscape": (0, 3)}


def _rotate_setup(p):
    p.add_argument("mode", choices=sorted(ROTATIONS) + ["auto"])


@command("rotate", _rotate_setup)
def cmd_rotate(ctx, args):
    """Fix the device rotation (restored by `restore`/`finish`)."""
    dev = ctx.device()
    adb = dev.adb
    state = load_state(ctx.build_dir)
    require_identity(state, dev.identity)
    before = {k: get_setting(adb, "system/" + k) for k in ("accelerometer_rotation", "user_rotation")}
    for k in ("accelerometer_rotation", "user_rotation"):
        record_original(state, dev.identity, "system/" + k, before[k])
    save_state(ctx.build_dir, state)
    if args.mode == "auto":
        put_setting(adb, "system/accelerometer_rotation", 1)
    else:
        put_setting(adb, "system/accelerometer_rotation", 0)
        put_setting(adb, "system/user_rotation", ROTATIONS[args.mode][1])
    time.sleep(1.0)
    after = {k: get_setting(adb, "system/" + k) for k in ("accelerometer_rotation", "user_rotation")}
    return Out({"before": before, "after": after, "mode": args.mode},
               ["rotation %s -> %s" % (before, after), "PENDING RESTORE: run `cdev restore` when finished"],
               dev.warnings)


@command("restore")
def cmd_restore(ctx, args):
    """Write back device settings changed by cdev."""
    dev = ctx.device()
    state = load_state(ctx.build_dir)
    if not pending_restore(state):
        return Out({"restored": {}, "failed": {}}, ["nothing to restore"], dev.warnings)
    require_identity(state, dev.identity)
    restored, failed = restore_all(dev.adb, state)
    save_state(ctx.build_dir, state)
    lines = ["restored %s -> %s" % (k, v) for k, v in restored.items()]
    if failed:
        raise adb_err("RESTORE_FAILED", "could not restore: %s" % json.dumps(failed),
                      "State is kept; fix the device and re-run `cdev restore`.")
    return Out({"restored": restored, "failed": failed}, lines or ["nothing to restore"], dev.warnings)


@command("cleanup")
def cmd_cleanup(ctx, args):
    """Delete leftover /data/local/tmp/clocky-dev-* files on the device."""
    dev = ctx.device()
    r = dev.adb.shell("ls %s" % C.DEVICE_TMP_DIR, timeout=10)
    names = [n for n in r.text.split() if n.startswith(C.DEVICE_TMP_PREFIX)]
    for n in names:
        dev.adb.shell("rm -f %s/%s" % (C.DEVICE_TMP_DIR, n), timeout=10)
    return Out({"removed": names}, ["removed %d file(s)" % len(names)] + names, dev.warnings)


@command("finish")
def cmd_finish(ctx, args):
    """Restore device settings and close the session."""
    dev = ctx.device()
    state = load_state(ctx.build_dir)
    restored, failed = {}, {}
    if pending_restore(state):
        require_identity(state, dev.identity)
        restored, failed = restore_all(dev.adb, state)
        save_state(ctx.build_dir, state)
    if failed:
        raise adb_err("RESTORE_FAILED", "could not restore: %s" % json.dumps(failed),
                      "Session left open; fix the device and re-run `cdev finish`.")
    sid = (state.get("session") or {}).get("id")
    state.pop("session", None)
    save_state(ctx.build_dir, state)
    cleanup = cmd_cleanup(ctx, args)
    lines = ["session %s closed" % sid] + ["restored %s -> %s" % kv for kv in restored.items()]
    lines += ["removed %d leftover temp file(s)" % len(cleanup.result["removed"])]
    return Out({"session": sid, "restored": restored, "cleanup": cleanup.result["removed"]}, lines, dev.warnings)
