#!/usr/bin/env python3
from pathlib import Path

LAYOUT = Path("app/src/main/res/layout/clocky_digital_widget_config.xml")
ACTIVITY = Path("app/src/main/java/com/stupidsavacan/clocky/widget/DigitalWidgetConfigActivity.kt")

layout = LAYOUT.read_text()
activity = ACTIVITY.read_text()

layout_anchor = '''        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/clocky_date_enabled"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="@string/clocky_show_date" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="32dp"
            android:text="@string/clocky_profile_overrides_title"
            android:textAppearance="?attr/textAppearanceHeadline6" />
'''
layout_replacement = '''        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/clocky_date_enabled"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="@string/clocky_show_date" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="28dp"
            android:text="@string/clocky_letter_spacing_title"
            android:textAppearance="?attr/textAppearanceHeadline6" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/clocky_letter_spacing_summary"
            android:textAppearance="?attr/textAppearanceBody2" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/clocky_time_letter_spacing"
                android:textAppearance="?attr/textAppearanceSubtitle1" />

            <TextView
                android:id="@+id/clocky_time_letter_spacing_value"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:gravity="end"
                android:minWidth="72dp"
                android:text="0.00 em"
                android:textAppearance="?attr/textAppearanceSubtitle1" />
        </LinearLayout>

        <com.google.android.material.slider.Slider
            android:id="@+id/clocky_time_letter_spacing_slider"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:contentDescription="@string/clocky_time_letter_spacing"
            android:stepSize="0.01"
            android:valueFrom="-0.2"
            android:valueTo="0.5"
            app:labelBehavior="withinBounds" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/clocky_date_letter_spacing"
                android:textAppearance="?attr/textAppearanceSubtitle1" />

            <TextView
                android:id="@+id/clocky_date_letter_spacing_value"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:gravity="end"
                android:minWidth="72dp"
                android:text="0.00 em"
                android:textAppearance="?attr/textAppearanceSubtitle1" />
        </LinearLayout>

        <com.google.android.material.slider.Slider
            android:id="@+id/clocky_date_letter_spacing_slider"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:contentDescription="@string/clocky_date_letter_spacing"
            android:stepSize="0.01"
            android:valueFrom="-0.2"
            android:valueTo="0.5"
            app:labelBehavior="withinBounds" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="32dp"
            android:text="@string/clocky_profile_overrides_title"
            android:textAppearance="?attr/textAppearanceHeadline6" />
'''

