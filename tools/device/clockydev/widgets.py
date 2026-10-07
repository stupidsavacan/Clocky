"""Clocky widget inspection: dumpsys appwidget, prefs XML/JSON, size class, host-view location."""
import json
import re
import unicodedata
import xml.etree.ElementTree as ET

from . import constants as C

# ----------------------------------------------------- TypedValue complex dims

_UNITS = {0: "px", 1: "dp", 2: "sp", 3: "pt", 4: "in", 5: "mm"}
_RADIX_MULT = (1.0 / (1 << 8), 1.0 / (1 << 15), 1.0 / (1 << 23), 1.0 / (1 << 31))


def decode_complex(v):
    """Android TypedValue complex dimension -> (value, unit). 64001 -> (250.0, 'dp')."""
    unit = v & 0xF
    radix = (v >> 4) & 3
    mant = v & 0xFFFFFF00           # as in TypedValue.complexToFloat: mantissa stays shifted
    if mant & 0x80000000:
        mant -= 0x100000000
    return mant * _RADIX_MULT[radix], _UNITS.get(unit, "unit%d" % unit)


def _dp(v):
    val, unit = decode_complex(v)
    val = round(val, 2)
    return val if unit == "dp" else {"value": val, "unit": unit}


# ------------------------------------------------------ dumpsys appwidget

_SECTION_RE = re.compile(r"^(Providers|Widgets|Hosts|Grants):\s*$", re.M)
_ENTRY_RE = re.compile(r"^  \[(\d+)\] (.*)$", re.M)
_CMP_RE = re.compile(r"ComponentInfo\{([^/}]+)/([^}]+)\}")


def _sections(text):
    marks = [(m.group(1), m.end()) for m in _SECTION_RE.finditer(text)]
    out = {}
    for i, (name, end) in enumerate(marks):
        nxt = _SECTION_RE.search(text, end)
        out[name] = text[end: nxt.start() if nxt else len(text)]
    return out


def _entries(body):
    ms = list(_ENTRY_RE.finditer(body))
    for i, m in enumerate(ms):
        yield m.group(2), body[m.end(): ms[i + 1].start() if i + 1 < len(ms) else len(body)]


def parse_options(block):
    """-> dict of dp options, {'unavailable': reason} or None when there is no options line."""
    m = re.search(r"options=(Bundle\[.*\])", block)
    if not m:
        return None
    b = m.group(1)
    if "mParcelledData" in b or "dataSize" in b:
        return {"unavailable": "parcelled Bundle (not expanded by dumpsys)"}
    opts = {}
    for k, v in re.findall(r"(appWidget(?:Min|Max)(?:Width|Height))=(-?\d+)", b):
        opts[k] = int(v)
    m = re.search(r"appWidgetCategory=(\d+)", b)
    if m:
        opts["appWidgetCategory"] = int(m.group(1))
    return opts or {"unavailable": "no size keys in Bundle"}


def parse_appwidget(text):
    sec = _sections(text)
    providers = []
    for head, body in _entries(sec.get("Providers", "")):
        m = _CMP_RE.search(head)
        if not m:
            continue
        p = {"package": m.group(1), "class": m.group(2)}
        dm = re.search(r"min=\((-?\d+)x(-?\d+)\)\s+minResize=\((-?\d+)x(-?\d+)\)", body)
        if dm:
            a, b, c, d = (int(x) for x in dm.groups())
            p["min_dp"] = [_dp(a), _dp(b)]
            p["min_resize_dp"] = [_dp(c), _dp(d)]
        for key, rx in (("resizeMode", r"resizeMode=(\d+)"), ("widgetCategory", r"widgetCategory=(\d+)")):
            mm = re.search(rx, body)
            if mm:
                p[key] = int(mm.group(1))
        p["zombie"] = bool(re.search(r"zombie=true", body))
        providers.append(p)
    widgets = []
    for head, body in _entries(sec.get("Widgets", "")):
        mid = re.match(r"id=(\d+)", head)
        if not mid:
            continue
        w = {"id": int(mid.group(1))}
        hm = re.search(r"host=HostId\{[^}]*pkg:([^,}\s]+)", body)
        w["host"] = hm.group(1) if hm else None
        pm = _CMP_RE.search(body.split("provider=", 1)[1]) if "provider=" in body else None
        w["provider_package"] = pm.group(1) if pm else None
        w["provider_class"] = pm.group(2) if pm else None
        w["options"] = parse_options(body)
        widgets.append(w)
    return {"providers": providers, "widgets": widgets}


