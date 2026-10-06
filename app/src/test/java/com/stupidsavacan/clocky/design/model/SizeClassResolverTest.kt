package com.stupidsavacan.clocky.design.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SizeClassResolverTest {
    @Test
    fun heightsMeasuredOnRealHostsInPhase0KeepTheirClass() {
        // 4x1: moto g13 58dp, API 25 emulator 58dp, API 30 emulator 54dp.
        listOf(54, 58).forEach { assertEquals(SizeClass.STRIP, SizeClassResolver.resolve(it)) }
        // 4x2: moto g13 132dp, API 25 132dp, API 30 125dp; Issue #31 fresh add 191dp.
        listOf(125, 132, 191).forEach { assertEquals(SizeClass.CARD, SizeClassResolver.resolve(it)) }
    }

    @Test
    fun boundaryIsBelow100dp() {
        assertEquals(SizeClass.STRIP, SizeClassResolver.resolve(1))
        assertEquals(SizeClass.STRIP, SizeClassResolver.resolve(99))
        assertEquals(SizeClass.CARD, SizeClassResolver.resolve(100))
    }

    @Test
    fun missingOrInvalidOptionsUseCard() {
        assertEquals(SizeClass.CARD, SizeClassResolver.resolve(0))
        assertEquals(SizeClass.CARD, SizeClassResolver.resolve(-5))
    }
}
