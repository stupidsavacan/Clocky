package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.model.*
import org.junit.Assert.*
import org.junit.Test

class AmPmMarkerResolverTest {
    private val size = SizeContext(363, 132, 667, 260)
    private fun resolve(d: DigitalDesign, sdk: Int) = DesignResolver.resolve(d, size, RenderEnvironment(sdk, false, "EEE"))

    @Test fun sdkChoosesTheMarkerWithoutChangingTheStoredRequest() {
        val d = DigitalDesign(behavior = Behavior(hourMode = HourMode.FORCE_12_HOUR, amPm = AmPmStyle(AmPmMode.SUFFIX, 0.5f), showSeconds = true))
        val before = d.copy()
        for (sdk in listOf(23, 24, 25, 26, 28, 31, 34, 35)) {
            val spec = resolve(d, sdk)
            val marker = spec.amPm!!
            assertEquals(sdk >= 26, marker.useLatinMarkerFont)
            assertEquals(if (sdk >= 26) "HH" else "a", marker.format12Hour)
            assertEquals(marker.format12Hour, marker.format24Hour)
            assertEquals(sdk < 26, Degradation.AmPmLocalized in spec.degradations)
            assertEquals(0.5f, marker.scale)
            assertEquals(TimeFormats("h:mm:ss", "h:mm:ss"), spec.timeFormats)
            assertEquals(before, d)
        }
    }

    @Test fun followSystemRetainsItsEmptyTwentyFourHourMarker() {
        val d = DigitalDesign(behavior = Behavior(amPm = AmPmStyle(AmPmMode.SUFFIX)))
        for (sdk in listOf(23, 25, 26, 34)) assertEquals("", resolve(d, sdk).amPm!!.format24Hour)
    }

    @Test fun disabledAndForceTwentyFourShowNoMarkerOrLocalizationNotice() {
        val forced = DigitalDesign(behavior = Behavior(hourMode = HourMode.FORCE_24_HOUR, amPm = AmPmStyle(AmPmMode.SUFFIX)))
        for (sdk in listOf(23, 25, 26, 34)) for (d in listOf(DigitalDesign(), forced)) {
            val spec = resolve(d, sdk)
            assertNull(spec.amPm)
            assertFalse(Degradation.AmPmLocalized in spec.degradations)
        }
    }
}
