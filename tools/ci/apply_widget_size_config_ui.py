#!/usr/bin/env python3
from pathlib import Path

ACTIVITY = Path("app/src/main/java/com/stupidsavacan/clocky/widget/DigitalWidgetConfigActivity.kt")
LAYOUT = Path("app/src/main/res/layout/clocky_digital_widget_config.xml")
STRINGS = Path("app/src/main/res/values/clocky_widget_config_strings.xml")
STRINGS_JA = Path("app/src/main/res/values-ja/clocky_widget_config_strings.xml")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


a = ACTIVITY.read_text()
a = replace_once(
    a,
    "import com.stupidsavacan.clocky.customization.ui.WidgetProfileDateVisibilityEditor\nimport com.stupidsavacan.clocky.customization.ui.WidgetProfileWeightEditor\n",
    "import com.stupidsavacan.clocky.customization.ui.WidgetProfileDateVisibilityEditor\nimport com.stupidsavacan.clocky.customization.ui.WidgetProfileSizeEditor\nimport com.stupidsavacan.clocky.customization.ui.WidgetProfileWeightEditor\n",
    "size-editor-import",
)
a = replace_once(
    a,
    '''        val timeSlider: Slider = findViewById(R.id.clocky_time_weight_slider)\n        val dateSlider: Slider = findViewById(R.id.clocky_date_weight_slider)\n        val dateEnabled: SwitchMaterial = findViewById(R.id.clocky_date_enabled)\n''',
    '''        val timeSlider: Slider = findViewById(R.id.clocky_time_weight_slider)\n        val dateSlider: Slider = findViewById(R.id.clocky_date_weight_slider)\n        val timeSizeValue: TextView = findViewById(R.id.clocky_time_size_value)\n        val dateSizeValue: TextView = findViewById(R.id.clocky_date_size_value)\n        val timeSizeSlider: Slider = findViewById(R.id.clocky_time_size_slider)\n        val dateSizeSlider: Slider = findViewById(R.id.clocky_date_size_slider)\n        val dateEnabled: SwitchMaterial = findViewById(R.id.clocky_date_enabled)\n''',
    "base-size-controls",
)
a = replace_once(
    a,
    '''        val fourByTwo = bindProfileControls(\n            switchId = R.id.clocky_four_by_two_enabled,\n            containerId = R.id.clocky_four_by_two_controls,\n            timeSliderId = R.id.clocky_four_by_two_time_weight_slider,\n            dateSliderId = R.id.clocky_four_by_two_date_weight_slider,\n            timeValueId = R.id.clocky_four_by_two_time_weight_value,\n            dateValueId = R.id.clocky_four_by_two_date_weight_value,\n        )\n''',
    '''        val fourByTwo = bindProfileControls(\n            switchId = R.id.clocky_four_by_two_enabled,\n            containerId = R.id.clocky_four_by_two_controls,\n            timeSliderId = R.id.clocky_four_by_two_time_weight_slider,\n            dateSliderId = R.id.clocky_four_by_two_date_weight_slider,\n            timeValueId = R.id.clocky_four_by_two_time_weight_value,\n            dateValueId = R.id.clocky_four_by_two_date_weight_value,\n        )\n        val fourByOneSize = bindSizeProfileControls(\n            switchId = R.id.clocky_four_by_one_size_enabled,\n            containerId = R.id.clocky_four_by_one_size_controls,\n            timeSliderId = R.id.clocky_four_by_one_time_size_slider,\n            dateSliderId = R.id.clocky_four_by_one_date_size_slider,\n            timeValueId = R.id.clocky_four_by_one_time_size_value,\n            dateValueId = R.id.clocky_four_by_one_date_size_value,\n        )\n        val fourByTwoSize = bindSizeProfileControls(\n            switchId = R.id.clocky_four_by_two_size_enabled,\n            containerId = R.id.clocky_four_by_two_size_controls,\n            timeSliderId = R.id.clocky_four_by_two_time_size_slider,\n            dateSliderId = R.id.clocky_four_by_two_date_size_slider,\n            timeValueId = R.id.clocky_four_by_two_time_size_value,\n            dateValueId = R.id.clocky_four_by_two_date_size_value,\n        )\n''',
    "profile-size-controls",
)
a = replace_once(
    a,
    '''        timeSlider.value = current.time.requestedWeight.toFloat()\n        dateSlider.value = current.date.requestedWeight.toFloat()\n        dateEnabled.isChecked = current.date.enabled\n''',
    '''        timeSlider.value = current.time.requestedWeight.toFloat()\n        dateSlider.value = current.date.requestedWeight.toFloat()\n        prepareSizeSlider(timeSizeSlider, current.time.sizeSp)\n        prepareSizeSlider(dateSizeSlider, current.date.sizeSp)\n        dateEnabled.isChecked = current.date.enabled\n''',
    "base-size-init",
)
a = replace_once(
    a,
    '''        fun refreshDate(weight: Int) {\n            dateValue.text = weight.toString()\n            applyPreviewWeight(datePreview, weight)\n        }\n\n        refreshTime(current.time.requestedWeight)\n        refreshDate(current.date.requestedWeight)\n''',
    '''        fun refreshDate(weight: Int) {\n            dateValue.text = weight.toString()\n            applyPreviewWeight(datePreview, weight)\n        }\n\n        fun refreshTimeSize(sizeSp: Float) {\n            timeSizeValue.text = getString(R.string.clocky_size_sp_value, sizeSp)\n            timePreview.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)\n        }\n\n        fun refreshDateSize(sizeSp: Float) {\n            dateSizeValue.text = getString(R.string.clocky_size_sp_value, sizeSp)\n            datePreview.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)\n        }\n\n        refreshTime(current.time.requestedWeight)\n        refreshDate(current.date.requestedWeight)\n        refreshTimeSize(current.time.sizeSp)\n        refreshDateSize(current.date.sizeSp)\n''',
    "size-preview-functions",
)
a = replace_once(
    a,
    '''        configureProfileControls(\n            fourByTwo,\n            WidgetProfileWeightEditor.state(\n                current.fourByTwo,\n                current.time.requestedWeight,\n                current.date.requestedWeight,\n            ),\n        )\n        configureDateVisibilityControls(\n''',
    '''        configureProfileControls(\n            fourByTwo,\n            WidgetProfileWeightEditor.state(\n                current.fourByTwo,\n                current.time.requestedWeight,\n                current.date.requestedWeight,\n            ),\n        )\n        configureSizeProfileControls(\n            fourByOneSize,\n            WidgetProfileSizeEditor.state(\n                current.fourByOne,\n                current.time.sizeSp,\n                current.date.sizeSp,\n            ),\n        )\n        configureSizeProfileControls(\n            fourByTwoSize,\n            WidgetProfileSizeEditor.state(\n                current.fourByTwo,\n                current.time.sizeSp,\n                current.date.sizeSp,\n            ),\n        )\n        configureDateVisibilityControls(\n''',
    "configure-size-profiles",
)
a = replace_once(
    a,
    '''        dateSlider.addOnChangeListener { _, value, _ ->\n            refreshDate(value.toInt())\n            if (!fourByOne.enabled.isChecked || fourByOne.dateInherited) {\n                setProfileDateWeight(fourByOne, value.toInt())\n            }\n            if (!fourByTwo.enabled.isChecked || fourByTwo.dateInherited) {\n                setProfileDateWeight(fourByTwo, value.toInt())\n            }\n        }\n        dateEnabled.setOnCheckedChangeListener { _, checked ->\n''',
    '''        dateSlider.addOnChangeListener { _, value, _ ->\n            refreshDate(value.toInt())\n            if (!fourByOne.enabled.isChecked || fourByOne.dateInherited) {\n                setProfileDateWeight(fourByOne, value.toInt())\n            }\n            if (!fourByTwo.enabled.isChecked || fourByTwo.dateInherited) {\n                setProfileDateWeight(fourByTwo, value.toInt())\n            }\n        }\n        timeSizeSlider.addOnChangeListener { _, value, _ ->\n            refreshTimeSize(value)\n            if (!fourByOneSize.enabled.isChecked || fourByOneSize.timeInherited) {\n                setProfileTimeSize(fourByOneSize, value)\n            }\n            if (!fourByTwoSize.enabled.isChecked || fourByTwoSize.timeInherited) {\n                setProfileTimeSize(fourByTwoSize, value)\n            }\n        }\n        dateSizeSlider.addOnChangeListener { _, value, _ ->\n            refreshDateSize(value)\n            if (!fourByOneSize.enabled.isChecked || fourByOneSize.dateInherited) {\n                setProfileDateSize(fourByOneSize, value)\n            }\n            if (!fourByTwoSize.enabled.isChecked || fourByTwoSize.dateInherited) {\n                setProfileDateSize(fourByTwoSize, value)\n            }\n        }\n        dateEnabled.setOnCheckedChangeListener { _, checked ->\n''',
    "base-size-listeners",
)
a = replace_once(
    a,
    '''            val withBaseSettings = current.copy(\n                time = current.time.copy(requestedWeight = timeSlider.value.toInt()),\n                date = current.date.copy(\n                    requestedWeight = dateSlider.value.toInt(),\n                    enabled = dateEnabled.isChecked,\n                ),\n            )\n''',
    '''            val withBaseSettings = current.copy(\n                time = current.time.copy(\n                    requestedWeight = timeSlider.value.toInt(),\n                    sizeSp = timeSizeSlider.value,\n                ),\n                date = current.date.copy(\n                    requestedWeight = dateSlider.value.toInt(),\n                    sizeSp = dateSizeSlider.value,\n                    enabled = dateEnabled.isChecked,\n                ),\n            )\n''',
    "save-base-size",
)
a = replace_once(
    a,
    '''            val updated = WidgetProfileDateVisibilityEditor.apply(\n                settings = withProfileWeights,\n''',
    '''            val withProfileSizes = WidgetProfileSizeEditor.apply(\n                settings = withProfileWeights,\n                fourByOneEnabled = fourByOneSize.enabled.isChecked,\n                fourByOneTimeSizeSp = if (fourByOneSize.timeInherited) null else fourByOneSize.timeSlider.value,\n                fourByOneDateSizeSp = if (fourByOneSize.dateInherited) null else fourByOneSize.dateSlider.value,\n                fourByTwoEnabled = fourByTwoSize.enabled.isChecked,\n                fourByTwoTimeSizeSp = if (fourByTwoSize.timeInherited) null else fourByTwoSize.timeSlider.value,\n                fourByTwoDateSizeSp = if (fourByTwoSize.dateInherited) null else fourByTwoSize.dateSlider.value,\n            )\n            val updated = WidgetProfileDateVisibilityEditor.apply(\n                settings = withProfileSizes,\n''',
    "save-profile-size",
)
a = replace_once(
    a,
    '''    private fun bindDateVisibilityControls(\n''',
    '''    private fun bindSizeProfileControls(\n        switchId: Int,\n        containerId: Int,\n        timeSliderId: Int,\n        dateSliderId: Int,\n        timeValueId: Int,\n        dateValueId: Int,\n    ): SizeProfileControls = SizeProfileControls(\n        enabled = findViewById(switchId),\n        container = findViewById(containerId),\n        timeSlider = findViewById(timeSliderId),\n        dateSlider = findViewById(dateSliderId),\n        timeValue = findViewById(timeValueId),\n        dateValue = findViewById(dateValueId),\n    )\n\n    private fun bindDateVisibilityControls(\n''',
    "bind-size-profile",
)
a = replace_once(
    a,
    '''    private fun configureDateVisibilityControls(\n''',
    '''    private fun configureSizeProfileControls(\n        controls: SizeProfileControls,\n        state: WidgetProfileSizeEditor.ProfileState,\n    ) {\n        controls.enabled.isChecked = state.enabled\n        controls.timeInherited = state.timeInherited\n        controls.dateInherited = state.dateInherited\n        controls.container.visibility = if (state.enabled) View.VISIBLE else View.GONE\n        prepareSizeSlider(controls.timeSlider, state.timeSizeSp)\n        prepareSizeSlider(controls.dateSlider, state.dateSizeSp)\n        setProfileTimeSize(controls, state.timeSizeSp)\n        setProfileDateSize(controls, state.dateSizeSp)\n\n        controls.timeSlider.addOnChangeListener { _, value, fromUser ->\n            controls.timeValue.text = getString(R.string.clocky_size_sp_value, value)\n            if (fromUser && controls.enabled.isChecked) {\n                controls.timeInherited = false\n            }\n        }\n        controls.dateSlider.addOnChangeListener { _, value, fromUser ->\n            controls.dateValue.text = getString(R.string.clocky_size_sp_value, value)\n            if (fromUser && controls.enabled.isChecked) {\n                controls.dateInherited = false\n            }\n        }\n        controls.enabled.setOnCheckedChangeListener { _, checked ->\n            controls.container.visibility = if (checked) View.VISIBLE else View.GONE\n            if (checked) {\n                controls.timeInherited = false\n                controls.dateInherited = false\n            } else {\n                controls.timeInherited = true\n                controls.dateInherited = true\n            }\n        }\n    }\n\n    private fun configureDateVisibilityControls(\n''',
    "configure-size-profile-method",
)
a = replace_once(
    a,
    '''    private fun requestWidgetRefresh(widgetId: Int) {\n''',
    '''    private fun prepareSizeSlider(slider: Slider, sizeSp: Float) {\n        val safe = sizeSp.coerceAtLeast(MIN_SIZE_SP)\n        slider.valueFrom = MIN_SIZE_SP\n        slider.valueTo = maxOf(DEFAULT_MAX_SIZE_SP, kotlin.math.ceil(safe.toDouble()).toFloat())\n        slider.value = safe\n    }\n\n    private fun setProfileTimeSize(controls: SizeProfileControls, sizeSp: Float) {\n        prepareSizeSlider(controls.timeSlider, sizeSp)\n        controls.timeValue.text = getString(R.string.clocky_size_sp_value, sizeSp)\n    }\n\n    private fun setProfileDateSize(controls: SizeProfileControls, sizeSp: Float) {\n        prepareSizeSlider(controls.dateSlider, sizeSp)\n        controls.dateValue.text = getString(R.string.clocky_size_sp_value, sizeSp)\n    }\n\n    private fun requestWidgetRefresh(widgetId: Int) {\n''',
    "size-slider-helpers",
)
a = replace_once(
    a,
    '''    private data class DateVisibilityControls(\n''',
    '''    private data class SizeProfileControls(\n        val enabled: SwitchMaterial,\n        val container: View,\n        val timeSlider: Slider,\n        val dateSlider: Slider,\n        val timeValue: TextView,\n        val dateValue: TextView,\n        var timeInherited: Boolean = true,\n        var dateInherited: Boolean = true,\n    )\n\n    private data class DateVisibilityControls(\n''',
    "size-profile-data-class",
)
a = replace_once(
    a,
    '''    private data class DateVisibilityControls(\n        val group: RadioGroup,\n        val inheritId: Int,\n        val showId: Int,\n        val hideId: Int,\n    )\n}\n''',
    '''    private data class DateVisibilityControls(\n        val group: RadioGroup,\n        val inheritId: Int,\n        val showId: Int,\n        val hideId: Int,\n    )\n\n    companion object {\n        private const val MIN_SIZE_SP = 1f\n        private const val DEFAULT_MAX_SIZE_SP = 256f\n    }\n}\n''',
    "size-constants",
)
ACTIVITY.write_text(a)

