package com.stupidsavacan.clocky.customization.ui

import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.SizeClass

/** Pure update rules for size-profile-specific time/date size overrides. */
object WidgetProfileSizeEditor {
    data class ProfileState(
        val enabled: Boolean,
        val timeSizeSp: Float,
        val dateSizeSp: Float,
        val timeInherited: Boolean,
        val dateInherited: Boolean,
    )

    fun state(
        override: LayoutPatch?,
        baseTimeSizeSp: Float,
        baseDateSizeSp: Float,
    ): ProfileState {
        val timeInherited = override?.timeSizeSp == null
        val dateInherited = override?.dateSizeSp == null
        return ProfileState(
            enabled = !timeInherited || !dateInherited,
            timeSizeSp = (override?.timeSizeSp ?: baseTimeSizeSp).coerceAtLeast(1f),
            dateSizeSp = (override?.dateSizeSp ?: baseDateSizeSp).coerceAtLeast(1f),
            timeInherited = timeInherited,
            dateInherited = dateInherited,
        )
    }

    fun updateOverride(
        original: LayoutPatch?,
        enabled: Boolean,
        timeSizeSp: Float?,
        dateSizeSp: Float?,
    ): LayoutPatch? {
        val updated = if (enabled) {
            (original ?: LayoutPatch()).copy(
                timeSizeSp = timeSizeSp?.coerceAtLeast(1f),
                dateSizeSp = dateSizeSp?.coerceAtLeast(1f),
            )
        } else {
            original?.copy(timeSizeSp = null, dateSizeSp = null)
        }
        return updated?.takeIf { !it.isEmpty }
    }

    fun apply(
        design: DigitalDesign,
        stripEnabled: Boolean,
        stripTimeSizeSp: Float?,
        stripDateSizeSp: Float?,
        cardEnabled: Boolean,
        cardTimeSizeSp: Float?,
        cardDateSizeSp: Float?,
    ): DigitalDesign = design.withPatches(
        strip = updateOverride(design.patchOrNull(SizeClass.STRIP), stripEnabled, stripTimeSizeSp, stripDateSizeSp),
        card = updateOverride(design.patchOrNull(SizeClass.CARD), cardEnabled, cardTimeSizeSp, cardDateSizeSp),
    )
}
