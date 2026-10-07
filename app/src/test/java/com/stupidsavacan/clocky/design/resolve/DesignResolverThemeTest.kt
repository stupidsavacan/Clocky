package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.ColorRole
import com.stupidsavacan.clocky.design.model.DesignLayout
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.FontSpec
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.Palette
import com.stupidsavacan.clocky.design.model.PaletteColors
import com.stupidsavacan.clocky.design.model.PaletteVariant
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.model.TextSizeStep
import com.stupidsavacan.clocky.design.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignResolverThemeTest {
    private val strip = SizeContext(minWidthDp = 363, minHeightDp = 58, maxWidthDp = 667, maxHeightDp = 122)
    private val card = SizeContext(minWidthDp = 363, minHeightDp = 132, maxWidthDp = 667, maxHeightDp = 260)

    private fun env(sdk: Int, night: Boolean = false) =
        RenderEnvironment(sdkInt = sdk, isRtl = false, localeAutoDatePattern = "EEE, MMM d", isNight = night)

    // Distinct light/dark values per role so a mixed-up variant cannot pass.
    private val palette = Palette(
        id = "test",
        light = PaletteColors(primary = 0x111111, secondary = 0x222222, accent = 0x333333, surface = 0x444444),
        dark = PaletteColors(primary = 0xAAAAAA, secondary = 0xBBBBBB, accent = 0xCCCCCC, surface = 0xDDDDDD),
        fixedVariant = PaletteVariant.LIGHT,
    )

    private fun base(
        mode: ThemeMode = ThemeMode.FIXED,
        template: Template = Template.CENTER_STACK,
        textSize: TextSizeStep = TextSizeStep.MEDIUM,
        palette: Palette = this.palette,
    ): DigitalDesign {
        val d = BuiltinDesigns.clockyDefault.design
        return d.copy(
            style = d.style!!.copy(palette = palette, themeMode = mode, textSize = textSize),
            layout = DesignLayout(template = template),
        )
    }

    private fun argb(rgb: Int) = 0xFF000000.toInt() or rgb

    @Test
    fun fixedUsesThePaletteFixedVariantWithoutBindings() {
        val lightFixed = DesignResolver.resolve(base(), card, env(34, night = true))
        assertEquals(argb(0x111111), lightFixed.time.argb)
        assertEquals(argb(0x222222), lightFixed.date.argb)
        assertNull(lightFixed.time.binding)
        assertNull(lightFixed.date.binding)

        val darkFixed = DesignResolver.resolve(base(palette = palette.copy(fixedVariant = PaletteVariant.DARK)), card, env(34))
        assertEquals(argb(0xAAAAAA), darkFixed.time.argb)
        assertEquals(argb(0xBBBBBB), darkFixed.date.argb)
        assertFalse(darkFixed.degradations.any { it is Degradation.ThemeSwitchUnavailable })
    }

    @Test
    fun followSystemBindsDayNightOnApi34() {
        val night = DesignResolver.resolve(base(ThemeMode.FOLLOW_SYSTEM), card, env(34, night = true))
        assertEquals(ColorBinding.DayNight(argb(0x111111), argb(0xAAAAAA)), night.time.binding)
        assertEquals(ColorBinding.DayNight(argb(0x222222), argb(0xBBBBBB)), night.date.binding)
        assertEquals(argb(0xAAAAAA), night.time.argb)
        assertFalse(night.degradations.any { it is Degradation.ThemeSwitchUnavailable })

        val day = DesignResolver.resolve(base(ThemeMode.FOLLOW_SYSTEM), card, env(34, night = false))
        assertEquals(argb(0x111111), day.time.argb)
        assertEquals(night.time.binding, day.time.binding)
    }

    @Test
    fun followSystemBelowApi31FallsBackToTheCurrentNightVariantAndSaysSo() {
        val design = base(ThemeMode.FOLLOW_SYSTEM)
        val night = DesignResolver.resolve(design, card, env(30, night = true))
        assertNull(night.time.binding)
        assertNull(night.date.binding)
        assertEquals(argb(0xAAAAAA), night.time.argb)
        assertEquals(argb(0xBBBBBB), night.date.argb)
        assertTrue(Degradation.ThemeSwitchUnavailable in night.degradations)
        assertEquals(1, night.degradations.count { it == Degradation.ThemeSwitchUnavailable })

        val day = DesignResolver.resolve(design, card, env(30, night = false))
        assertEquals(argb(0x111111), day.time.argb)
        assertTrue(Degradation.ThemeSwitchUnavailable in day.degradations)
        // The request is never rewritten.
        assertEquals(ThemeMode.FOLLOW_SYSTEM, design.style!!.themeMode)
    }

    @Test
    fun materialYouBindsSystemRolesOnApi34WithoutDegradation() {
        val design = base(ThemeMode.MATERIAL_YOU)
        val spec = DesignResolver.resolve(design, card, env(34, night = true))
        assertEquals(ColorBinding.SystemRole(ColorRole.PRIMARY), spec.time.binding)
        assertEquals(ColorBinding.SystemRole(ColorRole.SECONDARY), spec.date.binding)
        assertFalse(spec.degradations.any { it is Degradation.DynamicColorUnavailable })
        assertFalse(spec.degradations.any { it is Degradation.ThemeSwitchUnavailable })
        assertEquals(ThemeMode.MATERIAL_YOU, design.style!!.themeMode)
    }

    @Test
    fun materialYouBelowApi31UsesThePaletteAndReportsDynamicColorUnavailable() {
        val design = base(ThemeMode.MATERIAL_YOU)
        val night = DesignResolver.resolve(design, card, env(30, night = true))
        assertNull(night.time.binding)
        assertEquals(argb(0xAAAAAA), night.time.argb)
        assertTrue(Degradation.DynamicColorUnavailable in night.degradations)
        assertFalse(Degradation.ThemeSwitchUnavailable in night.degradations)
        val day = DesignResolver.resolve(design, card, env(30, night = false))
        assertEquals(argb(0x111111), day.time.argb)
        assertEquals(ThemeMode.MATERIAL_YOU, design.style!!.themeMode)
    }

    @Test
    fun solidBackgroundTokenFollowsTheSameThemeRules() {
        val d = BuiltinDesigns.boldBlock.design
        val tokens = d.style!!.copy(palette = palette, themeMode = ThemeMode.FOLLOW_SYSTEM)
        val design = d.copy(style = tokens)
        val modern = DesignResolver.resolve(design, card, env(34, night = true))
        assertTrue(modern.background.visible)
        assertEquals(0xDDDDDD, modern.background.rgb)
        assertEquals(ColorBinding.DayNight(argb(0x444444), argb(0xDDDDDD)), modern.background.binding)
        val legacy = DesignResolver.resolve(design, card, env(30, night = false))
        assertEquals(0x444444, legacy.background.rgb)
        assertNull(legacy.background.binding)
        assertTrue(Degradation.ThemeSwitchUnavailable in legacy.degradations)
    }

    @Test
    fun templateComesFromTheSizeClassOverrideThenTheBase() {
        val design = base().copy(
            layout = DesignLayout(
                template = Template.CENTER_STACK,
                overrides = mapOf(SizeClass.STRIP to LayoutPatch(template = Template.INLINE)),
            ),
        )
        val s = DesignResolver.resolve(design, strip, env(34))
        assertEquals(SizeClass.STRIP, s.sizeClass)
        assertEquals(Template.INLINE, s.template)
        val c = DesignResolver.resolve(design, card, env(34))
        assertEquals(SizeClass.CARD, c.sizeClass)
        assertEquals(Template.CENTER_STACK, c.template)

        val cardOverride = design.copy(
            layout = design.layout.withPatch(SizeClass.CARD, LayoutPatch(template = Template.SPLIT)),
        )
        assertEquals(Template.SPLIT, DesignResolver.resolve(cardOverride, card, env(34)).template)
        assertEquals(Template.INLINE, DesignResolver.resolve(cardOverride, strip, env(34)).template)
    }

    @Test
    fun minimalHidesTheDateInTheSpecButKeepsTheRequest() {
        val design = base(template = Template.MINIMAL)
        assertTrue(design.date.visible)
        val spec = DesignResolver.resolve(design, card, env(34))
        assertFalse(spec.dateVisible)
        assertTrue(design.date.visible)
        // Without MINIMAL the same request is visible.
        assertTrue(DesignResolver.resolve(base(template = Template.CENTER_STACK), card, env(34)).dateVisible)
        // A per-class dateVisible=true cannot resurrect it either.
        val patched = design.copy(layout = design.layout.withPatch(SizeClass.CARD, LayoutPatch(dateVisible = true)))
        assertFalse(DesignResolver.resolve(patched, card, env(34)).dateVisible)
    }

    @Test
    fun splitForcesTimeEndAndDateStart() {
        val design = base(template = Template.SPLIT).let {
            it.copy(
                time = it.time.copy(style = it.time.style.copy(alignment = Alignment.CENTER)),
                date = it.date.copy(style = it.date.style.copy(alignment = Alignment.CENTER)),
            )
        }
        val spec = DesignResolver.resolve(design, card, env(34))
        assertEquals(Alignment.END, spec.time.alignment)
        assertEquals(Alignment.START, spec.date.alignment)
        val other = DesignResolver.resolve(base(template = Template.CENTER_STACK), card, env(34))
        assertEquals(Alignment.CENTER, other.time.alignment)
        assertEquals(Alignment.CENTER, other.date.alignment)
    }

    @Test
    fun textSizeStepScalesBothElementsAndOverrides() {
        val d = base()
        val medium = DesignResolver.resolve(d, card, env(34))
        val large = DesignResolver.resolve(base(textSize = TextSizeStep.LARGE), card, env(34))
        val small = DesignResolver.resolve(base(textSize = TextSizeStep.SMALL), card, env(34))
        assertEquals(88f, medium.time.sizeSp, 0.001f)
        assertEquals(88f * 1.2f, large.time.sizeSp, 0.001f)
        assertEquals(18f * 1.2f, large.date.sizeSp, 0.001f)
        assertEquals(88f * 0.82f, small.time.sizeSp, 0.001f)
        assertEquals(18f * 0.82f, small.date.sizeSp, 0.001f)

        val patched = base(textSize = TextSizeStep.LARGE)
            .let { it.copy(layout = it.layout.withPatch(SizeClass.STRIP, LayoutPatch(timeSizeSp = 40f))) }
        assertEquals(48f, DesignResolver.resolve(patched, strip, env(34)).time.sizeSp, 0.001f)
    }

    @Test
    fun tokenFontsResolveToTheTokenSpecAndPatchWeightWins() {
        val d = base()
        val design = d.copy(
            style = d.style!!.copy(
                fontPrimary = FontSpec("serif", 400),
                fontSecondary = FontSpec(FontIds.SYSTEM_SANS, 600),
            ),
        )
        val spec = DesignResolver.resolve(design, card, env(34))
        assertEquals("serif", spec.time.face.fontId)
        assertEquals(400, spec.time.face.weight)
        assertEquals(FontIds.SYSTEM_SANS, spec.date.face.fontId)
        assertEquals(600, spec.date.face.weight)

        val sans = d.copy(style = d.style!!.copy(fontPrimary = FontSpec(FontIds.SYSTEM_SANS, 300)))
        assertEquals(300, DesignResolver.resolve(sans, card, env(34)).time.face.weight)
        val patched = sans.copy(
            layout = sans.layout.withPatch(SizeClass.CARD, LayoutPatch(timeWeight = 800, dateWeight = 200)),
        )
        val p = DesignResolver.resolve(patched, card, env(34))
        assertEquals(800, p.time.face.weight)
        assertEquals(200, p.date.face.weight)
        // The other size class is not affected by the card patch.
        assertEquals(300, DesignResolver.resolve(patched, strip, env(34)).time.face.weight)
    }
}
