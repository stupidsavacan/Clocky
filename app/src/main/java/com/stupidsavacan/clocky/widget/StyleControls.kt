package com.stupidsavacan.clocky.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.android.deskclock.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.CornerRadius
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.MAX_PADDING_DP
import com.stupidsavacan.clocky.design.model.TextStyle
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Phase 1A style controls (font, color + opacity, alignment, date format, Native background).
 * A deliberately plain pre-Studio editor: it only reads and writes Design v2 fields; the preview
 * and the widget render whatever it produces through the same composer.
 */
class StyleControls(
    private val context: Context,
    container: ViewGroup,
    initial: DigitalDesign,
    localeAutoPattern: String,
    private val onChange: () -> Unit,
) {
    private var timeFont = initial.time.style.fontId
    private var dateFont = initial.date.style.fontId
    private var timeColor = rgbOf(initial.time.style.color)
    private var dateColor = rgbOf(initial.date.style.color)
    private var timeOpacity = initial.time.style.opacity
    private var dateOpacity = initial.date.style.opacity
    private var timeAlignment = initial.time.style.alignment
    private var dateAlignment = initial.date.style.alignment
    private var dateFormat: String? = initial.date.formatPattern
    private var backgroundType = initial.background.type
    private var backgroundColor = rgbOf(initial.background.color)
    private var backgroundOpacity = initial.background.opacity
    private var cornerRadius = initial.background.cornerRadius
    private var padding = initial.background.paddingDp

    init {
        container.addView(header(R.string.clocky_style_title))

        container.addView(label(R.string.clocky_time_font))
        container.addView(fontChips(timeFont) { timeFont = it })
        container.addView(label(R.string.clocky_date_font))
        container.addView(fontChips(dateFont) { dateFont = it })

        container.addView(label(R.string.clocky_time_color))
        container.addView(colorChips(TEXT_PALETTE, timeColor) { timeColor = it })
        container.addView(percentSlider(R.string.clocky_time_opacity, timeOpacity) { timeOpacity = it })
        container.addView(label(R.string.clocky_date_color))
        container.addView(colorChips(TEXT_PALETTE, dateColor) { dateColor = it })
        container.addView(percentSlider(R.string.clocky_date_opacity, dateOpacity) { dateOpacity = it })

        container.addView(label(R.string.clocky_time_alignment))
        container.addView(alignmentToggle(timeAlignment) { timeAlignment = it })
        container.addView(label(R.string.clocky_date_alignment))
        container.addView(alignmentToggle(dateAlignment) { dateAlignment = it })

        container.addView(label(R.string.clocky_date_format))
        container.addView(dateFormatChips(localeAutoPattern))

        container.addView(header(R.string.clocky_background_title))
        container.addView(backgroundTypeToggle())
        container.addView(colorChips(BACKGROUND_PALETTE, backgroundColor) { backgroundColor = it })
        container.addView(percentSlider(R.string.clocky_background_opacity, backgroundOpacity) { backgroundOpacity = it })
        container.addView(label(R.string.clocky_background_corners))
        container.addView(radiusChips())
        container.addView(paddingSlider())
    }

    /** Writes the control state into [design] (all other fields untouched). */
    fun applyTo(design: DigitalDesign): DigitalDesign = design.copy(
        time = design.time.copy(style = design.time.style.with(timeFont, timeColor, timeOpacity, timeAlignment)),
        date = design.date.copy(
            style = design.date.style.with(dateFont, dateColor, dateOpacity, dateAlignment),
            formatPattern = dateFormat,
        ),
        background = design.background.copy(
            type = backgroundType,
            color = ColorRef.Fixed(backgroundColor),
            opacity = backgroundOpacity,
            cornerRadius = cornerRadius,
            paddingDp = padding,
        ),
    )

    private fun TextStyle.with(font: String, rgb: Int, opacity: Float, alignment: Alignment) =
        copy(fontId = font, color = ColorRef.Fixed(rgb), opacity = opacity, alignment = alignment)

    // ---- builders ----

    private fun fontChips(selected: String, set: (String) -> Unit): View {
        val options = FONT_OPTIONS.toMutableList()
        if (options.none { it.first == selected }) options += selected to 0
        return chipGroup(options.map { (id, labelRes) ->
            ChipSpec(if (labelRes != 0) context.getString(labelRes) else id, id == selected) {
                set(id)
                onChange()
            }
        })
    }

    private fun colorChips(palette: List<Pair<Int, Int>>, selected: Int, set: (Int) -> Unit): View {
        val options = palette.toMutableList()
        if (options.none { it.first == selected }) options += selected to 0
        return chipGroup(options.map { (rgb, labelRes) ->
            val text = if (labelRes != 0) {
                context.getString(labelRes)
            } else {
                String.format(Locale.ROOT, "#%06X", rgb)
            }
            ChipSpec(text, rgb == selected, swatch = rgb) {
                set(rgb)
                onChange()
            }
        })
    }

    private fun dateFormatChips(localeAutoPattern: String): View {
        val now = Calendar.getInstance()
        val options = mutableListOf<Pair<String?, String>>(
            null to context.getString(R.string.clocky_date_format_auto, DateFormat.format(localeAutoPattern, now)),
        )
        DATE_PRESETS.forEach { options += it to DateFormat.format(it, now).toString() }
        dateFormat?.takeIf { it !in DATE_PRESETS }?.let { options += it to DateFormat.format(it, now).toString() }
        return chipGroup(options.map { (pattern, text) ->
            ChipSpec(text, pattern == dateFormat) {
                dateFormat = pattern
                onChange()
            }
        })
    }

    private fun radiusChips(): View {
        val options = listOf<CornerRadius>(CornerRadius.System) + RADIUS_OPTIONS_DP.map { CornerRadius.Dp(it) }
        val all = if (cornerRadius in options) options else options + cornerRadius
        return chipGroup(all.map { r ->
            val text = when (r) {
                CornerRadius.System -> context.getString(R.string.clocky_corners_system)
                is CornerRadius.Dp -> context.getString(R.string.clocky_dp_value, r.value.roundToInt())
            }
            ChipSpec(text, r == cornerRadius) {
                cornerRadius = r
                onChange()
            }
        })
    }

    private fun alignmentToggle(selected: Alignment, set: (Alignment) -> Unit): View {
        val labels = listOf(
            Alignment.START to R.string.clocky_align_start,
            Alignment.CENTER to R.string.clocky_align_center,
            Alignment.END to R.string.clocky_align_end,
        )
        return toggle(labels.map { (value, res) -> context.getString(res) to (value == selected) }) { index ->
            set(labels[index].first)
            onChange()
        }
    }

    private fun backgroundTypeToggle(): View {
        val labels = listOf(
            BackgroundType.NONE to R.string.clocky_background_none,
            BackgroundType.SOLID to R.string.clocky_background_solid,
        )
        return toggle(labels.map { (value, res) -> context.getString(res) to (value == backgroundType) }) { index ->
            backgroundType = labels[index].first
            onChange()
        }
    }

    private fun percentSlider(@StringRes labelRes: Int, value: Float, set: (Float) -> Unit): View {
        val row = valueRow(labelRes)
        val slider = Slider(context).apply {
            valueFrom = 0f
            valueTo = 100f
            stepSize = 1f
            this.value = (value * 100f).roundToInt().toFloat().coerceIn(0f, 100f)
        }
        fun show(v: Float) { row.second.text = context.getString(R.string.clocky_percent_value, v.roundToInt()) }
        show(slider.value)
        slider.addOnChangeListener { _, v, _ ->
            show(v)
            set(v / 100f)
            onChange()
        }
        return column(row.first, slider)
    }

    private fun paddingSlider(): View {
        val row = valueRow(R.string.clocky_background_padding)
        val slider = Slider(context).apply {
            valueFrom = 0f
            valueTo = MAX_PADDING_DP
            stepSize = 1f
            value = padding.roundToInt().toFloat().coerceIn(0f, MAX_PADDING_DP)
        }
        fun show(v: Float) { row.second.text = context.getString(R.string.clocky_dp_value, v.roundToInt()) }
        show(slider.value)
        slider.addOnChangeListener { _, v, _ ->
            show(v)
            padding = v
            onChange()
        }
        return column(row.first, slider)
    }

    // ---- view helpers ----

    private data class ChipSpec(val text: String, val checked: Boolean, val swatch: Int? = null, val onSelect: () -> Unit)

    private fun chipGroup(specs: List<ChipSpec>): ChipGroup = ChipGroup(context).apply {
        isSingleSelection = true
        isSelectionRequired = true
        specs.forEach { spec ->
            val chip = Chip(context).apply {
                id = View.generateViewId()
                text = spec.text
                isCheckable = true
                isChecked = spec.checked
                spec.swatch?.let { rgb ->
                    chipIcon = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(OPAQUE or rgb)
                        setStroke(dp(1), OUTLINE)
                    }
                    isChipIconVisible = true
                    chipIconTint = null
                }
                setOnCheckedChangeListener { _, checked -> if (checked) spec.onSelect() }
            }
            addView(chip)
        }
        layoutParams = marginTop(4)
    }

    private fun toggle(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit): View =
        MaterialButtonToggleGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = true
            val ids = options.map { (text, checked) ->
                val button = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    id = View.generateViewId()
                    this.text = text
                }
                addView(button)
                if (checked) check(button.id)
                button.id
            }
            addOnButtonCheckedListener { _, id, isChecked -> if (isChecked) onSelect(ids.indexOf(id)) }
            layoutParams = marginTop(4)
        }

    private fun header(@StringRes res: Int) = TextView(context).apply {
        setText(res)
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
        layoutParams = marginTop(28)
    }

    private fun label(@StringRes res: Int) = TextView(context).apply {
        setText(res)
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
        layoutParams = marginTop(16)
    }

    private fun valueRow(@StringRes res: Int): Pair<View, TextView> {
        val value = TextView(context).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(context).apply {
                setText(res)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(value)
        }
        return row to value
    }

    private fun column(vararg views: View) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        views.forEach { addView(it) }
        layoutParams = marginTop(8)
    }

    private fun marginTop(dpValue: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(dpValue) }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    companion object {
        private const val OPAQUE = 0xFF000000.toInt()
        private val OUTLINE = 0x33000000

        val FONT_OPTIONS: List<Pair<String, Int>> = listOf(
            FontIds.SYSTEM_SANS to R.string.clocky_font_system,
            "sans-serif-light" to R.string.clocky_font_light,
            "sans-serif-rounded" to R.string.clocky_font_rounded,
            "serif" to R.string.clocky_font_serif,
            "sans-serif-condensed" to R.string.clocky_font_condensed,
            "monospace" to R.string.clocky_font_mono,
        )

        val TEXT_PALETTE: List<Pair<Int, Int>> = listOf(
            0xFFFFFF to R.string.clocky_color_white,
            0x16161A to R.string.clocky_color_ink,
            0xE6E2DA to R.string.clocky_color_stone,
            0x8AB4F8 to R.string.clocky_color_sky,
            0x81C995 to R.string.clocky_color_mint,
            0xFDD663 to R.string.clocky_color_amber,
            0xF28B82 to R.string.clocky_color_coral,
        )

        /** Includes the legacy MVP dark (#111111) and light (#FFFFFF) backgrounds (contract §7). */
        val BACKGROUND_PALETTE: List<Pair<Int, Int>> = listOf(
            0x111111 to R.string.clocky_color_dark,
            0xFFFFFF to R.string.clocky_color_white,
            0xE6E2DA to R.string.clocky_color_stone,
            0x1F2A44 to R.string.clocky_color_navy,
            0x2D3B2F to R.string.clocky_color_forest,
        )

        /** End-State §5.2 presets, stored and rendered literally (design record §7 question 2). */
        val DATE_PRESETS: List<String> = listOf(
            "EEE, MMM d",
            "M月d日(E)",
            "yyyy.MM.dd",
            "EEE d MMM",
            "EEEE",
            "MMMM d",
        )

        val RADIUS_OPTIONS_DP: List<Float> = listOf(0f, 8f, 16f, 24f, 32f, 48f)

        fun rgbOf(color: ColorRef): Int = when (color) {
            is ColorRef.Fixed -> color.rgb
        }
    }
}
