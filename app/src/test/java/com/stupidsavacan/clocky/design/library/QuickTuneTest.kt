package com.stupidsavacan.clocky.design.library

import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.model.TextSizeStep
import com.stupidsavacan.clocky.design.model.ThemeMode
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickTuneTest {
    private val designs = BuiltinDesigns.all.map { it.design }
    private val default = BuiltinDesigns.clockyDefault.design

    private data class Combo(val palette: String?, val typeface: TypefaceCategory?, val card: Template, val strip: Template)

    private fun comboOf(d: DigitalDesign) = Combo(
        d.style?.palette?.id,
        QuickTune.typefaceOf(d),
        QuickTune.templateOf(d, SizeClass.CARD),
        QuickTune.templateOf(d, SizeClass.STRIP),
    )

    @Test
    fun selectPaletteChangesOnlyThePaletteAndLeavesMaterialYou() {
        val ocean = Palettes.OCEAN
        val out = QuickTune.selectPalette(default, ocean)
        assertEquals(ocean, out.style!!.palette)
        assertEquals(default.style!!.fontPrimary, out.style!!.fontPrimary)
        assertEquals(default.style!!.fontSecondary, out.style!!.fontSecondary)
        assertEquals(default.layout, out.layout)
        assertEquals(default.time, out.time)
        assertEquals(default.date, out.date)
        assertEquals(default.background, out.background)

        val you = QuickTune.selectMaterialYou(default)
        assertEquals(ThemeMode.FIXED, QuickTune.selectPalette(you, ocean).style!!.themeMode)
        val follow = QuickTune.setFollowSystem(default, true)
        assertEquals(ThemeMode.FOLLOW_SYSTEM, QuickTune.selectPalette(follow, ocean).style!!.themeMode)
    }

    @Test
    fun selectMaterialYouKeepsTheStoredPalette() {
        val out = QuickTune.selectMaterialYou(default)
        assertEquals(ThemeMode.MATERIAL_YOU, out.style!!.themeMode)
        assertEquals(default.style!!.palette, out.style!!.palette)
    }

    @Test
    fun followSystemToggleAndIgnoredUnderMaterialYou() {
        val on = QuickTune.setFollowSystem(default, true)
        assertEquals(ThemeMode.FOLLOW_SYSTEM, on.style!!.themeMode)
        assertEquals(ThemeMode.FIXED, QuickTune.setFollowSystem(on, false).style!!.themeMode)

        val you = QuickTune.selectMaterialYou(default)
        assertEquals(you, QuickTune.setFollowSystem(you, true))
        assertEquals(you, QuickTune.setFollowSystem(you, false))
    }

    @Test
    fun selectTypefaceSetsTheKitPairAndRoundTripsForEveryNativeDesign() {
        BuiltinDesigns.all.forEach { b ->
            val kit = Kits.byId(b.kitId)!!
            TypefaceCategory.entries.forEach { cat ->
                val out = QuickTune.selectTypeface(b.design, cat)
                assertEquals("${b.id}/$cat", cat, QuickTune.typefaceOf(out))
                assertEquals(kit.typePairs.getValue(cat).primary, out.style!!.fontPrimary)
                assertEquals(kit.typePairs.getValue(cat).secondary, out.style!!.fontSecondary)
                assertEquals(b.design.layout, out.layout)
                assertEquals(b.design.style!!.palette, out.style!!.palette)
            }
        }
        // Each design starts on its declared native category.
        assertEquals(TypefaceCategory.MODERN, QuickTune.typefaceOf(default))
        assertEquals(TypefaceCategory.DISPLAY, QuickTune.typefaceOf(BuiltinDesigns.boldPoster.design))
        assertEquals(TypefaceCategory.SERIF, QuickTune.typefaceOf(BuiltinDesigns.editorialSerif.design))
    }

    @Test
    fun typefaceOfIsNullAfterCustomEditAndForStyleNullDesigns() {
        val custom = default.copy(style = default.style!!.copy(fontPrimary = default.style!!.fontPrimary.copy(weight = 123)))
        assertNull(QuickTune.typefaceOf(custom))
        assertNull(QuickTune.typefaceOf(DigitalDesign()))
    }

    @Test
    fun selectTemplateWritesOnlyTheGivenClassPatch() {
        val base = default
        val stripBefore = base.layout.patchFor(SizeClass.STRIP)
        val out = QuickTune.selectTemplate(base, SizeClass.CARD, Template.SPLIT)
        assertEquals(Template.SPLIT, QuickTune.templateOf(out, SizeClass.CARD))
        assertEquals(Template.SPLIT, out.layout.patchFor(SizeClass.CARD).template)
        assertEquals(stripBefore, out.layout.patchFor(SizeClass.STRIP))
        assertEquals(Template.INLINE, QuickTune.templateOf(out, SizeClass.STRIP))
        assertEquals(base.layout.template, out.layout.template)

        val strip = QuickTune.selectTemplate(base, SizeClass.STRIP, Template.MINIMAL)
        assertEquals(Template.MINIMAL, QuickTune.templateOf(strip, SizeClass.STRIP))
        assertEquals(base.layout.patchFor(SizeClass.CARD), strip.layout.patchFor(SizeClass.CARD))
        assertEquals(Template.CENTER_STACK, QuickTune.templateOf(strip, SizeClass.CARD))
    }

    @Test
    fun selectTemplatePreservesOtherPatchFields() {
        val d = default.copy(
            layout = default.layout.withPatch(SizeClass.CARD, LayoutPatch(timeSizeSp = 50f, dateVisible = false)),
        )
        val out = QuickTune.selectTemplate(d, SizeClass.CARD, Template.TIME_FIRST)
        val patch = out.layout.patchFor(SizeClass.CARD)
        assertEquals(50f, patch.timeSizeSp)
        assertEquals(false, patch.dateVisible)
        assertEquals(Template.TIME_FIRST, patch.template)
    }

    @Test
    fun setTextSizeChangesOnlyTheStep() {
        TextSizeStep.entries.forEach { step ->
            val out = QuickTune.setTextSize(default, step)
            assertEquals(step, out.style!!.textSize)
            assertEquals(default.style!!.copy(textSize = step), out.style)
            assertEquals(default.time, out.time)
        }
        val plain = DigitalDesign()
        assertSame(plain, QuickTune.setTextSize(plain, TextSizeStep.LARGE))
    }

    @Test
    fun setDateVisibleClearsPerClassDateOverrides() {
        val d = default.copy(
            layout = default.layout
                .withPatch(SizeClass.STRIP, LayoutPatch(template = Template.INLINE, dateVisible = true, timeSizeSp = 30f))
                .withPatch(SizeClass.CARD, LayoutPatch(dateVisible = false)),
        )
        val hidden = QuickTune.setDateVisible(d, false)
        assertFalse(hidden.date.visible)
        SizeClass.entries.forEach { assertNull(hidden.layout.patchFor(it).dateVisible) }
        // Unrelated patch fields survive; an emptied patch is dropped.
        assertEquals(Template.INLINE, hidden.layout.patchFor(SizeClass.STRIP).template)
        assertEquals(30f, hidden.layout.patchFor(SizeClass.STRIP).timeSizeSp)
        assertFalse(SizeClass.CARD in hidden.layout.overrides)
        assertTrue(QuickTune.setDateVisible(hidden, true).date.visible)
    }

    @Test
    fun surpriseAlwaysPicksADeclaredComboDifferentFromTheCurrentOne() {
        designs.forEach { start ->
            val kit = QuickTune.kitOf(start)!!
            val declared = kit.combos.map { Combo(it.paletteId, it.typeface, it.cardTemplate, it.stripTemplate) }.toSet()
            val random = Random(20260607)
            var current = QuickTune.setTextSize(QuickTune.setFollowSystem(start, true), TextSizeStep.LARGE)
            repeat(200) {
                val before = comboOf(current)
                val next = QuickTune.surprise(current, random)
                val after = comboOf(next)
                assertTrue("${start.source?.builtinId}: $after not declared", after in declared)
                assertNotEquals("${start.source?.builtinId}: repeated $before", before, after)
                assertEquals(current.style!!.textSize, next.style!!.textSize)
                assertEquals(current.style!!.themeMode, next.style!!.themeMode)
                assertEquals(current.date, next.date)
                assertEquals(current.time, next.time)
                assertEquals(current.background, next.background)
                current = next
            }
        }
    }

    @Test
    fun surpriseKeepsMaterialYouAndStaysWithinPaletteOptionsOfTheBackground() {
        val you = QuickTune.selectMaterialYou(BuiltinDesigns.boldPoster.design)
        val random = Random(7)
        var d = you
        repeat(100) {
            d = QuickTune.surprise(d, random)
            assertEquals(ThemeMode.MATERIAL_YOU, d.style!!.themeMode)
            // Bold Poster is bare, so a surface-only palette (ember) must never be drawn.
            assertNotEquals("ember", d.style!!.palette.id)
        }
    }

    @Test
    fun surpriseOnDesignsWithoutTokensIsANoOp() {
        val plain = DigitalDesign()
        assertSame(plain, QuickTune.surprise(plain, Random(1)))
    }

    @Test
    fun paletteOptionsExcludeSurfaceOnlyPalettesWithoutASolidBackground() {
        val poster = QuickTune.paletteOptions(BuiltinDesigns.boldPoster.design).map { it.id }
        val block = QuickTune.paletteOptions(BuiltinDesigns.boldBlock.design).map { it.id }
        assertFalse("ember" in poster)
        assertTrue("ember" in block)
        assertTrue("poster" in poster)
        assertEquals(Kits.bold.palettes.size - 1, poster.size)
        assertEquals(Kits.bold.palettes.size, block.size)
        assertTrue(QuickTune.paletteOptions(DigitalDesign()).isEmpty())
    }

    @Test
    fun detachFreezesTokensIntoFixedValuesScaledByTextSize() {
        val src = BuiltinDesigns.boldBlock.design
        val tokens = src.style!!
        val scale = TextSizeStep.LARGE.scale
        val withOverrides = QuickTune.setTextSize(
            src.copy(
                layout = src.layout
                    .withPatch(SizeClass.STRIP, LayoutPatch(timeSizeSp = 40f, dateSizeSp = 10f, template = Template.INLINE)),
            ),
            TextSizeStep.LARGE,
        )
        val out = QuickTune.detach(withOverrides)
        val colors = tokens.palette.variant(tokens.palette.fixedVariant)

        assertNull(out.style)
        assertEquals(src.source, out.source)
        assertEquals(ColorRef.Fixed(colors.primary), out.time.style.color)
        assertEquals(ColorRef.Fixed(colors.secondary), out.date.style.color)
        assertEquals(ColorRef.Fixed(colors.surface), out.background.color)
        assertEquals(tokens.fontPrimary.fontId, out.time.style.fontId)
        assertEquals(tokens.fontPrimary.weight, out.time.style.weight)
        assertEquals(tokens.fontSecondary.fontId, out.date.style.fontId)
        assertEquals(tokens.fontSecondary.weight, out.date.style.weight)
        assertFalse(FontIds.isToken(out.time.style.fontId))
        assertFalse(FontIds.isToken(out.date.style.fontId))
        assertEquals(src.time.style.sizeSp * scale, out.time.style.sizeSp, 0.001f)
        assertEquals(src.date.style.sizeSp * scale, out.date.style.sizeSp, 0.001f)
        val strip = out.layout.patchFor(SizeClass.STRIP)
        assertEquals(40f * scale, strip.timeSizeSp!!, 0.001f)
        assertEquals(10f * scale, strip.dateSizeSp!!, 0.001f)
        assertEquals(Template.INLINE, strip.template)
        assertEquals(src.layout.template, out.layout.template)
    }

    @Test
    fun detachUsesTheFixedVariantOfThePalette() {
        // Ember is a LIGHT-fixed palette: frozen colors must come from light, not dark.
        val d = BuiltinDesigns.boldBlock.design
        val light = d.style!!.palette.light
        assertEquals(ColorRef.Fixed(light.surface), QuickTune.detach(d).background.color)
        // Clocky is DARK-fixed: light text.
        assertEquals(
            ColorRef.Fixed(Palettes.CLOCKY.dark.primary),
            QuickTune.detach(default).time.style.color,
        )
    }

    @Test
    fun detachLeavesStyleNullDesignUntouched() {
        val plain = DigitalDesign()
        assertSame(plain, QuickTune.detach(plain))
    }
}
