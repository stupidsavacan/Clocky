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
        assertTrue(state.timeInherited)
        assertTrue(state.dateInherited)
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
        assertFalse(state.timeInherited)
        assertFalse(state.dateInherited)
    }

    @Test
    fun partialStateKeepsPerFieldInheritance() {
        val state = WidgetProfileWeightEditor.state(
            ProfileOverride(timeWeight = 650, dateWeight = null),
            400,
            430,
        )

        assertTrue(state.enabled)
        assertEquals(650, state.timeWeight)
        assertEquals(430, state.dateWeight)
        assertFalse(state.timeInherited)
        assertTrue(state.dateInherited)
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
    fun enabledUpdatePreservesInheritedNullAndOtherFields() {
        val original = ProfileOverride(
            timeWeight = 650,
            dateWeight = null,
            timeSizeSp = 50f,
            dateYDp = 6f,
            dateEnabled = false,
        )

        val updated = WidgetProfileWeightEditor.updateOverride(
            original,
            enabled = true,
            timeWeight = 700,
            dateWeight = null,
        )

        requireNotNull(updated)
        assertEquals(700, updated.timeWeight)
        assertNull(updated.dateWeight)
        assertEquals(50f, updated.timeSizeSp)
        assertEquals(6f, updated.dateYDp)
        assertEquals(false, updated.dateEnabled)
    }

    @Test
    fun enabledUpdateClampsExplicitWeightsToStorageRange() {
        val updated = WidgetProfileWeightEditor.updateOverride(
            original = null,
            enabled = true,
            timeWeight = 50,
            dateWeight = 950,
        )

        requireNotNull(updated)
        assertEquals(100, updated.timeWeight)
        assertEquals(900, updated.dateWeight)
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

    @Test
    fun applyKeepsProfilesIndependentIncludingInheritedFields() {
        val settings = WidgetSettings(
            appWidgetId = 12,
            time = TimeSettings(requestedWeight = 575),
            date = DateSettings(requestedWeight = 430),
            fourByOne = ProfileOverride(timeSizeSp = 42f),
            fourByTwo = ProfileOverride(dateYDp = 3f),
        )

        val updated = WidgetProfileWeightEditor.apply(
            settings,
            fourByOneEnabled = true,
            fourByOneTimeWeight = 700,
            fourByOneDateWeight = null,
            fourByTwoEnabled = true,
            fourByTwoTimeWeight = null,
            fourByTwoDateWeight = 600,
        )

        assertEquals(700, updated.fourByOne?.timeWeight)
        assertNull(updated.fourByOne?.dateWeight)
        assertEquals(42f, updated.fourByOne?.timeSizeSp)
        assertNull(updated.fourByTwo?.timeWeight)
        assertEquals(600, updated.fourByTwo?.dateWeight)
        assertEquals(3f, updated.fourByTwo?.dateYDp)
    }
}
