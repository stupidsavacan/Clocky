"""logcat parsing, Clocky filtering, classification, gap detection (all host-side, pure)."""
import re
import time

from . import constants as C

LINE_RE = re.compile(r"^\s*(\d{9,}\.\d+)\s+(\S+)\s+(\d+)\s+(\d+)\s+([VDIWEFS])\s+(.*?)\s*:\s(.*)$")
LINE_NOUID_RE = re.compile(r"^\s*(\d{9,}\.\d+)\s+(\d+)\s+(\d+)\s+([VDIWEFS])\s+(.*?)\s*:\s(.*)$")
NOISE_TAGS = {"AppsFilter"}
BODY_NEEDLES = (C.PACKAGE, "DigitalAppWidgetProvider", "DigitalWidgetConfigActivity", "appWidgetId")
EVENT_TAGS = {"wm_create_activity", "am_proc_start", "am_proc_died", "am_crash", "am_anr", "am_kill", "am_create_activity",
              "wm_finish_activity", "wm_destroy_activity", "wm_restart_activity"}
ACT_KEY_TAGS = {"wm_create_activity", "wm_on_create_called", "wm_finish_activity", "wm_on_destroy_called", "am_create_activity"}
CLASSES = ("CRASH", "ANR", "PROC", "ACT", "LAUNCH", "WIDGET", "other")


def parse_line(line):
    """-> dict(epoch, uid, pid, tid, level, tag, msg, raw) or None (buffer markers, blank, junk)."""
    m = LINE_RE.match(line)
    if m:
        e, uid, pid, tid, lvl, tag, msg = m.groups()
        return {"epoch": float(e), "uid": uid, "pid": int(pid), "tid": int(tid), "level": lvl,
                "tag": tag.strip(), "msg": msg, "raw": line}
    m = LINE_NOUID_RE.match(line)
    if m:
        e, pid, tid, lvl, tag, msg = m.groups()
        return {"epoch": float(e), "uid": None, "pid": int(pid), "tid": int(tid), "level": lvl,
                "tag": tag.strip(), "msg": msg, "raw": line}
    return None


def parse_log(text):
    return [e for e in (parse_line(l) for l in text.splitlines()) if e]


def uid_names(uid):
    """Numeric uid -> the spellings logcat may print."""
    if uid is None:
        return set()
    out = {str(uid)}
    if uid >= 10000:
        out.add("u0_a%d" % (uid - 10000))
    return out


def oldest_epoch(entries):
    return entries[0]["epoch"] if entries else None


def gap_warning(buffer, entries, since_epoch):
    """Warn when the buffer's oldest entry is newer than the requested start."""
    old = oldest_epoch(entries)
    if since_epoch and old is not None and old > since_epoch + 1:
        return "buffer %s rolled over: logs between the mark and %s are lost (~%ds); run `cdev logs`/`collect` right after acting" % (
            buffer, hhmmss(old), int(old - since_epoch))
    return None


def hhmmss(epoch):
    return time.strftime("%H:%M:%S", time.localtime(epoch))


def is_clocky_related(e, uids):
    if e["tag"] in NOISE_TAGS:
        return False
    if e["uid"] is not None and e["uid"] in uids:
        return True
    body = e["msg"]
    return any(n in body for n in BODY_NEEDLES)


def classify(e, buffer):
    tag, msg = e["tag"], e["msg"]
    if tag in ("AndroidRuntime", "am_crash", "DEBUG", "libc") and (buffer == "crash" or tag != "libc"):
        return "CRASH"
    if buffer == "crash":
        return "CRASH"
    if tag == "am_anr" or msg.startswith("ANR in "):
        return "ANR"
    if tag in ("am_proc_start", "am_proc_died", "am_kill"):
        return "PROC"
    if tag.startswith("wm_on_") or tag in EVENT_TAGS:
        return "ACT"
    if "was not returned" in msg:
        return "LAUNCH"
    if "AppWidget" in tag or "appWidgetId" in msg or "AppWidget" in msg:
        return "WIDGET"
    return "other"


