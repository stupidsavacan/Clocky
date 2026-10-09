"""Session state (build/device/state.json), step directories, index.jsonl, saved settings."""
import json
import os
import re
import sys
import time

from . import constants as C
from .devices import fill_identities, parse_devices, select_device
from .errors import adb_err, device_err, refused


# ---------------------------------------------------------------- state file

def state_path(build_dir):
    return os.path.join(build_dir, "state.json")


def load_state(build_dir):
    try:
        with open(state_path(build_dir), encoding="utf-8") as f:
            st = json.load(f)
            if isinstance(st, dict):
                return st
    except (OSError, ValueError):
        pass
    return {}


def save_state(build_dir, st):
    os.makedirs(build_dir, exist_ok=True)
    tmp = state_path(build_dir) + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(st, f, indent=2, ensure_ascii=True)
    os.replace(tmp, state_path(build_dir))


# ------------------------------------------------------------------- Device

class Device:
    def __init__(self, cand, adb, warnings, how):
        self.serial = cand["serial"]
        self.transport = cand["transport"]
        self.identity = cand["identity"]
        self.model = cand.get("model")
        self.adb = adb
        self.warnings = warnings
        self.how = how
        self._props = None

    def props(self):
        if self._props is None:
            from .devstate import parse_getprop
            self._props = parse_getprop(self.adb.shell("getprop", timeout=20).text)
        return self._props

    def sdk(self):
        v = self.props().get("ro.build.version.sdk", "")
        return int(v) if v.isdigit() else None

    def model_name(self):
        return self.props().get("ro.product.model") or self.model

    def summary(self):
        return {"serial": self.serial, "transport": self.transport, "identity": self.identity}


def select_for_ctx(ctx):
    raw = ctx.raw_adb()
    if getattr(ctx, "broker", None):
        # Do not discover/probe other devices. The registered alias was authorized
        # before ADB; now verify physical identity before any further operation.
        serial = ctx.flag_serial or ctx.env.get("CLOCKY_SERIAL") or ctx.env.get("ANDROID_SERIAL")
        actual = raw.bind(serial).shell("getprop ro.serialno", timeout=10, check=True).text.strip()
        if actual != ctx.broker_identity:
            raise device_err("BROKER_IDENTITY_MISMATCH", "Registered transport is connected to a different physical device")
        cand = {"serial": serial, "identity": actual, "transport": "usb", "model": None}
        from .devices import classify_transport
        cand["transport"] = classify_transport(serial)
        dev = Device(cand, raw.bind(serial), [], "broker")
        ctx.all_candidates = [cand]
        ctx.pinned_mismatch = False
        return dev
    res = raw.run(["devices", "-l"], timeout=20)
    if res.rc != 0:
        raise adb_err("ADB_DEVICES_FAILED", "adb devices failed: " + res.err_text.strip()[:200],
                      "Check that adb works from this shell: `adb version`.")
    cands = parse_devices(res.text)

    def serialno(serial):
        r = raw.bind(serial).shell("getprop ro.serialno", timeout=10)
        return r.text.strip() if r.rc == 0 else None

    fill_identities(cands, serialno)
    st = load_state(ctx.build_dir)
    pinned = st.get("pinned")
    # an explicit --serial on prepare re-pins; other commands treat it as a plain override
    cand, warnings, how = select_device(cands, ctx.flag_serial, ctx.env, pinned)
    ctx.all_candidates = cands
    dev = Device(cand, raw.bind(cand["serial"]), warnings, how)
    # explicit selection of a different identity than the pin is allowed for read-only/ui commands
    ctx.pinned_mismatch = bool(pinned and pinned.get("identity") != dev.identity)
    return dev


# ------------------------------------------------------------- session dirs

def new_session_id(identity, now=None):
    t = time.localtime(now if now is not None else time.time())
    return "s-%s-%s" % (time.strftime("%Y%m%d-%H%M%S", t), re.sub(r"[^A-Za-z0-9_.-]", "_", identity))


def session_dir(build_dir, st):
    s = st.get("session")
    if not s:
        return None
    return os.path.join(build_dir, "sessions", s["id"])


def slug(label):
    return re.sub(r"[^A-Za-z0-9_.-]+", "-", label).strip("-") or "x"