l = LAYOUT.read_text()
base_size = '''\n        <TextView\n            android:layout_width="wrap_content"\n            android:layout_height="wrap_content"\n            android:layout_marginTop="28dp"\n            android:text="@string/clocky_size_title"\n            android:textAppearance="?attr/textAppearanceHeadline6" />\n\n        <TextView\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:layout_marginTop="6dp"\n            android:text="@string/clocky_size_summary"\n            android:textAppearance="?attr/textAppearanceBody2" />\n\n        <LinearLayout\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:layout_marginTop="12dp"\n            android:gravity="center_vertical"\n            android:orientation="horizontal">\n\n            <TextView\n                android:layout_width="0dp"\n                android:layout_height="wrap_content"\n                android:layout_weight="1"\n                android:text="@string/clocky_time_size" />\n\n            <TextView\n                android:id="@+id/clocky_time_size_value"\n                android:layout_width="wrap_content"\n                android:layout_height="wrap_content"\n                android:minWidth="64dp"\n                android:gravity="end"\n                android:text="64.0 sp" />\n        </LinearLayout>\n\n        <com.google.android.material.slider.Slider\n            android:id="@+id/clocky_time_size_slider"\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:contentDescription="@string/clocky_time_size"\n            android:valueFrom="1"\n            android:valueTo="256"\n            app:labelBehavior="withinBounds" />\n\n        <LinearLayout\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:gravity="center_vertical"\n            android:orientation="horizontal">\n\n            <TextView\n                android:layout_width="0dp"\n                android:layout_height="wrap_content"\n                android:layout_weight="1"\n                android:text="@string/clocky_date_size" />\n\n            <TextView\n                android:id="@+id/clocky_date_size_value"\n                android:layout_width="wrap_content"\n                android:layout_height="wrap_content"\n                android:minWidth="64dp"\n                android:gravity="end"\n                android:text="14.0 sp" />\n        </LinearLayout>\n\n        <com.google.android.material.slider.Slider\n            android:id="@+id/clocky_date_size_slider"\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:contentDescription="@string/clocky_date_size"\n            android:valueFrom="1"\n            android:valueTo="256"\n            app:labelBehavior="withinBounds" />\n'''
l = replace_once(
    l,
    '''        <com.google.android.material.switchmaterial.SwitchMaterial\n            android:id="@+id/clocky_date_enabled"\n''',
    base_size + '''\n        <com.google.android.material.switchmaterial.SwitchMaterial\n            android:id="@+id/clocky_date_enabled"\n''',
    "layout-base-size",
)


