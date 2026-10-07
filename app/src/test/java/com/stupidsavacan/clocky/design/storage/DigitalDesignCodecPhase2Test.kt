package com.stupidsavacan.clocky.design.storage

import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.model.AmPmMode
import com.stupidsavacan.clocky.design.model.AmPmStyle
import com.stupidsavacan.clocky.design.model.BackgroundElement
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.Behavior
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.ColorRole
import com.stupidsavacan.clocky.design.model.DateElement
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.Effects
import com.stupidsavacan.clocky.design.model.InfoElement
import com.stupidsavacan.clocky.design.model.InfoSource
import com.stupidsavacan.clocky.design.model.ShadowLevel
import com.stupidsavacan.clocky.design.model.TapAction
import com.stupidsavacan.clocky.design.model.TapActions
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** org.json needs Robolectric on the JVM, like the other codec tests. */
@RunWith(RobolectricTestRunner::class)
class DigitalDesignCodecPhase2Test {
    private fun roundTrip(design: DigitalDesign) = DigitalDesignCodec.decode(DigitalDesignCodec.encode(design))

    private val phase2 = DigitalDesign(
        date = DateElement(uppercase = false),
        background = BackgroundElement(
            type = BackgroundType.GRADIENT,
            color = ColorRef.Token(ColorRole.ACCENT),
            gradientEnd = ColorRef.Fixed(0x123456),
            gradientAngleDeg = 135,
            borderWidthDp = 3f,
        ),
        behavior = Behavior(
            amPm = AmPmStyle(AmPmMode.SUFFIX, 0.5f),
            showSeconds = true,
            tap = TapActions(TapAction.OPEN_TIMER, TapAction.OPEN_CALENDAR, TapAction.NONE),
        ),
        info = InfoElement(source = InfoSource.SECOND_TIMEZONE, timeZoneId = "Asia/Tokyo", label = "Tokyo"),
        effects = Effects(ShadowLevel.STRONG),
    ).let { it.copy(info = it.info.copy(style = it.info.style.copy(sizeSp = 13f))) }

    @Test
    fun everyPhase2FieldRoundTrips() {
        assertEquals(phase2, roundTrip(phase2))
    }

    @Test
    fun phase1BDocumentsDecodeToPhase2DefaultsWithoutLosingAnything() {
        BuiltinDesigns.all.forEach { builtin ->
            val design = builtin.instantiate()
            val json = DigitalDesignCodec.encode(design)
            // Simulate a document written before Phase 2: none of the new keys exist.
            json.getJSONObject("date").remove("uppercase")
            json.getJSONObject("background").apply { remove("gradientEnd"); remove("gradientAngleDeg"); remove("borderWidthDp") }
            json.getJSONObject("behavior").apply { remove("amPm"); remove("showSeconds"); remove("tap") }
            json.remove("info")
            json.remove("effects")
            val decoded = DigitalDesignCodec.decode(json)
            assertEquals("${builtin.id} must decode unchanged", design, decoded)
        }
    }

    @Test
    fun defaultsAreTheBehaviorOfEarlierPhases() {
        val d = DigitalDesign()
        assertEquals(ShadowLevel.CLASSIC, d.effects.shadow)
        assertEquals(InfoSource.NONE, d.info.source)
        assertEquals(AmPmMode.HIDDEN, d.behavior.amPm.mode)
        assertFalse(d.behavior.showSeconds)
        assertTrue(d.behavior.tap.isDefault)
        assertTrue(d.date.uppercase)
    }

    @Test
    fun unknownEnumValuesFallBackToDefaultsInsteadOfFailingTheWholeDocument() {
        val json = DigitalDesignCodec.encode(phase2)
        json.getJSONObject("effects").put("shadow", "HOLOGRAM")
        json.getJSONObject("behavior").getJSONObject("tap").put("time", "TELEPORT")
        json.getJSONObject("info").put("source", "WEATHER")
        val decoded = DigitalDesignCodec.decode(json)
        assertEquals(ShadowLevel.CLASSIC, decoded.effects.shadow)
        assertEquals(TapAction.OPEN_CLOCKY, decoded.behavior.tap.time)
        assertEquals(InfoSource.NONE, decoded.info.source)
        // The valid parts survive.
        assertEquals(135, decoded.background.gradientAngleDeg)
    }

    @Test
    fun outOfRangeValuesAreNormalizedNotStored() {
        val json = DigitalDesignCodec.encode(phase2)
        json.getJSONObject("behavior").getJSONObject("amPm").put("scale", 9.0)
        json.getJSONObject("background").put("borderWidthDp", 99.0).put("gradientAngleDeg", -90)
        json.getJSONObject("info").put("label", "x".repeat(60))
        val d = DigitalDesignCodec.decode(json)
        assertEquals(0.6f, d.behavior.amPm.scale, 0f)
        assertEquals(4f, d.background.borderWidthDp, 0f)
        assertEquals(270, d.background.gradientAngleDeg)
        assertEquals(24, d.info.label!!.length)
    }

    @Test
    fun schemaStaysTwo() {
        assertEquals(2, DigitalDesignCodec.encode(phase2).getInt("schema"))
        assertTrue(JSONObject(DigitalDesignCodec.encode(phase2).toString()).has("info"))
    }
}
