package com.stupidsavacan.clocky.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

import com.android.alarmclock.DigitalAppWidgetProvider
import com.android.deskclock.R
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.stupidsavacan.clocky.customization.font.DigitalWidgetWeightRenderer
import com.stupidsavacan.clocky.customization.model.withWeightOverrides
import com.stupidsavacan.clocky.customization.storage.SharedPreferencesWidgetSettingsStore

/**
 * Clocky-owned configuration surface for the AOSP digital widget.
 *
 * Base time/date weights are always stored as exact 100..900 requests. Compact (4x1) and regular
 * (4x2) profile weights are optional: an unchecked override inherits the base value, while a
 * checked override stores its own exact request. RemoteViews decides the effective face at render
 * time for the current Android version.
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

        val compactTime = profileControl(
            switchId = R.id.clocky_compact_time_override_switch,
            valueId = R.id.clocky_compact_time_weight_value,
            sliderId = R.id.clocky_compact_time_weight_slider,
        )
        val compactDate = profileControl(
            switchId = R.id.clocky_compact_date_override_switch,
            valueId = R.id.clocky_compact_date_weight_value,
            sliderId = R.id.clocky_compact_date_weight_slider,
        )
        val regularTime = profileControl(
            switchId = R.id.clocky_regular_time_override_switch,
            valueId = R.id.clocky_regular_time_weight_value,
            sliderId = R.id.clocky_regular_time_weight_slider,
        )
        val regularDate = profileControl(
            switchId = R.id.clocky_regular_date_override_switch,
            valueId = R.id.clocky_regular_date_weight_value,
            sliderId = R.id.clocky_regular_date_weight_slider,
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

        bindProfileControl(
            control = compactTime,
            initialOverrideWeight = current.fourByOne?.timeWeight,
            baseSlider = timeSlider,
        )
        bindProfileControl(
            control = compactDate,
            initialOverrideWeight = current.fourByOne?.dateWeight,
            baseSlider = dateSlider,
        )
        bindProfileControl(
            control = regularTime,
            initialOverrideWeight = current.fourByTwo?.timeWeight,
            baseSlider = timeSlider,
        )
        bindProfileControl(
            control = regularDate,
            initialOverrideWeight = current.fourByTwo?.dateWeight,
            baseSlider = dateSlider,
        )

        timeSlider.addOnChangeListener { _, value, _ -> refreshTime(value.toInt()) }
        dateSlider.addOnChangeListener { _, value, _ -> refreshDate(value.toInt()) }

        saveButton.setOnClickListener {
            val updated = current.copy(
                time = current.time.copy(requestedWeight = timeSlider.value.toInt()),
                date = current.date.copy(requestedWeight = dateSlider.value.toInt()),
                fourByOne = current.fourByOne.withWeightOverrides(
                    timeWeight = compactTime.overrideValue(),
                    dateWeight = compactDate.overrideValue(),
                ),
                fourByTwo = current.fourByTwo.withWeightOverrides(
                    timeWeight = regularTime.overrideValue(),
                    dateWeight = regularDate.overrideValue(),
                ),
            )
            store.save(updated)
            requestWidgetRefresh(appWidgetId)

            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, result)
            finish()
        }
    }

    private fun profileControl(switchId: Int, valueId: Int, sliderId: Int): ProfileWeightControl =
        ProfileWeightControl(
            toggle = findViewById(switchId),
            value = findViewById(valueId),
            slider = findViewById(sliderId),
        )

    private fun bindProfileControl(
        control: ProfileWeightControl,
        initialOverrideWeight: Int?,
        baseSlider: Slider,
    ) {
        control.toggle.isChecked = initialOverrideWeight != null
        control.slider.value = (initialOverrideWeight ?: baseSlider.value.toInt()).toFloat()
        control.slider.isEnabled = control.toggle.isChecked
        control.value.text = control.slider.value.toInt().toString()

        control.slider.addOnChangeListener { _, value, _ ->
            control.value.text = value.toInt().toString()
        }
        control.toggle.setOnCheckedChangeListener { _, checked ->
            control.slider.isEnabled = checked
            if (!checked) {
                control.slider.value = baseSlider.value
            }
        }
        baseSlider.addOnChangeListener { _, value, _ ->
            if (!control.toggle.isChecked) {
                control.slider.value = value
            }
        }
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

    private data class ProfileWeightControl(
        val toggle: SwitchMaterial,
        val value: TextView,
        val slider: Slider,
    ) {
        fun overrideValue(): Int? =
            if (toggle.isChecked) slider.value.toInt() else null
    }
}
