package com.stupidsavacan.clocky.design.model

import com.stupidsavacan.clocky.customization.font.WidgetLetterSpacingPolicy

/**
 * Design model v2 for the Clocky Digital widget (CLOCKY_END_STATE.md §5, §12).
 *
 * Every value here is a user *request*. Device- and host-dependent degradation happens only in
 * DesignResolver, so the stored request survives SDK/launcher limits (principle 5).
 */
data class WidgetInstance(
    val appWidgetId: Int,
    val design: DigitalDesign = DigitalDesign(),
)

data class DigitalDesign(
    /** Preset identity the design was derived from; carried over from v1 `presetId`. */
    val origin: String? = DEFAULT_ORIGIN,
    val time: TimeElement = TimeElement(),
    val date: DateElement = DateElement(),
    val background: BackgroundElement = BackgroundElement(),
    val layout: DesignLayout = DesignLayout(),
    val behavior: Behavior = Behavior(),
    /** Phase 2 Info line (End-State 5.3); [InfoSource.NONE] renders nothing, so older designs are unchanged. */
    val info: InfoElement = InfoElement(),
    /** Phase 2 effects (End-State 5.6). */
    val effects: Effects = Effects(),
    /** Phase 1B style tokens (null for designs that predate them: every color is then Fixed). */
    val style: StyleTokens? = null,
    /** Built-in provenance of the snapshot this design was copied from (null for custom designs). */
    val source: DesignSource? = null,
) {
    companion object {
        const val DEFAULT_ORIGIN = "google-clock"
    }
}

data class TextStyle(
    val fontId: String = FontIds.SYSTEM_SANS,
    val weight: Int = 400,
    val sizeSp: Float,
    val letterSpacingEm: Float = 0f,
    val color: ColorRef = ColorRef.Fixed(0xFFFFFF),
    val opacity: Float = 1f,
    val alignment: Alignment = Alignment.CENTER,
    val xDp: Float = 0f,
    val yDp: Float = 0f,
)

data class TimeElement(
    val style: TextStyle = TextStyle(sizeSp = 64f),
    val leadingZero: Boolean = false,
)

data class DateElement(
    val visible: Boolean = true,
    val style: TextStyle = TextStyle(sizeSp = 14f),
    /** `null` = Locale Auto (contract §6); non-null = explicit pattern rendered literally. */
    val formatPattern: String? = null,
    /** Phase 1A/1B always showed dates in capitals; kept as the default. As-is uses the pattern's own case. */
    val uppercase: Boolean = true,
    /** null retains the historical token-dependent gap (4dp with tokens, otherwise 0dp). */
    val gapDp: Float? = null,
)

/**
 * Color references (End-State §5.5). [Fixed] is a literal; [Token] follows the design's
 * [StyleTokens.palette] role, so Quick Tune recolors a whole design by swapping the palette.
 * Dynamic (Material You) is not a third reference kind: it is a [ThemeMode] of the tokens, so the
 * same Token resolves to system colors on API 31+.
 */
sealed interface ColorRef {
    /** Opaque 0xRRGGBB. Alpha always lives in the element's separate opacity (contract §5). */
    data class Fixed(val rgb: Int) : ColorRef {
        init {
            require(rgb ushr 24 == 0) { "Fixed colors are stored without alpha: ${rgb.toString(16)}" }
        }
    }

    data class Token(val role: ColorRole) : ColorRef
}

enum class Alignment { START, CENTER, END }

/**
 * [SOLID] and the Phase 2 rendered types: [GRADIENT] (linear, [BackgroundElement.color] to
 * [BackgroundElement.gradientEnd]) and [OUTLINE] (stroke only). The rendered types are bitmaps
 * drawn at the widget's real size (End-State 5.7), regenerated only when size, theme or settings change.
 */
enum class BackgroundType { NONE, SOLID, GRADIENT, OUTLINE }

sealed interface CornerRadius {
    /** Follow the launcher's widget radius (API 31+ system_app_widget_background_radius). */
    data object System : CornerRadius
    data class Dp(val value: Float) : CornerRadius
}

