#!/usr/bin/env python3
from pathlib import Path

PATH = Path("app/src/main/java/com/stupidsavacan/clocky/widget/DigitalWidgetConfigActivity.kt")
text = PATH.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "import com.stupidsavacan.clocky.customization.ui.WidgetProfileDateVisibilityEditor\n"
    "import com.stupidsavacan.clocky.customization.ui.WidgetProfileWeightEditor\n",
    "import com.stupidsavacan.clocky.customization.ui.WidgetProfileDateVisibilityEditor\n"
    "import com.stupidsavacan.clocky.customization.ui.WidgetProfileSizeEditor\n"
    "import com.stupidsavacan.clocky.customization.ui.WidgetProfileWeightEditor\n",
    "size-editor-import",
)

replace_once(
    "        val timeSlider: Slider = findViewById(R.id.clocky_time_weight_slider)\n"
    "        val dateSlider: Slider = findViewById(R.id.clocky_date_weight_slider)\n"
    "        val dateEnabled: SwitchMaterial = findViewById(R.id.clocky_date_enabled)\n"
    "        val timeLetterSpacingValue: TextView = findViewById(R.id.clocky_time_letter_spacing_value)\n"
    "        val dateLetterSpacingValue: TextView = findViewById(R.id.clocky_date_letter_spacing_value)\n"
    "        val timeLetterSpacingSlider: Slider = findViewById(R.id.clocky_time_letter_spacing_slider)\n"
    "        val dateLetterSpacingSlider: Slider = findViewById(R.id.clocky_date_letter_spacing_slider)\n",
    "        val timeSlider: Slider = findViewById(R.id.clocky_time_weight_slider)\n"
    "        val dateSlider: Slider = findViewById(R.id.clocky_date_weight_slider)\n"
    "        val timeSizeValue: TextView = findViewById(R.id.clocky_time_size_value)\n"
    "        val dateSizeValue: TextView = findViewById(R.id.clocky_date_size_value)\n"
    "        val timeSizeSlider: Slider = findViewById(R.id.clocky_time_size_slider)\n"
    "        val dateSizeSlider: Slider = findViewById(R.id.clocky_date_size_slider)\n"
    "        val dateEnabled: SwitchMaterial = findViewById(R.id.clocky_date_enabled)\n"
    "        val timeLetterSpacingValue: TextView = findViewById(R.id.clocky_time_letter_spacing_value)\n"
    "        val dateLetterSpacingValue: TextView = findViewById(R.id.clocky_date_letter_spacing_value)\n"
    "        val timeLetterSpacingSlider: Slider = findViewById(R.id.clocky_time_letter_spacing_slider)\n"
    "        val dateLetterSpacingSlider: Slider = findViewById(R.id.clocky_date_letter_spacing_slider)\n",
    "base-size-controls",
)

