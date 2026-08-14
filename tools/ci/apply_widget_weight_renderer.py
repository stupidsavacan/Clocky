#!/usr/bin/env python3
from pathlib import Path

TARGET = Path("app/src/main/java/com/android/alarmclock/DigitalAppWidgetProvider.kt")
WORKFLOW = Path(".github/workflows/apply-widget-weight-renderer.yml")
SELF = Path("tools/ci/apply_widget_weight_renderer.py")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


text = TARGET.read_text()

text = replace_once(
    text,
    "import com.android.deskclock.worldclock.CitySelectionActivity\n\nimport java.util.Calendar",
    "import com.android.deskclock.worldclock.CitySelectionActivity\n"
    "import com.stupidsavacan.clocky.customization.font.DigitalWidgetWeightRenderer\n"
    "import com.stupidsavacan.clocky.customization.storage.SharedPreferencesWidgetSettingsStore\n\n"
    "import java.util.Calendar",
    "imports",
)

text = replace_once(
    text,
    "            val packageName: String = context.getPackageName()\n"
    "            val rv = RemoteViews(packageName, R.layout.digital_widget)\n",
    "            val packageName: String = context.getPackageName()\n"
    "            val rv = RemoteViews(packageName, R.layout.digital_widget)\n"
    "            val widgetSettings = SharedPreferencesWidgetSettingsStore(context).load(widgetId)\n",
    "settings load",
)

text = replace_once(
    text,
    "            val dateFormat: CharSequence = getDateFormat(context)\n"
    "            rv.setCharSequence(R.id.date, \"setFormat12Hour\", dateFormat)\n"
    "            rv.setCharSequence(R.id.date, \"setFormat24Hour\", dateFormat)\n",
    "            val dateFormat: CharSequence = getDateFormat(context)\n",
    "remote date format",
)

text = replace_once(
    text,
    "            val sizes = optimizeSizes(context, template, nextAlarmTime)\n",
    "            val sizes = optimizeSizes(\n"
    "                    context,\n"
    "                    template,\n"
    "                    nextAlarmTime,\n"
    "                    widgetSettings.time.requestedWeight,\n"
    "                    widgetSettings.date.requestedWeight,\n"
    "            )\n",
    "optimize call",
)

text = replace_once(
    text,
    "            rv.setImageViewBitmap(R.id.nextAlarmIcon, sizes.mIconBitmap)\n"
    "            rv.setTextViewTextSize(R.id.date, COMPLEX_UNIT_PX, sizes.mFontSizePx.toFloat())\n"
    "            rv.setTextViewTextSize(R.id.nextAlarm, COMPLEX_UNIT_PX, sizes.mFontSizePx.toFloat())\n"
    "            rv.setTextViewTextSize(R.id.clock, COMPLEX_UNIT_PX, sizes.mClockFontSizePx.toFloat())\n",
    "            rv.setImageViewBitmap(R.id.nextAlarmIcon, sizes.mIconBitmap)\n"
    "            DigitalWidgetWeightRenderer.applyRemoteViews(\n"
    "                    remoteViews = rv,\n"
    "                    requestedTimeWeight = widgetSettings.time.requestedWeight,\n"
    "                    requestedDateWeight = widgetSettings.date.requestedWeight,\n"
    "                    clockSizePx = sizes.mClockFontSizePx.toFloat(),\n"
    "                    dateSizePx = sizes.mFontSizePx.toFloat(),\n"
    "                    dateFormat = dateFormat,\n"
    "            )\n"
    "            rv.setTextViewTextSize(R.id.nextAlarm, COMPLEX_UNIT_PX, sizes.mFontSizePx.toFloat())\n",
    "remote weight application",
)

text = replace_once(
    text,
    "        private fun optimizeSizes(\n"
    "            context: Context,\n"
    "            template: Sizes,\n"
    "            nextAlarmTime: String?\n"
    "        ): Sizes {",
    "        private fun optimizeSizes(\n"
    "            context: Context,\n"
    "            template: Sizes,\n"
    "            nextAlarmTime: String?,\n"
    "            requestedTimeWeight: Int,\n"
    "            requestedDateWeight: Int,\n"
    "        ): Sizes {",
    "optimize signature",
)

text = replace_once(
    text,
    "            val date: TextClock = sizer.findViewById(R.id.date) as TextClock\n"
    "            date.setFormat12Hour(dateFormat)\n"
    "            date.setFormat24Hour(dateFormat)\n",
    "            val date: TextClock = sizer.findViewById(R.id.date) as TextClock\n"
    "            val clock: TextClock = sizer.findViewById(R.id.clock) as TextClock\n"
    "            date.setFormat12Hour(dateFormat)\n"
    "            date.setFormat24Hour(dateFormat)\n"
    "            DigitalWidgetWeightRenderer.applySizerWeights(\n"
    "                    clock, date, requestedTimeWeight, requestedDateWeight)\n",
    "sizer weight application",
)

TARGET.write_text(text)

# This is an implementation transport only; keep the final branch free of one-shot automation.
for path in (WORKFLOW, SELF):
    if path.exists():
        path.unlink()
