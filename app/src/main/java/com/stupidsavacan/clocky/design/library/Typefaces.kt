package com.stupidsavacan.clocky.design.library

import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.FontSpec

/** End-State 5.4 categories. Each resolves to a (time, date) font pair per kit. */
enum class TypefaceCategory { MODERN, ROUNDED, SERIF, CONDENSED, MONO, DISPLAY }

data class TypePair(val primary: FontSpec, val secondary: FontSpec)

/**
 * Phase 1B maps the six categories onto platform font families that Clocky already renders
 * exactly and that need no redistribution license: system sans (weights 100..900) and the five
 * platform aliases from the Phase 1A catalog (exact at weight 400). Bundled OFL families are
 * Phase 2 scope (End-State 13), when these pairs only change their font ids.
 */
object Typefaces {
    private const val ROUNDED = "sans-serif-rounded"
    private const val SERIF = "serif"
    private const val CONDENSED = "sans-serif-condensed"
    private const val MONOSPACE = "monospace"

    /**
     * A kit's six pairs from its weight vocabulary: sans time/date weights for Modern, heavier
     * weights for Display. Non-sans categories use the family's regular face.
     */
    fun pairs(
        modernTime: Int,
        modernDate: Int,
        displayTime: Int,
        displayDate: Int,
    ): Map<TypefaceCategory, TypePair> {
        fun sans(weight: Int) = FontSpec(FontIds.SYSTEM_SANS, weight)
        fun regular(id: String) = FontSpec(id, 400)
        return mapOf(
            TypefaceCategory.MODERN to TypePair(sans(modernTime), sans(modernDate)),
            TypefaceCategory.ROUNDED to TypePair(regular(ROUNDED), regular(ROUNDED)),
            TypefaceCategory.SERIF to TypePair(regular(SERIF), sans(modernDate)),
            TypefaceCategory.CONDENSED to TypePair(regular(CONDENSED), regular(CONDENSED)),
            TypefaceCategory.MONO to TypePair(regular(MONOSPACE), regular(MONOSPACE)),
            TypefaceCategory.DISPLAY to TypePair(sans(displayTime), sans(displayDate)),
        )
    }
}
