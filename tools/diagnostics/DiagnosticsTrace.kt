package com.stupidsavacan.clocky.diagnostics

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

object DiagnosticsTrace {
    private const val TAG = "ClockyDiagnostics"
    private val installed = AtomicBoolean(false)
    private val lock = Any()
    private lateinit var appContext: Context
    private var previousCrashHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        appContext = context.applicationContext
        diagnosticsDir().mkdirs()
        event(
            "diagnostics.install",
            mapOf(
                "pid" to Process.myPid(),
                "sdk" to Build.VERSION.SDK_INT,
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
            ),
        )
        installCrashHandler()
        registerActivityLifecycleCallbacks()
        registerComponentCallbacks()
        captureEnvironment()
    }

    fun event(name: String, fields: Map<String, Any?> = emptyMap()) {
        if (!::appContext.isInitialized) return
        val line = buildString {
            append(timestamp())
            append(" | EVENT | ")
            append(sanitize(name))
            fields.entries.sortedBy { it.key }.forEach { (key, value) ->
                append(" | ")
                append(sanitize(key))
                append('=')
                append(sanitize(value?.toString() ?: "<null>"))
            }
        }
        appendTraceLine(line)
    }

    fun crash(thread: Thread, throwable: Throwable) {
        if (!::appContext.isInitialized) return
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val text = buildString {
            append(timestamp())
            append(" | CRASH | thread=")
            append(sanitize(thread.name))
            append(" | type=")
            append(throwable.javaClass.name)
            append(" | message=")
            append(sanitize(throwable.message ?: "<null>"))
            append('\n')
            append(sw.toString())
            append('\n')
        }
        try {
            synchronized(lock) {
                FileOutputStream(crashFile(), true).bufferedWriter().use { it.append(text) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to persist crash trace", t)
        }
        appendTraceLine("${timestamp()} | EVENT | diagnostics.crash.persisted")
    }

    fun widgetOptions(options: Bundle?): String {
        if (options == null) return "<null>"
        return listOf(
            "minW" to options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, -1),
            "minH" to options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, -1),
            "maxW" to options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, -1),
            "maxH" to options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, -1),
            "hostCategory" to options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1),
        ).joinToString(",") { (key, value) -> "$key=$value" }
    }

    fun snapshot(): String {
        if (!::appContext.isInitialized) return "Clocky diagnostics not initialized.\n"
        return buildString {
            append("Clocky runtime diagnostics snapshot\n")
            append("exported_at=")
            append(timestamp())
            append("\n\n=== TRACE ===\n")
            append(readSafe(traceFile()))
            append("\n=== CRASHES ===\n")
            append(readSafe(crashFile()))
        }
    }

    fun clear() {
        if (!::appContext.isInitialized) return
        synchronized(lock) {
            traceFile().delete()
            crashFile().delete()
        }
        event("diagnostics.cleared")
    }

    private fun installCrashHandler() {
        previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                crash(thread, throwable)
            } catch (_: Throwable) {
            }
            val previous = previousCrashHandler
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    private fun registerActivityLifecycleCallbacks() {
        val application = appContext as? Application ?: return
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                event("activity.created", activityFields(activity) + ("restored" to (savedInstanceState != null)))
            }

            override fun onActivityStarted(activity: Activity) {
                event("activity.started", activityFields(activity))
            }

            override fun onActivityResumed(activity: Activity) {
                event("activity.resumed", activityFields(activity))
            }

            override fun onActivityPaused(activity: Activity) {
                event("activity.paused", activityFields(activity))
            }

            override fun onActivityStopped(activity: Activity) {
                event("activity.stopped", activityFields(activity))
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                event("activity.saveInstanceState", activityFields(activity))
            }

            override fun onActivityDestroyed(activity: Activity) {
                event("activity.destroyed", activityFields(activity))
            }
        })
    }

    private fun registerComponentCallbacks() {
        appContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {
                event(
                    "configuration.changed",
                    mapOf(
                        "orientation" to newConfig.orientation,
                        "densityDpi" to newConfig.densityDpi,
                        "fontScale" to newConfig.fontScale,
                        "uiMode" to newConfig.uiMode,
                    ),
                )
            }

            override fun onLowMemory() {
                event("memory.low")
            }

            override fun onTrimMemory(level: Int) {
                event("memory.trim", mapOf("level" to level))
            }
        })
    }

    @Suppress("DEPRECATION")
    private fun captureEnvironment() {
        try {
            val pm = appContext.packageManager
            val flags = PackageManager.GET_ACTIVITIES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_SERVICES or
                PackageManager.GET_PROVIDERS or
                PackageManager.GET_PERMISSIONS
            val packageInfo = pm.getPackageInfo(appContext.packageName, flags)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
            val appInfo = packageInfo.applicationInfo
            val iconResource = try {
                if (appInfo != null && appInfo.icon != 0) {
                    appContext.resources.getResourceName(appInfo.icon)
                } else {
                    "<none>"
                }
            } catch (_: Throwable) {
                appInfo?.icon?.toString() ?: "<none>"
            }
            val metrics = appContext.resources.displayMetrics
            val config = appContext.resources.configuration
            event(
                "environment.app",
                mapOf(
                    "package" to appContext.packageName,
                    "versionName" to packageInfo.versionName,
                    "versionCode" to versionCode,
                    "iconResource" to iconResource,
                    "sdk" to Build.VERSION.SDK_INT,
                    "release" to Build.VERSION.RELEASE,
                    "manufacturer" to Build.MANUFACTURER,
                    "brand" to Build.BRAND,
                    "model" to Build.MODEL,
                    "device" to Build.DEVICE,
                    "density" to metrics.density,
                    "scaledDensity" to metrics.scaledDensity,
                    "densityDpi" to metrics.densityDpi,
                    "widthPx" to metrics.widthPixels,
                    "heightPx" to metrics.heightPixels,
                    "orientation" to config.orientation,
                    "fontScale" to config.fontScale,
                    "locale" to localeTags(config),
                ),
            )
            logComponents("activities", packageInfo.activities)
            logComponents("receivers", packageInfo.receivers)
            logComponents("services", packageInfo.services)
            logComponents("providers", packageInfo.providers)
            event(
                "environment.permissions",
                mapOf("requested" to (packageInfo.requestedPermissions?.joinToString(",") ?: "")),
            )
            val wm = AppWidgetManager.getInstance(appContext)
            wm.installedProviders
                .filter { it.provider.packageName == appContext.packageName }
                .sortedBy { it.provider.className }
                .forEach { info ->
                    event(
                        "environment.appWidgetProvider",
                        mapOf(
                            "provider" to info.provider.flattenToShortString(),
                            "minWidth" to info.minWidth,
                            "minHeight" to info.minHeight,
                            "minResizeWidth" to info.minResizeWidth,
                            "minResizeHeight" to info.minResizeHeight,
                            "resizeMode" to info.resizeMode,
                            "widgetCategory" to info.widgetCategory,
                            "initialLayout" to resourceName(info.initialLayout),
                            "previewImage" to resourceName(info.previewImage),
                            "configure" to info.configure?.flattenToShortString(),
                        ),
                    )
                }
        } catch (t: Throwable) {
            event(
                "environment.capture.failed",
                mapOf("type" to t.javaClass.name, "message" to t.message),
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun localeTags(config: Configuration): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales.toLanguageTags()
        } else {
            config.locale?.toLanguageTag() ?: ""
        }
    }

    private fun logComponents(kind: String, components: Array<out ComponentInfo>?) {
        event(
            "environment.components.$kind",
            mapOf(
                "count" to (components?.size ?: 0),
                "names" to (components?.map { it.name }?.sorted()?.joinToString(",") ?: ""),
            ),
        )
    }

    private fun resourceName(id: Int): String {
        if (id == 0) return "<none>"
        return try {
            appContext.resources.getResourceName(id)
        } catch (_: Throwable) {
            id.toString()
        }
    }

    private fun activityFields(activity: Activity): Map<String, Any?> = mapOf(
        "activity" to activity.javaClass.name,
        "action" to activity.intent?.action,
        "changingConfigurations" to activity.isChangingConfigurations,
        "finishing" to activity.isFinishing,
    )

    private fun appendTraceLine(line: String) {
        try {
            synchronized(lock) {
                FileOutputStream(traceFile(), true).bufferedWriter().use {
                    it.append(line)
                    it.append('\n')
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to persist diagnostic event", t)
        }
    }

    private fun readSafe(file: File): String = try {
        if (file.exists()) file.readText() else "<none>\n"
    } catch (t: Throwable) {
        "<failed to read ${file.name}: ${t.javaClass.name}: ${t.message}>\n"
    }

    private fun diagnosticsDir(): File = File(appContext.filesDir, "clocky-runtime-diagnostics")
    private fun traceFile(): File = File(diagnosticsDir(), "trace.log")
    private fun crashFile(): File = File(diagnosticsDir(), "crash.log")

    private fun timestamp(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        format.timeZone = TimeZone.getDefault()
        val raw = format.format(Date())
        return if (raw.length >= 5) {
            raw.dropLast(2) + ":" + raw.takeLast(2)
        } else {
            raw
        }
    }

    private fun sanitize(value: String): String = value
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("|", "¦")
}