def filter_clocky(entries_by_buffer, uid, since_epoch=0.0, pids_hint=True):
    """Apply the Clocky filter to {buffer: [entries]}; returns list of (buffer, entry, class)."""
    uids = uid_names(uid)
    out = []
    for buf, entries in entries_by_buffer.items():
        entries = [e for e in entries if e["epoch"] >= since_epoch]
        crash_pids = {e["pid"] for e in entries if e["tag"] == "AndroidRuntime" and
                      (C.PACKAGE in e["msg"] or (e["uid"] in uids))}
        for e in entries:
            keep = False
            if is_clocky_related(e, uids):
                keep = True
            elif e["tag"] == "AndroidRuntime" and e["pid"] in crash_pids:
                keep = True
            elif e["tag"].startswith("wm_on_") and C.PACKAGE in e["msg"]:
                keep = True
            elif e["tag"] == "Launcher" and "was not returned" in e["msg"]:
                keep = True
            if keep:
                out.append((buf, e, classify(e, buf)))
    out.sort(key=lambda t: t[1]["epoch"])
    return out


def format_entry(buf, e, cls):
    return "%s %-6s %s/%s %s %s: %s" % (hhmmss(e["epoch"]), cls, buf, e["level"], e["uid"] or "-", e["tag"], e["msg"][:200])


def summarize(items, gaps, since_epoch, now_epoch=None, max_per_class=8):
    """Build a dict summary + human lines."""
    by = {c: [] for c in CLASSES}
    for buf, e, cls in items:
        by[cls].append((buf, e))
    lines = []
    span = ""
    if since_epoch and now_epoch:
        span = " (%s, %ds)" % (hhmmss(since_epoch), int(now_epoch - since_epoch))
    lines.append("logs since%s | %d relevant line(s)" % (span, len(items)))
    for cls in CLASSES:
        rows = by[cls]
        if cls == "other":
            lines.append("%-6s %s" % (cls, "%d line(s) (see logcat_clocky.txt)" % len(rows) if rows else "none"))
            continue
        if not rows:
            lines.append("%-6s none" % cls)
            continue
        if cls == "ACT":
            key = [r for r in rows if r[1]["tag"] in ACT_KEY_TAGS]
            if key:
                hidden = len(rows) - len(key)
                rows = key + ([] if not hidden else [])
                if hidden:
                    lines.append("ACT    (%d other lifecycle line(s) hidden; see logcat_clocky.txt)" % hidden)
        for buf, e in rows[:max_per_class]:
            lines.append("%-6s %s %s: %s" % (cls, hhmmss(e["epoch"]), e["tag"], e["msg"][:160]))
        if len(rows) > max_per_class:
            lines.append("%-6s ... %d more" % (cls, len(rows) - max_per_class))
    for g in gaps:
        lines.append("WARNING " + g)
    counts = {c: len(by[c]) for c in CLASSES}
    return {"counts": counts, "gaps": gaps, "crash": counts["CRASH"] > 0, "anr": counts["ANR"] > 0}, lines


def resolve_since(spec, marks, now_epoch):
    """'prepare' | 'mark:NAME' | '<N>s' | 'all-buffer' -> epoch (0.0 = whole buffer). Raises ValueError."""
    if spec == "all-buffer":
        return 0.0
    if spec == "prepare":
        if "prepare" not in marks:
            raise ValueError("no prepare mark; run `cdev prepare` or use --since <N>s / mark:NAME / all-buffer")
        return marks["prepare"]
    if spec.startswith("mark:"):
        name = spec[5:]
        if name not in marks:
            raise ValueError("unknown mark %r (known: %s)" % (name, ", ".join(sorted(marks)) or "none"))
        return marks[name]
    m = re.fullmatch(r"(\d+)s", spec)
    if m:
        return now_epoch - int(m.group(1))
    raise ValueError("bad --since %r (use prepare, mark:NAME, <N>s or all-buffer)" % spec)
