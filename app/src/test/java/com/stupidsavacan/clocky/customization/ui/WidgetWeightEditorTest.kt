package com.stupidsavacan.clocky.customization.ui

import com.stupidsavacan.clocky.customization.model.WidgetSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetWeightEditorTest {
    @Test
    fun progressRoundTripPreservesArbitraryWeight() {
        val requested = 575
        val progress = WidgetWeightEditor.weightToProgress(requested)

        assertEquals(475, progress)
        assertEquals(requested, WidgetWeightEditor.progressToWeight(progress))
    }

    @Test
    fun conversionClampsOutsideSupportedRange() {
        assertEquals(0, WidgetWeightEditor.weightToProgress(20))
        assertEquals(800, WidgetWeightEditor.weightToProgress(1200))
        assertEquals(100, WidgetWeightEditor.progressToWeight(-50))
        assertEquals(900, WidgetWeightEditor.progressToWeight(9999))
    }

    @Test
    fun applyWeightsChangesOnlyRequestedWeights() {
        val original = WidgetSettings(appWidgetId = 42)
        val updated = WidgetWeightEditor.applyWeights(original, 575, 430)

        assertEquals(575, updated.time.requestedWeight)
        assertEquals(430, updated.date.requestedWeight)
        assertEquals(original.time.sizeSp, updated.time.sizeSp)
        assertEquals(original.date.sizeSp, updated.date.sizeSp)
        assertEquals(original.background, updated.background)
    }
}
