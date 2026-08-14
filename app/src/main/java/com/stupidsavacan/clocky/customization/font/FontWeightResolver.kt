package com.stupidsavacan.clocky.customization.font

import kotlin.math.abs

data class FontCapabilities(
    val staticWeights: Set<Int> = CANONICAL_WEIGHTS,
    val variableWeightRange: IntRange? = null,
)

data class ResolvedFontWeight(
    val requested: Int,
    val effective: Int,
    val exact: Boolean,
    val variationSettings: String?,
    val reason: String,
)

object FontWeightResolver {
    fun resolve(requestedWeight: Int, capabilities: FontCapabilities): ResolvedFontWeight {
        val requested = requestedWeight.coerceIn(100, 900)
        val variable = capabilities.variableWeightRange
        if (variable != null) {
            val effective = requested.coerceIn(variable.first, variable.last)
            return ResolvedFontWeight(
                requested = requested,
                effective = effective,
                exact = effective == requested,
                variationSettings = "'wght' $effective",
                reason = if (effective == requested) "variable-font-exact" else "variable-font-clamped",
            )
        }

        val available = capabilities.staticWeights
            .map { it.coerceIn(100, 900) }
            .distinct()
            .ifEmpty { CANONICAL_WEIGHTS.toList() }
        val effective = available.minWithOrNull(
            compareBy<Int> { abs(it - requested) }.thenBy { it }
        ) ?: 400
        return ResolvedFontWeight(
            requested = requested,
            effective = effective,
            exact = effective == requested,
            variationSettings = null,
            reason = if (effective == requested) "static-face-exact" else "nearest-static-face",
        )
    }
}

val CANONICAL_WEIGHTS: Set<Int> = (100..900 step 100).toSet()
