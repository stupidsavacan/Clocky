package com.stupidsavacan.clocky.customization.ui

import com.stupidsavacan.clocky.customization.model.DateSettings
import com.stupidsavacan.clocky.customization.model.ProfileOverride
import com.stupidsavacan.clocky.customization.model.TimeSettings
import com.stupidsavacan.clocky.customization.model.WidgetSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetProfileWeightEditorTest {
    @Test
    fun disabledStateInheritsBaseWeights() {
        val state = WidgetProfileWeightEditor.state(null, 575, 430)

        assertFalse(state.enabled)
        assertEquals(575, state.timeWeight)
        assertEquals(430, state.dateWeight)
    }

    @Test
    fun enabledStateUsesExplicitProfileWeights() {
        val state = WidgetProfileWeightEditor.state(
            ProfileOverride(timeWeight = 650, dateWeight = 350),
            400,
            400,
        )

        assertTrue(state.enabled)
        assertEquals(650, state.timeWeight)
        assertEquals(350, state.dateWeight)
    }

    @Test
    fun disablingWeightsPreservesOtherProfileFields() {
        val original = ProfileOverride(
            timeWeight = 575,
            dateWeight = 430,
            timeSizeSp = 50f,
            dateYDp = 6f,
            dateEnabled = false,
        )

        val updated = WidgetProfileWeightEditor.updateOverride(
            original,
            enabled = false,
            timeWeight = 100,
            dateWeight = 100,
        )

        requireNotNull(updated)
        assertNull(updated.timeWeight)
        assertNull(updated.dateWeight)
        assertEquals(50f, updated.timeSizeSp)
        assertEquals(6f, updated.dateYDp)
        assertEquals(false, updated.dateEnabled)
    }

    @Test
    fun disablingWeightOnlyProfileCollapsesToNull() {
        val updated = WidgetProfileWeightEditor.updateOverride(
            ProfileOverride(timeWeight = 600, dateWeight = 500),
            enabled = false,
            timeWeight = 600,
            dateWeight = 500,
        )

        assertNull(updated)
    }

    @Test
    fun applyUpdatesBothProfilesWithoutChangingBase() {
        val settings = WidgetSettings(
            appWidgetId = 11,
            time = TimeSettings(requestedWeight = 575),
            date = DateSettings(requestedWeight = 430),
        )

        val updated = WidgetProfileWeightEditor.apply(
            settings,
            fourByOneEnabled = true,
            fourByOneTimeWeight = 700,
            fourByOneDateWeight = 300,
            fourByTwoEnabled = true,
            fourByTwoTimeWeight = 500,
            fourByTwoDateWeight = 600,
        )

        assertEquals(575, updated.time.requestedWeight)
        assertEquals(430, updated.date.requestedWeight)
        assertEquals(700, updated.fourByOne?.timeWeight)
        assertEquals(300, updated.fourByOne?.dateWeight)
        assertEquals(500, updated.fourByTwo?.timeWeight)
        assertEquals(600, updated.fourByTwo?.dateWeight)
    }
}
