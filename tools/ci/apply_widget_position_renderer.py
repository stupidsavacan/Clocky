#!/usr/bin/env python3
from pathlib import Path

PATH = Path("app/src/main/java/com/android/alarmclock/DigitalAppWidgetProvider.kt")
text = PATH.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "import com.stupidsavacan.clocky.customization.font.DigitalWidgetWeightRenderer\n",
    "import com.stupidsavacan.clocky.customization.font.DigitalWidgetWeightRenderer\n"
    "import com.stupidsavacan.clocky.customization.position.DigitalWidgetOffsetRenderer\n",
    "offset-renderer-import",
)

replace_once(
'''            val resolvedSizes = DigitalWidgetProfileResolver.resolveSizes(
                    widgetSettings,
                    options.getInt(OPTION_APPWIDGET_MIN_HEIGHT),
            )
''',
'''            val resolvedSizes = DigitalWidgetProfileResolver.resolveSizes(
                    widgetSettings,
                    options.getInt(OPTION_APPWIDGET_MIN_HEIGHT),
            )
            val resolvedOffsets = DigitalWidgetProfileResolver.resolveOffsets(
                    widgetSettings,
                    options.getInt(OPTION_APPWIDGET_MIN_HEIGHT),
            )
''',
    "resolved-offsets",
)

replace_once(
'''            DigitalWidgetWeightRenderer.applyRemoteViews(
                    remoteViews = rv,
                    requestedTimeWeight = resolvedWeights.timeWeight,
                    requestedDateWeight = resolvedWeights.dateWeight,
                    clockSizePx = sizes.mClockFontSizePx.toFloat(),
                    dateSizePx = sizes.mFontSizePx.toFloat(),
                    dateFormat = dateFormat,
                    dateEnabled = resolvedWeights.dateEnabled,
                    timeLetterSpacing = widgetSettings.time.letterSpacing,
                    dateLetterSpacing = widgetSettings.date.letterSpacing,
            )
            rv.setTextViewTextSize(R.id.nextAlarm, COMPLEX_UNIT_PX, sizes.mFontSizePx.toFloat())
''',
'''            DigitalWidgetWeightRenderer.applyRemoteViews(
                    remoteViews = rv,
                    requestedTimeWeight = resolvedWeights.timeWeight,
                    requestedDateWeight = resolvedWeights.dateWeight,
                    clockSizePx = sizes.mClockFontSizePx.toFloat(),
                    dateSizePx = sizes.mFontSizePx.toFloat(),
                    dateFormat = dateFormat,
                    dateEnabled = resolvedWeights.dateEnabled,
                    timeLetterSpacing = widgetSettings.time.letterSpacing,
                    dateLetterSpacing = widgetSettings.date.letterSpacing,
            )
            DigitalWidgetOffsetRenderer.applyRemoteViews(
                    remoteViews = rv,
                    requested = resolvedOffsets,
                    density = density,
            )
            rv.setTextViewTextSize(R.id.nextAlarm, COMPLEX_UNIT_PX, sizes.mFontSizePx.toFloat())
''',
    "apply-offset-renderer",
)

PATH.write_text(text)
print("Applied Clocky Digital Widget position renderer patch")
