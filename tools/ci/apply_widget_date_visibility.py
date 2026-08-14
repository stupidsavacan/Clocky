#!/usr/bin/env python3
from pathlib import Path

PATH = Path("app/src/main/java/com/android/alarmclock/DigitalAppWidgetProvider.kt")
text = PATH.read_text()

replacements = [
    (
        "                    resolvedWeights.timeWeight,\n                    resolvedWeights.dateWeight,\n            )\n",
        "                    resolvedWeights.timeWeight,\n                    resolvedWeights.dateWeight,\n                    resolvedWeights.dateEnabled,\n            )\n",
    ),
    (
        "                    dateSizePx = sizes.mFontSizePx.toFloat(),\n                    dateFormat = dateFormat,\n            )\n",
        "                    dateSizePx = sizes.mFontSizePx.toFloat(),\n                    dateFormat = dateFormat,\n                    dateEnabled = resolvedWeights.dateEnabled,\n            )\n",
    ),
    (
        "            requestedTimeWeight: Int,\n            requestedDateWeight: Int,\n        ): Sizes {\n",
        "            requestedTimeWeight: Int,\n            requestedDateWeight: Int,\n            dateEnabled: Boolean,\n        ): Sizes {\n",
    ),
    (
        "            DigitalWidgetWeightRenderer.applySizerWeights(\n                    clock, date, requestedTimeWeight, requestedDateWeight)\n",
        "            DigitalWidgetWeightRenderer.applySizerWeights(\n                    clock, date, requestedTimeWeight, requestedDateWeight, dateEnabled)\n",
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match, found {count}: {old[:100]!r}")
    text = text.replace(old, new, 1)

PATH.write_text(text)
print("Applied digital widget date visibility wiring")
