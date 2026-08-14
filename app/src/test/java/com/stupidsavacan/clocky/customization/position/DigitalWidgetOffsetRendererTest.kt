package com.stupidsavacan.clocky.customization.position

import com.stupidsavacan.clocky.customization.model.DigitalWidgetProfile
import com.stupidsavacan.clocky.customization.model.ResolvedWidgetOffsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalWidgetOffsetRendererTest {
    private val requested = ResolvedWidgetOffsets(
        profile = DigitalWidgetProfile.FOUR_BY_ONE,
        timeXDp = 12f,
        timeYDp = -6f,
        dateXDp = -4f,
        dateYDp = 8f,
    )

    @Test
    fun api31AndLaterRenderRequestedOffsetsExactly() {
        val api31 = DigitalWidgetOffsetRenderer.effectiveOffsets(requested, 31)
        assertEquals(12f, api31.timeXDp, 0.001f)
        assertEquals(-6f, api31.timeYDp, 0.001f)
        assertEquals(-4f, api31.dateXDp, 0.001f)
        assertEquals(8f, api31.dateYDp, 0.001f)
        assertTrue(api31.exact)

        val api35 = DigitalWidgetOffsetRenderer.effectiveOffsets(requested, 35)
        assertEquals(api31, api35)
    }

    @Test
    fun api30AndEarlierFallBackToZeroWithoutLosingRequestedContract() {
        val effective = DigitalWidgetOffsetRenderer.effectiveOffsets(requested, 30)
        assertEquals(0f, effective.timeXDp, 0.001f)
        assertEquals(0f, effective.timeYDp, 0.001f)
        assertEquals(0f, effective.dateXDp, 0.001f)
        assertEquals(0f, effective.dateYDp, 0.001f)
        assertFalse(effective.exact)
    }

    @Test
    fun zeroOffsetsAreExactEvenOnLegacyRemoteViews() {
        val zero = ResolvedWidgetOffsets(
            profile = DigitalWidgetProfile.FOUR_BY_TWO,
            timeXDp = 0f,
            timeYDp = 0f,
            dateXDp = 0f,
            dateYDp = 0f,
        )
        assertTrue(DigitalWidgetOffsetRenderer.effectiveOffsets(zero, 23).exact)
    }
}
