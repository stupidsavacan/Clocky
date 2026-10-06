package com.stupidsavacan.clocky.design.library

import com.stupidsavacan.clocky.design.model.Palette
import com.stupidsavacan.clocky.design.model.PaletteColors
import com.stupidsavacan.clocky.design.model.PaletteVariant

/**
 * Built-in palettes (immutable source assets). A design copies the palette it uses, so changing a
 * value here never recolors an already-placed widget. Values follow the 26-board mockup's kit
 * colors (Default/Tonal, Poster, Block, Editorial); the rest are neutral companions.
 *
 * Text roles must stay readable on their own [PaletteColors.surface] in both variants.
 */
object Palettes {
    /** Clocky Default / Hairline: bare text for a wallpaper; light text in Fixed, ink in Light. */
    val CLOCKY = Palette(
        id = "clocky",
        light = PaletteColors(primary = 0x1B1B1B, secondary = 0x3C3C42, accent = 0x1B1B1B, surface = 0xF4F2EE),
        dark = PaletteColors(primary = 0xFFFFFF, secondary = 0xE6E6E8, accent = 0xFFFFFF, surface = 0x1B1B1B),
    )

    /** Default Tonal (mockup DefaultTonal). */
    val MINT = Palette(
        id = "mint",
        light = PaletteColors(primary = 0x00504A, secondary = 0x1E3A36, accent = 0x00504A, surface = 0xD3E8E2),
        dark = PaletteColors(primary = 0xA9D6CC, secondary = 0xDCE8E5, accent = 0xA9D6CC, surface = 0x2B3D3A),
    )

    val MONO = Palette(
        id = "mono",
        light = PaletteColors(primary = 0x141414, secondary = 0x5C5C63, accent = 0x141414, surface = 0xFFFFFF),
        dark = PaletteColors(primary = 0xFFFFFF, secondary = 0xB8B8BE, accent = 0xFFFFFF, surface = 0x141414),
    )

    val SAND = Palette(
        id = "sand",
        light = PaletteColors(primary = 0x3B2F22, secondary = 0x7A6A55, accent = 0xB5651D, surface = 0xF3EBDD),
        dark = PaletteColors(primary = 0xF3E3C8, secondary = 0xBFA98A, accent = 0xE8A15B, surface = 0x2A241C),
    )

    val OCEAN = Palette(
        id = "ocean",
        light = PaletteColors(primary = 0x12304D, secondary = 0x4A6580, accent = 0x1F6FB5, surface = 0xDCE9F5),
        dark = PaletteColors(primary = 0xCFE4FA, secondary = 0x8FB0D0, accent = 0x7CC4FF, surface = 0x17283A),
    )

    val ROSE = Palette(
        id = "rose",
        light = PaletteColors(primary = 0x5A1F2E, secondary = 0x8A5A66, accent = 0xC2415B, surface = 0xF6DDE2),
        dark = PaletteColors(primary = 0xFFD9E0, secondary = 0xD1A3AE, accent = 0xFFB1C1, surface = 0x3A2229),
    )

    /** Bold Poster: bare heavy time with an orange date (mockup BoldPoster accent #FF7A3D). */
    val POSTER = Palette(
        id = "poster",
        light = PaletteColors(primary = 0x141414, secondary = 0x3C3C42, accent = 0xB3501C, surface = 0xF4F2EE),
        dark = PaletteColors(primary = 0xFFFFFF, secondary = 0xE6E6E8, accent = 0xFF7A3D, surface = 0x1A1A1A),
    )

    /** Bold Block: ink on a solid accent card, the same in both themes (mockup BoldBlock). */
    val EMBER = Palette(
        id = "ember",
        light = PaletteColors(primary = 0x141414, secondary = 0x141414, accent = 0x141414, surface = 0xFF7A3D),
        dark = PaletteColors(primary = 0x141414, secondary = 0x141414, accent = 0x141414, surface = 0xFF7A3D),
        fixedVariant = PaletteVariant.LIGHT,
    )

    /** Editorial: warm paper and ink (mockup EditorialSerif / EditorialPaper). */
    val PAPER = Palette(
        id = "paper",
        light = PaletteColors(primary = 0x1C1A17, secondary = 0x5E574D, accent = 0x8A6A2E, surface = 0xF6F1E8),
        dark = PaletteColors(primary = 0xEFE8DC, secondary = 0xBDB4A6, accent = 0xC8A96A, surface = 0x26231F),
    )

    val all: List<Palette> = listOf(CLOCKY, MINT, MONO, SAND, OCEAN, ROSE, POSTER, EMBER, PAPER)
}