def profile_size(prefix: str, label: str) -> str:
    return f'''\n        <com.google.android.material.switchmaterial.SwitchMaterial\n            android:id="@+id/clocky_{prefix}_size_enabled"\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:layout_marginTop="12dp"\n            android:text="@string/{label}" />\n\n        <LinearLayout\n            android:id="@+id/clocky_{prefix}_size_controls"\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:orientation="vertical"\n            android:visibility="gone">\n\n            <LinearLayout\n                android:layout_width="match_parent"\n                android:layout_height="wrap_content"\n                android:gravity="center_vertical"\n                android:orientation="horizontal">\n\n                <TextView\n                    android:layout_width="0dp"\n                    android:layout_height="wrap_content"\n                    android:layout_weight="1"\n                    android:text="@string/clocky_time_size" />\n\n                <TextView\n                    android:id="@+id/clocky_{prefix}_time_size_value"\n                    android:layout_width="wrap_content"\n                    android:layout_height="wrap_content"\n                    android:minWidth="64dp"\n                    android:gravity="end"\n                    android:text="64.0 sp" />\n            </LinearLayout>\n\n            <com.google.android.material.slider.Slider\n                android:id="@+id/clocky_{prefix}_time_size_slider"\n                android:layout_width="match_parent"\n                android:layout_height="wrap_content"\n                android:contentDescription="@string/clocky_time_size"\n                android:valueFrom="1"\n                android:valueTo="256"\n                app:labelBehavior="withinBounds" />\n\n            <LinearLayout\n                android:layout_width="match_parent"\n                android:layout_height="wrap_content"\n                android:gravity="center_vertical"\n                android:orientation="horizontal">\n\n                <TextView\n                    android:layout_width="0dp"\n                    android:layout_height="wrap_content"\n                    android:layout_weight="1"\n                    android:text="@string/clocky_date_size" />\n\n                <TextView\n                    android:id="@+id/clocky_{prefix}_date_size_value"\n                    android:layout_width="wrap_content"\n                    android:layout_height="wrap_content"\n                    android:minWidth="64dp"\n                    android:gravity="end"\n                    android:text="14.0 sp" />\n            </LinearLayout>\n\n            <com.google.android.material.slider.Slider\n                android:id="@+id/clocky_{prefix}_date_size_slider"\n                android:layout_width="match_parent"\n                android:layout_height="wrap_content"\n                android:contentDescription="@string/clocky_date_size"\n                android:valueFrom="1"\n                android:valueTo="256"\n                app:labelBehavior="withinBounds" />\n        </LinearLayout>\n'''

