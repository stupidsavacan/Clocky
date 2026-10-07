package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.model.*
import org.junit.Assert.*
import org.junit.Test

class AmPmMarkerResolverTest {
    private val size = SizeContext(363, 132, 667, 260)
    private fun resolve(d: DigitalDesign, sdk: Int, supported: Boolean = false) = DesignResolver.resolve(d, size, RenderEnvironment(sdk, false, "EEE", supportsLatinAmPmMarker = supported))

    @Test fun sdkAndHostCapabilityChooseTheMarkerWithoutChangingTheStoredRequest() {
        val d = DigitalDesign(behavior = Behavior(hourMode = HourMode.FORCE_12_HOUR, amPm = AmPmStyle(AmPmMode.SUFFIX, 0.5f), showSeconds = true))
        val before = d.copy()
        for (sdk in listOf(23, 24, 25, 26, 28, 31, 34, 35)) for (supported in listOf(false, true)) {
            val spec = resolve(d, sdk, supported)
            val latin = sdk >= 26 && supported
            val marker = spec.amPm!!
            assertEquals(latin, marker.useLatinMarkerFont)
            assertEquals(if (latin) "HH" else "a", marker.format12Hour)
            assertEquals(marker.format12Hour, marker.format24Hour)
            assertEquals(!latin, Degradation.AmPmLocalized in spec.degradations)
            assertEquals(0.5f, marker.scale)
            assertEquals(TimeFormats("h:mm:ss", "h:mm:ss"), spec.timeFormats)
            assertEquals(before, d)
        }
    }

    @Test fun followSystemRetainsItsEmptyTwentyFourHourMarker() {
        val d = DigitalDesign(behavior = Behavior(amPm = AmPmStyle(AmPmMode.SUFFIX)))
        for (sdk in listOf(23, 25, 26, 34)) assertEquals("", resolve(d, sdk).amPm!!.format24Hour)
    }

    @Test fun unsupportedHostUsesAPlatformMarkerFaceWithoutChangingTheTimeRequest() {
        for (font in FontCatalog.allIds) {
            val d = DigitalDesign(
                time = TimeElement(style = TimeElement().style.copy(fontId = font)),
                behavior = Behavior(hourMode = HourMode.FORCE_12_HOUR, amPm = AmPmStyle(AmPmMode.SUFFIX)),
            )
            val s = resolve(d, 34, false)
            val expected = if (FontCatalog.family(font)?.source == FontCatalog.Source.BUNDLED) FontIds.SYSTEM_SANS else font
            assertEquals(expected, s.amPm!!.text.face.fontId)
            assertEquals(font, d.time.style.fontId)
            assertEquals(font, s.time.face.fontId)
            assertTrue(Degradation.AmPmLocalized in s.degradations)
        }
    }

    @Test fun disabledAndForceTwentyFourShowNoMarkerOrLocalizationNotice() {
        val forced = DigitalDesign(behavior = Behavior(hourMode = HourMode.FORCE_24_HOUR, amPm = AmPmStyle(AmPmMode.SUFFIX)))
        for (sdk in listOf(23, 25, 26, 34)) for (supported in listOf(false, true)) for (d in listOf(DigitalDesign(), forced)) {
            val spec = resolve(d, sdk, supported)
            assertNull(spec.amPm)
            assertFalse(Degradation.AmPmLocalized in spec.degradations)
        }
    }
}