replace_once(
    "        val fourByTwo = bindProfileControls(\n"
    "            switchId = R.id.clocky_four_by_two_enabled,\n"
    "            containerId = R.id.clocky_four_by_two_controls,\n"
    "            timeSliderId = R.id.clocky_four_by_two_time_weight_slider,\n"
    "            dateSliderId = R.id.clocky_four_by_two_date_weight_slider,\n"
    "            timeValueId = R.id.clocky_four_by_two_time_weight_value,\n"
    "            dateValueId = R.id.clocky_four_by_two_date_weight_value,\n"
    "        )\n"
    "        val fourByOneDate = bindDateVisibilityControls(\n",
    "        val fourByTwo = bindProfileControls(\n"
    "            switchId = R.id.clocky_four_by_two_enabled,\n"
    "            containerId = R.id.clocky_four_by_two_controls,\n"
    "            timeSliderId = R.id.clocky_four_by_two_time_weight_slider,\n"
    "            dateSliderId = R.id.clocky_four_by_two_date_weight_slider,\n"
    "            timeValueId = R.id.clocky_four_by_two_time_weight_value,\n"
    "            dateValueId = R.id.clocky_four_by_two_date_weight_value,\n"
    "        )\n"
    "        val fourByOneSize = bindSizeProfileControls(\n"
    "            switchId = R.id.clocky_four_by_one_size_enabled,\n"
    "            containerId = R.id.clocky_four_by_one_size_controls,\n"
    "            timeSliderId = R.id.clocky_four_by_one_time_size_slider,\n"
    "            dateSliderId = R.id.clocky_four_by_one_date_size_slider,\n"
    "            timeValueId = R.id.clocky_four_by_one_time_size_value,\n"
    "            dateValueId = R.id.clocky_four_by_one_date_size_value,\n"
    "        )\n"
    "        val fourByTwoSize = bindSizeProfileControls(\n"
    "            switchId = R.id.clocky_four_by_two_size_enabled,\n"
    "            containerId = R.id.clocky_four_by_two_size_controls,\n"
    "            timeSliderId = R.id.clocky_four_by_two_time_size_slider,\n"
    "            dateSliderId = R.id.clocky_four_by_two_date_size_slider,\n"
    "            timeValueId = R.id.clocky_four_by_two_time_size_value,\n"
    "            dateValueId = R.id.clocky_four_by_two_date_size_value,\n"
    "        )\n"
    "        val fourByOneDate = bindDateVisibilityControls(\n",
    "profile-size-controls",
)

replace_once(
    "        timeSlider.value = current.time.requestedWeight.toFloat()\n"
    "        dateSlider.value = current.date.requestedWeight.toFloat()\n"
    "        dateEnabled.isChecked = current.date.enabled\n",
    "        timeSlider.value = current.time.requestedWeight.toFloat()\n"
    "        dateSlider.value = current.date.requestedWeight.toFloat()\n"
    "        prepareSizeSlider(timeSizeSlider, current.time.sizeSp)\n"
    "        prepareSizeSlider(dateSizeSlider, current.date.sizeSp)\n"
    "        dateEnabled.isChecked = current.date.enabled\n",
    "base-size-init",
)

replace_once(
    "        fun refreshDate(weight: Int) {\n"
    "            dateValue.text = weight.toString()\n"
    "            applyPreviewWeight(datePreview, weight)\n"
    "        }\n\n"
    "        fun refreshTimeLetterSpacing(value: Float) {\n",
    "        fun refreshDate(weight: Int) {\n"
    "            dateValue.text = weight.toString()\n"
    "            applyPreviewWeight(datePreview, weight)\n"
    "        }\n\n"
    "        fun refreshTimeSize(sizeSp: Float) {\n"
    "            timeSizeValue.text = getString(R.string.clocky_size_sp_value, sizeSp)\n"
    "            timePreview.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)\n"
    "        }\n\n"
    "        fun refreshDateSize(sizeSp: Float) {\n"
    "            dateSizeValue.text = getString(R.string.clocky_size_sp_value, sizeSp)\n"
    "            datePreview.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)\n"
    "        }\n\n"
    "        fun refreshTimeLetterSpacing(value: Float) {\n",
    "size-preview-functions",
)

replace_once(
    "        refreshTime(current.time.requestedWeight)\n"
    "        refreshDate(current.date.requestedWeight)\n"
    "        refreshTimeLetterSpacing(current.time.letterSpacing)\n"
    "        refreshDateLetterSpacing(current.date.letterSpacing)\n",
    "        refreshTime(current.time.requestedWeight)\n"
    "        refreshDate(current.date.requestedWeight)\n"
    "        refreshTimeSize(current.time.sizeSp)\n"
    "        refreshDateSize(current.date.sizeSp)\n"
    "        refreshTimeLetterSpacing(current.time.letterSpacing)\n"
    "        refreshDateLetterSpacing(current.date.letterSpacing)\n",
    "refresh-size-previews",
)