l = replace_once(
    l,
    '''        <TextView\n            android:layout_width="wrap_content"\n            android:layout_height="wrap_content"\n            android:layout_marginTop="12dp"\n            android:text="@string/clocky_profile_date_visibility"\n            android:textAppearance="?attr/textAppearanceSubtitle2" />\n\n        <RadioGroup\n            android:id="@+id/clocky_four_by_one_date_visibility"\n''',
    profile_size("four_by_one", "clocky_four_by_one_size_override") + '''\n        <TextView\n            android:layout_width="wrap_content"\n            android:layout_height="wrap_content"\n            android:layout_marginTop="12dp"\n            android:text="@string/clocky_profile_date_visibility"\n            android:textAppearance="?attr/textAppearanceSubtitle2" />\n\n        <RadioGroup\n            android:id="@+id/clocky_four_by_one_date_visibility"\n''',
    "layout-4x1-size",
)
l = replace_once(
    l,
    '''        <TextView\n            android:layout_width="wrap_content"\n            android:layout_height="wrap_content"\n            android:layout_marginTop="12dp"\n            android:text="@string/clocky_profile_date_visibility"\n            android:textAppearance="?attr/textAppearanceSubtitle2" />\n\n        <RadioGroup\n            android:id="@+id/clocky_four_by_two_date_visibility"\n''',
    profile_size("four_by_two", "clocky_four_by_two_size_override") + '''\n        <TextView\n            android:layout_width="wrap_content"\n            android:layout_height="wrap_content"\n            android:layout_marginTop="12dp"\n            android:text="@string/clocky_profile_date_visibility"\n            android:textAppearance="?attr/textAppearanceSubtitle2" />\n\n        <RadioGroup\n            android:id="@+id/clocky_four_by_two_date_visibility"\n''',
    "layout-4x2-size",
)
LAYOUT.write_text(l)

