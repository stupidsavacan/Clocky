package com.stupidsavacan.clocky.customization.format

import com.stupidsavacan.clocky.customization.model.HourMode

/** Pure hour-mode mapping for settings whose runtime meaning is explicitly defined. */
object DigitalWidgetFormatPolicy {
    fun timeOverride(hourMode: HourMode): String? = when (hourMode) {
        HourMode.FOLLOW_SYSTEM -> null
        HourMode.FORCE_12_HOUR -> "h:mm"
        HourMode.FORCE_24_HOUR -> "HH:mm"
    }
}