data class BackgroundElement(
    val type: BackgroundType = BackgroundType.NONE,
    val color: ColorRef = ColorRef.Fixed(0x111111),
    val opacity: Float = 0.9f,
    val cornerRadius: CornerRadius = CornerRadius.System,
    val paddingDp: Float = 0f,
    /** Gradient end color ([color] is the start). Only used by [BackgroundType.GRADIENT]. */
    val gradientEnd: ColorRef = ColorRef.Fixed(0x444444),
    /** Linear gradient direction in degrees clockwise from "start color at top" (0 = top to bottom). */
    val gradientAngleDeg: Int = 0,
    /** Stroke width of [BackgroundType.OUTLINE]. */
    val borderWidthDp: Float = 2f,
)

/**
 * Compositions (End-State §5.8). [TIME_FIRST] is the Phase 1A template and honors each element's
 * alignment. [CENTER_STACK] puts the date above the time. [INLINE] is one row, time then date.
 * [SPLIT] is date top-left / time bottom-right on Card and a space-between row on Strip.
 * [MINIMAL] is time only; the requested date visibility is retained but not rendered.
 */
enum class Template { TIME_FIRST, CENTER_STACK, INLINE, SPLIT, MINIMAL }

enum class SizeClass {
    STRIP, CARD, SQUARE, LARGE;

    val allowsWeightOverride: Boolean get() = this == STRIP || this == CARD
}

/**
 * Nullable per-size-class patch: `null` inherits the design's base value. Persisted as
 * property-path keys (End-State §9). Weight is overridable for v1 compatibility; see
 * docs/architecture/PHASE_1A_DIGITAL_CORE.md §2.
 */
data class LayoutPatch(
    val timeWeight: Int? = null,
    val dateWeight: Int? = null,
    val timeSizeSp: Float? = null,
    val dateSizeSp: Float? = null,
    val timeXDp: Float? = null,
    val timeYDp: Float? = null,
    val dateXDp: Float? = null,
    val dateYDp: Float? = null,
    val dateVisible: Boolean? = null,
    val template: Template? = null,
    val timeAlignment: Alignment? = null,
    val dateAlignment: Alignment? = null,
    val infoSizeSp: Float? = null,
    val infoXDp: Float? = null,
    val infoYDp: Float? = null,
    val infoAlignment: Alignment? = null,
    val infoVisible: Boolean? = null,
    val paddingDp: Float? = null,
    val dateGapDp: Float? = null,
    /** Unknown requested template; runtime uses TIME_FIRST without rewriting this request. */
    val requestedTemplate: String? = null,
    /** Inert JSON values of unrecognized paths (including future values of known paths). */
    val preserved: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = this == EMPTY

    companion object {
        val EMPTY = LayoutPatch()
    }

    /** This patch wins field-by-field; null inherits from [parent]. */
    fun over(parent: LayoutPatch): LayoutPatch = LayoutPatch(
        timeWeight = timeWeight ?: parent.timeWeight,
        dateWeight = dateWeight ?: parent.dateWeight,
        timeSizeSp = timeSizeSp ?: parent.timeSizeSp,
        dateSizeSp = dateSizeSp ?: parent.dateSizeSp,
        timeXDp = timeXDp ?: parent.timeXDp,
        timeYDp = timeYDp ?: parent.timeYDp,
        dateXDp = dateXDp ?: parent.dateXDp,
        dateYDp = dateYDp ?: parent.dateYDp,
        dateVisible = dateVisible ?: parent.dateVisible,
        template = if (requestedTemplate != null) null else template ?: parent.template,
        requestedTemplate = if (template != null) null else requestedTemplate ?: parent.requestedTemplate,
        timeAlignment = timeAlignment ?: parent.timeAlignment,
        dateAlignment = dateAlignment ?: parent.dateAlignment,
        infoSizeSp = infoSizeSp ?: parent.infoSizeSp,
        infoXDp = infoXDp ?: parent.infoXDp,
        infoYDp = infoYDp ?: parent.infoYDp,
        infoAlignment = infoAlignment ?: parent.infoAlignment,
        infoVisible = infoVisible ?: parent.infoVisible,
        paddingDp = paddingDp ?: parent.paddingDp,
        dateGapDp = dateGapDp ?: parent.dateGapDp,
        preserved = parent.preserved + preserved,
    )
}

