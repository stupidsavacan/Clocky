package com.stupidsavacan.clocky.customization.ui

import com.stupidsavacan.clocky.customization.model.ProfileOverride
import com.stupidsavacan.clocky.customization.model.WidgetSettings
import com.stupidsavacan.clocky.customization.model.normalized

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
        override: ProfileOverride?,
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
        original: ProfileOverride?,
        enabled: Boolean,
        timeSizeSp: Float?,
        dateSizeSp: Float?,
    ): ProfileOverride? {
        val updated = if (enabled) {
            (original ?: ProfileOverride()).copy(
                timeSizeSp = timeSizeSp?.coerceAtLeast(1f),
                dateSizeSp = dateSizeSp?.coerceAtLeast(1f),
            )
        } else {
            original?.copy(timeSizeSp = null, dateSizeSp = null)
        }
        return updated?.takeIf { it.hasAnyValue() }
    }

    fun apply(
        settings: WidgetSettings,
        fourByOneEnabled: Boolean,
        fourByOneTimeSizeSp: Float?,
        fourByOneDateSizeSp: Float?,
        fourByTwoEnabled: Boolean,
        fourByTwoTimeSizeSp: Float?,
        fourByTwoDateSizeSp: Float?,
    ): WidgetSettings = settings.copy(
        fourByOne = updateOverride(
            settings.fourByOne,
            fourByOneEnabled,
            fourByOneTimeSizeSp,
            fourByOneDateSizeSp,
        ),
        fourByTwo = updateOverride(
            settings.fourByTwo,
            fourByTwoEnabled,
            fourByTwoTimeSizeSp,
            fourByTwoDateSizeSp,
        ),
    ).normalized()

    private fun ProfileOverride.hasAnyValue(): Boolean =
        timeWeight != null ||
            dateWeight != null ||
            timeSizeSp != null ||
            dateSizeSp != null ||
            timeXDp != null ||
            timeYDp != null ||
            dateXDp != null ||
            dateYDp != null ||
            dateEnabled != null
}
