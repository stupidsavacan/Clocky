package com.stupidsavacan.clocky.studio

import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.AmPmMode
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.CornerRadius
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.HourMode
import com.stupidsavacan.clocky.design.model.InfoSource
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.MAX_AM_PM_SCALE
import com.stupidsavacan.clocky.design.model.MAX_BORDER_DP
import com.stupidsavacan.clocky.design.model.MAX_CORNER_RADIUS_DP
import com.stupidsavacan.clocky.design.model.MAX_INFO_LABEL
import com.stupidsavacan.clocky.design.model.MAX_PADDING_DP
import com.stupidsavacan.clocky.design.model.MAX_WEIGHT
import com.stupidsavacan.clocky.design.model.MIN_AM_PM_SCALE
import com.stupidsavacan.clocky.design.model.MIN_WEIGHT
import com.stupidsavacan.clocky.design.model.ShadowLevel
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.TapAction
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.model.TextSizeStep
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.ThemeMode
import com.stupidsavacan.clocky.design.model.normalized
import com.stupidsavacan.clocky.design.library.BuiltinDesigns

/** The three text elements Studio edits (Info is the Phase 2 element). */
enum class TextTarget { TIME, DATE, INFO }

/** The semantic slots of the Studio UI (End-State 7). */
enum class Slot { TIME, DATE, INFO, BACKGROUND, LAYOUT, BEHAVIOR }

/** Which tap zone a [TapAction] belongs to. */
enum class TapZone { TIME, DATE, INFO }

/**
 * Where a size/weight/position/visibility/template edit is written: [ALL] writes the design's base
 * value; [sizeClass] writes that class's override patch, leaving every other class inheriting.
 */
data class EditScope(val sizeClass: SizeClass? = null) {
    companion object {
        val ALL = EditScope(null)
    }
}

/**
 * Every Studio operation as a pure `DigitalDesign -> DigitalDesign` function over the *requested*
 * model. None of them look at the device: degradation is the resolver's job, so a request is never
 * rewritten because this SDK or launcher cannot show it (principle 5).
 */
object DesignEdits {
    // ---- ranges shared by the UI and the numeric-entry validation ----

    val timeSizeSp = 12f..256f
    val dateSizeSp = 8f..96f
    val infoSizeSp = 8f..48f
    val letterSpacingEm = -0.2f..0.5f
    val opacityPercent = 0..100
    val offsetDp = -200f..200f
    val weightRange = MIN_WEIGHT..MAX_WEIGHT
    val paddingDp = 0f..MAX_PADDING_DP
    val radiusDp = 0f..MAX_CORNER_RADIUS_DP
    val borderDp = 0f..MAX_BORDER_DP
    val amPmPercent = (MIN_AM_PM_SCALE * 100).toInt()..(MAX_AM_PM_SCALE * 100).toInt()

    fun sizeRange(target: TextTarget): ClosedFloatingPointRange<Float> = when (target) {
        TextTarget.TIME -> timeSizeSp
        TextTarget.DATE -> dateSizeSp
        TextTarget.INFO -> infoSizeSp
    }

    // ---- reading (what the editor shows for the scope being edited) ----

    fun style(d: DigitalDesign, target: TextTarget): TextStyle = when (target) {
        TextTarget.TIME -> d.time.style
        TextTarget.DATE -> d.date.style
        TextTarget.INFO -> d.info.style
    }

    private fun DigitalDesign.withStyle(target: TextTarget, f: (TextStyle) -> TextStyle): DigitalDesign = when (target) {
        TextTarget.TIME -> copy(time = time.copy(style = f(time.style)))
        TextTarget.DATE -> copy(date = date.copy(style = f(date.style)))
        TextTarget.INFO -> copy(info = info.copy(style = f(info.style)))
    }

    /** The design's own value for the scope (the override when one exists, else the inherited base). */
    fun weightOf(d: DigitalDesign, target: TextTarget, scope: EditScope): Int {
        val patch = scope.sizeClass?.let { d.layout.patchFor(it) }
        val overridden = when (target) {
            TextTarget.TIME -> patch?.timeWeight
            TextTarget.DATE -> patch?.dateWeight
            TextTarget.INFO -> null
        }
        return overridden ?: tokenAwareWeight(d, target)
    }

