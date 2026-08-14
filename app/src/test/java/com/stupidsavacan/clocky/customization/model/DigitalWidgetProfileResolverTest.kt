package com.stupidsavacan.clocky.customization.model

import org.junit.Assert.assertEquals
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
}
