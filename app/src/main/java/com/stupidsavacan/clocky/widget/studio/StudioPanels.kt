package com.stupidsavacan.clocky.widget.studio

import android.content.Context
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.LinearLayout
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.AmPmMode
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.CornerRadius
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.HourMode
import com.stupidsavacan.clocky.design.model.InfoSource
import com.stupidsavacan.clocky.design.model.MAX_INFO_LABEL
import com.stupidsavacan.clocky.design.model.ShadowLevel
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.TapAction
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.model.ThemeMode
import com.stupidsavacan.clocky.design.resolve.FontCatalog
import com.stupidsavacan.clocky.design.resolve.ResolvedDigitalSpec
import com.stupidsavacan.clocky.design.library.QuickTune
import com.stupidsavacan.clocky.studio.DesignEdits
import com.stupidsavacan.clocky.studio.DesignEdits.OverrideField
import com.stupidsavacan.clocky.studio.EditScope
import com.stupidsavacan.clocky.studio.Slot
import com.stupidsavacan.clocky.studio.TapZone
import com.stupidsavacan.clocky.studio.TextTarget
import com.stupidsavacan.clocky.widget.easy.DesignLabels
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** What a panel needs from the Studio screen. Panels only read state and call [edit]. */
interface StudioHost {
    val context: Context
    val design: DigitalDesign
    val reference: DigitalDesign
    val referenceIsPreset: Boolean
    val scope: EditScope
    val previewClass: SizeClass
    val advanced: Boolean

    /** The last resolved spec of the preview (null before the first render). */
    val spec: ResolvedDigitalSpec?

    /**
     * Applies one named edit. [key] merges a continuous gesture into one undo step; [rebuild] false
     * keeps the panel's views alive (a slider being dragged).
     */
    fun edit(id: String, key: String? = null, rebuild: Boolean = true, transform: (DigitalDesign) -> DigitalDesign)

    fun endGesture()
}

/** Builds the six semantic-slot panels (Time | Date | Info | Background | Layout | Behavior). */
class StudioPanels(private val host: StudioHost, container: LinearLayout) {
    private val context = host.context
    private val rows = StudioRows(context, container)
    private val d: DigitalDesign get() = host.design
    private val scope: EditScope get() = host.scope

    private var resetButton: android.widget.Button? = null
    private var builtSlot: Slot = Slot.TIME

    /** True while the Info panel shows the "sample alarm" note, so the host can rebuild when it flips. */
    var showsSampleNote: Boolean = false
        private set

    fun build(slot: Slot) {
        rows.clear()
        builtSlot = slot
        showsSampleNote = false
        when (slot) {
            Slot.TIME -> time()
            Slot.DATE -> date()
            Slot.INFO -> info()
            Slot.BACKGROUND -> background()
            Slot.LAYOUT -> layout()
            Slot.BEHAVIOR -> behavior()
        }
        resetButton(slot)
    }

    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    // ---- Time ----

    private fun time() {
        textBasics(TextTarget.TIME, OverrideField.TIME_WEIGHT, OverrideField.TIME_SIZE)
        if (host.advanced) rows.header(str(R.string.clocky_studio_advanced))
        if (host.advanced) letterSpacing(TextTarget.TIME)
    }

    // ---- Date ----

