import unittest

from clockydev import logs as L

# formats copied from a real moto g13 `logcat -d -v epoch,uid` capture (Clocky lines modeled on them)
MAIN = """--------- beginning of main
         1791300000.100 10232 5001 5001 I ClockyWidget: update appWidgetId=17
         1791300000.200  1000 1781 1847 I AppsFilter: interaction: PackageSetting{7c3a4aa com.stupidsavacan.clocky/10232} -> x BLOCKED
         1791300001.000 10080 3338 3338 W Launcher: appWidgetId 18 (EXTRA_APPWIDGET_ID) was not returned from the widget configuration activity.
         1791300001.100  1000 1781 1800 I AppWidgetServiceImpl: bind appWidgetId=17
         1791300002.000 10123 4000 4000 I other: unrelated line
garbage line without format
"""
EVENTS = """--------- beginning of events
         1791300000.050  1000 1781 1824 I am_proc_start: [0,5001,10232,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.alarmclock.DigitalAppWidgetProvider}]
         1791300000.060  1000 1781 1824 I am_proc_start: [0,6000,10123,com.other,broadcast,{com.other/x}]
         1791300000.900 10232 5001 5001 I wm_on_create_called: [1,com.stupidsavacan.clocky.widget.DigitalWidgetConfigActivity,performCreate,12]
         1791300000.950 10232 5001 5001 I wm_on_resume_called: [1,com.stupidsavacan.clocky.widget.DigitalWidgetConfigActivity,RESUME_ACTIVITY,0]
         1791300003.000  1000 1781 1824 I am_proc_died: [0,5001,com.stupidsavacan.clocky,935,17]
"""
CRASH = """--------- beginning of crash
         1791300004.000 10232 5001 5001 E AndroidRuntime: FATAL EXCEPTION: main
         1791300004.001 10232 5001 5001 E AndroidRuntime: Process: com.stupidsavacan.clocky, PID: 5001
         1791300004.002 10232 5001 5001 E AndroidRuntime: java.lang.RuntimeException: boom
         1791300004.003 10123 6000 6000 E AndroidRuntime: FATAL EXCEPTION: main
"""


class Parse(unittest.TestCase):
    def test_line(self):
        e = L.parse_line("         1791300000.100 10232 5001 5001 I ClockyWidget: update appWidgetId=17")
        self.assertEqual((e["epoch"], e["uid"], e["pid"], e["level"], e["tag"]), (1791300000.1, "10232", 5001, "I", "ClockyWidget"))
        self.assertEqual(e["msg"], "update appWidgetId=17")

    def test_markers_and_junk_ignored(self):
        self.assertIsNone(L.parse_line("--------- beginning of main"))
        self.assertIsNone(L.parse_line("garbage"))
        self.assertEqual(len(L.parse_log(MAIN)), 5)

    def test_no_uid_format(self):
        e = L.parse_line("  1791300000.100  5001  5001 I Tag: hello: world")
        self.assertIsNone(e["uid"])
        self.assertEqual(e["msg"], "hello: world")

    def test_uid_names(self):
        self.assertEqual(L.uid_names(10232), {"10232", "u0_a232"})


class Filter(unittest.TestCase):
    def setUp(self):
        self.by = {"main": L.parse_log(MAIN), "events": L.parse_log(EVENTS), "crash": L.parse_log(CRASH)}
        self.items = L.filter_clocky(self.by, 10232, 0.0)

    def classes(self):
        return [c for _b, _e, c in self.items]

    def test_classification(self):
        cl = self.classes()
        for c in ("PROC", "ACT", "LAUNCH", "WIDGET", "CRASH"):
            self.assertIn(c, cl)
        self.assertEqual(cl.count("PROC"), 2)   # start + died, not com.other
        self.assertEqual(cl.count("ACT"), 2)

    def test_noise_and_unrelated_excluded(self):
        msgs = " ".join(e["msg"] for _b, e, _c in self.items)
        self.assertNotIn("PackageSetting", msgs)
        self.assertNotIn("unrelated", msgs)
        self.assertNotIn("com.other", msgs)

    def test_since_filters(self):
        items = L.filter_clocky(self.by, 10232, 1791300002.5)
        self.assertTrue(all(e["epoch"] >= 1791300002.5 for _b, e, _c in items))
        self.assertTrue(any(c == "CRASH" for _b, _e, c in items))

    def test_sorted(self):
        ep = [e["epoch"] for _b, e, _c in self.items]
        self.assertEqual(ep, sorted(ep))

    def test_summary_counts(self):
        s, lines = L.summarize(self.items, [], 1791300000.0, 1791300010.0)
        self.assertTrue(s["crash"])
        self.assertFalse(s["anr"])
        self.assertTrue(any(l.startswith("LAUNCH") and "was not returned" in l for l in lines))


API30 = """         1791304700.100  1000  1000  1100 I wm_create_activity: [0,181433538,16,com.stupidsavacan.clocky/.widget.DigitalWidgetConfigActivity,android.appwidget.action.APPWIDGET_CONFIGURE,NULL,NULL,268435456]
         1791304701.100 10169  3000  3000 I wm_on_create_called: [181433538,com.stupidsavacan.clocky.widget.DigitalWidgetConfigActivity,performCreate]
         1791304702.100  1000  1000  1100 I wm_finish_activity: [0,181433538,16,com.stupidsavacan.clocky/.widget.DigitalWidgetConfigActivity,app-request]
         1791304702.200 10169  3000  3000 I wm_on_paused_called: [181433538,com.stupidsavacan.clocky.widget.DigitalWidgetConfigActivity,performPause]
"""


class Api30(unittest.TestCase):
    def test_config_cancel_lifecycle(self):
        items = L.filter_clocky({"events": L.parse_log(API30)}, 10169, 0.0)
        self.assertEqual([c for _b, _e, c in items], ["ACT"] * 4)
        _s, lines = L.summarize(items, [], 0.0)
        self.assertTrue(any("1 other lifecycle line" in l for l in lines))
        self.assertTrue(any("wm_finish_activity" in l for l in lines))


class Gap(unittest.TestCase):
    def test_gap_detected(self):
        ents = L.parse_log(MAIN)
        self.assertIsNotNone(L.gap_warning("main", ents, 1791299000.0))
        self.assertIn("rolled over", L.gap_warning("main", ents, 1791299000.0))

    def test_no_gap(self):
        self.assertIsNone(L.gap_warning("main", L.parse_log(MAIN), 1791300000.5))
        self.assertIsNone(L.gap_warning("main", [], 5.0))
        self.assertIsNone(L.gap_warning("main", L.parse_log(MAIN), 0.0))


class Since(unittest.TestCase):
    def test_specs(self):
        marks = {"prepare": 100.0, "add2": 200.0}
        self.assertEqual(L.resolve_since("prepare", marks, 1000), 100.0)
        self.assertEqual(L.resolve_since("mark:add2", marks, 1000), 200.0)
        self.assertEqual(L.resolve_since("30s", marks, 1000), 970)
        self.assertEqual(L.resolve_since("all-buffer", marks, 1000), 0.0)
        for bad in ("mark:zz", "xyz"):
            with self.assertRaises(ValueError):
                L.resolve_since(bad, marks, 1000)
        with self.assertRaises(ValueError):
            L.resolve_since("prepare", {}, 1000)


if __name__ == "__main__":
    unittest.main()
