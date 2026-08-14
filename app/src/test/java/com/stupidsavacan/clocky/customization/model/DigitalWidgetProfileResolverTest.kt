package com.stupidsavacan.clocky.customization.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalWidgetProfileResolverTest {
    @Test
    fun compactBoundaryMapsToFourByOne() {
        assertEquals(
            DigitalWidgetProfile.FOUR_BY_ONE,
            DigitalWidgetProfileResolver.profileForHeightDp(59),
        )
        assertEquals(
            DigitalWidgetProfile.FOUR_BY_ONE,
            DigitalWidgetProfileResolver.profileForHeightDp(94),
        )
    }

    @Test
    fun regularHeightMapsToFourByTwo() {
        assertEquals(
            DigitalWidgetProfile.FOUR_BY_TWO,
            DigitalWidgetProfileResolver.profileForHeightDp(95),
        )
        assertEquals(
            DigitalWidgetProfile.FOUR_BY_TWO,
            DigitalWidgetProfileResolver.profileForHeightDp(129),
        )
    }

    @Test
    fun missingHostHeightFallsBackToRegularProfile() {
        assertEquals(
            DigitalWidgetProfile.FOUR_BY_TWO,
            DigitalWidgetProfileResolver.profileForHeightDp(0),
        )
    }

    @Test
    fun profileWeightsOverrideBaseIndependently() {
        val settings = WidgetSettings(
            appWidgetId = 7,
            time = TimeSettings(requestedWeight = 400),
            date = DateSettings(requestedWeight = 300),
            fourByOne = ProfileOverride(timeWeight = 575),
            fourByTwo = ProfileOverride(dateWeight = 650),
        )

        val compact = DigitalWidgetProfileResolver.resolveWeights(settings, 59)
        assertEquals(DigitalWidgetProfile.FOUR_BY_ONE, compact.profile)
        assertEquals(575, compact.timeWeight)
        assertEquals(300, compact.dateWeight)

        val regular = DigitalWidgetProfileResolver.resolveWeights(settings, 129)
        assertEquals(DigitalWidgetProfile.FOUR_BY_TWO, regular.profile)
        assertEquals(400, regular.timeWeight)
        assertEquals(650, regular.dateWeight)
    }

    @Test
    fun dateVisibilityInheritsBaseWhenProfileDoesNotOverrideIt() {
        val hidden = WidgetSettings(
            appWidgetId = 8,
            date = DateSettings(enabled = false),
            fourByOne = ProfileOverride(timeWeight = 575),
        )
        val visible = WidgetSettings(
            appWidgetId = 9,
            date = DateSettings(enabled = true),
            fourByTwo = ProfileOverride(dateWeight = 650),
        )

        assertFalse(DigitalWidgetProfileResolver.resolveWeights(hidden, 59).dateEnabled)
        assertTrue(DigitalWidgetProfileResolver.resolveWeights(visible, 129).dateEnabled)
    }

    @Test
    fun profileDateVisibilityOverridesBaseIndependently() {
        val settings = WidgetSettings(
            appWidgetId = 10,
            date = DateSettings(enabled = true),
            fourByOne = ProfileOverride(dateEnabled = false),
            fourByTwo = ProfileOverride(dateEnabled = true),
        )

        assertFalse(DigitalWidgetProfileResolver.resolveWeights(settings, 59).dateEnabled)
        assertTrue(DigitalWidgetProfileResolver.resolveWeights(settings, 129).dateEnabled)
    }
}