replace_once(
    "        configureProfileControls(\n"
    "            fourByTwo,\n"
    "            WidgetProfileWeightEditor.state(\n"
    "                current.fourByTwo,\n"
    "                current.time.requestedWeight,\n"
    "                current.date.requestedWeight,\n"
    "            ),\n"
    "        )\n"
    "        configureDateVisibilityControls(\n",
    "        configureProfileControls(\n"
    "            fourByTwo,\n"
    "            WidgetProfileWeightEditor.state(\n"
    "                current.fourByTwo,\n"
    "                current.time.requestedWeight,\n"
    "                current.date.requestedWeight,\n"
    "            ),\n"
    "        )\n"
    "        configureSizeProfileControls(\n"
    "            fourByOneSize,\n"
    "            WidgetProfileSizeEditor.state(\n"
    "                current.fourByOne,\n"
    "                current.time.sizeSp,\n"
    "                current.date.sizeSp,\n"
    "            ),\n"
    "        )\n"
    "        configureSizeProfileControls(\n"
    "            fourByTwoSize,\n"
    "            WidgetProfileSizeEditor.state(\n"
    "                current.fourByTwo,\n"
    "                current.time.sizeSp,\n"
    "                current.date.sizeSp,\n"
    "            ),\n"
    "        )\n"
    "        configureDateVisibilityControls(\n",
    "configure-size-profiles",
)

replace_once(
    "        dateSlider.addOnChangeListener { _, value, _ ->\n"
    "            refreshDate(value.toInt())\n"
    "            if (!fourByOne.enabled.isChecked || fourByOne.dateInherited) {\n"
    "                setProfileDateWeight(fourByOne, value.toInt())\n"
    "            }\n"
    "            if (!fourByTwo.enabled.isChecked || fourByTwo.dateInherited) {\n"
    "                setProfileDateWeight(fourByTwo, value.toInt())\n"
    "            }\n"
    "        }\n"
    "        timeLetterSpacingSlider.addOnChangeListener { _, value, _ ->\n",
    "        dateSlider.addOnChangeListener { _, value, _ ->\n"
    "            refreshDate(value.toInt())\n"
    "            if (!fourByOne.enabled.isChecked || fourByOne.dateInherited) {\n"
    "                setProfileDateWeight(fourByOne, value.toInt())\n"
    "            }\n"
    "            if (!fourByTwo.enabled.isChecked || fourByTwo.dateInherited) {\n"
    "                setProfileDateWeight(fourByTwo, value.toInt())\n"
    "            }\n"
    "        }\n"
    "        timeSizeSlider.addOnChangeListener { _, value, _ ->\n"
    "            refreshTimeSize(value)\n"
    "            if (!fourByOneSize.enabled.isChecked || fourByOneSize.timeInherited) {\n"
    "                setProfileTimeSize(fourByOneSize, value)\n"
    "            }\n"
    "            if (!fourByTwoSize.enabled.isChecked || fourByTwoSize.timeInherited) {\n"
    "                setProfileTimeSize(fourByTwoSize, value)\n"
    "            }\n"
    "        }\n"
    "        dateSizeSlider.addOnChangeListener { _, value, _ ->\n"
    "            refreshDateSize(value)\n"
    "            if (!fourByOneSize.enabled.isChecked || fourByOneSize.dateInherited) {\n"
    "                setProfileDateSize(fourByOneSize, value)\n"
    "            }\n"
    "            if (!fourByTwoSize.enabled.isChecked || fourByTwoSize.dateInherited) {\n"
    "                setProfileDateSize(fourByTwoSize, value)\n"
    "            }\n"
    "        }\n"
    "        timeLetterSpacingSlider.addOnChangeListener { _, value, _ ->\n",
    "base-size-listeners",
)

