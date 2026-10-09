"""Evidence bundle: meta.json and the PR-safe summary.md (pure builders)."""
import time

from . import __version__

# summary.md deliberately omits the device serial, other apps' data and raw logs.


def build_meta(label, status, state, session_id, device_epoch, git, launcher, errors, identity=None):
    st = status or {}
    return {
        "label": label,
        "host_time": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "device_epoch": device_epoch,
        "session": session_id,
        "marks": (state or {}).get("marks") or {},
        "device": {"identity": identity, "model": st.get("model"), "sdk": st.get("sdk"),
                   "release": st.get("release"), "fingerprint": st.get("fingerprint"),
                   "locale": st.get("locale"), "size": st.get("size"), "density": st.get("density"),
                   "rotation": st.get("rotation")},
        "clocky": {"installed": st.get("clocky"), "install_record": (state or {}).get("install")},
        "git": git,
        "launcher": launcher,
        "tool_version": __version__,
        "errors": errors,
    }


def _widget_rows(widget_state):
    rows = ["| id | kind | host | size class | settings |", "|---|---|---|---|---|"]
    for w in widget_state.get("widgets", []):
        s = w.get("settings")
        if s is None:
            sd = "none"
        elif isinstance(s, dict) and "unavailable" in s:
            sd = "unavailable"
        elif isinstance(s, dict) and "decode_error" in s:
            sd = "decode error"
        else:
            sd = "schema %s" % (s.get("schema") if isinstance(s, dict) else "?")
        sc = w.get("size_class", "unknown")
        if w.get("size_source") not in (None, "options"):
            sc += " (est.)"
        rows.append("| %s | %s | %s | %s | %s |" % (w["id"], w.get("kind"), w.get("host"), sc, sd))
    if len(rows) == 2:
        rows.append("| - | no Clocky widgets placed | | | |")
    return rows


def build_summary_md(meta, widget_state, log_lines, files, log_gaps=None):
    d, c = meta["device"], meta["clocky"].get("installed") or {}
    g = meta.get("git") or {}
    out = ["# cdev evidence: %s" % meta["label"], ""]
    out.append("- device: %s, Android %s (API %s), locale %s, %s dpi, rotation %s" % (
        d.get("model"), d.get("release"), d.get("sdk"), d.get("locale"), d.get("density"), d.get("rotation")))
    out.append("- Clocky: versionName %s, versionCode %s, last updated %s%s" % (
        c.get("versionName"), c.get("versionCode"), c.get("lastUpdateTime"),
        " (debuggable)" if c.get("debuggable") else ""))
    rec = meta["clocky"].get("install_record") or {}
    if rec.get("sha256"):
        out.append("- installed APK sha256: `%s`" % rec["sha256"])
        if "source_commit" in rec:
            out.append("- installed APK source commit: `%s`" % (rec["source_commit"] or "UNKNOWN"))
    out.append("- repo: `%s`%s on branch `%s`" % ((g.get("head") or "?")[:10], " (dirty)" if g.get("dirty") else "",
                                                 g.get("branch")))
    out.append("- launcher: %s" % ((meta.get("launcher") or {}).get("package") or "?"))
    out.append("- session %s, captured %s, cdev %s" % (meta.get("session"), meta["host_time"], meta["tool_version"]))
    out += ["", "## Widgets", ""] + _widget_rows(widget_state)
    if widget_state.get("host_note"):
        out.append("")
        out.append("_%s_" % widget_state["host_note"])
    out += ["", "## Checks", ""]
    checks = widget_state.get("checks") or []
    out += ["- none"] if not checks else ["- `%s` id=%s %s" % (c["check"], c.get("id"), c.get("note", "")) for c in checks]
    out += ["", "## Clocky log summary (since prepare/mark, Clocky-filtered)", "", "```"] + list(log_lines) + ["```"]
    if meta.get("errors"):
        out += ["", "## Collection errors", ""] + ["- %s: %s" % (e["part"], e["error"]) for e in meta["errors"]]
    out += ["", "## Files (local only, may contain personal data: do not attach except this summary)", ""]
    out += ["- `%s`" % f for f in sorted(files)]
    return "\n".join(out) + "\n"


def build_compare_md(label, res, paths):
    """summary.md section: SAME/DIFFERENT and JSON paths only (never values)."""
    sm = res.get("same") or {}
    word = lambda v: "unavailable" if v is None else ("SAME" if v else "DIFFERENT")  # noqa: E731
    out = ["", "## Compared to %s" % label, ""]
    if sm.get("settings") is False:
        shown = paths[:20]
        out.append("- settings: %d path(s) differ: %s%s" % (len(paths), ", ".join(shown), ", ..." if len(paths) > 20 else ""))
    else:
        out.append("- settings: %s" % word(sm.get("settings")))
    out.append("- widgets: %s" % word(sm.get("widgets")))
    return "\n".join(out) + "\n"
