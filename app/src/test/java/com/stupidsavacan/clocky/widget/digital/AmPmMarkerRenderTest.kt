package com.stupidsavacan.clocky.widget.digital

import android.os.Build
import android.view.View
import android.widget.FrameLayout
import android.widget.TextClock
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.model.*
import com.stupidsavacan.clocky.design.resolve.*
import com.stupidsavacan.clocky.design.storage.DigitalDesignCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 25, 26, 28, 31, 34, 35])
class AmPmMarkerRenderTest {
    private val context = RuntimeEnvironment.getApplication()
    private val size = SizeContext(363, 132, 667, 260)
    private val design = DigitalDesign(behavior = Behavior(hourMode = HourMode.FORCE_12_HOUR, amPm = AmPmStyle(AmPmMode.SUFFIX, 0.5f), showSeconds = true))
    private fun spec(d: DigitalDesign, supported: Boolean = false) = DesignResolver.resolve(d, size, DigitalWidgetUpdater.environment(context).copy(supportsLatinAmPmMarker = supported))
    private fun marker(root: View) = DigitalWidgetFit.visibleTextsIn(root, R.id.clocky_time_slot)[1] as TextClock

    @Test fun applyAndReapplyUseTheDedicatedFaceAndExcludeOnlyTheMarkerFromAccessibility() {
        for (supported in listOf(false, true)) for (font in FontCatalog.allIds) for (shadow in ShadowLevel.entries) {
            val d = design.copy(time = design.time.copy(style = design.time.style.copy(fontId = font)), effects = Effects(shadow))
            val s = spec(d, supported)
            val rv = DigitalWidgetComposer.compose(context, s, DigitalWidgetFit.requested(context, s))
            val root = rv.apply(context, FrameLayout(context))
            repeat(2) { rv.reapply(context, root) }
            val clocks = DigitalWidgetFit.visibleTextsIn(root, R.id.clocky_time_slot)
            assertEquals(2, clocks.size)
            val suffix = marker(root)
            assertEquals(if (s.amPm!!.useLatinMarkerFont) "HH" else "a", suffix.format12Hour.toString())
            assertEquals(suffix.format12Hour, suffix.format24Hour)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, suffix.importantForAccessibility)
            assertNotEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, clocks[0].importantForAccessibility)
            assertEquals("h:mm:ss", (clocks[0] as TextClock).format12Hour.toString())
            if (s.amPm!!.useLatinMarkerFont) {
                assertEquals(R.id.clocky_ampm_marker, suffix.id)
                assertEquals(context.resources.getFont(R.font.clocky_ampm_marker), suffix.typeface)
            }
        }
    }

    @Test fun fallbackNeverRewritesTheSavedBehavior() {
        val before = DigitalDesignCodec.encode(design).toString()
        spec(design)
        assertEquals(before, DigitalDesignCodec.encode(design).toString())
        assertEquals(design, DigitalDesignCodec.decode(DigitalDesignCodec.encode(design)))
    }

    @Test fun markerFitsWithTheTimeAndDateAndDisclosesTheLegacyPath() {
        val s = spec(design)
        val fit = DigitalWidgetFit.fit(context, s, 260, 150)
        assertTrue(fit.timePx > 0f)
        assertTrue(fit.timePx <= DigitalWidgetFit.requested(context, s).timePx)
        val root = DigitalWidgetComposer.compose(context, s, fit).apply(context, FrameLayout(context))
        DigitalWidgetFit.visibleTextsIn(root, R.id.clocky_time_slot)[0].text = "12:59:59"
        marker(root).text = if (s.amPm!!.useLatinMarkerFont) "00" else "PM"
        root.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        assertTrue("width ${root.measuredWidth}", root.measuredWidth <= 260)
        assertTrue("height ${root.measuredHeight}", root.measuredHeight <= 150)
        val notes = DegradationNotices.describe(context, s.degradations)
        assertEquals(!s.amPm!!.useLatinMarkerFont, notes.any { it.contains("AM/PM") })
    }

    @Test fun previewAndProviderUseTheSameMarker() {
        val (_, previewRv) = DigitalWidgetUpdater.buildPortrait(context, design, size)
        val providerRv = DigitalWidgetUpdater.build(context, WidgetInstance(23, design), size, null)
        val preview = marker(previewRv.apply(context, FrameLayout(context)))
        val provider = marker(providerRv.apply(context, FrameLayout(context)))
        assertEquals(preview.id, provider.id)
        assertEquals(preview.typeface, provider.typeface)
        assertEquals(preview.textSize, provider.textSize, 0.001f)
        assertEquals(preview.format12Hour, provider.format12Hour)
        assertEquals(preview.format24Hour, provider.format24Hour)
    }

    @Test @Config(qualifiers = "ja") fun japaneseLabelsMatchTheLatinMarker() {
        assertEquals("AM/PM", context.getString(R.string.clocky_studio_ampm))
        assertEquals("AM/PMのサイズ", context.getString(R.string.clocky_studio_ampm_size))
        assertTrue(DegradationNotices.describe(context, spec(design).degradations).any { it.contains("AM/PM →") })
    }
}
