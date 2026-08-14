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

    @Test
    fun normalizedSanitizesNonFiniteOffsetsWithoutFillingInheritedFields() {
        val normalized = WidgetSettings(
            appWidgetId = 8,
            time = TimeSettings(xDp = Float.NaN, yDp = Float.POSITIVE_INFINITY),
            date = DateSettings(xDp = Float.NEGATIVE_INFINITY, yDp = 12.5f),
            fourByOne = ProfileOverride(
                timeXDp = Float.NaN,
                timeYDp = null,
                dateXDp = Float.POSITIVE_INFINITY,
                dateYDp = -4f,
            ),
        ).normalized()

        assertEquals(0f, normalized.time.xDp)
        assertEquals(0f, normalized.time.yDp)
        assertEquals(0f, normalized.date.xDp)
        assertEquals(12.5f, normalized.date.yDp)
        assertEquals(0f, normalized.fourByOne?.timeXDp)
        assertEquals(null, normalized.fourByOne?.timeYDp)
        assertEquals(0f, normalized.fourByOne?.dateXDp)
        assertEquals(-4f, normalized.fourByOne?.dateYDp)
    }
}
