package com.stupidsavacan.clocky.customization.model

import com.stupidsavacan.clocky.customization.font.WidgetLetterSpacingPolicy

/** Clocky-owned settings layered on top of the AOSP DeskClock functional base. */
data class WidgetSettings(
    val appWidgetId: Int,
    val presetId: String = GOOGLE_CLOCK_PRESET_ID,
    val time: TimeSettings = TimeSettings(),
    val date: DateSettings = DateSettings(),
    val background: BackgroundSettings = BackgroundSettings(),
    val fourByTwo: ProfileOverride? = null,
    val fourByOne: ProfileOverride? = null,
) {
    companion object {
        const val GOOGLE_CLOCK_PRESET_ID = "google-clock"
    }
}

data class TimeSettings(
    val fontFamily: String = "system-sans",
    val requestedWeight: Int = 400,
    val sizeSp: Float = 64f,
    val letterSpacing: Float = 0f,
    val argb: Int = 0xFFFFFFFF.toInt(),
    val opacity: Float = 1f,
    val xDp: Float = 0f,
    val yDp: Float = 0f,
    val alignment: HorizontalAlignment = HorizontalAlignment.CENTER,
    val hourMode: HourMode = HourMode.FOLLOW_SYSTEM,
    val leadingZero: Boolean = false,
)

data class DateSettings(
    val enabled: Boolean = true,
    val fontFamily: String = "system-sans",
    val requestedWeight: Int = 400,
    val sizeSp: Float = 14f,
    val letterSpacing: Float = 0f,
    val argb: Int = 0xFFFFFFFF.toInt(),
    val opacity: Float = 1f,
    val formatPattern: String = "EEE, MMM d",
    val xDp: Float = 0f,
    val yDp: Float = 0f,
    val alignment: HorizontalAlignment = HorizontalAlignment.CENTER,
)

data class BackgroundSettings(
    val argb: Int = 0x00000000,
    val opacity: Float = 0f,
    val cornerRadiusDp: Float = 0f,
    val paddingDp: Float = 0f,
)

/** Nullable members inherit from the widget base settings. */
data class ProfileOverride(
    val timeWeight: Int? = null,
    val dateWeight: Int? = null,
    val timeSizeSp: Float? = null,
    val dateSizeSp: Float? = null,
    val timeXDp: Float? = null,
    val timeYDp: Float? = null,
    val dateXDp: Float? = null,
    val dateYDp: Float? = null,
    val dateEnabled: Boolean? = null,
)

enum class HorizontalAlignment { LEFT, CENTER, RIGHT }
enum class HourMode { FOLLOW_SYSTEM, FORCE_12_HOUR, FORCE_24_HOUR }

fun WidgetSettings.normalized(): WidgetSettings = copy(
    time = time.copy(
        requestedWeight = time.requestedWeight.coerceIn(100, 900),
        sizeSp = time.sizeSp.coerceAtLeast(1f),
        letterSpacing = WidgetLetterSpacingPolicy.normalize(time.letterSpacing),
        opacity = time.opacity.coerceIn(0f, 1f),
        xDp = time.xDp.finiteOrZero(),
        yDp = time.yDp.finiteOrZero(),
    ),
    date = date.copy(
        requestedWeight = date.requestedWeight.coerceIn(100, 900),
        sizeSp = date.sizeSp.coerceAtLeast(1f),
        letterSpacing = WidgetLetterSpacingPolicy.normalize(date.letterSpacing),
        opacity = date.opacity.coerceIn(0f, 1f),
        xDp = date.xDp.finiteOrZero(),
        yDp = date.yDp.finiteOrZero(),
    ),
    background = background.copy(
        opacity = background.opacity.coerceIn(0f, 1f),
        cornerRadiusDp = background.cornerRadiusDp.coerceAtLeast(0f),
        paddingDp = background.paddingDp.coerceAtLeast(0f),
    ),
    fourByTwo = fourByTwo?.normalized(),
    fourByOne = fourByOne?.normalized(),
)

private fun ProfileOverride.normalized(): ProfileOverride = copy(
    timeWeight = timeWeight?.coerceIn(100, 900),
    dateWeight = dateWeight?.coerceIn(100, 900),
    timeSizeSp = timeSizeSp?.coerceAtLeast(1f),
    dateSizeSp = dateSizeSp?.coerceAtLeast(1f),
    timeXDp = timeXDp.finiteOrZeroIfPresent(),
    timeYDp = timeYDp.finiteOrZeroIfPresent(),
    dateXDp = dateXDp.finiteOrZeroIfPresent(),
    dateYDp = dateYDp.finiteOrZeroIfPresent(),
)

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

private fun Float?.finiteOrZeroIfPresent(): Float? = this?.finiteOrZero()
