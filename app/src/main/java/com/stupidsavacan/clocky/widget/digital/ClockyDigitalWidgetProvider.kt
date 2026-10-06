package com.stupidsavacan.clocky.widget.digital

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
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
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> DigitalWidgetUpdater.updateAll(context)
        }
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
        appWidgetIds.forEach { store.delete(it) }
    }
}
