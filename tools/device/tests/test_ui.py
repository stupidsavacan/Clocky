import unicodedata
import unittest

from clockydev import ui
from clockydev.errors import CdevError
from tests.helpers import FakeAdb, fixture

DUP = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
<node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="p" content-desc="" clickable="false" scrollable="false" bounds="[0,0][720,1600]">
 <node index="0" text="" resource-id="p:id/list" class="android.widget.ScrollView" package="p" content-desc="" clickable="false" scrollable="true" bounds="[0,100][720,1500]">
  <node index="0" text="Save" resource-id="p:id/save_a" class="android.widget.Button" package="p" content-desc="" clickable="true" bounds="[10,200][300,300]"/>
  <node index="1" text="Save" resource-id="p:id/save_b" class="android.widget.Button" package="p" content-desc="" clickable="true" bounds="[10,400][300,500]"/>
  <node index="2" text="" resource-id="p:id/dup" class="android.widget.TextView" package="p" content-desc="Dup" clickable="false" bounds="[10,600][300,700]"/>
  <node index="3" text="Dup" resource-id="p:id/dup" class="android.widget.TextView" package="p" content-desc="" clickable="true" bounds="[10,600][300,700]"/>
  <node index="4" text="Below" resource-id="p:id/below" class="android.widget.Button" package="p" content-desc="" clickable="true" bounds="[10,1700][300,1800]"/>
  <node index="5" text="Zero" resource-id="" class="android.widget.Button" package="p" content-desc="" clickable="true" bounds="[0,0][0,0]"/>
  <node index="6" text="Café" resource-id="" class="android.widget.TextView" package="p" content-desc="" clickable="false" bounds="[10,800][300,900]"/>
  <node index="7" text="  Two   words " resource-id="" class="android.widget.TextView" package="p" content-desc="" clickable="false" bounds="[10,900][300,1000]"/>
  <node index="9" text="" resource-id="p:id/tiny" class="android.widget.ScrollView" package="p" content-desc="" clickable="false" scrollable="true" bounds="[400,1400][700,1450]">
    <node index="0" text="Clipped" resource-id="" class="android.widget.Button" package="p" content-desc="" clickable="true" bounds="[400,1460][700,1560]"/>
    <node index="1" text="InView" resource-id="" class="android.widget.Button" package="p" content-desc="" clickable="true" bounds="[400,1410][700,1440]"/>
  </node>
  <node index="8" text="" resource-id="" class="android.widget.FrameLayout" package="p" content-desc="" clickable="true" bounds="[10,1100][300,1200]">
    <node index="0" text="Inner" resource-id="" class="android.widget.TextView" package="p" content-desc="" clickable="false" bounds="[20,1110][290,1190]"/>
  </node>
 </node>
