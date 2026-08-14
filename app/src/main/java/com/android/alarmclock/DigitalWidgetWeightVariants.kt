package com.android.alarmclock

import android.view.View
import android.widget.RemoteViews
import android.widget.TextClock
import com.android.deskclock.R
import com.stupidsavacan.clocky.customization.font.CANONICAL_WEIGHTS
import com.stupidsavacan.clocky.customization.font.FontCapabilities
import com.stupidsavacan.clocky.customization.font.FontWeightResolver

/**
 * Selects predeclared TextClock faces because RemoteViews cannot remotely replace a Typeface or
 * TextAppearance. The user-requested value remains preserved in WidgetSettings; this layer only
 * resolves the effective home-screen face.
 */
internal data class DigitalWidgetWeightSelection(
    val requestedTimeWeight: Int,
    val requestedDateWeight: Int,
    val effectiveTimeWeight: Int,
    val effectiveDateWeight: Int,
    val timeViewId: Int,
    val dateViewId: Int,
)

internal object DigitalWidgetWeightVariants {
    private val timeIds = linkedMapOf(
        100 to R.id.clock_w100,
        200 to R.id.clock_w200,
        300 to R.id.clock_w300,
        400 to R.id.clock,
        500 to R.id.clock_w500,
        600 to R.id.clock_w600,
        700 to R.id.clock_w700,
        800 to R.id.clock_w800,
        900 to R.id.clock_w900,
    )
    private val dateIds = linkedMapOf(
        100 to R.id.date_w100,
        200 to R.id.date_w200,
        300 to R.id.date_w300,
        400 to R.id.date,
        500 to R.id.date_w500,
        600 to R.id.date_w600,
        700 to R.id.date_w700,
        800 to R.id.date_w800,
        900 to R.id.date_w900,
    )
    private val capabilities = FontCapabilities(staticWeights = CANONICAL_WEIGHTS)

    fun select(timeWeight: Int, dateWeight: Int): DigitalWidgetWeightSelection {
        val time = FontWeightResolver.resolve(timeWeight, capabilities)
        val date = FontWeightResolver.resolve(dateWeight, capabilities)
        return DigitalWidgetWeightSelection(
            requestedTimeWeight = time.requested,
            requestedDateWeight = date.requested,
            effectiveTimeWeight = time.effective,
            effectiveDateWeight = date.effective,
            timeViewId = timeIds.getValue(time.effective),
            dateViewId = dateIds.getValue(date.effective),
        )
    }

    fun apply(remoteViews: RemoteViews, selection: DigitalWidgetWeightSelection, dateEnabled: Boolean) {
        timeIds.values.forEach { id ->
            remoteViews.setViewVisibility(id, if (id == selection.timeViewId) View.VISIBLE else View.GONE)
        }
        dateIds.values.forEach { id ->
            val visible = dateEnabled && id == selection.dateViewId
            remoteViews.setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)
        }
    }

    fun apply(root: View, selection: DigitalWidgetWeightSelection, dateEnabled: Boolean) {
        timeIds.values.forEach { id ->
            root.findViewById<View>(id).visibility = if (id == selection.timeViewId) View.VISIBLE else View.GONE
        }
        dateIds.values.forEach { id ->
            val visible = dateEnabled && id == selection.dateViewId
            root.findViewById<View>(id).visibility = if (visible) View.VISIBLE else View.GONE
        }
    }
}
