package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.InfoSource
import com.stupidsavacan.clocky.design.model.TapActions
import com.stupidsavacan.clocky.design.model.ColorRole
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.Template

/** Host size in dp as reported by AppWidget options (all zero when the host has not said). */
data class SizeContext(
    val minWidthDp: Int,
    val minHeightDp: Int,
    val maxWidthDp: Int,
    val maxHeightDp: Int,
)

/** Device facts the resolver needs; kept explicit so resolution stays pure and testable. */
data class RenderEnvironment(
    val sdkInt: Int,
    val isRtl: Boolean,
    /** Locale-derived default date pattern (Locale Auto, contract §6). */
    val localeAutoDatePattern: String,
    /** System night mode at resolve time; selects the variant when the host cannot switch itself (API < 31). */
    val isNight: Boolean = false,
    /** Formatted next alarm from AlarmManager, or null when none is set (Info line, End-State 5.3). */
    val nextAlarmText: String? = null,
    /** Editor-only stand-in shown for the next-alarm Info line when no alarm is set. Never set by the provider. */
    val sampleAlarmText: String? = null,
    /**
     * Whether the real RemoteViews host renders this APK's bundled `res/font` faces. Unknown hosts are false:
     * a bundled family is then resolved to the platform-safe system face (requested id is kept).
     */
    val supportsBundledFonts: Boolean = false,
    /** Cross-package RemoteViews marker font/shaping capability; unknown hosts use localized a. */
    val supportsLatinAmPmMarker: Boolean = false,
)

/**
 * How the host should keep a color current without an app update (API 31+ only). [argb] on the
 * owning element is always the value for the current night mode, so unsupported hosts and tests
 * have a static answer.
 */
sealed interface ColorBinding {
    /** `setColorInt(notNight, night)`: the launcher picks by its own night mode. */
    data class DayNight(val notNightArgb: Int, val nightArgb: Int) : ColorBinding

    /** A system (Material You) role resolved by the launcher from `system_accent*`/`system_neutral*`. */
    data class SystemRole(val role: ColorRole) : ColorBinding
}

/** A font fragment face: [fontId] selects the fragment layout, [weight] the face inside it. */
data class ResolvedFace(
    val fontId: String,
    val weight: Int,
)

/** Which pre-built fragment layout carries the legibility shadow (End-State 5.6). */
enum class ShadowVariant { CLASSIC, OFF, SOFT_DARK, SOFT_LIGHT, STRONG_DARK, STRONG_LIGHT }

data class ResolvedText(
    val face: ResolvedFace,
    val sizeSp: Float,
    val letterSpacingEm: Float,
    /** Effective ARGB: stored RGB with the element opacity applied as alpha. */
    val argb: Int,
    val alignment: Alignment,
    /** Effective translation in dp, already mirrored for RTL and degraded per SDK. */
    val xDp: Float,
    val yDp: Float,
    /** Opacity applied on top of a [binding] that cannot carry alpha itself (SystemRole). */
    val opacity: Float = 1f,
    val binding: ColorBinding? = null,
    val shadow: ShadowVariant = ShadowVariant.CLASSIC,
)

sealed interface ResolvedRadius {
    /** API 31+: follow the launcher's system widget radius. */
    data object System : ResolvedRadius
    data class Dp(val value: Float) : ResolvedRadius
}

data class ResolvedBackground(
    val visible: Boolean,
    val rgb: Int,
    /** 0..255 */
    val alpha: Int,
    val radius: ResolvedRadius,
    val binding: ColorBinding? = null,
    val type: BackgroundType = BackgroundType.SOLID,
    /** Gradient end color (opaque RGB), already resolved for the current night mode. */
    val endRgb: Int = rgb,
    val gradientAngleDeg: Int = 0,
    val borderWidthDp: Float = 0f,
) {
    /** Gradient and outline are drawn into a bitmap; solid is tinted natively (End-State 5.7). */
    val isRendered: Boolean get() = visible && (type == BackgroundType.GRADIENT || type == BackgroundType.OUTLINE)
}

/** The Info line as the composer renders it: always a TextClock, the text being a pattern (End-State 5.3). */
data class ResolvedInfo(
    val source: InfoSource,
    val text: ResolvedText,
    val format12Hour: String,
    val format24Hour: String,
    val timeZoneId: String?,
    /** True when [format12Hour] shows an editor sample because no alarm is set. */
    val isSample: Boolean,
)

/** Host-ticking suffix: HH on font-capable hosts, localized a otherwise. */
data class ResolvedAmPm(
    val text: ResolvedText,
    val scale: Float,
    val format12Hour: String = "a",
    /** Empty when the system decides: a 24-hour system clock shows no AM/PM. */
    val format24Hour: String = "",
    /** Renderer-only choice; never stored in behavior.amPm or offered in FontCatalog. */
    val useLatinMarkerFont: Boolean = false,
)

data class ResolvedDigitalSpec(
    val sizeClass: SizeClass,
    val template: Template,
    val time: ResolvedText,
    /** TextClock formats; the host picks one from the system 12/24h setting. */
    val timeFormats: TimeFormats,
    val date: ResolvedText,
    val dateVisible: Boolean,
    val dateUppercase: Boolean = true,
    val datePattern: String,
    val background: ResolvedBackground,
    val paddingDp: Float,
    /** Time/date gap of the template. Zero for designs without style tokens, so Phase 1A widgets keep their exact look. */
    val gapDp: Float = 0f,
    val amPm: ResolvedAmPm? = null,
    /** Null when the design has no Info line or it is not shown at this size/template. */
    val info: ResolvedInfo? = null,
    val taps: TapActions = TapActions(),
    /** Every requested ≠ effective difference, for editor disclosure (principle 5). */
    val degradations: List<Degradation>,
)

data class TimeFormats(val format12Hour: String, val format24Hour: String)

enum class TextElementKind { TIME, DATE, INFO }

enum class InfoHiddenReason { STRIP_SIZE, MINIMAL_TEMPLATE }

sealed interface Degradation {
    /** The host cannot apply the dedicated Latin marker font (including API 23–25). */
    data object AmPmLocalized : Degradation
    data class WeightApproximated(
        val element: TextElementKind,
        val requested: Int,
        val effective: Int,
        /** Set when the chosen family itself lacks the weight (bundled / alias font), not the Android version. */
        val fontId: String? = null,
    ) : Degradation
    data class FontFallback(val element: TextElementKind, val requestedFontId: String, val reason: String) : Degradation
    data class OffsetUnsupported(val element: TextElementKind, val minSdk: Int) : Degradation
    data class OffsetClamped(val element: TextElementKind) : Degradation
    /** The Info line is requested but cannot be shown here. */
    data class InfoNotShown(val reason: InfoHiddenReason) : Degradation

    data class RadiusApproximated(val requestedDp: Float?, val effectiveDp: Float) : Degradation

    /** Gradient / outline backgrounds are bitmaps: colors cannot follow day/night or Material You between updates. */
    data object RenderedBackgroundStatic : Degradation

    /** Follow system below API 31: shows the variant of the night mode at the last widget update. */
    data object ThemeSwitchUnavailable : Degradation

    /** Material You below API 31: the design's own palette is shown instead. */
    data object DynamicColorUnavailable : Degradation
}