</node>
</hierarchy>"""


def vis(xml):
    nodes, screen = ui.parse_hierarchy(xml)
    return ui.visible(nodes, screen)


class RealDump(unittest.TestCase):
    def setUp(self):
        self.nodes, self.screen = ui.parse_hierarchy(fixture("moto_g13_launcher_home.xml"))

    def test_parse(self):
        self.assertEqual(len(self.nodes), 44)
        self.assertEqual(self.screen, (720, 1600))

    def test_widget_host_and_japanese_label(self):
        v = ui.visible(self.nodes, self.screen)
        hosts = [n for n in v if n.is_host]
        self.assertEqual(len(hosts), 1)
        self.assertEqual(hosts[0].desc, "Google")
        n, _ = ui.pick(v, ui.Selector(label="電話"))
        self.assertTrue(n.clickable)
        self.assertEqual(n.center, (100, 1412))

    def test_id_suffix_and_full(self):
        v = ui.visible(self.nodes, self.screen)
        a, _ = ui.pick(v, ui.Selector(id="googleapp_search_widget_voice_btn"))
        b, _ = ui.pick(v, ui.Selector(id="com.google.android.googlequicksearchbox:id/googleapp_search_widget_voice_btn"))
        self.assertIs(a, b)

    def test_table_renders(self):
        lines = ui.render_table(ui.visible(self.nodes, self.screen))
        self.assertTrue(any("LauncherAppWidgetHostView" in l for l in lines))
        self.assertIn("of 44 nodes", lines[-1])


class Synthetic(unittest.TestCase):
    def setUp(self):
        self.v = vis(DUP)

    def test_zero_area_and_offscreen_dropped(self):
        labels = [n.label for n in self.v]
        self.assertNotIn("Zero", labels)
        self.assertNotIn("Below", labels)

    def test_duplicate_label_is_ambiguous_exit2(self):
        with self.assertRaises(CdevError) as cm:
            ui.pick(self.v, ui.Selector(label="Save"))
        self.assertEqual(cm.exception.exit_code, 2)
        self.assertEqual(cm.exception.code, "AMBIGUOUS_MATCH")
        self.assertEqual(len(cm.exception.candidates), 2)

    def test_index_picks_document_order(self):
        n, _ = ui.pick(self.v, ui.Selector(label="Save", index=1))
        self.assertEqual(n.rid_short, "save_b")
        with self.assertRaises(CdevError) as cm:
            ui.pick(self.v, ui.Selector(label="Save", index=5))
        self.assertEqual(cm.exception.exit_code, 2)

    def test_not_found_exit4(self):
        with self.assertRaises(CdevError) as cm:
            ui.pick(self.v, ui.Selector(label="Nope"))
        self.assertEqual(cm.exception.exit_code, 4)

    def test_clipped_by_scroll_viewport_dropped(self):
        labels = [n.label for n in self.v]
        self.assertNotIn("Clipped", labels)
        self.assertIn("InView", labels)

    def test_same_bounds_deduped_prefers_clickable(self):
        n, _ = ui.pick(self.v, ui.Selector(label="Dup"))
        self.assertTrue(n.clickable)

    def test_contains_and_exact(self):
        with self.assertRaises(CdevError):
            ui.pick(self.v, ui.Selector(label="Inn"))
        n, _ = ui.pick(self.v, ui.Selector(label="Inn", contains=True))
        self.assertEqual(n.text, "Inner")

    def test_nfc_and_whitespace(self):
        n, _ = ui.pick(self.v, ui.Selector(label=unicodedata.normalize("NFC", "Café")))
        self.assertEqual(n.bounds[1], 800)
        n, _ = ui.pick(self.v, ui.Selector(label="Two words"))
        self.assertEqual(n.bounds[1], 900)

    def test_clickable_ancestor(self):
        n, _ = ui.pick(self.v, ui.Selector(label="Inner"))
        self.assertEqual(ui.clickable_ancestor(n).bounds, (10, 1100, 300, 1200))

    def test_class_and_package_filter(self):
        ms = ui.find(self.v, ui.Selector(cls="Button", pkg="p"))
        self.assertEqual(len(ms), 3)
        self.assertEqual(ui.find(self.v, ui.Selector(cls="Button", pkg="q")), [])

    def test_empty_selector_is_usage_error(self):
        with self.assertRaises(CdevError) as cm:
            ui.pick(self.v, ui.Selector())
        self.assertEqual(cm.exception.exit_code, 2)

    def test_scrollable_and_hash(self):
        self.assertEqual(ui.largest_scrollable(self.v).rid_short, "list")
        self.assertEqual(ui.content_hash(self.v), ui.content_hash(vis(DUP)))


class Dump(unittest.TestCase):
    def test_dump_retries_and_cleans_up(self):
        good = fixture("moto_g13_launcher_home.xml")
        attempts = []

        def uia(cmd):
            attempts.append(cmd)
            return "ERROR: null root node returned by UiTestAutomationBridge." if len(attempts) == 1 else "UI hierchary dumped to: x"

        adb = FakeAdb({"uiautomator dump": uia, "cat ": good})
        xml = ui.dump_xml(adb, sleep=lambda s: None)
        self.assertEqual(len(attempts), 2)
        rms = [c for c in adb.calls if c.startswith("rm -f /data/local/tmp/clocky-dev-")]
        self.assertEqual(len(rms), 2)
        self.assertEqual(len(ui.parse_hierarchy(xml)[0]), 44)

    def test_dump_failure_exit4_and_cleans(self):
        adb = FakeAdb({"uiautomator dump": "ERROR: boom"})
        with self.assertRaises(CdevError) as cm:
            ui.dump_xml(adb, sleep=lambda s: None)
        self.assertEqual(cm.exception.exit_code, 4)
        self.assertEqual(len([c for c in adb.calls if c.startswith("rm -f")]), 3)


if __name__ == "__main__":
    unittest.main()


class StudioScroll(unittest.TestCase):
    def setUp(self):
        self.vis = vis(fixture("moto_g13_studio_landscape.xml"))

    def test_candidate_order_vertical_first_then_horizontal(self):
        cs = ui.scroll_candidates(self.vis)
        self.assertEqual([(c.rid_short, ui.orientation(c)) for c in cs],
                         [("", "vertical"), ("clocky_studio_scroll", "vertical"), ("clocky_studio_tabs_scroll", "horizontal")])
        # the preview ScrollView is the largest, which is what misled the old single-container logic
        self.assertEqual(ui.largest_scrollable(self.vis).rid_short, "")

    def test_all_clocky_scrollables(self):
        self.assertTrue(ui.all_clocky_scrollables(self.vis))
        self.assertFalse(ui.all_clocky_scrollables(vis(DUP)))          # package "p": not Clocky -> legacy path
        self.assertFalse(ui.all_clocky_scrollables([]))

    def test_scroller_visits_every_clocky_container_then_stops(self):
        from clockydev.cmd_ui import Scroller
        sc = Scroller(self.vis, None)
        self.assertTrue(sc.multi)
        seen = []
        while True:
            c = sc.next(self.vis)      # unchanged content -> each container is abandoned after one swipe
            if c is None:
                break
            seen.append(c.rid_short)
        self.assertEqual(seen, ["", "clocky_studio_scroll", "clocky_studio_tabs_scroll"])
        self.assertEqual([e["dir"] for e in sc.summary()], ["down", "down", "forward"])

    def test_scroller_legacy_for_foreign_package_and_in_sel(self):
        from clockydev.cmd_ui import Scroller
        v = vis(DUP)
        sc = Scroller(v, None)
        self.assertFalse(sc.multi)
        self.assertEqual(sc.next(v).rid_short, "list")
        self.assertIsNone(sc.next(v))                                    # unchanged -> done
        self.assertFalse(Scroller(self.vis, "clocky_studio_scroll").multi)

    def test_near_miss_marker_label(self):
        near = ui.near_misses(self.vis, ui.Selector(label="動作"))
        self.assertEqual([n.label for n in near], ["動作 •"])
        self.assertEqual(ui.near_misses(self.vis, ui.Selector(label="日付"))[0].label, "日付 •")
        self.assertEqual(ui.near_misses(self.vis, ui.Selector(label="存在しない")), [])

    def test_not_found_carries_near_miss_hint(self):
        with self.assertRaises(CdevError) as c:
            ui.pick(self.vis, ui.Selector(label="動作"))
        e = c.exception
        self.assertEqual(e.exit_code, 4)
        self.assertIn("did you mean '動作 •'", e.hint)
        self.assertEqual(e.candidates[0]["label"], "動作 •")

    def test_near_misses_never_auto_pick(self):
        n, _ = ui.pick(self.vis, ui.Selector(label="動作", contains=True))
        self.assertEqual(n.label, "動作 •")


class InspectDiff(unittest.TestCase):
    def test_studio_before_after_tab_panel_opened(self):
        a, b = vis(fixture("moto_g13_studio_landscape.xml")), vis(fixture("moto_g13_studio_landscape_after.xml"))
        d = ui.diff_nodes(a, b)
        added = {n.label for n in d["added"] if ui.interesting(n)}
        removed = {n.label for n in d["removed"] if ui.interesting(n)}
        self.assertIn("12時間", added)
        self.assertIn("表示しない", added)
        self.assertIn("表示する項目", removed)
        # header controls are unchanged and therefore not repeated
        self.assertFalse({"元に戻す", "やり直す", "保存", "スタジオ"} & (added | removed))
        self.assertGreater(d["overlap"], 0.5)
        lines = ui.render_diff(d)
        self.assertTrue(any(l.startswith("+ 12時間") for l in lines))
        self.assertLess(len(lines), len(ui.render_table(b)))

    def test_identical_dumps_have_no_changes(self):
        a = vis(fixture("moto_g13_studio_landscape.xml"))
        d = ui.diff_nodes(a, a)
        self.assertEqual((d["added"], d["removed"], d["changed"], d["bounds_only"]), ([], [], [], 0))
        self.assertEqual(d["overlap"], 1.0)

    def test_label_change_is_changed_when_node_has_id(self):
        a = vis(fixture("moto_g13_launcher_tick_a.xml"))
        b = vis(fixture("moto_g13_launcher_tick_b.xml"))
        d = ui.diff_nodes(a, b)
        self.assertEqual([(o.label, n.label) for o, n in d["changed"]], [("7:19", "7:20")])
        self.assertEqual((d["added"], d["removed"]), ([], []))

    def test_different_screens_have_low_overlap(self):
        d = ui.diff_nodes(vis(fixture("moto_g13_launcher_home.xml")), vis(fixture("moto_g13_studio_landscape.xml")))
        self.assertLess(d["overlap"], 0.5)
