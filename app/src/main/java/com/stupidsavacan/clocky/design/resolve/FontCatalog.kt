package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.customization.font.LegacyWidgetFontFamilyPolicy
import com.stupidsavacan.clocky.customization.font.RemoteViewsFontWeightPolicy
import com.stupidsavacan.clocky.customization.font.FontCapabilities
import com.stupidsavacan.clocky.customization.font.FontWeightResolver
import com.stupidsavacan.clocky.design.library.TypefaceCategory
import com.stupidsavacan.clocky.design.model.FontIds

/**
 * Phase 2 font library (End-State 5.4): 12 families. Six are platform families (system sans plus
 * the five retained MVP aliases) and six are bundled, redistributable OFL families (licenses under
 * third_party/fonts). Bundled families are static files, so each declares the weights it really
 * has; any other request resolves to the nearest face and is disclosed as an approximation. They
 * need `res/font` support in RemoteViews (API 26+); below that the platform sans stands in.
 *
 * Phase 1A font set (kept): the system sans family (requested weight 100..900, resolved per SDK) plus the
 * retained MVP's five platform families, which are exact only at weight 400 (PR #27 behavior).
 * The bundled 12/24-family library is Phase 2/5 scope.
 */
object FontCatalog {
    val legacyFamilyIds: List<String> = listOf(
        "sans-serif-light",
        "sans-serif-rounded",
        "serif",
        "sans-serif-condensed",
        "monospace",
    )

    enum class Source { SYSTEM, BUNDLED }

    data class Family(
        val id: String,
        val category: TypefaceCategory,
        val source: Source,
        /** Weights with a real face; the system sans has all nine canonical weights. */
        val weights: Set<Int>,
        /** Whether the family's glyphs cover CJK; if not, e.g. a Japanese date uses the system font for kanji. */
        val coversCjk: Boolean = false,
    )

    const val MIN_BUNDLED_FONT_SDK = 26

    val families: List<Family> = listOf(
        Family(FontIds.SYSTEM_SANS, TypefaceCategory.MODERN, Source.SYSTEM, (100..900 step 100).toSet(), coversCjk = true),
        Family("sans-serif-light", TypefaceCategory.MODERN, Source.SYSTEM, setOf(400), coversCjk = true),
        Family("sans-serif-rounded", TypefaceCategory.ROUNDED, Source.SYSTEM, setOf(400), coversCjk = true),
        Family("serif", TypefaceCategory.SERIF, Source.SYSTEM, setOf(400), coversCjk = true),
        Family("sans-serif-condensed", TypefaceCategory.CONDENSED, Source.SYSTEM, setOf(400), coversCjk = true),
        Family("monospace", TypefaceCategory.MONO, Source.SYSTEM, setOf(400), coversCjk = true),
        Family("clocky-poppins", TypefaceCategory.MODERN, Source.BUNDLED, setOf(300, 400, 500, 600, 700)),
        Family("clocky-varela-round", TypefaceCategory.ROUNDED, Source.BUNDLED, setOf(400)),
        Family("clocky-dm-serif-display", TypefaceCategory.SERIF, Source.BUNDLED, setOf(400)),
        Family("clocky-barlow-condensed", TypefaceCategory.CONDENSED, Source.BUNDLED, setOf(300, 400, 500, 600, 700)),
        Family("clocky-plex-mono", TypefaceCategory.MONO, Source.BUNDLED, setOf(300, 400, 500, 700)),
        Family("clocky-bebas-neue", TypefaceCategory.DISPLAY, Source.BUNDLED, setOf(400)),
    )

    private val byId: Map<String, Family> = families.associateBy { it.id }

    fun family(fontId: String): Family? = byId[fontId]

    val bundledIds: List<String> = families.filter { it.source == Source.BUNDLED }.map { it.id }

    val allIds: List<String> = families.map { it.id }

    data class FaceResolution(
        val face: ResolvedFace,
        val requestedWeight: Int,
        val effectiveWeight: Int,
        val fontFallbackReason: String?,
    )

    fun resolve(fontId: String, requestedWeight: Int, sdkInt: Int): FaceResolution {
        val weight = RemoteViewsFontWeightPolicy.resolve(requestedWeight, sdkInt)
        val sans = ResolvedFace(FontIds.SYSTEM_SANS, weight.effective)
        return when {
            fontId == FontIds.SYSTEM_SANS ->
                FaceResolution(sans, weight.requested, weight.effective, null)
            fontId in legacyFamilyIds -> {
                val exact = LegacyWidgetFontFamilyPolicy.exactFamilyOrNull(fontId, weight.effective)
                if (exact != null) {
                    FaceResolution(
                        ResolvedFace(fontId, LegacyWidgetFontFamilyPolicy.EXACT_WEIGHT),
                        weight.requested,
                        weight.effective,
                        null,
                    )
                } else {
                    FaceResolution(sans, weight.requested, weight.effective, REASON_WEIGHT_UNAVAILABLE)
                }
            }
            fontId in bundledIds -> resolveBundled(byId.getValue(fontId), requestedWeight, sdkInt, sans, weight.effective)
            else -> FaceResolution(sans, weight.requested, weight.effective, REASON_UNKNOWN_FONT)
        }
    }

    private fun resolveBundled(
        family: Family,
        requestedWeight: Int,
        sdkInt: Int,
        sansFallback: ResolvedFace,
        sansWeight: Int,
    ): FaceResolution {
        val requested = requestedWeight.coerceIn(100, 900)
        if (sdkInt < MIN_BUNDLED_FONT_SDK) {
            return FaceResolution(sansFallback, requested, sansWeight, REASON_NEEDS_API_26)
        }
        val face = FontWeightResolver.resolve(requested, FontCapabilities(staticWeights = family.weights))
        return FaceResolution(ResolvedFace(family.id, face.effective), requested, face.effective, null)
    }

    const val REASON_WEIGHT_UNAVAILABLE = "family-has-no-face-at-weight"
    const val REASON_UNKNOWN_FONT = "unknown-font"
    const val REASON_NEEDS_API_26 = "bundled-font-needs-api-26"
}
