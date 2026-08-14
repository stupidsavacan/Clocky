package com.stupidsavacan.clocky.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

import com.android.alarmclock.DigitalAppWidgetProvider
import com.android.deskclock.R
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.stupidsavacan.clocky.customization.font.DigitalWidgetWeightRenderer
import com.stupidsavacan.clocky.customization.storage.SharedPreferencesWidgetSettingsStore
import com.stupidsavacan.clocky.customization.ui.WidgetProfileWeightEditor

/**
 * Clocky-owned configuration surface for the AOSP digital widget.
 *
 * Base values are always integers in 100..900. Size-specific profiles can independently override
 * those weights; disabling a profile weight override restores inheritance from the base values.
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
        val saveButton: Button = findViewById(R.id.clocky_widget_save)

        timeSlider.value = current.time.requestedWeight.toFloat()
        dateSlider.value = current.date.requestedWeight.toFloat()

        fun refreshTime(weight: Int) {
            timeValue.text = weight.toString()
            applyPreviewWeight(timePreview, weight)
        }

        fun refreshDate(weight: Int) {
            dateValue.text = weight.toString()
            applyPreviewWeight(datePreview, weight)
        }

        refreshTime(current.time.requestedWeight)
        refreshDate(current.date.requestedWeight)

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

        timeSlider.addOnChangeListener { _, value, _ ->
            refreshTime(value.toInt())
            if (!fourByOne.enabled.isChecked) setProfileTimeWeight(fourByOne, value.toInt())
            if (!fourByTwo.enabled.isChecked) setProfileTimeWeight(fourByTwo, value.toInt())
        }
        dateSlider.addOnChangeListener { _, value, _ ->
            refreshDate(value.toInt())
            if (!fourByOne.enabled.isChecked) setProfileDateWeight(fourByOne, value.toInt())
            if (!fourByTwo.enabled.isChecked) setProfileDateWeight(fourByTwo, value.toInt())
        }

        saveButton.setOnClickListener {
            val withBaseWeights = current.copy(
                time = current.time.copy(requestedWeight = timeSlider.value.toInt()),
                date = current.date.copy(requestedWeight = dateSlider.value.toInt()),
            )
            val updated = WidgetProfileWeightEditor.apply(
                settings = withBaseWeights,
                fourByOneEnabled = fourByOne.enabled.isChecked,
                fourByOneTimeWeight = fourByOne.timeSlider.value.toInt(),
                fourByOneDateWeight = fourByOne.dateSlider.value.toInt(),
                fourByTwoEnabled = fourByTwo.enabled.isChecked,
                fourByTwoTimeWeight = fourByTwo.timeSlider.value.toInt(),
                fourByTwoDateWeight = fourByTwo.dateSlider.value.toInt(),
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

    private fun configureProfileControls(
        controls: ProfileControls,
        state: WidgetProfileWeightEditor.ProfileState,
    ) {
        controls.enabled.isChecked = state.enabled
        controls.container.visibility = if (state.enabled) View.VISIBLE else View.GONE
        setProfileTimeWeight(controls, state.timeWeight)
        setProfileDateWeight(controls, state.dateWeight)

        controls.timeSlider.addOnChangeListener { _, value, _ ->
            controls.timeValue.text = value.toInt().toString()
        }
        controls.dateSlider.addOnChangeListener { _, value, _ ->
            controls.dateValue.text = value.toInt().toString()
        }
        controls.enabled.setOnCheckedChangeListener { _, checked ->
            controls.container.visibility = if (checked) View.VISIBLE else View.GONE
        }
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
    )
}
