import unittest

from clockydev import ui, widgets as W
from tests.helpers import fixture

PREFS_V2 = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="widget.5.settings">{&quot;schema&quot;:2,&quot;origin&quot;:&quot;user&quot;,&quot;time&quot;:{&quot;weight&quot;:400,&quot;sizeSp&quot;:64,&quot;alignment&quot;:&quot;CENTER&quot;},&quot;date&quot;:{&quot;visible&quot;:true,&quot;formatPattern&quot;:&quot;EEE &amp;amp; d&quot;},&quot;background&quot;:{&quot;type&quot;:&quot;none&quot;},&quot;layout&quot;:{&quot;template&quot;:&quot;stack&quot;,&quot;overrides&quot;:{&quot;Strip&quot;:{&quot;time&quot;:1,&quot;date&quot;:2}}},&quot;behavior&quot;:{&quot;hourMode&quot;:&quot;FOLLOW_SYSTEM&quot;}}</string>
    <string name="widget.6.settings">{not json</string>
    <string name="other">x</string>
</map>"""

WITH_OPTS = """Widgets:
  [0] id=21
    host=HostId{user:0, app:10080, hostId:1024, pkg:com.android.launcher3}
    provider=ProviderId{user:0, app:10232, cmp:ComponentInfo{com.stupidsavacan.clocky/com.android.alarmclock.DigitalAppWidgetProvider}}
    options=Bundle[{appWidgetMaxHeight=92, appWidgetMinHeight=70, appWidgetMaxWidth=360, appWidgetMinWidth=330, appWidgetCategory=1}]
    views=x
  [1] id=22
    host=HostId{user:0, app:10080, hostId:1024, pkg:com.android.launcher3}
    provider=ProviderId{user:0, app:10232, cmp:ComponentInfo{com.stupidsavacan.clocky/com.android.alarmclock.DigitalAppWidgetProvider}}
    options=Bundle[mParcelledData.dataSize=92]
Hosts:
  [0] hostId=x
"""


class Complex(unittest.TestCase):
    def test_decode(self):
        self.assertEqual(W.decode_complex(64001), (250.0, "dp"))
        self.assertEqual(W.decode_complex(17921), (70.0, "dp"))
        self.assertEqual(W.decode_complex(10241), (40.0, "dp"))
        self.assertEqual(W.decode_complex(0), (0.0, "px"))
        self.assertEqual(W.decode_complex((2 << 8) | 0), (2.0, "px"))

    def test_nondp_unit_kept(self):
        self.assertEqual(W._dp((12 << 8) | 2), {"value": 12.0, "unit": "sp"})


class AppWidget(unittest.TestCase):
    def test_real_dump_one_widget(self):
        parsed = W.parse_appwidget(fixture("moto_g13_appwidget_1widget.txt"))
        provs, ws = W.clocky_view(parsed)
        self.assertEqual({p["kind"] for p in provs}, {"analog", "digital"})
        dig = [p for p in provs if p["kind"] == "digital"][0]
        self.assertEqual(dig["min_dp"], [250.0, 70.0])
        self.assertEqual(dig["min_resize_dp"], [250.0, 40.0])
        self.assertEqual(dig["resizeMode"], 3)
        self.assertEqual(len(ws), 1)
        self.assertEqual(ws[0]["id"], 17)
        self.assertEqual(ws[0]["host"], "com.motorola.launcher3")
        self.assertIsNone(ws[0]["options"])      # U1: API 34 prints no options line

    def test_zero_widgets(self):
        p = W.parse_appwidget("Providers:\n  [0] provider ProviderId{user:0, app:1, cmp:ComponentInfo{a.b/a.b.C}}\n"
                              "    min=(1x1) minResize=(1x1) resizeMode=0 widgetCategory=1 zombie=false\n \nWidgets:\n \nHosts:\n")
        self.assertEqual(W.clocky_view(p), ([], []))

    def test_options_expanded_and_parcelled(self):
        _p, ws = W.clocky_view(W.parse_appwidget(WITH_OPTS))
        self.assertEqual(ws[0]["options"]["appWidgetMinHeight"], 70)
        self.assertIn("unavailable", ws[1]["options"])

    def test_two_widgets(self):
        _p, ws = W.clocky_view(W.parse_appwidget(WITH_OPTS))
        self.assertEqual([w["id"] for w in ws], [21, 22])


class SizeClass(unittest.TestCase):
    def test_boundaries(self):
        for h, want in [(0, "Card"), (54, "Strip"), (58, "Strip"), (99, "Strip"), (100, "Card"),
                        (125, "Card"), (132, "Card"), (191, "Card"), (None, "unknown")]:
            self.assertEqual(W.size_class(h), want, h)


class Prefs(unittest.TestCase):
    def test_empty_map(self):
        self.assertEqual(W.parse_prefs("<?xml version='1.0'?><map />"), {})
        self.assertEqual(W.parse_prefs(""), {})

    def test_v2_and_bad_json_and_entities(self):
        p = W.parse_prefs(PREFS_V2)
        self.assertEqual(sorted(p), [5, 6])
        s = W.summarize_settings(p[5])
        self.assertEqual(s["schema"], 2)
        self.assertEqual(s["date"]["formatPattern"], "EEE &amp; d")  # entity decoded once by XML, once left literal
        self.assertEqual(s["layout"]["overrides"], {"Strip": ["date", "time"]})
        self.assertEqual(s["behavior"]["hourMode"], "FOLLOW_SYSTEM")
        bad = W.summarize_settings(p[6])
        self.assertIn("decode_error", bad)

    def test_schema1_real_prefs(self):
        p = W.parse_prefs(fixture("moto_g13_prefs_schema1.xml"))
        s = W.summarize_settings(p[17])
        self.assertEqual(s["schema"], 1)
        self.assertEqual(s["time"]["sizeSp"], 64)


class Checks(unittest.TestCase):
    def test_all_kinds(self):
        ws = [{"id": 1}, {"id": 2, "zombie": True}]
        prefs = {2: {"json": {"schema": 1}, "raw": "", "decode_error": None},
                 9: {"json": {"schema": 2}, "raw": "", "decode_error": None}}
        names = {(c["check"], c["id"]) for c in W.build_checks(ws, prefs, True)}
        self.assertEqual(names, {("bound_without_settings", 1), ("schema!=2", 2), ("settings_without_widget", 9),
                                 ("zombie", 2)})

    def test_unavailable_prefs_skips_settings_checks(self):
        self.assertEqual(W.build_checks([{"id": 1}], {}, False), [])

    def test_clean(self):
        self.assertEqual(W.build_checks([{"id": 3}], {3: {"json": {"schema": 2}, "raw": "", "decode_error": None}}, True), [])


class Locate(unittest.TestCase):
    def test_real_launcher_dump(self):
        nodes, screen = ui.parse_hierarchy(fixture("moto_g13_launcher_with_clocky.xml"))
        hosts = W.locate_hosts(ui.visible(nodes, screen), 280)
        self.assertEqual(len(hosts), 1)         # Google search host has no Clocky child
        h = hosts[0]
        self.assertEqual(h["bounds"], [14, 815, 706, 1299])
        self.assertAlmostEqual(h["inner_size_dp"][1], 260.6, 0)
        self.assertEqual(h["center"], [360, 1057])


if __name__ == "__main__":
    unittest.main()
