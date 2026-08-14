package com.stupidsavacan.clocky.customization.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DigitalWidgetProfileResolverTest {
    @Test
    fun compactBoundaryMapsToFourByOne() {
        assertEquals(DigitalWidgetProfile.FOUR_BY_ONE, DigitalWidgetProfileResolver.profileForHeightDp(59))
        assertEquals(DigitalWidgetProfile.FOUR_BY_ONE, DigitalWidgetProfileResolver.profileForHeightDp(94))
    }

    @Test
    fun regularHeightMapsToFourByTwo() {
        assertEquals(DigitalWidgetProfile.FOUR_BY_TWO, DigitalWidgetProfileResolver.profileForHeightDp(95))
        assertEquals(DigitalWidgetProfile.FOUR_BY_TWO, DigitalWidgetProfileResolver.profileForHeightDp(129))
    }

    @Test
    fun missingHostHeightFallsBackToRegularProfile() {
        assertEquals(DigitalWidgetProfile.FOUR_BY_TWO, DigitalWidgetProfileResolver.profileForHeightDp(0))
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
    fun profileSizesOverrideBaseIndependently() {
        val settings = WidgetSettings(
            appWidgetId = 8,
            time = TimeSettings(sizeSp = 64f),
            date = DateSettings(sizeSp = 14f),
            fourByOne = ProfileOverride(timeSizeSp = 52f),
            fourByTwo = ProfileOverride(dateSizeSp = 16f),
        )

        val compact = DigitalWidgetProfileResolver.resolveSizes(settings, 59)
        assertEquals(DigitalWidgetProfile.FOUR_BY_ONE, compact.profile)
        assertEquals(52f, compact.timeSizeSp)
        assertEquals(14f, compact.dateSizeSp)

        val regular = DigitalWidgetProfileResolver.resolveSizes(settings, 129)
        assertEquals(DigitalWidgetProfile.FOUR_BY_TWO, regular.profile)
        assertEquals(64f, regular.timeSizeSp)
        assertEquals(16f, regular.dateSizeSp)
    }

    @Test
    fun resolvedSizesNeverDropBelowOneSp() {
        val settings = WidgetSettings(
            appWidgetId = 9,
            time = TimeSettings(sizeSp = 0.25f),
            date = DateSettings(sizeSp = -5f),
        )

        val resolved = DigitalWidgetProfileResolver.resolveSizes(settings, 129)
        assertEquals(1f, resolved.timeSizeSp)
        assertEquals(1f, resolved.dateSizeSp)
    }
}
