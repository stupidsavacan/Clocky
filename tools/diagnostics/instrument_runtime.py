from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}: found {count}\n--- anchor ---\n{old}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def copy_source(name: str) -> None:
    source = ROOT / "tools" / "diagnostics" / name
    target = ROOT / "app" / "src" / "main" / "java" / "com" / "stupidsavacan" / "clocky" / "diagnostics" / name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)


copy_source("DiagnosticsTrace.kt")
copy_source("DiagnosticsExportActivity.kt")

# Make the debug package obviously temporary and installable beside the normal build.
gradle = ROOT / "app" / "build.gradle.kts"
replace_once(
    gradle,
    """    buildTypes {\n        release {\n            isMinifyEnabled = false\n        }\n    }\n""",
    """    buildTypes {\n        debug {\n            applicationIdSuffix = \".diagnostics\"\n            versionNameSuffix = \"-diagnostics\"\n        }\n        release {\n            isMinifyEnabled = false\n        }\n    }\n""",
)

# Install diagnostics before AOSP singleton initialization so startup failures are retained.
application = ROOT / "app" / "src" / "main" / "java" / "com" / "android" / "deskclock" / "DeskClockApplication.kt"
replace_once(
    application,
    "import com.android.deskclock.uidata.UiDataModel\n",
    "import com.android.deskclock.uidata.UiDataModel\nimport com.stupidsavacan.clocky.diagnostics.DiagnosticsTrace\n",
)
replace_once(
    application,
    """        super.onCreate()\n\n        val applicationContext = applicationContext\n""",
    """        super.onCreate()\n\n        DiagnosticsTrace.install(this)\n        DiagnosticsTrace.event(\"application.onCreate.enter\")\n\n        val applicationContext = applicationContext\n""",
)
replace_once(
    application,
    """        DataModel.dataModel.init(applicationContext, prefs)\n        UiDataModel.uiDataModel.init(applicationContext, prefs)\n        Controller.getController().setContext(applicationContext)\n        Controller.getController().addEventTracker(LogEventTracker(applicationContext))\n""",
    """        DiagnosticsTrace.event(\"application.dataModel.init.begin\")\n        DataModel.dataModel.init(applicationContext, prefs)\n        DiagnosticsTrace.event(\"application.dataModel.init.end\")\n        DiagnosticsTrace.event(\"application.uiDataModel.init.begin\")\n        UiDataModel.uiDataModel.init(applicationContext, prefs)\n        DiagnosticsTrace.event(\"application.uiDataModel.init.end\")\n        Controller.getController().setContext(applicationContext)\n        Controller.getController().addEventTracker(LogEventTracker(applicationContext))\n        DiagnosticsTrace.event(\"application.onCreate.ready\")\n""",
)

# Add a separate temporary launcher entry that only saves the local trace using SAF.
manifest = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
replace_once(
    manifest,
    """        <activity android:name=\".HandleShortcuts\" android:excludeFromRecents=\"true\" android:launchMode=\"singleInstance\" android:taskAffinity=\"\" android:theme=\"@android:style/Theme.NoDisplay\" />\n""",
    """        <activity\n            android:name=\"com.stupidsavacan.clocky.diagnostics.DiagnosticsExportActivity\"\n            android:exported=\"true\"\n            android:label=\"@string/clocky_diagnostics_temp_label\">\n            <intent-filter>\n                <action android:name=\"android.intent.action.MAIN\" />\n                <category android:name=\"android.intent.category.LAUNCHER\" />\n            </intent-filter>\n        </activity>\n        <activity android:name=\".HandleShortcuts\" android:excludeFromRecents=\"true\" android:launchMode=\"singleInstance\" android:taskAffinity=\"\" android:theme=\"@android:style/Theme.NoDisplay\" />\n""",
)
strings = ROOT / "app" / "src" / "main" / "res" / "values" / "strings.xml"
replace_once(
    strings,
    "</resources>",
    "    <string name=\"clocky_diagnostics_temp_label\" translatable=\"false\">Clocky Diagnostics TEMP</string>\n</resources>",
)

