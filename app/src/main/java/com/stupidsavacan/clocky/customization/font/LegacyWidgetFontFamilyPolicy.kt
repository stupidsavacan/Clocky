package com.stupidsavacan.clocky.customization.font

/** Exact platform family aliases retained by the repository-owned MVP source bundle. */
object LegacyWidgetFontFamilyPolicy {
    const val EXACT_WEIGHT = 400

    private val exactFamilies = setOf(
        "sans-serif-light",
        "sans-serif-rounded",
        "serif",
        "sans-serif-condensed",
        "monospace",
    )

    fun exactFamilyOrNull(fontFamily: String, effectiveWeight: Int): String? =
        fontFamily.takeIf { effectiveWeight == EXACT_WEIGHT && it in exactFamilies }
}
