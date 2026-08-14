package com.stupidsavacan.clocky.customization.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProfileOverrideWeightEditingTest {
    @Test
    fun weightEditingPreservesUnrelatedProfileFields() {
        val original = ProfileOverride(
            timeWeight = 300,
            dateWeight = 400,
            timeSizeSp = 52f,
            dateSizeSp = 16f,
            timeXDp = 3f,
            dateYDp = -2f,
            dateEnabled = false,
        )

        val updated = original.withWeightOverrides(timeWeight = 575, dateWeight = null)
        assertNotNull(updated)
        updated!!
        assertEquals(575, updated.timeWeight)
        assertNull(updated.dateWeight)
        assertEquals(52f, updated.timeSizeSp)
        assertEquals(16f, updated.dateSizeSp)
        assertEquals(3f, updated.timeXDp)
        assertEquals(-2f, updated.dateYDp)
        assertEquals(false, updated.dateEnabled)
    }

    @Test
    fun clearingOnlyWeightsCollapsesEmptyProfileToNull() {
        val original = ProfileOverride(timeWeight = 575, dateWeight = 650)
        assertNull(original.withWeightOverrides(timeWeight = null, dateWeight = null))
    }

    @Test
    fun clearingWeightsKeepsProfileWhenOtherOverridesRemain() {
        val original = ProfileOverride(
            timeWeight = 575,
            dateWeight = 650,
            timeXDp = 8f,
        )

        val updated = original.withWeightOverrides(timeWeight = null, dateWeight = null)
        assertNotNull(updated)
        updated!!
        assertNull(updated.timeWeight)
        assertNull(updated.dateWeight)
        assertEquals(8f, updated.timeXDp)
    }

    @Test
    fun weightInputsAreClampedToSupportedRange() {
        val updated = ProfileOverride().withWeightOverrides(timeWeight = 50, dateWeight = 975)
        assertNotNull(updated)
        updated!!
        assertEquals(100, updated.timeWeight)
        assertEquals(900, updated.dateWeight)
    }
}
