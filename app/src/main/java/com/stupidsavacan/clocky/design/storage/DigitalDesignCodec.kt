package com.stupidsavacan.clocky.design.storage

import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.BackgroundElement
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.Behavior
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.CornerRadius
import com.stupidsavacan.clocky.design.model.DateElement
import com.stupidsavacan.clocky.design.model.DesignLayout
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.HourMode
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement
import com.stupidsavacan.clocky.design.model.normalized
import org.json.JSONObject
import java.util.Locale

/**
 * JSON codec for Design schema v2, including the one-way v1 → v2 migration.
 *
 * The migration table is docs/architecture/PHASE_1A_DIGITAL_CORE.md §2. v1 decoding uses the
 * exact defaults of the Phase 0 SharedPreferencesWidgetSettingsStore so absent keys migrate to
 * what that store would have loaded.
 */
object DigitalDesignCodec {
    const val SCHEMA_V1 = 1
    const val SCHEMA_V2 = 2

    fun schemaOf(root: JSONObject): Int = root.optInt("schema", SCHEMA_V1)

    /** Decodes v1 or v2 JSON into a normalized v2 design. Unknown future schemas are rejected. */
    fun decode(root: JSONObject): DigitalDesign = when (val schema = schemaOf(root)) {
        SCHEMA_V1 -> migrateV1(root)
        SCHEMA_V2 -> decodeV2(root)
        else -> throw IllegalArgumentException("Unsupported design schema $schema")
    }.normalized()

    fun encode(design: DigitalDesign): JSONObject {
        val d = design.normalized()
        return JSONObject().apply {
            put("schema", SCHEMA_V2)
            putOpt("origin", d.origin)
            put("time", JSONObject().apply {
                putStyle(d.time.style)
                put("leadingZero", d.time.leadingZero)
            })
            put("date", JSONObject().apply {
                put("visible", d.date.visible)
                putStyle(d.date.style)
                put("formatPattern", d.date.formatPattern ?: JSONObject.NULL)
            })
            put("background", JSONObject().apply {
                put("type", d.background.type.name)
                put("color", encodeColor(d.background.color))
                put("opacity", d.background.opacity.toDouble())
                put("cornerRadius", encodeRadius(d.background.cornerRadius))
                put("paddingDp", d.background.paddingDp.toDouble())
            })
            put("layout", JSONObject().apply {
                put("template", d.layout.template.name)
                put("overrides", JSONObject().apply {
                    d.layout.overrides.forEach { (sizeClass, patch) ->
                        put(sizeClass.key, encodePatch(patch))
                    }
                })
            })
            put("behavior", JSONObject().apply {
                put("hourMode", d.behavior.hourMode.name)
            })
        }
    }

    // ---- v2 ----

    private fun decodeV2(root: JSONObject): DigitalDesign {
        val t = root.optJSONObject("time") ?: JSONObject()
        val d = root.optJSONObject("date") ?: JSONObject()
        val b = root.optJSONObject("background") ?: JSONObject()
        val l = root.optJSONObject("layout") ?: JSONObject()
        val defaults = DigitalDesign()
        return DigitalDesign(
            origin = root.optStringOrNull("origin"),
            time = TimeElement(
                style = decodeStyle(t, defaults.time.style),
                leadingZero = t.optBoolean("leadingZero", defaults.time.leadingZero),
            ),
            date = DateElement(
                visible = d.optBoolean("visible", defaults.date.visible),
                style = decodeStyle(d, defaults.date.style),
                formatPattern = d.optStringOrNull("formatPattern"),
            ),
            background = BackgroundElement(
                type = enumOr(b.optString("type"), defaults.background.type),
                color = decodeColor(b.optJSONObject("color"), defaults.background.color),
                opacity = b.optDouble("opacity", defaults.background.opacity.toDouble()).toFloat(),
                cornerRadius = decodeRadius(b.optJSONObject("cornerRadius")),
                paddingDp = b.optDouble("paddingDp", defaults.background.paddingDp.toDouble()).toFloat(),
            ),
            layout = DesignLayout(
                template = enumOr(l.optString("template"), defaults.layout.template),
                overrides = decodeOverrides(l.optJSONObject("overrides")),
            ),
            behavior = Behavior(
                hourMode = enumOr(
                    (root.optJSONObject("behavior") ?: JSONObject()).optString("hourMode"),
                    defaults.behavior.hourMode,
                ),
            ),
        )
    }

    private fun JSONObject.putStyle(s: TextStyle) {
        put("font", s.fontId)
        put("weight", s.weight)
        put("sizeSp", s.sizeSp.toDouble())
        put("letterSpacingEm", s.letterSpacingEm.toDouble())
        put("color", encodeColor(s.color))
        put("opacity", s.opacity.toDouble())
        put("alignment", s.alignment.name)
        put("xDp", s.xDp.toDouble())
        put("yDp", s.yDp.toDouble())
    }