    fun sizeOf(d: DigitalDesign, target: TextTarget, scope: EditScope): Float {
        val patch = scope.sizeClass?.let { d.layout.patchFor(it) }
        val overridden = when (target) {
            TextTarget.TIME -> patch?.timeSizeSp
            TextTarget.DATE -> patch?.dateSizeSp
            TextTarget.INFO -> null
        }
        return overridden ?: style(d, target).sizeSp
    }

    fun offsetOf(d: DigitalDesign, target: TextTarget, scope: EditScope): Pair<Float, Float> {
        val patch = scope.sizeClass?.let { d.layout.patchFor(it) }
        val s = style(d, target)
        return when (target) {
            TextTarget.TIME -> (patch?.timeXDp ?: s.xDp) to (patch?.timeYDp ?: s.yDp)
            TextTarget.DATE -> (patch?.dateXDp ?: s.xDp) to (patch?.dateYDp ?: s.yDp)
            TextTarget.INFO -> s.xDp to s.yDp
        }
    }

    fun dateVisibleOf(d: DigitalDesign, scope: EditScope): Boolean =
        scope.sizeClass?.let { d.layout.patchFor(it).dateVisible } ?: d.date.visible

    fun templateOf(d: DigitalDesign, scope: EditScope): Template =
        scope.sizeClass?.let { d.layout.patchFor(it).template } ?: d.layout.template

    /** True when the scope's class overrides [field] (the editor shows the override badge). */
    fun isOverridden(d: DigitalDesign, sizeClass: SizeClass, field: OverrideField): Boolean {
        val p = d.layout.patchFor(sizeClass)
        return when (field) {
            OverrideField.TIME_WEIGHT -> p.timeWeight != null
            OverrideField.DATE_WEIGHT -> p.dateWeight != null
            OverrideField.TIME_SIZE -> p.timeSizeSp != null
            OverrideField.DATE_SIZE -> p.dateSizeSp != null
            OverrideField.TIME_OFFSET -> p.timeXDp != null || p.timeYDp != null
            OverrideField.DATE_OFFSET -> p.dateXDp != null || p.dateYDp != null
            OverrideField.DATE_VISIBLE -> p.dateVisible != null
            OverrideField.TEMPLATE -> p.template != null
        }
    }

    enum class OverrideField {
        TIME_WEIGHT, DATE_WEIGHT, TIME_SIZE, DATE_SIZE, TIME_OFFSET, DATE_OFFSET, DATE_VISIBLE, TEMPLATE,
    }

    /** Drops one override so the class inherits the base value again ("long press to revert", End-State 7). */
    fun revertOverride(d: DigitalDesign, sizeClass: SizeClass, field: OverrideField): DigitalDesign =
        patched(d, sizeClass) {
            when (field) {
                OverrideField.TIME_WEIGHT -> it.copy(timeWeight = null)
                OverrideField.DATE_WEIGHT -> it.copy(dateWeight = null)
                OverrideField.TIME_SIZE -> it.copy(timeSizeSp = null)
                OverrideField.DATE_SIZE -> it.copy(dateSizeSp = null)
                OverrideField.TIME_OFFSET -> it.copy(timeXDp = null, timeYDp = null)
                OverrideField.DATE_OFFSET -> it.copy(dateXDp = null, dateYDp = null)
                OverrideField.DATE_VISIBLE -> it.copy(dateVisible = null)
                OverrideField.TEMPLATE -> it.copy(template = null)
            }
        }

    private fun patched(d: DigitalDesign, sizeClass: SizeClass, f: (LayoutPatch) -> LayoutPatch): DigitalDesign =
        d.copy(layout = d.layout.withPatch(sizeClass, f(d.layout.patchFor(sizeClass))))

    // ---- typography ----

    /** A token font on an element comes from the design's tokens; editing it first makes it explicit. */
    private fun materializeFont(d: DigitalDesign, target: TextTarget): DigitalDesign {
        val s = style(d, target)
        if (!FontIds.isToken(s.fontId)) return d
        val tokens = d.style ?: return d.withStyle(target) { it.copy(fontId = FontIds.SYSTEM_SANS) }
        val spec = if (s.fontId == FontIds.TOKEN_PRIMARY) tokens.fontPrimary else tokens.fontSecondary
        return d.withStyle(target) { it.copy(fontId = spec.fontId, weight = spec.weight) }
    }

