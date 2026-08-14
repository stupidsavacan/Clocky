package com.stupidsavacan.clocky.customization.font

import java.util.Locale

/** Shared bounds and display rules for TextView letter spacing, expressed in em units. */
object WidgetLetterSpacingPolicy {
    const val MIN_EM = -0.20f
    const val MAX_EM = 0.50f
    const val STEP_EM = 0.01f

    fun normalize(value: Float): Float = value.coerceIn(MIN_EM, MAX_EM)

    fun display(value: Float): String = String.format(Locale.US, "%.2f em", normalize(value))
}
