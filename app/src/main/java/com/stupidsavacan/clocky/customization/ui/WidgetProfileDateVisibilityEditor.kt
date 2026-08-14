package com.stupidsavacan.clocky.customization.ui

import com.stupidsavacan.clocky.customization.model.ProfileOverride
import com.stupidsavacan.clocky.customization.model.WidgetSettings
import com.stupidsavacan.clocky.customization.model.normalized

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

    fun updateOverride(original: ProfileOverride?, mode: Mode): ProfileOverride? {
        val value = explicitValue(mode)
        val updated = when {
            original != null -> original.copy(dateEnabled = value)
            value != null -> ProfileOverride(dateEnabled = value)
            else -> null
        }
        return updated?.takeIf { it.hasAnyValue() }
    }

    fun apply(
        settings: WidgetSettings,
        baseDateEnabled: Boolean,
        fourByOneMode: Mode,
        fourByTwoMode: Mode,
    ): WidgetSettings = settings.copy(
        date = settings.date.copy(enabled = baseDateEnabled),
        fourByOne = updateOverride(settings.fourByOne, fourByOneMode),
        fourByTwo = updateOverride(settings.fourByTwo, fourByTwoMode),
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
