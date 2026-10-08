package com.stupidsavacan.clocky.widget.digital

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.android.alarmclock.DigitalAppWidgetProvider
import com.android.deskclock.DeskClock
import com.android.deskclock.R
import com.android.deskclock.Utils
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.SizeClassRule
import com.stupidsavacan.clocky.design.model.WidgetInstance
import com.stupidsavacan.clocky.design.resolve.DesignResolver
import com.stupidsavacan.clocky.design.resolve.RenderEnvironment
import com.stupidsavacan.clocky.design.resolve.ResolvedDigitalSpec
import com.stupidsavacan.clocky.design.resolve.SizeContext
import com.stupidsavacan.clocky.design.storage.SharedPreferencesDesignStore
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import android.util.Log
import com.android.deskclock.BuildConfig

/**
 * Single update path for the Clocky Digital widget, shared by the provider and the config
 * Activity: store → resolver → fit → composer → AppWidgetManager.
 */
object DigitalWidgetUpdater {
    private val firstGeneration = java.util.concurrent.atomic.AtomicBoolean(true)
    // No permanent thread/service: the sole worker expires one second after the drain ends.
    private val executor = ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS, LinkedBlockingQueue()) { work ->
        Thread(work, "ClockyWidgetRender")
    }
    @androidx.annotation.VisibleForTesting
    internal var queue = WidgetGenerationQueue(executor) { Log.e("ClockyRender", "Widget generation failed", it) }

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
        onFinished: () -> Unit = {},
        /** Debug harness only: ephemeral design, never persisted. */
        measurementDesign: DigitalDesign? = null,
        onMeasured: ((RenderBatch, Double, Double) -> Unit)? = null,
    ) {
        check(BuildConfig.DEBUG || (measurementDesign == null && onMeasured == null))
        // Callers include the config Activity; measure with the plain application context.
        val app = context.applicationContext
        val snapshot = Bundle(options)
        val received = System.nanoTime()
        queue.submit(appWidgetId, onFinished) { publish ->
            val first = firstGeneration.getAndSet(false)
            val start = System.nanoTime()
            val instance = measurementDesign?.let { WidgetInstance(appWidgetId, it) } ?: SharedPreferencesDesignStore(app).load(appWidgetId)
            val clickable = Utils.isWidgetClickable(wm, appWidgetId)
            val onClick = if (clickable) openClockyIntent(app) else null
            val zones = if (clickable && !instance.design.behavior.tap.isDefault) {
                TapIntents.create(app, appWidgetId, instance.design.behavior.tap)
            } else null
            val batch = generate(app, instance, snapshot, onClick, zones)
            val generated = System.nanoTime()
            val sent = publish { wm.updateAppWidget(appWidgetId, batch.views) }
            val end = System.nanoTime()
            if (BuildConfig.DEBUG) {
                batch.entries.forEach { e -> Log.d("ClockyRender", "id=$appWidgetId class=${e.spec.sizeClass} key=${e.size} resolveMs=${e.resolveNanos / 1e6} fitMs=${e.fitNanos / 1e6} composeMs=${e.composeNanos / 1e6}") }
                Log.d("ClockyRender", "id=$appWidgetId firstUpdate=$first map=${batch.plan.useMap} sent=$sent generationMs=${(generated-start)/1e6} remoteViewsMs=${batch.remoteViewsNanos/1e6} sendMs=${(end-generated)/1e6} endToEndMs=${(end-received)/1e6}")
            }
            if (sent) onMeasured?.invoke(batch, (end-generated)/1e6, (end-received)/1e6)
        }
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
        return generate(context, instance, optionsOf(size), onClick, zones).views
    }

    /** Production generation; no diagnostic apply, bitmap inspection or parceling inside its timing. */
    fun generate(
        context: Context,
        instance: WidgetInstance,
        options: Bundle,
        onClick: PendingIntent? = null,
        zones: ZoneClicks? = null,
        env: RenderEnvironment? = null,
    ): RenderBatch {
        val start = System.nanoTime()
        val renderEnvironment = env ?: environment(context)
        val plan = ResponsiveRenderPlan.from(options, Build.VERSION.SDK_INT)
        val samples = DigitalWidgetFit.Samples()
        val entries = plan.entries.map { size ->
            renderEntry(context, instance.design, size, plan.sizeClass, renderEnvironment, onClick, zones, samples)
        }
        val rvStart = System.nanoTime()
        val views = if (plan.useMap && Build.VERSION.SDK_INT >= 31) {
            RemoteViews(entries.associate { it.size to it.views })
        } else RemoteViews(entries[1].views, entries[0].views)
        return RenderBatch(views, plan, entries, System.nanoTime() - start, System.nanoTime() - rvStart)
    }

    fun renderEntry(
        context: Context,
        design: DigitalDesign,
        size: SizeF,
        sizeClass: SizeClass,
        env: RenderEnvironment,
        onClick: PendingIntent? = null,
        zones: ZoneClicks? = null,
        samples: DigitalWidgetFit.Samples = DigitalWidgetFit.Samples(),
    ): RenderEntry {
        val start = System.nanoTime()
        val spec = DesignResolver.resolve(design, ResponsiveRenderPlan.geometry(size), env, sizeClass)
        val resolved = System.nanoTime()
        val density = context.resources.displayMetrics.density
        val widthPx = (size.width * density).toInt()
        val heightPx = (size.height * density).toInt()
        val fit = DigitalWidgetFit.fit(context, spec, widthPx, heightPx, samples)
        val fitted = System.nanoTime()
        val bounds = if (widthPx > 0 && heightPx > 0) widthPx to heightPx else null
        val views = DigitalWidgetComposer.compose(context, spec, fit, onClick, zones = zones, boundsPx = bounds)
        return RenderEntry(size, spec, fit, views, resolved - start, fitted - resolved, System.nanoTime() - fitted)
    }

    /** The portrait half of [build], for the editor's PreviewHost (no click handling). */
    fun buildPortrait(
        context: Context,
        design: DigitalDesign,
        size: SizeContext,
        sizeClass: SizeClass = SizeClassRule.resolve(size.maxWidthDp.toFloat(), size.minHeightDp.toFloat()),
    ): Pair<ResolvedDigitalSpec, RemoteViews> {
        // The application context keeps fit measurement free of an Activity's AppCompat inflater.
        val app = context.applicationContext
        val entry = renderEntry(app, design, SizeF(size.minWidthDp.toFloat(), size.maxHeightDp.toFloat()),
            sizeClass, environment(app, forEditor = true))
        return entry.spec to entry.views
    }

    /** The size class the placed widget has now; Card when the host has not reported a size yet. */
    fun hostSizeClass(context: Context, appWidgetId: Int): SizeClass {
        val wm = AppWidgetManager.getInstance(context) ?: return SizeClass.CARD
        val size = sizeContextOf(wm.getAppWidgetOptions(appWidgetId))
        return SizeClassRule.resolve(size.maxWidthDp.toFloat(), size.minHeightDp.toFloat())
    }

    fun optionsOf(size: SizeContext) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, size.minWidthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, size.maxWidthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, size.minHeightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, size.maxHeightDp)
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
        // One probe for provider and Preview: both see the placed widget's host, never the Preview's own Context.
        val host = HostFontCapability.probe(context)
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
            supportsBundledFonts = host.bundledFonts,
            supportsLatinAmPmMarker = host.latinAmPmMarker,
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

}
