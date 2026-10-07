package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.model.AmPmMode
import com.stupidsavacan.clocky.design.model.AmPmStyle
import com.stupidsavacan.clocky.design.model.BackgroundElement
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.Behavior
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.DateElement
import com.stupidsavacan.clocky.design.model.DesignLayout
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.Effects
import com.stupidsavacan.clocky.design.model.HourMode
import com.stupidsavacan.clocky.design.model.InfoElement
import com.stupidsavacan.clocky.design.model.InfoSource
import com.stupidsavacan.clocky.design.model.ShadowLevel
import com.stupidsavacan.clocky.design.model.TapAction
import com.stupidsavacan.clocky.design.model.TapActions
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignResolverPhase2Test {
    private val strip = SizeContext(363, 58, 667, 122)
    private val card = SizeContext(363, 132, 667, 260)
    private val api34 = RenderEnvironment(sdkInt = 34, isRtl = false, localeAutoDatePattern = "EEE, MMM d")
    private val api25 = api34.copy(sdkInt = 25)

    // ---- Phase 1A/1B designs are untouched ----

    @Test
    fun designWithoutPhase2KeysResolvesLikeBefore() {
        val spec = DesignResolver.resolve(DigitalDesign(), card, api34)
        assertNull(spec.amPm)
        assertNull(spec.info)
        assertEquals(ShadowVariant.CLASSIC, spec.time.shadow)
        assertEquals(TimeFormats("h:mm", "HH:mm"), spec.timeFormats)
        assertTrue(spec.dateUppercase)
        assertTrue(spec.taps.isDefault)
    }

    // ---- AM/PM and seconds ----

    @Test
    fun amPmSuffixIsASmallerSecondClockOnlyWhenTheClockCanBeTwelveHour() {
        val design = DigitalDesign(behavior = Behavior(amPm = AmPmStyle(AmPmMode.SUFFIX, 0.5f)))
        val follow = DesignResolver.resolve(design, card, api34).amPm!!
        assertEquals(32f, follow.text.sizeSp, 0.001f)
        assertEquals("a", follow.format12Hour)
        assertEquals("", follow.format24Hour)

        val forced12 = DesignResolver.resolve(design.copy(behavior = design.behavior.copy(hourMode = HourMode.FORCE_12_HOUR)), card, api34)
        assertEquals("a", forced12.amPm!!.format24Hour)

        val forced24 = DesignResolver.resolve(design.copy(behavior = design.behavior.copy(hourMode = HourMode.FORCE_24_HOUR)), card, api34)
        assertNull(forced24.amPm)
    }

    @Test
    fun amPmScaleIsClampedToTheDocumentedRange() {
        val tiny = DigitalDesign(behavior = Behavior(amPm = AmPmStyle(AmPmMode.SUFFIX, 0.01f)))
        assertEquals(64f * 0.25f, DesignResolver.resolve(tiny, card, api34).amPm!!.text.sizeSp, 0.001f)
    }

    @Test
    fun secondsExtendBothFormatsAndLeadingZeroOnlyChangesTwelveHour() {
        assertEquals(
            TimeFormats("hh:mm:ss", "HH:mm:ss"),
            DesignResolver.timeFormatsFor(HourMode.FOLLOW_SYSTEM, leadingZero = true, seconds = true),
        )
        assertEquals(TimeFormats("HH:mm", "HH:mm"), DesignResolver.timeFormatsFor(HourMode.FORCE_24_HOUR, leadingZero = true))
    }

    // ---- Info line ----

    private val alarmInfo = DigitalDesign(info = InfoElement(source = InfoSource.NEXT_ALARM))

    @Test
    fun nextAlarmInfoIsALiteralPatternOfTheReportedText() {
        val spec = DesignResolver.resolve(alarmInfo, card, api34.copy(nextAlarmText = "Mon 7:30 AM"))
        val info = spec.info!!
        assertEquals("'Mon 7:30 AM'", info.format12Hour)
        assertFalse(info.isSample)
        assertEquals(12f, info.text.sizeSp, 0.001f)
    }

    @Test
    fun nextAlarmTextWithAQuoteIsEscaped() {
        assertEquals("'It''s 7'", DesignResolver.quotePattern("It's 7"))
    }

    @Test
    fun nextAlarmWithoutAnAlarmShowsNothingExceptTheEditorSample() {
        assertNull(DesignResolver.resolve(alarmInfo, card, api34).info)
        val sample = DesignResolver.resolve(alarmInfo, card, api34.copy(sampleAlarmText = "Tue 7:30 AM")).info!!
        assertTrue(sample.isSample)
    }

    @Test
    fun secondTimezoneIsARealClockInThatZoneWithAnOptionalLabel() {
        val design = DigitalDesign(
            info = InfoElement(source = InfoSource.SECOND_TIMEZONE, timeZoneId = "Asia/Tokyo", label = "Tokyo"),
        )
        val info = DesignResolver.resolve(design, card, api34).info!!
        assertEquals("Asia/Tokyo", info.timeZoneId)
        assertEquals("'Tokyo' h:mm", info.format12Hour)
        assertEquals("'Tokyo' HH:mm", info.format24Hour)
    }

    @Test
    fun infoIsKeptButDisclosedWhenTheSizeOrTemplateCannotShowIt() {
        val env = api34.copy(nextAlarmText = "Mon 7:30 AM")
        val onStrip = DesignResolver.resolve(alarmInfo, strip, env)
        assertNull(onStrip.info)
        assertTrue(Degradation.InfoNotShown(InfoHiddenReason.STRIP_SIZE) in onStrip.degradations)

        val minimal = alarmInfo.copy(layout = DesignLayout(template = Template.MINIMAL))
        val onMinimal = DesignResolver.resolve(minimal, card, env)
        assertNull(onMinimal.info)
        assertTrue(Degradation.InfoNotShown(InfoHiddenReason.MINIMAL_TEMPLATE) in onMinimal.degradations)

        // No Info requested: nothing to disclose.
        assertTrue(DesignResolver.resolve(DigitalDesign(), strip, env).degradations.none { it is Degradation.InfoNotShown })
    }

    // ---- Shadow ----

    @Test
    fun shadowPicksBlackForLightTextAndWhiteForDarkText() {
        val light = DigitalDesign(time = TimeElement(TextStyle(sizeSp = 64f, color = ColorRef.Fixed(0xFFFFFF))), effects = Effects(ShadowLevel.STRONG))
        val dark = light.copy(time = TimeElement(TextStyle(sizeSp = 64f, color = ColorRef.Fixed(0x101010))))
        assertEquals(ShadowVariant.STRONG_DARK, DesignResolver.resolve(light, card, api34).time.shadow)
        assertEquals(ShadowVariant.STRONG_LIGHT, DesignResolver.resolve(dark, card, api34).time.shadow)
        assertEquals(ShadowVariant.OFF, DesignResolver.resolve(light.copy(effects = Effects(ShadowLevel.OFF)), card, api34).time.shadow)
    }

    // ---- Rendered backgrounds ----

    @Test
    fun gradientAndOutlineAreRenderedAndKeepTheirRequest() {
        val gradient = DigitalDesign(
            background = BackgroundElement(
                type = BackgroundType.GRADIENT,
                color = ColorRef.Fixed(0x112233),
                gradientEnd = ColorRef.Fixed(0x445566),
                gradientAngleDeg = 90,
            ),
        )
        val g = DesignResolver.resolve(gradient, card, api34).background
        assertTrue(g.isRendered)
        assertEquals(0x112233, g.rgb)
        assertEquals(0x445566, g.endRgb)
        assertEquals(90, g.gradientAngleDeg)

        val outline = DigitalDesign(background = BackgroundElement(type = BackgroundType.OUTLINE, borderWidthDp = 3f))
        val o = DesignResolver.resolve(outline, card, api34).background
        assertTrue(o.isRendered)
        assertEquals(3f, o.borderWidthDp, 0f)

        assertFalse(DesignResolver.resolve(DigitalDesign(background = BackgroundElement(type = BackgroundType.SOLID)), card, api34).background.isRendered)
    }

    @Test
    fun renderedBackgroundWithThemeBoundColorsIsDisclosedAsStatic() {
        val builtin = com.stupidsavacan.clocky.design.library.BuiltinDesigns.default.instantiate()
        val follow = builtin.copy(
            style = builtin.style!!.copy(themeMode = com.stupidsavacan.clocky.design.model.ThemeMode.FOLLOW_SYSTEM),
            background = BackgroundElement(
                type = BackgroundType.GRADIENT,
                color = ColorRef.Token(com.stupidsavacan.clocky.design.model.ColorRole.SURFACE),
                gradientEnd = ColorRef.Token(com.stupidsavacan.clocky.design.model.ColorRole.ACCENT),
            ),
        )
        val spec = DesignResolver.resolve(follow, card, api34)
        assertTrue(Degradation.RenderedBackgroundStatic in spec.degradations)
        assertNull("a bitmap never carries a live theme binding", spec.background.binding)

        val fixed = follow.copy(background = follow.background.copy(color = ColorRef.Fixed(0x101010), gradientEnd = ColorRef.Fixed(0x303030)))
        assertTrue(Degradation.RenderedBackgroundStatic !in DesignResolver.resolve(fixed, card, api34).degradations)
    }

    // ---- Date case ----

    @Test
    fun dateCaseIsARequestedValue() {
        val asIs = DigitalDesign(date = DateElement(uppercase = false))
        assertFalse(DesignResolver.resolve(asIs, card, api34).dateUppercase)
    }

    // ---- Tap zones ----

    @Test
    fun tapActionsPassThroughUnchanged() {
        val taps = TapActions(time = TapAction.OPEN_TIMER, date = TapAction.OPEN_CALENDAR, info = TapAction.NONE)
        assertEquals(taps, DesignResolver.resolve(DigitalDesign(behavior = Behavior(tap = taps)), card, api34).taps)
    }

    // ---- Font library ----

    @Test
    fun theLibraryHasTwelveFamiliesAndNoDuplicateIds() {
        assertEquals(12, FontCatalog.families.size)
        assertEquals(12, FontCatalog.allIds.toSet().size)
        assertEquals(6, FontCatalog.bundledIds.size)
    }

    @Test
    fun bundledFontResolvesToItsOwnNearestFaceAndDisclosesTheDifference() {
        val design = DigitalDesign(time = TimeElement(TextStyle(fontId = "clocky-poppins", weight = 600, sizeSp = 64f)))
        val exact = DesignResolver.resolve(design, card, api34)
        assertEquals(ResolvedFace("clocky-poppins", 600), exact.time.face)
        assertTrue(exact.degradations.none { it is Degradation.WeightApproximated })

        val approx = DesignResolver.resolve(design.copy(time = design.time.copy(style = design.time.style.copy(weight = 900))), card, api34)
        assertEquals(ResolvedFace("clocky-poppins", 700), approx.time.face)
        val note = approx.degradations.filterIsInstance<Degradation.WeightApproximated>().single()
        assertEquals(900, note.requested)
        assertEquals(700, note.effective)
        assertEquals("clocky-poppins", note.fontId)
    }

    @Test
    fun singleWeightBundledFontCollapsesEveryRequestToItsOnlyFace() {
        val design = DigitalDesign(time = TimeElement(TextStyle(fontId = "clocky-bebas-neue", weight = 200, sizeSp = 64f)))
        assertEquals(ResolvedFace("clocky-bebas-neue", 400), DesignResolver.resolve(design, card, api34).time.face)
    }

    @Test
    fun bundledFontFallsBackToSystemSansBelowApi26AndKeepsTheRequest() {
        val design = DigitalDesign(time = TimeElement(TextStyle(fontId = "clocky-poppins", weight = 500, sizeSp = 64f)))
        val spec = DesignResolver.resolve(design, card, api25)
        assertEquals(FontIds_SYSTEM_SANS, spec.time.face.fontId)
        assertTrue(spec.degradations.any { it is Degradation.FontFallback && it.reason == FontCatalog.REASON_NEEDS_API_26 })
        // The stored request is untouched by resolution.
        assertEquals("clocky-poppins", design.time.style.fontId)
        assertNotNull(spec.time.face)
    }

    private companion object {
        const val FontIds_SYSTEM_SANS = "system-sans"
    }
}
