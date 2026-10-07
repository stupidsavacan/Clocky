"""uiautomator dump -> Node list, selectors, dedupe, compact rendering.

Everything except `dump_xml` is pure and unit-tested against real dumps.
"""
import itertools
import os
import re
import time
import unicodedata
import xml.etree.ElementTree as ET

from . import constants as C
from .errors import adb_err, ui_err, usage

_BOUNDS_RE = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
_counter = itertools.count(1)


def norm(s):
    """NFC, trim, collapse internal whitespace."""
    return re.sub(r"\s+", " ", unicodedata.normalize("NFC", s or "")).strip()


class Node:
    __slots__ = ("path", "text", "desc", "rid", "cls", "pkg", "bounds", "clickable", "long_clickable",
                 "scrollable", "checked", "enabled", "focused", "selected", "parent", "depth")

    def __init__(self, **kw):
        for k in self.__slots__:
            setattr(self, k, kw.get(k))

    @property
    def label(self):
        return self.text or self.desc or ""

    @property
    def rid_short(self):
        return self.rid.split(":id/", 1)[1] if ":id/" in (self.rid or "") else (self.rid or "")

    @property
    def center(self):
        l, t, r, b = self.bounds
        return ((l + r) // 2, (t + b) // 2)

    @property
    def area(self):
        l, t, r, b = self.bounds
        return max(0, r - l) * max(0, b - t)

    @property
    def is_host(self):
        return (self.cls or "").endswith("AppWidgetHostView")

    def flags(self):
        f = ""
        for ch, on in (("C", self.clickable), ("L", self.long_clickable), ("S", self.scrollable),
                       ("K", self.checked), ("W", self.is_host), ("F", self.focused)):
            f += ch if on else ""
        return f or "-"

    def brief(self, idx=None):
        d = {"label": self.label, "id": self.rid or None, "class": self.cls, "package": self.pkg,
             "bounds": list(self.bounds), "center": list(self.center), "flags": self.flags()}
        if idx is not None:
            d["index"] = idx
        return d


def parse_bounds(s):
    m = _BOUNDS_RE.fullmatch(s or "")
    return tuple(int(x) for x in m.groups()) if m else (0, 0, 0, 0)


def parse_hierarchy(xml_text):
    """Return (nodes in document order, screen (w, h)). Raises ValueError on invalid XML."""
    root = ET.fromstring(xml_text.encode("utf-8") if isinstance(xml_text, str) else xml_text)
    nodes = []

    def walk(el, parent, depth, path):
        for i, ch in enumerate(el.findall("node")):
            a = ch.attrib
            n = Node(path=path + (i,), text=a.get("text", ""), desc=a.get("content-desc", ""),
                     rid=a.get("resource-id", ""), cls=a.get("class", ""), pkg=a.get("package", ""),
                     bounds=parse_bounds(a.get("bounds")), clickable=a.get("clickable") == "true",
                     long_clickable=a.get("long-clickable") == "true", scrollable=a.get("scrollable") == "true",
                     checked=a.get("checked") == "true", enabled=a.get("enabled") != "false",
                     focused=a.get("focused") == "true", selected=a.get("selected") == "true",
                     parent=parent, depth=depth)
            nodes.append(n)
            walk(ch, n, depth + 1, n.path)

    walk(root, None, 0, ())
    screen = (0, 0)
    if nodes:
        screen = (max(n.bounds[2] for n in nodes[:1]), max(n.bounds[3] for n in nodes[:1]))
    return nodes, screen


def visible(nodes, screen):
    """Drop zero-area nodes and nodes whose center is outside the screen."""
    w, h = screen
    out = []
    for n in nodes:
        if n.area <= 0:
            continue
        cx, cy = n.center
        if w and h and not (0 <= cx < w and 0 <= cy < h):
            continue
        if _clipped_by_scroller(n, cx, cy):
            continue
        out.append(n)
    return out


def _clipped_by_scroller(n, cx, cy):
    """True if an enclosing scrollable viewport does not contain the node center (it is scrolled out of view)."""
    p = n.parent
    while p is not None:
        if p.scrollable:
            l, t, r, b = p.bounds
            if not (l <= cx < r and t <= cy < b):
                return True
        p = p.parent
    return False


def dedupe(nodes):
    """Collapse nodes with identical bounds and label (e.g. text/desc duplicates)."""
    seen, out = set(), []
    for n in nodes:
        key = (n.bounds, norm(n.label), n.rid)
        if key in seen:
            continue
        seen.add(key)
        out.append(n)
    return out


class Selector:
    def __init__(self, label=None, text=None, desc=None, id=None, cls=None, pkg=None, contains=False, index=None):
        self.label, self.text, self.desc, self.id = label, text, desc, id
        self.cls, self.pkg, self.contains, self.index = cls, pkg, contains, index

    def is_empty(self):
        return not any([self.label, self.text, self.desc, self.id, self.cls, self.pkg])

    def describe(self):
        parts = []
        for k in ("label", "text", "desc", "id", "cls", "pkg"):
            v = getattr(self, k)
            if v:
                parts.append("%s=%r" % (k, v))
        return " ".join(parts) + (" (contains)" if self.contains else "")

    def _eq(self, want, have):
        want, have = norm(want), norm(have)
        return want in have if self.contains else want == have

    def matches(self, n):
        if self.label and not (self._eq(self.label, n.text) or self._eq(self.label, n.desc)):
            return False
        if self.text and not self._eq(self.text, n.text):
            return False
        if self.desc and not self._eq(self.desc, n.desc):
            return False
        if self.id:
            ok = n.rid == self.id or n.rid_short == self.id or n.rid.endswith(":id/" + self.id)
            if not ok:
                return False
        if self.cls and not (n.cls == self.cls or n.cls.endswith("." + self.cls)):
            return False
        if self.pkg and n.pkg != self.pkg:
            return False
        return True


def find(nodes, sel):
    """Matches in document order, deduped by bounds (prefer a clickable duplicate)."""
    ms = [n for n in nodes if sel.matches(n)]
    by_bounds, order = {}, []
    for n in ms:
        k = n.bounds
        if k not in by_bounds:
            by_bounds[k] = n
            order.append(k)
        elif n.clickable and not by_bounds[k].clickable:
            by_bounds[k] = n
    return [by_bounds[k] for k in order]


def host_name(n):
    p = n.parent
    while p is not None:
        if p.is_host:
            return p.desc or p.cls
        p = p.parent
    return None


def clickable_ancestor(n):
    p = n
    while p is not None:
        if p.clickable:
            return p
        p = p.parent
    return None


def candidates(ms):
    out = []
    for i, n in enumerate(ms):
        d = n.brief(i)
        d["host"] = host_name(n)
        out.append(d)
    return out


def pick(nodes, sel, what="target"):
    """Return exactly one node or raise (exit 4 not found / exit 2 ambiguous)."""
    if sel.is_empty():
        raise usage("NO_SELECTOR", "no selector given for %s" % what,
                    "Give a LABEL, --text/--desc/--id/--class, or --xy X Y.")
    ms = find(nodes, sel)
    if not ms:
        near = near_misses(nodes, sel)
        hint = "Run `cdev inspect` to see what is on screen; try --contains, --scroll, --wait N, or --xy."
        if near:
            hint = "did you mean %s? (re-run with that exact text or --contains). %s" % (
                " / ".join(repr(n.label) for n in near[:3]), hint)
        raise ui_err("NOT_FOUND", "no node matches %s" % sel.describe(), hint, candidates(near))
    if sel.index is not None:
        if not 0 <= sel.index < len(ms):
            raise usage("INDEX_OUT_OF_RANGE", "--index %d but only %d match(es)" % (sel.index, len(ms)),
                        "Use an index between 0 and %d." % (len(ms) - 1), candidates(ms))
        return ms[sel.index], ms
    if len(ms) > 1:
        raise usage("AMBIGUOUS_MATCH", "%d nodes match %s; refusing to guess" % (len(ms), sel.describe()),
                    "Re-run with --index N (see candidates), or narrow with --id/--class/--package.",
                    candidates(ms))
    return ms[0], ms


def content_hash(nodes):
    return hash(tuple((n.path, n.bounds, n.text, n.desc) for n in nodes))


def short(s, n):
    s = s or ""
    return s if len(s) <= n else s[: n - 1] + "~"


def render_table(nodes, only_interesting=True, limit=None):
    """Compact human table. Returns list of lines."""
    rows = []
    for i, n in enumerate(nodes):
        interesting = bool(n.label or n.is_host or n.scrollable or (n.clickable and n.rid))
        if only_interesting and not interesting:
            continue
        rows.append((i, n))
    lines = ["   n label                        id                                     class                     bounds              flags"]
    for i, n in rows[:limit]:
        b = "[%d,%d][%d,%d]" % n.bounds
        lines.append("%4d %-28s %-38s %-25s %-19s %s" % (
            i, short(n.label, 28) or "-", short(n.rid_short, 38) or "-", short((n.cls or "").rsplit(".", 1)[-1], 25), b, n.flags()))
    lines.append("(%d shown of %d nodes; use --all for every node; --index N counts only MATCHES of a selector, not this column)"
                 % (len(rows[:limit]), len(nodes)))
    return lines


def render_text(nodes):
    """Full text dump for ui.txt."""
    lines = []
    for n in nodes:
        lines.append("%s%s | label=%r id=%s cls=%s pkg=%s bounds=[%d,%d][%d,%d] %s" % (
            "  " * (n.depth or 0), ".".join(map(str, n.path)), n.label, n.rid or "-", n.cls, n.pkg,
            n.bounds[0], n.bounds[1], n.bounds[2], n.bounds[3], n.flags()))
    return "\n".join(lines) + "\n"


def orientation(node):
    """'horizontal' for HorizontalScrollView / ViewPager, otherwise 'vertical' (class name only; no guessing)."""
    c = node.cls or ""
    return "horizontal" if ("HorizontalScrollView" in c or "ViewPager" in c) else "vertical"


def scroll_candidates(nodes):
    """Visible scrollable containers, vertical first (area desc), then horizontal (area desc)."""
    cand = [n for n in nodes if n.scrollable and n.area > 0]
    key = lambda n: -n.area  # noqa: E731
    return (sorted([n for n in cand if orientation(n) == "vertical"], key=key)
            + sorted([n for n in cand if orientation(n) == "horizontal"], key=key))


def all_clocky_scrollables(nodes):
    """True when there is at least one scrollable and every visible scrollable belongs to Clocky."""
    sc = [n for n in nodes if n.scrollable]
    return bool(sc) and all(n.pkg == C.PACKAGE for n in sc)


_MARKS = "•・·●*★ "


def _bare(s):
    return norm(s).strip(_MARKS)


def near_misses(nodes, sel, limit=5):
    """Visible nodes whose label nearly equals the selector text (contains / contained-by / marker-stripped)."""
    want = norm(sel.label or sel.text or sel.desc or "")
    if len(want) < 1:
        return []
    bw = _bare(want)
    out = []
    for n in nodes:
        for have in (n.text, n.desc):
            h = norm(have)
            if not h or h == want:
                continue
            bh = _bare(h)
            if want in h or (len(h) >= 2 and h in want) or (bw and bw == bh):
                out.append(n)
                break
    return out[:limit]


def _ancestor_rid(n):
    p = n.parent
    while p is not None:
        if p.rid_short:
            return p.rid_short
        p = p.parent
    return ""


def _keyed(nodes):
    """Stable identity per node: (resource id, class) or (label, class, nearest ancestor id), plus an occurrence index."""
    seen, out = {}, {}
    for n in nodes:
        base = (n.rid_short, n.cls) if n.rid_short else (norm(n.label), n.cls, _ancestor_rid(n))
        k = seen.get(base, 0)
        seen[base] = k + 1
        out[(base, k)] = n
    return out


def diff_nodes(prev, cur):
    """Compare two visible-node lists -> {added, removed, changed: [(old, new)], bounds_only: int, overlap: float}."""
    a, b = _keyed(prev), _keyed(cur)
    added = [b[k] for k in b if k not in a]
    removed = [a[k] for k in a if k not in b]
    changed, bounds_only = [], 0
    for k in a:
        if k in b:
            o, n = a[k], b[k]
            if norm(o.label) != norm(n.label) or o.flags() != n.flags():
                changed.append((o, n))
            elif o.bounds != n.bounds:
                bounds_only += 1
    both = len(set(a) & set(b))
    overlap = both / float(max(1, max(len(a), len(b))))
    return {"added": added, "removed": removed, "changed": changed, "bounds_only": bounds_only, "overlap": overlap}


def interesting(n):
    return bool(n.label or n.is_host or n.scrollable or (n.clickable and n.rid))


def render_diff(d):
    """Human lines for diff_nodes output (only nodes worth showing, like the table)."""
    def row(sign, n):
        return "%s %-28s %-38s %s %s" % (sign, short(n.label, 28) or "-", short(n.rid_short, 38) or "-",
                                         "[%d,%d][%d,%d]" % n.bounds, n.flags())
    lines = [row("+", n) for n in d["added"] if interesting(n)]
    lines += [row("-", n) for n in d["removed"] if interesting(n)]
    for o, n in d["changed"]:
        if interesting(o) or interesting(n):
            lines.append("~ %s -> %s  id=%s flags %s -> %s" % (short(o.label, 28) or "-", short(n.label, 28) or "-",
                                                              short(n.rid_short, 30) or "-", o.flags(), n.flags()))
    lines.append("(%d added, %d removed, %d changed, %d moved/resized only)" % (
        len(d["added"]), len(d["removed"]), len(d["changed"]), d["bounds_only"]))
    return lines


def largest_scrollable(nodes, within=None):
    cand = [n for n in nodes if n.scrollable and (within is None or n in within)]
    return max(cand, key=lambda n: n.area) if cand else None


# ------------------------------------------------------------ device dump

def tmp_name(ext="xml"):
    return "%s/%s%d-%d.%s" % (C.DEVICE_TMP_DIR, C.DEVICE_TMP_PREFIX, os.getpid(), next(_counter), ext)


def dump_xml(adb, retries=3, timeout=25, sleep=time.sleep):
    """Dump the UI hierarchy; returns xml text. Temp file is always removed."""
    last = "no output"
    for attempt in range(retries):
        path = tmp_name("xml")
        try:
            r = adb.shell("uiautomator dump %s" % path, timeout=timeout)
            msg = (r.text + r.err_text).strip()
            if r.rc == 0 and "ERROR" not in msg and "null root node" not in msg:
                x = adb.exec_out("cat %s" % path, timeout=15)
                text = x.text if hasattr(x, "text") else x.out.decode("utf-8", "replace")
                if text.strip().startswith("<?xml") or text.strip().startswith("<hierarchy"):
                    try:
                        ET.fromstring(text.encode("utf-8"))
                        return text
                    except ET.ParseError as e:
                        last = "invalid XML: %s" % e
                else:
                    last = "empty dump"
            else:
                last = msg[:200] or "rc=%s" % r.rc
        finally:
            adb.shell("rm -f %s" % path, timeout=10)
        if attempt + 1 < retries:
            sleep(1.0)
    raise ui_err("DUMP_FAILED", "uiautomator dump failed after %d tries (%s)" % (retries, last),
                 "The screen may be animating or secure. Look at the screenshot and use `tap --xy X Y`.")


def screenshot(adb, timeout=20):
    r = adb.exec_out("screencap -p", timeout=timeout)
    if not r.out.startswith(b"\x89PNG"):
        raise adb_err("SCREENCAP_FAILED", "screencap did not return a PNG", "Is the screen on and unlocked?")
    return r.out


def save_small(png_path, width=360):
    """Write a width-limited copy next to png_path. Returns path or None when Pillow is unavailable."""
    try:
        from PIL import Image
    except ImportError:
        return None
    out = png_path[:-4] + "_small.png"
    im = Image.open(png_path)
    h = max(1, int(im.height * width / im.width))
    im.resize((width, h)).save(out)
    return out
