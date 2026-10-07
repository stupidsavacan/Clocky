package com.stupidsavacan.clocky.design.library

import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.FontSpec
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.Palette
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.StyleTokens
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.model.TextSizeStep
import com.stupidsavacan.clocky.design.model.ThemeMode
import kotlin.random.Random

/**
 * Quick Tune operations (End-State 6): palette, typography, template, text size and date
 * visibility, plus Surprise me. Everything edits [StyleTokens] or the per-class template, never the
 * low-level element values, so a kit's proportions survive. All functions are pure.
 */
object QuickTune {
    /**
     * Freezes token references into the fixed values they currently show, for the pre-Studio
     * detailed editor, which edits literal colors and fonts. The design keeps its `source` for
     * provenance but loses its tokens, so Follow system / Material You no longer apply to it
     * (Studio, Phase 2, edits through tokens and removes this limitation).
     */
    fun detach(design: DigitalDesign): DigitalDesign {
        val tokens = design.style ?: return design
        val colors = tokens.palette.variant(tokens.palette.fixedVariant)
        fun ColorRef.freeze(): ColorRef = when (this) {
            is ColorRef.Token -> ColorRef.Fixed(colors.of(role))
            is ColorRef.Fixed -> this
        }
        fun TextStyle.freeze(spec: FontSpec, scale: Float): TextStyle = copy(
            fontId = if (FontIds.isToken(fontId)) spec.fontId else fontId,
            weight = if (FontIds.isToken(fontId)) spec.weight else weight,
            sizeSp = sizeSp * scale,
            color = color.freeze(),
        )
        val scale = tokens.textSize.scale
        return design.copy(
            time = design.time.copy(style = design.time.style.freeze(tokens.fontPrimary, scale)),
            date = design.date.copy(style = design.date.style.freeze(tokens.fontSecondary, scale)),
            background = design.background.copy(color = design.background.color.freeze()),
            layout = design.layout.copy(
                overrides = design.layout.overrides.mapValues { (_, p) ->
                    p.copy(
                        timeSizeSp = p.timeSizeSp?.times(scale),
                        dateSizeSp = p.dateSizeSp?.times(scale),
                        infoSizeSp = p.infoSizeSp?.times(scale),
                    )
                },
            ),
            style = null,
        )
    }

    /** The kit grammar of a design, or null for designs without tokens (Phase 1A / hand-built). */
    fun kitOf(design: DigitalDesign): Kit? = Kits.byId(design.source?.kitId)

    /** Quick Tune applies only to designs that carry tokens and a kit to take its vocabulary from. */
    fun isTunable(design: DigitalDesign): Boolean = design.style != null && kitOf(design) != null

    /** Palettes the kit offers for this design (surface-dependent ones need a Solid background). */
    fun paletteOptions(design: DigitalDesign): List<Palette> {
        val kit = kitOf(design) ?: return emptyList()
        val solid = design.background.type == BackgroundType.SOLID
        val ownId = design.style?.palette?.id
        return kit.palettes
            .filter { solid || !it.needsSurface || it.palette.id == ownId }
            .map { it.palette }
    }

    fun selectPalette(design: DigitalDesign, palette: Palette): DigitalDesign {
        val tokens = design.style ?: return design
        // Leaving Material You returns to Fixed; an explicit Follow system stays on.
        val mode = if (tokens.themeMode == ThemeMode.MATERIAL_YOU) ThemeMode.FIXED else tokens.themeMode
        return design.copy(style = tokens.copy(palette = palette, themeMode = mode))
    }

    /**
     * Material You keeps the stored palette as its below-API-31 fallback (End-State 5.5), so the
     * requested mode survives even where the platform cannot honor it.
     */
    fun selectMaterialYou(design: DigitalDesign): DigitalDesign {
        val tokens = design.style ?: return design
        return design.copy(style = tokens.copy(themeMode = ThemeMode.MATERIAL_YOU))
    }

    /** Light/dark follow on or off; ignored while Material You is selected (it always follows). */
    fun setFollowSystem(design: DigitalDesign, follow: Boolean): DigitalDesign {
        val tokens = design.style ?: return design
        if (tokens.themeMode == ThemeMode.MATERIAL_YOU) return design
        val mode = if (follow) ThemeMode.FOLLOW_SYSTEM else ThemeMode.FIXED
        return design.copy(style = tokens.copy(themeMode = mode))
    }

