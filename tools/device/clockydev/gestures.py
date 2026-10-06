"""Gesture command builders (pure). Only numbers and fixed words reach the device shell."""
import re

from .errors import refused, usage

KEYS = {
    "back": "KEYCODE_BACK", "home": "KEYCODE_HOME", "recents": "KEYCODE_APP_SWITCH",
    "wakeup": "KEYCODE_WAKEUP", "enter": "KEYCODE_ENTER", "menu": "KEYCODE_MENU",
    "dpad_up": "KEYCODE_DPAD_UP", "dpad_down": "KEYCODE_DPAD_DOWN",
    "dpad_left": "KEYCODE_DPAD_LEFT", "dpad_right": "KEYCODE_DPAD_RIGHT", "tab": "KEYCODE_TAB",
}
_KEYCODE_RE = re.compile(r"^KEYCODE_[A-Z0-9_]+$")
BLOCKED_KEYS = {"KEYCODE_POWER", "KEYCODE_SLEEP", "KEYCODE_SOFT_SLEEP", "KEYCODE_ENDCALL", "KEYCODE_REBOOT"}
MOTIONEVENT_MIN_SDK = 30


def key_code(name):
    n = KEYS.get(name.lower())
    if n:
        return n
    up = name.upper()
    if _KEYCODE_RE.match(up):
        if up in BLOCKED_KEYS:
            raise refused("KEY_BLOCKED", "%s is not allowed (could power off the screen)" % up,
                          "Use the physical button if you really need this.")
        return up
    raise usage("UNKNOWN_KEY", "unknown key %r" % name,
                "Use one of: %s or a KEYCODE_* name." % ", ".join(sorted(KEYS)))


def _i(v):
    return int(round(v))


def tap_cmd(x, y):
    return "input tap %d %d" % (_i(x), _i(y))


def swipe_cmd(x1, y1, x2, y2, ms=300):
    return "input swipe %d %d %d %d %d" % (_i(x1), _i(y1), _i(x2), _i(y2), int(ms))


def long_press_cmd(x, y, ms=1500):
    """Zero-distance swipe: works on every API level (no motionevent needed)."""
    return swipe_cmd(x, y, x, y, ms)


def require_motionevent(sdk):
    if sdk is None or sdk < MOTIONEVENT_MIN_SDK:
        raise refused("GESTURE_UNSUPPORTED",
                      "drag needs `input motionevent` (API %d+); this device is API %s" % (MOTIONEVENT_MIN_SDK, sdk),
                      "Use raw `cdev adb -- shell input ...` with sendevent, or test on API 30+ (not implemented in cdev v1).")


def motion_script(start, end, hold_ms=1500, steps=10, move_ms=1000):
    """One shell line: DOWN, hold, MOVE x steps (linear), UP. Coordinates are ints."""
    if steps < 1:
        raise usage("BAD_STEPS", "--steps must be >= 1")
    (x1, y1), (x2, y2) = start, end
    cmds = ["input motionevent DOWN %d %d" % (_i(x1), _i(y1)), "sleep %s" % _secs(hold_ms)]
    per = _secs(move_ms / float(steps))
    for k in range(1, steps + 1):
        x = x1 + (x2 - x1) * k / float(steps)
        y = y1 + (y2 - y1) * k / float(steps)
        cmds.append("input motionevent MOVE %d %d" % (_i(x), _i(y)))
        cmds.append("sleep %s" % per)
    cmds.append("input motionevent UP %d %d" % (_i(x2), _i(y2)))
    return "; ".join(cmds)


def _secs(ms):
    return ("%.3f" % (ms / 1000.0)).rstrip("0").rstrip(".") or "0"


TINY_VIEWPORT_PX = 300
TINY_TRAVEL_PX = 450


def scroll_points(bounds, direction, screen_h=None):
    """Swipe endpoints (start inside bounds; 75% -> 25% of height for 'down' content scroll).

    A very short viewport (e.g. a 55px strip under a big preview) gets a long swipe that starts at its
    edge and travels outside it, because a 27px swipe would not scroll anything.
    """
    l, t, r, b = bounds
    cx = (l + r) // 2
    h = b - t
    if h < TINY_VIEWPORT_PX:
        limit = screen_h or (b + TINY_TRAVEL_PX)
        if direction == "down":
            y0 = b - 4
            return (cx, y0), (cx, max(0, y0 - TINY_TRAVEL_PX))
        if direction == "up":
            y0 = t + 4
            return (cx, y0), (cx, min(limit - 1, y0 + TINY_TRAVEL_PX))
        raise usage("BAD_DIRECTION", "scroll direction must be 'up' or 'down'")
    top, bot = t + int(h * 0.25), t + int(h * 0.75)
    if direction == "down":      # reveal content below: finger moves up
        return (cx, bot), (cx, top)
    if direction == "up":
        return (cx, top), (cx, bot)
    raise usage("BAD_DIRECTION", "scroll direction must be 'up' or 'down'")
