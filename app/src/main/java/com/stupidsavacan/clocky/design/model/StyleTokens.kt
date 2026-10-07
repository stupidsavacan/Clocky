package com.stupidsavacan.clocky.design.model

/** Palette roles an element can reference (End-State §5.0). */
enum class ColorRole { PRIMARY, SECONDARY, ACCENT, SURFACE }

/** Opaque 0xRRGGBB per role. */
data class PaletteColors(
    val primary: Int,
    val secondary: Int,
    val accent: Int,
    val surface: Int,
) {
    init {
        listOf(primary, secondary, accent, surface).forEach {
            require(it ushr 24 == 0) { "Palette colors are stored without alpha: ${it.toString(16)}" }
        }
    }

    fun of(role: ColorRole): Int = when (role) {
        ColorRole.PRIMARY -> primary
        ColorRole.SECONDARY -> secondary
        ColorRole.ACCENT -> accent
        ColorRole.SURFACE -> surface
    }
}

enum class PaletteVariant { LIGHT, DARK }

/**
 * A palette is a Light/Dark pair. [fixedVariant] is the variant [ThemeMode.FIXED] shows, so a
 * palette designed for a dark wallpaper keeps light text. A design stores the full palette it was
 * created with (a snapshot), so editing the built-in library later never recolors a placed widget.
 */
data class Palette(
    val id: String,
    val light: PaletteColors,
    val dark: PaletteColors,
    val fixedVariant: PaletteVariant = PaletteVariant.DARK,
) {
    fun variant(variant: PaletteVariant): PaletteColors = when (variant) {
        PaletteVariant.LIGHT -> light
        PaletteVariant.DARK -> dark
    }
}

/**
 * End-State §5.5 theme modes. [FIXED] shows one variant; [FOLLOW_SYSTEM] switches Light/Dark with
 * the system night mode; [MATERIAL_YOU] uses the system palette on API 31+ and falls back to the
 * design's own palette (Light/Dark) below that.
 */
enum class ThemeMode { FIXED, FOLLOW_SYSTEM, MATERIAL_YOU }

/** A font id (see FontCatalog) with the weight this role wants. */
data class FontSpec(val fontId: String, val weight: Int)

/** Quick Tune's S/M/L: a text-size step, not a widget size class (NOTES.md "文字サイズ"). */
enum class TextSizeStep(val scale: Float) {
    SMALL(0.82f),
    MEDIUM(1f),
    LARGE(1.2f),
}

/** The tokens Quick Tune edits (End-State §5.0); elements reference them via [ColorRef.Token] / font ids. */
data class StyleTokens(
    val palette: Palette,
    val themeMode: ThemeMode = ThemeMode.FIXED,
    val fontPrimary: FontSpec,
    val fontSecondary: FontSpec,
    val textSize: TextSizeStep = TextSizeStep.MEDIUM,
)

/** Provenance of a snapshot copied from the immutable built-in library. */
data class DesignSource(
    val builtinId: String,
    val version: Int,
    val kitId: String,
)

internal fun StyleTokens.normalized(): StyleTokens = copy(
    fontPrimary = fontPrimary.copy(weight = fontPrimary.weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT)),
    fontSecondary = fontSecondary.copy(weight = fontSecondary.weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT)),
)
