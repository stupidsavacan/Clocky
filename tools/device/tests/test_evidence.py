import unittest

from clockydev import evidence as E

STATUS = {"model": "moto g13", "sdk": 34, "release": "14", "fingerprint": "fp", "locale": "ja-JP", "size": [720, 1600],
          "density": 280, "rotation": 0,
          "clocky": {"versionName": "0.2.0", "versionCode": 2, "lastUpdateTime": "2026-10-06 19:00:32", "debuggable": True}}
STATE = {"marks": {"prepare": 1.0}, "install": {"sha256": "abc123"}}
GIT = {"head": "a1bc77ae85aaaa", "branch": "tools/device-layer", "dirty": False}
WIDGETS = {"widgets": [{"id": 17, "kind": "digital", "host": "com.motorola.launcher3", "size_class": "Card",
                        "size_source": "options", "settings": {"schema": 2}}],
           "checks": [], "host_note": None}

GOLDEN = """# cdev evidence: after-save

- device: moto g13, Android 14 (API 34), locale ja-JP, 280 dpi, rotation 0
- Clocky: versionName 0.2.0, versionCode 2, last updated 2026-10-06 19:00:32 (debuggable)
- installed APK sha256: `abc123`
- repo: `a1bc77ae85` on branch `tools/device-layer`
- launcher: com.motorola.launcher3
- session s-1, captured HOSTTIME, cdev 1.0.0

## Widgets

| id | kind | host | size class | settings |
|---|---|---|---|---|
| 17 | digital | com.motorola.launcher3 | Card | schema 2 |

## Checks

- none

## Clocky log summary (since prepare/mark, Clocky-filtered)

```
CRASH  none
```

## Files (local only, may contain personal data: do not attach except this summary)

- `meta.json`
- `summary.md`
"""


def meta(errors=None):
    return E.build_meta("after-save", STATUS, STATE, "s-1", 123.0, GIT, {"package": "com.motorola.launcher3"},
                        errors or [], "SERIALX")


class Evidence(unittest.TestCase):
    def test_meta_required_keys(self):
        m = meta()
        for k in ("label", "host_time", "device_epoch", "session", "marks", "device", "clocky", "git", "launcher",
                  "tool_version", "errors"):
            self.assertIn(k, m)
        self.assertEqual(m["git"]["head"], "a1bc77ae85aaaa")
        self.assertEqual(m["clocky"]["installed"]["versionCode"], 2)

    def test_summary_golden_and_privacy(self):
        m = meta()
        md = E.build_summary_md(m, WIDGETS, ["CRASH  none"], ["meta.json", "summary.md"])
        self.assertEqual(md, GOLDEN.replace("HOSTTIME", m["host_time"]))
        self.assertNotIn("SERIALX", md)       # device serial must never reach the PR-safe summary

    def test_partial_failure_listed(self):
        md = E.build_summary_md(meta([{"part": "ui_dump", "error": "DUMP_FAILED: x"}]), {"widgets": [], "checks": []}, [], [])
        self.assertIn("## Collection errors", md)
        self.assertIn("- ui_dump: DUMP_FAILED: x", md)
        self.assertIn("no Clocky widgets placed", md)

    def test_estimated_size_marked(self):
        w = {"widgets": [dict(WIDGETS["widgets"][0], size_class="Strip", size_source="estimated from launcher host-view bounds")],
             "checks": [{"check": "schema!=2", "id": 17, "note": "n"}]}
        md = E.build_summary_md(meta(), w, [], [])
        self.assertIn("Strip (est.)", md)
        self.assertIn("`schema!=2` id=17", md)


if __name__ == "__main__":
    unittest.main()
