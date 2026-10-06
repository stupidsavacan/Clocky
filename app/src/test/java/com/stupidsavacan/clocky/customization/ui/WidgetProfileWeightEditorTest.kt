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
            LayoutPatch(timeWeight = 650, dateWeight = 350),
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
            LayoutPatch(timeWeight = 650, dateWeight = null),
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
        val original = LayoutPatch(
            timeWeight = 575,
            dateWeight = 430,
            timeSizeSp = 50f,
            dateYDp = 6f,
            dateVisible = false,
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
        assertEquals(false, updated.dateVisible)
    }

    @Test
    fun enabledUpdatePreservesInheritedNullAndOtherFields() {
        val original = LayoutPatch(
            timeWeight = 650,
            dateWeight = null,
            timeSizeSp = 50f,
            dateYDp = 6f,
            dateVisible = false,
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
        assertEquals(false, updated.dateVisible)
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
            LayoutPatch(timeWeight = 600, dateWeight = 500),
            enabled = false,
            timeWeight = 600,
            dateWeight = 500,
        )

        assertNull(updated)
    }

    @Test
    fun applyUpdatesBothProfilesWithoutChangingBase() {
        val settings = DigitalDesign(
            time = TimeElement(TextStyle(sizeSp = 64f, weight = 575)),
            date = DateElement(style = TextStyle(sizeSp = 14f, weight = 430)),
        )

        val updated = WidgetProfileWeightEditor.apply(
            settings,
            stripEnabled = true,
            stripTimeWeight = 700,
            stripDateWeight = 300,
            cardEnabled = true,
            cardTimeWeight = 500,
            cardDateWeight = 600,
        )

        assertEquals(575, updated.time.style.weight)
        assertEquals(430, updated.date.style.weight)
        assertEquals(700, updated.layout.overrides[SizeClass.STRIP]?.timeWeight)
        assertEquals(300, updated.layout.overrides[SizeClass.STRIP]?.dateWeight)
        assertEquals(500, updated.layout.overrides[SizeClass.CARD]?.timeWeight)
        assertEquals(600, updated.layout.overrides[SizeClass.CARD]?.dateWeight)
    }

    @Test
    fun applyKeepsProfilesIndependentIncludingInheritedFields() {
        val settings = DigitalDesign(
            time = TimeElement(TextStyle(sizeSp = 64f, weight = 575)),
            date = DateElement(style = TextStyle(sizeSp = 14f, weight = 430)),
            layout = DesignLayout(

                overrides = mapOf(

                    SizeClass.STRIP to LayoutPatch(timeSizeSp = 42f),

                    SizeClass.CARD to LayoutPatch(dateYDp = 3f),

                ),

            ),
        )

        val updated = WidgetProfileWeightEditor.apply(
            settings,
            stripEnabled = true,
            stripTimeWeight = 700,
            stripDateWeight = null,
            cardEnabled = true,
            cardTimeWeight = null,
            cardDateWeight = 600,
        )

        assertEquals(700, updated.layout.overrides[SizeClass.STRIP]?.timeWeight)
        assertNull(updated.layout.overrides[SizeClass.STRIP]?.dateWeight)
        assertEquals(42f, updated.layout.overrides[SizeClass.STRIP]?.timeSizeSp)
        assertNull(updated.layout.overrides[SizeClass.CARD]?.timeWeight)
        assertEquals(600, updated.layout.overrides[SizeClass.CARD]?.dateWeight)
        assertEquals(3f, updated.layout.overrides[SizeClass.CARD]?.dateYDp)
    }
}
