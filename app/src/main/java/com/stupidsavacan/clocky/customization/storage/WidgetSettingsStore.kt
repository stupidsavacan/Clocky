package com.stupidsavacan.clocky.customization.storage

import android.content.Context
import com.stupidsavacan.clocky.customization.model.BackgroundSettings
import com.stupidsavacan.clocky.customization.model.DateSettings
import com.stupidsavacan.clocky.customization.model.HorizontalAlignment
import com.stupidsavacan.clocky.customization.model.HourMode
import com.stupidsavacan.clocky.customization.model.ProfileOverride
import com.stupidsavacan.clocky.customization.model.TimeSettings
import com.stupidsavacan.clocky.customization.model.WidgetSettings
import com.stupidsavacan.clocky.customization.model.normalized
import org.json.JSONObject

interface WidgetSettingsStore {
    fun load(appWidgetId: Int): WidgetSettings
    fun save(settings: WidgetSettings)
    fun delete(appWidgetId: Int)
}

class SharedPreferencesWidgetSettingsStore(context: Context) : WidgetSettingsStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(appWidgetId: Int): WidgetSettings {
        val raw = prefs.getString(key(appWidgetId), null)
            ?: return WidgetSettings(appWidgetId = appWidgetId)
        return runCatching { decode(appWidgetId, JSONObject(raw)).normalized() }
            .getOrElse { WidgetSettings(appWidgetId = appWidgetId) }
    }

    override fun save(settings: WidgetSettings) {
        val safe = settings.normalized()
        prefs.edit().putString(key(safe.appWidgetId), encode(safe).toString()).apply()
    }

    override fun delete(appWidgetId: Int) {
        prefs.edit().remove(key(appWidgetId)).apply()
    }

    private fun encode(s: WidgetSettings): JSONObject = JSONObject().apply {
        put("schema", SCHEMA_VERSION)
        put("presetId", s.presetId)
        put("time", JSONObject().apply {
            put("fontFamily", s.time.fontFamily)
            put("weight", s.time.requestedWeight)
            put("sizeSp", s.time.sizeSp.toDouble())
            put("letterSpacing", s.time.letterSpacing.toDouble())
            put("argb", s.time.argb.toLong())
            put("opacity", s.time.opacity.toDouble())
            put("xDp", s.time.xDp.toDouble())
            put("yDp", s.time.yDp.toDouble())
            put("alignment", s.time.alignment.name)
            put("hourMode", s.time.hourMode.name)
            put("leadingZero", s.time.leadingZero)
        })
        put("date", JSONObject().apply {
            put("enabled", s.date.enabled)
            put("fontFamily", s.date.fontFamily)
            put("weight", s.date.requestedWeight)
            put("sizeSp", s.date.sizeSp.toDouble())
            put("letterSpacing", s.date.letterSpacing.toDouble())
            put("argb", s.date.argb.toLong())
            put("opacity", s.date.opacity.toDouble())
            put("formatPattern", s.date.formatPattern)
            put("xDp", s.date.xDp.toDouble())
            put("yDp", s.date.yDp.toDouble())
            put("alignment", s.date.alignment.name)
        })
        put("background", JSONObject().apply {
            put("argb", s.background.argb.toLong())
            put("opacity", s.background.opacity.toDouble())
            put("cornerRadiusDp", s.background.cornerRadiusDp.toDouble())
            put("paddingDp", s.background.paddingDp.toDouble())
        })
        put("fourByTwo", encodeOverride(s.fourByTwo))
        put("fourByOne", encodeOverride(s.fourByOne))
    }

    private fun decode(id: Int, root: JSONObject): WidgetSettings {
        val t = root.optJSONObject("time") ?: JSONObject()
        val d = root.optJSONObject("date") ?: JSONObject()
        val b = root.optJSONObject("background") ?: JSONObject()
        return WidgetSettings(
            appWidgetId = id,
            presetId = root.optString("presetId", WidgetSettings.GOOGLE_CLOCK_PRESET_ID),
            time = TimeSettings(
                fontFamily = t.optString("fontFamily", "system-sans"),
                requestedWeight = t.optInt("weight", 400),
                sizeSp = t.optDouble("sizeSp", 64.0).toFloat(),
                letterSpacing = t.optDouble("letterSpacing", 0.0).toFloat(),
                argb = t.optLong("argb", 0xFFFFFFFFL).toInt(),
                opacity = t.optDouble("opacity", 1.0).toFloat(),
                xDp = t.optDouble("xDp", 0.0).toFloat(),
                yDp = t.optDouble("yDp", 0.0).toFloat(),
                alignment = enumValueOrDefault(t.optString("alignment"), HorizontalAlignment.CENTER),
                hourMode = enumValueOrDefault(t.optString("hourMode"), HourMode.FOLLOW_SYSTEM),
                leadingZero = t.optBoolean("leadingZero", false),
            ),
            date = DateSettings(
                enabled = d.optBoolean("enabled", true),
                fontFamily = d.optString("fontFamily", "system-sans"),
                requestedWeight = d.optInt("weight", 400),
                sizeSp = d.optDouble("sizeSp", 14.0).toFloat(),
                letterSpacing = d.optDouble("letterSpacing", 0.0).toFloat(),
                argb = d.optLong("argb", 0xFFFFFFFFL).toInt(),
                opacity = d.optDouble("opacity", 1.0).toFloat(),
                formatPattern = d.optString("formatPattern", "EEE, MMM d"),
                xDp = d.optDouble("xDp", 0.0).toFloat(),
                yDp = d.optDouble("yDp", 0.0).toFloat(),
                alignment = enumValueOrDefault(d.optString("alignment"), HorizontalAlignment.CENTER),
            ),
            background = BackgroundSettings(
                argb = b.optLong("argb", 0L).toInt(),
                opacity = b.optDouble("opacity", 0.0).toFloat(),
                cornerRadiusDp = b.optDouble("cornerRadiusDp", 0.0).toFloat(),
                paddingDp = b.optDouble("paddingDp", 0.0).toFloat(),
            ),
            fourByTwo = decodeOverride(root.opt("fourByTwo")),
            fourByOne = decodeOverride(root.opt("fourByOne")),
        )
    }

    private fun encodeOverride(v: ProfileOverride?): Any = v?.let {
        JSONObject().apply {
            putNullable("timeWeight", it.timeWeight)
            putNullable("dateWeight", it.dateWeight)
            putNullable("timeSizeSp", it.timeSizeSp)
            putNullable("dateSizeSp", it.dateSizeSp)
            putNullable("timeXDp", it.timeXDp)
            putNullable("timeYDp", it.timeYDp)
            putNullable("dateXDp", it.dateXDp)
            putNullable("dateYDp", it.dateYDp)
            putNullable("dateEnabled", it.dateEnabled)
        }
    } ?: JSONObject.NULL

    private fun decodeOverride(value: Any?): ProfileOverride? {
        val o = value as? JSONObject ?: return null
        fun optIntOrNull(k: String) = if (o.has(k) && !o.isNull(k)) o.getInt(k) else null
        fun optFloatOrNull(k: String) = if (o.has(k) && !o.isNull(k)) o.getDouble(k).toFloat() else null
        fun optBoolOrNull(k: String) = if (o.has(k) && !o.isNull(k)) o.getBoolean(k) else null
        return ProfileOverride(
            timeWeight = optIntOrNull("timeWeight"),
            dateWeight = optIntOrNull("dateWeight"),
            timeSizeSp = optFloatOrNull("timeSizeSp"),
            dateSizeSp = optFloatOrNull("dateSizeSp"),
            timeXDp = optFloatOrNull("timeXDp"),
            timeYDp = optFloatOrNull("timeYDp"),
            dateXDp = optFloatOrNull("dateXDp"),
            dateYDp = optFloatOrNull("dateYDp"),
            dateEnabled = optBoolOrNull("dateEnabled"),
        )
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private fun key(id: Int) = "widget.$id.settings"

    companion object {
        private const val PREFS_NAME = "clocky_widget_settings"
        private const val SCHEMA_VERSION = 1
    }
}
