"""Device discovery and selection policy (pure functions; adb access is injected)."""
import re

from .errors import device_err

_EMU_RE = re.compile(r"^emulator-\d+$")
_WIFI_RE = re.compile(r"^(\d{1,3}\.){3}\d{1,3}:\d+$|^adb-.*\._adb-tls-connect\._tcp\.?$")
_TRANSPORT_RANK = {"usb": 0, "emulator": 1, "wifi": 2}

STATE_HINTS = {
    "unauthorized": "Accept the USB debugging prompt on the device, then retry.",
    "offline": "Reconnect the cable / toggle USB debugging, then retry.",
    "no permissions": "adb lacks permission to access this device (udev/driver); fix on the host.",
}


def classify_transport(serial):
    if _EMU_RE.match(serial):
        return "emulator"
    if _WIFI_RE.match(serial):
        return "wifi"
    return "usb"


def parse_devices(text):
    """Parse `adb devices -l` output into a list of dicts (all states)."""
    out = []
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("List of devices") or line.startswith("*"):
            continue
        m = re.match(r"^(\S+)\s+(device|offline|unauthorized|no permissions|recovery|sideload|bootloader|host|authorizing|connecting)\b(.*)$", line)
        if not m:
            continue
        serial, state, rest = m.groups()
        info = dict(kv.split(":", 1) for kv in rest.split() if ":" in kv)
        out.append({
            "serial": serial,
            "state": state,
            "transport": classify_transport(serial),
            "model": info.get("model"),
            "product": info.get("product"),
            "identity": None,
        })
    return out


def fill_identities(cands, get_serialno):
    """Set `identity` for ready candidates. get_serialno(serial) -> ro.serialno or None."""
    for c in cands:
        if c["state"] != "device":
            continue
        if c["transport"] == "emulator":
            c["identity"] = c["serial"]
        else:
            c["identity"] = (get_serialno(c["serial"]) or "").strip() or c["serial"]
    return cands


def group_by_identity(cands):
    """identity -> candidate list (ready only), best transport first."""
    groups = {}
    for c in cands:
        if c["state"] == "device":
            groups.setdefault(c["identity"], []).append(c)
    for g in groups.values():
        g.sort(key=lambda c: _TRANSPORT_RANK[c["transport"]])
    return groups


def _describe(cands):
    rows = []
    for c in cands:
        rows.append({k: c.get(k) for k in ("serial", "state", "transport", "model", "identity")})
    return rows


def select_device(cands, flag_serial=None, env=None, pinned=None):
    """Apply the selection policy. Returns (candidate, warnings, reason). Raises CdevError(exit 3)."""
    env = env or {}
    warnings = []
    groups = group_by_identity(cands)
    ready = [c for c in cands if c["state"] == "device"]

    def by_serial(s):
        for c in cands:
            if c["serial"] == s:
                return c
        return None

    explicit = flag_serial or env.get("CLOCKY_SERIAL") or env.get("ANDROID_SERIAL")
    if explicit:
        src = "--serial" if flag_serial else ("CLOCKY_SERIAL" if env.get("CLOCKY_SERIAL") else "ANDROID_SERIAL")
        c = by_serial(explicit)
        if c is None:
            raise device_err("SERIAL_NOT_FOUND", "%s=%s is not connected" % (src, explicit),
                             "Run `cdev devices` to list connected devices.", _describe(cands))
        if c["state"] != "device":
            raise device_err("DEVICE_NOT_READY", "%s is %s" % (explicit, c["state"]),
                             STATE_HINTS.get(c["state"], "Wait for the device to become ready."), _describe(cands))
        return c, warnings, src

    if pinned:
        g = groups.get(pinned.get("identity"))
        if g:
            c = g[0]
            if pinned.get("transport") and pinned["transport"] != c["transport"]:
                warnings.append("transport changed %s->%s (identity %s)"
                                % (pinned["transport"], c["transport"], pinned["identity"]))
            return c, warnings, "pinned"
        if ready:
            raise device_err("PINNED_DEVICE_MISSING",
                             "pinned device %s is not visible, but other device(s) are: %s"
                             % (pinned["identity"], ", ".join(sorted(groups))),
                             "To continue on another device run `cdev prepare --serial <serial>`; "
                             "otherwise reconnect %s." % pinned["identity"], _describe(cands))
        # nothing ready: fall through to the empty-case error below

    if not ready:
        hint = "Connect a device or start an emulator, then run `cdev devices`."
        for c in cands:
            if c["state"] in STATE_HINTS:
                hint = "%s: %s" % (c["serial"], STATE_HINTS[c["state"]])
                break
        raise device_err("NO_DEVICE", "no ready device", hint, _describe(cands))
    if len(groups) > 1:
        raise device_err("MULTIPLE_DEVICES",
                         "%d devices connected; refusing to guess" % len(groups),
                         "Re-run with --serial <serial> (or set CLOCKY_SERIAL).", _describe(ready))
    only = next(iter(groups.values()))
    return only[0], warnings, "only-device"
