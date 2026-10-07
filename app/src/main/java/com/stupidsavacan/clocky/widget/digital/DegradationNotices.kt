package com.stupidsavacan.clocky.widget.digital

import android.content.Context
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.resolve.Degradation
import com.stupidsavacan.clocky.design.resolve.FontCatalog
import com.stupidsavacan.clocky.design.resolve.TextElementKind
import kotlin.math.roundToInt

/** Editor copy for requested ≠ effective differences (End-State principle 5). */
object DegradationNotices {
    fun describe(context: Context, degradations: List<Degradation>): List<String> =
        degradations.distinct().map { d ->
            when (d) {
                is Degradation.WeightApproximated -> context.getString(
                    R.string.clocky_degraded_weight, element(context, d.element), d.requested, d.effective,
                )
                is Degradation.FontFallback -> context.getString(
                    if (d.reason == FontCatalog.REASON_UNKNOWN_FONT) {
                        R.string.clocky_degraded_font_unknown
                    } else {
                        R.string.clocky_degraded_font_weight
                    },
                    element(context, d.element),
                )
                is Degradation.OffsetUnsupported ->
                    context.getString(R.string.clocky_degraded_offset, element(context, d.element))
                is Degradation.OffsetClamped ->
                    context.getString(R.string.clocky_degraded_offset_clamped, element(context, d.element))
                is Degradation.RadiusApproximated ->
                    context.getString(R.string.clocky_degraded_radius, d.effectiveDp.roundToInt())
                Degradation.ThemeSwitchUnavailable -> context.getString(R.string.clocky_degraded_theme_switch)
                Degradation.DynamicColorUnavailable -> context.getString(R.string.clocky_degraded_dynamic_color)
            }
        }

    private fun element(context: Context, kind: TextElementKind): String = context.getString(
        when (kind) {
            TextElementKind.TIME -> R.string.clocky_element_time
            TextElementKind.DATE -> R.string.clocky_element_date
        },
    )
}
