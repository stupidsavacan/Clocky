package com.stupidsavacan.clocky.widget.digital

import android.view.View
import android.widget.FrameLayout
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement
import com.stupidsavacan.clocky.design.resolve.DesignResolver
import com.stupidsavacan.clocky.design.resolve.ResolvedDigitalSpec
import com.stupidsavacan.clocky.design.resolve.SizeContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Preview, fit and the placed widget all consume one resolved spec, so the effective font decides all three.
 * Robolectric cannot reproduce a launcher's restricted Context; these tests inject the host answer through the
 * same environment() entry point the provider and Preview both use, never "same-package font worked = supported".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 34])
class HostFontParityTest {
    private val context = RuntimeEnvironment.getApplication()
    private val card = SizeContext(363, 132, 667, 260)

    @After fun reset() {
        HostFontCapability.probeOverride = null
    }

    private fun spec(font: String, hostBundled: Boolean): ResolvedDigitalSpec {
        HostFontCapability.probeOverride = { HostFontCapability.Support(hostBundled, hostBundled) }
        val design = DigitalDesign(time = TimeElement(TextStyle(fontId = font, weight = 400, sizeSp = 64f)))
        return DesignResolver.resolve(design, card, DigitalWidgetUpdater.environment(context))
    }

    private fun naturalWidth(spec: ResolvedDigitalSpec): Int {
        val root = DigitalWidgetComposer.compose(context, spec, DigitalWidgetFit.requested(context, spec))
            .apply(context, FrameLayout(context))
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        // Same worst-case strings the fit measures, so the target lies between the real fit widths.
        val is24 = android.text.format.DateFormat.is24HourFormat(context)
        val time = DigitalWidgetFit.visibleTextIn(root, com.android.deskclock.R.id.clocky_time_slot)!!
        time.text = DigitalWidgetFit.widestTime(time, if (is24) spec.timeFormats.format24Hour else spec.timeFormats.format12Hour)
        val date = DigitalWidgetFit.visibleTextIn(root, com.android.deskclock.R.id.clocky_date_slot)!!
        date.text = DigitalWidgetFit.widestDate(date, spec.datePattern)
        root.measure(unspecified, unspecified)
        return root.measuredWidth
    }

    @Test fun previewAndProviderTakeTheHostAnswerFromTheSameEnvironment() {
        HostFontCapability.probeOverride = { HostFontCapability.Support.NONE }
        assertEquals(false, DigitalWidgetUpdater.environment(context).supportsBundledFonts)
        assertEquals(false, DigitalWidgetUpdater.environment(context, forEditor = true).supportsBundledFonts)
    }

    @Test fun requestedFontIdSurvivesResolutionAndTheCodecRoundTrip() {
        val design = DigitalDesign(time = TimeElement(TextStyle(fontId = "clocky-poppins", weight = 400, sizeSp = 64f)))
        HostFontCapability.probeOverride = { HostFontCapability.Support.NONE }
        DesignResolver.resolve(design, card, DigitalWidgetUpdater.environment(context))
        val stored = com.stupidsavacan.clocky.design.storage.DigitalDesignCodec.encode(design)
        assertEquals("clocky-poppins", com.stupidsavacan.clocky.design.storage.DigitalDesignCodec.decode(stored).time.style.fontId)
        assertEquals(2, stored.getInt("schema"))
    }

    @Test fun fitMeasuresTheEffectiveFallbackNotTheRequestedBundledFace() {
        val requested = "clocky-bebas-neue"
        val supported = spec(requested, hostBundled = true)
        val fallback = spec(requested, hostBundled = false)
        val system = spec(FontIds.SYSTEM_SANS, hostBundled = true)

        val bebasWidth = naturalWidth(supported)
        val systemWidth = naturalWidth(system)
        assertTrue("fixture needs a narrower requested face: $bebasWidth vs $systemWidth", bebasWidth < systemWidth)
        assertEquals("fallback renders as system sans", systemWidth, naturalWidth(fallback))

        // A target between the two widths: Bebas would fit unscaled, system sans must shrink.
        val target = (bebasWidth + systemWidth) / 2
        val tall = 4000
        val requestedSizes = DigitalWidgetFit.requested(context, fallback)
        val bebasFit = DigitalWidgetFit.fit(context, supported, target, tall)
        val fallbackFit = DigitalWidgetFit.fit(context, fallback, target, tall)
        assertEquals("a capable host keeps the requested size", requestedSizes.timePx, bebasFit.timePx, 0f)
        assertTrue("the fallback face must be fit by its own metrics", fallbackFit.timePx < requestedSizes.timePx)
        assertEquals(DigitalWidgetFit.fit(context, system, target, tall), fallbackFit)
    }
}
