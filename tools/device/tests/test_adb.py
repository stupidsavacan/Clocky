import unittest

from clockydev.adb import check_raw, msys_suspect, normalize_text, raw_denied, resolve_adb
from clockydev.errors import CdevError


class Text(unittest.TestCase):
    def test_crlf(self):
        self.assertEqual(normalize_text(b"a\r\nb\r\n"), "a\nb\n")

    def test_utf8_replace(self):
        self.assertIn("\ufffd", normalize_text(b"\xff\xfe"))
        self.assertEqual(normalize_text("日本語".encode("utf-8")), "日本語")


class Deny(unittest.TestCase):
    def denied(self, *a):
        return raw_denied(list(a)) is not None

    def test_positive(self):
        for args in [("uninstall", "x"), ("shell", "pm clear com.x"), ("shell", "pm uninstall com.x"),
                     ("shell", "cmd package uninstall com.x"), ("reboot",), ("reboot", "recovery"), ("root",),
                     ("remount",), ("disable-verity",), ("logcat", "-c"), ("shell", "logcat -c"),
                     ("shell", "logcat", "-b", "main", "-G", "16m"), ("shell", "settings put system x 1"),
                     ("shell", "settings delete global y"), ("shell", "rm -rf /sdcard/x"),
                     ("shell", "rm /data/local/tmp/other.xml"), ("shell", "rm /data/local/tmp/clocky-dev-../x"),
                     ("shell", "rm"), ("emu", "kill"), ("install", "-d", "a.apk"), ("shell", "reboot"),
                     ("-s", "abc", "uninstall", "x")]:
            self.assertTrue(self.denied(*args), args)

    def test_negative(self):
        for args in [("shell", "getprop"), ("shell", "dumpsys window"), ("logcat", "-d"),
                     ("shell", "settings get system user_rotation"), ("install", "-r", "a.apk"),
                     ("shell", "rm -f /data/local/tmp/clocky-dev-1-2.xml"), ("devices", "-l"),
                     ("shell", "pm list packages"), ("shell", "input tap 1 2")]:
            self.assertFalse(self.denied(*args), args)

    def test_check_raw_exit_codes(self):
        with self.assertRaises(CdevError) as cm:
            check_raw(["uninstall", "x"])
        self.assertEqual(cm.exception.exit_code, 6)
        with self.assertRaises(CdevError) as cm:
            check_raw([])
        self.assertEqual(cm.exception.exit_code, 2)


class Msys(unittest.TestCase):
    def test_detect(self):
        self.assertTrue(msys_suspect(["shell", "ls", "C:/Program Files/Git/sdcard"]))
        self.assertTrue(msys_suspect(["shell", "cat C:/Users/me/AppData/Local/Programs/Git/sdcard/ui.xml"]))
        with self.assertRaises(CdevError) as cm:
            check_raw(["shell", "ls", "C:/Program Files/Git/sdcard"])
        self.assertEqual(cm.exception.exit_code, 2)

    def test_clean(self):
        self.assertIsNone(msys_suspect(["shell", "ls", "/sdcard"]))
        self.assertIsNone(msys_suspect(["push", "C:/work/a.txt", "/sdcard/a.txt"]))


class Resolve(unittest.TestCase):
    def test_order(self):
        none = lambda x: None
        always = lambda p: True
        self.assertEqual(resolve_adb({"CLOCKY_ADB": "X", "ANDROID_HOME": "H"}, which=lambda x: "P"), "X")
        self.assertEqual(resolve_adb({"ANDROID_HOME": "H"}, which=lambda x: "P"), "P")
        r = resolve_adb({"ANDROID_HOME": "H"}, which=none, exists=always)
        self.assertTrue(r.startswith("H"))
        r = resolve_adb({"LOCALAPPDATA": "L"}, which=none, exists=always)
        self.assertIn("platform-tools", r)
        self.assertIsNone(resolve_adb({}, which=none, exists=lambda p: False))


if __name__ == "__main__":
    unittest.main()
