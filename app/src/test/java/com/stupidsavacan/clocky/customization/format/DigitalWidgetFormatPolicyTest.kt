package com.stupidsavacan.clocky.customization.format

import com.stupidsavacan.clocky.customization.model.HourMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DigitalWidgetFormatPolicyTest {
    @Test
    fun followSystemLeavesExistingTextClockFormatsUntouched() {
        assertNull(DigitalWidgetFormatPolicy.timeOverride(HourMode.FOLLOW_SYSTEM))
    }

    @Test
    fun explicitHourModesMatchRepositoryMvpFormats() {
        assertEquals("h:mm", DigitalWidgetFormatPolicy.timeOverride(HourMode.FORCE_12_HOUR))
        assertEquals("HH:mm", DigitalWidgetFormatPolicy.timeOverride(HourMode.FORCE_24_HOUR))
    }
}