    private fun date() {
        val visible = DesignEdits.dateVisibleOf(d, scope)
        rows.switch(str(R.string.clocky_studio_date_show), visible) { on ->
            host.edit("date.visible") { DesignEdits.setDateVisible(it, on, scope) }
        }
        scope.sizeClass?.let { cls -> overrideBadge(cls, OverrideField.DATE_VISIBLE) }
        if (!visible) return

        val presets = DATE_PRESETS
        val current = d.date.formatPattern
        val options: List<Pair<String?, CharSequence>> =
            listOf<Pair<String?, CharSequence>>(null to str(R.string.clocky_studio_date_auto)) +
                presets.map { it to (formatExample(it)) } +
                (if (current != null && current !in presets) listOf(current to str(R.string.clocky_studio_date_custom_chip)) else emptyList())
        rows.chips(str(R.string.clocky_studio_date_format), options, current) { picked ->
            host.edit("date.format") { DesignEdits.setDateFormat(it, picked) }
        }
        rows.chips(
            str(R.string.clocky_studio_date_case),
            listOf(true to str(R.string.clocky_studio_case_upper), false to str(R.string.clocky_studio_case_as_is)),
            d.date.uppercase,
        ) { picked -> host.edit("date.case") { DesignEdits.setDateUppercase(it, picked) } }

        textBasics(TextTarget.DATE, OverrideField.DATE_WEIGHT, OverrideField.DATE_SIZE)
        cjkNote(TextTarget.DATE)
        if (host.advanced) {
            rows.header(str(R.string.clocky_studio_advanced))
            letterSpacing(TextTarget.DATE)
            rows.textField(
                label = str(R.string.clocky_studio_date_pattern),
                value = current.orEmpty(),
                maxLength = 40,
                helper = str(R.string.clocky_studio_date_pattern_help, exampleOf(current ?: host.spec?.datePattern ?: "EEE, MMM d")),
                validate = { text -> if (text.isBlank() || validPattern(text)) null else str(R.string.clocky_studio_date_pattern_invalid) },
            ) { text -> host.edit("date.format") { DesignEdits.setDateFormat(it, text) } }
        }
    }

    private fun cjkNote(target: TextTarget) {
        val family = FontCatalog.family(DesignEdits.fontIdOf(d, target)) ?: return
        val lang = Locale.getDefault().language
        if (!family.coversCjk && lang in CJK_LANGUAGES) rows.note(str(R.string.clocky_studio_cjk_fallback))
    }

    // ---- Info ----

    private fun info() {
        val source = d.info.source
        rows.chips(
            str(R.string.clocky_studio_info_source),
            listOf(
                InfoSource.NONE to str(R.string.clocky_studio_info_none),
                InfoSource.NEXT_ALARM to str(R.string.clocky_studio_info_alarm),
                InfoSource.SECOND_TIMEZONE to str(R.string.clocky_studio_info_timezone),
            ),
            source,
        ) { picked -> host.edit("info.source") { DesignEdits.setInfoSource(it, picked) } }
        if (source == InfoSource.NONE) {
            rows.note(str(R.string.clocky_studio_info_none_hint))
            return
        }
        when (source) {
            InfoSource.NEXT_ALARM -> {
                rows.note(str(R.string.clocky_studio_info_alarm_hint))
                if (host.spec?.info?.isSample == true) {
                    showsSampleNote = true
                    rows.note(str(R.string.clocky_studio_info_alarm_sample), emphasized = true)
                }
            }
            InfoSource.SECOND_TIMEZONE -> timezoneControls()
            InfoSource.NONE -> Unit
        }
        textBasics(TextTarget.INFO, null, null, withWeight = host.advanced)
        if (host.advanced) {
            rows.header(str(R.string.clocky_studio_advanced))
            fontChips(TextTarget.INFO)
            letterSpacing(TextTarget.INFO)
        }
    }

    private fun timezoneControls() {
        val zones = COMMON_ZONES
        val current = d.info.timeZoneId
        val options: List<Pair<String, CharSequence>> =
            zones.map { it to cityOf(it) } +
                (if (current != null && current !in zones) listOf(current to cityOf(current)) else emptyList())
        rows.chips(str(R.string.clocky_studio_info_zone), options, current) { zone ->
            host.edit("info.zone") { design ->
                // A label that was just the previous city name follows the new city; a typed label stays.
                val previousCity = design.info.timeZoneId?.let(::cityOf)
                val withZone = DesignEdits.setTimeZone(design, zone)
                if (design.info.label == null || design.info.label == previousCity) {
                    DesignEdits.setInfoLabel(withZone, cityOf(zone))
                } else {
                    withZone
                }
            }
        }
        rows.textField(
            label = str(R.string.clocky_studio_info_zone_custom),
            value = current.orEmpty(),
            maxLength = 40,
            helper = str(R.string.clocky_studio_info_zone_help),
            validate = { text -> if (text.isBlank() || text in TimeZone.getAvailableIDs()) null else str(R.string.clocky_studio_info_zone_invalid) },
        ) { text -> host.edit("info.zone") { DesignEdits.setTimeZone(it, text) } }
        rows.textField(
            label = str(R.string.clocky_studio_info_label),
            value = d.info.label.orEmpty(),
            maxLength = MAX_INFO_LABEL,
            helper = str(R.string.clocky_studio_info_label_help),
        ) { text -> host.edit("info.label") { DesignEdits.setInfoLabel(it, text) } }
    }