    private fun tokenAwareWeight(d: DigitalDesign, target: TextTarget): Int {
        val s = style(d, target)
        val tokens = d.style
        return when {
            s.fontId == FontIds.TOKEN_PRIMARY && tokens != null -> tokens.fontPrimary.weight
            s.fontId == FontIds.TOKEN_SECONDARY && tokens != null -> tokens.fontSecondary.weight
            else -> s.weight
        }
    }

    fun fontIdOf(d: DigitalDesign, target: TextTarget): String {
        val s = style(d, target)
        val tokens = d.style
        return when {
            s.fontId == FontIds.TOKEN_PRIMARY && tokens != null -> tokens.fontPrimary.fontId
            s.fontId == FontIds.TOKEN_SECONDARY && tokens != null -> tokens.fontSecondary.fontId
            FontIds.isToken(s.fontId) -> FontIds.SYSTEM_SANS
            else -> s.fontId
        }
    }

    /** The requested weight is kept as is; the resolver approximates it for the new font and discloses that. */
    fun setFont(d: DigitalDesign, target: TextTarget, fontId: String): DigitalDesign {
        val keepWeight = tokenAwareWeight(d, target)
        return materializeFont(d, target).withStyle(target) { it.copy(fontId = fontId, weight = keepWeight) }
    }

