package com.stupidsavacan.clocky.design.model

/** Phase 3A-0 calibrated widget rule: MAX_WIDTH / MIN_HEIGHT, never an individual entry's bounds. */
object SizeClassRule {
    const val STRIP_MAX_H = 100f
    const val LARGE_MIN_H = 160f
    const val SQUARE_RATIO = 2.5625f

    fun resolve(maxWidthDp: Float, minHeightDp: Float): SizeClass = when {
        !maxWidthDp.isFinite() || !minHeightDp.isFinite() || maxWidthDp <= 0f || minHeightDp <= 0f -> SizeClass.CARD
        minHeightDp < STRIP_MAX_H -> SizeClass.STRIP
        maxWidthDp.toDouble() < SQUARE_RATIO.toDouble() * minHeightDp -> SizeClass.SQUARE
        minHeightDp >= LARGE_MIN_H -> SizeClass.LARGE
        else -> SizeClass.CARD
    }
}