    // ---- Background ----

    private fun background() {
        val bg = d.background
        val renderedTypes = listOf(BackgroundType.GRADIENT, BackgroundType.OUTLINE)
        val types = buildList {
            add(BackgroundType.NONE to str(R.string.clocky_studio_bg_none))
            add(BackgroundType.SOLID to str(R.string.clocky_studio_bg_solid))
            if (host.advanced || bg.type in renderedTypes) {
                add(BackgroundType.GRADIENT to str(R.string.clocky_studio_bg_gradient))
                add(BackgroundType.OUTLINE to str(R.string.clocky_studio_bg_outline))
            }
        }
        rows.chips(str(R.string.clocky_studio_bg_type), types, bg.type) { picked ->
            host.edit("bg.type") { DesignEdits.setBackgroundType(it, picked) }
        }
        if (bg.type == BackgroundType.NONE) {
            rows.note(str(R.string.clocky_studio_bg_none_hint))
        } else {
            val colorLabel = when (bg.type) {
                BackgroundType.GRADIENT -> R.string.clocky_studio_bg_start_color
                BackgroundType.OUTLINE -> R.string.clocky_studio_bg_outline_color
                else -> R.string.clocky_studio_color
            }
            rows.colorRow(str(colorLabel), bg.color, d.style) { c -> host.edit("bg.color") { DesignEdits.setBackgroundColor(it, c) } }
            if (bg.type == BackgroundType.GRADIENT) {
                rows.colorRow(str(R.string.clocky_studio_bg_end_color), bg.gradientEnd, d.style) { c ->
                    host.edit("bg.gradientEnd") { DesignEdits.setGradientEnd(it, c) }
                }
                rows.slider(
                    str(R.string.clocky_studio_bg_angle), bg.gradientAngleDeg.toFloat(), 0f..355f, 5f,
                    format = { "${it.roundToInt()}°" },
                    onChange = { v -> host.edit("bg.angle", "bg.angle", rebuild = false) { DesignEdits.setGradientAngle(it, v.roundToInt()) } },
                    onGestureEnd = host::endGesture,
                )
            }
            percentSlider(R.string.clocky_studio_opacity, bg.opacity, "bg.opacity") { it2, v -> DesignEdits.setBackgroundOpacity(it2, v) }
            cornerControls()
            if (host.advanced) {
                rows.header(str(R.string.clocky_studio_advanced))
                rows.slider(
                    str(R.string.clocky_studio_bg_padding), bg.paddingDp, DesignEdits.paddingDp, 1f,
                    format = { "${it.roundToInt()} dp" },
                    onChange = { v -> host.edit("bg.padding", "bg.padding", rebuild = false) { DesignEdits.setPadding(it, v) } },
                    onGestureEnd = host::endGesture,
                )
                if (bg.type == BackgroundType.OUTLINE) {
                    rows.slider(
                        str(R.string.clocky_studio_bg_border), bg.borderWidthDp, DesignEdits.borderDp, 0.5f,
                        format = { String.format(Locale.ROOT, "%.1f dp", it) },
                        onChange = { v -> host.edit("bg.border", "bg.border", rebuild = false) { DesignEdits.setBorderWidth(it, v) } },
                        onGestureEnd = host::endGesture,
                    )
                }
            }
        }
        // Legibility shadow is a design-wide effect for text on a busy wallpaper (End-State 5.6, Advanced).
        if (host.advanced) {
            rows.header(str(R.string.clocky_studio_shadow))
            rows.chips(
                null,
                listOf(
                    ShadowLevel.CLASSIC to str(R.string.clocky_studio_shadow_default),
                    ShadowLevel.OFF to str(R.string.clocky_studio_shadow_off),
                    ShadowLevel.SOFT to str(R.string.clocky_studio_shadow_soft),
                    ShadowLevel.STRONG to str(R.string.clocky_studio_shadow_strong),
                ),
                d.effects.shadow,
            ) { picked -> host.edit("effects.shadow") { DesignEdits.setShadow(it, picked) } }
            rows.note(str(R.string.clocky_studio_shadow_hint))
        }
    }

