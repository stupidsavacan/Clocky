package com.stupidsavacan.clocky.design.library

import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.BackgroundElement
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.ColorRole
import com.stupidsavacan.clocky.design.model.CornerRadius
import com.stupidsavacan.clocky.design.model.DateElement
import com.stupidsavacan.clocky.design.model.DesignLayout
import com.stupidsavacan.clocky.design.model.DesignSource
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.Palette
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.StyleTokens
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement

/**
 * An immutable, versioned library asset. Placing a widget copies [design] (with its [DesignSource]);
 * the widget never reads this object again, so a later version bump cannot mutate placed widgets.
 */
data class BuiltinDesign(
    val id: String,
    val version: Int,
    val kitId: String,
    val design: DigitalDesign,
) {
    /** The snapshot a widget receives. */
    fun instantiate(): DigitalDesign = design
}

/**
 * Phase 1B Digital library: 4 kits x 2 designs (End-State 13). Every element references style
 * tokens (palette roles, font tokens), so Quick Tune restyles a whole design by swapping tokens
 * while the kit's proportions (sizes, spacing, alignment, background) stay intact.
 */
object BuiltinDesigns {
    const val CLOCKY_DEFAULT_ID = "clocky-default"
    const val LIBRARY_VERSION = 1

    private fun build(
        id: String,
        kit: Kit,
        palette: Palette,
        native: TypefaceCategory,
        card: Template,
        strip: Template?,
        time: TextStyle,
        date: TextStyle,
        background: BackgroundElement = BackgroundElement(type = BackgroundType.NONE),
    ): BuiltinDesign {
        val pair = kit.typePairs.getValue(native)
        val overrides = strip?.takeIf { it != card }?.let { mapOf(SizeClass.STRIP to LayoutPatch(template = it)) }
            ?: emptyMap()
        return BuiltinDesign(
            id = id,
            version = LIBRARY_VERSION,
            kitId = kit.id,
            design = DigitalDesign(
                origin = id,
                time = TimeElement(style = time),
                date = DateElement(visible = true, style = date),
                background = background,
                layout = DesignLayout(template = card, overrides = overrides),
                style = StyleTokens(palette = palette, fontPrimary = pair.primary, fontSecondary = pair.secondary),
                source = DesignSource(builtinId = id, version = LIBRARY_VERSION, kitId = kit.id),
            ),
        )
    }

    private fun timeStyle(sizeSp: Float, letterSpacingEm: Float, opacity: Float = 1f) = TextStyle(
        fontId = FontIds.TOKEN_PRIMARY,
        sizeSp = sizeSp,
        letterSpacingEm = letterSpacingEm,
        color = ColorRef.Token(ColorRole.PRIMARY),
        opacity = opacity,
        alignment = Alignment.CENTER,
    )

    private fun dateStyle(
        sizeSp: Float,
        letterSpacingEm: Float,
        role: ColorRole = ColorRole.SECONDARY,
        opacity: Float = 1f,
    ) = TextStyle(
        fontId = FontIds.TOKEN_SECONDARY,
        sizeSp = sizeSp,
        letterSpacingEm = letterSpacingEm,
        color = ColorRef.Token(role),
        opacity = opacity,
        alignment = Alignment.CENTER,
    )

    private fun solid(opacity: Float, radiusDp: Float, paddingDp: Float) = BackgroundElement(
        type = BackgroundType.SOLID,
        color = ColorRef.Token(ColorRole.SURFACE),
        opacity = opacity,
        cornerRadius = CornerRadius.Dp(radiusDp),
        paddingDp = paddingDp,
    )

    // Kit Default: Center Stack on Card, Inline on Strip (End-State 5.8, mockup Main / DefaultTonal).
    val clockyDefault = build(
        id = CLOCKY_DEFAULT_ID, kit = Kits.default, palette = Palettes.CLOCKY, native = TypefaceCategory.MODERN,
        card = Template.CENTER_STACK, strip = Template.INLINE,
        time = timeStyle(sizeSp = 88f, letterSpacingEm = -0.01f),
        date = dateStyle(sizeSp = 18f, letterSpacingEm = 0.01f),
    )

