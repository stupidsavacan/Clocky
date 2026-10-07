"""Compare two evidence bundles (pure, device not needed)."""
import json
import os

from . import widgets as W
from .errors import ui_err, usage

FILES = ("meta.json", "widget.json", "clocky_widget_settings.xml", "logs_summary.txt", "logcat_crash.txt")
MAX_SHOWN = 20


# ----------------------------------------------------------- bundle resolution

def _step_dirs(sessions_dir, sid):
    d = os.path.join(sessions_dir, sid)
    return sorted(n for n in os.listdir(d) if os.path.isdir(os.path.join(d, n))) if os.path.isdir(d) else []


def _latest_session(sessions_dir):
    if not os.path.isdir(sessions_dir):
        return None
    ss = [n for n in os.listdir(sessions_dir) if os.path.isdir(os.path.join(sessions_dir, n))]
    return max(ss, key=lambda n: os.path.getmtime(os.path.join(sessions_dir, n))) if ss else None


def resolve_bundle(ref, sessions_dir, current_sid=None):
    """Path | step dir name | collect label | <session-id>/<label|dir> -> bundle directory."""
    if os.path.isdir(ref):
        return ref
    name = ref
    sid = None
    if "/" in ref or "\\" in ref:
        sid, name = ref.replace("\\", "/").split("/", 1)
    elif current_sid and os.path.isdir(os.path.join(sessions_dir, current_sid)):
        sid = current_sid
    else:
        sid = _latest_session(sessions_dir)
    if not sid:
        raise ui_err("BUNDLE_NOT_FOUND", "no evidence session found for %r" % ref,
                     "Pass a bundle path or run `cdev collect <label>` first.")
    steps = _step_dirs(sessions_dir, sid)
    if name in steps:
        return os.path.join(sessions_dir, sid, name)
    hits = [n for n in steps if n.split("-", 1)[-1] == "collect-" + name]
    if len(hits) > 1:
        raise usage("AMBIGUOUS_BUNDLE", "label %r matches %d bundles in %s" % (name, len(hits), sid),
                    "Use the step directory name.", hits)
    if not hits:
        raise ui_err("BUNDLE_NOT_FOUND", "no bundle %r in session %s" % (name, sid),
                     "Use `<session-id>/<label>`, a step dir name, or a path.",
                     [n for n in steps if "-collect-" in n])
    return os.path.join(sessions_dir, sid, hits[0])


def load_bundle(d):
    b = {"dir": d, "missing": []}
    for f in FILES:
        p = os.path.join(d, f)
        if os.path.isfile(p):
            with open(p, encoding="utf-8", errors="replace", newline="") as fh:
                b[f] = fh.read()
        else:
            b[f] = None
            b["missing"].append(f)
    for f in ("meta.json", "widget.json"):
        if b[f] is not None:
            try:
                b[f] = json.loads(b[f])
            except ValueError:
                b[f] = None
                b["missing"].append(f + " (unreadable)")
    return b


# ------------------------------------------------------------------ diffing

def json_diff(a, b, path=""):
    """Deep diff -> (changed [{path,a,b}], added [path], removed [path])."""
    ch, ad, rm = [], [], []
    if isinstance(a, dict) and isinstance(b, dict):
        for k in sorted(set(a) | set(b)):
            p = "%s.%s" % (path, k) if path else str(k)
            if k not in b:
                rm.append(p)
            elif k not in a:
                ad.append(p)
            else:
                c, x, r = json_diff(a[k], b[k], p)
                ch += c
                ad += x
                rm += r
    elif isinstance(a, list) and isinstance(b, list):
        for i in range(max(len(a), len(b))):
            p = "%s[%d]" % (path, i)
            if i >= len(b):
                rm.append(p)
            elif i >= len(a):
                ad.append(p)
            else:
                c, x, r = json_diff(a[i], b[i], p)
                ch += c
                ad += x
                rm += r
    elif a != b or type(a) is not type(b):
        ch.append({"path": path, "a": a, "b": b})
    return ch, ad, rm


def diff_settings(xa, xb):
    if xa is None or xb is None:
        return None
    out = {"byte_identical": xa == xb, "changed": [], "added": [], "removed": []}
    if xa == xb:
        return out
    pa, pb = W.parse_prefs_all(xa), W.parse_prefs_all(xb)
    for k in sorted(set(pa) | set(pb)):
        if k not in pb:
            out["removed"].append(k)
        elif k not in pa:
            out["added"].append(k)
        elif pa[k][0] != pb[k][0]:
            out["changed"].append({"path": k, "a": pa[k][1], "b": pb[k][1]})
        else:
            c, x, r = json_diff(pa[k][1], pb[k][1], k)
            out["changed"] += c
            out["added"] += x
            out["removed"] += r
    return out


def _settings_same(s):
    return s is not None and not (s["changed"] or s["added"] or s["removed"])


def diff_widgets(wa, wb):
    if wa is None or wb is None:
        return None

    def view(w):
        return {str(x["id"]): {"host": x.get("host"), "size_class": x.get("size_class"),
                               "schema": x["settings"].get("schema") if isinstance(x.get("settings"), dict) else None}
                for x in w.get("widgets", [])}
    va, vb = view(wa), view(wb)
    ch, ad, rm = json_diff(va, vb, "widget")
    ca = {"%s:%s" % (c["check"], c.get("id")) for c in wa.get("checks", [])}
    cb = {"%s:%s" % (c["check"], c.get("id")) for c in wb.get("checks", [])}
    return {"ids": sorted(int(i) for i in vb), "changed": ch, "added": ad, "removed": rm,
            "checks_added": sorted(cb - ca), "checks_removed": sorted(ca - cb)}