data class DesignLayout(
    val template: Template = Template.TIME_FIRST,
    val overrides: Map<SizeClass, LayoutPatch> = emptyMap(),
    val preservedOverrides: Map<String, String> = emptyMap(),
    val requestedTemplate: String? = null,
) {
    init {
        require(overrides.all { (c, p) -> c.allowsWeightOverride || (p.timeWeight == null && p.dateWeight == null) }) {
            "Weight overrides are supported only for Strip/Card"
        }
    }

    fun patchFor(sizeClass: SizeClass): LayoutPatch = overrides[sizeClass] ?: LayoutPatch.EMPTY

    /** R2: Square/Large inherit Card, while Strip/Card inherit only the base. */
    fun inheritedPatchFor(sizeClass: SizeClass): LayoutPatch = when (sizeClass) {
        SizeClass.STRIP, SizeClass.CARD -> patchFor(sizeClass)
        SizeClass.SQUARE, SizeClass.LARGE -> patchFor(sizeClass).over(patchFor(SizeClass.CARD))
    }

    /** Replaces one class's patch; an empty patch removes the entry so storage stays minimal. */
    fun withPatch(sizeClass: SizeClass, patch: LayoutPatch): DesignLayout = copy(
        overrides = if (patch.isEmpty) overrides - sizeClass else overrides + (sizeClass to patch),
        // An explicit edit replaces an unrecognized representation of this known class only.
        preservedOverrides = preservedOverrides - sizeClass.name.lowercase(java.util.Locale.ROOT),
    )
}

enum class HourMode { FOLLOW_SYSTEM, FORCE_12_HOUR, FORCE_24_HOUR }

enum class AmPmMode { HIDDEN, SUFFIX }

/** AM/PM beside the time (End-State 5.1); shown only when the effective clock is 12-hour. */
data class AmPmStyle(
    val mode: AmPmMode = AmPmMode.HIDDEN,
    /** Suffix size as a fraction of the time size (End-State: 25..60%). */
    val scale: Float = DEFAULT_AM_PM_SCALE,
)

/** What a tap on a widget zone does (End-State 5.9). [OPEN_CLOCKY] is the pre-Phase-2 whole-widget behavior. */
enum class TapAction { OPEN_CLOCKY, OPEN_ALARMS, OPEN_TIMER, OPEN_STOPWATCH, OPEN_CALENDAR, EDIT_WIDGET, NONE }

data class TapActions(
    val time: TapAction = TapAction.OPEN_CLOCKY,
    val date: TapAction = TapAction.OPEN_CLOCKY,
    val info: TapAction = TapAction.OPEN_ALARMS,
) {
    val isDefault: Boolean get() = this == TapActions()
}

data class Behavior(
    val hourMode: HourMode = HourMode.FOLLOW_SYSTEM,
    val amPm: AmPmStyle = AmPmStyle(),
    /** Seconds make the host redraw every second; the editor warns about battery. */
    val showSeconds: Boolean = false,
    val tap: TapActions = TapActions(),
)

enum class InfoSource { NONE, NEXT_ALARM, SECOND_TIMEZONE }

/**
 * The single optional Info line (End-State 5.3). Weather, calendar, battery and free data
 * providers are rejected (End-State 10).
 */
data class InfoElement(
    val source: InfoSource = InfoSource.NONE,
    val style: TextStyle = TextStyle(
        fontId = FontIds.TOKEN_SECONDARY,
        sizeSp = 12f,
        color = ColorRef.Token(ColorRole.SECONDARY),
    ),
    /** IANA id for [InfoSource.SECOND_TIMEZONE]; null falls back to UTC. */
    val timeZoneId: String? = null,
    /** Optional city label in front of the second time (max [MAX_INFO_LABEL] chars). */
    val label: String? = null,
    val visible: Boolean = true,
)

/**
 * Legibility shadow (End-State 5.6). [CLASSIC] is the shadow carried over from the AOSP widget and
 * is what every pre-Phase-2 design shows; the others pick a black or white shadow from the text color.
 */
enum class ShadowLevel { CLASSIC, OFF, SOFT, STRONG }

data class Effects(val shadow: ShadowLevel = ShadowLevel.CLASSIC)

object FontIds {
    const val SYSTEM_SANS = "system-sans"

    /** Reserved ids: the face and weight come from [StyleTokens.fontPrimary] / [StyleTokens.fontSecondary]. */
    const val TOKEN_PRIMARY = "token:fontPrimary"
    const val TOKEN_SECONDARY = "token:fontSecondary"