# Trace the widget host contract and the AOSP relayout path around the observed crash point.
provider = ROOT / "app" / "src" / "main" / "java" / "com" / "android" / "alarmclock" / "DigitalAppWidgetProvider.kt"
replace_once(
    provider,
    "import com.stupidsavacan.clocky.customization.storage.SharedPreferencesWidgetSettingsStore\n",
    "import com.stupidsavacan.clocky.customization.storage.SharedPreferencesWidgetSettingsStore\nimport com.stupidsavacan.clocky.diagnostics.DiagnosticsTrace\n",
)
replace_once(
    provider,
    """    override fun onReceive(context: Context, intent: Intent) {\n        LOGGER.i(\"onReceive: $intent\")\n        super.onReceive(context, intent)\n""",
    """    override fun onReceive(context: Context, intent: Intent) {\n        DiagnosticsTrace.event(\"widget.onReceive.enter\", mapOf(\"action\" to intent.action))\n        LOGGER.i(\"onReceive: $intent\")\n        super.onReceive(context, intent)\n""",
)
replace_once(
    provider,
    """        val widgetIds: IntArray = wm.getAppWidgetIds(provider)\n\n        val action: String? = intent.action\n""",
    """        val widgetIds: IntArray = wm.getAppWidgetIds(provider)\n        DiagnosticsTrace.event(\"widget.onReceive.ids\", mapOf(\"count\" to widgetIds.size, \"ids\" to widgetIds.joinToString(\",\")))\n\n        val action: String? = intent.action\n""",
)
replace_once(
    provider,
    """    override fun onUpdate(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {\n        super.onUpdate(context, wm, widgetIds)\n\n        widgetIds.forEach { widgetId ->\n""",
    """    override fun onUpdate(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {\n        DiagnosticsTrace.event(\"widget.onUpdate\", mapOf(\"count\" to widgetIds.size, \"ids\" to widgetIds.joinToString(\",\")))\n        super.onUpdate(context, wm, widgetIds)\n\n        widgetIds.forEach { widgetId ->\n""",
)
replace_once(
    provider,
    """    override fun onDeleted(context: Context, appWidgetIds: IntArray) {\n        super.onDeleted(context, appWidgetIds)\n\n        val settingsStore = SharedPreferencesWidgetSettingsStore(context)\n""",
    """    override fun onDeleted(context: Context, appWidgetIds: IntArray) {\n        DiagnosticsTrace.event(\"widget.onDeleted\", mapOf(\"ids\" to appWidgetIds.joinToString(\",\")))\n        super.onDeleted(context, appWidgetIds)\n\n        val settingsStore = SharedPreferencesWidgetSettingsStore(context)\n""",
)
replace_once(
    provider,
    """        super.onAppWidgetOptionsChanged(context, wm, widgetId, options)\n\n        // Scale the fonts of the clock to fit inside the new size\n""",
    """        super.onAppWidgetOptionsChanged(context, wm, widgetId, options)\n        DiagnosticsTrace.event(\n            \"widget.optionsChanged\",\n            mapOf(\"widgetId\" to widgetId, \"options\" to DiagnosticsTrace.widgetOptions(options)),\n        )\n\n        // Scale the fonts of the clock to fit inside the new size\n""",
)
replace_once(
    provider,
    """        private fun relayoutWidget(\n            context: Context,\n            wm: AppWidgetManager,\n            widgetId: Int,\n            options: Bundle\n        ) {\n            val portrait: RemoteViews = relayoutWidget(context, wm, widgetId, options, true)\n            val landscape: RemoteViews = relayoutWidget(context, wm, widgetId, options, false)\n            val widget = RemoteViews(landscape, portrait)\n            wm.updateAppWidget(widgetId, widget)\n            wm.notifyAppWidgetViewDataChanged(widgetId, R.id.world_city_list)\n        }\n""",
    """        private fun relayoutWidget(\n            context: Context,\n            wm: AppWidgetManager,\n            widgetId: Int,\n            options: Bundle\n        ) {\n            DiagnosticsTrace.event(\"widget.relayout.begin\", mapOf(\"widgetId\" to widgetId, \"options\" to DiagnosticsTrace.widgetOptions(options)))\n            val portrait: RemoteViews = relayoutWidget(context, wm, widgetId, options, true)\n            DiagnosticsTrace.event(\"widget.relayout.portrait.ready\", mapOf(\"widgetId\" to widgetId))\n            val landscape: RemoteViews = relayoutWidget(context, wm, widgetId, options, false)\n            DiagnosticsTrace.event(\"widget.relayout.landscape.ready\", mapOf(\"widgetId\" to widgetId))\n            val widget = RemoteViews(landscape, portrait)\n            DiagnosticsTrace.event(\"widget.relayout.updateAppWidget.begin\", mapOf(\"widgetId\" to widgetId))\n            wm.updateAppWidget(widgetId, widget)\n            DiagnosticsTrace.event(\"widget.relayout.updateAppWidget.end\", mapOf(\"widgetId\" to widgetId))\n            wm.notifyAppWidgetViewDataChanged(widgetId, R.id.world_city_list)\n            DiagnosticsTrace.event(\"widget.relayout.end\", mapOf(\"widgetId\" to widgetId))\n        }\n""",
)
replace_once(
    provider,
    """        ): RemoteViews {\n            // Create a remote view for the digital clock.\n            val packageName: String = context.getPackageName()\n""",
    """        ): RemoteViews {\n            DiagnosticsTrace.event(\n                \"widget.relayout.orientation.begin\",\n                mapOf(\"widgetId\" to widgetId, \"portrait\" to portrait, \"options\" to DiagnosticsTrace.widgetOptions(options)),\n            )\n            // Create a remote view for the digital clock.\n            val packageName: String = context.getPackageName()\n""",
)
replace_once(
    provider,
    """            val rv = RemoteViews(packageName, R.layout.digital_widget)\n            val widgetSettings = SharedPreferencesWidgetSettingsStore(context).load(widgetId)\n\n            // Tapping on the widget opens the app (if not on the lock screen).\n""",
    """            val rv = RemoteViews(packageName, R.layout.digital_widget)\n            val widgetSettings = SharedPreferencesWidgetSettingsStore(context).load(widgetId)\n            DiagnosticsTrace.event(\n                \"widget.settings.loaded\",\n                mapOf(\n                    \"widgetId\" to widgetId,\n                    \"timeWeight\" to widgetSettings.time.requestedWeight,\n                    \"dateWeight\" to widgetSettings.date.requestedWeight,\n                    \"timeSizeSp\" to widgetSettings.time.sizeSp,\n                    \"dateSizeSp\" to widgetSettings.date.sizeSp,\n                    \"dateEnabled\" to widgetSettings.date.enabled,\n                    \"fourByOne\" to (widgetSettings.fourByOne != null),\n                    \"fourByTwo\" to (widgetSettings.fourByTwo != null),\n                ),\n            )\n\n            // Tapping on the widget opens the app (if not on the lock screen).\n""",
)
replace_once(
    provider,
    """            val resolvedOffsets = DigitalWidgetProfileResolver.resolveOffsets(\n                    widgetSettings,\n                    options.getInt(OPTION_APPWIDGET_MIN_HEIGHT),\n            )\n\n            // Fetch the widget size selected by the user.\n""",
    """            val resolvedOffsets = DigitalWidgetProfileResolver.resolveOffsets(\n                    widgetSettings,\n                    options.getInt(OPTION_APPWIDGET_MIN_HEIGHT),\n            )\n            DiagnosticsTrace.event(\n                \"widget.profile.resolved\",\n                mapOf(\n                    \"widgetId\" to widgetId,\n                    \"hostMinHeightDp\" to options.getInt(OPTION_APPWIDGET_MIN_HEIGHT),\n                    \"timeWeight\" to resolvedWeights.timeWeight,\n                    \"dateWeight\" to resolvedWeights.dateWeight,\n                    \"dateEnabled\" to resolvedWeights.dateEnabled,\n                    \"timeSizeSp\" to resolvedSizes.timeSizeSp,\n                    \"dateSizeSp\" to resolvedSizes.dateSizeSp,\n                ),\n            )\n\n            // Fetch the widget size selected by the user.\n""",
)
replace_once(
    provider,
    """            val targetWidthPx = if (portrait) minWidthPx else maxWidthPx\n            val targetHeightPx = if (portrait) maxHeightPx else minHeightPx\n            val scaledDensity: Float = resources.getDisplayMetrics().scaledDensity\n""",
    """            val targetWidthPx = if (portrait) minWidthPx else maxWidthPx\n            val targetHeightPx = if (portrait) maxHeightPx else minHeightPx\n            DiagnosticsTrace.event(\n                \"widget.host.geometry\",\n                mapOf(\n                    \"widgetId\" to widgetId,\n                    \"portrait\" to portrait,\n                    \"minWidthPx\" to minWidthPx,\n                    \"minHeightPx\" to minHeightPx,\n                    \"maxWidthPx\" to maxWidthPx,\n                    \"maxHeightPx\" to maxHeightPx,\n                    \"targetWidthPx\" to targetWidthPx,\n                    \"targetHeightPx\" to targetHeightPx,\n                ),\n            )\n            val scaledDensity: Float = resources.getDisplayMetrics().scaledDensity\n""",
)
replace_once(
    provider,
    """            val sizes = optimizeSizes(\n                    context,\n""",
    """            DiagnosticsTrace.event(\"widget.optimize.begin\", mapOf(\"widgetId\" to widgetId, \"portrait\" to portrait))\n            val sizes = optimizeSizes(\n                    context,\n""",
)
replace_once(
    provider,
    """            if (LOGGER.isVerboseLoggable) {\n                LOGGER.v(sizes.toString())\n            }\n\n            // Apply the computed sizes to the remote views.\n""",
    """            DiagnosticsTrace.event(\n                \"widget.optimize.end\",\n                mapOf(\n                    \"widgetId\" to widgetId,\n                    \"portrait\" to portrait,\n                    \"clockPx\" to sizes.mClockFontSizePx,\n                    \"datePx\" to sizes.mFontSizePx,\n                    \"measuredWidthPx\" to sizes.mMeasuredWidthPx,\n                    \"measuredHeightPx\" to sizes.mMeasuredHeightPx,\n                    \"listHeightPx\" to sizes.listHeight,\n                ),\n            )\n            if (LOGGER.isVerboseLoggable) {\n                LOGGER.v(sizes.toString())\n            }\n\n            // Apply the computed sizes to the remote views.\n""",
)
replace_once(
    provider,
    """            val smallestWorldCityListSizePx: Int =\n                    resources.getDimensionPixelSize(R.dimen.widget_min_world_city_list_size)\n            if (sizes.listHeight <= smallestWorldCityListSizePx) {\n""",
    """            val smallestWorldCityListSizePx: Int =\n                    resources.getDimensionPixelSize(R.dimen.widget_min_world_city_list_size)\n            DiagnosticsTrace.event(\n                \"widget.worldCity.decision\",\n                mapOf(\"widgetId\" to widgetId, \"listHeightPx\" to sizes.listHeight, \"thresholdPx\" to smallestWorldCityListSizePx),\n            )\n            if (sizes.listHeight <= smallestWorldCityListSizePx) {\n""",
)

