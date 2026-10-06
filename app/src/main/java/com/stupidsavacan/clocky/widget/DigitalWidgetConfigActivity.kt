package com.stupidsavacan.clocky.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

import com.android.deskclock.R
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.stupidsavacan.clocky.customization.font.WidgetLetterSpacingPolicy
import com.stupidsavacan.clocky.customization.ui.WidgetProfileDateVisibilityEditor
import com.stupidsavacan.clocky.customization.ui.WidgetProfileSizeEditor
import com.stupidsavacan.clocky.customization.ui.WidgetProfileWeightEditor
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.WidgetInstance
import com.stupidsavacan.clocky.design.storage.SharedPreferencesDesignStore
import com.stupidsavacan.clocky.widget.digital.DegradationNotices
import com.stupidsavacan.clocky.widget.digital.DigitalWidgetUpdater
import com.stupidsavacan.clocky.widget.digital.PreviewHost

/**
 * Clocky-owned configuration surface for the Clocky Digital widget.
 *
 * Base values are always integers in 100..900. Size-specific profiles can independently override
 * those weights; date visibility additionally keeps a tri-state inherit/show/hide value per
 * profile so a profile can change date visibility without forcing weight overrides.
 */
class DigitalWidgetConfigActivity : AppCompatActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // AppWidget hosts require configuration activities to explicitly opt in to success.
        setResult(RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.clocky_digital_widget_config)

        val store = SharedPreferencesDesignStore(this)
        val current = store.load(appWidgetId).design
        val currentStrip = current.layout.overrides[SizeClass.STRIP]
        val currentCard = current.layout.overrides[SizeClass.CARD]

        val timeValue: TextView = findViewById(R.id.clocky_time_weight_value)
        val dateValue: TextView = findViewById(R.id.clocky_date_weight_value)
        val timeSlider: Slider = findViewById(R.id.clocky_time_weight_slider)
        val dateSlider: Slider = findViewById(R.id.clocky_date_weight_slider)
        val timeSizeValue: TextView = findViewById(R.id.clocky_time_size_value)
        val dateSizeValue: TextView = findViewById(R.id.clocky_date_size_value)
        val timeSizeSlider: Slider = findViewById(R.id.clocky_time_size_slider)
        val dateSizeSlider: Slider = findViewById(R.id.clocky_date_size_slider)
        val dateEnabled: SwitchMaterial = findViewById(R.id.clocky_date_enabled)
        val timeLetterSpacingValue: TextView = findViewById(R.id.clocky_time_letter_spacing_value)
        val dateLetterSpacingValue: TextView = findViewById(R.id.clocky_date_letter_spacing_value)
        val timeLetterSpacingSlider: Slider = findViewById(R.id.clocky_time_letter_spacing_slider)
        val dateLetterSpacingSlider: Slider = findViewById(R.id.clocky_date_letter_spacing_slider)
        val fourByOne = bindProfileControls(
            switchId = R.id.clocky_four_by_one_enabled,
            containerId = R.id.clocky_four_by_one_controls,
            timeSliderId = R.id.clocky_four_by_one_time_weight_slider,
            dateSliderId = R.id.clocky_four_by_one_date_weight_slider,
            timeValueId = R.id.clocky_four_by_one_time_weight_value,
            dateValueId = R.id.clocky_four_by_one_date_weight_value,
        )
        val fourByTwo = bindProfileControls(
            switchId = R.id.clocky_four_by_two_enabled,
            containerId = R.id.clocky_four_by_two_controls,
            timeSliderId = R.id.clocky_four_by_two_time_weight_slider,
            dateSliderId = R.id.clocky_four_by_two_date_weight_slider,
            timeValueId = R.id.clocky_four_by_two_time_weight_value,
            dateValueId = R.id.clocky_four_by_two_date_weight_value,
        )
        val fourByOneSize = bindSizeProfileControls(
            switchId = R.id.clocky_four_by_one_size_enabled,
            containerId = R.id.clocky_four_by_one_size_controls,
            timeSliderId = R.id.clocky_four_by_one_time_size_slider,
            dateSliderId = R.id.clocky_four_by_one_date_size_slider,
            timeValueId = R.id.clocky_four_by_one_time_size_value,
            dateValueId = R.id.clocky_four_by_one_date_size_value,
        )
        val fourByTwoSize = bindSizeProfileControls(
            switchId = R.id.clocky_four_by_two_size_enabled,
            containerId = R.id.clocky_four_by_two_size_controls,
            timeSliderId = R.id.clocky_four_by_two_time_size_slider,
            dateSliderId = R.id.clocky_four_by_two_date_size_slider,
            timeValueId = R.id.clocky_four_by_two_time_size_value,
            dateValueId = R.id.clocky_four_by_two_date_size_value,
        )
        val fourByOneDate = bindDateVisibilityControls(
            groupId = R.id.clocky_four_by_one_date_visibility,
            inheritId = R.id.clocky_four_by_one_date_inherit,
            showId = R.id.clocky_four_by_one_date_show,
            hideId = R.id.clocky_four_by_one_date_hide,
        )
        val fourByTwoDate = bindDateVisibilityControls(
            groupId = R.id.clocky_four_by_two_date_visibility,
            inheritId = R.id.clocky_four_by_two_date_inherit,
            showId = R.id.clocky_four_by_two_date_show,
            hideId = R.id.clocky_four_by_two_date_hide,
        )
        val saveButton: Button = findViewById(R.id.clocky_widget_save)
        val previewSizeClass: MaterialButtonToggleGroup = findViewById(R.id.clocky_preview_size_class)
        val previewNotice: TextView = findViewById(R.id.clocky_preview_notice)

        timeSlider.value = current.time.style.weight.toFloat()
        dateSlider.value = current.date.style.weight.toFloat()
        prepareSizeSlider(timeSizeSlider, current.time.style.sizeSp)
        prepareSizeSlider(dateSizeSlider, current.date.style.sizeSp)
        dateEnabled.isChecked = current.date.visible
        timeLetterSpacingSlider.value = WidgetLetterSpacingPolicy.normalize(current.time.style.letterSpacingEm)
        dateLetterSpacingSlider.value = WidgetLetterSpacingPolicy.normalize(current.date.style.letterSpacingEm)

        fun refreshTime(weight: Int) {
            timeValue.text = weight.toString()
        }

        fun refreshDate(weight: Int) {
            dateValue.text = weight.toString()
        }

        fun refreshTimeSize(sizeSp: Float) {
            timeSizeValue.text = getString(R.string.clocky_size_sp_value, sizeSp)
        }

        fun refreshDateSize(sizeSp: Float) {
            dateSizeValue.text = getString(R.string.clocky_size_sp_value, sizeSp)
        }

        fun refreshTimeLetterSpacing(value: Float) {
            val safe = WidgetLetterSpacingPolicy.normalize(value)
            timeLetterSpacingValue.text = WidgetLetterSpacingPolicy.display(safe)
        }

        fun refreshDateLetterSpacing(value: Float) {
            val safe = WidgetLetterSpacingPolicy.normalize(value)
            dateLetterSpacingValue.text = WidgetLetterSpacingPolicy.display(safe)
        }

        refreshTime(current.time.style.weight)
        refreshDate(current.date.style.weight)
        refreshTimeSize(current.time.style.sizeSp)
        refreshDateSize(current.date.style.sizeSp)
        refreshTimeLetterSpacing(current.time.style.letterSpacingEm)
        refreshDateLetterSpacing(current.date.style.letterSpacingEm)

        configureProfileControls(
            fourByOne,
            WidgetProfileWeightEditor.state(
                currentStrip,
                current.time.style.weight,
                current.date.style.weight,
            ),
        )
        configureProfileControls(
            fourByTwo,
            WidgetProfileWeightEditor.state(
                currentCard,
                current.time.style.weight,
                current.date.style.weight,
            ),
        )
        configureSizeProfileControls(
            fourByOneSize,
            WidgetProfileSizeEditor.state(
                currentStrip,
                current.time.style.sizeSp,
                current.date.style.sizeSp,
            ),
        )
        configureSizeProfileControls(
            fourByTwoSize,
            WidgetProfileSizeEditor.state(
                currentCard,
                current.time.style.sizeSp,
                current.date.style.sizeSp,
            ),
        )
        configureDateVisibilityControls(
            fourByOneDate,
            WidgetProfileDateVisibilityEditor.mode(currentStrip?.dateVisible),
        )
        configureDateVisibilityControls(
            fourByTwoDate,
            WidgetProfileDateVisibilityEditor.mode(currentCard?.dateVisible),
        )

        timeSlider.addOnChangeListener { _, value, _ ->
            refreshTime(value.toInt())
            if (!fourByOne.enabled.isChecked || fourByOne.timeInherited) {
                setProfileTimeWeight(fourByOne, value.toInt())
            }
            if (!fourByTwo.enabled.isChecked || fourByTwo.timeInherited) {
                setProfileTimeWeight(fourByTwo, value.toInt())
            }
        }
        dateSlider.addOnChangeListener { _, value, _ ->
            refreshDate(value.toInt())
            if (!fourByOne.enabled.isChecked || fourByOne.dateInherited) {
                setProfileDateWeight(fourByOne, value.toInt())
            }
            if (!fourByTwo.enabled.isChecked || fourByTwo.dateInherited) {
                setProfileDateWeight(fourByTwo, value.toInt())
            }
        }
        timeSizeSlider.addOnChangeListener { _, value, _ ->
            refreshTimeSize(value)
            if (!fourByOneSize.enabled.isChecked || fourByOneSize.timeInherited) {
                setProfileTimeSize(fourByOneSize, value)
            }
            if (!fourByTwoSize.enabled.isChecked || fourByTwoSize.timeInherited) {
                setProfileTimeSize(fourByTwoSize, value)
            }
        }
        dateSizeSlider.addOnChangeListener { _, value, _ ->
            refreshDateSize(value)
            if (!fourByOneSize.enabled.isChecked || fourByOneSize.dateInherited) {
                setProfileDateSize(fourByOneSize, value)
            }
            if (!fourByTwoSize.enabled.isChecked || fourByTwoSize.dateInherited) {
                setProfileDateSize(fourByTwoSize, value)
            }
        }
        timeLetterSpacingSlider.addOnChangeListener { _, value, _ ->
            refreshTimeLetterSpacing(value)
        }
        dateLetterSpacingSlider.addOnChangeListener { _, value, _ ->
            refreshDateLetterSpacing(value)
        }

        fun currentDesign(): DigitalDesign {
            val withBaseSettings = current.copy(
                time = current.time.copy(
                    style = current.time.style.copy(
                        weight = timeSlider.value.toInt(),
                        sizeSp = timeSizeSlider.value,
                        letterSpacingEm = WidgetLetterSpacingPolicy.normalize(timeLetterSpacingSlider.value),
                    ),
                ),
                date = current.date.copy(
                    visible = dateEnabled.isChecked,
                    style = current.date.style.copy(
                        weight = dateSlider.value.toInt(),
                        sizeSp = dateSizeSlider.value,
                        letterSpacingEm = WidgetLetterSpacingPolicy.normalize(dateLetterSpacingSlider.value),
                    ),
                ),
            )
            val withProfileWeights = WidgetProfileWeightEditor.apply(
                design = withBaseSettings,
                stripEnabled = fourByOne.enabled.isChecked,
                stripTimeWeight = if (fourByOne.timeInherited) {
                    null
                } else {
                    fourByOne.timeSlider.value.toInt()
                },
                stripDateWeight = if (fourByOne.dateInherited) {
                    null
                } else {
                    fourByOne.dateSlider.value.toInt()
                },
                cardEnabled = fourByTwo.enabled.isChecked,
                cardTimeWeight = if (fourByTwo.timeInherited) {
                    null
                } else {
                    fourByTwo.timeSlider.value.toInt()
                },
                cardDateWeight = if (fourByTwo.dateInherited) {
                    null
                } else {
                    fourByTwo.dateSlider.value.toInt()
                },
            )
            val withProfileSizes = WidgetProfileSizeEditor.apply(
                design = withProfileWeights,
                stripEnabled = fourByOneSize.enabled.isChecked,
                stripTimeSizeSp = if (fourByOneSize.timeInherited) null else fourByOneSize.timeSlider.value,
                stripDateSizeSp = if (fourByOneSize.dateInherited) null else fourByOneSize.dateSlider.value,
                cardEnabled = fourByTwoSize.enabled.isChecked,
                cardTimeSizeSp = if (fourByTwoSize.timeInherited) null else fourByTwoSize.timeSlider.value,
                cardDateSizeSp = if (fourByTwoSize.dateInherited) null else fourByTwoSize.dateSlider.value,
            )
            return WidgetProfileDateVisibilityEditor.apply(
                design = withProfileSizes,
                baseDateVisible = dateEnabled.isChecked,
                stripMode = selectedDateVisibilityMode(fourByOneDate),
                cardMode = selectedDateVisibilityMode(fourByTwoDate),
            )
        }

        val previewHost = PreviewHost(findViewById<FrameLayout>(R.id.clocky_preview_frame), appWidgetId) { spec ->
            val notices = DegradationNotices.describe(this, spec.degradations)
            previewNotice.text = notices.joinToString(separator = System.lineSeparator())
            previewNotice.visibility = if (notices.isEmpty()) View.GONE else View.VISIBLE
        }
        previewSizeClass.check(
            if (previewHost.hostSizeClass() == SizeClass.STRIP) R.id.clocky_preview_strip else R.id.clocky_preview_card,
        )
        fun selectedPreviewClass(): SizeClass =
            if (previewSizeClass.checkedButtonId == R.id.clocky_preview_strip) SizeClass.STRIP else SizeClass.CARD
        val schedulePreview = { previewHost.schedule(currentDesign(), selectedPreviewClass()) }
        previewSizeClass.addOnButtonCheckedListener { _, _, isChecked -> if (isChecked) schedulePreview() }
        listOf(
            timeSlider, dateSlider, timeSizeSlider, dateSizeSlider, timeLetterSpacingSlider, dateLetterSpacingSlider,
            fourByOne.timeSlider, fourByOne.dateSlider, fourByTwo.timeSlider, fourByTwo.dateSlider,
            fourByOneSize.timeSlider, fourByOneSize.dateSlider, fourByTwoSize.timeSlider, fourByTwoSize.dateSlider,
        ).forEach { slider -> slider.addOnChangeListener { _, _, _ -> schedulePreview() } }
        dateEnabled.setOnCheckedChangeListener { _, _ -> schedulePreview() }
        listOf(fourByOne, fourByTwo).forEach { controls -> controls.onToggle = schedulePreview }
        listOf(fourByOneSize, fourByTwoSize).forEach { controls -> controls.onToggle = schedulePreview }
        listOf(fourByOneDate, fourByTwoDate).forEach { controls ->
            controls.group.setOnCheckedChangeListener { _, _ -> schedulePreview() }
        }
        schedulePreview()

        saveButton.setOnClickListener {
            val updated = currentDesign()
            store.save(WidgetInstance(appWidgetId, updated))
            DigitalWidgetUpdater.update(this, AppWidgetManager.getInstance(this), appWidgetId)

            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, result)
            finish()
        }
    }

    private fun bindProfileControls(
        switchId: Int,
        containerId: Int,
        timeSliderId: Int,
        dateSliderId: Int,
        timeValueId: Int,
        dateValueId: Int,
    ): ProfileControls = ProfileControls(
        enabled = findViewById(switchId),
        container = findViewById(containerId),
        timeSlider = findViewById(timeSliderId),
        dateSlider = findViewById(dateSliderId),
        timeValue = findViewById(timeValueId),
        dateValue = findViewById(dateValueId),
    )

    private fun bindSizeProfileControls(
        switchId: Int,
        containerId: Int,
        timeSliderId: Int,
        dateSliderId: Int,
        timeValueId: Int,
        dateValueId: Int,
    ): SizeProfileControls = SizeProfileControls(
        enabled = findViewById(switchId),
        container = findViewById(containerId),
        timeSlider = findViewById(timeSliderId),
        dateSlider = findViewById(dateSliderId),
        timeValue = findViewById(timeValueId),
        dateValue = findViewById(dateValueId),
    )

    private fun bindDateVisibilityControls(
        groupId: Int,
        inheritId: Int,
        showId: Int,
        hideId: Int,
    ): DateVisibilityControls = DateVisibilityControls(
        group = findViewById(groupId),
        inheritId = inheritId,
        showId = showId,
        hideId = hideId,
    )

    private fun configureProfileControls(
        controls: ProfileControls,
        state: WidgetProfileWeightEditor.ProfileState,
    ) {
        controls.enabled.isChecked = state.enabled
        controls.timeInherited = state.timeInherited
        controls.dateInherited = state.dateInherited
        controls.container.visibility = if (state.enabled) View.VISIBLE else View.GONE
        setProfileTimeWeight(controls, state.timeWeight)
        setProfileDateWeight(controls, state.dateWeight)

        controls.timeSlider.addOnChangeListener { _, value, fromUser ->
            controls.timeValue.text = value.toInt().toString()
            if (fromUser && controls.enabled.isChecked) {
                controls.timeInherited = false
            }
        }
        controls.dateSlider.addOnChangeListener { _, value, fromUser ->
            controls.dateValue.text = value.toInt().toString()
            if (fromUser && controls.enabled.isChecked) {
                controls.dateInherited = false
            }
        }
        controls.enabled.setOnCheckedChangeListener { _, checked ->
            controls.container.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) {
                controls.timeInherited = false
                controls.dateInherited = false
            } else {
                controls.timeInherited = true
                controls.dateInherited = true
            }
            controls.onToggle()
        }
    }

    private fun configureSizeProfileControls(
        controls: SizeProfileControls,
        state: WidgetProfileSizeEditor.ProfileState,
    ) {
        controls.enabled.isChecked = state.enabled
        controls.timeInherited = state.timeInherited
        controls.dateInherited = state.dateInherited
        controls.container.visibility = if (state.enabled) View.VISIBLE else View.GONE
        prepareSizeSlider(controls.timeSlider, state.timeSizeSp)
        prepareSizeSlider(controls.dateSlider, state.dateSizeSp)
        setProfileTimeSize(controls, state.timeSizeSp)
        setProfileDateSize(controls, state.dateSizeSp)

        controls.timeSlider.addOnChangeListener { _, value, fromUser ->
            controls.timeValue.text = getString(R.string.clocky_size_sp_value, value)
            if (fromUser && controls.enabled.isChecked) controls.timeInherited = false
        }
        controls.dateSlider.addOnChangeListener { _, value, fromUser ->
            controls.dateValue.text = getString(R.string.clocky_size_sp_value, value)
            if (fromUser && controls.enabled.isChecked) controls.dateInherited = false
        }
        controls.enabled.setOnCheckedChangeListener { _, checked ->
            controls.container.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) {
                controls.timeInherited = false
                controls.dateInherited = false
            } else {
                controls.timeInherited = true
                controls.dateInherited = true
            }
            controls.onToggle()
        }
    }

    private fun configureDateVisibilityControls(
        controls: DateVisibilityControls,
        mode: WidgetProfileDateVisibilityEditor.Mode,
    ) {
        val selectedId = when (mode) {
            WidgetProfileDateVisibilityEditor.Mode.INHERIT -> controls.inheritId
            WidgetProfileDateVisibilityEditor.Mode.SHOW -> controls.showId
            WidgetProfileDateVisibilityEditor.Mode.HIDE -> controls.hideId
        }
        controls.group.check(selectedId)
    }

    private fun selectedDateVisibilityMode(
        controls: DateVisibilityControls,
    ): WidgetProfileDateVisibilityEditor.Mode = when (controls.group.checkedRadioButtonId) {
        controls.showId -> WidgetProfileDateVisibilityEditor.Mode.SHOW
        controls.hideId -> WidgetProfileDateVisibilityEditor.Mode.HIDE
        else -> WidgetProfileDateVisibilityEditor.Mode.INHERIT
    }

    private fun setProfileTimeWeight(controls: ProfileControls, weight: Int) {
        controls.timeSlider.value = weight.coerceIn(100, 900).toFloat()
        controls.timeValue.text = weight.coerceIn(100, 900).toString()
    }

    private fun setProfileDateWeight(controls: ProfileControls, weight: Int) {
        controls.dateSlider.value = weight.coerceIn(100, 900).toFloat()
        controls.dateValue.text = weight.coerceIn(100, 900).toString()
    }

    private fun prepareSizeSlider(slider: Slider, sizeSp: Float) {
        val safe = sizeSp.coerceAtLeast(MIN_SIZE_SP)
        slider.valueFrom = MIN_SIZE_SP
        slider.valueTo = maxOf(DEFAULT_MAX_SIZE_SP, kotlin.math.ceil(safe.toDouble()).toFloat())
        slider.value = safe
    }

    private fun setProfileTimeSize(controls: SizeProfileControls, sizeSp: Float) {
        prepareSizeSlider(controls.timeSlider, sizeSp)
        controls.timeValue.text = getString(R.string.clocky_size_sp_value, sizeSp)
    }

    private fun setProfileDateSize(controls: SizeProfileControls, sizeSp: Float) {
        prepareSizeSlider(controls.dateSlider, sizeSp)
        controls.dateValue.text = getString(R.string.clocky_size_sp_value, sizeSp)
    }

    private data class ProfileControls(
        val enabled: SwitchMaterial,
        val container: View,
        val timeSlider: Slider,
        val dateSlider: Slider,
        val timeValue: TextView,
        val dateValue: TextView,
        var timeInherited: Boolean = true,
        var dateInherited: Boolean = true,
        var onToggle: () -> Unit = {},
    )

    private data class SizeProfileControls(
        val enabled: SwitchMaterial,
        val container: View,
        val timeSlider: Slider,
        val dateSlider: Slider,
        val timeValue: TextView,
        val dateValue: TextView,
        var timeInherited: Boolean = true,
        var dateInherited: Boolean = true,
        var onToggle: () -> Unit = {},
    )

    private data class DateVisibilityControls(
        val group: RadioGroup,
        val inheritId: Int,
        val showId: Int,
        val hideId: Int,
    )

    companion object {
        private const val MIN_SIZE_SP = 1f
        private const val DEFAULT_MAX_SIZE_SP = 256f
    }
}
