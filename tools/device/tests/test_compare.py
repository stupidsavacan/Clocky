import os
import shutil
import tempfile
import unittest

from clockydev import compare as K
from clockydev import widgets as W
from clockydev.errors import CdevError
from tests.helpers import FIX

B = os.path.join(FIX, "bundles")


def bundle(name):
    return os.path.join(B, name)


class TestDiff(unittest.TestCase):
    def test_moto_restored_is_byte_identical(self):
        r = K.compare_bundles(bundle("moto_baseline"), bundle("moto_restored"))
        self.assertTrue(r["settings"]["byte_identical"])
        self.assertTrue(r["same"]["settings"] and r["same"]["widgets"])
        self.assertEqual(K.failed_expectations(r, "all"), [])
        self.assertEqual(r["logs_b"]["crash_lines"], 0)
        self.assertIs(r["logs_b"]["anr"], False)

    def test_api25_only_adds_keys(self):
        r = K.compare_bundles(bundle("api25_pre"), bundle("api25_restored"))
        s = r["settings"]
        self.assertFalse(s["byte_identical"])
        self.assertEqual(s["changed"], [])
        self.assertEqual(s["removed"], [])
        self.assertIn("widget.4.settings.behavior.amPm", s["added"])
        self.assertEqual(K.failed_expectations(r, "settings"), ["settings"])
        self.assertEqual(K.diff_paths(r), s["added"])

    def test_json_diff_changed_added_removed(self):
        a = {"x": {"mode": "A", "n": 1}, "gone": 1, "l": [1, 2]}
        b = {"x": {"mode": "B", "n": 1, "new": True}, "l": [1, 2, 3]}
        ch, ad, rm = K.json_diff(a, b)
        self.assertEqual(ch, [{"path": "x.mode", "a": "A", "b": "B"}])
        self.assertEqual(sorted(ad), ["l[2]", "x.new"])
        self.assertEqual(rm, ["gone"])

    def test_int_vs_float_type_change_is_a_diff(self):
        ch, _a, _r = K.json_diff({"v": 1}, {"v": 1.0})
        self.assertEqual(len(ch), 1)

    def test_non_widget_keys_and_types(self):
        xa = "<map><string name='other'>a</string><boolean name='flag' value='true'/></map>"
        xb = "<map><string name='other'>b</string><boolean name='flag' value='true'/><int name='n' value='3'/></map>"
        s = K.diff_settings(xa, xb)
        self.assertEqual([c["path"] for c in s["changed"]], ["other"])
        self.assertEqual(s["added"], ["n"])

    def test_meta_other_device_warns(self):
        r = K.compare_bundles(bundle("moto_baseline"), bundle("api25_pre"))
        self.assertTrue(any("different devices" in w for w in r["warnings"]))
        fields = {c["field"] for c in r["meta"]["changed"]}
        self.assertIn("device.model", fields)

    def test_missing_files_are_warned_and_unavailable(self):
        with tempfile.TemporaryDirectory() as t:
            r = K.compare_bundles(t, bundle("moto_baseline"))
        self.assertIsNone(r["settings"])
        self.assertTrue(r["warnings"])
        self.assertEqual(K.failed_expectations(r, "settings"), ["settings"])

    def test_render_caps_lines(self):
        xa = "<map></map>"
        xb = "<map>" + "".join("<int name='k%d' value='1'/>" % i for i in range(30)) + "</map>"
        res = {"a": "A", "b": "B", "settings": K.diff_settings(xa, xb), "widgets": None, "meta": None, "logs_b": None,
               "same": {}}
        out = K.render_lines(res)
        self.assertIn("... 10 more", "\n".join(out))

    def test_parse_prefs_all(self):
        p = W.parse_prefs_all("<map><string name='widget.7.settings'>{\"a\":1}</string><string name='z'>q</string>"
                              "<set name='s'><string>b</string><string>a</string></set></map>")
        self.assertEqual(p["widget.7.settings"], ("string", {"a": 1}))
        self.assertEqual(p["z"], ("string", "q"))
        self.assertEqual(p["s"], ("set", ["a", "b"]))
        self.assertEqual(W.parse_prefs_all(""), {})


class TestResolve(unittest.TestCase):
    def setUp(self):
        self.t = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.t, True)
        for sid, dirs in {"s-1": ["0001-prepare", "0002-collect-base", "0003-collect-after", "0004-collect-after"],
                          "s-2": ["0001-collect-base"]}.items():
            for d in dirs:
                os.makedirs(os.path.join(self.t, sid, d))
        os.utime(os.path.join(self.t, "s-1"), (1, 1))

    def test_label_in_current_session(self):
        self.assertTrue(K.resolve_bundle("base", self.t, "s-1").endswith(os.path.join("s-1", "0002-collect-base")))

    def test_step_dir_name_and_session_prefix(self):
        self.assertTrue(K.resolve_bundle("0003-collect-after", self.t, "s-1").endswith("0003-collect-after"))
        self.assertTrue(K.resolve_bundle("s-2/base", self.t, "s-1").endswith(os.path.join("s-2", "0001-collect-base")))

    def test_latest_session_when_no_current(self):
        self.assertIn("s-2", K.resolve_bundle("base", self.t, None))

    def test_duplicate_label_is_ambiguous(self):
        with self.assertRaises(CdevError) as c:
            K.resolve_bundle("after", self.t, "s-1")
        self.assertEqual((c.exception.exit_code, c.exception.code), (2, "AMBIGUOUS_BUNDLE"))
        self.assertEqual(len(c.exception.candidates), 2)

    def test_not_found(self):
        with self.assertRaises(CdevError) as c:
            K.resolve_bundle("nope", self.t, "s-1")
        self.assertEqual((c.exception.exit_code, c.exception.code), (4, "BUNDLE_NOT_FOUND"))

    def test_path(self):
        self.assertEqual(K.resolve_bundle(bundle("moto_baseline"), self.t), bundle("moto_baseline"))


if __name__ == "__main__":
    unittest.main()
