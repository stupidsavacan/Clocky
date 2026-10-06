package com.stupidsavacan.clocky.design.storage

import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.BackgroundElement
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.Behavior
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.CornerRadius
import com.stupidsavacan.clocky.design.model.DateElement
import com.stupidsavacan.clocky.design.model.DesignLayout
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.HourMode
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json is an Android stub on the plain JVM, so these run under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DigitalDesignCodecTest {
    @Test
    fun v2RoundTripsEveryField() {
        val design = DigitalDesign(
            origin = "google-clock",
            time = TimeElement(
                style = TextStyle(
                    fontId = "serif",
                    weight = 300,
                    sizeSp = 72f,
                    letterSpacingEm = -0.05f,
                    color = ColorRef.Fixed(0xABCDEF),
                    opacity = 0.75f,
                    alignment = Alignment.START,
                    xDp = 4f,
                    yDp = -2f,
                ),
                leadingZero = true,
            ),
            date = DateElement(
                visible = false,
                style = TextStyle(sizeSp = 16f, weight = 700, alignment = Alignment.END),
                formatPattern = "yyyy.MM.dd",
            ),
            background = BackgroundElement(
                type = BackgroundType.SOLID,
                color = ColorRef.Fixed(0x123456),
                opacity = 0.5f,
                cornerRadius = CornerRadius.Dp(24f),
                paddingDp = 18f,
            ),
            layout = DesignLayout(
                overrides = mapOf(
                    SizeClass.STRIP to LayoutPatch(dateVisible = false, timeSizeSp = 40f, timeWeight = 200),
                    SizeClass.CARD to LayoutPatch(dateYDp = 3f),
                ),
            ),
            behavior = Behavior(HourMode.FORCE_24_HOUR),
        )

        val json = DigitalDesignCodec.encode(design)
        assertEquals(2, json.getInt("schema"))
        assertEquals(design, DigitalDesignCodec.decode(JSONObject(json.toString())))
    }

    @Test
    fun overridesArePersistedAsPropertyPaths() {
        val json = DigitalDesignCodec.encode(
            DigitalDesign(layout = DesignLayout(overrides = mapOf(SizeClass.STRIP to LayoutPatch(dateVisible = false)))),
        )
        val strip = json.getJSONObject("layout").getJSONObject("overrides").getJSONObject("strip")
        assertEquals(false, strip.getBoolean("date.visible"))
        assertEquals(1, strip.length())
    }

    @Test
    fun localeAutoIsStoredAsNull() {
        val json = DigitalDesignCodec.encode(DigitalDesign())
        assertTrue(json.getJSONObject("date").isNull("formatPattern"))
        assertNull(DigitalDesignCodec.decode(json).date.formatPattern)
    }

    @Test
    fun defaultV1DocumentMigratesToDefaultDesign() {
        // Exactly what the Phase 0 store wrote for an untouched widget (see the on-device dumps).
        val v1 = JSONObject(
            """
            {"schema":1,"presetId":"google-clock",
             "time":{"fontFamily":"system-sans","weight":400,"sizeSp":64,"letterSpacing":0,"argb":-1,
                     "opacity":1,"xDp":0,"yDp":0,"alignment":"CENTER","hourMode":"FOLLOW_SYSTEM","leadingZero":false},
             "date":{"enabled":true,"fontFamily":"system-sans","weight":400,"sizeSp":14,"letterSpacing":0,"argb":-1,
                     "opacity":1,"formatPattern":"EEE, MMM d","xDp":0,"yDp":0,"alignment":"CENTER"},
             "background":{"argb":0,"opacity":0,"cornerRadiusDp":0,"paddingDp":0},
             "fourByTwo":null,"fourByOne":null}
            """.trimIndent(),
        )
        assertEquals(DigitalDesign(), DigitalDesignCodec.decode(v1))
    }

    @Test
    fun v1ProfileOverridesBecomeSizeClassPatches() {
        val v1 = JSONObject(
            """
            {"schema":1,
             "fourByOne":{"timeWeight":300,"dateWeight":null,"timeSizeSp":40,"dateSizeSp":null,"timeXDp":null,
                          "timeYDp":null,"dateXDp":2.5,"dateYDp":null,"dateEnabled":false},
             "fourByTwo":{"timeWeight":null,"dateWeight":800,"timeSizeSp":null,"dateSizeSp":null,"timeXDp":null,
                          "timeYDp":null,"dateXDp":null,"dateYDp":null,"dateEnabled":null}}
            """.trimIndent(),
        )
        val layout = DigitalDesignCodec.decode(v1).layout
        assertEquals(
            LayoutPatch(timeWeight = 300, timeSizeSp = 40f, dateXDp = 2.5f, dateVisible = false),
            layout.patchFor(SizeClass.STRIP),
        )
        assertEquals(LayoutPatch(dateWeight = 800), layout.patchFor(SizeClass.CARD))
    }

    @Test
    fun v1AllNullOverrideIsDropped() {
        val v1 = JSONObject("""{"schema":1,"fourByOne":{"timeWeight":null,"dateEnabled":null}}""")
        assertTrue(DigitalDesignCodec.decode(v1).layout.overrides.isEmpty())
    }

    @Test
    fun v1BaseValuesAndHourModeCarryOver() {
        val v1 = JSONObject(
            """
            {"schema":1,"presetId":"custom",
             "time":{"fontFamily":"monospace","weight":650,"sizeSp":80,"letterSpacing":0.1,"hourMode":"FORCE_12_HOUR","leadingZero":true},
             "date":{"enabled":false,"fontFamily":"serif","weight":200,"sizeSp":18,"letterSpacing":-0.1}}
            """.trimIndent(),
        )
        val d = DigitalDesignCodec.decode(v1)
        assertEquals("custom", d.origin)
        assertEquals("monospace", d.time.style.fontId)
        assertEquals(650, d.time.style.weight)
        assertEquals(80f, d.time.style.sizeSp)
        assertEquals(0.1f, d.time.style.letterSpacingEm, 1e-6f)
        assertTrue(d.time.leadingZero)
        assertEquals(HourMode.FORCE_12_HOUR, d.behavior.hourMode)
        assertEquals(false, d.date.visible)
        assertEquals("serif", d.date.style.fontId)
        assertEquals(200, d.date.style.weight)
        assertEquals(18f, d.date.style.sizeSp)
    }

    @Test
    fun v1ArgbAlphaIsFoldedIntoOpacity() {
        val v1 = JSONObject(
            """{"schema":1,"time":{"argb":${0x80FF0000.toInt()},"opacity":0.5}}""",
        )
        val style = DigitalDesignCodec.decode(v1).time.style
        assertEquals(ColorRef.Fixed(0xFF0000), style.color)
        assertEquals(0.5f * 128f / 255f, style.opacity, 1e-6f)
    }

    @Test
    fun v1NeverRenderedDateFormatMigratesToLocaleAuto() {
        val v1 = JSONObject("""{"schema":1,"date":{"formatPattern":"yyyy.MM.dd"}}""")
        assertNull(DigitalDesignCodec.decode(v1).date.formatPattern)
    }

    @Test
    fun v1AlignmentMapsToRelativeValues() {
        fun align(raw: String) = DigitalDesignCodec.decode(
            JSONObject("""{"schema":1,"time":{"alignment":"$raw"}}"""),
        ).time.style.alignment
        assertEquals(Alignment.START, align("LEFT"))
        assertEquals(Alignment.CENTER, align("CENTER"))
        assertEquals(Alignment.END, align("RIGHT"))
    }

    @Test
    fun v1BackgroundMapsToNoneOrSolid() {
        val none = DigitalDesignCodec.decode(JSONObject("""{"schema":1,"background":{"argb":0,"opacity":0}}"""))
        assertEquals(BackgroundType.NONE, none.background.type)

        val solid = DigitalDesignCodec.decode(
            JSONObject(
                """{"schema":1,"background":{"argb":${0xFF222222.toInt()},"opacity":0.8,"cornerRadiusDp":12,"paddingDp":10}}""",
            ),
        )
        assertEquals(BackgroundType.SOLID, solid.background.type)
        assertEquals(ColorRef.Fixed(0x222222), solid.background.color)
        assertEquals(0.8f, solid.background.opacity, 1e-6f)
        assertEquals(CornerRadius.Dp(12f), solid.background.cornerRadius)
        assertEquals(10f, solid.background.paddingDp)
    }

    @Test
    fun missingSchemaIsTreatedAsV1() {
        assertEquals(1, DigitalDesignCodec.schemaOf(JSONObject("{}")))
        assertEquals(DigitalDesign(), DigitalDesignCodec.decode(JSONObject("{}")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun futureSchemaIsRejected() {
        DigitalDesignCodec.decode(JSONObject("""{"schema":3}"""))
    }

    @Test
    fun malformedColorFallsBackToDefault() {
        val json = DigitalDesignCodec.encode(DigitalDesign())
        json.getJSONObject("time").put("color", JSONObject().put("type", "fixed").put("rgb", "zzz"))
        assertEquals(ColorRef.Fixed(0xFFFFFF), DigitalDesignCodec.decode(json).time.style.color)
    }
}
