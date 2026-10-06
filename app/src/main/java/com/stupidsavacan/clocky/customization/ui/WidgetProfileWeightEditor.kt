package com.stupidsavacan.clocky.customization.ui

import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.SizeClass

/** Pure update rules for size-profile-specific weight overrides. */
object WidgetProfileWeightEditor {
    data class ProfileState(
        val enabled: Boolean,
        val timeWeight: Int,
        val dateWeight: Int,
        val timeInherited: Boolean,
        val dateInherited: Boolean,
    )

    fun state(
        override: LayoutPatch?,
        baseTimeWeight: Int,
        baseDateWeight: Int,
    ): ProfileState {
        val timeInherited = override?.timeWeight == null
        val dateInherited = override?.dateWeight == null
        return ProfileState(
            enabled = !timeInherited || !dateInherited,
            timeWeight = override?.timeWeight ?: baseTimeWeight,
            dateWeight = override?.dateWeight ?: baseDateWeight,
            timeInherited = timeInherited,
            dateInherited = dateInherited,
        )
    }

    fun updateOverride(
        original: LayoutPatch?,
        enabled: Boolean,
        timeWeight: Int?,
        dateWeight: Int?,
    ): LayoutPatch? {
        val updated = if (enabled) {
            (original ?: LayoutPatch()).copy(
                timeWeight = timeWeight?.coerceIn(100, 900),
                dateWeight = dateWeight?.coerceIn(100, 900),
            )
        } else {
            original?.copy(timeWeight = null, dateWeight = null)
        }
        return updated?.takeIf { !it.isEmpty }
    }

    fun apply(
        design: DigitalDesign,
        stripEnabled: Boolean,
        stripTimeWeight: Int?,
        stripDateWeight: Int?,
        cardEnabled: Boolean,
        cardTimeWeight: Int?,
        cardDateWeight: Int?,
    ): DigitalDesign = design.withPatches(
        strip = updateOverride(design.patchOrNull(SizeClass.STRIP), stripEnabled, stripTimeWeight, stripDateWeight),
        card = updateOverride(design.patchOrNull(SizeClass.CARD), cardEnabled, cardTimeWeight, cardDateWeight),
    )
}
