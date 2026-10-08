package com.stupidsavacan.clocky.widget.digital

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import com.stupidsavacan.clocky.design.storage.SharedPreferencesDesignStore

/**
 * Clocky-owned Digital widget provider (Issue #31 §3, End-State §12 decision 3).
 *
 * TextClock keeps time and date ticking inside the host, so the app only re-renders on host size
 * changes, configuration saves, locale changes (Locale Auto date pattern) and TIME_SET, which
 * Settings sends when the 12/24h preference changes. There is no app-driven update loop.
 *
 * Registered under the legacy component name [com.android.alarmclock.DigitalAppWidgetProvider] so
 * placed widgets (and their per-id settings) survive app updates.
 */
abstract class ClockyDigitalWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        val render = when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> true
            else -> false
        }
        if (!render) { super.onReceive(context, intent); return }
        val result = goAsync()
        // One pending result for the broadcast, released for success, stale work, invalid input or exception.
        val remaining = java.util.concurrent.atomic.AtomicInteger(1)
        val done = { if (remaining.decrementAndGet() == 0) result?.finish(); Unit }
        try {
            val app = context.applicationContext
            val wm = AppWidgetManager.getInstance(app)
            val ids = when (intent.action) {
                AppWidgetManager.ACTION_APPWIDGET_UPDATE -> intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: intArrayOf()
                AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> intArrayOf(intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
                else -> wm.getAppWidgetIds(ComponentName(app, com.android.alarmclock.DigitalAppWidgetProvider::class.java))
            }
            ids.filter { it != AppWidgetManager.INVALID_APPWIDGET_ID }.distinct().forEach { id ->
                val options = if (intent.action == AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED) {
                    intent.getBundleExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS) ?: wm.getAppWidgetOptions(id)
                } else wm.getAppWidgetOptions(id)
                remaining.incrementAndGet()
                try { DigitalWidgetUpdater.update(app, wm, id, options, done) }
                catch (e: Exception) { done(); throw e }
            }
        } finally { done() }
    }

    override fun onUpdate(context: Context, wm: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { DigitalWidgetUpdater.update(context, wm, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        wm: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        DigitalWidgetUpdater.update(context, wm, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = SharedPreferencesDesignStore(context)
        appWidgetIds.forEach { DigitalWidgetUpdater.queue.invalidate(it); store.delete(it) }
    }
}
