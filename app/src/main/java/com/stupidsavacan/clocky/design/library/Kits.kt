package com.stupidsavacan.clocky.design.library

import com.stupidsavacan.clocky.design.model.Palette
import com.stupidsavacan.clocky.design.model.Template

/** A palette a kit offers in Quick Tune; [needsSurface] palettes only make sense behind a Solid background. */
data class PaletteOption(val palette: Palette, val needsSurface: Boolean = false)

/**
 * One entry of a kit's "visually compatible" table (End-State 6, Surprise me). Surprise me draws
 * only from these, so randomness never produces a combination the kit did not declare.
 */
data class KitCombo(
    val paletteId: String,
    val typeface: TypefaceCategory,
    val cardTemplate: Template,
    val stripTemplate: Template,
)

/**
 * A Kit is a design grammar (End-State 4): a typography vocabulary, the palettes that suit it, and
 * the combinations it allows. Every built-in design of the kit is built from it.
 */
data class Kit(
    val id: String,
    val typePairs: Map<TypefaceCategory, TypePair>,
    val palettes: List<PaletteOption>,
    val combos: List<KitCombo>,
) {
    fun paletteById(id: String): Palette? = palettes.firstOrNull { it.palette.id == id }?.palette
}

object Kits {
    const val DEFAULT = "default"
    const val MINIMAL = "minimal"
    const val BOLD = "bold"
    const val EDITORIAL = "editorial"

    /** Templates Quick Tune offers per size class, in display order (End-State 6: 3-4 per target size). */
    val cardTemplates: List<Template> =
        listOf(Template.CENTER_STACK, Template.TIME_FIRST, Template.SPLIT, Template.MINIMAL)
    val stripTemplates: List<Template> =
        listOf(Template.INLINE, Template.SPLIT, Template.TIME_FIRST, Template.MINIMAL)

    val default = Kit(
        id = DEFAULT,
        typePairs = Typefaces.pairs(modernTime = 300, modernDate = 500, displayTime = 800, displayDate = 600),
        palettes = listOf(
            PaletteOption(Palettes.CLOCKY),
            PaletteOption(Palettes.MINT),
            PaletteOption(Palettes.MONO),
            PaletteOption(Palettes.SAND),
            PaletteOption(Palettes.OCEAN),
            PaletteOption(Palettes.ROSE),
        ),
        combos = listOf(
            KitCombo("clocky", TypefaceCategory.MODERN, Template.CENTER_STACK, Template.INLINE),
            KitCombo("mint", TypefaceCategory.MODERN, Template.CENTER_STACK, Template.INLINE),
            KitCombo("sand", TypefaceCategory.ROUNDED, Template.CENTER_STACK, Template.INLINE),
            KitCombo("ocean", TypefaceCategory.MODERN, Template.TIME_FIRST, Template.INLINE),
            KitCombo("rose", TypefaceCategory.SERIF, Template.CENTER_STACK, Template.INLINE),
            KitCombo("mono", TypefaceCategory.MONO, Template.TIME_FIRST, Template.INLINE),
        ),
    )

    val minimal = Kit(
        id = MINIMAL,
        typePairs = Typefaces.pairs(modernTime = 200, modernDate = 600, displayTime = 700, displayDate = 600),
        palettes = listOf(
            PaletteOption(Palettes.CLOCKY),
            PaletteOption(Palettes.MONO),
            PaletteOption(Palettes.SAND),
            PaletteOption(Palettes.OCEAN),
            PaletteOption(Palettes.ROSE),
            PaletteOption(Palettes.PAPER),
        ),
        combos = listOf(
            KitCombo("clocky", TypefaceCategory.MODERN, Template.MINIMAL, Template.MINIMAL),
            KitCombo("mono", TypefaceCategory.MODERN, Template.SPLIT, Template.SPLIT),
            KitCombo("sand", TypefaceCategory.MODERN, Template.MINIMAL, Template.MINIMAL),
            KitCombo("ocean", TypefaceCategory.ROUNDED, Template.SPLIT, Template.SPLIT),
            KitCombo("paper", TypefaceCategory.SERIF, Template.MINIMAL, Template.MINIMAL),
            KitCombo("rose", TypefaceCategory.MODERN, Template.SPLIT, Template.SPLIT),
        ),
    )

    val bold = Kit(
        id = BOLD,
        typePairs = Typefaces.pairs(modernTime = 500, modernDate = 600, displayTime = 900, displayDate = 700),
        palettes = listOf(
            PaletteOption(Palettes.POSTER),
            PaletteOption(Palettes.EMBER, needsSurface = true),
            PaletteOption(Palettes.MONO),
            PaletteOption(Palettes.OCEAN),
            PaletteOption(Palettes.ROSE),
            PaletteOption(Palettes.SAND),
        ),
        combos = listOf(
            KitCombo("poster", TypefaceCategory.DISPLAY, Template.TIME_FIRST, Template.INLINE),
            KitCombo("mono", TypefaceCategory.DISPLAY, Template.TIME_FIRST, Template.INLINE),
            KitCombo("ocean", TypefaceCategory.DISPLAY, Template.SPLIT, Template.SPLIT),
            KitCombo("rose", TypefaceCategory.CONDENSED, Template.TIME_FIRST, Template.INLINE),
            KitCombo("sand", TypefaceCategory.DISPLAY, Template.TIME_FIRST, Template.INLINE),
            KitCombo("poster", TypefaceCategory.CONDENSED, Template.CENTER_STACK, Template.INLINE),
        ),
    )

    val editorial = Kit(
        id = EDITORIAL,
        typePairs = Typefaces.pairs(modernTime = 300, modernDate = 500, displayTime = 700, displayDate = 500),
        palettes = listOf(
            PaletteOption(Palettes.PAPER),
            PaletteOption(Palettes.MONO),
            PaletteOption(Palettes.SAND),
            PaletteOption(Palettes.ROSE),
            PaletteOption(Palettes.OCEAN),
            PaletteOption(Palettes.CLOCKY),
        ),
        combos = listOf(
            KitCombo("paper", TypefaceCategory.SERIF, Template.CENTER_STACK, Template.INLINE),
            KitCombo("paper", TypefaceCategory.SERIF, Template.SPLIT, Template.SPLIT),
            KitCombo("sand", TypefaceCategory.SERIF, Template.CENTER_STACK, Template.INLINE),
            KitCombo("rose", TypefaceCategory.SERIF, Template.SPLIT, Template.SPLIT),
            KitCombo("ocean", TypefaceCategory.SERIF, Template.CENTER_STACK, Template.INLINE),
            KitCombo("mono", TypefaceCategory.SERIF, Template.SPLIT, Template.SPLIT),
        ),
    )

    val all: List<Kit> = listOf(default, minimal, bold, editorial)

    fun byId(id: String?): Kit? = all.firstOrNull { it.id == id }
}
