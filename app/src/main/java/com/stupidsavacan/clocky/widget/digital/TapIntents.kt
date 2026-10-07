package com.stupidsavacan.clocky.widget.digital

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.android.alarmclock.DigitalAppWidgetProvider
import com.android.deskclock.DeskClock
import com.stupidsavacan.clocky.design.model.TapAction
import com.stupidsavacan.clocky.design.model.TapActions
import com.stupidsavacan.clocky.widget.DigitalWidgetConfigActivity

/**
 * Click targets for the widget. [root] is the whole widget (the pre-Phase-2 behavior, Open Clocky);
 * a zone only gets its own intent when its action differs from that, so a design that never touches
 * Behavior > Tap keeps exactly one click target.
 */
data class ZoneClicks(
    val root: PendingIntent?,
    val time: PendingIntent? = null,
    val date: PendingIntent? = null,
    val info: PendingIntent? = null,
)

object TapIntents {
    const val ACTION_NOOP = "com.stupidsavacan.clocky.action.WIDGET_NOOP"

    fun create(context: Context, appWidgetId: Int, taps: TapActions): ZoneClicks = ZoneClicks(
        root = pending(context, appWidgetId, "root", TapAction.OPEN_CLOCKY),
        time = taps.time.takeIf { it != TapAction.OPEN_CLOCKY }?.let { pending(context, appWidgetId, "time", it) },
        date = taps.date.takeIf { it != TapAction.OPEN_CLOCKY }?.let { pending(context, appWidgetId, "date", it) },
        info = taps.info.takeIf { it != TapAction.OPEN_CLOCKY }?.let { pending(context, appWidgetId, "info", it) },
    )

    fun intentFor(context: Context, appWidgetId: Int, action: TapAction): Intent = when (action) {
        TapAction.OPEN_CLOCKY -> Intent(context, DeskClock::class.java)
        TapAction.OPEN_ALARMS -> tab(context, "ALARMS")
        TapAction.OPEN_TIMER -> tab(context, "TIMERS")
        TapAction.OPEN_STOPWATCH -> tab(context, "STOPWATCH")
        TapAction.OPEN_CALENDAR ->
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        TapAction.EDIT_WIDGET -> Intent(context, DigitalWidgetConfigActivity::class.java)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        TapAction.NONE -> Intent(ACTION_NOOP).setComponent(ComponentName(context, DigitalAppWidgetProvider::class.java))
    }

    private fun tab(context: Context, name: String) =
        Intent(context, DeskClock::class.java).putExtra(DeskClock.EXTRA_SELECT_TAB, name)

    private fun pending(context: Context, appWidgetId: Int, zone: String, action: TapAction): PendingIntent {
        val intent = intentFor(context, appWidgetId, action)
        // PendingIntents match on the Intent only, so the request code keeps each widget's zones apart.
        val requestCode = appWidgetId * 31 + zone.hashCode()
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (action == TapAction.NONE) {
            PendingIntent.getBroadcast(context, requestCode, intent, flags)
        } else {
            PendingIntent.getActivity(context, requestCode, intent, flags)
        }
    }
}
