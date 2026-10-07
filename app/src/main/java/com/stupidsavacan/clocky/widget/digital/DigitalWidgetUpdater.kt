package com.stupidsavacan.clocky.widget.digital

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import com.android.alarmclock.DigitalAppWidgetProvider
import com.android.deskclock.DeskClock
import com.android.deskclock.R
import com.android.deskclock.Utils
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.SizeClassResolver
import com.stupidsavacan.clocky.design.model.WidgetInstance
import com.stupidsavacan.clocky.design.resolve.DesignResolver
import com.stupidsavacan.clocky.design.resolve.RenderEnvironment
import com.stupidsavacan.clocky.design.resolve.ResolvedDigitalSpec
import com.stupidsavacan.clocky.design.resolve.SizeContext
import com.stupidsavacan.clocky.design.storage.SharedPreferencesDesignStore
import java.util.Locale

/**
 * Single update path for the Clocky Digital widget, shared by the provider and the config
 * Activity: store → resolver → fit → composer → AppWidgetManager.
 */
object DigitalWidgetUpdater {
    fun updateAll(context: Context) {
        val wm = AppWidgetManager.getInstance(context) ?: return
        wm.getAppWidgetIds(ComponentName(context, DigitalAppWidgetProvider::class.java))
            .forEach { update(context, wm, it) }
    }

    fun update(
        context: Context,
        wm: AppWidgetManager,
        appWidgetId: Int,
        options: Bundle = wm.getAppWidgetOptions(appWidgetId),
    ) {
        // Callers include the config Activity; measure with the plain application context.
        val app = context.applicationContext
        val instance = SharedPreferencesDesignStore(app).load(appWidgetId)
        val clickable = Utils.isWidgetClickable(wm, appWidgetId)
        val onClick = if (clickable) openClockyIntent(app) else null
        val zones = if (clickable && !instance.design.behavior.tap.isDefault) {
            TapIntents.create(app, appWidgetId, instance.design.behavior.tap)
        } else {
            null
        }
        wm.updateAppWidget(appWidgetId, build(app, instance, sizeContextOf(options), onClick, zones))
    }

    /**
     * Portrait and landscape are fitted separately (portrait: min width × max height; landscape:
     * max width × min height, as the AOSP provider did) and each is built completely before the
     * two are combined.
     */
    fun build(
        context: Context,
        instance: WidgetInstance,
        size: SizeContext,
        onClick: PendingIntent?,
        zones: ZoneClicks? = null,
    ): RemoteViews {
        val spec = DesignResolver.resolve(instance.design, size, environment(context))
        val portrait = compose(context, spec, size.minWidthDp, size.maxHeightDp, onClick, zones)
        val landscape = compose(context, spec, size.maxWidthDp, size.minHeightDp, onClick, zones)
        return RemoteViews(landscape, portrait)
    }

    /** The portrait half of [build], for the editor's PreviewHost (no click handling). */
    fun buildPortrait(
        context: Context,
        design: DigitalDesign,
        size: SizeContext,
    ): Pair<ResolvedDigitalSpec, RemoteViews> {
        // The application context keeps fit measurement free of an Activity's AppCompat inflater.
        val app = context.applicationContext
        // The editor shows a sample next-alarm line when none is set, so the Info row can be styled.
        val spec = DesignResolver.resolve(design, size, environment(app, forEditor = true))
        return spec to compose(app, spec, size.minWidthDp, size.maxHeightDp, onClick = null)
    }

    private fun compose(
        context: Context,
        spec: ResolvedDigitalSpec,
        widthDp: Int,
        heightDp: Int,
        onClick: PendingIntent?,
        zones: ZoneClicks? = null,
    ): RemoteViews {
        val density = context.resources.displayMetrics.density
        val widthPx = px(widthDp, density)
        val heightPx = px(heightDp, density)
        val sizes = DigitalWidgetFit.fit(context, spec, widthPx, heightPx)
        val bounds = if (widthPx > 0 && heightPx > 0) widthPx to heightPx else null
        return DigitalWidgetComposer.compose(context, spec, sizes, onClick, zones = zones, boundsPx = bounds)
    }

    /** The size class the placed widget has now; Card when the host has not reported a size yet. */
    fun hostSizeClass(context: Context, appWidgetId: Int): SizeClass {
        val wm = AppWidgetManager.getInstance(context) ?: return SizeClass.CARD
        val size = sizeContextOf(wm.getAppWidgetOptions(appWidgetId))
        val known = size.minHeightDp > 0 && size.minWidthDp > 0 && size.maxHeightDp > 0
        return if (known) SizeClassResolver.resolve(size.minHeightDp) else SizeClass.CARD
    }

    fun sizeContextOf(options: Bundle): SizeContext = SizeContext(
        minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
        minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
        maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
        maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
    )

    fun environment(context: Context, forEditor: Boolean = false): RenderEnvironment {
        val configuration = context.resources.configuration
        val locale: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            configuration.locale
        }
        return RenderEnvironment(
            sdkInt = Build.VERSION.SDK_INT,
            isRtl = configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL,
            isNight = (configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES,
            localeAutoDatePattern = DateFormat.getBestDateTimePattern(
                locale,
                context.getString(R.string.abbrev_wday_month_day_no_year),
            ),
            nextAlarmText = nextAlarmText(context),
            sampleAlarmText = if (forEditor) sampleAlarmText(context, locale) else null,
        )
    }

    /** The system's next alarm as Clocky formats it, or null when none is set (or it cannot be read). */
    private fun nextAlarmText(context: Context): String? =
        runCatching { Utils.getNextAlarm(context) }.getOrNull()?.takeIf { it.isNotBlank() }

    /** A plausible alarm for the editor preview: tomorrow 7:30, in the same format a real one would use. */
    private fun sampleAlarmText(context: Context, locale: Locale): String {
        val cal = java.util.Calendar.getInstance(locale).apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 7)
            set(java.util.Calendar.MINUTE, 30)
        }
        return com.android.deskclock.AlarmUtils.getFormattedTime(context, cal)
    }

    private fun openClockyIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, DeskClock::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun px(dp: Int, density: Float): Int = (dp * density).toInt()
}
