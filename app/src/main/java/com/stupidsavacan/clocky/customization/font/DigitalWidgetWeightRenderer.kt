package com.stupidsavacan.clocky.customization.font

import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import android.widget.TextClock

import com.android.deskclock.R

/**
 * Applies Clocky-owned font-weight settings to the classic RemoteViews digital widget.
 *
 * RemoteViews cannot receive an arbitrary Typeface instance. The widget therefore exposes one
 * TextClock per canonical 100-step weight and toggles visibility. The requested 100..900 value is
 * preserved by WidgetSettings; only the renderer resolves to an available canonical face.
 */
object DigitalWidgetWeightRenderer {
    private val capabilities = FontCapabilities()

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
    )

    fun applyRemoteViews(
        remoteViews: RemoteViews,
        requestedTimeWeight: Int,
        requestedDateWeight: Int,
        clockSizePx: Float,
        dateSizePx: Float,
        dateFormat: CharSequence,
    ) {
        val time = FontWeightResolver.resolve(requestedTimeWeight, capabilities)
        val date = FontWeightResolver.resolve(requestedDateWeight, capabilities)

        applyGroup(
            remoteViews = remoteViews,
            ids = timeViewIds,
            effectiveWeight = time.effective,
            sizePx = clockSizePx,
            dateFormat = null,
        )
        applyGroup(
            remoteViews = remoteViews,
            ids = dateViewIds,
            effectiveWeight = date.effective,
            sizePx = dateSizePx,
            dateFormat = dateFormat,
        )
    }

    /** Apply the same resolved weight to the in-process measurement views. */
    fun applySizerWeights(
        clock: TextClock,
        date: TextClock,
        requestedTimeWeight: Int,
        requestedDateWeight: Int,
    ) {
        val time = FontWeightResolver.resolve(requestedTimeWeight, capabilities)
        val dateWeight = FontWeightResolver.resolve(requestedDateWeight, capabilities)
        clock.typeface = typefaceFor(time.effective)
        date.typeface = typefaceFor(dateWeight.effective)
    }

    fun effectiveWeight(requestedWeight: Int): Int =
        FontWeightResolver.resolve(requestedWeight, capabilities).effective

    private fun applyGroup(
        remoteViews: RemoteViews,
        ids: IntArray,
        effectiveWeight: Int,
        sizePx: Float,
        dateFormat: CharSequence?,
    ) {
        val selectedIndex = ((effectiveWeight.coerceIn(100, 900) - 100) / 100)
        ids.forEachIndexed { index, id ->
            remoteViews.setViewVisibility(id, if (index == selectedIndex) View.VISIBLE else View.GONE)
            if (index == selectedIndex) {
                remoteViews.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_PX, sizePx)
            }
            if (dateFormat != null) {
                remoteViews.setCharSequence(id, "setFormat12Hour", dateFormat)
                remoteViews.setCharSequence(id, "setFormat24Hour", dateFormat)
            }
        }
    }

    private fun typefaceFor(weight: Int): Typeface {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create("sans-serif", weight.coerceIn(100, 900), false)
        }
        return Typeface.create(legacyFamilyFor(weight), Typeface.NORMAL)
    }

    internal fun legacyFamilyFor(weight: Int): String = when (weight.coerceIn(100, 900)) {
        in 100..200 -> "sans-serif-thin"
        300 -> "sans-serif-light"
        400 -> "sans-serif"
        in 500..700 -> "sans-serif-medium"
        else -> "sans-serif-black"
    }
}
