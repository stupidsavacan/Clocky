import unittest

from clockydev import gestures as G
from clockydev.errors import CdevError


class Gestures(unittest.TestCase):
    def test_tap_swipe(self):
        self.assertEqual(G.tap_cmd(10.4, 20.6), "input tap 10 21")
        self.assertEqual(G.swipe_cmd(1, 2, 3, 4, 250), "input swipe 1 2 3 4 250")
        self.assertEqual(G.long_press_cmd(5, 6, 1500), "input swipe 5 6 5 6 1500")

    def test_motion_script_shape(self):
        s = G.motion_script((100, 200), (300, 600), hold_ms=1500, steps=4, move_ms=400)
        parts = s.split("; ")
        self.assertEqual(parts[0], "input motionevent DOWN 100 200")
        self.assertEqual(parts[1], "sleep 1.5")
        moves = [p for p in parts if p.startswith("input motionevent MOVE")]
        self.assertEqual(len(moves), 4)
        self.assertEqual(moves[0], "input motionevent MOVE 150 300")
        self.assertEqual(moves[-1], "input motionevent MOVE 300 600")
        self.assertEqual(parts[-1], "input motionevent UP 300 600")
        self.assertIn("sleep 0.1", parts)

    def test_motion_needs_steps(self):
        with self.assertRaises(CdevError):
            G.motion_script((0, 0), (1, 1), steps=0)

    def test_sdk_gate(self):
        for sdk in (25, 29, None):
            with self.assertRaises(CdevError) as cm:
                G.require_motionevent(sdk)
            self.assertEqual(cm.exception.exit_code, 6)
        G.require_motionevent(30)
        G.require_motionevent(34)

    def test_scroll_points_inside_bounds(self):
        b = (0, 100, 720, 1500)
        for d in ("up", "down"):
            for x, y in G.scroll_points(b, d):
                self.assertTrue(b[0] <= x <= b[2] and b[1] <= y <= b[3])
        (x1, y1), (x2, y2) = G.scroll_points(b, "down")
        self.assertGreater(y1, y2)

    def test_tiny_viewport_swipes_long(self):
        (x1, y1), (x2, y2) = G.scroll_points((84, 665, 1530, 720), "down", 720)
        self.assertTrue(665 <= y1 <= 720)
        self.assertEqual(y2, 0 if 716 - 450 < 0 else 716 - 450)
        self.assertGreater(y1 - y2, 200)
        (_a, b0), (_c, b1) = G.scroll_points((0, 0, 100, 50), "up", 1000)
        self.assertGreater(b1 - b0, 200)

    def test_keys(self):
        self.assertEqual(G.key_code("back"), "KEYCODE_BACK")
        self.assertEqual(G.key_code("KEYCODE_VOLUME_UP"), "KEYCODE_VOLUME_UP")
        with self.assertRaises(CdevError) as cm:
            G.key_code("nonsense")
        self.assertEqual(cm.exception.exit_code, 2)
        with self.assertRaises(CdevError) as cm:
            G.key_code("KEYCODE_POWER")
        self.assertEqual(cm.exception.exit_code, 6)
        with self.assertRaises(CdevError):
            G.key_code("KEYCODE_BACK; reboot")


if __name__ == "__main__":
    unittest.main()


class TestHScroll(unittest.TestCase):
    def test_forward_and_back(self):
        from clockydev import gestures as G
        self.assertEqual(G.hscroll_points((807, 126, 1530, 210), "forward"), ((1349, 168), (987, 168)))
        self.assertEqual(G.hscroll_points((807, 126, 1530, 210), "back"), ((987, 168), (1349, 168)))
        with self.assertRaises(CdevError):
            G.hscroll_points((0, 0, 10, 10), "down")