META_FIELDS = (
    ("clocky.versionName", ("clocky", "installed", "versionName")),
    ("clocky.versionCode", ("clocky", "installed", "versionCode")),
    ("clocky.lastUpdateTime", ("clocky", "installed", "lastUpdateTime")),
    ("install.sha256", ("clocky", "install_record", "sha256")),
    ("device.model", ("device", "model")),
    ("device.sdk", ("device", "sdk")),
    ("device.locale", ("device", "locale")),
    ("device.rotation", ("device", "rotation")),
    ("device.identity", ("device", "identity")),
    ("git.head", ("git", "head")),
)


def _dig(d, path):
    for k in path:
        d = d.get(k) if isinstance(d, dict) else None
    return d


def diff_meta(ma, mb):
    if ma is None or mb is None:
        return None
    return {"changed": [{"field": n, "a": _dig(ma, p), "b": _dig(mb, p)} for n, p in META_FIELDS
                        if _dig(ma, p) != _dig(mb, p)]}


def logs_of(b):
    summary, crash = b.get("logs_summary.txt"), b.get("logcat_crash.txt")
    if summary is None and crash is None:
        return None
    anr = crash_s = None
    for line in (summary or "").splitlines():
        if line.startswith("ANR"):
            anr = line[3:].strip() != "none"
        elif line.startswith("CRASH"):
            crash_s = line[5:].strip() != "none"
    lines = len([x for x in crash.splitlines() if x.strip()]) if crash is not None else None
    return {"crash_lines": lines, "crash": crash_s, "anr": anr}


def compare_bundles(da, db, only=None):
    """-> result dict. `only` limits to one of settings|widgets|meta|logs."""
    a, b = load_bundle(da), load_bundle(db)
    warnings = ["bundle %s: missing %s" % (os.path.basename(x["dir"]), ", ".join(x["missing"]))
                for x in (a, b) if x["missing"]]

    def want(n):
        return only in (None, n)
    settings = diff_settings(a["clocky_widget_settings.xml"], b["clocky_widget_settings.xml"]) if want("settings") else None
    widgets = diff_widgets(a["widget.json"], b["widget.json"]) if want("widgets") else None
    meta = diff_meta(a["meta.json"], b["meta.json"]) if want("meta") else None
    logs_b = logs_of(b) if want("logs") else None
    if meta:
        names = {c["field"] for c in meta["changed"]} & {"device.identity", "device.model"}
        if names:
            warnings.append("bundles come from different devices (%s differ)" % ", ".join(sorted(names)))
    same = {
        "settings": None if settings is None else _settings_same(settings),
        "widgets": None if widgets is None else not (widgets["changed"] or widgets["added"] or widgets["removed"]
                                                      or widgets["checks_added"] or widgets["checks_removed"]),
        "meta": None if meta is None else not meta["changed"],
    }
    return {"a": os.path.basename(da), "b": os.path.basename(db), "same": same, "settings": settings,
            "widgets": widgets, "meta": meta, "logs_b": logs_b, "warnings": warnings}


def diff_paths(res):
    """Settings JSON paths that differ (values are never included)."""
    s = res.get("settings") or {}
    return [c["path"] for c in s.get("changed", [])] + list(s.get("added", [])) + list(s.get("removed", []))


def failed_expectations(res, which):
    """`--expect-same settings|all`: names that are not provably identical (missing data counts as a failure)."""
    keys = ["settings"] if which == "settings" else ["settings", "widgets"]
    return [k for k in keys if res["same"].get(k) is not True]


# ----------------------------------------------------------------- rendering

def _short(v):
    s = json.dumps(v, ensure_ascii=False)
    return s if len(s) <= 60 else s[:57] + "..."


def _id(v):
    return v[:12] if isinstance(v, str) and len(v) > 40 else v


def _tri(v):
    return {True: "present", False: "none", None: "?"}[v]


def render_lines(res):
    lines = ["compare %s -> %s" % (res["a"], res["b"])]
    s = res["settings"]
    if s is None:
        lines.append("settings  unavailable")
    elif s["byte_identical"]:
        lines.append("settings  SAME (byte-identical)")
    else:
        n = len(s["changed"]) + len(s["added"]) + len(s["removed"])
        lines.append("settings  %s" % ("DIFFERENT %d path(s)" % n if n else "SAME (semantically)"))
        rows = ["  ~ %s: %s -> %s" % (c["path"], _short(c["a"]), _short(c["b"])) for c in s["changed"]]
        rows += ["  + " + p for p in s["added"]] + ["  - " + p for p in s["removed"]]
        lines += rows[:MAX_SHOWN]
        if len(rows) > MAX_SHOWN:
            lines.append("  ... %d more" % (len(rows) - MAX_SHOWN))
    w = res["widgets"]
    if w is None:
        lines.append("widgets   unavailable")
    else:
        lines.append("widgets   %s  ids %s" % ("SAME" if res["same"]["widgets"] else "DIFFERENT", w["ids"]))
        for k, sign in (("changed", "~"), ("added", "+"), ("removed", "-")):
            for x in w[k][:MAX_SHOWN]:
                lines.append("  %s %s" % (sign, x["path"] if isinstance(x, dict) else x))
        lines += ["  + check " + c for c in w["checks_added"]] + ["  - check " + c for c in w["checks_removed"]]
    m = res["meta"]
    if m is None:
        lines.append("meta      unavailable")
    elif m["changed"]:
        lines.append("meta      " + "; ".join("%s %s -> %s" % (c["field"], _id(c["a"]), _id(c["b"])) for c in m["changed"]))
    else:
        lines.append("meta      SAME")
    lb = res["logs_b"]
    if lb is not None:
        lines.append("logs(B)   CRASH %s, ANR %s, crash log lines %s" % (_tri(lb["crash"]), _tri(lb["anr"]), lb["crash_lines"]))
    return lines