activity_replacements = [
    (
        'import com.stupidsavacan.clocky.customization.font.DigitalWidgetWeightRenderer\n',
        'import com.stupidsavacan.clocky.customization.font.DigitalWidgetWeightRenderer\n'
        'import com.stupidsavacan.clocky.customization.font.WidgetLetterSpacingPolicy\n',
    ),
    (
        '        val dateEnabled: SwitchMaterial = findViewById(R.id.clocky_date_enabled)\n',
        '        val dateEnabled: SwitchMaterial = findViewById(R.id.clocky_date_enabled)\n'
        '        val timeLetterSpacingValue: TextView = findViewById(R.id.clocky_time_letter_spacing_value)\n'
        '        val dateLetterSpacingValue: TextView = findViewById(R.id.clocky_date_letter_spacing_value)\n'
        '        val timeLetterSpacingSlider: Slider = findViewById(R.id.clocky_time_letter_spacing_slider)\n'
        '        val dateLetterSpacingSlider: Slider = findViewById(R.id.clocky_date_letter_spacing_slider)\n',
    ),
    (
        '        dateEnabled.isChecked = current.date.enabled\n'
        '        datePreview.visibility = if (current.date.enabled) View.VISIBLE else View.GONE\n',
        '        dateEnabled.isChecked = current.date.enabled\n'
        '        datePreview.visibility = if (current.date.enabled) View.VISIBLE else View.GONE\n'
        '        timeLetterSpacingSlider.value = WidgetLetterSpacingPolicy.normalize(current.time.letterSpacing)\n'
        '        dateLetterSpacingSlider.value = WidgetLetterSpacingPolicy.normalize(current.date.letterSpacing)\n',
    ),
    (
        '        fun refreshDate(weight: Int) {\n'
        '            dateValue.text = weight.toString()\n'
        '            applyPreviewWeight(datePreview, weight)\n'
        '        }\n\n'
        '        refreshTime(current.time.requestedWeight)\n'
        '        refreshDate(current.date.requestedWeight)\n',
        '        fun refreshDate(weight: Int) {\n'
        '            dateValue.text = weight.toString()\n'
        '            applyPreviewWeight(datePreview, weight)\n'
        '        }\n\n'
        '        fun refreshTimeLetterSpacing(value: Float) {\n'
        '            val safe = WidgetLetterSpacingPolicy.normalize(value)\n'
        '            timeLetterSpacingValue.text = WidgetLetterSpacingPolicy.display(safe)\n'
        '            timePreview.letterSpacing = safe\n'
        '        }\n\n'
        '        fun refreshDateLetterSpacing(value: Float) {\n'
        '            val safe = WidgetLetterSpacingPolicy.normalize(value)\n'
        '            dateLetterSpacingValue.text = WidgetLetterSpacingPolicy.display(safe)\n'
        '            datePreview.letterSpacing = safe\n'
        '        }\n\n'
        '        refreshTime(current.time.requestedWeight)\n'
        '        refreshDate(current.date.requestedWeight)\n'
        '        refreshTimeLetterSpacing(current.time.letterSpacing)\n'
        '        refreshDateLetterSpacing(current.date.letterSpacing)\n',
    ),
    (
        '        dateEnabled.setOnCheckedChangeListener { _, checked ->\n'
        '            datePreview.visibility = if (checked) View.VISIBLE else View.GONE\n'
        '        }\n',
        '        timeLetterSpacingSlider.addOnChangeListener { _, value, _ ->\n'
        '            refreshTimeLetterSpacing(value)\n'
        '        }\n'
        '        dateLetterSpacingSlider.addOnChangeListener { _, value, _ ->\n'
        '            refreshDateLetterSpacing(value)\n'
        '        }\n'
        '        dateEnabled.setOnCheckedChangeListener { _, checked ->\n'
        '            datePreview.visibility = if (checked) View.VISIBLE else View.GONE\n'
        '        }\n',
    ),
    (
        '                time = current.time.copy(requestedWeight = timeSlider.value.toInt()),\n'
        '                date = current.date.copy(\n'
        '                    requestedWeight = dateSlider.value.toInt(),\n'
        '                    enabled = dateEnabled.isChecked,\n'
        '                ),\n',
        '                time = current.time.copy(\n'
        '                    requestedWeight = timeSlider.value.toInt(),\n'
        '                    letterSpacing = WidgetLetterSpacingPolicy.normalize(timeLetterSpacingSlider.value),\n'
        '                ),\n'
        '                date = current.date.copy(\n'
        '                    requestedWeight = dateSlider.value.toInt(),\n'
        '                    letterSpacing = WidgetLetterSpacingPolicy.normalize(dateLetterSpacingSlider.value),\n'
        '                    enabled = dateEnabled.isChecked,\n'
        '                ),\n',
    ),
]

if layout.count(layout_anchor) != 1:
    raise SystemExit(f"Expected one layout anchor, found {layout.count(layout_anchor)}")
layout = layout.replace(layout_anchor, layout_replacement, 1)

for old, new in activity_replacements:
    count = activity.count(old)
    if count != 1:
        raise SystemExit(f"Expected one activity match, found {count}: {old[:90]!r}")
    activity = activity.replace(old, new, 1)

LAYOUT.write_text(layout)
ACTIVITY.write_text(activity)
print("Applied widget letter-spacing settings UI wiring")
