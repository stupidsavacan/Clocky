package com.stupidsavacan.clocky.widget.digital

import android.content.Context
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.resolve.ResolvedDigitalSpec
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Size fitting (contract §4; Phase 0 behavior): start at the requested sizes and, if the widget
 * would overflow the host bounds, scale time and date down by one common factor (binary search on
 * a per-mille scale) so their requested ratio survives.
 *
 * Measurement runs on the production RemoteViews applied locally, once per call. Only the text
 * sizes change between search steps. The texts are worst-case strings (widest time in the active
 * format, widest date of the year), so the fit holds as the clock and calendar advance without an
 * app update loop.
 */
object DigitalWidgetFit {
    private const val MAX_SCALE = 1000
    private const val MIN_SCALE = 1

    fun requested(context: Context, spec: ResolvedDigitalSpec): FitSizes {
        val metrics = context.resources.displayMetrics
        val sp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, metrics)
        return FitSizes(
            timePx = max(1f, (spec.time.sizeSp * sp).roundToInt().toFloat()),
            datePx = max(1f, (spec.date.sizeSp * sp).roundToInt().toFloat()),
        )
    }

    fun fit(context: Context, spec: ResolvedDigitalSpec, targetWidthPx: Int, targetHeightPx: Int): FitSizes {
        val requested = requested(context, spec)
        if (targetWidthPx <= 0 || targetHeightPx <= 0) return requested

        val root = DigitalWidgetComposer.compose(context, spec, requested)
            .apply(context, FrameLayout(context))
        val time = visibleTextIn(root, R.id.clocky_time_slot) ?: return requested
        val date = if (spec.dateVisible) visibleTextIn(root, R.id.clocky_date_slot) else null

        val is24 = DateFormat.is24HourFormat(context)
        time.text = widestTime(time, if (is24) spec.timeFormats.format24Hour else spec.timeFormats.format12Hour)
        date?.let { it.text = widestDate(it, spec.datePattern) }

        fun sizesAt(scale: Int) = FitSizes(
            timePx = max(1, (requested.timePx * scale / MAX_SCALE).roundToInt()).toFloat(),
            datePx = max(1, (requested.datePx * scale / MAX_SCALE).roundToInt()).toFloat(),
        )

        fun fits(scale: Int): Boolean {
            val s = sizesAt(scale)
            time.setTextSize(TypedValue.COMPLEX_UNIT_PX, s.timePx)
            date?.setTextSize(TypedValue.COMPLEX_UNIT_PX, s.datePx)
            val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            root.measure(unspecified, unspecified)
            return root.measuredWidth <= targetWidthPx && root.measuredHeight <= targetHeightPx
        }

        if (fits(MAX_SCALE)) return requested
        if (!fits(MIN_SCALE)) return sizesAt(MIN_SCALE)
        var low = MIN_SCALE
        var high = MAX_SCALE
        while (high - low > 1) {
            val mid = (low + high) / 2
            if (fits(mid)) low = mid else high = mid
        }
        return sizesAt(low)
    }

    /** The fragment's visible face inside a slot (exactly one by construction). */
    internal fun visibleTextIn(root: View, slotId: Int): TextView? {
        val slot = root.findViewById<ViewGroup>(slotId) ?: return null
        return findVisibleText(slot)
    }

    private fun findVisibleText(view: View): TextView? {
        if (view.visibility != View.VISIBLE) return null
        if (view is TextView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findVisibleText(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    /**
     * Widest rendering of [format] over a day. Hours and minutes are measured separately (widths
     * of "hours" and "minutes" add up), which needs 24 + 60 samples instead of 1440.
     */
    internal fun widestTime(view: TextView, format: String): CharSequence {
        val cal = Calendar.getInstance()
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MINUTE, 0)
        var widestHour = 0
        var best = -1f
        for (hour in 0..23) {
            cal.set(Calendar.HOUR_OF_DAY, hour)
            val w = width(view, DateFormat.format(format, cal))
            if (w > best) { best = w; widestHour = hour }
        }
        cal.set(Calendar.HOUR_OF_DAY, widestHour)
        var widest: CharSequence = DateFormat.format(format, cal)
        best = -1f
        for (minute in 0..59) {
            cal.set(Calendar.MINUTE, minute)
            val candidate = DateFormat.format(format, cal)
            val w = width(view, candidate)
            if (w > best) { best = w; widest = candidate }
        }
        return widest
    }

    /** Widest rendering of [pattern] over every day of the current year. */
    internal fun widestDate(view: TextView, pattern: String): CharSequence {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_YEAR, 1)
        val days = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
        var widest: CharSequence = DateFormat.format(pattern, cal)
        var best = -1f
        repeat(days) {
            val candidate = DateFormat.format(pattern, cal)
            val w = width(view, candidate)
            if (w > best) { best = w; widest = candidate }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return widest
    }

    /** Width as displayed, including transformations such as textAllCaps and letter spacing. */
    private fun width(view: TextView, text: CharSequence): Float {
        val shown = view.transformationMethod?.getTransformation(text, view) ?: text
        return view.paint.measureText(shown, 0, shown.length)
    }
}
