package com.stupidsavacan.clocky.customization.ui

import com.stupidsavacan.clocky.customization.model.WidgetSettings
import com.stupidsavacan.clocky.customization.model.normalized

object WidgetWeightEditor {
    const val MIN_WEIGHT = 100
    const val MAX_WEIGHT = 900
    const val SEEK_RANGE = MAX_WEIGHT - MIN_WEIGHT

    fun weightToProgress(weight: Int): Int =
        weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT) - MIN_WEIGHT

    fun progressToWeight(progress: Int): Int =
        (progress + MIN_WEIGHT).coerceIn(MIN_WEIGHT, MAX_WEIGHT)

    fun applyWeights(
        settings: WidgetSettings,
        timeWeight: Int,
        dateWeight: Int,
    ): WidgetSettings = settings.copy(
        time = settings.time.copy(requestedWeight = timeWeight),
        date = settings.date.copy(requestedWeight = dateWeight),
    ).normalized()
}
