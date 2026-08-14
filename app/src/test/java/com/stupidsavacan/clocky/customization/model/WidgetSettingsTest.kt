package com.stupidsavacan.clocky.customization.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSettingsTest {
    @Test
    fun normalizedClampsCoreWidgetValues() {
        val normalized = WidgetSettings(
            appWidgetId = 42,
            time = TimeSettings(requestedWeight = 50, sizeSp = 0f, opacity = 1.5f),
            date = DateSettings(requestedWeight = 950, sizeSp = -2f, opacity = -0.5f),
            background = BackgroundSettings(opacity = 2f, cornerRadiusDp = -4f, paddingDp = -8f),
        ).normalized()

        assertEquals(100, normalized.time.requestedWeight)
        assertEquals(1f, normalized.time.sizeSp)
        assertEquals(1f, normalized.time.opacity)
        assertEquals(900, normalized.date.requestedWeight)
        assertEquals(1f, normalized.date.sizeSp)
        assertEquals(0f, normalized.date.opacity)
        assertEquals(1f, normalized.background.opacity)
        assertEquals(0f, normalized.background.cornerRadiusDp)
        assertEquals(0f, normalized.background.paddingDp)
    }

    @Test
    fun normalizedClampsProfileOverridesWithoutFillingInheritedFields() {
        val normalized = WidgetSettings(
            appWidgetId = 7,
            fourByOne = ProfileOverride(
                timeWeight = 999,
                dateWeight = 1,
                timeSizeSp = 0f,
                dateSizeSp = -10f,
                timeXDp = null,
            ),
        ).normalized()

        assertEquals(900, normalized.fourByOne?.timeWeight)
        assertEquals(100, normalized.fourByOne?.dateWeight)
        assertEquals(1f, normalized.fourByOne?.timeSizeSp)
        assertEquals(1f, normalized.fourByOne?.dateSizeSp)
        assertEquals(null, normalized.fourByOne?.timeXDp)
    }
}
