import unittest

from clockydev import devstate as D
from tests.helpers import fixture


class MotoG13(unittest.TestCase):
    def test_getprop(self):
        p = D.parse_getprop(fixture("moto_g13_getprop.txt"))
        self.assertEqual(p["ro.product.model"], "moto g13")
        self.assertEqual(p["ro.build.version.sdk"], "34")
        self.assertEqual(p["persist.sys.locale"], "ja-JP")

    def test_wm(self):
        self.assertEqual(D.parse_wm("Physical density: 280\n")["physical"], 280)
        self.assertEqual(D.parse_wm("Physical size: 720x1600\n")["physical"], (720, 1600))
        self.assertEqual(D.parse_wm("Physical size: 1080x2400\nOverride size: 720x1600\n")["override"], (720, 1600))

    def test_window(self):
        w = D.parse_window(fixture("moto_g13_dumpsys_window.txt"))
        self.assertIs(w["keyguard"], False)
        self.assertEqual(w["rotation"], 0)
        self.assertEqual(w["user_rotation_mode"], "USER_ROTATION_FREE")
        self.assertEqual(w["user_rotation"], 0)
        self.assertEqual(w["focus"], "com.motorola.launcher3/com.android.launcher3.CustomizationPanelLauncher")

    def test_power_activity_ime_home_package(self):
        self.assertEqual(D.parse_power(fixture("moto_g13_dumpsys_power.txt")), "Awake")
        self.assertIn("CustomizationPanelLauncher", D.parse_resumed(fixture("moto_g13_dumpsys_activity.txt")))
        self.assertIs(D.parse_ime(fixture("moto_g13_dumpsys_input_method.txt")), False)
        self.assertEqual(D.parse_home(fixture("moto_g13_resolve_home.txt")),
                         "com.motorola.launcher3/com.android.launcher3.CustomizationPanelLauncher")
        p = D.parse_package(fixture("moto_g13_dumpsys_package.txt"))
        self.assertEqual(p["versionCode"], 2)
        self.assertEqual(p["uid"], 10232)
        self.assertTrue(p["debuggable"])
        self.assertEqual(p["lastUpdateTime"], "2026-10-06 19:00:32")


class Emulator30(unittest.TestCase):
    def test_parsers(self):
        w = D.parse_window(fixture("emu30_dumpsys_window.txt"))
        self.assertIs(w["keyguard"], False)
        self.assertEqual(w["rotation"], 0)
        self.assertEqual(w["user_rotation_mode"], "USER_ROTATION_FREE")
        self.assertIn("NexusLauncherActivity", w["focus"])
        self.assertIn("NexusLauncherActivity", D.parse_resumed(fixture("emu30_dumpsys_activity.txt")))
        p = D.parse_package(fixture("emu30_dumpsys_package.txt"))
        self.assertEqual((p["uid"], p["versionCode"], p["debuggable"]), (10169, 2, True))


class Variants(unittest.TestCase):
    def test_legacy_keyguard(self):
        self.assertIs(D.parse_window("    mShowingLockscreen=true mDreamingLockscreen=false")["keyguard"], True)
        self.assertIs(D.parse_window("    mShowingLockscreen=false mDreamingLockscreen=false")["keyguard"], False)
        self.assertNotIn("keyguard", D.parse_window("nothing"))

    def test_legacy_package_userid_and_missing(self):
        self.assertEqual(D.parse_package("  userId=10050\n  versionCode=1 minSdk=23\n  flags=[ HAS_CODE ]\n")["uid"], 10050)
        self.assertFalse(D.parse_package("  versionCode=1\n  flags=[ HAS_CODE ]")["debuggable"])
        self.assertIsNone(D.parse_package("Unable to find package: x"))

    def test_resumed_legacy(self):
        self.assertEqual(D.parse_resumed("  mResumedActivity: ActivityRecord{1a2b u0 a.b/.C t5}"), "a.b/.C")

    def test_rotation_names(self):
        w = D.parse_window("    mRotation=1 x\n    mUserRotationMode=USER_ROTATION_LOCKED mUserRotation=ROTATION_90\n")
        self.assertEqual((w["rotation"], w["user_rotation"]), (1, 1))

    def test_api25_rotation(self):
        w = D.parse_window("    mUserRotationMode=0 mUserRotation=0 mAllowAllRotations=-1\n      mCurrentRotation=1\n")
        self.assertEqual(w["rotation"], 1)
        self.assertEqual(w["user_rotation"], 0)

    def test_epoch(self):
        self.assertAlmostEqual(D.parse_device_epoch("1791296632.469123456\n"), 1791296632.469)
        self.assertEqual(D.parse_device_epoch("1791296632\n"), 1791296632.0)
        self.assertEqual(D.parse_epoch_first_line("--------- beginning of main\n 1791295850.079  1000 1 1 I x: y\n"), 1791295850.079)

    def test_human_line_ascii(self):
        line = D.human_line("S1", "usb", {"model": "m", "sdk": 34, "rotation": 0, "wakefulness": "Awake",
                                         "keyguard": False, "ime_shown": False,
                                         "settings": {"accelerometer_rotation": "1"}})
        line.encode("ascii")
        self.assertIn("rot 0 (auto)", line)


if __name__ == "__main__":
    unittest.main()