    private fun cornerControls() {
        val radius = d.background.cornerRadius
        rows.chips(
            str(R.string.clocky_studio_bg_corners),
            listOf(
                true to str(R.string.clocky_studio_bg_corners_system),
                false to str(R.string.clocky_studio_bg_corners_custom),
            ),
            radius == CornerRadius.System,
        ) { system ->
            host.edit("bg.radius") { DesignEdits.setCornerRadius(it, if (system) CornerRadius.System else CornerRadius.Dp(16f)) }
        }
        if (radius is CornerRadius.Dp) {
            rows.slider(
                str(R.string.clocky_studio_bg_radius), radius.value, DesignEdits.radiusDp, 1f,
                format = { "${it.roundToInt()} dp" },
                onChange = { v -> host.edit("bg.radius", "bg.radius", rebuild = false) { DesignEdits.setCornerRadius(it, CornerRadius.Dp(v)) } },
                onGestureEnd = host::endGesture,
            )
        }
    }

    // ---- Layout ----

    private fun layout() {
        val cls = host.previewClass
        val templates = QuickTune.templatesFor(cls)
        val current = DesignEdits.templateOf(d, scope).takeIf { it in templates } ?: DesignEdits.templateOf(d, scope)
        rows.chips(
            str(R.string.clocky_studio_layout_template),
            (templates + Template.entries.filter { it !in templates && it == current }).map { it to str(DesignLabels.template(it)) },
            current,
        ) { picked -> host.edit("layout.template") { DesignEdits.setTemplate(it, picked, scope) } }
        scope.sizeClass?.let { overrideBadge(it, OverrideField.TEMPLATE) }
        if (current == Template.SPLIT) rows.note(str(R.string.clocky_studio_layout_split_note))

        alignmentChips(R.string.clocky_studio_layout_align_time, TextTarget.TIME)
        alignmentChips(R.string.clocky_studio_layout_align_date, TextTarget.DATE)
        if (d.info.source != InfoSource.NONE) alignmentChips(R.string.clocky_studio_layout_align_info, TextTarget.INFO)

        if (host.advanced) {
            rows.header(str(R.string.clocky_studio_layout_position))
            rows.note(str(R.string.clocky_studio_layout_position_hint))
            offsetSliders(TextTarget.TIME, OverrideField.TIME_OFFSET)
            offsetSliders(TextTarget.DATE, OverrideField.DATE_OFFSET)
        }

        val overrides = d.layout.patchFor(cls)
        if (!overrides.isEmpty) {
            rows.header(str(R.string.clocky_studio_layout_overrides, sizeName(cls)))
            rows.note(str(R.string.clocky_studio_layout_overrides_hint))
            rows.button(str(R.string.clocky_studio_layout_revert, sizeName(cls))) {
                host.edit("layout.revertAll") { it.copy(layout = it.layout.withPatch(cls, com.stupidsavacan.clocky.design.model.LayoutPatch.EMPTY)) }
            }
        }
    }

    private fun alignmentChips(label: Int, target: TextTarget) {
        rows.chips(
            str(label),
            listOf(
                Alignment.START to str(R.string.clocky_studio_align_start),
                Alignment.CENTER to str(R.string.clocky_studio_align_center),
                Alignment.END to str(R.string.clocky_studio_align_end),
            ),
            DesignEdits.style(d, target).alignment,
        ) { picked -> host.edit("layout.align.${target.name}") { DesignEdits.setAlignment(it, target, picked) } }
    }