def next_step_dir(ctx, label, create=True):
    """Allocate NNNN-label inside the open session (or a loose 'adhoc' session)."""
    st = load_state(ctx.build_dir)
    if not st.get("session"):
        sid = "adhoc-" + time.strftime("%Y%m%d")
        st["session"] = {"id": sid, "step": 0, "adhoc": True}
    st["session"]["step"] = int(st["session"].get("step", 0)) + 1
    n = st["session"]["step"]
    save_state(ctx.build_dir, st)
    d = os.path.join(ctx.build_dir, "sessions", st["session"]["id"], "%04d-%s" % (n, slug(label)))
    if create:
        os.makedirs(d, exist_ok=True)
    return d


def log_command(ctx, ok, err, out=None):
    """Append one line to the open session's index.jsonl (only for device-touching commands)."""
    if getattr(ctx, "_device", None) is None:
        return
    st = load_state(ctx.build_dir)
    sd = session_dir(ctx.build_dir, st)
    if not sd:
        return
    os.makedirs(sd, exist_ok=True)
    line = {
        "ts": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "command": ctx.command,
        "argv": [a for a in sys.argv[1:]][:20],
        "device": ctx._device.summary(),
        "adb": ctx.adb_calls[-12:],
        "ok": ok,
        "error": err,
        "artifacts": list(out.artifacts) if out else [],
    }
    with open(os.path.join(sd, "index.jsonl"), "a", encoding="utf-8") as f:
        f.write(json.dumps(line, ensure_ascii=True) + "\n")


def sessions_size_mb(build_dir):
    total = 0
    root = os.path.join(build_dir, "sessions")
    for dp, _dn, fn in os.walk(root):
        for f in fn:
            try:
                total += os.path.getsize(os.path.join(dp, f))
            except OSError:
                pass
    return total / (1024.0 * 1024.0)


# --------------------------------------------------------- saved settings (I)

def ns_key(item):
    ns, key = item.split("/", 1)
    return ns, key


def get_setting(adb, item):
    ns, key = ns_key(item)
    r = adb.shell("settings get %s %s" % (ns, key), timeout=10, check=True)
    v = r.text.strip()
    return None if (r.rc != 0 or v in ("", "null")) else v


def record_original(st, identity, item, value):
    """Remember the original value of `item` once (first modification only)."""
    if item not in C.SETTINGS_ALLOWLIST:
        raise refused("SETTING_NOT_ALLOWED", "refusing to touch setting %s" % item,
                      "cdev only changes: " + ", ".join(C.SETTINGS_ALLOWLIST))
    ss = st.setdefault("saved_settings", {"identity": identity, "items": {}})
    if ss.get("identity") not in (None, identity) and ss["items"]:
        raise device_err("IDENTITY_MISMATCH",
                         "pending restore belongs to device %s, but current device is %s" % (ss["identity"], identity),
                         "Reconnect %s and run `cdev restore`." % ss["identity"])
    ss["identity"] = identity
    if item not in ss["items"]:
        ss["items"][item] = value   # None means 'was undefined'
    return ss


def pending_restore(st):
    ss = st.get("saved_settings") or {}
    return dict(ss.get("items") or {})


def require_identity(st, identity):
    ss = st.get("saved_settings") or {}
    if ss.get("items") and ss.get("identity") != identity:
        raise device_err("IDENTITY_MISMATCH",
                         "pending restore belongs to device %s, but current device is %s" % (ss.get("identity"), identity),
                         "Reconnect %s (or use --serial for it) and run `cdev restore`." % ss.get("identity"))


def put_setting(adb, item, value):
    if item not in C.SETTINGS_ALLOWLIST:
        raise refused("SETTING_NOT_ALLOWED", "refusing to touch setting %s" % item)
    ns, key = ns_key(item)
    if value is None:
        return adb.shell("settings delete %s %s" % (ns, key), timeout=10, check=True)
    if not re.fullmatch(r"-?\d+", str(value)):
        raise refused("SETTING_VALUE", "refusing non-numeric value for %s" % item)
    return adb.shell("settings put %s %s %s" % (ns, key, value), timeout=10, check=True)


def restore_all(adb, st):
    """Write back saved values; remove each item only after verifying. Returns (restored, failed)."""
    ss = st.get("saved_settings") or {}
    restored, failed = {}, {}
    for item, orig in list((ss.get("items") or {}).items()):
        put_setting(adb, item, orig)
        now = get_setting(adb, item)
        if now == orig:
            restored[item] = orig
            del ss["items"][item]
        else:
            failed[item] = {"expected": orig, "actual": now}
    if not ss.get("items"):
        st.pop("saved_settings", None)
    return restored, failed
