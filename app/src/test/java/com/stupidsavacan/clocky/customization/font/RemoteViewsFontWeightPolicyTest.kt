package com.stupidsavacan.clocky.customization.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteViewsFontWeightPolicyTest {
    @Test
    fun api28AndLaterExposeEveryCanonicalWeight() {
        val resolved = RemoteViewsFontWeightPolicy.resolve(600, 28)

        assertEquals(600, resolved.effective)
        assertTrue(resolved.exact)
        assertEquals(5, RemoteViewsFontWeightPolicy.canonicalIndex(resolved.effective))
    }

    @Test
    fun legacyDevicesResolveToAvailablePlatformFaces() {
        val extraLight = RemoteViewsFontWeightPolicy.resolve(200, 27)
        val semiBold = RemoteViewsFontWeightPolicy.resolve(600, 27)
        val extraBold = RemoteViewsFontWeightPolicy.resolve(800, 27)

        assertEquals(100, extraLight.effective)
        assertEquals(500, semiBold.effective)
        assertEquals(700, extraBold.effective)
        assertFalse(extraLight.exact)
        assertFalse(semiBold.exact)
        assertFalse(extraBold.exact)
    }

    @Test
    fun requestedValueIsStillClampedToClockySemanticRange() {
        val low = RemoteViewsFontWeightPolicy.resolve(20, 35)
        val high = RemoteViewsFontWeightPolicy.resolve(1200, 35)

        assertEquals(100, low.requested)
        assertEquals(100, low.effective)
        assertEquals(900, high.requested)
        assertEquals(900, high.effective)
    }
}
