#!/usr/bin/env python3
from pathlib import Path

PATH = Path("app/src/main/java/com/android/alarmclock/DigitalAppWidgetProvider.kt")
text = PATH.read_text()

replacements = [
    (
        "                    resolvedWeights.dateWeight,\n                    resolvedWeights.dateEnabled,\n            )\n",
        "                    resolvedWeights.dateWeight,\n                    resolvedWeights.dateEnabled,\n                    widgetSettings.time.letterSpacing,\n                    widgetSettings.date.letterSpacing,\n            )\n",
    ),
    (
        "                    dateFormat = dateFormat,\n                    dateEnabled = resolvedWeights.dateEnabled,\n            )\n",
        "                    dateFormat = dateFormat,\n                    dateEnabled = resolvedWeights.dateEnabled,\n                    timeLetterSpacing = widgetSettings.time.letterSpacing,\n                    dateLetterSpacing = widgetSettings.date.letterSpacing,\n            )\n",
    ),
    (
        "            requestedDateWeight: Int,\n            dateEnabled: Boolean,\n        ): Sizes {\n",
        "            requestedDateWeight: Int,\n            dateEnabled: Boolean,\n            timeLetterSpacing: Float,\n            dateLetterSpacing: Float,\n        ): Sizes {\n",
    ),
    (
        "            DigitalWidgetWeightRenderer.applySizerWeights(\n                    clock, date, requestedTimeWeight, requestedDateWeight, dateEnabled)\n",
        "            DigitalWidgetWeightRenderer.applySizerWeights(\n                    clock,\n                    date,\n                    requestedTimeWeight,\n                    requestedDateWeight,\n                    dateEnabled,\n                    timeLetterSpacing,\n                    dateLetterSpacing,\n            )\n",
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match, found {count}: {old[:100]!r}")
    text = text.replace(old, new, 1)

PATH.write_text(text)
print("Applied PR19-compatible digital widget letter-spacing wiring")
