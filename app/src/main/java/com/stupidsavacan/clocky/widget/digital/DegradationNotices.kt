package com.stupidsavacan.clocky.widget.digital

import android.content.Context
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.resolve.Degradation
import com.stupidsavacan.clocky.design.resolve.FontCatalog
import com.stupidsavacan.clocky.design.resolve.InfoHiddenReason
import com.stupidsavacan.clocky.design.resolve.TextElementKind
import com.stupidsavacan.clocky.widget.easy.DesignLabels
import kotlin.math.roundToInt

/** Editor copy for requested ≠ effective differences (End-State principle 5). */
object DegradationNotices {
    fun describe(context: Context, degradations: List<Degradation>): List<String> =
        degradations.distinct().map { d ->
            when (d) {
                is Degradation.TemplateFallback -> context.getString(R.string.clocky_degraded_template_unknown, d.requested)
                Degradation.AmPmLocalized -> context.getString(R.string.clocky_degraded_ampm_localized)
                is Degradation.WeightApproximated -> context.getString(
                    if (d.fontId != null) R.string.clocky_degraded_weight_font else R.string.clocky_degraded_weight,
                    element(context, d.element), d.requested, d.effective,
                )
                is Degradation.FontFallback ->
                    if (d.reason == FontCatalog.REASON_HOST_BUNDLED_UNSUPPORTED) {
                        // One line per font, not per element: Time/Date/Info share the same host limit.
                        val name = context.getString(DesignLabels.font(d.requestedFontId))
                        context.getString(R.string.clocky_degraded_font_host, name)
                    } else context.getString(
                    when (d.reason) {
                        FontCatalog.REASON_UNKNOWN_FONT -> R.string.clocky_degraded_font_unknown
                        FontCatalog.REASON_NEEDS_API_26 -> R.string.clocky_degraded_font_needs_api
                        else -> R.string.clocky_degraded_font_weight
                    },
                    element(context, d.element),
                )
                is Degradation.InfoNotShown -> context.getString(
                    when (d.reason) {
                        InfoHiddenReason.STRIP_SIZE -> R.string.clocky_degraded_info_strip
                        InfoHiddenReason.MINIMAL_TEMPLATE -> R.string.clocky_degraded_info_minimal
                    },
                )
                Degradation.RenderedBackgroundStatic -> context.getString(R.string.clocky_degraded_rendered_background)
                is Degradation.OffsetUnsupported ->
                    context.getString(R.string.clocky_degraded_offset, element(context, d.element))
                is Degradation.OffsetClamped ->
                    context.getString(R.string.clocky_degraded_offset_clamped, element(context, d.element))
                is Degradation.RadiusApproximated ->
                    context.getString(R.string.clocky_degraded_radius, d.effectiveDp.roundToInt())
                Degradation.ThemeSwitchUnavailable -> context.getString(R.string.clocky_degraded_theme_switch)
                Degradation.DynamicColorUnavailable -> context.getString(R.string.clocky_degraded_dynamic_color)
            }
        }.distinct()

    private fun element(context: Context, kind: TextElementKind): String = context.getString(
        when (kind) {
            TextElementKind.TIME -> R.string.clocky_element_time
            TextElementKind.DATE -> R.string.clocky_element_date
            TextElementKind.INFO -> R.string.clocky_element_info
        },
    )
}