    private fun decodeStyle(o: JSONObject, defaults: TextStyle) = TextStyle(
        fontId = o.optString("font", defaults.fontId),
        weight = o.optInt("weight", defaults.weight),
        sizeSp = o.optDouble("sizeSp", defaults.sizeSp.toDouble()).toFloat(),
        letterSpacingEm = o.optDouble("letterSpacingEm", defaults.letterSpacingEm.toDouble()).toFloat(),
        color = decodeColor(o.optJSONObject("color"), defaults.color),
        opacity = o.optDouble("opacity", defaults.opacity.toDouble()).toFloat(),
        alignment = enumOr(o.optString("alignment"), defaults.alignment),
        xDp = o.optDouble("xDp", defaults.xDp.toDouble()).toFloat(),
        yDp = o.optDouble("yDp", defaults.yDp.toDouble()).toFloat(),
    )

    private fun encodeColor(color: ColorRef): JSONObject = when (color) {
        is ColorRef.Fixed -> JSONObject()
            .put("type", "fixed")
            .put("rgb", String.format(Locale.ROOT, "#%06X", color.rgb))
    }

    private fun decodeColor(o: JSONObject?, fallback: ColorRef): ColorRef {
        if (o == null || o.optString("type") != "fixed") return fallback
        val raw = o.optString("rgb").removePrefix("#")
        val value = raw.takeIf { it.length == 6 }?.toIntOrNull(16) ?: return fallback
        return ColorRef.Fixed(value)
    }

    private fun encodeRadius(r: CornerRadius): JSONObject = when (r) {
        CornerRadius.System -> JSONObject().put("type", "system")
        is CornerRadius.Dp -> JSONObject().put("type", "dp").put("value", r.value.toDouble())
    }

    private fun decodeRadius(o: JSONObject?): CornerRadius = when (o?.optString("type")) {
        "dp" -> CornerRadius.Dp(o.optDouble("value", 0.0).toFloat())
        else -> CornerRadius.System
    }

    private fun encodePatch(p: LayoutPatch): JSONObject = JSONObject().apply {
        p.timeWeight?.let { put(PATH_TIME_WEIGHT, it) }
        p.dateWeight?.let { put(PATH_DATE_WEIGHT, it) }
        p.timeSizeSp?.let { put(PATH_TIME_SIZE, it.toDouble()) }
        p.dateSizeSp?.let { put(PATH_DATE_SIZE, it.toDouble()) }
        p.timeXDp?.let { put(PATH_TIME_X, it.toDouble()) }
        p.timeYDp?.let { put(PATH_TIME_Y, it.toDouble()) }
        p.dateXDp?.let { put(PATH_DATE_X, it.toDouble()) }
        p.dateYDp?.let { put(PATH_DATE_Y, it.toDouble()) }
        p.dateVisible?.let { put(PATH_DATE_VISIBLE, it) }
    }

    private fun decodeOverrides(o: JSONObject?): Map<SizeClass, LayoutPatch> {
        if (o == null) return emptyMap()
        return SizeClass.entries.mapNotNull { sizeClass ->
            o.optJSONObject(sizeClass.key)?.let { sizeClass to decodePatch(it) }
        }.toMap()
    }

    private fun decodePatch(o: JSONObject) = LayoutPatch(
        timeWeight = o.intOrNull(PATH_TIME_WEIGHT),
        dateWeight = o.intOrNull(PATH_DATE_WEIGHT),
        timeSizeSp = o.floatOrNull(PATH_TIME_SIZE),
        dateSizeSp = o.floatOrNull(PATH_DATE_SIZE),
        timeXDp = o.floatOrNull(PATH_TIME_X),
        timeYDp = o.floatOrNull(PATH_TIME_Y),
        dateXDp = o.floatOrNull(PATH_DATE_X),
        dateYDp = o.floatOrNull(PATH_DATE_Y),
        dateVisible = if (o.has(PATH_DATE_VISIBLE) && !o.isNull(PATH_DATE_VISIBLE)) {
            o.getBoolean(PATH_DATE_VISIBLE)
        } else {
            null
        },
    )

    // ---- v1 → v2 migration ----

