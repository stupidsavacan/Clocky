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

class WidgetProfileSizeEditorTest {
    @Test
    fun stateKeepsPerFieldInheritance() {
        val state = WidgetProfileSizeEditor.state(
            LayoutPatch(timeSizeSp = 52f),
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
        val original = LayoutPatch(
            timeWeight = 575,
            dateWeight = 650,
            timeSizeSp = 52f,
            dateSizeSp = 16f,
            timeXDp = 3f,
            dateYDp = -2f,
            dateVisible = false,
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
        assertFalse(updated.dateVisible ?: true)
    }

    @Test
    fun disablingSizeOnlyProfileCollapsesToNull() {
        assertNull(
            WidgetProfileSizeEditor.updateOverride(
                original = LayoutPatch(timeSizeSp = 48f, dateSizeSp = 12f),
                enabled = false,
                timeSizeSp = null,
                dateSizeSp = null,
            )
        )
    }

    @Test
    fun enabledProfileCanOverrideOnlyOneSizeField() {
        val updated = WidgetProfileSizeEditor.updateOverride(
            original = LayoutPatch(dateWeight = 650),
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
        val settings = DigitalDesign(
            time = TimeElement(TextStyle(sizeSp = 64f)),
            date = DateElement(style = TextStyle(sizeSp = 14f)),
            layout = DesignLayout(

                overrides = mapOf(

                    SizeClass.STRIP to LayoutPatch(timeWeight = 575),

                    SizeClass.CARD to LayoutPatch(dateVisible = false),

                ),

            ),
        )

        val updated = WidgetProfileSizeEditor.apply(
            design = settings,
            stripEnabled = true,
            stripTimeSizeSp = 0.25f,
            stripDateSizeSp = null,
            cardEnabled = true,
            cardTimeSizeSp = null,
            cardDateSizeSp = 18f,
        )

        assertEquals(1f, updated.layout.overrides[SizeClass.STRIP]?.timeSizeSp ?: 0f, 0.001f)
        assertNull(updated.layout.overrides[SizeClass.STRIP]?.dateSizeSp)
        assertEquals(575, updated.layout.overrides[SizeClass.STRIP]?.timeWeight)
        assertNull(updated.layout.overrides[SizeClass.CARD]?.timeSizeSp)
        assertEquals(18f, updated.layout.overrides[SizeClass.CARD]?.dateSizeSp ?: 0f, 0.001f)
        assertFalse(updated.layout.overrides[SizeClass.CARD]?.dateVisible ?: true)
    }
}
