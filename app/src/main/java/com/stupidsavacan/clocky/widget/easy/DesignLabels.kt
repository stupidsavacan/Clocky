package com.stupidsavacan.clocky.widget.easy

import android.content.Context
import androidx.annotation.StringRes
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.library.TypefaceCategory
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.Template

/** User-facing names for library assets; ids stay language-neutral in the model. */
object DesignLabels {
    @StringRes
    fun kit(kitId: String): Int = when (kitId) {
        "minimal" -> R.string.clocky_kit_minimal
        "bold" -> R.string.clocky_kit_bold
        "editorial" -> R.string.clocky_kit_editorial
        else -> R.string.clocky_kit_default
    }

    @StringRes
    fun design(builtinId: String?): Int = when (builtinId) {
        "clocky-default" -> R.string.clocky_design_clocky_default
        "default-tonal" -> R.string.clocky_design_default_tonal
        "minimal-hairline" -> R.string.clocky_design_minimal_hairline
        "minimal-quiet-split" -> R.string.clocky_design_minimal_quiet_split
        "bold-poster" -> R.string.clocky_design_bold_poster
        "bold-block" -> R.string.clocky_design_bold_block
        "editorial-serif" -> R.string.clocky_design_editorial_serif
        "editorial-paper" -> R.string.clocky_design_editorial_paper
        else -> R.string.clocky_design_custom
    }

    fun designName(context: Context, design: DigitalDesign): String =
        context.getString(design(design.source?.builtinId))

    @StringRes
    fun palette(paletteId: String): Int = when (paletteId) {
        "clocky" -> R.string.clocky_palette_clocky
        "mint" -> R.string.clocky_palette_mint
        "mono" -> R.string.clocky_palette_mono
        "sand" -> R.string.clocky_palette_sand
        "ocean" -> R.string.clocky_palette_ocean
        "rose" -> R.string.clocky_palette_rose
        "poster" -> R.string.clocky_palette_poster
        "ember" -> R.string.clocky_palette_ember
        "paper" -> R.string.clocky_palette_paper
        else -> R.string.clocky_palette_kit
    }

    @StringRes
    fun typeface(category: TypefaceCategory): Int = when (category) {
        TypefaceCategory.MODERN -> R.string.clocky_typeface_modern
        TypefaceCategory.ROUNDED -> R.string.clocky_typeface_rounded
        TypefaceCategory.SERIF -> R.string.clocky_typeface_serif
        TypefaceCategory.CONDENSED -> R.string.clocky_typeface_condensed
        TypefaceCategory.MONO -> R.string.clocky_typeface_mono
        TypefaceCategory.DISPLAY -> R.string.clocky_typeface_display
    }

    @StringRes
    fun template(template: Template): Int = when (template) {
        Template.CENTER_STACK -> R.string.clocky_template_center_stack
        Template.TIME_FIRST -> R.string.clocky_template_time_first
        Template.INLINE -> R.string.clocky_template_inline
        Template.SPLIT -> R.string.clocky_template_split
        Template.MINIMAL -> R.string.clocky_template_minimal
    }
}
