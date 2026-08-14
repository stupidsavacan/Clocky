package com.stupidsavacan.clocky.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

import com.android.alarmclock.DigitalAppWidgetProvider
import com.android.deskclock.R
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.stupidsavacan.clocky.customization.font.DigitalWidgetWeightRenderer
import com.stupidsavacan.clocky.customization.font.WidgetLetterSpacingPolicy
import com.stupidsavacan.clocky.customization.storage.SharedPreferencesWidgetSettingsStore
import com.stupidsavacan.clocky.customization.ui.WidgetProfileDateVisibilityEditor
import com.stupidsavacan.clocky.customization.ui.WidgetProfileWeightEditor

/**
 * Clocky-owned configuration surface for the AOSP digital widget.
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

        val store = SharedPreferencesWidgetSettingsStore(this)
        val current = store.load(appWidgetId)

        val timePreview: TextView = findViewById(R.id.clocky_time_preview)
        val datePreview: TextView = findViewById(R.id.clocky_date_preview)
        val timeValue: TextView = findViewById(R.id.clocky_time_weight_value)
        val dateValue: TextView = findViewById(R.id.clocky_date_weight_value)
        val timeSlider: Slider = findViewById(R.id.clocky_time_weight_slider)
        val dateSlider: Slider = findViewById(R.id.clocky_date_weight_slider)
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

        timeSlider.value = current.time.requestedWeight.toFloat()
        dateSlider.value = current.date.requestedWeight.toFloat()
        dateEnabled.isChecked = current.date.enabled
        datePreview.visibility = if (current.date.enabled) View.VISIBLE else View.GONE
        timeLetterSpacingSlider.value = WidgetLetterSpacingPolicy.normalize(current.time.letterSpacing)
        dateLetterSpacingSlider.value = WidgetLetterSpacingPolicy.normalize(current.date.letterSpacing)

        fun refreshTime(weight: Int) {
            timeValue.text = weight.toString()
            applyPreviewWeight(timePreview, weight)
        }

        fun refreshDate(weight: Int) {
            dateValue.text = weight.toString()
            applyPreviewWeight(datePreview, weight)
        }

        fun refreshTimeLetterSpacing(value: Float) {
            val safe = WidgetLetterSpacingPolicy.normalize(value)
            timeLetterSpacingValue.text = WidgetLetterSpacingPolicy.display(safe)
            timePreview.letterSpacing = safe
        }

        fun refreshDateLetterSpacing(value: Float) {
            val safe = WidgetLetterSpacingPolicy.normalize(value)
            dateLetterSpacingValue.text = WidgetLetterSpacingPolicy.display(safe)
            datePreview.letterSpacing = safe
        }

        refreshTime(current.time.requestedWeight)
        refreshDate(current.date.requestedWeight)
        refreshTimeLetterSpacing(current.time.letterSpacing)
        refreshDateLetterSpacing(current.date.letterSpacing)

        configureProfileControls(
            fourByOne,
            WidgetProfileWeightEditor.state(
                current.fourByOne,
                current.time.requestedWeight,
                current.date.requestedWeight,
            ),
        )
        configureProfileControls(
            fourByTwo,
            WidgetProfileWeightEditor.state(
                current.fourByTwo,
                current.time.requestedWeight,
                current.date.requestedWeight,
            ),
        )
        configureDateVisibilityControls(
            fourByOneDate,
            WidgetProfileDateVisibilityEditor.mode(current.fourByOne?.dateEnabled),
        )
        configureDateVisibilityControls(
            fourByTwoDate,
            WidgetProfileDateVisibilityEditor.mode(current.fourByTwo?.dateEnabled),
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
        timeLetterSpacingSlider.addOnChangeListener { _, value, _ ->
            refreshTimeLetterSpacing(value)
        }
        dateLetterSpacingSlider.addOnChangeListener { _, value, _ ->
            refreshDateLetterSpacing(value)
        }
        dateEnabled.setOnCheckedChangeListener { _, checked ->
            datePreview.visibility = if (checked) View.VISIBLE else View.GONE
        }

        saveButton.setOnClickListener {
            val withBaseSettings = current.copy(
                time = current.time.copy(
                    requestedWeight = timeSlider.value.toInt(),
                    letterSpacing = WidgetLetterSpacingPolicy.normalize(timeLetterSpacingSlider.value),
                ),
                date = current.date.copy(
                    requestedWeight = dateSlider.value.toInt(),
                    letterSpacing = WidgetLetterSpacingPolicy.normalize(dateLetterSpacingSlider.value),
                    enabled = dateEnabled.isChecked,
                ),
            )
            val withProfileWeights = WidgetProfileWeightEditor.apply(
                settings = withBaseSettings,
                fourByOneEnabled = fourByOne.enabled.isChecked,
                fourByOneTimeWeight = if (fourByOne.timeInherited) {
                    null
                } else {
                    fourByOne.timeSlider.value.toInt()
                },
                fourByOneDateWeight = if (fourByOne.dateInherited) {
                    null
                } else {
                    fourByOne.dateSlider.value.toInt()
                },
                fourByTwoEnabled = fourByTwo.enabled.isChecked,
                fourByTwoTimeWeight = if (fourByTwo.timeInherited) {
                    null
                } else {
                    fourByTwo.timeSlider.value.toInt()
                },
                fourByTwoDateWeight = if (fourByTwo.dateInherited) {
                    null
                } else {
                    fourByTwo.dateSlider.value.toInt()
                },
            )
            val updated = WidgetProfileDateVisibilityEditor.apply(
                settings = withProfileWeights,
                baseDateEnabled = dateEnabled.isChecked,
                fourByOneMode = selectedDateVisibilityMode(fourByOneDate),
                fourByTwoMode = selectedDateVisibilityMode(fourByTwoDate),
            )
            store.save(updated)
            requestWidgetRefresh(appWidgetId)

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

    private fun requestWidgetRefresh(widgetId: Int) {
        val updateIntent = Intent(this, DigitalAppWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
        sendBroadcast(updateIntent)
    }

    private fun applyPreviewWeight(view: TextView, requestedWeight: Int) {
        val effective = DigitalWidgetWeightRenderer.effectiveWeight(requestedWeight)
        view.typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val base = Typeface.create("sans-serif", Typeface.NORMAL)
            Typeface.create(base, effective, false)
        } else {
            val spec = DigitalWidgetWeightRenderer.legacyTypefaceSpec(effective)
            Typeface.create(spec.familyName, spec.style)
        }
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
    )

    private data class DateVisibilityControls(
        val group: RadioGroup,
        val inheritId: Int,
        val showId: Int,
        val hideId: Int,
    )
}
