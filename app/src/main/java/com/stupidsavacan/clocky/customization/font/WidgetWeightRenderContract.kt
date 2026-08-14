package com.stupidsavacan.clocky.customization.font

/**
 * Separates the value the user requests from what a concrete AppWidget renderer can apply.
 * The storage/UI contract always remains 100..900 even if a RemoteViews backend quantizes it.
 */
data class WidgetWeightRenderContract(
    val requestedWeight: Int,
    val resolved: ResolvedFontWeight,
    val backend: Backend,
) {
    enum class Backend {
        VARIABLE_AXIS,
        STATIC_FACE,
        CANONICAL_REMOTE_VIEWS_FALLBACK,
    }
}