    private fun offsetSliders(target: TextTarget, field: OverrideField) {
        val (x, y) = DesignEdits.offsetOf(d, target, scope)
        val name = str(if (target == TextTarget.TIME) R.string.clocky_element_time else R.string.clocky_element_date)
        val badge = badgeFor(field)
        rows.slider(
            str(R.string.clocky_studio_layout_offset_x, name), x, DesignEdits.offsetDp, 1f,
            format = { "${it.roundToInt()} dp" },
            badge = badge?.first, onBadge = badge?.second,
            onChange = { v -> host.edit("offset.x.${target.name}", "offset.x.${target.name}", rebuild = false) {
                val (_, cy) = DesignEdits.offsetOf(it, target, scope)
                DesignEdits.setOffset(it, target, v, cy, scope)
            } },
            onGestureEnd = host::endGesture,
        )
        rows.slider(
            str(R.string.clocky_studio_layout_offset_y, name), y, DesignEdits.offsetDp, 1f,
            format = { "${it.roundToInt()} dp" },
            onChange = { v -> host.edit("offset.y.${target.name}", "offset.y.${target.name}", rebuild = false) {
                val (cx, _) = DesignEdits.offsetOf(it, target, scope)
                DesignEdits.setOffset(it, target, cx, v, scope)
            } },
            onGestureEnd = host::endGesture,
        )
    }

    // ---- Behavior ----

    private fun behavior() {
        val b = d.behavior
        rows.chips(
            str(R.string.clocky_studio_hour_mode),
            listOf(
                HourMode.FOLLOW_SYSTEM to str(R.string.clocky_studio_hour_system),
                HourMode.FORCE_12_HOUR to str(R.string.clocky_studio_hour_12),
                HourMode.FORCE_24_HOUR to str(R.string.clocky_studio_hour_24),
            ),
            b.hourMode,
        ) { picked -> host.edit("behavior.hourMode") { DesignEdits.setHourMode(it, picked) } }

        if (b.hourMode == HourMode.FORCE_24_HOUR) {
            rows.note(str(R.string.clocky_studio_ampm_24_note))
        } else {
            rows.chips(
                str(R.string.clocky_studio_ampm),
                listOf(
                    AmPmMode.HIDDEN to str(R.string.clocky_studio_ampm_hidden),
                    AmPmMode.SUFFIX to str(R.string.clocky_studio_ampm_suffix),
                ),
                b.amPm.mode,
            ) { picked -> host.edit("behavior.ampm") { DesignEdits.setAmPmMode(it, picked) } }
            if (b.hourMode == HourMode.FOLLOW_SYSTEM) rows.note(str(R.string.clocky_studio_ampm_follow_note))
        }

        d.style?.let { tokens ->
            rows.chips(
                str(R.string.clocky_studio_theme),
                listOf(
                    ThemeMode.FIXED to str(R.string.clocky_studio_theme_fixed),
                    ThemeMode.FOLLOW_SYSTEM to str(R.string.clocky_studio_theme_follow),
                    ThemeMode.MATERIAL_YOU to str(R.string.clocky_studio_theme_dynamic),
                ),
                tokens.themeMode,
            ) { picked -> host.edit("behavior.theme") { DesignEdits.setThemeMode(it, picked) } }
        }

        rows.header(str(R.string.clocky_studio_tap_title))
        rows.note(str(R.string.clocky_studio_tap_hint))
        tapChips(TapZone.TIME, R.string.clocky_element_time, b.tap.time)
        tapChips(TapZone.DATE, R.string.clocky_element_date, b.tap.date)
        if (d.info.source != InfoSource.NONE) tapChips(TapZone.INFO, R.string.clocky_element_info, b.tap.info)

        if (host.advanced) {
            rows.header(str(R.string.clocky_studio_advanced))
            rows.switch(str(R.string.clocky_studio_leading_zero), d.time.leadingZero, str(R.string.clocky_studio_leading_zero_hint)) { on ->
                host.edit("behavior.leadingZero") { DesignEdits.setLeadingZero(it, on) }
            }
            rows.switch(str(R.string.clocky_studio_seconds), b.showSeconds, str(R.string.clocky_studio_seconds_hint)) { on ->
                host.edit("behavior.seconds") { DesignEdits.setSeconds(it, on) }
            }
            if (b.amPm.mode == AmPmMode.SUFFIX && b.hourMode != HourMode.FORCE_24_HOUR) {
                rows.slider(
                    str(R.string.clocky_studio_ampm_size), b.amPm.scale * 100f, DesignEdits.amPmPercent.first.toFloat()..DesignEdits.amPmPercent.last.toFloat(), 1f,
                    format = { "${it.roundToInt()}%" },
                    onChange = { v -> host.edit("behavior.ampmScale", "behavior.ampmScale", rebuild = false) { DesignEdits.setAmPmScalePercent(it, v.roundToInt()) } },
                    onGestureEnd = host::endGesture,
                )
            }
        }
    }

