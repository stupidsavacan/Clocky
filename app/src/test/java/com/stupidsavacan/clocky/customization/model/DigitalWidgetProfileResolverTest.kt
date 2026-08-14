package com.stupidsavacan.clocky.customization.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun profileSizesOverrideBaseIndependently() {
        val settings = WidgetSettings(
            appWidgetId = 11,
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
            appWidgetId = 12,
            time = TimeSettings(sizeSp = 0.25f),
            date = DateSettings(sizeSp = -5f),
        )

        val resolved = DigitalWidgetProfileResolver.resolveSizes(settings, 129)
        assertEquals(1f, resolved.timeSizeSp)
        assertEquals(1f, resolved.dateSizeSp)
    }

    @Test
    fun profileOffsetsOverrideBasePerField() {
        val settings = WidgetSettings(
            appWidgetId = 13,
            time = TimeSettings(xDp = 2f, yDp = 3f),
            date = DateSettings(xDp = -4f, yDp = 5f),
            fourByOne = ProfileOverride(timeXDp = 12f, dateYDp = -8f),
            fourByTwo = ProfileOverride(timeYDp = 9f, dateXDp = 7f),
        )

        val compact = DigitalWidgetProfileResolver.resolveOffsets(settings, 59)
        assertEquals(DigitalWidgetProfile.FOUR_BY_ONE, compact.profile)
        assertEquals(12f, compact.timeXDp, 0.001f)
        assertEquals(3f, compact.timeYDp, 0.001f)
        assertEquals(-4f, compact.dateXDp, 0.001f)
        assertEquals(-8f, compact.dateYDp, 0.001f)

        val regular = DigitalWidgetProfileResolver.resolveOffsets(settings, 129)
        assertEquals(DigitalWidgetProfile.FOUR_BY_TWO, regular.profile)
        assertEquals(2f, regular.timeXDp, 0.001f)
        assertEquals(9f, regular.timeYDp, 0.001f)
        assertEquals(7f, regular.dateXDp, 0.001f)
        assertEquals(5f, regular.dateYDp, 0.001f)
    }

    @Test
    fun nonFiniteOffsetsAreSanitizedAtResolverBoundary() {
        val settings = WidgetSettings(
            appWidgetId = 14,
            time = TimeSettings(xDp = Float.NaN, yDp = Float.POSITIVE_INFINITY),
            date = DateSettings(xDp = Float.NEGATIVE_INFINITY, yDp = Float.NaN),
        )

        val resolved = DigitalWidgetProfileResolver.resolveOffsets(settings, 129)
        assertEquals(0f, resolved.timeXDp, 0.001f)
        assertEquals(0f, resolved.timeYDp, 0.001f)
        assertEquals(0f, resolved.dateXDp, 0.001f)
        assertEquals(0f, resolved.dateYDp, 0.001f)
    }
}
