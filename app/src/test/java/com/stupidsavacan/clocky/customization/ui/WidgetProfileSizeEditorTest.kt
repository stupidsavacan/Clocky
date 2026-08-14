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

class WidgetProfileSizeEditorTest {
    @Test
    fun stateKeepsPerFieldInheritance() {
        val state = WidgetProfileSizeEditor.state(
            ProfileOverride(timeSizeSp = 52f),
            baseTimeSizeSp = 64f,
            baseDateSizeSp = 14f,
        )

        assertTrue(state.enabled)
        assertFalse(state.timeInherited)
        assertTrue(state.dateInherited)
        assertEquals(52f, state.timeSizeSp, 0.001f)
        assertEquals(14f, state.dateSizeSp, 0.001f)
    }

    @Test
    fun disablingSizeOverridePreservesOtherProfileFields() {
        val original = ProfileOverride(
            timeWeight = 575,
            dateWeight = 650,
            timeSizeSp = 52f,
            dateSizeSp = 16f,
            timeXDp = 3f,
            dateYDp = -2f,
            dateEnabled = false,
        )

        val updated = WidgetProfileSizeEditor.updateOverride(
            original = original,
            enabled = false,
            timeSizeSp = null,
            dateSizeSp = null,
        )

        requireNotNull(updated)
        assertNull(updated.timeSizeSp)
        assertNull(updated.dateSizeSp)
        assertEquals(575, updated.timeWeight)
        assertEquals(650, updated.dateWeight)
        assertEquals(3f, updated.timeXDp ?: 0f, 0.001f)
        assertEquals(-2f, updated.dateYDp ?: 0f, 0.001f)
        assertFalse(updated.dateEnabled ?: true)
    }

    @Test
    fun disablingSizeOnlyProfileCollapsesToNull() {
        assertNull(
            WidgetProfileSizeEditor.updateOverride(
                original = ProfileOverride(timeSizeSp = 48f, dateSizeSp = 12f),
                enabled = false,
                timeSizeSp = null,
                dateSizeSp = null,
            )
        )
    }

    @Test
    fun enabledProfileCanOverrideOnlyOneSizeField() {
        val updated = WidgetProfileSizeEditor.updateOverride(
            original = ProfileOverride(dateWeight = 650),
            enabled = true,
            timeSizeSp = 50f,
            dateSizeSp = null,
        )

        requireNotNull(updated)
        assertEquals(50f, updated.timeSizeSp ?: 0f, 0.001f)
        assertNull(updated.dateSizeSp)
        assertEquals(650, updated.dateWeight)
    }

    @Test
    fun applyUpdatesProfilesIndependentlyAndClampsMinimum() {
        val settings = WidgetSettings(
            appWidgetId = 20,
            time = TimeSettings(sizeSp = 64f),
            date = DateSettings(sizeSp = 14f),
            fourByOne = ProfileOverride(timeWeight = 575),
            fourByTwo = ProfileOverride(dateEnabled = false),
        )

        val updated = WidgetProfileSizeEditor.apply(
            settings = settings,
            fourByOneEnabled = true,
            fourByOneTimeSizeSp = 0.25f,
            fourByOneDateSizeSp = null,
            fourByTwoEnabled = true,
            fourByTwoTimeSizeSp = null,
            fourByTwoDateSizeSp = 18f,
        )

        assertEquals(1f, updated.fourByOne?.timeSizeSp ?: 0f, 0.001f)
        assertNull(updated.fourByOne?.dateSizeSp)
        assertEquals(575, updated.fourByOne?.timeWeight)
        assertNull(updated.fourByTwo?.timeSizeSp)
        assertEquals(18f, updated.fourByTwo?.dateSizeSp ?: 0f, 0.001f)
        assertFalse(updated.fourByTwo?.dateEnabled ?: true)
    }
}
