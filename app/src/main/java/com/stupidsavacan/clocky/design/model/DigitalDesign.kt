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

enum class BackgroundType { NONE, SOLID }

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
)

/**
 * Compositions (End-State §5.8). [TIME_FIRST] is the Phase 1A template and honors each element's
 * alignment. [CENTER_STACK] puts the date above the time. [INLINE] is one row, time then date.
 * [SPLIT] is date top-left / time bottom-right on Card and a space-between row on Strip.
 * [MINIMAL] is time only; the requested date visibility is retained but not rendered.
 */
enum class Template { TIME_FIRST, CENTER_STACK, INLINE, SPLIT, MINIMAL }

enum class SizeClass { STRIP, CARD }

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
) {
    val isEmpty: Boolean get() = this == EMPTY

    companion object {
        val EMPTY = LayoutPatch()
    }
}

data class DesignLayout(
    val template: Template = Template.TIME_FIRST,
    val overrides: Map<SizeClass, LayoutPatch> = emptyMap(),
) {
    fun patchFor(sizeClass: SizeClass): LayoutPatch = overrides[sizeClass] ?: LayoutPatch.EMPTY

    /** Replaces one class's patch; an empty patch removes the entry so storage stays minimal. */
    fun withPatch(sizeClass: SizeClass, patch: LayoutPatch): DesignLayout = copy(
        overrides = if (patch.isEmpty) overrides - sizeClass else overrides + (sizeClass to patch),
    )
}

enum class HourMode { FOLLOW_SYSTEM, FORCE_12_HOUR, FORCE_24_HOUR }

data class Behavior(
    val hourMode: HourMode = HourMode.FOLLOW_SYSTEM,
)

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

/** Clamps requested values to their documented domains without filling inherited (null) fields. */
fun DigitalDesign.normalized(): DigitalDesign = copy(
    time = time.copy(style = time.style.normalized()),
    date = date.copy(style = date.style.normalized(), formatPattern = date.formatPattern?.takeIf { it.isNotBlank() }),
    background = background.copy(
        opacity = background.opacity.unitOr(0.9f),
        cornerRadius = when (val r = background.cornerRadius) {
            CornerRadius.System -> r
            is CornerRadius.Dp -> CornerRadius.Dp(r.value.finiteOr(0f).coerceIn(0f, MAX_CORNER_RADIUS_DP))
        },
        paddingDp = background.paddingDp.finiteOr(0f).coerceIn(0f, MAX_PADDING_DP),
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
)

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

private fun Float.unitOr(fallback: Float): Float = if (isFinite()) coerceIn(0f, 1f) else fallback
