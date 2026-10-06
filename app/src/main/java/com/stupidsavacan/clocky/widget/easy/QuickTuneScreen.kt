package com.stupidsavacan.clocky.widget.easy

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.android.deskclock.R
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.stupidsavacan.clocky.design.library.QuickTune
import com.stupidsavacan.clocky.design.library.TypefaceCategory
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.Palette
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.model.TextSizeStep
import com.stupidsavacan.clocky.design.model.ThemeMode
import com.stupidsavacan.clocky.widget.digital.DegradationNotices
import com.stupidsavacan.clocky.widget.digital.DesignPreview
import com.stupidsavacan.clocky.widget.digital.PreviewHost
import kotlin.random.Random
import kotlin.math.roundToInt

/**
 * Quick Tune (End-State 6). Edits only the high-frequency controls, through [QuickTune]'s token
 * operations, and re-renders the production RemoteViews after every change. It owns no state beyond
 * the draft design; the host Activity decides when (and whether) the draft is saved.
 */
class QuickTuneScreen(
    private val root: View,
    appWidgetId: Int,
    initialDraft: DigitalDesign,
    initialClass: SizeClass? = null,
    private val onDraftChanged: (DigitalDesign) -> Unit,
    private val onDone: () -> Unit,
    private val onBack: () -> Unit,
    private val onDetail: () -> Unit,
    private val random: Random = Random.Default,
) {
    private val context = root.context
    private val density = context.resources.displayMetrics.density
    var draft: DigitalDesign = initialDraft
        private set
    var previewClass: SizeClass
        private set

    private val previewHost: PreviewHost
    private val panel: View = root.findViewById(R.id.clocky_preview_panel)
    private val notice: TextView = root.findViewById(R.id.clocky_preview_notice)
    private val classToggle: MaterialButtonToggleGroup = root.findViewById(R.id.clocky_preview_size_class)
    private val palettes: LinearLayout = root.findViewById(R.id.clocky_tune_palettes)
    private val follow: MaterialSwitch = root.findViewById(R.id.clocky_tune_follow)
    private val typefaces: LinearLayout = root.findViewById(R.id.clocky_tune_typefaces)
    private val templates: LinearLayout = root.findViewById(R.id.clocky_tune_templates)
    private val textSize: MaterialButtonToggleGroup = root.findViewById(R.id.clocky_tune_text_size)
    private val dateSwitch: MaterialSwitch = root.findViewById(R.id.clocky_tune_date)
    private val dateHint: TextView = root.findViewById(R.id.clocky_tune_date_hint)
    private var binding = false

    init {
        previewHost = PreviewHost(
            frame = root.findViewById<FrameLayout>(R.id.clocky_preview_frame),
            appWidgetId = appWidgetId,
            maxDisplayHeightDp = context.resources.getInteger(R.integer.clocky_tune_preview_max_height_dp),
        ) { spec ->
            val notes = DegradationNotices.describe(context, spec.degradations)
            notice.text = notes.joinToString(separator = System.lineSeparator())
            notice.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
            (panel.background?.mutate() as? GradientDrawable)?.setColor(DesignPreview.backdropColor(spec))
        }
        previewClass = initialClass ?: previewHost.hostSizeClass()
        classToggle.check(if (previewClass == SizeClass.STRIP) R.id.clocky_preview_strip else R.id.clocky_preview_card)
        classToggle.addOnButtonCheckedListener { _, id, checked ->
            if (checked && !binding) {
                previewClass = if (id == R.id.clocky_preview_strip) SizeClass.STRIP else SizeClass.CARD
                refresh()
            }
        }
        follow.setOnCheckedChangeListener { _, checked -> if (!binding) change(QuickTune.setFollowSystem(draft, checked)) }
        dateSwitch.setOnCheckedChangeListener { _, checked -> if (!binding) change(QuickTune.setDateVisible(draft, checked)) }
        textSize.addOnButtonCheckedListener { _, id, checked ->
            if (checked && !binding) {
                val step = when (id) {
                    R.id.clocky_tune_size_s -> TextSizeStep.SMALL
                    R.id.clocky_tune_size_l -> TextSizeStep.LARGE
                    else -> TextSizeStep.MEDIUM
                }
                change(QuickTune.setTextSize(draft, step))
            }
        }
        root.findViewById<Button>(R.id.clocky_tune_surprise).setOnClickListener { change(QuickTune.surprise(draft, random)) }
        root.findViewById<Button>(R.id.clocky_tune_done).setOnClickListener { onDone() }
        root.findViewById<View>(R.id.clocky_tune_back).setOnClickListener { onBack() }
        root.findViewById<Button>(R.id.clocky_tune_detail).setOnClickListener { onDetail() }
        refresh()
    }

    /** Replaces the draft (e.g. a Gallery pick) and re-renders. */
    fun setDraft(design: DigitalDesign) {
        draft = design
        refresh()
    }

    private fun change(next: DigitalDesign) {
        draft = next
        onDraftChanged(next)
        refresh()
    }

    private fun refresh() {
        binding = true
        previewHost.render(draft, previewClass)
        bindPalettes()
        bindFollow()
        bindTypefaces()
        bindTemplates()
        bindTextSizeAndDate()
        binding = false
    }

    // ---- color ----

    private fun bindPalettes() {
        palettes.removeAllViews()
        val tokens = draft.style ?: return
        QuickTune.paletteOptions(draft).forEach { palette ->
            val selected = tokens.themeMode != ThemeMode.MATERIAL_YOU && palette.id == tokens.palette.id
            palettes.addView(paletteItem(context.getString(DesignLabels.palette(palette.id)), swatch(palette, selected), selected) {
                change(QuickTune.selectPalette(draft, palette))
            })
        }
        val my = tokens.themeMode == ThemeMode.MATERIAL_YOU
        palettes.addView(paletteItem(context.getString(R.string.clocky_palette_material_you), materialYouSwatch(my), my) {
            change(QuickTune.selectMaterialYou(draft))
        })
    }

    private fun bindFollow() {
        val mode = draft.style?.themeMode
        follow.isEnabled = mode != null && mode != ThemeMode.MATERIAL_YOU
        follow.isChecked = mode == ThemeMode.FOLLOW_SYSTEM || mode == ThemeMode.MATERIAL_YOU
    }

    private fun paletteItem(label: String, swatch: android.graphics.drawable.Drawable, selected: Boolean, click: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            this.isSelected = selected
            contentDescription = label
            setOnClickListener { click() }
            addView(View(context).apply { background = swatch }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(TextView(context).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                maxLines = 1
            })
        }

    private fun swatch(palette: Palette, selected: Boolean): android.graphics.drawable.Drawable {
        val colors = palette.variant(palette.fixedVariant)
        val outer = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF000000.toInt() or colors.surface)
            setStroke(dp(1), ringColor(alpha = 0x55))
        }
        val inner = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF000000.toInt() or colors.primary)
        }
        return LayerDrawable(arrayOf(selectionRing(selected), outer, inner)).apply {
            setLayerInset(1, dp(5), dp(5), dp(5), dp(5))
            setLayerInset(2, dp(15), dp(15), dp(15), dp(15))
        }
    }

    private fun materialYouSwatch(selected: Boolean): android.graphics.drawable.Drawable {
        val (a, b) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getColor(android.R.color.system_accent1_200) to context.getColor(android.R.color.system_accent3_200)
        } else {
            0xFFA9D6CC.toInt() to 0xFFE8C9A0.toInt()
        }
        val fill = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a, b)).apply { shape = GradientDrawable.OVAL }
        return LayerDrawable(arrayOf(selectionRing(selected), fill)).apply { setLayerInset(1, dp(5), dp(5), dp(5), dp(5)) }
    }

    private fun selectionRing(selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0)
        if (selected) setStroke(dp(2), ringColor(alpha = 0xFF))
    }

    // ---- typeface ----

    private fun bindTypefaces() {
        typefaces.removeAllViews()
        if (draft.style == null) return
        val current = QuickTune.typefaceOf(draft)
        TypefaceCategory.entries.forEach { category ->
            typefaces.addView(typefaceTile(category, category == current))
        }
    }

    private fun typefaceTile(category: TypefaceCategory, selected: Boolean): View {
        val label = context.getString(DesignLabels.typeface(category))
        val ink = ringColor(alpha = 0xFF)
        val surface = MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurface)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            this.isSelected = selected
            contentDescription = label
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                if (selected) setColor(ink) else setStroke(dp(1), ringColor(alpha = 0x66))
            }
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(64)).apply { marginEnd = dp(6) }
            setOnClickListener { change(QuickTune.selectTypeface(draft, category)) }
            addView(TextView(context).apply {
                text = context.getString(R.string.clocky_sample_time)
                textSize = 22f
                typeface = sampleTypeface(category)
                setTextColor(if (selected) surface else ink)
                gravity = Gravity.CENTER
                includeFontPadding = false
            })
            addView(TextView(context).apply {
                text = label
                textSize = 11f
                setTextColor(if (selected) surface else ink)
                gravity = Gravity.CENTER
                maxLines = 1
            })
        }
    }

    private fun sampleTypeface(category: TypefaceCategory): Typeface = when (category) {
        TypefaceCategory.MODERN -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        TypefaceCategory.ROUNDED -> Typeface.create("sans-serif-rounded", Typeface.NORMAL)
        TypefaceCategory.SERIF -> Typeface.create("serif", Typeface.NORMAL)
        TypefaceCategory.CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        TypefaceCategory.MONO -> Typeface.create("monospace", Typeface.NORMAL)
        TypefaceCategory.DISPLAY -> Typeface.create("sans-serif-black", Typeface.NORMAL)
    }

    // ---- layout, size, date ----

    private fun bindTemplates() {
        templates.removeAllViews()
        if (draft.style == null) return
        val current = QuickTune.templateOf(draft, previewClass)
        QuickTune.templatesFor(previewClass).forEach { template ->
            templates.addView(templateChip(template, template == current))
        }
    }

    private fun templateChip(template: Template, selected: Boolean) =
        Chip(context, null, com.google.android.material.R.attr.chipStyle).apply {
            text = context.getString(DesignLabels.template(template))
            isCheckable = true
            isChecked = selected
            setEnsureMinTouchTargetSize(true)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply { marginEnd = dp(6) }
            // A checkable chip toggles itself; re-assert the state the draft dictates.
            setOnClickListener { change(QuickTune.selectTemplate(draft, previewClass, template)) }
        }

    private fun bindTextSizeAndDate() {
        val step = draft.style?.textSize ?: TextSizeStep.MEDIUM
        textSize.check(
            when (step) {
                TextSizeStep.SMALL -> R.id.clocky_tune_size_s
                TextSizeStep.MEDIUM -> R.id.clocky_tune_size_m
                TextSizeStep.LARGE -> R.id.clocky_tune_size_l
            },
        )
        val timeOnly = QuickTune.templateOf(draft, previewClass) == Template.MINIMAL
        dateSwitch.isChecked = draft.date.visible
        dateSwitch.isEnabled = !timeOnly
        dateHint.visibility = if (timeOnly) View.VISIBLE else View.GONE
    }

    // ---- helpers ----

    private fun ringColor(alpha: Int): Int {
        val ink = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface)
        return (ink and 0xFFFFFF) or (alpha shl 24)
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()
}
