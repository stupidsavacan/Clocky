package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.SizeClass

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
)

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
)

data class ResolvedDigitalSpec(
    val sizeClass: SizeClass,
    val time: ResolvedText,
    /** TextClock formats; the host picks one from the system 12/24h setting. */
    val timeFormats: TimeFormats,
    val date: ResolvedText,
    val dateVisible: Boolean,
    val datePattern: String,
    val background: ResolvedBackground,
    val paddingDp: Float,
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
}
