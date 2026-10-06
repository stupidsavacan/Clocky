package com.stupidsavacan.clocky.design.storage

import android.content.Context
import android.content.SharedPreferences
import com.stupidsavacan.clocky.design.model.WidgetInstance
import org.json.JSONObject

/** Per-appWidgetId design persistence: the single source of truth for Clocky widget presentation. */
interface DesignStore {
    fun load(appWidgetId: Int): WidgetInstance
    fun save(instance: WidgetInstance)
    fun delete(appWidgetId: Int)
}

/**
 * Keeps the Phase 0 location (`clocky_widget_settings` / `widget.<id>.settings`) so existing
 * widgets' settings migrate in place. v1 documents are migrated on load and written back as v2
 * only after a successful decode. A document that cannot be decoded renders defaults and is left
 * untouched rather than silently overwritten.
 */
class SharedPreferencesDesignStore(context: Context) : DesignStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(appWidgetId: Int): WidgetInstance {
        val raw = prefs.getString(key(appWidgetId), null) ?: return WidgetInstance(appWidgetId)
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return WidgetInstance(appWidgetId)
        val design = runCatching { DigitalDesignCodec.decode(root) }.getOrNull()
            ?: return WidgetInstance(appWidgetId)
        val instance = WidgetInstance(appWidgetId, design)
        if (DigitalDesignCodec.schemaOf(root) != DigitalDesignCodec.SCHEMA_V2) {
            save(instance)
        }
        return instance
    }

    override fun save(instance: WidgetInstance) {
        prefs.edit()
            .putString(key(instance.appWidgetId), DigitalDesignCodec.encode(instance.design).toString())
            .apply()
    }

    override fun delete(appWidgetId: Int) {
        prefs.edit().remove(key(appWidgetId)).apply()
    }

    companion object {
        const val PREFS_NAME = "clocky_widget_settings"

        fun key(appWidgetId: Int) = "widget.$appWidgetId.settings"
    }
}