def clocky_view(parsed):
    """Filter to the Clocky providers/widgets, tagging digital/analog."""
    def kind(cls):
        return "digital" if cls == C.DIGITAL_PROVIDER else "analog" if cls == C.ANALOG_PROVIDER else "other"
    provs = []
    for p in parsed["providers"]:
        if p["package"] == C.PACKAGE:
            provs.append(dict(p, kind=kind(p["class"])))
    ws = []
    for w in parsed["widgets"]:
        if w["provider_package"] == C.PACKAGE:
            ws.append(dict(w, kind=kind(w["provider_class"])))
    return provs, ws


# ----------------------------------------------------------- settings prefs

def parse_prefs(xml_text):
    """prefs XML -> {widget_id: {'raw':str, 'json':obj|None, 'decode_error':str|None}}."""
    out = {}
    if not xml_text.strip():
        return out
    root = ET.fromstring(xml_text)
    rx = re.compile(C.PREFS_KEY_RE)
    for el in root.findall("string"):
        m = rx.match(el.get("name", ""))
        if not m:
            continue
        raw = el.text or ""
        entry = {"raw": raw, "json": None, "decode_error": None}
        try:
            entry["json"] = json.loads(raw)
        except ValueError as e:
            entry["decode_error"] = str(e)
        out[int(m.group(1))] = entry
    return out


def parse_prefs_all(xml_text):
    """prefs XML -> {key: (type, value)} for every entry; widget.N.settings values are JSON-decoded when possible."""
    out = {}
    if not xml_text.strip():
        return out
    root = ET.fromstring(xml_text)
    rx = re.compile(C.PREFS_KEY_RE)
    for el in root:
        name = el.get("name")
        if not name:
            continue
        if el.tag == "string":
            val = el.text or ""
            if rx.match(name):
                try:
                    val = json.loads(val)
                except ValueError:
                    pass
        elif el.tag == "set":
            val = sorted((c.text or "") for c in el)
        else:
            val = el.get("value")
        out[name] = (el.tag, val)
    return out


def summarize_settings(entry):
    """Schema-tolerant summary; absent keys are omitted."""
    if entry.get("decode_error"):
        return {"decode_error": entry["decode_error"], "raw_prefix": entry["raw"][:80]}
    d = entry["json"]
    if not isinstance(d, dict):
        return {"decode_error": "not a JSON object"}
    s = {}
    for k in ("schema", "origin", "presetId"):
        if k in d:
            s[k] = d[k]
    pick = {
        "time": ("fontFamily", "font", "weight", "sizeSp", "argb", "color", "opacity", "alignment", "hourMode"),
        "date": ("enabled", "visible", "formatPattern", "fontFamily", "font", "weight", "sizeSp"),
        "background": ("type", "argb", "color", "opacity", "cornerRadiusDp", "cornerRadius"),
        "behavior": ("hourMode", "amPm", "showSeconds"),
    }
    for sect, keys in pick.items():
        v = d.get(sect)
        if isinstance(v, dict):
            sub = {k: v[k] for k in keys if k in v}
            if sub:
                s[sect] = sub
    lay = d.get("layout")
    if isinstance(lay, dict):
        ls = {}
        if "template" in lay:
            ls["template"] = lay["template"]
        ov = lay.get("overrides")
        if isinstance(ov, dict):
            ls["overrides"] = {k: sorted(v.keys()) if isinstance(v, dict) else v for k, v in ov.items()}
        if ls:
            s["layout"] = ls
    for k in ("fourByTwo", "fourByOne"):
        if k in d:
            s[k] = "set" if d[k] else None
    return s


# ---------------------------------------------------------------- size class

def size_class(min_height_dp):
    """Tool replica of SizeClassResolver: 0 < minH < 100 -> Strip; otherwise Card; None -> unknown."""
    if min_height_dp is None:
        return "unknown"
    return "Strip" if 0 < min_height_dp < C.STRIP_MAX_DP else "Card"