    private fun migrateV1(root: JSONObject): DigitalDesign {
        val t = root.optJSONObject("time") ?: JSONObject()
        val d = root.optJSONObject("date") ?: JSONObject()
        val b = root.optJSONObject("background") ?: JSONObject()
        val overrides = buildMap {
            migrateV1Override(root.opt("fourByOne"))?.let { put(SizeClass.STRIP, it) }
            migrateV1Override(root.opt("fourByTwo"))?.let { put(SizeClass.CARD, it) }
        }
        val backgroundOpacity = foldedOpacity(
            b.optLong("argb", 0L).toInt(),
            b.optDouble("opacity", 0.0).toFloat(),
        )
        val radius = b.optDouble("cornerRadiusDp", 0.0).toFloat()
        val solid = backgroundOpacity > 0f
        return DigitalDesign(
            origin = root.optString("presetId", DigitalDesign.DEFAULT_ORIGIN),
            time = TimeElement(
                style = migrateV1Style(t, defaultSizeSp = 64.0),
                leadingZero = t.optBoolean("leadingZero", false),
            ),
            date = DateElement(
                visible = d.optBoolean("enabled", true),
                style = migrateV1Style(d, defaultSizeSp = 14.0),
                // v1 never rendered formatPattern (the provider always used the locale skeleton),
                // so Locale Auto preserves exactly what the user saw.
                formatPattern = null,
            ),
            background = BackgroundElement(
                // v1 never rendered a background; only a visible one carries its color across.
                type = if (solid) BackgroundType.SOLID else BackgroundType.NONE,
                color = if (solid) {
                    ColorRef.Fixed(b.optLong("argb", 0L).toInt() and RGB_MASK)
                } else {
                    BackgroundElement().color
                },
                opacity = if (solid) backgroundOpacity else BackgroundElement().opacity,
                cornerRadius = if (radius > 0f) CornerRadius.Dp(radius) else CornerRadius.System,
                paddingDp = b.optDouble("paddingDp", 0.0).toFloat(),
            ),
            layout = DesignLayout(overrides = overrides),
            behavior = Behavior(hourMode = enumOr(t.optString("hourMode"), HourMode.FOLLOW_SYSTEM)),
        )
    }

    private fun migrateV1Style(o: JSONObject, defaultSizeSp: Double): TextStyle {
        val argb = o.optLong("argb", 0xFFFFFFFFL).toInt()
        return TextStyle(
            fontId = o.optString("fontFamily", FontIds.SYSTEM_SANS),
            weight = o.optInt("weight", 400),
            sizeSp = o.optDouble("sizeSp", defaultSizeSp).toFloat(),
            letterSpacingEm = o.optDouble("letterSpacing", 0.0).toFloat(),
            color = ColorRef.Fixed(argb and RGB_MASK),
            opacity = foldedOpacity(argb, o.optDouble("opacity", 1.0).toFloat()),
            alignment = when (o.optString("alignment")) {
                "LEFT" -> Alignment.START
                "RIGHT" -> Alignment.END
                else -> Alignment.CENTER
            },
            xDp = o.optDouble("xDp", 0.0).toFloat(),
            yDp = o.optDouble("yDp", 0.0).toFloat(),
        )
    }

    private fun migrateV1Override(value: Any?): LayoutPatch? {
        val o = value as? JSONObject ?: return null
        return LayoutPatch(
            timeWeight = o.intOrNull("timeWeight"),
            dateWeight = o.intOrNull("dateWeight"),
            timeSizeSp = o.floatOrNull("timeSizeSp"),
            dateSizeSp = o.floatOrNull("dateSizeSp"),
            timeXDp = o.floatOrNull("timeXDp"),
            timeYDp = o.floatOrNull("timeYDp"),
            dateXDp = o.floatOrNull("dateXDp"),
            dateYDp = o.floatOrNull("dateYDp"),
            dateVisible = if (o.has("dateEnabled") && !o.isNull("dateEnabled")) {
                o.getBoolean("dateEnabled")
            } else {
                null
            },
        ).takeIf { !it.isEmpty }
    }

    /** Contract §5: effectiveOpacity = separateOpacity × (legacyAlpha / 255). */
    internal fun foldedOpacity(argb: Int, opacity: Float): Float {
        val alpha = (argb ushr 24) and 0xFF
        val safe = if (opacity.isFinite()) opacity.coerceIn(0f, 1f) else 1f
        return safe * (alpha / 255f)
    }

    // ---- helpers ----

    private const val RGB_MASK = 0xFFFFFF

    const val PATH_TIME_WEIGHT = "time.weight"
    const val PATH_DATE_WEIGHT = "date.weight"
    const val PATH_TIME_SIZE = "time.sizeSp"
    const val PATH_DATE_SIZE = "date.sizeSp"
    const val PATH_TIME_X = "time.xDp"
    const val PATH_TIME_Y = "time.yDp"
    const val PATH_DATE_X = "date.xDp"
    const val PATH_DATE_Y = "date.yDp"
    const val PATH_DATE_VISIBLE = "date.visible"

    private val SizeClass.key: String get() = name.lowercase(Locale.ROOT)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.floatOrNull(key: String): Float? =
        if (has(key) && !isNull(key)) getDouble(key).toFloat() else null

    private inline fun <reified T : Enum<T>> enumOr(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
