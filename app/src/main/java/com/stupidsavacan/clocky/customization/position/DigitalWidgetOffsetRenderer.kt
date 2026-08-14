package com.stupidsavacan.clocky.customization.position

import android.os.Build
import android.widget.RemoteViews
import com.android.deskclock.R
import com.stupidsavacan.clocky.customization.model.ResolvedWidgetOffsets

/**
 * Applies Clocky's independently requested time/date X/Y offsets to the classic RemoteViews widget.
 *
 * View translation became a remotely callable operation in API 31. Older hosts therefore preserve
 * the requested values in storage but render an effective zero offset instead of attempting a
 * padding/margin approximation that would move sibling content or change layout height.
 */
object DigitalWidgetOffsetRenderer {
    const val MIN_TRANSLATION_SDK = 31

    data class EffectiveOffsets(
        val timeXDp: Float,
        val timeYDp: Float,
        val dateXDp: Float,
        val dateYDp: Float,
        val exact: Boolean,
    )

    fun effectiveOffsets(requested: ResolvedWidgetOffsets, sdkInt: Int): EffectiveOffsets {
        if (sdkInt < MIN_TRANSLATION_SDK) {
            return EffectiveOffsets(0f, 0f, 0f, 0f, exact = requested.isZero())
        }
        return EffectiveOffsets(
            timeXDp = finiteOrZero(requested.timeXDp),
            timeYDp = finiteOrZero(requested.timeYDp),
            dateXDp = finiteOrZero(requested.dateXDp),
            dateYDp = finiteOrZero(requested.dateYDp),
            exact = true,
        )
    }

    fun applyRemoteViews(
        remoteViews: RemoteViews,
        requested: ResolvedWidgetOffsets,
        density: Float,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): EffectiveOffsets {
        val effective = effectiveOffsets(requested, sdkInt)
        if (sdkInt < MIN_TRANSLATION_SDK) {
            return effective
        }

        remoteViews.setFloat(
            R.id.clock_weight_container,
            "setTranslationX",
            effective.timeXDp * density,
        )
        remoteViews.setFloat(
            R.id.clock_weight_container,
            "setTranslationY",
            effective.timeYDp * density,
        )
        remoteViews.setFloat(
            R.id.date_weight_container,
            "setTranslationX",
            effective.dateXDp * density,
        )
        remoteViews.setFloat(
            R.id.date_weight_container,
            "setTranslationY",
            effective.dateYDp * density,
        )
        return effective
    }

    private fun finiteOrZero(value: Float): Float = if (value.isFinite()) value else 0f

    private fun ResolvedWidgetOffsets.isZero(): Boolean =
        finiteOrZero(timeXDp) == 0f &&
            finiteOrZero(timeYDp) == 0f &&
            finiteOrZero(dateXDp) == 0f &&
            finiteOrZero(dateYDp) == 0f
}