    val defaultTonal = build(
        id = "default-tonal", kit = Kits.default, palette = Palettes.MINT, native = TypefaceCategory.MODERN,
        card = Template.CENTER_STACK, strip = Template.INLINE,
        time = timeStyle(sizeSp = 84f, letterSpacingEm = -0.02f),
        date = dateStyle(sizeSp = 17f, letterSpacingEm = 0.01f),
        background = solid(opacity = 1f, radiusDp = 24f, paddingDp = 12f),
    )

    // Kit Minimal: hairline time-only, and a quiet split on glass (mockup MinimalHairline / MinimalSplit).
    val minimalHairline = build(
        id = "minimal-hairline", kit = Kits.minimal, palette = Palettes.CLOCKY, native = TypefaceCategory.MODERN,
        card = Template.MINIMAL, strip = null,
        time = timeStyle(sizeSp = 96f, letterSpacingEm = 0.04f, opacity = 0.92f),
        date = dateStyle(sizeSp = 12f, letterSpacingEm = 0.24f, opacity = 0.7f),
    )

    val minimalQuietSplit = build(
        id = "minimal-quiet-split", kit = Kits.minimal, palette = Palettes.MONO, native = TypefaceCategory.MODERN,
        card = Template.SPLIT, strip = null,
        time = timeStyle(sizeSp = 72f, letterSpacingEm = -0.01f),
        date = dateStyle(sizeSp = 12f, letterSpacingEm = 0.24f, opacity = 0.7f),
        background = solid(opacity = 0.4f, radiusDp = 24f, paddingDp = 14f),
    )

    // Kit Bold: poster-heavy time with an accent date, and a solid block (mockup BoldPoster / BoldBlock).
    val boldPoster = build(
        id = "bold-poster", kit = Kits.bold, palette = Palettes.POSTER, native = TypefaceCategory.DISPLAY,
        card = Template.TIME_FIRST, strip = Template.INLINE,
        time = timeStyle(sizeSp = 120f, letterSpacingEm = -0.01f),
        date = dateStyle(sizeSp = 16f, letterSpacingEm = 0.2f, role = ColorRole.ACCENT),
    )

    val boldBlock = build(
        id = "bold-block", kit = Kits.bold, palette = Palettes.EMBER, native = TypefaceCategory.DISPLAY,
        card = Template.SPLIT, strip = null,
        time = timeStyle(sizeSp = 112f, letterSpacingEm = -0.01f),
        date = dateStyle(sizeSp = 16f, letterSpacingEm = 0.18f),
        background = solid(opacity = 1f, radiusDp = 12f, paddingDp = 14f),
    )

    // Kit Editorial: serif time over small-caps-like date, and paper (mockup EditorialSerif / EditorialPaper).
    val editorialSerif = build(
        id = "editorial-serif", kit = Kits.editorial, palette = Palettes.PAPER, native = TypefaceCategory.SERIF,
        card = Template.CENTER_STACK, strip = Template.INLINE,
        time = timeStyle(sizeSp = 96f, letterSpacingEm = -0.02f),
        date = dateStyle(sizeSp = 12f, letterSpacingEm = 0.22f),
    )

    val editorialPaper = build(
        id = "editorial-paper", kit = Kits.editorial, palette = Palettes.PAPER, native = TypefaceCategory.SERIF,
        card = Template.SPLIT, strip = null,
        time = timeStyle(sizeSp = 96f, letterSpacingEm = -0.02f),
        date = dateStyle(sizeSp = 13f, letterSpacingEm = 0.06f),
        background = solid(opacity = 0.94f, radiusDp = 28f, paddingDp = 14f),
    )

    /** Gallery order: kit by kit, the default first. */
    val all: List<BuiltinDesign> = listOf(
        clockyDefault, defaultTonal,
        minimalHairline, minimalQuietSplit,
        boldPoster, boldBlock,
        editorialSerif, editorialPaper,
    )

    fun byId(id: String?): BuiltinDesign? = all.firstOrNull { it.id == id }

    val default: BuiltinDesign get() = clockyDefault
}
