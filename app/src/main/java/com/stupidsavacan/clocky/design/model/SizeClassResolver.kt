package com.stupidsavacan.clocky.design.model

/**
 * Maps host dimensions to a Phase 1A size class (CLOCKY_END_STATE.md §9; Strip and Card only).
 *
 * Input is the host's OPTION_APPWIDGET_MIN_HEIGHT (dp): the landscape height, so one widget keeps
 * one class in both orientations, as in Phase 0. Heights measured on real hosts in Phase 0 —
 * 4×1 ≈ 54–58dp, 4×2 ≈ 125–132dp, Issue #31 fresh add 191dp — classify identically under the
 * former 94dp profile boundary and this 100dp boundary.
 */
object SizeClassResolver {
    const val STRIP_MAX_HEIGHT_DP_EXCLUSIVE = 100

    fun resolve(minHeightDp: Int): SizeClass = when {
        minHeightDp <= 0 -> SizeClass.CARD
        minHeightDp < STRIP_MAX_HEIGHT_DP_EXCLUSIVE -> SizeClass.STRIP
        else -> SizeClass.CARD
    }
}
