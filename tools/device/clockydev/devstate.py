"""Device status snapshot. Parsers are pure; `snapshot()` does the adb calls.

No device-side grep: full dumpsys text is fetched and parsed on the host.
"""
import re

from . import constants as C


def parse_getprop(text):
    props = {}
    for m in re.finditer(r"^\[([^\]]+)\]: \[(.*)\]$", text, re.M):
        props[m.group(1)] = m.group(2)
    return props


def parse_wm(text):
    """`wm size` / `wm density` -> dict(physical=(w,h)|int, override=...)."""
    out = {}
    for m in re.finditer(r"^(Physical|Override) (size|density): (\d+)(?:x(\d+))?", text, re.M):
        kind = m.group(1).lower()
        val = (int(m.group(3)), int(m.group(4))) if m.group(4) else int(m.group(3))
        out[kind] = val
    return out


def parse_window(text):
    out = {}
    m = re.search(r"isKeyguardShowing=(true|false)", text)
    if m:
        out["keyguard"] = m.group(1) == "true"
    else:   # older API fallback
        m = re.search(r"mShowingLockscreen=(true|false)", text)
        d = re.search(r"mDreamingLockscreen=(true|false)", text)
        if m or d:
            out["keyguard"] = bool((m and m.group(1) == "true") or (d and d.group(1) == "true"))
    m = (re.search(r"^\s*mRotation=(\d)\b", text, re.M) or re.search(r"\bmCurrentRotation=(?:ROTATION_)?(\d+)", text)
         or re.search(r"\bmDisplayRotation=ROTATION_(\d+)", text))
    if m:
        out["rotation"] = int(m.group(1)) // (90 if int(m.group(1)) >= 90 else 1)
    m = re.search(r"mUserRotationMode=(\w+)", text)
    if m:
        out["user_rotation_mode"] = m.group(1)
    m = re.search(r"mUserRotation=(?:ROTATION_)?(\d+)", text)
    if m:
        out["user_rotation"] = int(m.group(1)) // (90 if int(m.group(1)) >= 90 else 1)
    m = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([^}\s]+)\}", text)
    if m:
        out["focus"] = m.group(1)
    m = re.search(r"mFocusedApp=\S*ActivityRecord\{\S+ \S+ ([^\s}]+)", text)
    if m:
        out["focused_app"] = m.group(1)
    return out


def parse_power(text):
    m = re.search(r"mWakefulness=(\w+)", text)
    return m.group(1) if m else None


def parse_resumed(text):
    m = (re.search(r"topResumedActivity=ActivityRecord\{\S+ \S+ ([^\s}]+)", text)
         or re.search(r"mResumedActivity:\s*ActivityRecord\{\S+ \S+ ([^\s}]+)", text)
         or re.search(r"ResumedActivity:\s*ActivityRecord\{\S+ \S+ ([^\s}]+)", text))
    return m.group(1) if m else None


def parse_ime(text):
    m = re.search(r"mInputShown=(true|false)", text)
    return (m.group(1) == "true") if m else None


def parse_home(text):
    lines = [l.strip() for l in text.splitlines() if "/" in l and not l.startswith("priority")]
    return lines[-1] if lines else None


def parse_package(text):
    if "versionCode" not in text:
        return None
    out = {}
    for key, rx in (("versionCode", r"versionCode=(\d+)"), ("versionName", r"versionName=(\S+)"),
                    ("lastUpdateTime", r"lastUpdateTime=([^\n]+)"), ("uid", r"(?:userId|appId)=(\d+)")):
        m = re.search(rx, text)
        if m:
            out[key] = int(m.group(1)) if key in ("versionCode", "uid") else m.group(1).strip()
    out["debuggable"] = bool(re.search(r"flags=\[[^\]]*\bDEBUGGABLE\b", text))
    return out if out.get("versionCode") is not None else None


def parse_epoch_first_line(text):
    """First `logcat -v epoch` line -> float epoch (or None)."""
    for line in text.splitlines():
        m = re.match(r"^\s*(\d{9,}\.\d+)\s", line)
        if m:
            return float(m.group(1))
    return None


def parse_device_epoch(text):
    m = re.search(r"(\d{9,})(?:\.(\d+))?", text)
    if not m:
        return None
    return float("%s.%s" % (m.group(1), (m.group(2) or "0")[:3]))


def _setting(adb, item):
    ns, key = item.split("/", 1)
    r = adb.shell("settings get %s %s" % (ns, key), timeout=10)
    v = r.text.strip()
    return None if v in ("", "null") else v