replace_once(
    "                time = current.time.copy(\n"
    "                    requestedWeight = timeSlider.value.toInt(),\n"
    "                    letterSpacing = WidgetLetterSpacingPolicy.normalize(timeLetterSpacingSlider.value),\n"
    "                ),\n"
    "                date = current.date.copy(\n"
    "                    requestedWeight = dateSlider.value.toInt(),\n"
    "                    letterSpacing = WidgetLetterSpacingPolicy.normalize(dateLetterSpacingSlider.value),\n"
    "                    enabled = dateEnabled.isChecked,\n"
    "                ),\n",
    "                time = current.time.copy(\n"
    "                    requestedWeight = timeSlider.value.toInt(),\n"
    "                    sizeSp = timeSizeSlider.value,\n"
    "                    letterSpacing = WidgetLetterSpacingPolicy.normalize(timeLetterSpacingSlider.value),\n"
    "                ),\n"
    "                date = current.date.copy(\n"
    "                    requestedWeight = dateSlider.value.toInt(),\n"
    "                    sizeSp = dateSizeSlider.value,\n"
    "                    letterSpacing = WidgetLetterSpacingPolicy.normalize(dateLetterSpacingSlider.value),\n"
    "                    enabled = dateEnabled.isChecked,\n"
    "                ),\n",
    "save-base-size-with-spacing",
)

replace_once(
    "            val updated = WidgetProfileDateVisibilityEditor.apply(\n"
    "                settings = withProfileWeights,\n",
    "            val withProfileSizes = WidgetProfileSizeEditor.apply(\n"
    "                settings = withProfileWeights,\n"
    "                fourByOneEnabled = fourByOneSize.enabled.isChecked,\n"
    "                fourByOneTimeSizeSp = if (fourByOneSize.timeInherited) null else fourByOneSize.timeSlider.value,\n"
    "                fourByOneDateSizeSp = if (fourByOneSize.dateInherited) null else fourByOneSize.dateSlider.value,\n"
    "                fourByTwoEnabled = fourByTwoSize.enabled.isChecked,\n"
    "                fourByTwoTimeSizeSp = if (fourByTwoSize.timeInherited) null else fourByTwoSize.timeSlider.value,\n"
    "                fourByTwoDateSizeSp = if (fourByTwoSize.dateInherited) null else fourByTwoSize.dateSlider.value,\n"
    "            )\n"
    "            val updated = WidgetProfileDateVisibilityEditor.apply(\n"
    "                settings = withProfileSizes,\n",
    "save-profile-size",
)

replace_once(
    "    private fun bindDateVisibilityControls(\n",
    "    private fun bindSizeProfileControls(\n"
    "        switchId: Int,\n"
    "        containerId: Int,\n"
    "        timeSliderId: Int,\n"
    "        dateSliderId: Int,\n"
    "        timeValueId: Int,\n"
    "        dateValueId: Int,\n"
    "    ): SizeProfileControls = SizeProfileControls(\n"
    "        enabled = findViewById(switchId),\n"
    "        container = findViewById(containerId),\n"
    "        timeSlider = findViewById(timeSliderId),\n"
    "        dateSlider = findViewById(dateSliderId),\n"
    "        timeValue = findViewById(timeValueId),\n"
    "        dateValue = findViewById(dateValueId),\n"
    "    )\n\n"
    "    private fun bindDateVisibilityControls(\n",
    "bind-size-profile-controls",
)

