package com.stupidsavacan.clocky.studio.canvas

import com.stupidsavacan.clocky.design.model.SizeClassRule
import com.stupidsavacan.clocky.design.resolve.SizeContext
import kotlin.math.roundToInt

/** Requested text size uses the start span, never the fitted size or changing preview scale. */
object CanvasPinch {
    fun size(startSp: Float, startSpan: Float, span: Float): Float? {
        if (!startSp.isFinite() || !startSpan.isFinite() || !span.isFinite() || startSpan <= 0f || span < 0f) return null
        return (startSp * (span / startSpan)).takeIf { it.isFinite() }
    }
}

/** Moto Launcher3 pairs from phase3a0/raw-inputs.csv. Missing cells are extrapolated, not measured. */
data class PreviewCells(val columns: Int, val rows: Int) {
    init { require(columns in 2..5 && rows in 1..4) }
    val size: SizeContext get() = SizeContext(
        173 + (columns - 2) * 95, intArrayOf(58, 132, 206, 281)[rows - 1],
        325 + (columns - 2) * 171, intArrayOf(122, 260, 398, 537)[rows - 1],
    )
    val sizeClass get() = SizeClassRule.resolve(size.maxWidthDp.toFloat(), size.minHeightDp.toFloat())
    fun dragged(dxDp: Float, dyDp: Float, landscape: Boolean = false): PreviewCells = PreviewCells(
        (columns + (dxDp / if (landscape) 171f else 95f).roundToInt()).coerceIn(2, 5),
        (rows + (dyDp / if (landscape) 74f else 138f).roundToInt()).coerceIn(1, 4),
    )
    companion object {
        val ALL = (2..5).flatMap { c -> (1..4).map { r -> PreviewCells(c, r) } }
    }
}