def snapshot(adb, with_logs=True):
    """Collect the full status dict (read-only)."""
    props = parse_getprop(adb.shell("getprop", timeout=20).text)
    st = {
        "model": props.get("ro.product.model"),
        "manufacturer": props.get("ro.product.manufacturer"),
        "sdk": int(props["ro.build.version.sdk"]) if props.get("ro.build.version.sdk", "").isdigit() else None,
        "release": props.get("ro.build.version.release"),
        "fingerprint": props.get("ro.build.fingerprint"),
        "locale": props.get("persist.sys.locale") or props.get("ro.product.locale"),
    }
    wm = parse_wm(adb.shell("wm size", timeout=10).text)
    dn = parse_wm(adb.shell("wm density", timeout=10).text)
    st["size"] = list(wm.get("override") or wm.get("physical") or [])
    st["density"] = dn.get("override") or dn.get("physical")
    win = parse_window(adb.shell("dumpsys window", timeout=30).text)
    st.update({k: win.get(k) for k in ("keyguard", "rotation", "user_rotation_mode", "user_rotation",
                                       "focus", "focused_app")})
    st["wakefulness"] = parse_power(adb.shell("dumpsys power", timeout=30).text)
    st["resumed"] = parse_resumed(adb.shell("dumpsys activity activities", timeout=30).text)
    st["ime_shown"] = parse_ime(adb.shell("dumpsys input_method", timeout=30).text)
    st["launcher"] = parse_home(adb.shell(
        "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
        timeout=15).text)
    st["clocky"] = parse_package(adb.shell("dumpsys package " + C.PACKAGE, timeout=30).text)
    st["settings"] = {
        "accelerometer_rotation": _setting(adb, "system/accelerometer_rotation"),
        "user_rotation": _setting(adb, "system/user_rotation"),
        "font_scale": _setting(adb, "system/font_scale"),
        "stay_on_while_plugged_in": _setting(adb, "global/stay_on_while_plugged_in"),
    }
    night = adb.shell("cmd uimode night", timeout=10).text.strip()
    m = re.search(r"Night mode:\s*(\w+)", night)
    st["night"] = m.group(1) if m else (night or None)
    st["log_buffer"] = None
    if with_logs:
        r = adb.shell("logcat -d -v epoch -b main", timeout=60)
        first = parse_epoch_first_line(r.text)
        now = parse_device_epoch(adb.shell("date +%s.%N", timeout=10).text)
        if first and now:
            st["log_buffer"] = {"main_oldest_epoch": first, "now_epoch": now,
                                "retained_seconds": int(now - first)}
    return st


def human_line(serial, transport, st):
    rot = st.get("rotation")
    auto = (st.get("settings") or {}).get("accelerometer_rotation")
    rot_s = "rot %s (%s)" % (rot if rot is not None else "?", "auto" if auto == "1" else "fixed")
    awake = {"Awake": "awake", "Asleep": "asleep", "Dozing": "dozing"}.get(st.get("wakefulness"), str(st.get("wakefulness")))
    kg = {True: "LOCKED", False: "unlocked", None: "keyguard ?"}[st.get("keyguard")]
    ime = {True: "IME shown", False: "IME hidden", None: "IME ?"}[st.get("ime_shown")]
    return "%s %s | %s | API %s | %s | %s | %s | %s" % (
        serial, transport, st.get("model") or "?", st.get("sdk") or "?", rot_s, awake, kg, ime)


def human_lines(serial, transport, st):
    lines = [human_line(serial, transport, st)]
    lines.append("focus    %s" % (st.get("focus") or st.get("resumed") or "?"))
    lines.append("launcher %s" % (st.get("launcher") or "?"))
    c = st.get("clocky")
    if c:
        lines.append("clocky   v%s (%s)%s uid=%s updated %s" % (
            c.get("versionName"), c.get("versionCode"), " debuggable" if c.get("debuggable") else " NOT-debuggable",
            c.get("uid"), c.get("lastUpdateTime")))
    else:
        lines.append("clocky   NOT INSTALLED (run `cdev install`)")
    lb = st.get("log_buffer")
    if lb:
        lines.append("logbuf   main buffer holds ~%ds (run `cdev logs`/`collect` soon after acting)" % lb["retained_seconds"])
    s = st.get("settings") or {}
    lines.append("display  %s dpi=%s locale=%s font_scale=%s night=%s" % (
        "x".join(str(x) for x in st.get("size") or []), st.get("density"), st.get("locale"),
        s.get("font_scale"), st.get("night")))
    return lines


def parse_pidof(text, rc):
    """pidof output -> list of pids; [] when the process is absent (rc 1); None when pidof itself is unusable."""
    t = (text or "").strip()
    if rc == 127 or "not found" in t.lower() or "inaccessible" in t.lower():
        return None
    return [int(x) for x in t.split() if x.isdigit()]


def parse_ps(text, package):
    """`ps` / `ps -A` output -> pids whose NAME column equals `package` exactly."""
    lines = [l for l in (text or "").splitlines() if l.strip()]
    if not lines:
        return []
    header = lines[0].split()
    if "PID" not in header:
        return []
    ip = header.index("PID")
    out = []
    for l in lines[1:]:
        f = l.split()
        if len(f) > ip and f[-1] == package and f[ip].isdigit():
            out.append(int(f[ip]))
    return out