# Trace the configuration activity contract and explicit refresh broadcast.
config_activity = ROOT / "app" / "src" / "main" / "java" / "com" / "stupidsavacan" / "clocky" / "widget" / "DigitalWidgetConfigActivity.kt"
replace_once(
    config_activity,
    "import com.stupidsavacan.clocky.customization.ui.WidgetProfileWeightEditor\n",
    "import com.stupidsavacan.clocky.customization.ui.WidgetProfileWeightEditor\nimport com.stupidsavacan.clocky.diagnostics.DiagnosticsTrace\n",
)
replace_once(
    config_activity,
    """    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n\n        // AppWidget hosts require configuration activities to explicitly opt in to success.\n""",
    """    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        DiagnosticsTrace.event(\"widget.config.onCreate\")\n\n        // AppWidget hosts require configuration activities to explicitly opt in to success.\n""",
)
replace_once(
    config_activity,
    """        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {\n            finish()\n            return\n        }\n\n        setContentView(R.layout.clocky_digital_widget_config)\n""",
    """        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {\n            DiagnosticsTrace.event(\"widget.config.invalidId\")\n            finish()\n            return\n        }\n        DiagnosticsTrace.event(\"widget.config.validId\", mapOf(\"widgetId\" to appWidgetId))\n\n        setContentView(R.layout.clocky_digital_widget_config)\n""",
)
replace_once(
    config_activity,
    """        val store = SharedPreferencesWidgetSettingsStore(this)\n        val current = store.load(appWidgetId)\n\n        val timePreview: TextView = findViewById(R.id.clocky_time_preview)\n""",
    """        val store = SharedPreferencesWidgetSettingsStore(this)\n        val current = store.load(appWidgetId)\n        DiagnosticsTrace.event(\n            \"widget.config.settings.loaded\",\n            mapOf(\n                \"widgetId\" to appWidgetId,\n                \"timeWeight\" to current.time.requestedWeight,\n                \"dateWeight\" to current.date.requestedWeight,\n                \"timeSizeSp\" to current.time.sizeSp,\n                \"dateSizeSp\" to current.date.sizeSp,\n                \"dateEnabled\" to current.date.enabled,\n            ),\n        )\n\n        val timePreview: TextView = findViewById(R.id.clocky_time_preview)\n""",
)
replace_once(
    config_activity,
    """        saveButton.setOnClickListener {\n            val withBaseSettings = current.copy(\n""",
    """        saveButton.setOnClickListener {\n            DiagnosticsTrace.event(\"widget.config.save.begin\", mapOf(\"widgetId\" to appWidgetId))\n            val withBaseSettings = current.copy(\n""",
)
replace_once(
    config_activity,
    """            store.save(updated)\n            requestWidgetRefresh(appWidgetId)\n\n            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)\n""",
    """            store.save(updated)\n            DiagnosticsTrace.event(\"widget.config.save.persisted\", mapOf(\"widgetId\" to appWidgetId))\n            requestWidgetRefresh(appWidgetId)\n            DiagnosticsTrace.event(\"widget.config.save.refreshSent\", mapOf(\"widgetId\" to appWidgetId))\n\n            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)\n""",
)
replace_once(
    config_activity,
    """    private fun requestWidgetRefresh(widgetId: Int) {\n        val updateIntent = Intent(this, DigitalAppWidgetProvider::class.java).apply {\n""",
    """    private fun requestWidgetRefresh(widgetId: Int) {\n        DiagnosticsTrace.event(\"widget.config.requestRefresh\", mapOf(\"widgetId\" to widgetId))\n        val updateIntent = Intent(this, DigitalAppWidgetProvider::class.java).apply {\n""",
)

print("Clocky runtime diagnostics instrumentation applied successfully")