s = STRINGS.read_text()
s = replace_once(
    s,
    '''    <string name="clocky_show_date" tools:ignore="MissingTranslation">Show date</string>\n''',
    '''    <string name="clocky_size_title" tools:ignore="MissingTranslation">Text size</string>\n    <string name="clocky_size_summary" tools:ignore="MissingTranslation">Clocky uses the requested time and date sizes when they fit. If the widget is too small, both are scaled down together to prevent clipping.</string>\n    <string name="clocky_time_size" tools:ignore="MissingTranslation">Time size</string>\n    <string name="clocky_date_size" tools:ignore="MissingTranslation">Date size</string>\n    <string name="clocky_size_sp_value" tools:ignore="MissingTranslation">%1$.1f sp</string>\n    <string name="clocky_show_date" tools:ignore="MissingTranslation">Show date</string>\n''',
    "strings-base-size",
)
s = replace_once(
    s,
    '''    <string name="clocky_profile_overrides_summary" tools:ignore="MissingTranslation">Leave an override off to inherit the common weights above. Turn it on to give that widget height its own time and date weights.</string>\n''',
    '''    <string name="clocky_profile_overrides_summary" tools:ignore="MissingTranslation">Weights, text sizes, and date visibility can inherit the common values independently for each widget height.</string>\n''',
    "strings-profile-summary",
)
s = replace_once(
    s,
    '''    <string name="clocky_four_by_two_override" tools:ignore="MissingTranslation">Regular / 4×2 weights</string>\n''',
    '''    <string name="clocky_four_by_two_override" tools:ignore="MissingTranslation">Regular / 4×2 weights</string>\n    <string name="clocky_four_by_one_size_override" tools:ignore="MissingTranslation">Compact / 4×1 sizes</string>\n    <string name="clocky_four_by_two_size_override" tools:ignore="MissingTranslation">Regular / 4×2 sizes</string>\n''',
    "strings-profile-size",
)
STRINGS.write_text(s)