    private fun tapChips(zone: TapZone, label: Int, current: TapAction) {
        rows.chips(
            str(label),
            TapAction.entries.map { it to str(DesignLabels.tapAction(it)) },
            current,
        ) { picked -> host.edit("behavior.tap.${zone.name}") { DesignEdits.setTap(it, zone, picked) } }
    }

    // ---- shared text controls ----

    private fun textBasics(target: TextTarget, weightField: OverrideField?, sizeField: OverrideField?, withWeight: Boolean = true) {
        fontChips(target)
        if (withWeight) {
            val weight = DesignEdits.weightOf(d, target, scope)
            val badge = weightField?.let { badgeFor(it) }
            rows.slider(
                str(R.string.clocky_studio_weight), weight.toFloat(), 100f..900f, 10f,
                format = { it.roundToInt().toString() },
                parse = { it.trim().toIntOrNull()?.toFloat() },
                badge = badge?.first, onBadge = badge?.second,
                onChange = { v -> host.edit("weight.${target.name}", "weight.${target.name}", rebuild = false) {
                    DesignEdits.setWeight(it, target, v.roundToInt(), scope)
                } },
                onGestureEnd = host::endGesture,
            )
            weightDisclosure(target, weight)
        }
        val size = DesignEdits.sizeOf(d, target, scope)
        val badge = sizeField?.let { badgeFor(it) }
        rows.slider(
            str(R.string.clocky_studio_size), size, DesignEdits.sizeRange(target), 1f,
            format = { "${it.roundToInt()} sp" },
            badge = badge?.first, onBadge = badge?.second,
            onChange = { v -> host.edit("size.${target.name}", "size.${target.name}", rebuild = false) {
                DesignEdits.setSize(it, target, v, scope)
            } },
            onGestureEnd = host::endGesture,
        )
        rows.colorRow(str(R.string.clocky_studio_color), DesignEdits.style(d, target).color, d.style) { c ->
            host.edit("color.${target.name}") { DesignEdits.setColor(it, target, c) }
        }
        percentSlider(R.string.clocky_studio_opacity, DesignEdits.style(d, target).opacity, "opacity.${target.name}") { design, v ->
            DesignEdits.setOpacity(design, target, v)
        }
    }

    /** Requested weight vs what this font and Android version can show (principle 5), next to the control. */
    private fun weightDisclosure(target: TextTarget, requested: Int) {
        val fontId = DesignEdits.fontIdOf(d, target)
        val family = FontCatalog.family(fontId) ?: return
        // Host fallback is disclosed by its own notice; a weight note for the stand-in face would repeat it.
        if (host.spec?.degradations?.any {
                it is com.stupidsavacan.clocky.design.resolve.Degradation.FontFallback &&
                    it.reason == FontCatalog.REASON_HOST_BUNDLED_UNSUPPORTED && it.requestedFontId == fontId
            } == true) return
        val face = FontCatalog.resolve(fontId, requested, android.os.Build.VERSION.SDK_INT)
        val shown = if (face.fontFallbackReason != null) face.effectiveWeight else face.face.weight
        if (shown != requested) {
            rows.note(str(R.string.clocky_studio_weight_shows, requested, shown, str(DesignLabels.font(family.id))), emphasized = true)
        }
    }

    private fun fontChips(target: TextTarget) {
        val current = DesignEdits.fontIdOf(d, target)
        rows.chips(
            str(R.string.clocky_studio_font),
            FontCatalog.families.map { it.id to str(DesignLabels.font(it.id)) },
            current,
            render = { chip, id -> StudioRows.typefaceOf(context, id)?.let { chip.typeface = it } },
        ) { picked -> host.edit("font.${target.name}") { DesignEdits.setFont(it, target, picked) } }
    }

