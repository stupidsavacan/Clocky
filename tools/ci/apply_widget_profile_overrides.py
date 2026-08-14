#!/usr/bin/env python3
from pathlib import Path

PATH = Path("app/src/main/java/com/android/alarmclock/DigitalAppWidgetProvider.kt")
text = PATH.read_text()

replacements = [
    (
        "import com.stupidsavacan.clocky.customization.font.DigitalWidgetWeightRenderer\nimport com.stupidsavacan.clocky.customization.storage.SharedPreferencesWidgetSettingsStore\n",
        "import com.stupidsavacan.clocky.customization.font.DigitalWidgetWeightRenderer\nimport com.stupidsavacan.clocky.customization.model.DigitalWidgetProfileResolver\nimport com.stupidsavacan.clocky.customization.storage.SharedPreferencesWidgetSettingsStore\n",
    ),
    (
        "            val options = options ?: wm.getAppWidgetOptions(widgetId)\n\n            // Fetch the widget size selected by the user.\n",
        "            val options = options ?: wm.getAppWidgetOptions(widgetId)\n            val resolvedWeights = DigitalWidgetProfileResolver.resolveWeights(\n                    widgetSettings,\n                    options.getInt(OPTION_APPWIDGET_MIN_HEIGHT),\n            )\n\n            // Fetch the widget size selected by the user.\n",
    ),
    (
        "                    widgetSettings.time.requestedWeight,\n                    widgetSettings.date.requestedWeight,\n",
        "                    resolvedWeights.timeWeight,\n                    resolvedWeights.dateWeight,\n",
    ),
    (
        "                    requestedTimeWeight = widgetSettings.time.requestedWeight,\n                    requestedDateWeight = widgetSettings.date.requestedWeight,\n",
        "                    requestedTimeWeight = resolvedWeights.timeWeight,\n                    requestedDateWeight = resolvedWeights.dateWeight,\n",
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match, found {count}: {old[:90]!r}")
    text = text.replace(old, new, 1)

PATH.write_text(text)
print("Applied digital widget profile override renderer wiring")
