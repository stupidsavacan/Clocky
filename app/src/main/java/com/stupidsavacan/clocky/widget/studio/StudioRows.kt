package com.stupidsavacan.clocky.widget.studio

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.android.deskclock.R
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.ColorRole
import com.stupidsavacan.clocky.design.model.StyleTokens
import kotlin.math.roundToInt

/**
 * Small view factory for Studio's panels. Every interactive element is at least 48dp tall, values
 * can be typed (numeric entry) as well as dragged, and nothing here knows about the design model:
 * panels pass plain values and callbacks.
 */
class StudioRows(private val context: Context, private val container: LinearLayout) {
    private val density = context.resources.displayMetrics.density

    fun dp(value: Int): Int = (value * density).roundToInt()

    fun clear() = container.removeAllViews()

    fun header(text: CharSequence): TextView = TextView(context).apply {
        this.text = text
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
        setTextColor(onSurface(0xFF))
        setPadding(0, dp(20), 0, dp(4))
        accessibilityHeading()
        container.addView(this)
    }

    private fun TextView.accessibilityHeading() {
        androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
    }

    fun note(text: CharSequence, emphasized: Boolean = false): TextView = TextView(context).apply {
        this.text = text
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        setTextColor(if (emphasized) onSurface(0xFF) else onSurface(0xB3))
        setPadding(0, dp(2), 0, dp(6))
        container.addView(this)
    }

