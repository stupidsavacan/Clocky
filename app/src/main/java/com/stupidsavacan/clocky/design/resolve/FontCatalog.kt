package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.customization.font.LegacyWidgetFontFamilyPolicy
import com.stupidsavacan.clocky.customization.font.RemoteViewsFontWeightPolicy
import com.stupidsavacan.clocky.design.model.FontIds

/**
 * Phase 1A font set: the system sans family (requested weight 100..900, resolved per SDK) plus the
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

    val allIds: List<String> = listOf(FontIds.SYSTEM_SANS) + legacyFamilyIds

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
            else -> FaceResolution(sans, weight.requested, weight.effective, REASON_UNKNOWN_FONT)
        }
    }

    const val REASON_WEIGHT_UNAVAILABLE = "family-has-no-face-at-weight"
    const val REASON_UNKNOWN_FONT = "unknown-font"
}
