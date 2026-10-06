package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.CornerRadius
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.HourMode
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.SizeClassResolver
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.normalized
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure requested → effective resolution for one widget instance (CLOCKY_END_STATE.md §12 resolve/).
 * The widget provider and the editor preview both render the spec this returns.
 */
object DesignResolver {
    /** View translation became remotable in API 31; earlier hosts render 0dp (contract §3). */
    const val MIN_TRANSLATION_SDK = 31

    /** API 31 added RemoteViews outline radius control; earlier hosts use drawable variants. */
    const val MIN_OUTLINE_RADIUS_SDK = 31

    /** Corner radii available as drawable variants below API 31. */
    val legacyRadiusVariantsDp: List<Float> = listOf(0f, 8f, 16f, 24f, 32f, 48f)

    /** Pre-31 stand-in for "Match system": the framework's default widget radius. */
    const val LEGACY_SYSTEM_RADIUS_DP = 16f

    fun resolve(
        design: DigitalDesign,
        size: SizeContext,
        env: RenderEnvironment,
    ): ResolvedDigitalSpec {
        val d = design.normalized()
        val sizeClass = SizeClassResolver.resolve(size.minHeightDp)
        val patch = d.layout.patchFor(sizeClass)
        val degradations = mutableListOf<Degradation>()

        val timeStyle = d.time.style.copy(
            weight = patch.timeWeight ?: d.time.style.weight,
            sizeSp = patch.timeSizeSp ?: d.time.style.sizeSp,
            xDp = patch.timeXDp ?: d.time.style.xDp,
            yDp = patch.timeYDp ?: d.time.style.yDp,
        )
        val dateStyle = d.date.style.copy(
            weight = patch.dateWeight ?: d.date.style.weight,
            sizeSp = patch.dateSizeSp ?: d.date.style.sizeSp,
            xDp = patch.dateXDp ?: d.date.style.xDp,
            yDp = patch.dateYDp ?: d.date.style.yDp,
        )
        val dateVisible = patch.dateVisible ?: d.date.visible

        return ResolvedDigitalSpec(
            sizeClass = sizeClass,
            time = resolveText(TextElementKind.TIME, timeStyle, size, env, degradations),
            timeFormats = timeFormatsFor(d.behavior.hourMode, d.time.leadingZero),
            date = resolveText(TextElementKind.DATE, dateStyle, size, env, degradations.takeIf { dateVisible }),
            dateVisible = dateVisible,
            datePattern = d.date.formatPattern ?: env.localeAutoDatePattern,
            background = resolveBackground(d, env, degradations),
            paddingDp = d.background.paddingDp,
            degradations = degradations.toList(),
        )
    }

    /**
     * Explicit hour modes force one format (PR #28 behavior). Follow-system keeps both and lets
     * TextClock pick. 24h is always `HH` (End-State §5.1; the AOSP `kk` showed 24:05 after
     * midnight). 12h uses `h`, or `hh` with leading zero (contract §8).
     */
    fun timeFormatsFor(hourMode: HourMode, leadingZero: Boolean): TimeFormats {
        val twelve = if (leadingZero) "hh:mm" else "h:mm"
        val twentyFour = "HH:mm"
        return when (hourMode) {
            HourMode.FOLLOW_SYSTEM -> TimeFormats(twelve, twentyFour)
            HourMode.FORCE_12_HOUR -> TimeFormats(twelve, twelve)
            HourMode.FORCE_24_HOUR -> TimeFormats(twentyFour, twentyFour)
        }
    }

    fun effectiveArgb(color: ColorRef, opacity: Float): Int = when (color) {
        is ColorRef.Fixed -> (alphaOf(opacity) shl 24) or color.rgb
    }

    fun alphaOf(opacity: Float): Int = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()

    /**
     * Degradations for a hidden element are not reported (nothing is rendered), so [degradations]
     * may be null.
     */
    private fun resolveText(
        kind: TextElementKind,
        style: TextStyle,
        size: SizeContext,
        env: RenderEnvironment,
        degradations: MutableList<Degradation>?,
    ): ResolvedText {
        val font = FontCatalog.resolve(style.fontId, style.weight, env.sdkInt)
        if (font.requestedWeight != font.effectiveWeight) {
            degradations?.add(Degradation.WeightApproximated(kind, font.requestedWeight, font.effectiveWeight))
        }
        font.fontFallbackReason?.let {
            degradations?.add(Degradation.FontFallback(kind, style.fontId, it))
        }

        val requestedNonZero = style.xDp != 0f || style.yDp != 0f
        var xDp = 0f
        var yDp = 0f
        if (env.sdkInt < MIN_TRANSLATION_SDK) {
            if (requestedNonZero) degradations?.add(Degradation.OffsetUnsupported(kind, MIN_TRANSLATION_SDK))
        } else {
            // Contract §2/§3: X follows writing direction; clamp to ±50% of the host size only here.
            val directionalX = if (env.isRtl) -style.xDp else style.xDp
            xDp = clampHalf(directionalX, size.minWidthDp)
            yDp = clampHalf(style.yDp, size.minHeightDp)
            if (xDp != directionalX || yDp != style.yDp) degradations?.add(Degradation.OffsetClamped(kind))
        }

        return ResolvedText(
            face = font.face,
            sizeSp = style.sizeSp,
            letterSpacingEm = style.letterSpacingEm,
            argb = effectiveArgb(style.color, style.opacity),
            alignment = style.alignment,
            xDp = xDp,
            yDp = yDp,
        )
    }

    private fun clampHalf(value: Float, extentDp: Int): Float {
        if (extentDp <= 0) return value
        val half = extentDp / 2f
        return value.coerceIn(-half, half)
    }

    private fun resolveBackground(
        d: DigitalDesign,
        env: RenderEnvironment,
        degradations: MutableList<Degradation>,
    ): ResolvedBackground {
        val bg = d.background
        val visible = bg.type == BackgroundType.SOLID
        val rgb = when (val c = bg.color) {
            is ColorRef.Fixed -> c.rgb
        }
        val radius: ResolvedRadius = if (env.sdkInt >= MIN_OUTLINE_RADIUS_SDK) {
            when (val r = bg.cornerRadius) {
                CornerRadius.System -> ResolvedRadius.System
                is CornerRadius.Dp -> ResolvedRadius.Dp(r.value)
            }
        } else {
            val requested = (bg.cornerRadius as? CornerRadius.Dp)?.value
            val effective = nearestLegacyVariant(requested ?: LEGACY_SYSTEM_RADIUS_DP)
            if (visible && (requested == null || requested != effective)) {
                degradations.add(Degradation.RadiusApproximated(requested, effective))
            }
            ResolvedRadius.Dp(effective)
        }
        return ResolvedBackground(
            visible = visible,
            rgb = rgb,
            alpha = alphaOf(bg.opacity),
            radius = radius,
        )
    }

    fun nearestLegacyVariant(requestedDp: Float): Float =
        legacyRadiusVariantsDp.minWith(compareBy<Float> { abs(it - requestedDp) }.thenBy { it })

    /** Convenience for callers that only need the class (e.g. editor labels). */
    fun sizeClassOf(size: SizeContext): SizeClass = SizeClassResolver.resolve(size.minHeightDp)
}
