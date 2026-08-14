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

    private val timeViewIds = intArrayOf(
        R.id.clock_w100,
        R.id.clock_w200,
        R.id.clock_w300,
        R.id.clock_w400,
        R.id.clock_w500,
        R.id.clock_w600,
        R.id.clock_w700,
        R.id.clock_w800,
        R.id.clock_w900,
        R.id.clock_legacy_sans_light,
        R.id.clock_legacy_sans_rounded,
        R.id.clock_legacy_serif,
        R.id.clock_legacy_sans_condensed,
        R.id.clock_legacy_monospace,
    )

    private val dateViewIds = intArrayOf(
        R.id.date_w100,
        R.id.date_w200,
        R.id.date_w300,
        R.id.date_w400,
        R.id.date_w500,
        R.id.date_w600,
        R.id.date_w700,
        R.id.date_w800,
        R.id.date_w900,
        R.id.date_legacy_sans_light,
        R.id.date_legacy_sans_rounded,
        R.id.date_legacy_serif,
        R.id.date_legacy_sans_condensed,
        R.id.date_legacy_monospace,
    )

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

        applyGroupTranslation(
            remoteViews = remoteViews,
            ids = timeViewIds,
            xPx = effective.timeXDp * density,
            yPx = effective.timeYDp * density,
        )
        applyGroupTranslation(
            remoteViews = remoteViews,
            ids = dateViewIds,
            xPx = effective.dateXDp * density,
            yPx = effective.dateYDp * density,
        )
        return effective
    }

    private fun applyGroupTranslation(
        remoteViews: RemoteViews,
        ids: IntArray,
        xPx: Float,
        yPx: Float,
    ) {
        ids.forEach { id ->
            remoteViews.setFloat(id, "setTranslationX", xPx)
            remoteViews.setFloat(id, "setTranslationY", yPx)
        }
    }

    private fun finiteOrZero(value: Float): Float = if (value.isFinite()) value else 0f

    private fun ResolvedWidgetOffsets.isZero(): Boolean =
        finiteOrZero(timeXDp) == 0f &&
            finiteOrZero(timeYDp) == 0f &&
            finiteOrZero(dateXDp) == 0f &&
            finiteOrZero(dateYDp) == 0f
}