replace_once(
    "    private fun configureDateVisibilityControls(\n",
    "    private fun configureSizeProfileControls(\n"
    "        controls: SizeProfileControls,\n"
    "        state: WidgetProfileSizeEditor.ProfileState,\n"
    "    ) {\n"
    "        controls.enabled.isChecked = state.enabled\n"
    "        controls.timeInherited = state.timeInherited\n"
    "        controls.dateInherited = state.dateInherited\n"
    "        controls.container.visibility = if (state.enabled) View.VISIBLE else View.GONE\n"
    "        prepareSizeSlider(controls.timeSlider, state.timeSizeSp)\n"
    "        prepareSizeSlider(controls.dateSlider, state.dateSizeSp)\n"
    "        setProfileTimeSize(controls, state.timeSizeSp)\n"
    "        setProfileDateSize(controls, state.dateSizeSp)\n\n"
    "        controls.timeSlider.addOnChangeListener { _, value, fromUser ->\n"
    "            controls.timeValue.text = getString(R.string.clocky_size_sp_value, value)\n"
    "            if (fromUser && controls.enabled.isChecked) controls.timeInherited = false\n"
    "        }\n"
    "        controls.dateSlider.addOnChangeListener { _, value, fromUser ->\n"
    "            controls.dateValue.text = getString(R.string.clocky_size_sp_value, value)\n"
    "            if (fromUser && controls.enabled.isChecked) controls.dateInherited = false\n"
    "        }\n"
    "        controls.enabled.setOnCheckedChangeListener { _, checked ->\n"
    "            controls.container.visibility = if (checked) View.VISIBLE else View.GONE\n"
    "            if (checked) {\n"
    "                controls.timeInherited = false\n"
    "                controls.dateInherited = false\n"
    "            } else {\n"
    "                controls.timeInherited = true\n"
    "                controls.dateInherited = true\n"
    "            }\n"
    "        }\n"
    "    }\n\n"
    "    private fun configureDateVisibilityControls(\n",
    "configure-size-profile-controls",
)

replace_once(
    "    private fun requestWidgetRefresh(widgetId: Int) {\n",
    "    private fun prepareSizeSlider(slider: Slider, sizeSp: Float) {\n"
    "        val safe = sizeSp.coerceAtLeast(MIN_SIZE_SP)\n"
    "        slider.valueFrom = MIN_SIZE_SP\n"
    "        slider.valueTo = maxOf(DEFAULT_MAX_SIZE_SP, kotlin.math.ceil(safe.toDouble()).toFloat())\n"
    "        slider.value = safe\n"
    "    }\n\n"
    "    private fun setProfileTimeSize(controls: SizeProfileControls, sizeSp: Float) {\n"
    "        prepareSizeSlider(controls.timeSlider, sizeSp)\n"
    "        controls.timeValue.text = getString(R.string.clocky_size_sp_value, sizeSp)\n"
    "    }\n\n"
    "    private fun setProfileDateSize(controls: SizeProfileControls, sizeSp: Float) {\n"
    "        prepareSizeSlider(controls.dateSlider, sizeSp)\n"
    "        controls.dateValue.text = getString(R.string.clocky_size_sp_value, sizeSp)\n"
    "    }\n\n"
    "    private fun requestWidgetRefresh(widgetId: Int) {\n",
    "size-slider-helpers",
)

replace_once(
    "    private data class DateVisibilityControls(\n",
    "    private data class SizeProfileControls(\n"
    "        val enabled: SwitchMaterial,\n"
    "        val container: View,\n"
    "        val timeSlider: Slider,\n"
    "        val dateSlider: Slider,\n"
    "        val timeValue: TextView,\n"
    "        val dateValue: TextView,\n"
    "        var timeInherited: Boolean = true,\n"
    "        var dateInherited: Boolean = true,\n"
    "    )\n\n"
    "    private data class DateVisibilityControls(\n",
    "size-profile-data-class",
)

replace_once(
    "    private data class DateVisibilityControls(\n"
    "        val group: RadioGroup,\n"
    "        val inheritId: Int,\n"
    "        val showId: Int,\n"
    "        val hideId: Int,\n"
    "    )\n"
    "}\n",
    "    private data class DateVisibilityControls(\n"
    "        val group: RadioGroup,\n"
    "        val inheritId: Int,\n"
    "        val showId: Int,\n"
    "        val hideId: Int,\n"
    "    )\n\n"
    "    companion object {\n"
    "        private const val MIN_SIZE_SP = 1f\n"
    "        private const val DEFAULT_MAX_SIZE_SP = 256f\n"
    "    }\n"
    "}\n",
    "size-constants",
)

PATH.write_text(text)
print("Integrated size UI into letter-spacing-aware config activity")
