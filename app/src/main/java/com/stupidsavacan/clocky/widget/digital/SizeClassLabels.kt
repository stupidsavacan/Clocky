package com.stupidsavacan.clocky.widget.digital

import com.android.deskclock.R
import com.stupidsavacan.clocky.design.model.SizeClass

object SizeClassLabels {
    fun label(cls: SizeClass): Int = when (cls) {
        SizeClass.STRIP -> R.string.clocky_preview_strip
        SizeClass.CARD -> R.string.clocky_preview_card
        SizeClass.SQUARE -> R.string.clocky_preview_square
        SizeClass.LARGE -> R.string.clocky_preview_large
    }
    fun button(cls: SizeClass): Int = when (cls) {
        SizeClass.STRIP -> R.id.clocky_preview_strip
        SizeClass.CARD -> R.id.clocky_preview_card
        SizeClass.SQUARE -> R.id.clocky_preview_square
        SizeClass.LARGE -> R.id.clocky_preview_large
    }
    fun fromButton(id: Int): SizeClass = SizeClass.entries.first { button(it) == id }
}