    private fun letterSpacing(target: TextTarget) {
        rows.slider(
            str(R.string.clocky_studio_letter_spacing), DesignEdits.style(d, target).letterSpacingEm, DesignEdits.letterSpacingEm, 0.01f,
            format = { String.format(Locale.ROOT, "%.2f em", it) },
            onChange = { v -> host.edit("spacing.${target.name}", "spacing.${target.name}", rebuild = false) {
                DesignEdits.setLetterSpacing(it, target, v)
            } },
            onGestureEnd = host::endGesture,
        )
    }

    private fun percentSlider(label: Int, value: Float, key: String, apply: (DigitalDesign, Float) -> DigitalDesign) {
        rows.slider(
            str(label), value * 100f, 0f..100f, 1f,
            format = { "${it.roundToInt()}%" },
            parse = { it.trim().removeSuffix("%").toFloatOrNull() },
            onChange = { v -> host.edit(key, key, rebuild = false) { apply(it, v / 100f) } },
            onGestureEnd = host::endGesture,
        )
    }

    // ---- overrides ----

    /** The "only this size" marker with a tap to revert (End-State 7: override badge + revert). */
    private fun badgeFor(field: OverrideField): Pair<CharSequence, () -> Unit>? {
        val cls = scope.sizeClass ?: return null
        if (!DesignEdits.isOverridden(d, cls, field)) return null
        return str(R.string.clocky_studio_override_badge, sizeName(cls)) to {
            host.edit("override.revert") { DesignEdits.revertOverride(it, cls, field) }
        }
    }

    private fun overrideBadge(cls: SizeClass, field: OverrideField) {
        if (!DesignEdits.isOverridden(d, cls, field)) return
        rows.button(str(R.string.clocky_studio_override_revert, sizeName(cls))) {
            host.edit("override.revert") { DesignEdits.revertOverride(it, cls, field) }
        }
    }

    private fun sizeName(cls: SizeClass) = str(if (cls == SizeClass.STRIP) R.string.clocky_preview_strip else R.string.clocky_preview_card)

    // ---- reset ----

    private fun resetButton(slot: Slot) {
        val modified = DesignEdits.isSlotModified(d, slot, host.reference)
        val label = if (host.referenceIsPreset) R.string.clocky_studio_reset_preset else R.string.clocky_studio_reset_saved
        resetButton = rows.button(str(label, str(slotLabel(slot))), enabled = modified) {
            host.edit("reset.${slot.name}") { DesignEdits.resetSlot(it, slot, host.reference) }
        }
    }

    /** Slider drags keep the panel's views alive, so the reset button follows the draft separately. */
    fun refreshReset() {
        resetButton?.isEnabled = DesignEdits.isSlotModified(d, builtSlot, host.reference)
    }

    // ---- date helpers ----

    private fun exampleOf(pattern: String): String = runCatching {
        DateFormat.format(pattern, Calendar.getInstance()).toString()
    }.getOrDefault("")

    private fun formatExample(pattern: String) = exampleOf(pattern).let { if (it.isEmpty()) pattern else it }

    private fun validPattern(pattern: String): Boolean = runCatching { SimpleDateFormat(pattern, Locale.getDefault()); true }.getOrDefault(false)

    private fun cityOf(zoneId: String): String =
        zoneId.substringAfterLast('/').replace('_', ' ')

    companion object {
        /** End-State 5.2 date presets (all go through the locale's skeleton on the widget side). */
        val DATE_PRESETS = listOf("EEE, MMM d", "M月d日(E)", "yyyy.MM.dd", "EEE d MMM", "EEEE", "MMMM d")

        val COMMON_ZONES = listOf(
            "Asia/Tokyo", "Europe/London", "America/New_York", "America/Los_Angeles",
            "Europe/Paris", "Asia/Kolkata", "Asia/Dubai", "Australia/Sydney",
        )

        private val CJK_LANGUAGES = setOf("ja", "zh", "ko")

        @androidx.annotation.StringRes
        fun slotLabel(slot: Slot): Int = when (slot) {
            Slot.TIME -> R.string.clocky_element_time
            Slot.DATE -> R.string.clocky_element_date
            Slot.INFO -> R.string.clocky_element_info
            Slot.BACKGROUND -> R.string.clocky_studio_tab_background
            Slot.LAYOUT -> R.string.clocky_studio_tab_layout
            Slot.BEHAVIOR -> R.string.clocky_studio_tab_behavior
        }
    }
}