    fun isToken(fontId: String): Boolean = fontId == TOKEN_PRIMARY || fontId == TOKEN_SECONDARY
}

const val MIN_WEIGHT = 100
const val MAX_WEIGHT = 900
const val MAX_PADDING_DP = 32f
const val MAX_CORNER_RADIUS_DP = 48f
const val MAX_BORDER_DP = 4f
const val MIN_AM_PM_SCALE = 0.25f
const val MAX_AM_PM_SCALE = 0.6f
const val DEFAULT_AM_PM_SCALE = 0.4f
const val MAX_INFO_LABEL = 24

/** Clamps requested values to their documented domains without filling inherited (null) fields. */
fun DigitalDesign.normalized(): DigitalDesign = copy(
    time = time.copy(style = time.style.normalized()),
    date = date.copy(style = date.style.normalized(), formatPattern = date.formatPattern?.takeIf { it.isNotBlank() },
        gapDp = date.gapDp?.finiteOr(0f)?.coerceIn(-20f, 40f)),
    background = background.copy(
        opacity = background.opacity.unitOr(0.9f),
        cornerRadius = when (val r = background.cornerRadius) {
            CornerRadius.System -> r
            is CornerRadius.Dp -> CornerRadius.Dp(r.value.finiteOr(0f).coerceIn(0f, MAX_CORNER_RADIUS_DP))
        },
        paddingDp = background.paddingDp.finiteOr(0f).coerceIn(0f, MAX_PADDING_DP),
        gradientAngleDeg = ((background.gradientAngleDeg % 360) + 360) % 360,
        borderWidthDp = background.borderWidthDp.finiteOr(2f).coerceIn(0f, MAX_BORDER_DP),
    ),
    behavior = behavior.copy(
        amPm = behavior.amPm.copy(scale = behavior.amPm.scale.finiteOr(DEFAULT_AM_PM_SCALE).coerceIn(MIN_AM_PM_SCALE, MAX_AM_PM_SCALE)),
    ),
    info = info.copy(
        style = info.style.normalized(),
        timeZoneId = info.timeZoneId?.takeIf { it.isNotBlank() },
        label = info.label?.trim()?.take(MAX_INFO_LABEL)?.takeIf { it.isNotEmpty() },
    ),
    style = style?.normalized(),
    layout = layout.copy(
        overrides = layout.overrides
            .mapValues { (_, patch) -> patch.normalized() }
            .filterValues { !it.isEmpty },
    ),
)

private fun TextStyle.normalized(): TextStyle = copy(
    weight = weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT),
    sizeSp = sizeSp.finiteOr(1f).coerceAtLeast(1f),
    letterSpacingEm = WidgetLetterSpacingPolicy.normalize(letterSpacingEm.finiteOr(0f)),
    opacity = opacity.unitOr(1f),
    xDp = xDp.finiteOr(0f),
    yDp = yDp.finiteOr(0f),
)

private fun LayoutPatch.normalized(): LayoutPatch = copy(
    timeWeight = timeWeight?.coerceIn(MIN_WEIGHT, MAX_WEIGHT),
    dateWeight = dateWeight?.coerceIn(MIN_WEIGHT, MAX_WEIGHT),
    timeSizeSp = timeSizeSp?.finiteOr(1f)?.coerceAtLeast(1f),
    dateSizeSp = dateSizeSp?.finiteOr(1f)?.coerceAtLeast(1f),
    timeXDp = timeXDp?.finiteOr(0f),
    timeYDp = timeYDp?.finiteOr(0f),
    dateXDp = dateXDp?.finiteOr(0f),
    dateYDp = dateYDp?.finiteOr(0f),
    infoSizeSp = infoSizeSp?.finiteOr(1f)?.coerceAtLeast(1f),
    infoXDp = infoXDp?.finiteOr(0f),
    infoYDp = infoYDp?.finiteOr(0f),
    paddingDp = paddingDp?.finiteOr(0f)?.coerceIn(0f, MAX_PADDING_DP),
    dateGapDp = dateGapDp?.finiteOr(0f)?.coerceIn(-20f, 40f),
)

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

private fun Float.unitOr(fallback: Float): Float = if (isFinite()) coerceIn(0f, 1f) else fallback
