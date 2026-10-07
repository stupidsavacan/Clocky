package com.stupidsavacan.clocky.widget.digital

import androidx.annotation.LayoutRes
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.resolve.ShadowVariant

/** Generated localized-marker variants; same geometry, accessibility excluded in XML. */
internal object AmPmFallbackFragmentTable {
    private data class Key(val fontId: String, val shadow: ShadowVariant)
    private val layouts = mapOf(
        Key("system-sans", ShadowVariant.CLASSIC) to R.layout.clocky_ampm_fallback_system_sans,
        Key("system-sans", ShadowVariant.OFF) to R.layout.clocky_ampm_fallback_system_sans_off,
        Key("system-sans", ShadowVariant.SOFT_DARK) to R.layout.clocky_ampm_fallback_system_sans_soft_dark,
        Key("system-sans", ShadowVariant.SOFT_LIGHT) to R.layout.clocky_ampm_fallback_system_sans_soft_light,
        Key("system-sans", ShadowVariant.STRONG_DARK) to R.layout.clocky_ampm_fallback_system_sans_strong_dark,
        Key("system-sans", ShadowVariant.STRONG_LIGHT) to R.layout.clocky_ampm_fallback_system_sans_strong_light,
        Key("sans-serif-light", ShadowVariant.CLASSIC) to R.layout.clocky_ampm_fallback_sans_serif_light,
        Key("sans-serif-light", ShadowVariant.OFF) to R.layout.clocky_ampm_fallback_sans_serif_light_off,
        Key("sans-serif-light", ShadowVariant.SOFT_DARK) to R.layout.clocky_ampm_fallback_sans_serif_light_soft_dark,
        Key("sans-serif-light", ShadowVariant.SOFT_LIGHT) to R.layout.clocky_ampm_fallback_sans_serif_light_soft_light,
        Key("sans-serif-light", ShadowVariant.STRONG_DARK) to R.layout.clocky_ampm_fallback_sans_serif_light_strong_dark,
        Key("sans-serif-light", ShadowVariant.STRONG_LIGHT) to R.layout.clocky_ampm_fallback_sans_serif_light_strong_light,
        Key("sans-serif-rounded", ShadowVariant.CLASSIC) to R.layout.clocky_ampm_fallback_sans_serif_rounded,
        Key("sans-serif-rounded", ShadowVariant.OFF) to R.layout.clocky_ampm_fallback_sans_serif_rounded_off,
        Key("sans-serif-rounded", ShadowVariant.SOFT_DARK) to R.layout.clocky_ampm_fallback_sans_serif_rounded_soft_dark,
        Key("sans-serif-rounded", ShadowVariant.SOFT_LIGHT) to R.layout.clocky_ampm_fallback_sans_serif_rounded_soft_light,
        Key("sans-serif-rounded", ShadowVariant.STRONG_DARK) to R.layout.clocky_ampm_fallback_sans_serif_rounded_strong_dark,
        Key("sans-serif-rounded", ShadowVariant.STRONG_LIGHT) to R.layout.clocky_ampm_fallback_sans_serif_rounded_strong_light,
        Key("serif", ShadowVariant.CLASSIC) to R.layout.clocky_ampm_fallback_serif,
        Key("serif", ShadowVariant.OFF) to R.layout.clocky_ampm_fallback_serif_off,
        Key("serif", ShadowVariant.SOFT_DARK) to R.layout.clocky_ampm_fallback_serif_soft_dark,
        Key("serif", ShadowVariant.SOFT_LIGHT) to R.layout.clocky_ampm_fallback_serif_soft_light,
        Key("serif", ShadowVariant.STRONG_DARK) to R.layout.clocky_ampm_fallback_serif_strong_dark,
        Key("serif", ShadowVariant.STRONG_LIGHT) to R.layout.clocky_ampm_fallback_serif_strong_light,
        Key("sans-serif-condensed", ShadowVariant.CLASSIC) to R.layout.clocky_ampm_fallback_sans_serif_condensed,
        Key("sans-serif-condensed", ShadowVariant.OFF) to R.layout.clocky_ampm_fallback_sans_serif_condensed_off,
        Key("sans-serif-condensed", ShadowVariant.SOFT_DARK) to R.layout.clocky_ampm_fallback_sans_serif_condensed_soft_dark,
        Key("sans-serif-condensed", ShadowVariant.SOFT_LIGHT) to R.layout.clocky_ampm_fallback_sans_serif_condensed_soft_light,
        Key("sans-serif-condensed", ShadowVariant.STRONG_DARK) to R.layout.clocky_ampm_fallback_sans_serif_condensed_strong_dark,
        Key("sans-serif-condensed", ShadowVariant.STRONG_LIGHT) to R.layout.clocky_ampm_fallback_sans_serif_condensed_strong_light,
        Key("monospace", ShadowVariant.CLASSIC) to R.layout.clocky_ampm_fallback_monospace,
        Key("monospace", ShadowVariant.OFF) to R.layout.clocky_ampm_fallback_monospace_off,
        Key("monospace", ShadowVariant.SOFT_DARK) to R.layout.clocky_ampm_fallback_monospace_soft_dark,
        Key("monospace", ShadowVariant.SOFT_LIGHT) to R.layout.clocky_ampm_fallback_monospace_soft_light,
        Key("monospace", ShadowVariant.STRONG_DARK) to R.layout.clocky_ampm_fallback_monospace_strong_dark,
        Key("monospace", ShadowVariant.STRONG_LIGHT) to R.layout.clocky_ampm_fallback_monospace_strong_light,
    )

    @LayoutRes
    fun layoutFor(fontId: String, shadow: ShadowVariant): Int = layouts.getValue(Key(fontId, shadow))
}
