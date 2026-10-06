package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.BackgroundElement
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.Behavior
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.CornerRadius
import com.stupidsavacan.clocky.design.model.DateElement
import com.stupidsavacan.clocky.design.model.DesignLayout
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.HourMode
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignResolverTest {
    private val strip = SizeContext(minWidthDp = 363, minHeightDp = 58, maxWidthDp = 667, maxHeightDp = 122)
    private val card = SizeContext(minWidthDp = 363, minHeightDp = 132, maxWidthDp = 667, maxHeightDp = 260)
    private val api34 = RenderEnvironment(sdkInt = 34, isRtl = false, localeAutoDatePattern = "EEE, MMM d")
    private val api25 = api34.copy(sdkInt = 25)

    @Test
    fun sizeClassSelectsItsOwnPatchAndNullInherits() {
        val design = DigitalDesign(
            layout = DesignLayout(
                overrides = mapOf(
                    SizeClass.STRIP to LayoutPatch(dateVisible = false, timeSizeSp = 40f),
                    SizeClass.CARD to LayoutPatch(timeWeight = 700),
                ),
            ),
        )

        val s = DesignResolver.resolve(design, strip, api34)
        assertEquals(SizeClass.STRIP, s.sizeClass)
        assertFalse(s.dateVisible)
        assertEquals(40f, s.time.sizeSp)
        assertEquals(400, s.time.face.weight)

        val c = DesignResolver.resolve(design, card, api34)
        assertEquals(SizeClass.CARD, c.sizeClass)
        assertTrue(c.dateVisible)
        assertEquals(64f, c.time.sizeSp)
        assertEquals(700, c.time.face.weight)
    }

    @Test
    fun colorAndOpacityCombineIntoEffectiveArgb() {
        val design = DigitalDesign(
            time = TimeElement(TextStyle(sizeSp = 64f, color = ColorRef.Fixed(0x112233), opacity = 0.5f)),
        )
        assertEquals(0x80112233.toInt(), DesignResolver.resolve(design, card, api34).time.argb)
        assertEquals(0xFFFFFFFF.toInt(), DesignResolver.resolve(DigitalDesign(), card, api34).time.argb)
    }

    @Test
    fun localeAutoUsesEnvironmentPatternAndExplicitPatternWins() {
        assertEquals("EEE, MMM d", DesignResolver.resolve(DigitalDesign(), card, api34).datePattern)
        val explicit = DigitalDesign(date = DateElement(formatPattern = "yyyy.MM.dd"))
        assertEquals("yyyy.MM.dd", DesignResolver.resolve(explicit, card, api34).datePattern)
    }

    @Test
    fun hourModeSelectsTextClockFormats() {
        fun formats(mode: HourMode, leadingZero: Boolean = false) = DesignResolver.resolve(
            DigitalDesign(time = TimeElement(leadingZero = leadingZero), behavior = Behavior(mode)),
            card,
            api34,
        ).timeFormats
        assertEquals(TimeFormats("h:mm", "HH:mm"), formats(HourMode.FOLLOW_SYSTEM))
        assertEquals(TimeFormats("h:mm", "h:mm"), formats(HourMode.FORCE_12_HOUR))
        assertEquals(TimeFormats("HH:mm", "HH:mm"), formats(HourMode.FORCE_24_HOUR))
        assertEquals(TimeFormats("hh:mm", "HH:mm"), formats(HourMode.FOLLOW_SYSTEM, leadingZero = true))
        assertEquals(TimeFormats("HH:mm", "HH:mm"), formats(HourMode.FORCE_24_HOUR, leadingZero = true))
    }

    @Test
    fun weightIsApproximatedBelowApi28AndDisclosed() {
        val design = DigitalDesign(time = TimeElement(TextStyle(sizeSp = 64f, weight = 600)))
        val spec = DesignResolver.resolve(design, card, api25)
        assertEquals(500, spec.time.face.weight)
        assertTrue(spec.degradations.contains(Degradation.WeightApproximated(TextElementKind.TIME, 600, 500)))
        assertTrue(DesignResolver.resolve(design, card, api34).degradations.isEmpty())
    }

    @Test
    fun legacyFamiliesAreExactOnlyAt400() {
        val serif400 = DigitalDesign(time = TimeElement(TextStyle(sizeSp = 64f, fontId = "serif")))
        assertEquals(ResolvedFace("serif", 400), DesignResolver.resolve(serif400, card, api34).time.face)

        val serif700 = DigitalDesign(time = TimeElement(TextStyle(sizeSp = 64f, fontId = "serif", weight = 700)))
        val spec = DesignResolver.resolve(serif700, card, api34)
        assertEquals(ResolvedFace(FontIds.SYSTEM_SANS, 700), spec.time.face)
        assertTrue(spec.degradations.any { it is Degradation.FontFallback && it.requestedFontId == "serif" })
    }

    @Test
    fun unknownFontFallsBackToSystemSans() {
        val design = DigitalDesign(date = DateElement(style = TextStyle(sizeSp = 14f, fontId = "nope")))
        val spec = DesignResolver.resolve(design, card, api34)
        assertEquals(FontIds.SYSTEM_SANS, spec.date.face.fontId)
        assertTrue(spec.degradations.any { it is Degradation.FontFallback && it.element == TextElementKind.DATE })
    }

    @Test
    fun offsetsAreZeroBeforeApi31ButStayRequested() {
        val design = DigitalDesign(time = TimeElement(TextStyle(sizeSp = 64f, xDp = 12f, yDp = -4f)))
        val old = DesignResolver.resolve(design, card, api25)
        assertEquals(0f, old.time.xDp)
        assertEquals(0f, old.time.yDp)
        assertTrue(old.degradations.contains(Degradation.OffsetUnsupported(TextElementKind.TIME, 31)))
        assertEquals(12f, design.time.style.xDp)

        val new = DesignResolver.resolve(design, card, api34)
        assertEquals(12f, new.time.xDp)
        assertEquals(-4f, new.time.yDp)
    }

    @Test
    fun offsetXMirrorsInRtlAndClampsToHalfTheHost() {
        val design = DigitalDesign(time = TimeElement(TextStyle(sizeSp = 64f, xDp = 10f, yDp = 500f)))
        val spec = DesignResolver.resolve(design, card, api34.copy(isRtl = true))
        assertEquals(-10f, spec.time.xDp)
        assertEquals(66f, spec.time.yDp)
        assertTrue(spec.degradations.contains(Degradation.OffsetClamped(TextElementKind.TIME)))
    }

    @Test
    fun hiddenDateReportsNoDegradations() {
        val design = DigitalDesign(
            date = DateElement(visible = false, style = TextStyle(sizeSp = 14f, weight = 650)),
        )
        assertTrue(DesignResolver.resolve(design, card, api25).degradations.isEmpty())
    }

    @Test
    fun backgroundNoneIsInvisibleAndSolidCarriesColorAndAlpha() {
        assertFalse(DesignResolver.resolve(DigitalDesign(), card, api34).background.visible)

        val solid = DigitalDesign(
            background = BackgroundElement(
                type = BackgroundType.SOLID,
                color = ColorRef.Fixed(0x111111),
                opacity = 0.9f,
                cornerRadius = CornerRadius.Dp(20f),
                paddingDp = 12f,
            ),
        )
        val spec = DesignResolver.resolve(solid, card, api34)
        assertTrue(spec.background.visible)
        assertEquals(0x111111, spec.background.rgb)
        assertEquals(230, spec.background.alpha)
        assertEquals(ResolvedRadius.Dp(20f), spec.background.radius)
        assertEquals(12f, spec.paddingDp)
    }

    @Test
    fun cornerRadiusUsesSystemOnApi31AndNearestVariantBefore() {
        val system = DigitalDesign(background = BackgroundElement(type = BackgroundType.SOLID))
        assertEquals(ResolvedRadius.System, DesignResolver.resolve(system, card, api34).background.radius)

        val old = DesignResolver.resolve(system, card, api25)
        assertEquals(ResolvedRadius.Dp(16f), old.background.radius)
        assertTrue(old.degradations.contains(Degradation.RadiusApproximated(null, 16f)))

        val dp20 = DigitalDesign(
            background = BackgroundElement(type = BackgroundType.SOLID, cornerRadius = CornerRadius.Dp(20f)),
        )
        assertEquals(ResolvedRadius.Dp(16f), DesignResolver.resolve(dp20, card, api25).background.radius)
        val dp24 = dp20.copy(background = dp20.background.copy(cornerRadius = CornerRadius.Dp(24f)))
        val exact = DesignResolver.resolve(dp24, card, api25)
        assertEquals(ResolvedRadius.Dp(24f), exact.background.radius)
        assertTrue(exact.degradations.isEmpty())
    }

    @Test
    fun alignmentPassesThroughAsSemanticValue() {
        val design = DigitalDesign(
            time = TimeElement(TextStyle(sizeSp = 64f, alignment = Alignment.START)),
            date = DateElement(style = TextStyle(sizeSp = 14f, alignment = Alignment.END)),
        )
        val spec = DesignResolver.resolve(design, card, api34)
        assertEquals(Alignment.START, spec.time.alignment)
        assertEquals(Alignment.END, spec.date.alignment)
    }
}