    /** A text button; [enabled] false greys it out (e.g. reset when nothing changed). */
    fun button(label: CharSequence, enabled: Boolean = true, onClick: () -> Unit): Button =
        Button(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            isEnabled = enabled
            minimumHeight = dp(48)
            setOnClickListener { onClick() }
            container.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            })
        }

    fun switch(label: CharSequence, checked: Boolean, hint: CharSequence? = null, onChange: (Boolean) -> Unit): MaterialSwitch {
        val sw = MaterialSwitch(context).apply {
            text = label
            isChecked = checked
            minHeight = dp(48)
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }
        container.addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        hint?.let { note(it) }
        return sw
    }

    /**
     * Single choice as chips in one horizontally scrolling row (never shrunk below a usable size).
     * [render] customizes a chip, e.g. to show a font name in its own typeface.
     */
    fun <T> chips(
        label: CharSequence?,
        options: List<Pair<T, CharSequence>>,
        selected: T?,
        render: (Chip, T) -> Unit = { _, _ -> },
        onSelect: (T) -> Unit,
    ) {
        label?.let { rowLabel(it) }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEach { (value, text) ->
            val chip = Chip(context, null, com.google.android.material.R.attr.chipStyle).apply {
                this.text = text
                isCheckable = true
                isChecked = value == selected
                setEnsureMinTouchTargetSize(true)
                render(this, value)
                // A checkable chip toggles itself; the model decides the state, so re-assert after selection.
                setOnClickListener { onSelect(value) }
            }
            row.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply { marginEnd = dp(6) })
        }
        container.addView(
            HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(row) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    /**
     * A value you can drag or type. [format] renders the value; [parse] turns typed text back into
     * a value (null = not a number). [onChange] fires for every drag step (`fromUser`) and for typed
     * values; [onGestureEnd] fires when a drag ends or a typed value was committed.
     */
    fun slider(
        label: CharSequence,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        step: Float,
        format: (Float) -> String,
        parse: (String) -> Float? = { it.trim().replace(',', '.').toFloatOrNull() },
        badge: CharSequence? = null,
        onBadge: (() -> Unit)? = null,
        onChange: (Float) -> Unit,
        onGestureEnd: () -> Unit,
    ) {
        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(
            TextView(context).apply {
                text = label
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                setTextColor(onSurface(0xFF))
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (badge != null && onBadge != null) {
            head.addView(
                TextView(context).apply {
                    text = badge
                    gravity = Gravity.CENTER
                    minHeight = dp(48)
                    setPadding(dp(8), 0, dp(8), 0)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                    isClickable = true
                    setOnClickListener { onBadge() }
                },
            )
        }
        val valueView = TextView(context).apply {
            text = format(value)
            gravity = Gravity.CENTER or Gravity.END
            minHeight = dp(48)
            minWidth = dp(64)
            setPadding(dp(8), 0, 0, 0)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
            isClickable = true
            contentDescription = context.getString(R.string.clocky_studio_value_a11y, label, format(value))
        }
        head.addView(valueView)
        container.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val slider = Slider(context).apply {
            valueFrom = range.start
            valueTo = range.endInclusive
            stepSize = step
            this.value = snap(value, range, step)
            isTickVisible = false
            contentDescription = label
            // The value text is the visible label; keep the slider's own bubble out of the way.
            setLabelFormatter { format(it) }
        }
        slider.addOnChangeListener { _, v, fromUser ->
            if (fromUser) {
                valueView.text = format(v)
                onChange(v)
            }
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) = onGestureEnd()
        })
        container.addView(slider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

        valueView.setOnClickListener {
            promptNumber(label, format(value), range, parse, format) { typed ->
                val clamped = typed.coerceIn(range)
                valueView.text = format(clamped)
                slider.value = snap(clamped, range, step)
                onChange(clamped)
                onGestureEnd()
            }
        }
    }

    private fun snap(v: Float, range: ClosedFloatingPointRange<Float>, step: Float): Float {
        val clamped = v.coerceIn(range)
        if (step <= 0f) return clamped
        val steps = ((clamped - range.start) / step).roundToInt()
        return (range.start + steps * step).coerceIn(range)
    }

    private fun promptNumber(
        label: CharSequence,
        current: String,
        range: ClosedFloatingPointRange<Float>,
        parse: (String) -> Float?,
        format: (Float) -> String,
        onValue: (Float) -> Unit,
    ) {
        val inputLayout = TextInputLayout(context).apply {
            hint = label
            helperText = context.getString(R.string.clocky_studio_range_hint, format(range.start), format(range.endInclusive))
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val input = TextInputEditText(inputLayout.context).apply {
            setText(current.filter { it.isDigit() || it == '.' || it == '-' || it == ',' })
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSelectAllOnFocus(true)
        }
        inputLayout.addView(input)
        val dialog = AlertDialog.Builder(context)
            .setTitle(label)
            .setView(inputLayout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = parse(input.text?.toString().orEmpty())
                if (parsed == null || !parsed.isFinite()) {
                    inputLayout.error = context.getString(R.string.clocky_studio_not_a_number)
                } else {
                    onValue(parsed)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    /** Free text (label, custom date pattern). [validate] returns an error message, or null when fine. */
    fun textField(
        label: CharSequence,
        value: String,
        maxLength: Int,
        helper: CharSequence? = null,
        validate: (String) -> String? = { null },
        onCommit: (String) -> Unit,
    ) {
        val layout = TextInputLayout(context).apply {
            hint = label
            helperText = helper
            counterMaxLength = maxLength
            isCounterEnabled = true
        }
        val edit = TextInputEditText(layout.context).apply {
            setText(value)
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            minimumHeight = dp(48)
        }
        layout.addView(edit)
        val commit = {
            val text = edit.text?.toString().orEmpty()
            val error = validate(text)
            layout.error = error
            if (error == null && text != value) onCommit(text)
        }
        edit.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { commit(); false } else false
        }
        edit.setOnFocusChangeListener { _, focused -> if (!focused) commit() }
        container.addView(layout, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
    }

    /**
     * Colors: palette roles first (when the design has tokens, so the color keeps following the
     * palette and theme), a few fixed swatches, and a custom hex entry.
     */
    fun colorRow(
        label: CharSequence,
        current: ColorRef,
        tokens: StyleTokens?,
        onPick: (ColorRef) -> Unit,
    ) {
        rowLabel(label)
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val roleNames = mapOf(
            ColorRole.PRIMARY to R.string.clocky_studio_role_primary,
            ColorRole.SECONDARY to R.string.clocky_studio_role_secondary,
            ColorRole.ACCENT to R.string.clocky_studio_role_accent,
            ColorRole.SURFACE to R.string.clocky_studio_role_surface,
        )
        if (tokens != null) {
            ColorRole.entries.forEach { role ->
                val rgb = tokens.palette.variant(tokens.palette.fixedVariant).of(role)
                row.addView(swatch(rgb, current == ColorRef.Token(role), context.getString(roleNames.getValue(role)), ring = true) {
                    onPick(ColorRef.Token(role))
                })
            }
        }
        FIXED_SWATCHES.forEach { rgb ->
            row.addView(swatch(rgb, current == ColorRef.Fixed(rgb), hex(rgb)) { onPick(ColorRef.Fixed(rgb)) })
        }
        val isCustom = current is ColorRef.Fixed && current.rgb !in FIXED_SWATCHES
        val customRgb = (current as? ColorRef.Fixed)?.rgb ?: 0x808080
        row.addView(swatch(customRgb, isCustom, context.getString(R.string.clocky_studio_color_custom), glyph = "#") {
            promptHex(label, customRgb) { onPick(ColorRef.Fixed(it)) }
        })
        container.addView(
            HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(row) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun swatch(rgb: Int, selected: Boolean, description: String, ring: Boolean = false, glyph: String? = null, click: () -> Unit): View {
        val size = dp(48)
        val frame = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = if (selected) context.getString(R.string.clocky_studio_selected_a11y, description) else description
            this.isSelected = selected
            setOnClickListener { click() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0)
                if (selected) setStroke(dp(3), onSurface(0xFF))
            }
        }
        frame.addView(
            TextView(context).apply {
                gravity = Gravity.CENTER
                text = glyph.orEmpty()
                setTextColor(if (com.stupidsavacan.clocky.design.resolve.Contrast.luminance(0xFF000000.toInt() or rgb) > 0.5) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF000000.toInt() or rgb)
                    setStroke(dp(if (ring) 3 else 1), onSurface(if (ring) 0x99 else 0x44))
                }
            },
            LinearLayout.LayoutParams(dp(34), dp(34)),
        )
        return frame.also { it.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(4) } }
    }

    private fun promptHex(label: CharSequence, currentRgb: Int, onValue: (Int) -> Unit) {
        val layout = TextInputLayout(context).apply {
            hint = context.getString(R.string.clocky_studio_hex_hint)
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val edit = TextInputEditText(layout.context).apply {
            setText(hex(currentRgb))
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(7))
        }
        layout.addView(edit)
        val dialog = AlertDialog.Builder(context).setTitle(label).setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = parseHex(edit.text?.toString().orEmpty())
                if (parsed == null) {
                    layout.error = context.getString(R.string.clocky_studio_hex_invalid)
                } else {
                    onValue(parsed)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    fun rowLabel(text: CharSequence) {
        container.addView(
            TextView(context).apply {
                this.text = text
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                setTextColor(onSurface(0xFF))
                setPadding(0, dp(12), 0, dp(2))
            },
        )
    }

    /** Appends an arbitrary view (checks list, previews) to the panel. */
    fun add(view: View, params: ViewGroup.LayoutParams? = null) {
        container.addView(view, params ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun onSurface(alpha: Int): Int {
        val ink = MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnSurface)
        return (ink and 0xFFFFFF) or (alpha shl 24)
    }

    companion object {
        val FIXED_SWATCHES = listOf(0xFFFFFF, 0xE6E6E8, 0x1B1B1B, 0x000000, 0xFFB74D, 0xEF5350, 0x66BB6A, 0x42A5F5, 0xAB47BC)

        fun hex(rgb: Int): String = String.format(java.util.Locale.ROOT, "#%06X", rgb and 0xFFFFFF)

        /** Accepts `#RRGGBB`, `RRGGBB` and `#RGB`. */
        fun parseHex(raw: String): Int? {
            var s = raw.trim().removePrefix("#")
            if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
            if (s.length != 6) return null
            return s.toIntOrNull(16)
        }

        fun typefaceOf(context: Context, fontId: String): Typeface? = runCatching {
            when (fontId) {
                "system-sans" -> Typeface.create("sans-serif", Typeface.NORMAL)
                "sans-serif-light", "sans-serif-rounded", "serif", "sans-serif-condensed", "monospace" ->
                    Typeface.create(fontId, Typeface.NORMAL)
                else -> {
                    val res = when (fontId) {
                        "clocky-poppins" -> R.font.clocky_poppins_400
                        "clocky-varela-round" -> R.font.clocky_varela_round_400
                        "clocky-dm-serif-display" -> R.font.clocky_dm_serif_display_400
                        "clocky-barlow-condensed" -> R.font.clocky_barlow_condensed_400
                        "clocky-plex-mono" -> R.font.clocky_plex_mono_400
                        "clocky-bebas-neue" -> R.font.clocky_bebas_neue_400
                        else -> 0
                    }
                    if (res == 0) null else androidx.core.content.res.ResourcesCompat.getFont(context, res)
                }
            }
        }.getOrNull()
    }
}
