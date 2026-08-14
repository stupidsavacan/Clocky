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
import com.stupidsavacan.clocky.customization.font.DigitalWidgetWeightRenderer
import com.stupidsavacan.clocky.customization.storage.SharedPreferencesWidgetSettingsStore

/**
 * Minimal Clocky-owned configuration surface for the AOSP digital widget.
 *
 * The user-facing value is always an integer in 100..900. The store preserves that exact request;
 * the RemoteViews renderer decides what effective face can be displayed on the current API level.
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

        timeSlider.addOnChangeListener { _, value, _ -> refreshTime(value.toInt()) }
        dateSlider.addOnChangeListener { _, value, _ -> refreshDate(value.toInt()) }

        saveButton.setOnClickListener {
            val updated = current.copy(
                time = current.time.copy(requestedWeight = timeSlider.value.toInt()),
                date = current.date.copy(requestedWeight = dateSlider.value.toInt()),
            )
            store.save(updated)
            requestWidgetRefresh(appWidgetId)

            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, result)
            finish()
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
}
