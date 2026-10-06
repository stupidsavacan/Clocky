package com.stupidsavacan.clocky.customization.ui

import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.SizeClass

/** Pure update rules for base and size-profile-specific date visibility. */
object WidgetProfileDateVisibilityEditor {
    enum class Mode {
        INHERIT,
        SHOW,
        HIDE,
    }

    fun mode(value: Boolean?): Mode = when (value) {
        null -> Mode.INHERIT
        true -> Mode.SHOW
        false -> Mode.HIDE
    }

    fun explicitValue(mode: Mode): Boolean? = when (mode) {
        Mode.INHERIT -> null
        Mode.SHOW -> true
        Mode.HIDE -> false
    }

    fun updateOverride(original: LayoutPatch?, mode: Mode): LayoutPatch? {
        val value = explicitValue(mode)
        val updated = when {
            original != null -> original.copy(dateVisible = value)
            value != null -> LayoutPatch(dateVisible = value)
            else -> null
        }
        return updated?.takeIf { !it.isEmpty }
    }

    fun apply(
        design: DigitalDesign,
        baseDateVisible: Boolean,
        stripMode: Mode,
        cardMode: Mode,
    ): DigitalDesign = design.copy(date = design.date.copy(visible = baseDateVisible)).withPatches(
        strip = updateOverride(design.patchOrNull(SizeClass.STRIP), stripMode),
        card = updateOverride(design.patchOrNull(SizeClass.CARD), cardMode),
    )
}
