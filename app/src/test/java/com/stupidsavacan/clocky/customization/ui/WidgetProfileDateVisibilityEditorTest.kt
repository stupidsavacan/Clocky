package com.stupidsavacan.clocky.customization.ui

import com.stupidsavacan.clocky.design.model.DateElement
import com.stupidsavacan.clocky.design.model.DesignLayout
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement
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
        val original = LayoutPatch(
            timeWeight = 650,
            dateWeight = 350,
            dateVisible = false,
        )

        val updated = WidgetProfileDateVisibilityEditor.updateOverride(
            original,
            WidgetProfileDateVisibilityEditor.Mode.INHERIT,
        )

        requireNotNull(updated)
        assertEquals(650, updated.timeWeight)
        assertEquals(350, updated.dateWeight)
        assertNull(updated.dateVisible)
    }

    @Test
    fun inheritCollapsesDateOnlyProfileToNull() {
        assertNull(
            WidgetProfileDateVisibilityEditor.updateOverride(
                LayoutPatch(dateVisible = true),
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

        assertTrue(show?.dateVisible == true)
        assertFalse(hide?.dateVisible ?: true)
    }

    @Test
    fun applyUpdatesBaseAndProfilesIndependently() {
        val settings = DigitalDesign(
            date = DateElement(visible = true),
            layout = DesignLayout(

                overrides = mapOf(

                    SizeClass.STRIP to LayoutPatch(timeWeight = 575),

                    SizeClass.CARD to LayoutPatch(dateWeight = 650),

                ),

            ),
        )

        val updated = WidgetProfileDateVisibilityEditor.apply(
            settings,
            baseDateVisible = false,
            stripMode = WidgetProfileDateVisibilityEditor.Mode.SHOW,
            cardMode = WidgetProfileDateVisibilityEditor.Mode.HIDE,
        )

        assertFalse(updated.date.visible)
        assertEquals(575, updated.layout.overrides[SizeClass.STRIP]?.timeWeight)
        assertTrue(updated.layout.overrides[SizeClass.STRIP]?.dateVisible == true)
        assertEquals(650, updated.layout.overrides[SizeClass.CARD]?.dateWeight)
        assertFalse(updated.layout.overrides[SizeClass.CARD]?.dateVisible ?: true)
    }
}