sj = STRINGS_JA.read_text()
sj = replace_once(
    sj,
    '''    <string name="clocky_show_date">日付を表示</string>\n''',
    '''    <string name="clocky_size_title">文字サイズ</string>\n    <string name="clocky_size_summary">指定サイズが収まる場合はそのまま使い、ウィジェットが小さい場合だけ時刻と日付を同じ倍率で縮小してはみ出しを防ぎます。</string>\n    <string name="clocky_time_size">時刻のサイズ</string>\n    <string name="clocky_date_size">日付のサイズ</string>\n    <string name="clocky_size_sp_value">%1$.1f sp</string>\n    <string name="clocky_show_date">日付を表示</string>\n''',
    "strings-ja-base-size",
)
sj = replace_once(
    sj,
    '''    <string name="clocky_profile_overrides_summary">上書きをOFFにすると上の共通値を継承します。ONにすると、そのウィジェット高さ専用の時刻・日付ウェイトを設定できます。</string>\n''',
    '''    <string name="clocky_profile_overrides_summary">太さ・文字サイズ・日付表示は、4×1 / 4×2ごとに共通値を個別継承できます。</string>\n''',
    "strings-ja-profile-summary",
)
sj = replace_once(
    sj,
    '''    <string name="clocky_four_by_two_override">通常 / 4×2 の太さ</string>\n''',
    '''    <string name="clocky_four_by_two_override">通常 / 4×2 の太さ</string>\n    <string name="clocky_four_by_one_size_override">コンパクト / 4×1 のサイズ</string>\n    <string name="clocky_four_by_two_size_override">通常 / 4×2 のサイズ</string>\n''',
    "strings-ja-profile-size",
)
STRINGS_JA.write_text(sj)

print("Applied Clocky Digital Widget size configuration UI patch")
