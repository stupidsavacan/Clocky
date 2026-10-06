package com.stupidsavacan.clocky.design.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalDesignTest {
    @Test
    fun defaultsMatchTheCurrentWidget() {
        val d = DigitalDesign()
        assertEquals(FontIds.SYSTEM_SANS, d.time.style.fontId)
        assertEquals(400, d.time.style.weight)
        assertEquals(64f, d.time.style.sizeSp)
        assertEquals(14f, d.date.style.sizeSp)
        assertTrue(d.date.visible)
        assertNull("Locale Auto is the default date format", d.date.formatPattern)
        assertEquals(Alignment.CENTER, d.time.style.alignment)
        assertEquals(BackgroundType.NONE, d.background.type)
        assertEquals(CornerRadius.System, d.background.cornerRadius)
        assertEquals(ColorRef.Fixed(0xFFFFFF), d.time.style.color)
    }

    @Test
    fun fixedColorsRejectAlpha() {
        val failure = runCatching { ColorRef.Fixed(0x80FFFFFF.toInt()) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun normalizedClampsRequestsWithoutFillingInheritedPatchFields() {
        val d = DigitalDesign(
            time = TimeElement(TextStyle(weight = 50, sizeSp = 0f, opacity = 1.5f, letterSpacingEm = 9f, xDp = Float.NaN)),
            date = DateElement(style = TextStyle(weight = 950, sizeSp = Float.POSITIVE_INFINITY, opacity = -1f), formatPattern = " "),
            background = BackgroundElement(opacity = 3f, cornerRadius = CornerRadius.Dp(200f), paddingDp = -4f),
            layout = DesignLayout(
                overrides = mapOf(
                    SizeClass.STRIP to LayoutPatch(timeWeight = 1000, dateSizeSp = -2f, timeXDp = Float.NaN),
                    SizeClass.CARD to LayoutPatch(),
                ),
            ),
        ).normalized()

        assertEquals(100, d.time.style.weight)
        assertEquals(1f, d.time.style.sizeSp)
        assertEquals(1f, d.time.style.opacity)
        assertEquals(0.5f, d.time.style.letterSpacingEm)
        assertEquals(0f, d.time.style.xDp)
        assertEquals(900, d.date.style.weight)
        assertEquals(1f, d.date.style.sizeSp)
        assertEquals(0f, d.date.style.opacity)
        assertNull(d.date.formatPattern)
        assertEquals(1f, d.background.opacity)
        assertEquals(CornerRadius.Dp(48f), d.background.cornerRadius)
        assertEquals(0f, d.background.paddingDp)

        val strip = d.layout.patchFor(SizeClass.STRIP)
        assertEquals(900, strip.timeWeight)
        assertEquals(1f, strip.dateSizeSp)
        assertEquals(0f, strip.timeXDp)
        assertNull(strip.dateWeight)
        assertNull(strip.dateVisible)
        assertFalse("empty patches are dropped", d.layout.overrides.containsKey(SizeClass.CARD))
    }

    @Test
    fun withPatchRemovesEmptyPatches() {
        val layout = DesignLayout()
            .withPatch(SizeClass.STRIP, LayoutPatch(dateVisible = false))
        assertEquals(false, layout.patchFor(SizeClass.STRIP).dateVisible)
        assertTrue(layout.withPatch(SizeClass.STRIP, LayoutPatch()).overrides.isEmpty())
    }
}
