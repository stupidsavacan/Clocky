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
        val onClick = if (Utils.isWidgetClickable(wm, appWidgetId)) openClockyIntent(app) else null
        wm.updateAppWidget(appWidgetId, build(app, instance, sizeContextOf(options), onClick))
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
    ): RemoteViews {
        val spec = DesignResolver.resolve(instance.design, size, environment(context))
        val portrait = compose(context, spec, size.minWidthDp, size.maxHeightDp, onClick)
        val landscape = compose(context, spec, size.maxWidthDp, size.minHeightDp, onClick)
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
        val spec = DesignResolver.resolve(design, size, environment(app))
        return spec to compose(app, spec, size.minWidthDp, size.maxHeightDp, onClick = null)
    }

    private fun compose(
        context: Context,
        spec: ResolvedDigitalSpec,
        widthDp: Int,
        heightDp: Int,
        onClick: PendingIntent?,
    ): RemoteViews {
        val density = context.resources.displayMetrics.density
        val sizes = DigitalWidgetFit.fit(context, spec, px(widthDp, density), px(heightDp, density))
        return DigitalWidgetComposer.compose(context, spec, sizes, onClick)
    }

    fun sizeContextOf(options: Bundle): SizeContext = SizeContext(
        minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
        minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
        maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
        maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
    )

    fun environment(context: Context): RenderEnvironment {
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
            localeAutoDatePattern = DateFormat.getBestDateTimePattern(
                locale,
                context.getString(R.string.abbrev_wday_month_day_no_year),
            ),
        )
    }

    private fun openClockyIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, DeskClock::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun px(dp: Int, density: Float): Int = (dp * density).toInt()
}
