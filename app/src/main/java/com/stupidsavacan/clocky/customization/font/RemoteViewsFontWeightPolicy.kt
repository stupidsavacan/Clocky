package com.stupidsavacan.clocky.customization.font

/**
 * Resolves a user-requested 100..900 weight to a face that an AppWidget can safely select.
 *
 * API 28+ supports android:textFontWeight in XML, so every canonical 100-step face is available.
 * Older releases use stable platform family aliases and therefore expose a smaller face set.
 * The requested value remains untouched in WidgetSettings; this class only describes rendering.
 */
object RemoteViewsFontWeightPolicy {
    val canonicalWeights: List<Int> = (100..900 step 100).toList()

    private val legacyWeights: Set<Int> = setOf(100, 300, 400, 500, 700, 900)

    fun resolve(requestedWeight: Int, sdkInt: Int): ResolvedFontWeight {
        val available = if (sdkInt >= 28) canonicalWeights.toSet() else legacyWeights
        return FontWeightResolver.resolve(
            requestedWeight,
            FontCapabilities(staticWeights = available),
        )
    }

    fun canonicalIndex(effectiveWeight: Int): Int {
        val index = canonicalWeights.indexOf(effectiveWeight)
        require(index >= 0) { "Unsupported canonical widget weight: $effectiveWeight" }
        return index
    }
}