    fun setWeight(d: DigitalDesign, target: TextTarget, weight: Int, scope: EditScope): DigitalDesign {
        val w = weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT)
        val cls = scope.sizeClass
        if (cls != null && target != TextTarget.INFO) {
            return patched(d, cls) { if (target == TextTarget.TIME) it.copy(timeWeight = w) else it.copy(dateWeight = w) }
        }
        return materializeFont(d, target).withStyle(target) { it.copy(weight = w) }
    }

    fun setSize(d: DigitalDesign, target: TextTarget, sp: Float, scope: EditScope): DigitalDesign {
        val v = sp.coerceIn(sizeRange(target))
        val cls = scope.sizeClass
        if (cls != null && target != TextTarget.INFO) {
            return patched(d, cls) { if (target == TextTarget.TIME) it.copy(timeSizeSp = v) else it.copy(dateSizeSp = v) }
        }
        return d.withStyle(target) { it.copy(sizeSp = v) }
    }

    fun setLetterSpacing(d: DigitalDesign, target: TextTarget, em: Float): DigitalDesign =
        d.withStyle(target) { it.copy(letterSpacingEm = em.coerceIn(letterSpacingEm)) }

    fun setColor(d: DigitalDesign, target: TextTarget, color: ColorRef): DigitalDesign =
        d.withStyle(target) { it.copy(color = color) }

    fun setOpacity(d: DigitalDesign, target: TextTarget, opacity: Float): DigitalDesign =
        d.withStyle(target) { it.copy(opacity = opacity.coerceIn(0f, 1f)) }

    fun setAlignment(d: DigitalDesign, target: TextTarget, alignment: Alignment): DigitalDesign =
        d.withStyle(target) { it.copy(alignment = alignment) }

    fun setOffset(d: DigitalDesign, target: TextTarget, xDp: Float, yDp: Float, scope: EditScope): DigitalDesign {
        val x = xDp.coerceIn(offsetDp)
        val y = yDp.coerceIn(offsetDp)
        val cls = scope.sizeClass
        if (cls != null && target != TextTarget.INFO) {
            return patched(d, cls) {
                if (target == TextTarget.TIME) it.copy(timeXDp = x, timeYDp = y) else it.copy(dateXDp = x, dateYDp = y)
            }
        }
        return d.withStyle(target) { it.copy(xDp = x, yDp = y) }
    }

    // ---- date ----

    fun setDateVisible(d: DigitalDesign, visible: Boolean, scope: EditScope): DigitalDesign {
        val cls = scope.sizeClass
        return if (cls != null) patched(d, cls) { it.copy(dateVisible = visible) } else d.copy(date = d.date.copy(visible = visible))
    }

    /** `null` is Locale Auto (contract 6); anything else is an explicit ICU pattern. */
    fun setDateFormat(d: DigitalDesign, pattern: String?): DigitalDesign =
        d.copy(date = d.date.copy(formatPattern = pattern?.trim()?.takeIf { it.isNotEmpty() }))

    fun setDateUppercase(d: DigitalDesign, uppercase: Boolean): DigitalDesign =
        d.copy(date = d.date.copy(uppercase = uppercase))

    // ---- info ----

    /**
     * Turning Info on gives it the Date's look (font, weight, color) when the design has no style
     * tokens to supply a secondary role, so a Phase 1A design never shows grey text on a light card.
     */
    fun setInfoSource(d: DigitalDesign, source: InfoSource): DigitalDesign {
        var next = d.copy(info = d.info.copy(source = source))
        if (source != InfoSource.NONE && d.info.source == InfoSource.NONE && d.style == null) {
            val date = d.date.style
            next = next.withStyle(TextTarget.INFO) {
                it.copy(fontId = date.fontId, weight = date.weight, color = date.color, opacity = date.opacity)
            }
        }
        return next
    }

    fun setTimeZone(d: DigitalDesign, zoneId: String?): DigitalDesign =
        d.copy(info = d.info.copy(timeZoneId = zoneId?.takeIf { it.isNotBlank() }))

    fun setInfoLabel(d: DigitalDesign, label: String?): DigitalDesign =
        d.copy(info = d.info.copy(label = label?.take(MAX_INFO_LABEL)?.takeIf { it.isNotBlank() }))

    // ---- background and effects ----

    fun setBackgroundType(d: DigitalDesign, type: BackgroundType): DigitalDesign =
        d.copy(background = d.background.copy(type = type))

    fun setBackgroundColor(d: DigitalDesign, color: ColorRef): DigitalDesign =
        d.copy(background = d.background.copy(color = color))

    fun setGradientEnd(d: DigitalDesign, color: ColorRef): DigitalDesign =
        d.copy(background = d.background.copy(gradientEnd = color))

    fun setGradientAngle(d: DigitalDesign, degrees: Int): DigitalDesign =
        d.copy(background = d.background.copy(gradientAngleDeg = ((degrees % 360) + 360) % 360))

    fun setBackgroundOpacity(d: DigitalDesign, opacity: Float): DigitalDesign =
        d.copy(background = d.background.copy(opacity = opacity.coerceIn(0f, 1f)))

    fun setCornerRadius(d: DigitalDesign, radius: CornerRadius): DigitalDesign = d.copy(
        background = d.background.copy(
            cornerRadius = when (radius) {
                CornerRadius.System -> radius
                is CornerRadius.Dp -> CornerRadius.Dp(radius.value.coerceIn(radiusDp))
            },
        ),
    )

    fun setPadding(d: DigitalDesign, dp: Float): DigitalDesign =
        d.copy(background = d.background.copy(paddingDp = dp.coerceIn(paddingDp)))

    fun setBorderWidth(d: DigitalDesign, dp: Float): DigitalDesign =
        d.copy(background = d.background.copy(borderWidthDp = dp.coerceIn(borderDp)))

    fun setShadow(d: DigitalDesign, level: ShadowLevel): DigitalDesign = d.copy(effects = d.effects.copy(shadow = level))

    // ---- layout and behavior ----

    fun setTemplate(d: DigitalDesign, template: Template, scope: EditScope): DigitalDesign {
        val cls = scope.sizeClass
        return if (cls != null) patched(d, cls) { it.copy(template = template) } else d.copy(layout = d.layout.copy(template = template))
    }

    fun setHourMode(d: DigitalDesign, mode: HourMode): DigitalDesign = d.copy(behavior = d.behavior.copy(hourMode = mode))

    fun setAmPmMode(d: DigitalDesign, mode: AmPmMode): DigitalDesign =
        d.copy(behavior = d.behavior.copy(amPm = d.behavior.amPm.copy(mode = mode)))

    fun setAmPmScalePercent(d: DigitalDesign, percent: Int): DigitalDesign = d.copy(
        behavior = d.behavior.copy(
            amPm = d.behavior.amPm.copy(scale = percent.coerceIn(amPmPercent) / 100f),
        ),
    )

    fun setLeadingZero(d: DigitalDesign, on: Boolean): DigitalDesign = d.copy(time = d.time.copy(leadingZero = on))

    fun setSeconds(d: DigitalDesign, on: Boolean): DigitalDesign = d.copy(behavior = d.behavior.copy(showSeconds = on))

    fun setTap(d: DigitalDesign, zone: TapZone, action: TapAction): DigitalDesign {
        val tap = d.behavior.tap
        return d.copy(
            behavior = d.behavior.copy(
                tap = when (zone) {
                    TapZone.TIME -> tap.copy(time = action)
                    TapZone.DATE -> tap.copy(date = action)
                    TapZone.INFO -> tap.copy(info = action)
                },
            ),
        )
    }

    /** Theme mode lives on the style tokens; a design without tokens has no theme to switch. */
    fun setThemeMode(d: DigitalDesign, mode: ThemeMode): DigitalDesign =
        d.style?.let { d.copy(style = it.copy(themeMode = mode)) } ?: d

    // ---- reset ----

    /**
     * Restores one slot from [reference]. Layout also restores the alignment and offsets of every
     * text element (they are placed by the layout, not by the element), and Behavior also restores
     * the leading-zero flag and the theme mode.
     */
    fun resetSlot(d: DigitalDesign, slot: Slot, reference: DigitalDesign): DigitalDesign = when (slot) {
        Slot.TIME -> d.copy(time = reference.time.copy(leadingZero = d.time.leadingZero, style = restorePlacement(reference.time.style, d.time.style)))
        Slot.DATE -> d.copy(date = reference.date.copy(style = restorePlacement(reference.date.style, d.date.style)))
        Slot.INFO -> d.copy(info = reference.info.copy(style = restorePlacement(reference.info.style, d.info.style)))
        Slot.BACKGROUND -> d.copy(background = reference.background, effects = reference.effects)
        Slot.LAYOUT -> d.copy(
            layout = reference.layout,
            time = d.time.copy(style = placementOf(reference.time.style, d.time.style)),
            date = d.date.copy(style = placementOf(reference.date.style, d.date.style)),
            info = d.info.copy(style = placementOf(reference.info.style, d.info.style)),
        )
        Slot.BEHAVIOR -> d.copy(
            behavior = reference.behavior,
            time = d.time.copy(leadingZero = reference.time.leadingZero),
            style = d.style?.let { s -> reference.style?.let { r -> s.copy(themeMode = r.themeMode) } ?: s },
        )
    }

    fun resetAll(reference: DigitalDesign): DigitalDesign = reference

    /** True when [slot] differs from [reference] (drives the "changed" dot and enables the reset button). */
    fun isSlotModified(d: DigitalDesign, slot: Slot, reference: DigitalDesign): Boolean =
        resetSlot(d, slot, reference).normalized() != d.normalized()

    // Alignment/offsets belong to Layout; every other field of the element belongs to the element's own slot.
    private fun restorePlacement(from: TextStyle, current: TextStyle): TextStyle =
        from.copy(alignment = current.alignment, xDp = current.xDp, yDp = current.yDp)

    private fun placementOf(from: TextStyle, current: TextStyle): TextStyle =
        current.copy(alignment = from.alignment, xDp = from.xDp, yDp = from.yDp)

    // ---- opening ----

    /**
     * Prepares a design for Studio: the Quick Tune text-size step is folded into the base sizes so the
     * numbers on screen are the real ones. Palette, fonts and theme mode stay tokens, so Material You
     * and Follow system survive a detailed edit.
     */
    fun prepare(d: DigitalDesign): DigitalDesign {
        val tokens = d.style ?: return d
        val scale = tokens.textSize.scale
        if (scale == 1f) return d
        fun TextStyle.scaled() = copy(sizeSp = sizeSp * scale)
        return d.copy(
            time = d.time.copy(style = d.time.style.scaled()),
            date = d.date.copy(style = d.date.style.scaled()),
            info = d.info.copy(style = d.info.style.scaled()),
            layout = d.layout.copy(
                overrides = d.layout.overrides.mapValues { (_, p) ->
                    p.copy(timeSizeSp = p.timeSizeSp?.times(scale), dateSizeSp = p.dateSizeSp?.times(scale))
                },
            ),
            style = tokens.copy(textSize = TextSizeStep.MEDIUM),
        )
    }

    /** The design a session resets to: the built-in it was created from (prepared like the draft), else null. */
    fun presetReference(d: DigitalDesign): DigitalDesign? =
        BuiltinDesigns.byId(d.source?.builtinId)?.instantiate()?.let { prepare(it) }
}
