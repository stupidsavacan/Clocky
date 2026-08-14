package com.stupidsavacan.clocky.customization.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyWidgetFontFamilyPolicyTest {
    @Test
    fun repositoryMvpFamiliesAreExactAtNormalWeight() {
        val families = listOf(
            "sans-serif-light",
            "sans-serif-rounded",
            "serif",
            "sans-serif-condensed",
            "monospace",
        )
        families.forEach { family ->
            assertEquals(family, LegacyWidgetFontFamilyPolicy.exactFamilyOrNull(family, 400))
        }
    }

    @Test
    fun otherFamiliesOrWeightsStayOnExistingRenderer() {
        assertNull(LegacyWidgetFontFamilyPolicy.exactFamilyOrNull("system-sans", 400))
        assertNull(LegacyWidgetFontFamilyPolicy.exactFamilyOrNull("not-a-clocky-family", 400))
        assertNull(LegacyWidgetFontFamilyPolicy.exactFamilyOrNull("serif", 300))
        assertNull(LegacyWidgetFontFamilyPolicy.exactFamilyOrNull("serif", 700))
    }
}
