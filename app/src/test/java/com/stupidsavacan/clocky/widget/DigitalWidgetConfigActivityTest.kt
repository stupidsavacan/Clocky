package com.stupidsavacan.clocky.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextClock
import com.android.alarmclock.DigitalAppWidgetProvider
import com.android.deskclock.R
import com.google.android.material.slider.Slider
import com.stupidsavacan.clocky.design.storage.SharedPreferencesDesignStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import com.stupidsavacan.clocky.widget.digital.DigitalWidgetFit
import android.os.Looper
import org.robolectric.annotation.Config

/**
 * Inflation smoke test for Issue #31: the configuration Activity must inflate its Material
 * controls under the theme declared in the manifest, and must return the AppWidget result the
 * host expects. Robolectric resolves real app resources/themes but is not launcher verification.
 *
 * SDK 35 is omitted because Robolectric's SDK 35 runtime needs JDK 21 and CI runs JDK 17.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 34])
class DigitalWidgetConfigActivityTest {

    private fun launchIntent(widgetId: Int?): Intent =
        Intent(RuntimeEnvironment.getApplication(), DigitalWidgetConfigActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .apply { if (widgetId != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId) }

    @Test
    fun inflatesMaterialControlsUnderManifestTheme() {
        Robolectric.buildActivity(DigitalWidgetConfigActivity::class.java, launchIntent(41))
            .setup()
            .use { controller ->
                val activity = controller.get()
                assertNotNull(activity.findViewById<Slider>(R.id.clocky_time_weight_slider))
                assertNotNull(activity.findViewById<Slider>(R.id.clocky_four_by_two_date_size_slider))
                assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
            }
    }

    @Test
    fun missingWidgetIdFinishesCanceled() {
        Robolectric.buildActivity(DigitalWidgetConfigActivity::class.java, launchIntent(null))
            .setup()
            .use { controller ->
                val activity = controller.get()
                assertTrue(activity.isFinishing)
                assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
            }
    }

    @Test
    fun backWithoutSavingLeavesResultCanceled() {
        Robolectric.buildActivity(DigitalWidgetConfigActivity::class.java, launchIntent(42))
            .setup()
            .use { controller ->
                val activity = controller.get()
                @Suppress("DEPRECATION")
                activity.onBackPressed()
                assertTrue(activity.isFinishing)
                assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
            }
    }

    @Test
    fun saveReturnsOkForTheConfiguredWidgetAndPersistsSettings() {
        val app = RuntimeEnvironment.getApplication()
        val widgetId = shadowOf(AppWidgetManager.getInstance(app))
            .createWidget(DigitalAppWidgetProvider::class.java, R.layout.clocky_digital_widget)
        Robolectric.buildActivity(DigitalWidgetConfigActivity::class.java, launchIntent(widgetId))
            .setup()
            .use { controller ->
                val activity = controller.get()
                activity.findViewById<Slider>(R.id.clocky_time_weight_slider).value = 700f
                activity.findViewById<Button>(R.id.clocky_widget_save).performClick()

                assertTrue(activity.isFinishing)
                val shadow = shadowOf(activity)
                assertEquals(Activity.RESULT_OK, shadow.resultCode)
                assertEquals(
                    widgetId,
                    shadow.resultIntent.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID,
                    ),
                )
                val saved = SharedPreferencesDesignStore(activity).load(widgetId)
                assertEquals(700, saved.design.time.style.weight)
            }
    }

    @Test
    fun previewAppliesTheProductionRemoteViewsAndFollowsEdits() {
        Robolectric.buildActivity(DigitalWidgetConfigActivity::class.java, launchIntent(44))
            .setup()
            .use { controller ->
                val activity = controller.get()
                shadowOf(Looper.getMainLooper()).idle()
                val frame = activity.findViewById<FrameLayout>(R.id.clocky_preview_frame)
                val time = DigitalWidgetFit.visibleTextIn(frame, R.id.clocky_time_slot)
                assertTrue("preview shows the widget's TextClock fragment", time is TextClock)
                assertEquals(R.id.clocky_face_w400, time!!.id)

                activity.findViewById<Slider>(R.id.clocky_time_weight_slider).value = 700f
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(
                    R.id.clocky_face_w700,
                    DigitalWidgetFit.visibleTextIn(frame, R.id.clocky_time_slot)!!.id,
                )
            }
    }
}
