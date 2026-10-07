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
        val patch = scope.sizeClass?.let { d.layout.inheritedPatchFor(it) }
        val overridden = when (target) {
            TextTarget.TIME -> patch?.timeWeight
            TextTarget.DATE -> patch?.dateWeight
            TextTarget.INFO -> null
        }
        return overridden ?: tokenAwareWeight(d, target)
    }

    fun sizeOf(d: DigitalDesign, target: TextTarget, scope: EditScope): Float {
        val patch = scope.sizeClass?.let { d.layout.inheritedPatchFor(it) }
        val overridden = when (target) {
            TextTarget.TIME -> patch?.timeSizeSp
            TextTarget.DATE -> patch?.dateSizeSp
            TextTarget.INFO -> patch?.infoSizeSp
        }
        return overridden ?: style(d, target).sizeSp
    }

    fun offsetOf(d: DigitalDesign, target: TextTarget, scope: EditScope): Pair<Float, Float> {
        val patch = scope.sizeClass?.let { d.layout.inheritedPatchFor(it) }
        val s = style(d, target)
        return when (target) {
            TextTarget.TIME -> (patch?.timeXDp ?: s.xDp) to (patch?.timeYDp ?: s.yDp)
            TextTarget.DATE -> (patch?.dateXDp ?: s.xDp) to (patch?.dateYDp ?: s.yDp)
            TextTarget.INFO -> (patch?.infoXDp ?: s.xDp) to (patch?.infoYDp ?: s.yDp)
        }
    }

    fun dateVisibleOf(d: DigitalDesign, scope: EditScope): Boolean =
        scope.sizeClass?.let { d.layout.inheritedPatchFor(it).dateVisible } ?: d.date.visible

    fun templateOf(d: DigitalDesign, scope: EditScope): Template {
        val p = scope.sizeClass?.let { d.layout.inheritedPatchFor(it) }
        if (p?.requestedTemplate != null || (p?.template == null && d.layout.requestedTemplate != null)) return Template.TIME_FIRST
        return p?.template ?: d.layout.template
    }

    fun alignmentOf(d: DigitalDesign, target: TextTarget, scope: EditScope): Alignment {
        val p = scope.sizeClass?.let { d.layout.inheritedPatchFor(it) }
        return when (target) {
            TextTarget.TIME -> p?.timeAlignment
            TextTarget.DATE -> p?.dateAlignment
            TextTarget.INFO -> p?.infoAlignment
        } ?: style(d, target).alignment
    }

    fun infoVisibleOf(d: DigitalDesign, scope: EditScope): Boolean =
        scope.sizeClass?.let { d.layout.inheritedPatchFor(it).infoVisible } ?: d.info.visible

    fun paddingOf(d: DigitalDesign, scope: EditScope): Float =
        scope.sizeClass?.let { d.layout.inheritedPatchFor(it).paddingDp } ?: d.background.paddingDp

    fun dateGapOf(d: DigitalDesign, scope: EditScope): Float =
        scope.sizeClass?.let { d.layout.inheritedPatchFor(it).dateGapDp } ?: d.date.gapDp ?: if (d.style != null) 4f else 0f

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
            OverrideField.TEMPLATE -> p.template != null || p.requestedTemplate != null
            OverrideField.TIME_ALIGNMENT -> p.timeAlignment != null
            OverrideField.DATE_ALIGNMENT -> p.dateAlignment != null
            OverrideField.INFO_SIZE -> p.infoSizeSp != null
            OverrideField.INFO_OFFSET -> p.infoXDp != null || p.infoYDp != null
            OverrideField.INFO_ALIGNMENT -> p.infoAlignment != null
            OverrideField.INFO_VISIBLE -> p.infoVisible != null
            OverrideField.PADDING -> p.paddingDp != null
            OverrideField.DATE_GAP -> p.dateGapDp != null
        }
    }

    enum class OverrideField(vararg val paths: String) {
        TIME_WEIGHT("time.weight"), DATE_WEIGHT("date.weight"),
        TIME_SIZE("time.sizeSp"), DATE_SIZE("date.sizeSp"),
        TIME_OFFSET("time.xDp", "time.yDp"), DATE_OFFSET("date.xDp", "date.yDp"),
        DATE_VISIBLE("date.visible"), TEMPLATE("layout.template"),
        TIME_ALIGNMENT("time.alignment"), DATE_ALIGNMENT("date.alignment"),
        INFO_SIZE("info.sizeSp"), INFO_OFFSET("info.xDp", "info.yDp"), INFO_ALIGNMENT("info.alignment"),
        INFO_VISIBLE("info.visible"), PADDING("background.paddingDp"), DATE_GAP("date.gapDp"),
    }

    /** Drops one override so the class inherits the base value again ("long press to revert", End-State 7). */
    fun revertOverride(d: DigitalDesign, sizeClass: SizeClass, field: OverrideField): DigitalDesign =
        patched(d, sizeClass, field) {
            when (field) {
                OverrideField.TIME_WEIGHT -> it.copy(timeWeight = null)
                OverrideField.DATE_WEIGHT -> it.copy(dateWeight = null)
                OverrideField.TIME_SIZE -> it.copy(timeSizeSp = null)
                OverrideField.DATE_SIZE -> it.copy(dateSizeSp = null)
                OverrideField.TIME_OFFSET -> it.copy(timeXDp = null, timeYDp = null)
                OverrideField.DATE_OFFSET -> it.copy(dateXDp = null, dateYDp = null)
                OverrideField.DATE_VISIBLE -> it.copy(dateVisible = null)
                OverrideField.TEMPLATE -> it.copy(template = null, requestedTemplate = null)
                OverrideField.TIME_ALIGNMENT -> it.copy(timeAlignment = null)
                OverrideField.DATE_ALIGNMENT -> it.copy(dateAlignment = null)
                OverrideField.INFO_SIZE -> it.copy(infoSizeSp = null)
                OverrideField.INFO_OFFSET -> it.copy(infoXDp = null, infoYDp = null)
                OverrideField.INFO_ALIGNMENT -> it.copy(infoAlignment = null)
                OverrideField.INFO_VISIBLE -> it.copy(infoVisible = null)
                OverrideField.PADDING -> it.copy(paddingDp = null)
                OverrideField.DATE_GAP -> it.copy(dateGapDp = null)
            }
        }

    private fun patched(d: DigitalDesign, sizeClass: SizeClass, field: OverrideField, f: (LayoutPatch) -> LayoutPatch): DigitalDesign {
        val patch = f(d.layout.patchFor(sizeClass))
        return d.copy(layout = d.layout.withPatch(sizeClass, patch.copy(preserved = patch.preserved - field.paths.toSet())))
    }

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
        require(cls == null || (cls.allowsWeightOverride && target != TextTarget.INFO)) {
            "Only Strip/Card Time/Date support weight overrides"
        }
        if (cls != null && target != TextTarget.INFO) {
            return patched(d, cls, if (target == TextTarget.TIME) OverrideField.TIME_WEIGHT else OverrideField.DATE_WEIGHT) {
                if (target == TextTarget.TIME) it.copy(timeWeight = w) else it.copy(dateWeight = w)
            }
        }
        return materializeFont(d, target).withStyle(target) { it.copy(weight = w) }
    }

    fun setSize(d: DigitalDesign, target: TextTarget, sp: Float, scope: EditScope): DigitalDesign {
        val v = sp.coerceIn(sizeRange(target))
        val cls = scope.sizeClass
        if (cls != null) {
            val field = when (target) { TextTarget.TIME -> OverrideField.TIME_SIZE; TextTarget.DATE -> OverrideField.DATE_SIZE; TextTarget.INFO -> OverrideField.INFO_SIZE }
            return patched(d, cls, field) { when (target) {
                TextTarget.TIME -> it.copy(timeSizeSp = v)
                TextTarget.DATE -> it.copy(dateSizeSp = v)
                TextTarget.INFO -> it.copy(infoSizeSp = v)
            } }
        }
        return d.withStyle(target) { it.copy(sizeSp = v) }
    }

    fun setLetterSpacing(d: DigitalDesign, target: TextTarget, em: Float): DigitalDesign =
        d.withStyle(target) { it.copy(letterSpacingEm = em.coerceIn(letterSpacingEm)) }

    fun setColor(d: DigitalDesign, target: TextTarget, color: ColorRef): DigitalDesign =
        d.withStyle(target) { it.copy(color = color) }

    fun setOpacity(d: DigitalDesign, target: TextTarget, opacity: Float): DigitalDesign =
        d.withStyle(target) { it.copy(opacity = opacity.coerceIn(0f, 1f)) }

    fun setAlignment(d: DigitalDesign, target: TextTarget, alignment: Alignment, scope: EditScope = EditScope.ALL): DigitalDesign {
        val cls = scope.sizeClass ?: return d.withStyle(target) { it.copy(alignment = alignment) }
        val field = when (target) { TextTarget.TIME -> OverrideField.TIME_ALIGNMENT; TextTarget.DATE -> OverrideField.DATE_ALIGNMENT; TextTarget.INFO -> OverrideField.INFO_ALIGNMENT }
        return patched(d, cls, field) { when (target) {
            TextTarget.TIME -> it.copy(timeAlignment = alignment)
            TextTarget.DATE -> it.copy(dateAlignment = alignment)
            TextTarget.INFO -> it.copy(infoAlignment = alignment)
        } }
    }

    fun setOffset(d: DigitalDesign, target: TextTarget, xDp: Float, yDp: Float, scope: EditScope): DigitalDesign {
        require(xDp.isFinite() && yDp.isFinite()) { "Offsets must be finite" }
        // ±half-widget clamping belongs to the effective resolver, never the saved request.
        val x = xDp
        val y = yDp
        val cls = scope.sizeClass
        if (cls != null) {
            val field = when (target) { TextTarget.TIME -> OverrideField.TIME_OFFSET; TextTarget.DATE -> OverrideField.DATE_OFFSET; TextTarget.INFO -> OverrideField.INFO_OFFSET }
            return patched(d, cls, field) { when (target) {
                TextTarget.TIME -> it.copy(timeXDp = x, timeYDp = y)
                TextTarget.DATE -> it.copy(dateXDp = x, dateYDp = y)
                TextTarget.INFO -> it.copy(infoXDp = x, infoYDp = y)
            }
            }
        }
        return d.withStyle(target) { it.copy(xDp = x, yDp = y) }
    }

    // ---- date ----

    fun setDateVisible(d: DigitalDesign, visible: Boolean, scope: EditScope): DigitalDesign {
        val cls = scope.sizeClass
        return if (cls != null) patched(d, cls, OverrideField.DATE_VISIBLE) { it.copy(dateVisible = visible) } else d.copy(date = d.date.copy(visible = visible))
    }

    fun setDateGap(d: DigitalDesign, dp: Float, scope: EditScope): DigitalDesign {
        val v = dp.coerceIn(-20f, 40f)
        val cls = scope.sizeClass ?: return d.copy(date = d.date.copy(gapDp = v))
        return patched(d, cls, OverrideField.DATE_GAP) { it.copy(dateGapDp = v) }
    }

    fun setInfoVisible(d: DigitalDesign, visible: Boolean, scope: EditScope): DigitalDesign {
        val cls = scope.sizeClass ?: return d.copy(info = d.info.copy(visible = visible))
        return patched(d, cls, OverrideField.INFO_VISIBLE) { it.copy(infoVisible = visible) }
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

    fun setPadding(d: DigitalDesign, dp: Float, scope: EditScope = EditScope.ALL): DigitalDesign {
        val v = dp.coerceIn(paddingDp)
        val cls = scope.sizeClass ?: return d.copy(background = d.background.copy(paddingDp = v))
        return patched(d, cls, OverrideField.PADDING) { it.copy(paddingDp = v) }
    }

    fun setBorderWidth(d: DigitalDesign, dp: Float): DigitalDesign =
        d.copy(background = d.background.copy(borderWidthDp = dp.coerceIn(borderDp)))

    fun setShadow(d: DigitalDesign, level: ShadowLevel): DigitalDesign = d.copy(effects = d.effects.copy(shadow = level))

    // ---- layout and behavior ----

    fun setTemplate(d: DigitalDesign, template: Template, scope: EditScope): DigitalDesign {
        val cls = scope.sizeClass
        return if (cls != null) patched(d, cls, OverrideField.TEMPLATE) { it.copy(template = template, requestedTemplate = null) }
        else d.copy(layout = d.layout.copy(template = template, requestedTemplate = null))
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
            layout = resetKnownLayout(d.layout, reference.layout),
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

    /** Reset fields this version understands without deleting future classes or unrelated paths. */
    private fun resetKnownLayout(current: com.stupidsavacan.clocky.design.model.DesignLayout,
        reference: com.stupidsavacan.clocky.design.model.DesignLayout): com.stupidsavacan.clocky.design.model.DesignLayout {
        var result = reference.copy(preservedOverrides = reference.preservedOverrides + current.preservedOverrides)
        current.overrides.forEach { (c, p) ->
            val knownPaths = OverrideField.entries.filter {
                c.allowsWeightOverride || (it != OverrideField.TIME_WEIGHT && it != OverrideField.DATE_WEIGHT)
            }.flatMap { it.paths.toList() }.toSet()
            val future = p.preserved.filterKeys { it !in knownPaths }
            if (future.isNotEmpty()) {
                val replacement = result.patchFor(c)
                result = result.withPatch(c, replacement.copy(preserved = future + replacement.preserved))
            }
        }
        return result
    }

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
                    p.copy(timeSizeSp = p.timeSizeSp?.times(scale), dateSizeSp = p.dateSizeSp?.times(scale), infoSizeSp = p.infoSizeSp?.times(scale))
                },
            ),
            style = tokens.copy(textSize = TextSizeStep.MEDIUM),
        )
    }

    /** The design a session resets to: the built-in it was created from (prepared like the draft), else null. */
    fun presetReference(d: DigitalDesign): DigitalDesign? =
        BuiltinDesigns.byId(d.source?.builtinId)?.instantiate()?.let { prepare(it) }
}
