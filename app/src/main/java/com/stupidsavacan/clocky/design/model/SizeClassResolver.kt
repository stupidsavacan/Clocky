package com.stupidsavacan.clocky.design.model

/**
 * Historical Phase 1A helper retained for legacy tests only. Production uses SizeClassRule.
 * Responsive model/resolver code uses [SizeClassRule] with MAX_WIDTH / MIN_HEIGHT.
 *
 * Input is the host's OPTION_APPWIDGET_MIN_HEIGHT (dp): the landscape height, so one widget keeps
 * one class in both orientations, as in Phase 0. Heights measured on real hosts in Phase 0 —
 * 4×1 ≈ 54–58dp, 4×2 ≈ 125–132dp, Issue #31 fresh add 191dp — classify identically under the
 * former 94dp profile boundary and this 100dp boundary.
 */
@Deprecated("Historical two-class policy; use SizeClassRule with MAX_WIDTH / MIN_HEIGHT")
object SizeClassResolver {
    const val STRIP_MAX_HEIGHT_DP_EXCLUSIVE = 100

    fun resolve(minHeightDp: Int): SizeClass = when {
        minHeightDp <= 0 -> SizeClass.CARD
        minHeightDp < STRIP_MAX_HEIGHT_DP_EXCLUSIVE -> SizeClass.STRIP
        else -> SizeClass.CARD
    }
}
