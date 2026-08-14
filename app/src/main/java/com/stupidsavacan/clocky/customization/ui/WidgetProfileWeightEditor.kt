package com.stupidsavacan.clocky.customization.ui

import com.stupidsavacan.clocky.customization.model.ProfileOverride
import com.stupidsavacan.clocky.customization.model.WidgetSettings
import com.stupidsavacan.clocky.customization.model.normalized

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
        override: ProfileOverride?,
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
        original: ProfileOverride?,
        enabled: Boolean,
        timeWeight: Int?,
        dateWeight: Int?,
    ): ProfileOverride? {
        val updated = if (enabled) {
            (original ?: ProfileOverride()).copy(
                timeWeight = timeWeight?.coerceIn(100, 900),
                dateWeight = dateWeight?.coerceIn(100, 900),
            )
        } else {
            original?.copy(timeWeight = null, dateWeight = null)
        }
        return updated?.takeIf { it.hasAnyValue() }
    }

    fun apply(
        settings: WidgetSettings,
        fourByOneEnabled: Boolean,
        fourByOneTimeWeight: Int?,
        fourByOneDateWeight: Int?,
        fourByTwoEnabled: Boolean,
        fourByTwoTimeWeight: Int?,
        fourByTwoDateWeight: Int?,
    ): WidgetSettings = settings.copy(
        fourByOne = updateOverride(
            settings.fourByOne,
            fourByOneEnabled,
            fourByOneTimeWeight,
            fourByOneDateWeight,
        ),
        fourByTwo = updateOverride(
            settings.fourByTwo,
            fourByTwoEnabled,
            fourByTwoTimeWeight,
            fourByTwoDateWeight,
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
