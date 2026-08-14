#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROVIDER = ROOT / "app/src/main/java/com/android/alarmclock/DigitalAppWidgetProvider.kt"
HELPER = ROOT / "app/src/main/java/com/android/alarmclock/DigitalWidgetWeightVariants.kt"
STYLES = ROOT / "app/src/main/res/values/styles.xml"
WIDGET = ROOT / "app/src/main/res/layout/digital_widget.xml"
SIZER = ROOT / "app/src/main/res/layout/digital_widget_sizer.xml"

WEIGHTS = tuple(range(100, 1000, 100))


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one old block, found {count}")
    return text.replace(old, new, 1)


def style_block() -> str:
    fallback = {
        100: ("sans-serif-thin", None),
        200: ("sans-serif-thin", None),
        300: ("sans-serif-light", None),
        400: ("sans-serif", None),
        500: ("sans-serif-medium", None),
        600: ("sans-serif-medium", None),
        700: ("sans-serif", "bold"),
        800: ("sans-serif", "bold"),
        900: ("sans-serif-black", None),
    }
    lines = [
        "",
        "    <!-- Clocky selectable font-weight faces for RemoteViews.",
        "         android:textFontWeight is honored on API 28+; fontFamily/textStyle provide",
        "         a best-effort fallback on older supported Android versions. -->",
        "    <style name=\"widget_big_weight_base\">",
        "        <item name=\"android:textSize\">@dimen/big_font_size</item>",
        "        <item name=\"android:shadowRadius\">@dimen/widget_shadow_radius</item>",
        "        <item name=\"android:shadowColor\">@color/widget_shadow_color</item>",
        "        <item name=\"android:shadowDy\">@dimen/widget_shadow_dy</item>",
        "    </style>",
        "",
    ]
    for weight in WEIGHTS:
        family, text_style = fallback[weight]
        lines.extend([
            f"    <style name=\"widget_big_weight_{weight}\" parent=\"widget_big_weight_base\">",
            f"        <item name=\"android:fontFamily\">{family}</item>",
        ])
        if text_style:
            lines.append(f"        <item name=\"android:textStyle\">{text_style}</item>")
        lines.extend([
            f"        <item name=\"android:textFontWeight\" tools:targetApi=\"28\">{weight}</item>",
            "    </style>",
            "",
            f"    <style name=\"widget_label_weight_{weight}\" parent=\"widget_label\">",
            f"        <item name=\"android:fontFamily\">{family}</item>",
        ])
        if text_style:
            lines.append(f"        <item name=\"android:textStyle\">{text_style}</item>")
        lines.extend([
            f"        <item name=\"android:textFontWeight\" tools:targetApi=\"28\">{weight}</item>",
            "    </style>",
            "",
        ])
    return "\n".join(lines)


def clock_variant(weight: int, sizer: bool = False) -> str:
    view_id = "clock" if weight == 400 else f"clock_w{weight}"
    visibility = "" if weight == 400 else '\n            android:visibility="gone"'
    indent = "        "
    return f'''{indent}<TextClock
            android:id="@+id/{view_id}"
            style="@style/widget_big_weight_{weight}"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center_horizontal|top"
            android:ellipsize="none"
            android:format12Hour="@string/lock_screen_12_hour_format"
            android:format24Hour="@string/lock_screen_24_hour_format"
            android:includeFontPadding="false"
            android:singleLine="true"
            android:textColor="@color/white"{visibility} />'''


def date_variant(weight: int) -> str:
    view_id = "date" if weight == 400 else f"date_w{weight}"
    visibility = "" if weight == 400 else '\n                android:visibility="gone"'
    return f'''            <TextClock
                android:id="@+id/{view_id}"
                style="@style/widget_label_weight_{weight}"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center"
                android:ellipsize="none"
                android:includeFontPadding="false"
                android:singleLine="true"
                android:textAllCaps="true"
                android:textColor="@color/white"{visibility} />'''


