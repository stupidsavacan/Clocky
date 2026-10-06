import unittest

from clockydev.devices import (classify_transport, fill_identities, parse_devices, select_device)
from clockydev.errors import CdevError

USB = "List of devices attached\nSERIAL0001   device product:p model:moto_g13 device:d transport_id:1\n"
USB_WIFI = USB + "adb-SERIAL0001-abc123._adb-tls-connect._tcp device product:p model:moto_g13 device:d transport_id:2\n"
IPPORT = USB + "192.168.1.20:37000 device product:p model:moto_g13 transport_id:3\n"
TWO = USB + "SERIAL0002   device product:q model:pixel transport_id:4\n"
EMU = USB + "emulator-5554 device product:sdk model:sdk_gphone transport_id:5\n"
BAD = "List of devices attached\nSERIAL0003 unauthorized transport_id:6\nSERIAL0004 offline\n"


def cands(text):
    c = parse_devices(text)
    # identity from adb-<serial>-xxx or the serial itself
    def sn(s):
        return s.split("-")[1] if s.startswith("adb-") else {"192.168.1.20:37000": "SERIAL0001"}.get(s, s)
    return fill_identities(c, sn)


class Parse(unittest.TestCase):
    def test_transport(self):
        self.assertEqual(classify_transport("emulator-5554"), "emulator")
        self.assertEqual(classify_transport("192.168.1.2:5555"), "wifi")
        self.assertEqual(classify_transport("adb-X-y._adb-tls-connect._tcp"), "wifi")
        self.assertEqual(classify_transport("SERIAL0001"), "usb")

    def test_parse(self):
        c = parse_devices(BAD)
        self.assertEqual([x["state"] for x in c], ["unauthorized", "offline"])
        self.assertEqual(parse_devices(USB)[0]["model"], "moto_g13")

    def test_daemon_noise_ignored(self):
        t = "* daemon not running; starting now at tcp:5037\n* daemon started successfully\n" + USB
        self.assertEqual(len(parse_devices(t)), 1)


class Select(unittest.TestCase):
    def test_single(self):
        c, w, how = select_device(cands(USB))
        self.assertEqual(c["serial"], "SERIAL0001")

    def test_usb_preferred_over_wifi_same_identity(self):
        for t in (USB_WIFI, IPPORT):
            c, w, _ = select_device(cands(t))
            self.assertEqual(c["transport"], "usb")

    def test_two_devices_refuse(self):
        with self.assertRaises(CdevError) as cm:
            select_device(cands(TWO))
        self.assertEqual(cm.exception.exit_code, 3)
        self.assertEqual(cm.exception.code, "MULTIPLE_DEVICES")

    def test_physical_plus_emulator_refuse(self):
        with self.assertRaises(CdevError) as cm:
            select_device(cands(EMU))
        self.assertEqual(cm.exception.code, "MULTIPLE_DEVICES")

    def test_flag_serial(self):
        c, _, how = select_device(cands(EMU), flag_serial="emulator-5554")
        self.assertEqual(c["serial"], "emulator-5554")
        with self.assertRaises(CdevError) as cm:
            select_device(cands(EMU), flag_serial="nope")
        self.assertEqual(cm.exception.code, "SERIAL_NOT_FOUND")

    def test_env_precedence(self):
        env = {"ANDROID_SERIAL": "SERIAL0001", "CLOCKY_SERIAL": "emulator-5554"}
        c, _, how = select_device(cands(EMU), env=env)
        self.assertEqual(c["serial"], "emulator-5554")
        self.assertEqual(how, "CLOCKY_SERIAL")
        c, _, how = select_device(cands(EMU), flag_serial="SERIAL0001", env=env)
        self.assertEqual(c["serial"], "SERIAL0001")
        c, _, how = select_device(cands(EMU), env={"ANDROID_SERIAL": "SERIAL0001"})
        self.assertEqual(how, "ANDROID_SERIAL")

    def test_unauthorized_only(self):
        with self.assertRaises(CdevError) as cm:
            select_device(cands(BAD))
        self.assertEqual(cm.exception.code, "NO_DEVICE")
        self.assertIn("USB debugging", cm.exception.hint)

    def test_flag_unauthorized(self):
        with self.assertRaises(CdevError) as cm:
            select_device(cands(BAD), flag_serial="SERIAL0003")
        self.assertEqual(cm.exception.code, "DEVICE_NOT_READY")

    def test_pinned_prefers_identity_and_warns_on_transport_change(self):
        pin = {"identity": "SERIAL0001", "transport": "usb"}
        # wifi only now
        wifi_only = "List of devices attached\nadb-SERIAL0001-abc._adb-tls-connect._tcp device model:m\n" \
                    "SERIAL0002 device model:x\n"
        c, w, how = select_device(cands(wifi_only), pinned=pin)
        self.assertEqual(c["transport"], "wifi")
        self.assertTrue(any("transport changed usb->wifi" in x for x in w))

    def test_pinned_with_multiple_devices_selects_pin(self):
        c, w, how = select_device(cands(TWO), pinned={"identity": "SERIAL0002", "transport": "usb"})
        self.assertEqual(c["identity"], "SERIAL0002")

    def test_pinned_missing_other_present_refuses(self):
        with self.assertRaises(CdevError) as cm:
            select_device(cands(USB), pinned={"identity": "OTHER", "transport": "usb"})
        self.assertEqual(cm.exception.code, "PINNED_DEVICE_MISSING")
        self.assertIn("prepare --serial", cm.exception.hint)


if __name__ == "__main__":
    unittest.main()