SIZE_RULE = "tool replica of SizeClassResolver (0<minH<100)"


# ------------------------------------------------------------ host-view UI

def locate_hosts(nodes, density):
    """Find Clocky widget host views in a launcher UI dump (pure)."""
    out = []
    for n in nodes:
        if not n.is_host:
            continue
        inner = [d for d in nodes if d is not n and d.pkg == C.PACKAGE and _is_descendant(d, n)]
        has_root = any(d.rid_short == "clocky_widget_root" for d in inner)
        if not inner and not has_root:
            continue
        top = [d for d in inner if d.parent is n]
        ref = top[0] if top else n
        l, t, r, b = n.bounds
        il, it, ir, ib = ref.bounds
        scale = 160.0 / density if density else None
        out.append({
            "label": n.desc or n.text, "bounds": [l, t, r, b], "center": list(n.center),
            "size_dp": [round((r - l) * scale, 1), round((b - t) * scale, 1)] if scale else None,
            "inner_size_dp": [round((ir - il) * scale, 1), round((ib - it) * scale, 1)] if scale else None,
            "has_clocky_widget_root": has_root,
        })
    return out


def _is_descendant(d, anc):
    p = d.parent
    while p is not None:
        if p is anc:
            return True
        p = p.parent
    return False


# --------------------------------------------------------------------- checks

def build_checks(widgets, prefs, prefs_available):
    """Consistency checks between placed widgets and stored settings."""
    checks = []
    ids = {w["id"] for w in widgets}
    if prefs_available:
        for w in widgets:
            if w["id"] not in prefs:
                checks.append({"check": "bound_without_settings", "id": w["id"],
                               "note": "widget placed but no settings saved (cancelled config, or not saved yet)"})
        for i in sorted(set(prefs) - ids):
            checks.append({"check": "settings_without_widget", "id": i,
                           "note": "orphan settings (widget deleted without cleanup?)"})
        for i, e in sorted(prefs.items()):
            sch = e["json"].get("schema") if isinstance(e.get("json"), dict) else None
            if sch is not None and sch != 2:
                checks.append({"check": "schema!=2", "id": i, "schema": sch,
                               "note": "migration to schema 2 has not run (old build or never re-saved)"})
            if e.get("decode_error"):
                checks.append({"check": "settings_decode_error", "id": i, "note": e["decode_error"]})
    for w in widgets:
        if w.get("zombie"):
            checks.append({"check": "zombie", "id": w["id"]})
    return checks


# -------------------------------------------------------------- ticking time

_TIME_RE = re.compile(C.TIME_TEXT_RE)


def normalize_time_text(text):
    """Unicode digits -> ASCII, fullwidth colon -> ':'. Returns the normalized string."""
    out = []
    for ch in unicodedata.normalize("NFC", (text or "").strip()):
        if ch in "：﹕∶":
            out.append(":")
        elif ch.isdigit():
            try:
                out.append(str(unicodedata.digit(ch)))
            except ValueError:
                out.append(ch)
        else:
            out.append(ch)
    return "".join(out)


def is_time_text(text):
    return bool(_TIME_RE.match(normalize_time_text(text)))


def find_time_nodes(nodes):
    """One entry per Clocky host view: {'host': node, 'time': node|None, 'text': str|None}.

    The time node is the Clocky TextView under the host whose label looks like a clock (H:MM[:SS]); when several
    qualify (e.g. seconds shown) the one with the largest area wins. Hosts are ordered by (top, left).
    """
    out = []
    for h in nodes:
        if not h.is_host:
            continue
        inner = [d for d in nodes if d is not h and d.pkg == C.PACKAGE and _is_descendant(d, h)]
        if not inner:
            continue
        cands = [d for d in inner if (d.cls or "").endswith("TextView") and is_time_text(d.label)]
        best = max(cands, key=lambda d: d.area) if cands else None
        out.append({"host": h, "time": best, "text": normalize_time_text(best.label) if best else None})
    out.sort(key=lambda e: (e["host"].bounds[1], e["host"].bounds[0]))
    return out
