package com.stupidsavacan.clocky.customization.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontWeightResolverTest {
    @Test
    fun clampsRequestedWeightToSupportedSemanticRange() {
        val low = FontWeightResolver.resolve(20, FontCapabilities())
        val high = FontWeightResolver.resolve(1200, FontCapabilities())

        assertEquals(100, low.requested)
        assertEquals(100, low.effective)
        assertEquals(900, high.requested)
        assertEquals(900, high.effective)
    }

    @Test
    fun preservesExactVariableFontWeightWhenInRange() {
        val resolved = FontWeightResolver.resolve(
            563,
            FontCapabilities(variableWeightRange = 100..900),
        )

        assertEquals(563, resolved.requested)
        assertEquals(563, resolved.effective)
        assertTrue(resolved.exact)
        assertEquals("'wght' 563", resolved.variationSettings)
        assertEquals("variable-font-exact", resolved.reason)
    }

    @Test
    fun clampsVariableFontWeightToFontRange() {
        val resolved = FontWeightResolver.resolve(
            250,
            FontCapabilities(variableWeightRange = 300..700),
        )

        assertEquals(250, resolved.requested)
        assertEquals(300, resolved.effective)
        assertFalse(resolved.exact)
        assertEquals("'wght' 300", resolved.variationSettings)
        assertEquals("variable-font-clamped", resolved.reason)
    }

    @Test
    fun preservesExactStaticFaceWhenAvailable() {
        val resolved = FontWeightResolver.resolve(
            600,
            FontCapabilities(staticWeights = setOf(400, 600, 700)),
        )

        assertEquals(600, resolved.requested)
        assertEquals(600, resolved.effective)
        assertTrue(resolved.exact)
        assertNull(resolved.variationSettings)
        assertEquals("static-face-exact", resolved.reason)
    }

    @Test
    fun choosesNearestStaticFaceWithLowerWeightAsTieBreaker() {
        val resolved = FontWeightResolver.resolve(
            550,
            FontCapabilities(staticWeights = setOf(500, 600)),
        )

        assertEquals(500, resolved.effective)
        assertFalse(resolved.exact)
        assertNull(resolved.variationSettings)
        assertEquals("nearest-static-face", resolved.reason)
    }

    @Test
    fun fallsBackToCanonicalWeightsWhenStaticSetIsEmpty() {
        val resolved = FontWeightResolver.resolve(
            640,
            FontCapabilities(staticWeights = emptySet()),
        )

        assertEquals(600, resolved.effective)
        assertEquals("nearest-static-face", resolved.reason)
    }
}
