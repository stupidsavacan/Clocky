import json
import os
import tempfile
import unittest

from clockydev import session as S
from clockydev.errors import CdevError
from tests.helpers import FakeAdb


class Ctx:
    def __init__(self, d):
        self.build_dir = d
        self.adb_calls = []
        self.command = "x"
        self._device = None


class State(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = self.tmp.name

    def tearDown(self):
        self.tmp.cleanup()

    def test_state_roundtrip_and_missing(self):
        self.assertEqual(S.load_state(self.d), {})
        S.save_state(self.d, {"a": 1})
        self.assertEqual(S.load_state(self.d), {"a": 1})

    def test_original_saved_only_first_time(self):
        st = {}
        S.record_original(st, "ID", "system/user_rotation", "0")
        S.record_original(st, "ID", "system/user_rotation", "1")   # second rotate must not overwrite
        self.assertEqual(st["saved_settings"]["items"]["system/user_rotation"], "0")

    def test_null_original_kept_and_restored_by_delete(self):
        st = {}
        S.record_original(st, "ID", "global/stay_on_while_plugged_in", None)
        self.assertIn("global/stay_on_while_plugged_in", st["saved_settings"]["items"])
        calls = {}
        adb = FakeAdb({"settings get": "null\n"})
        restored, failed = S.restore_all(adb, st)
        self.assertEqual(failed, {})
        self.assertTrue(any(c.startswith("settings delete global stay_on_while_plugged_in") for c in adb.calls))
        self.assertNotIn("saved_settings", st)

    def test_restore_writes_value_and_verifies(self):
        st = {}
        S.record_original(st, "ID", "system/user_rotation", "2")
        adb = FakeAdb({"settings get": "2\n"})
        restored, failed = S.restore_all(adb, st)
        self.assertEqual(restored, {"system/user_rotation": "2"})
        self.assertIn("settings put system user_rotation 2", adb.calls)

    def test_restore_failure_keeps_item(self):
        st = {}
        S.record_original(st, "ID", "system/user_rotation", "2")
        adb = FakeAdb({"settings get": "1\n"})
        restored, failed = S.restore_all(adb, st)
        self.assertIn("system/user_rotation", failed)
        self.assertIn("system/user_rotation", st["saved_settings"]["items"])

    def test_identity_mismatch_refused(self):
        st = {}
        S.record_original(st, "ID1", "system/user_rotation", "0")
        with self.assertRaises(CdevError) as cm:
            S.require_identity(st, "ID2")
        self.assertEqual(cm.exception.exit_code, 3)
        with self.assertRaises(CdevError):
            S.record_original(st, "ID2", "system/accelerometer_rotation", "1")
        S.require_identity(st, "ID1")

    def test_allowlist(self):
        with self.assertRaises(CdevError) as cm:
            S.record_original({}, "ID", "secure/lock_screen", "1")
        self.assertEqual(cm.exception.exit_code, 6)
        with self.assertRaises(CdevError):
            S.put_setting(FakeAdb(), "system/screen_brightness", 5)
        with self.assertRaises(CdevError):
            S.put_setting(FakeAdb(), "system/user_rotation", "1; reboot")

    def test_step_numbering_and_index(self):
        ctx = Ctx(self.d)
        S.save_state(self.d, {"session": {"id": "s-1", "step": 0}})
        d1 = S.next_step_dir(ctx, "inspect")
        d2 = S.next_step_dir(ctx, "tap now!")
        self.assertTrue(d1.endswith(os.path.join("s-1", "0001-inspect")))
        self.assertTrue(d2.endswith("0002-tap-now"))
        self.assertTrue(os.path.isdir(d2))

        class Dev:
            def summary(self):
                return {"serial": "S"}
        ctx._device = Dev()
        S.log_command(ctx, True, None)
        S.log_command(ctx, False, "boom")
        with open(os.path.join(self.d, "sessions", "s-1", "index.jsonl"), encoding="utf-8") as f:
            lines = f.read().splitlines()
        self.assertEqual(len(lines), 2)
        self.assertFalse(json.loads(lines[1])["ok"])

    def test_session_id(self):
        sid = S.new_session_id("ZY 22", 0)
        self.assertTrue(sid.startswith("s-") and sid.endswith("ZY_22"))


if __name__ == "__main__":
    unittest.main()
