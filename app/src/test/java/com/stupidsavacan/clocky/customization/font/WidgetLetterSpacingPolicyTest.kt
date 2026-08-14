package com.stupidsavacan.clocky.customization.font

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLetterSpacingPolicyTest {
    @Test
    fun normalizeClampsToSupportedUiRange() {
        assertEquals(-0.20f, WidgetLetterSpacingPolicy.normalize(-2f), 0.0001f)
        assertEquals(0.05f, WidgetLetterSpacingPolicy.normalize(0.05f), 0.0001f)
        assertEquals(0.50f, WidgetLetterSpacingPolicy.normalize(2f), 0.0001f)
    }

    @Test
    fun displayUsesStableTwoDecimalEmFormat() {
        assertEquals("-0.20 em", WidgetLetterSpacingPolicy.display(-1f))
        assertEquals("0.00 em", WidgetLetterSpacingPolicy.display(0f))
        assertEquals("0.05 em", WidgetLetterSpacingPolicy.display(0.05f))
        assertEquals("0.50 em", WidgetLetterSpacingPolicy.display(1f))
    }
}