def patch_layout(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if "@+id/clock_w100" in text and "@+id/date_w100" in text:
        return

    old_clock = '''    <TextClock
        android:id="@+id/clock"
        style="@style/widget_big_thin"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal|top"
        android:ellipsize="none"
        android:format12Hour="@string/lock_screen_12_hour_format"
        android:format24Hour="@string/lock_screen_24_hour_format"
        android:includeFontPadding="false"
        android:singleLine="true"
        android:textColor="@color/white" />'''
    # The sizer has includeFontPadding before ellipsize in the upstream XML.
    old_clock_sizer = '''    <TextClock
        android:id="@+id/clock"
        style="@style/widget_big_thin"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal|top"
        android:includeFontPadding="false"
        android:ellipsize="none"
        android:format12Hour="@string/lock_screen_12_hour_format"
        android:format24Hour="@string/lock_screen_24_hour_format"
        android:singleLine="true"
        android:textColor="@color/white" />'''
    clock_old = old_clock if old_clock in text else old_clock_sizer
    if clock_old not in text:
        raise RuntimeError(f"{path.name}: clock block not found")
    clock_views = "\n\n".join(clock_variant(w) for w in WEIGHTS)
    clock_new = f'''    <FrameLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal|top">\n\n{clock_views}\n\n    </FrameLayout>'''
    text = text.replace(clock_old, clock_new, 1)

    old_date_widget = '''        <TextClock
            android:id="@+id/date"
            style="@style/widget_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:ellipsize="none"
            android:includeFontPadding="false"
            android:singleLine="true"
            android:textAllCaps="true"
            android:textColor="@color/white" />'''
    old_date_sizer = '''        <TextClock
            android:id="@+id/date"
            style="@style/widget_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:includeFontPadding="false"
            android:ellipsize="none"
            android:singleLine="true"
            android:textAllCaps="true"
            android:textColor="@color/white" />'''
    date_old = old_date_widget if old_date_widget in text else old_date_sizer
    if date_old not in text:
        raise RuntimeError(f"{path.name}: date block not found")
    date_views = "\n\n".join(date_variant(w) for w in WEIGHTS)
    date_new = f'''        <FrameLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center">\n\n{date_views}\n\n        </FrameLayout>'''
    text = text.replace(date_old, date_new, 1)
    path.write_text(text, encoding="utf-8")


def helper_source() -> str:
    return '''package com.android.alarmclock

import android.view.View
import android.widget.RemoteViews
import android.widget.TextClock
import com.android.deskclock.R
import com.stupidsavacan.clocky.customization.font.CANONICAL_WEIGHTS
import com.stupidsavacan.clocky.customization.font.FontCapabilities
import com.stupidsavacan.clocky.customization.font.FontWeightResolver

/**
 * Selects predeclared TextClock faces because RemoteViews cannot remotely replace a Typeface or
 * TextAppearance. The user-requested value remains preserved in WidgetSettings; this layer only
 * resolves the effective home-screen face.
 */
internal data class DigitalWidgetWeightSelection(
    val requestedTimeWeight: Int,
    val requestedDateWeight: Int,
    val effectiveTimeWeight: Int,
    val effectiveDateWeight: Int,
    val timeViewId: Int,
    val dateViewId: Int,
)

internal object DigitalWidgetWeightVariants {
    private val timeIds = linkedMapOf(
        100 to R.id.clock_w100,
        200 to R.id.clock_w200,
        300 to R.id.clock_w300,
        400 to R.id.clock,
        500 to R.id.clock_w500,
        600 to R.id.clock_w600,
        700 to R.id.clock_w700,
        800 to R.id.clock_w800,
        900 to R.id.clock_w900,
    )
    private val dateIds = linkedMapOf(
        100 to R.id.date_w100,
        200 to R.id.date_w200,
        300 to R.id.date_w300,
        400 to R.id.date,
        500 to R.id.date_w500,
        600 to R.id.date_w600,
        700 to R.id.date_w700,
        800 to R.id.date_w800,
        900 to R.id.date_w900,
    )
    private val capabilities = FontCapabilities(staticWeights = CANONICAL_WEIGHTS)

    fun select(timeWeight: Int, dateWeight: Int): DigitalWidgetWeightSelection {
        val time = FontWeightResolver.resolve(timeWeight, capabilities)
        val date = FontWeightResolver.resolve(dateWeight, capabilities)
        return DigitalWidgetWeightSelection(
            requestedTimeWeight = time.requested,
            requestedDateWeight = date.requested,
            effectiveTimeWeight = time.effective,
            effectiveDateWeight = date.effective,
            timeViewId = timeIds.getValue(time.effective),
            dateViewId = dateIds.getValue(date.effective),
        )
    }

    fun apply(remoteViews: RemoteViews, selection: DigitalWidgetWeightSelection, dateEnabled: Boolean) {
        timeIds.values.forEach { id ->
            remoteViews.setViewVisibility(id, if (id == selection.timeViewId) View.VISIBLE else View.GONE)
        }
        dateIds.values.forEach { id ->
            val visible = dateEnabled && id == selection.dateViewId
            remoteViews.setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)
        }
    }

    fun apply(root: View, selection: DigitalWidgetWeightSelection, dateEnabled: Boolean) {
        timeIds.values.forEach { id ->
            root.findViewById<View>(id).visibility = if (id == selection.timeViewId) View.VISIBLE else View.GONE
        }
        dateIds.values.forEach { id ->
            val visible = dateEnabled && id == selection.dateViewId
            root.findViewById<View>(id).visibility = if (visible) View.VISIBLE else View.GONE
        }
    }
}
'''


def patch_provider() -> None:
    text = PROVIDER.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "import com.android.deskclock.worldclock.CitySelectionActivity\n",
        "import com.android.deskclock.worldclock.CitySelectionActivity\nimport com.stupidsavacan.clocky.customization.storage.SharedPreferencesWidgetSettingsStore\n",
        "settings store import",
    )
    text = replace_once(
        text,
        '''            val packageName: String = context.getPackageName()\n            val rv = RemoteViews(packageName, R.layout.digital_widget)\n\n            // Tapping on the widget opens the app (if not on the lock screen).''',
        '''            val packageName: String = context.getPackageName()\n            val rv = RemoteViews(packageName, R.layout.digital_widget)\n            val settings = SharedPreferencesWidgetSettingsStore(context).load(widgetId)\n            val weightSelection = DigitalWidgetWeightVariants.select(\n                    settings.time.requestedWeight, settings.date.requestedWeight)\n            DigitalWidgetWeightVariants.apply(rv, weightSelection, settings.date.enabled)\n\n            // Tapping on the widget opens the app (if not on the lock screen).''',
        "load widget weights",
    )
    text = replace_once(
        text,
        '''            val dateFormat: CharSequence = getDateFormat(context)\n            rv.setCharSequence(R.id.date, "setFormat12Hour", dateFormat)\n            rv.setCharSequence(R.id.date, "setFormat24Hour", dateFormat)''',
        '''            val dateFormat: CharSequence = getDateFormat(context)\n            if (settings.date.enabled) {\n                rv.setCharSequence(weightSelection.dateViewId, "setFormat12Hour", dateFormat)\n                rv.setCharSequence(weightSelection.dateViewId, "setFormat24Hour", dateFormat)\n            }''',
        "date format target",
    )
    text = replace_once(
        text,
        "            val sizes = optimizeSizes(context, template, nextAlarmTime)",
        "            val sizes = optimizeSizes(context, template, nextAlarmTime, weightSelection, settings.date.enabled)",
        "weight-aware optimize call",
    )
    text = replace_once(
        text,
        '''            rv.setImageViewBitmap(R.id.nextAlarmIcon, sizes.mIconBitmap)\n            rv.setTextViewTextSize(R.id.date, COMPLEX_UNIT_PX, sizes.mFontSizePx.toFloat())\n            rv.setTextViewTextSize(R.id.nextAlarm, COMPLEX_UNIT_PX, sizes.mFontSizePx.toFloat())\n            rv.setTextViewTextSize(R.id.clock, COMPLEX_UNIT_PX, sizes.mClockFontSizePx.toFloat())''',
        '''            rv.setImageViewBitmap(R.id.nextAlarmIcon, sizes.mIconBitmap)\n            if (settings.date.enabled) {\n                rv.setTextViewTextSize(weightSelection.dateViewId, COMPLEX_UNIT_PX, sizes.mFontSizePx.toFloat())\n            }\n            rv.setTextViewTextSize(R.id.nextAlarm, COMPLEX_UNIT_PX, sizes.mFontSizePx.toFloat())\n            rv.setTextViewTextSize(weightSelection.timeViewId, COMPLEX_UNIT_PX, sizes.mClockFontSizePx.toFloat())''',
        "weight-aware remote sizes",
    )
    text = replace_once(
        text,
        '''        private fun optimizeSizes(\n            context: Context,\n            template: Sizes,\n            nextAlarmTime: String?\n        ): Sizes {''',
        '''        private fun optimizeSizes(\n            context: Context,\n            template: Sizes,\n            nextAlarmTime: String?,\n            weightSelection: DigitalWidgetWeightSelection,\n            dateEnabled: Boolean\n        ): Sizes {''',
        "optimize signature",
    )
    text = replace_once(
        text,
        '''            @SuppressLint("InflateParams") val sizer: View =\n                    inflater.inflate(R.layout.digital_widget_sizer, null /* root */)\n\n            // Configure the date to display the current date string.\n            val dateFormat: CharSequence = getDateFormat(context)\n            val date: TextClock = sizer.findViewById(R.id.date) as TextClock\n            date.setFormat12Hour(dateFormat)\n            date.setFormat24Hour(dateFormat)''',
        '''            @SuppressLint("InflateParams") val sizer: View =\n                    inflater.inflate(R.layout.digital_widget_sizer, null /* root */)\n            DigitalWidgetWeightVariants.apply(sizer, weightSelection, dateEnabled)\n\n            // Configure the date to display the current date string.\n            val dateFormat: CharSequence = getDateFormat(context)\n            val date: TextClock = sizer.findViewById(weightSelection.dateViewId) as TextClock\n            if (dateEnabled) {\n                date.setFormat12Hour(dateFormat)\n                date.setFormat24Hour(dateFormat)\n            }''',
        "weight-aware sizer",
    )
    text = text.replace(
        "measure(template, template.largestClockFontSizePx, sizer)",
        "measure(template, template.largestClockFontSizePx, sizer, weightSelection)",
    )
    text = text.replace(
        "measure(template, template.smallestClockFontSizePx, sizer)",
        "measure(template, template.smallestClockFontSizePx, sizer, weightSelection)",
    )
    text = text.replace(
        "measure(template, midFontSize, sizer)",
        "measure(template, midFontSize, sizer, weightSelection)",
    )
    text = replace_once(
        text,
        "        private fun measure(template: Sizes, clockFontSize: Int, sizer: View): Sizes {",
        "        private fun measure(template: Sizes, clockFontSize: Int, sizer: View, weightSelection: DigitalWidgetWeightSelection): Sizes {",
        "measure signature",
    )
    text = replace_once(
        text,
        '''            val date: TextClock = sizer.findViewById(R.id.date) as TextClock\n            val clock: TextClock = sizer.findViewById(R.id.clock) as TextClock''',
        '''            val date: TextClock = sizer.findViewById(weightSelection.dateViewId) as TextClock\n            val clock: TextClock = sizer.findViewById(weightSelection.timeViewId) as TextClock''',
        "measure active views",
    )
    PROVIDER.write_text(text, encoding="utf-8")


def main() -> None:
    HELPER.write_text(helper_source(), encoding="utf-8")

    styles = STYLES.read_text(encoding="utf-8")
    if 'name="widget_big_weight_100"' not in styles:
        styles = styles.replace("\n</resources>\n", style_block() + "\n</resources>\n", 1)
        STYLES.write_text(styles, encoding="utf-8")

    patch_layout(WIDGET)
    patch_layout(SIZER)
    patch_provider()


if __name__ == "__main__":
    main()