    fun selectTypeface(design: DigitalDesign, category: TypefaceCategory): DigitalDesign {
        val tokens = design.style ?: return design
        val pair = kitOf(design)?.typePairs?.get(category) ?: return design
        return design.copy(style = tokens.copy(fontPrimary = pair.primary, fontSecondary = pair.secondary))
    }

    /** The category whose pair the tokens currently hold, or null after a custom edit. */
    fun typefaceOf(design: DigitalDesign): TypefaceCategory? {
        val tokens = design.style ?: return null
        val kit = kitOf(design) ?: return null
        return kit.typePairs.entries.firstOrNull {
            it.value.primary == tokens.fontPrimary && it.value.secondary == tokens.fontSecondary
        }?.key
    }

    fun templatesFor(sizeClass: SizeClass): List<Template> = when (sizeClass) {
        SizeClass.STRIP -> Kits.stripTemplates
        SizeClass.CARD, SizeClass.SQUARE, SizeClass.LARGE -> Kits.cardTemplates
    }

    /** Template effective for [sizeClass] (override first, then the design's base). */
    fun templateOf(design: DigitalDesign, sizeClass: SizeClass): Template =
        design.layout.inheritedPatchFor(sizeClass).let { p ->
            if (p.requestedTemplate != null || (p.template == null && design.layout.requestedTemplate != null)) Template.TIME_FIRST
            else p.template ?: design.layout.template
        }

    fun selectTemplate(design: DigitalDesign, sizeClass: SizeClass, template: Template): DigitalDesign {
        val patch = design.layout.patchFor(sizeClass).copy(template = template, requestedTemplate = null,
            preserved = design.layout.patchFor(sizeClass).preserved - "layout.template")
        return design.copy(layout = design.layout.withPatch(sizeClass, patch))
    }

    fun setTextSize(design: DigitalDesign, step: TextSizeStep): DigitalDesign {
        val tokens = design.style ?: return design
        return design.copy(style = tokens.copy(textSize = step))
    }

    /**
     * One switch, one meaning: date shows or hides everywhere. Any per-class date override from an
     * earlier editor is cleared so the switch always does what it says.
     */
    fun setDateVisible(design: DigitalDesign, visible: Boolean): DigitalDesign {
        var layout = design.layout
        SizeClass.entries.forEach { c ->
            val patch = layout.patchFor(c)
            if (patch.dateVisible != null) layout = layout.withPatch(c, patch.copy(dateVisible = null))
        }
        return design.copy(date = design.date.copy(visible = visible), layout = layout)
    }

    /**
     * Picks one entry of the kit's compatibility table (never the one currently shown, when the
     * table has another) and applies it: palette, typeface, and the template for each size class.
     * Follow-system / Material You choices are kept; text size and date visibility are not touched.
     */
    fun surprise(design: DigitalDesign, random: Random): DigitalDesign {
        val kit = kitOf(design) ?: return design
        if (design.style == null) return design
        val allowed = paletteOptions(design).map { it.id }.toSet()
        val usable = kit.combos.filter { it.paletteId in allowed }
        if (usable.isEmpty()) return design
        val current = usable.firstOrNull { it.matches(design) }
        val candidates = usable.filter { it != current }.ifEmpty { usable }
        val combo = candidates[random.nextInt(candidates.size)]
        val palette = kit.paletteById(combo.paletteId) ?: return design
        // selectPalette leaves Material You (a palette pick is an explicit choice); Surprise me is not.
        var next = selectPalette(design, palette)
        next = next.copy(style = next.style!!.copy(themeMode = design.style!!.themeMode))
        next = selectTypeface(next, combo.typeface)
        next = selectTemplate(next, SizeClass.CARD, combo.cardTemplate)
        next = selectTemplate(next, SizeClass.STRIP, combo.stripTemplate)
        return next
    }

    private fun KitCombo.matches(design: DigitalDesign): Boolean =
        design.style?.palette?.id == paletteId &&
            typefaceOf(design) == typeface &&
            templateOf(design, SizeClass.CARD) == cardTemplate &&
            templateOf(design, SizeClass.STRIP) == stripTemplate
}
