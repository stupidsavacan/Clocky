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
            infoPx = spec.info?.let { max(1f, (it.text.sizeSp * sp).roundToInt().toFloat()) } ?: 0f,
        )
    }

    /** Short-lived, generation-local samples. All entries have the same effective font/format/size. */
    class Samples {
        private val texts = mutableMapOf<String, CharSequence>()
        fun get(key: String, compute: () -> CharSequence): CharSequence = texts.getOrPut(key, compute)
    }

    fun fit(context: Context, spec: ResolvedDigitalSpec, targetWidthPx: Int, targetHeightPx: Int, samples: Samples = Samples()): FitSizes {
        val requested = requested(context, spec)
        if (targetWidthPx <= 0 || targetHeightPx <= 0) return requested

        val root = DigitalWidgetComposer.compose(context, spec, requested)
            .apply(context, FrameLayout(context))
        // The time slot holds the time and, with AM/PM on, a second smaller TextClock after it.
        val timeTexts = visibleTextsIn(root, R.id.clocky_time_slot)
        val time = timeTexts.firstOrNull() ?: return requested
        val amPm = if (spec.amPm != null) timeTexts.getOrNull(1) else null
        val date = if (spec.dateVisible) visibleTextIn(root, R.id.clocky_date_slot) else null
        val info = if (spec.info != null) visibleTextIn(root, R.id.clocky_info_slot) else null

        val is24 = DateFormat.is24HourFormat(context)
        fun key(role: String, text: com.stupidsavacan.clocky.design.resolve.ResolvedText, format: String) =
            "$role|${text.face}|${text.sizeSp}|${text.letterSpacingEm}|$format|${spec.dateUppercase}|$is24"
        val timeFormat = if (is24) spec.timeFormats.format24Hour else spec.timeFormats.format12Hour
        time.text = samples.get(key("time",spec.time,timeFormat)) { widestTime(time, timeFormat) }
        date?.let { it.text = samples.get(key("date",spec.date,spec.datePattern)) { widestDate(it, spec.datePattern) } }
        amPm?.let { view ->
            val format = if (is24) spec.amPm!!.format24Hour else spec.amPm!!.format12Hour
            view.text = if (format.isEmpty()) "" else samples.get(key("ampm",spec.amPm!!.text,format)) { widestAmPm(view, format) }
        }
        info?.let { view ->
            val resolved = spec.info!!
            val format = if (is24) resolved.format24Hour else resolved.format12Hour
            view.text = samples.get(key("info",resolved.text,format)) { if (resolved.timeZoneId == null) DateFormat.format(format, Calendar.getInstance()) else widestTime(view, format) }
        }

        fun scaled(px: Float, scale: Int) = max(1, (px * scale / MAX_SCALE).roundToInt()).toFloat()

        fun sizesAt(scale: Int) = FitSizes(
            timePx = scaled(requested.timePx, scale),
            datePx = scaled(requested.datePx, scale),
            infoPx = if (spec.info != null) scaled(requested.infoPx, scale) else 0f,
        )

        fun fits(scale: Int): Boolean {
            val s = sizesAt(scale)
            time.setTextSize(TypedValue.COMPLEX_UNIT_PX, s.timePx)
            amPm?.setTextSize(TypedValue.COMPLEX_UNIT_PX, max(1f, s.timePx * spec.amPm!!.scale))
            date?.setTextSize(TypedValue.COMPLEX_UNIT_PX, s.datePx)
            info?.setTextSize(TypedValue.COMPLEX_UNIT_PX, s.infoPx)
            val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            root.measure(unspecified, unspecified)
            // Text shaping/rounding at the final smaller face can differ by a pixel from the
            // requested-size widest-string probe. Keep a two-pixel gutter at the host edge.
            return root.measuredWidth <= (targetWidthPx - 2).coerceAtLeast(1) &&
                root.measuredHeight <= (targetHeightPx - 2).coerceAtLeast(1)
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

    /** Every visible text face directly inside a slot, in order (time, then AM/PM). */
    internal fun visibleTextsIn(root: View, slotId: Int): List<TextView> {
        val slot = root.findViewById<ViewGroup>(slotId) ?: return emptyList()
        return (0 until slot.childCount).mapNotNull { findVisibleText(slot.getChildAt(it)) }
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
        var widestMinute = 0
        best = -1f
        for (minute in 0..59) {
            cal.set(Calendar.MINUTE, minute)
            val candidate = DateFormat.format(format, cal)
            val w = width(view, candidate)
            if (w > best) { best = w; widest = candidate; widestMinute = minute }
        }
        if (format.contains("ss")) {
            cal.set(Calendar.MINUTE, widestMinute)
            best = -1f
            for (second in 0..59) {
                cal.set(Calendar.SECOND, second)
                val candidate = DateFormat.format(format, cal)
                val w = width(view, candidate)
                if (w > best) { best = w; widest = candidate }
            }
        }
        return widest
    }

    /** AM or PM, whichever renders wider in [format]. */
    internal fun widestAmPm(view: TextView, format: String): CharSequence {
        val cal = Calendar.getInstance()
        return listOf(0, 12).map { hour ->
            cal.set(Calendar.HOUR_OF_DAY, hour)
            DateFormat.format(format, cal)
        }.maxBy { width(view, it) }
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
