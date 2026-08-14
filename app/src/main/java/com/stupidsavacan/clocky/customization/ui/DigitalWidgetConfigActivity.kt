package com.stupidsavacan.clocky.customization.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity

import com.android.alarmclock.DigitalAppWidgetProvider
import com.android.deskclock.R
import com.stupidsavacan.clocky.customization.storage.SharedPreferencesWidgetSettingsStore

/** Per-widget Clocky configuration entry point for the AOSP digital AppWidget. */
class DigitalWidgetConfigActivity : AppCompatActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var timeControl: WeightControl
    private lateinit var dateControl: WeightControl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // AppWidget hosts require RESULT_CANCELED until the configuration is explicitly applied.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val settings = SharedPreferencesWidgetSettingsStore(this).load(appWidgetId)
        setContentView(buildContent(settings.time.requestedWeight, settings.date.requestedWeight))
    }

    private fun buildContent(timeWeight: Int, dateWeight: Int): ScrollView {
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        content.addView(TextView(this).apply {
            setText(R.string.clocky_widget_config_title)
            textSize = 24f
            setPadding(0, 0, 0, dp(8))
        })
        content.addView(TextView(this).apply {
            setText(R.string.clocky_widget_config_weight_help)
            textSize = 14f
            setPadding(0, 0, 0, dp(24))
        })

        timeControl = addWeightControl(content, R.string.clocky_widget_time_weight, timeWeight)
        dateControl = addWeightControl(content, R.string.clocky_widget_date_weight, dateWeight)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(24), 0, 0)
        }
        buttons.addView(Button(this).apply {
            setText(android.R.string.cancel)
            setOnClickListener { finish() }
        })
        buttons.addView(Button(this).apply {
            setText(R.string.clocky_widget_apply)
            setOnClickListener { saveAndFinish() }
        })
        content.addView(
            buttons,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        return scroll
    }

    private fun addWeightControl(
        parent: LinearLayout,
        @StringRes titleRes: Int,
        initialWeight: Int,
    ): WeightControl {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                setText(titleRes)
                textSize = 16f
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        val value = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.END
        }
        header.addView(value)
        parent.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val seekBar = SeekBar(this).apply {
            max = WidgetWeightEditor.SEEK_RANGE
            progress = WidgetWeightEditor.weightToProgress(initialWeight)
            setPadding(0, 0, 0, dp(20))
        }
        fun updateValue(progress: Int) {
            value.text = WidgetWeightEditor.progressToWeight(progress).toString()
        }
        updateValue(seekBar.progress)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateValue(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        parent.addView(
            seekBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        return WeightControl(seekBar, value)
    }

    private fun saveAndFinish() {
        val store = SharedPreferencesWidgetSettingsStore(this)
        val current = store.load(appWidgetId)
        val updated = WidgetWeightEditor.applyWeights(
            settings = current,
            timeWeight = WidgetWeightEditor.progressToWeight(timeControl.seekBar.progress),
            dateWeight = WidgetWeightEditor.progressToWeight(dateControl.seekBar.progress),
        )
        store.save(updated)

        // Keep the AOSP provider as the rendering authority; ask it to relayout this widget now.
        val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            component = ComponentName(this@DigitalWidgetConfigActivity, DigitalAppWidgetProvider::class.java)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        sendBroadcast(updateIntent)

        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class WeightControl(
        val seekBar: SeekBar,
        val valueView: TextView,
    )
}
