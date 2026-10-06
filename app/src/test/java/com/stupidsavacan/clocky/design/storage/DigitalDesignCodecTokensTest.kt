package com.stupidsavacan.clocky.design.storage

import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.ColorRole
import com.stupidsavacan.clocky.design.model.DesignLayout
import com.stupidsavacan.clocky.design.model.DesignSource
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.model.TextSizeStep
import com.stupidsavacan.clocky.design.model.ThemeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 1B additive keys of schema 2. org.json is an Android stub on the plain JVM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DigitalDesignCodecTokensTest {
    private fun roundTrip(d: DigitalDesign) = DigitalDesignCodec.decode(JSONObject(DigitalDesignCodec.encode(d).toString()))

    @Test
    fun schema2JsonFromBeforePhase1BDecodesWithoutStyleOrSource() {
        val legacy = JSONObject(
            """
            {
              "schema": 2,
              "origin": "google-clock",
              "time": {"font": "serif", "weight": 300, "sizeSp": 72.0, "color": {"type": "fixed", "rgb": "#ABCDEF"}, "leadingZero": true},
              "date": {"visible": true, "sizeSp": 16.0, "color": {"type": "fixed", "rgb": "#112233"}, "formatPattern": null},
              "background": {"type": "SOLID", "color": {"type": "fixed", "rgb": "#123456"}, "opacity": 0.5,
                             "cornerRadius": {"type": "dp", "value": 24.0}, "paddingDp": 12.0},
              "layout": {"template": "TIME_FIRST", "overrides": {"strip": {"date.visible": false, "time.sizeSp": 40.0}}},
              "behavior": {"hourMode": "FORCE_24_HOUR"}
            }
            """.trimIndent(),
        )
        val d = DigitalDesignCodec.decode(legacy)
        assertNull(d.style)
        assertNull(d.source)
        assertEquals(ColorRef.Fixed(0xABCDEF), d.time.style.color)
        assertEquals(ColorRef.Fixed(0x112233), d.date.style.color)
        assertEquals(ColorRef.Fixed(0x123456), d.background.color)
        assertEquals(Template.TIME_FIRST, d.layout.template)
        assertEquals(false, d.layout.patchFor(SizeClass.STRIP).dateVisible)
        assertNull(d.layout.patchFor(SizeClass.STRIP).template)
        // Re-encoding such a design must not invent token keys.
        val again = DigitalDesignCodec.encode(d)
        assertFalse(again.has("style"))
        assertFalse(again.has("source"))
    }

    @Test
    fun tokenColorsTokensSourceAndPatchTemplateRoundTrip() {
        val b = BuiltinDesigns.boldBlock.design
        val design = b.copy(
            style = b.style!!.copy(themeMode = ThemeMode.MATERIAL_YOU, textSize = TextSizeStep.LARGE),
            layout = DesignLayout(
                template = Template.SPLIT,
                overrides = mapOf(
                    SizeClass.STRIP to LayoutPatch(template = Template.INLINE, timeSizeSp = 30f),
                    SizeClass.CARD to LayoutPatch(template = Template.MINIMAL),
                ),
            ),
            source = DesignSource(builtinId = "bold-block", version = 3, kitId = "bold"),
        )
        val json = DigitalDesignCodec.encode(design)
        val overrides = json.getJSONObject("layout").getJSONObject("overrides")
        assertEquals("INLINE", overrides.getJSONObject("strip").getString("layout.template"))
        assertEquals("MINIMAL", overrides.getJSONObject("card").getString("layout.template"))
        assertEquals("token", json.getJSONObject("time").getJSONObject("color").getString("type"))
        assertEquals("PRIMARY", json.getJSONObject("time").getJSONObject("color").getString("role"))
        assertEquals("MATERIAL_YOU", json.getJSONObject("style").getString("themeMode"))
        assertEquals("LARGE", json.getJSONObject("style").getString("textSize"))

        val back = roundTrip(design)
        assertEquals(design, back)
        assertEquals(ColorRef.Token(ColorRole.PRIMARY), back.time.style.color)
        assertEquals(ColorRef.Token(ColorRole.SURFACE), back.background.color)
        assertEquals(DesignSource("bold-block", 3, "bold"), back.source)
    }

    @Test
    fun unknownTemplateNamesInOverridesDecodeToNullInsteadOfThrowing() {
        val json = DigitalDesignCodec.encode(DigitalDesign(layout = DesignLayout(overrides = mapOf(
            SizeClass.STRIP to LayoutPatch(template = Template.INLINE, timeSizeSp = 40f),
            SizeClass.CARD to LayoutPatch(template = Template.SPLIT),
        ))))
        val overrides = json.getJSONObject("layout").getJSONObject("overrides")
        overrides.getJSONObject("strip").put("layout.template", "HOLOGRAM")
        overrides.getJSONObject("card").put("layout.template", "HOLOGRAM")

        val d = DigitalDesignCodec.decode(JSONObject(json.toString()))
        assertNull(d.layout.patchFor(SizeClass.STRIP).template)
        // The rest of the patch survives; a patch that only held the bad template is dropped.
        assertEquals(40f, d.layout.patchFor(SizeClass.STRIP).timeSizeSp)
        assertTrue(d.layout.patchFor(SizeClass.CARD).isEmpty)
        assertFalse(SizeClass.CARD in d.layout.overrides)
    }

    @Test
    fun paletteColorsArePersistedAsHashRrggbbAndSurviveUnchanged() {
        val design = BuiltinDesigns.defaultTonal.design
        val palette = JSONObject(DigitalDesignCodec.encode(design).toString()).getJSONObject("style").getJSONObject("palette")
        val light = palette.getJSONObject("light")
        assertEquals("#00504A", light.getString("primary"))
        assertEquals("#D3E8E2", light.getString("surface"))
        assertEquals("#A9D6CC", palette.getJSONObject("dark").getString("primary"))
        listOf("light", "dark").forEach { variant ->
            listOf("primary", "secondary", "accent", "surface").forEach { role ->
                assertTrue(palette.getJSONObject(variant).getString(role).matches(Regex("#[0-9A-F]{6}")))
            }
        }
        assertEquals(design.style!!.palette, roundTrip(design).style!!.palette)
    }

    @Test
    fun incompleteStyleBlockDecodesToNullTokensRatherThanThrowing() {
        val json = DigitalDesignCodec.encode(BuiltinDesigns.clockyDefault.design)
        json.getJSONObject("style").remove("fontPrimary")
        val d = DigitalDesignCodec.decode(JSONObject(json.toString()))
        assertNull(d.style)
    }
}
