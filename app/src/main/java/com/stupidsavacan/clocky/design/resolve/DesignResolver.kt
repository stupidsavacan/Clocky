package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.AmPmMode
import com.stupidsavacan.clocky.design.model.InfoSource
import com.stupidsavacan.clocky.design.model.ShadowLevel
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.FontSpec
import com.stupidsavacan.clocky.design.model.Palette
import com.stupidsavacan.clocky.design.model.PaletteColors
import com.stupidsavacan.clocky.design.model.StyleTokens
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.model.ThemeMode
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

    /** RemoteViews.setColorInt(notNight, night) and setColor(@ColorRes) arrived in API 31. */
    const val MIN_THEME_BINDING_SDK = 31

    private const val TEMPLATE_GAP_DP = 4f

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

        val tokens = d.style
        val template = patch.template ?: d.layout.template
        val sizeScale = tokens?.textSize?.scale ?: 1f
        val dateVisible = (patch.dateVisible ?: d.date.visible) && template != Template.MINIMAL

        var timeStyle = d.time.style.withTokenFont(tokens, patch.timeWeight).copy(
            sizeSp = (patch.timeSizeSp ?: d.time.style.sizeSp) * sizeScale,
            xDp = patch.timeXDp ?: d.time.style.xDp,
            yDp = patch.timeYDp ?: d.time.style.yDp,
        )
        var dateStyle = d.date.style.withTokenFont(tokens, patch.dateWeight).copy(
            sizeSp = (patch.dateSizeSp ?: d.date.style.sizeSp) * sizeScale,
            xDp = patch.dateXDp ?: d.date.style.xDp,
            yDp = patch.dateYDp ?: d.date.style.yDp,
        )
        if (template == Template.SPLIT) {
            // Split is defined by its diagonal: the template, not the element, decides alignment.
            timeStyle = timeStyle.copy(alignment = Alignment.END)
            dateStyle = dateStyle.copy(alignment = Alignment.START)
        }
        val themeNotes = linkedSetOf<Degradation>()
        val shadow = d.effects.shadow

        val time = resolveText(TextElementKind.TIME, timeStyle, size, env, degradations, tokens, themeNotes, shadow)
        return ResolvedDigitalSpec(
            sizeClass = sizeClass,
            template = template,
            time = time,
            timeFormats = timeFormatsFor(d.behavior.hourMode, d.time.leadingZero, d.behavior.showSeconds),
            amPm = resolveAmPm(d, time),
            info = resolveInfo(d, sizeClass, template, size, env, degradations, tokens, themeNotes, sizeScale),
            taps = d.behavior.tap,
            date = resolveText(
                TextElementKind.DATE, dateStyle, size, env, degradations.takeIf { dateVisible }, tokens,
                themeNotes.takeIf { dateVisible }, shadow,
            ),
            dateVisible = dateVisible,
            dateUppercase = d.date.uppercase,
            datePattern = d.date.formatPattern ?: env.localeAutoDatePattern,
            background = resolveBackground(d, env, degradations, tokens, themeNotes),
            paddingDp = d.background.paddingDp,
            gapDp = if (tokens != null) TEMPLATE_GAP_DP else 0f,
            degradations = degradations.toList() + themeNotes,
        )
    }

    /**
     * AM/PM is a second TextClock (pattern `a`) next to the time. It exists only when the clock can
     * be 12-hour: Force 24-hour keeps the request but renders nothing.
     */
    private fun resolveAmPm(d: DigitalDesign, time: ResolvedText): ResolvedAmPm? {
        val style = d.behavior.amPm
        if (style.mode != AmPmMode.SUFFIX || d.behavior.hourMode == HourMode.FORCE_24_HOUR) return null
        return ResolvedAmPm(
            text = time.copy(sizeSp = time.sizeSp * style.scale, xDp = 0f, yDp = 0f),
            scale = style.scale,
            format24Hour = if (d.behavior.hourMode == HourMode.FORCE_12_HOUR) "a" else "",
        )
    }

    /**
     * The Info line is one extra TextClock row below the date. Next alarm is a literal pattern of the
     * text AlarmManager reported (the app re-renders when the alarm changes, not every minute);
     * second timezone is a real TextClock in that zone and keeps ticking in the host.
     */
    private fun resolveInfo(
        d: DigitalDesign,
        sizeClass: SizeClass,
        template: Template,
        size: SizeContext,
        env: RenderEnvironment,
        degradations: MutableList<Degradation>,
        tokens: StyleTokens?,
        themeNotes: MutableSet<Degradation>,
        sizeScale: Float,
    ): ResolvedInfo? {
        val info = d.info
        if (info.source == InfoSource.NONE) return null
        if (sizeClass == SizeClass.STRIP) {
            degradations.add(Degradation.InfoNotShown(InfoHiddenReason.STRIP_SIZE))
            return null
        }
        if (template == Template.MINIMAL) {
            degradations.add(Degradation.InfoNotShown(InfoHiddenReason.MINIMAL_TEMPLATE))
            return null
        }
        val style = info.style.withTokenFont(tokens, null).copy(sizeSp = info.style.sizeSp * sizeScale)
        val text = resolveText(TextElementKind.INFO, style, size, env, degradations, tokens, themeNotes, d.effects.shadow)
        return when (info.source) {
            InfoSource.NEXT_ALARM -> {
                val shown = env.nextAlarmText ?: env.sampleAlarmText ?: return null
                val pattern = quotePattern(shown)
                ResolvedInfo(info.source, text, pattern, pattern, null, isSample = env.nextAlarmText == null)
            }
            InfoSource.SECOND_TIMEZONE -> {
                val formats = timeFormatsFor(d.behavior.hourMode, d.time.leadingZero)
                val label = info.label?.let { quotePattern(it) + " " } ?: ""
                ResolvedInfo(
                    info.source, text,
                    label + formats.format12Hour, label + formats.format24Hour,
                    info.timeZoneId ?: "UTC", isSample = false,
                )
            }
            InfoSource.NONE -> null
        }
    }

    /** Wraps free text as a literal TextClock/SimpleDateFormat pattern (a quote is doubled). */
    fun quotePattern(text: String): String = "'" + text.replace("'", "''") + "'"

    /** Neutral tokens for a Token color in a design that carries no [StyleTokens] (hand-edited JSON). */
    private val FALLBACK_TOKENS = StyleTokens(
        palette = Palette(
            id = "fallback",
            light = PaletteColors(0x1B1B1B, 0x3C3C42, 0x1B1B1B, 0xFFFFFF),
            dark = PaletteColors(0xFFFFFF, 0xE6E6E8, 0xFFFFFF, 0x1B1B1B),
        ),
        fontPrimary = FontSpec(FontIds.SYSTEM_SANS, 400),
        fontSecondary = FontSpec(FontIds.SYSTEM_SANS, 400),
    )

    private fun TextStyle.withTokenFont(tokens: StyleTokens?, patchWeight: Int?): TextStyle {
        val spec = when (fontId) {
            FontIds.TOKEN_PRIMARY -> (tokens ?: FALLBACK_TOKENS).fontPrimary
            FontIds.TOKEN_SECONDARY -> (tokens ?: FALLBACK_TOKENS).fontSecondary
            else -> return copy(weight = patchWeight ?: weight)
        }
        return copy(fontId = spec.fontId, weight = patchWeight ?: spec.weight)
    }

    private data class ColorResult(val argb: Int, val binding: ColorBinding?)

    /**
     * Fixed colors are static. Token colors follow the theme mode (End-State 5.5): FIXED shows one
     * palette variant; FOLLOW_SYSTEM and MATERIAL_YOU bind to the host's own night/system colors on
     * API 31+ so a theme or wallpaper change needs no app update, and degrade to a static variant
     * below that. The requested mode is never rewritten; only the effective color differs.
     */
    private fun resolveColor(
        ref: ColorRef,
        opacity: Float,
        tokens: StyleTokens?,
        env: RenderEnvironment,
        notes: MutableSet<Degradation>?,
    ): ColorResult {
        val alpha = alphaOf(opacity)
        val withAlpha = { rgb: Int -> (alpha shl 24) or rgb }
        when (ref) {
            is ColorRef.Fixed -> return ColorResult(withAlpha(ref.rgb), null)
            is ColorRef.Token -> {
                val t = tokens ?: FALLBACK_TOKENS
                val light = t.palette.light.of(ref.role)
                val dark = t.palette.dark.of(ref.role)
                val night = if (env.isNight) dark else light
                val canBind = env.sdkInt >= MIN_THEME_BINDING_SDK
                return when (t.themeMode) {
                    ThemeMode.FIXED ->
                        ColorResult(withAlpha(t.palette.variant(t.palette.fixedVariant).of(ref.role)), null)
                    ThemeMode.FOLLOW_SYSTEM -> {
                        if (!canBind) notes?.add(Degradation.ThemeSwitchUnavailable)
                        ColorResult(
                            withAlpha(night),
                            if (canBind) ColorBinding.DayNight(withAlpha(light), withAlpha(dark)) else null,
                        )
                    }
                    ThemeMode.MATERIAL_YOU -> {
                        if (!canBind) notes?.add(Degradation.DynamicColorUnavailable)
                        ColorResult(withAlpha(night), if (canBind) ColorBinding.SystemRole(ref.role) else null)
                    }
                }
            }
        }
    }

    /**
     * Explicit hour modes force one format (PR #28 behavior). Follow-system keeps both and lets
     * TextClock pick. 24h is always `HH` (End-State §5.1; the AOSP `kk` showed 24:05 after
     * midnight). 12h uses `h`, or `hh` with leading zero (contract §8).
     */
    fun timeFormatsFor(hourMode: HourMode, leadingZero: Boolean, seconds: Boolean = false): TimeFormats {
        val tail = if (seconds) ":ss" else ""
        val twelve = (if (leadingZero) "hh:mm" else "h:mm") + tail
        val twentyFour = "HH:mm$tail"
        return when (hourMode) {
            HourMode.FOLLOW_SYSTEM -> TimeFormats(twelve, twentyFour)
            HourMode.FORCE_12_HOUR -> TimeFormats(twelve, twelve)
            HourMode.FORCE_24_HOUR -> TimeFormats(twentyFour, twentyFour)
        }
    }

    fun effectiveArgb(color: ColorRef, opacity: Float): Int = when (color) {
        is ColorRef.Fixed -> (alphaOf(opacity) shl 24) or color.rgb
        is ColorRef.Token -> error("Token colors need the design's tokens; resolve through DesignResolver.resolve")
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
        tokens: StyleTokens?,
        themeNotes: MutableSet<Degradation>?,
        shadowLevel: ShadowLevel = ShadowLevel.CLASSIC,
    ): ResolvedText {
        val font = FontCatalog.resolve(style.fontId, style.weight, env.sdkInt)
        if (font.requestedWeight != font.effectiveWeight) {
            val fontId = style.fontId.takeIf { FontCatalog.family(it)?.source == FontCatalog.Source.BUNDLED && font.fontFallbackReason == null }
            degradations?.add(Degradation.WeightApproximated(kind, font.requestedWeight, font.effectiveWeight, fontId))
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

        val color = resolveColor(style.color, style.opacity, tokens, env, themeNotes)
        return ResolvedText(
            face = font.face,
            sizeSp = style.sizeSp,
            letterSpacingEm = style.letterSpacingEm,
            argb = color.argb,
            alignment = style.alignment,
            xDp = xDp,
            yDp = yDp,
            opacity = style.opacity,
            binding = color.binding,
            shadow = shadowVariant(shadowLevel, color.argb),
        )
    }

    /** SOFT/STRONG pick a black shadow for light text and a white one for dark text (End-State 5.6). */
    fun shadowVariant(level: ShadowLevel, textArgb: Int): ShadowVariant {
        val light = Contrast.luminance(textArgb) > 0.5
        return when (level) {
            ShadowLevel.CLASSIC -> ShadowVariant.CLASSIC
            ShadowLevel.OFF -> ShadowVariant.OFF
            ShadowLevel.SOFT -> if (light) ShadowVariant.SOFT_DARK else ShadowVariant.SOFT_LIGHT
            ShadowLevel.STRONG -> if (light) ShadowVariant.STRONG_DARK else ShadowVariant.STRONG_LIGHT
        }
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
        tokens: StyleTokens?,
        themeNotes: MutableSet<Degradation>,
    ): ResolvedBackground {
        val bg = d.background
        val visible = bg.type != BackgroundType.NONE
        val rendered = bg.type == BackgroundType.GRADIENT || bg.type == BackgroundType.OUTLINE
        // Opacity travels separately (setImageAlpha), so resolve the color at full alpha.
        val color = resolveColor(bg.color, 1f, tokens, env, themeNotes.takeIf { visible })
        val end = if (bg.type == BackgroundType.GRADIENT) resolveColor(bg.gradientEnd, 1f, tokens, env, themeNotes) else color
        val rgb = color.argb and 0xFFFFFF
        // A bitmap cannot switch with the host's night mode or system palette between updates.
        if (rendered && (color.binding != null || end.binding != null)) {
            degradations.add(Degradation.RenderedBackgroundStatic)
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
            // A rendered background is a static bitmap, so it never carries a live theme binding.
            binding = if (rendered) null else color.binding,
            type = bg.type,
            endRgb = end.argb and 0xFFFFFF,
            gradientAngleDeg = bg.gradientAngleDeg,
            borderWidthDp = if (bg.type == BackgroundType.OUTLINE) bg.borderWidthDp else 0f,
        )
    }

    fun nearestLegacyVariant(requestedDp: Float): Float =
        legacyRadiusVariantsDp.minWith(compareBy<Float> { abs(it - requestedDp) }.thenBy { it })

    /** Convenience for callers that only need the class (e.g. editor labels). */
    fun sizeClassOf(size: SizeContext): SizeClass = SizeClassResolver.resolve(size.minHeightDp)
}
