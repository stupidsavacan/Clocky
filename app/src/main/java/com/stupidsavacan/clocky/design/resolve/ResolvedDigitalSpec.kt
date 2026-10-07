package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.model.Alignment
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
)

data class ResolvedDigitalSpec(
    val sizeClass: SizeClass,
    val template: Template,
    val time: ResolvedText,
    /** TextClock formats; the host picks one from the system 12/24h setting. */
    val timeFormats: TimeFormats,
    val date: ResolvedText,
    val dateVisible: Boolean,
    val datePattern: String,
    val background: ResolvedBackground,
    val paddingDp: Float,
    /** Time/date gap of the template. Zero for designs without style tokens, so Phase 1A widgets keep their exact look. */
    val gapDp: Float = 0f,
    /** Every requested ≠ effective difference, for editor disclosure (principle 5). */
    val degradations: List<Degradation>,
)

data class TimeFormats(val format12Hour: String, val format24Hour: String)

enum class TextElementKind { TIME, DATE }

sealed interface Degradation {
    data class WeightApproximated(val element: TextElementKind, val requested: Int, val effective: Int) : Degradation
    data class FontFallback(val element: TextElementKind, val requestedFontId: String, val reason: String) : Degradation
    data class OffsetUnsupported(val element: TextElementKind, val minSdk: Int) : Degradation
    data class OffsetClamped(val element: TextElementKind) : Degradation
    data class RadiusApproximated(val requestedDp: Float?, val effectiveDp: Float) : Degradation

    /** Follow system below API 31: shows the variant of the night mode at the last widget update. */
    data object ThemeSwitchUnavailable : Degradation

    /** Material You below API 31: the design's own palette is shown instead. */
    data object DynamicColorUnavailable : Degradation
}
