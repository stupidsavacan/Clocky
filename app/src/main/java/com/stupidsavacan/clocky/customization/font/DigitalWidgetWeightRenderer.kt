package com.stupidsavacan.clocky.customization.font

import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import android.widget.TextClock
import androidx.annotation.RequiresApi

import com.android.deskclock.R

/**
 * Applies Clocky-owned text appearance settings to the classic RemoteViews digital widget.
 *
 * RemoteViews cannot receive an arbitrary Typeface instance. The widget therefore exposes one
 * TextClock per canonical 100-step weight and toggles visibility. The requested 100..900 value is
 * preserved by WidgetSettings; only the renderer resolves to a face available on the device.
 */
object DigitalWidgetWeightRenderer {
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
        dateEnabled: Boolean = true,
        timeLetterSpacing: Float = 0f,
        dateLetterSpacing: Float = 0f,
    ) {
        val sdkInt = Build.VERSION.SDK_INT
        val time = RemoteViewsFontWeightPolicy.resolve(requestedTimeWeight, sdkInt)
        val date = RemoteViewsFontWeightPolicy.resolve(requestedDateWeight, sdkInt)

        applyGroup(
            remoteViews = remoteViews,
            ids = timeViewIds,
            effectiveWeight = time.effective,
            sizePx = clockSizePx,
            dateFormat = null,
            enabled = true,
            letterSpacing = timeLetterSpacing,
        )
        applyGroup(
            remoteViews = remoteViews,
            ids = dateViewIds,
            effectiveWeight = date.effective,
            sizePx = dateSizePx,
            dateFormat = dateFormat,
            enabled = dateEnabled,
            letterSpacing = dateLetterSpacing,
        )
    }

    /** Apply identical appearance values to the in-process measurement views. */
    fun applySizerWeights(
        clock: TextClock,
        date: TextClock,
        requestedTimeWeight: Int,
        requestedDateWeight: Int,
        dateEnabled: Boolean = true,
        timeLetterSpacing: Float = 0f,
        dateLetterSpacing: Float = 0f,
    ) {
        val sdkInt = Build.VERSION.SDK_INT
        val time = RemoteViewsFontWeightPolicy.resolve(requestedTimeWeight, sdkInt)
        val dateWeight = RemoteViewsFontWeightPolicy.resolve(requestedDateWeight, sdkInt)
        clock.typeface = typefaceFor(time.effective)
        date.typeface = typefaceFor(dateWeight.effective)
        clock.letterSpacing = WidgetLetterSpacingPolicy.normalize(timeLetterSpacing)
        date.letterSpacing = WidgetLetterSpacingPolicy.normalize(dateLetterSpacing)
        date.visibility = if (dateEnabled) View.VISIBLE else View.GONE
    }

    fun effectiveWeight(requestedWeight: Int, sdkInt: Int = Build.VERSION.SDK_INT): Int =
        RemoteViewsFontWeightPolicy.resolve(requestedWeight, sdkInt).effective

    private fun applyGroup(
        remoteViews: RemoteViews,
        ids: IntArray,
        effectiveWeight: Int,
        sizePx: Float,
        dateFormat: CharSequence?,
        enabled: Boolean,
        letterSpacing: Float,
    ) {
        val selectedIndex = RemoteViewsFontWeightPolicy.canonicalIndex(effectiveWeight)
        val safeLetterSpacing = WidgetLetterSpacingPolicy.normalize(letterSpacing)
        ids.forEachIndexed { index, id ->
            val visible = enabled && index == selectedIndex
            remoteViews.setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)
            remoteViews.setFloat(id, "setLetterSpacing", safeLetterSpacing)
            if (visible) {
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
            return createWeightedTypeface(weight)
        }

        val spec = legacyTypefaceSpec(weight)
        return Typeface.create(spec.familyName, spec.style)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createWeightedTypeface(weight: Int): Typeface {
        val base = Typeface.create("sans-serif", Typeface.NORMAL)
        return Typeface.create(base, weight.coerceIn(100, 900), false)
    }

    internal fun legacyTypefaceSpec(weight: Int): LegacyTypefaceSpec = when (weight) {
        100 -> LegacyTypefaceSpec("sans-serif-thin", Typeface.NORMAL)
        300 -> LegacyTypefaceSpec("sans-serif-light", Typeface.NORMAL)
        400 -> LegacyTypefaceSpec("sans-serif", Typeface.NORMAL)
        500 -> LegacyTypefaceSpec("sans-serif-medium", Typeface.NORMAL)
        700 -> LegacyTypefaceSpec("sans-serif", Typeface.BOLD)
        900 -> LegacyTypefaceSpec("sans-serif-black", Typeface.NORMAL)
        else -> error("Legacy renderer received unsupported weight: $weight")
    }
}

data class LegacyTypefaceSpec(
    val familyName: String,
    val style: Int,
)
