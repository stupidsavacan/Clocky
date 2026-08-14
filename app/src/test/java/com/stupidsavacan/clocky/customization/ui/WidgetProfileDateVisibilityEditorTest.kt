package com.stupidsavacan.clocky.customization.ui

import com.stupidsavacan.clocky.customization.model.DateSettings
import com.stupidsavacan.clocky.customization.model.ProfileOverride
import com.stupidsavacan.clocky.customization.model.WidgetSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetProfileDateVisibilityEditorTest {
    @Test
    fun nullableValueMapsToThreeStateMode() {
        assertEquals(
            WidgetProfileDateVisibilityEditor.Mode.INHERIT,
            WidgetProfileDateVisibilityEditor.mode(null),
        )
        assertEquals(
            WidgetProfileDateVisibilityEditor.Mode.SHOW,
            WidgetProfileDateVisibilityEditor.mode(true),
        )
        assertEquals(
            WidgetProfileDateVisibilityEditor.Mode.HIDE,
            WidgetProfileDateVisibilityEditor.mode(false),
        )
    }

    @Test
    fun inheritClearsOnlyDateVisibilityAndPreservesWeightOverrides() {
        val original = ProfileOverride(
            timeWeight = 650,
            dateWeight = 350,
            dateEnabled = false,
        )

        val updated = WidgetProfileDateVisibilityEditor.updateOverride(
            original,
            WidgetProfileDateVisibilityEditor.Mode.INHERIT,
        )

        requireNotNull(updated)
        assertEquals(650, updated.timeWeight)
        assertEquals(350, updated.dateWeight)
        assertNull(updated.dateEnabled)
    }

    @Test
    fun inheritCollapsesDateOnlyProfileToNull() {
        assertNull(
            WidgetProfileDateVisibilityEditor.updateOverride(
                ProfileOverride(dateEnabled = true),
                WidgetProfileDateVisibilityEditor.Mode.INHERIT,
            )
        )
    }

    @Test
    fun showAndHideCreateExplicitOverrides() {
        val show = WidgetProfileDateVisibilityEditor.updateOverride(
            null,
            WidgetProfileDateVisibilityEditor.Mode.SHOW,
        )
        val hide = WidgetProfileDateVisibilityEditor.updateOverride(
            null,
            WidgetProfileDateVisibilityEditor.Mode.HIDE,
        )

        assertTrue(show?.dateEnabled == true)
        assertFalse(hide?.dateEnabled ?: true)
    }

    @Test
    fun applyUpdatesBaseAndProfilesIndependently() {
        val settings = WidgetSettings(
            appWidgetId = 18,
            date = DateSettings(enabled = true),
            fourByOne = ProfileOverride(timeWeight = 575),
            fourByTwo = ProfileOverride(dateWeight = 650),
        )

        val updated = WidgetProfileDateVisibilityEditor.apply(
            settings,
            baseDateEnabled = false,
            fourByOneMode = WidgetProfileDateVisibilityEditor.Mode.SHOW,
            fourByTwoMode = WidgetProfileDateVisibilityEditor.Mode.HIDE,
        )

        assertFalse(updated.date.enabled)
        assertEquals(575, updated.fourByOne?.timeWeight)
        assertTrue(updated.fourByOne?.dateEnabled == true)
        assertEquals(650, updated.fourByTwo?.dateWeight)
        assertFalse(updated.fourByTwo?.dateEnabled ?: true)
    }
}
