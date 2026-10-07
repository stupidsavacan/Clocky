package com.stupidsavacan.clocky.widget.digital

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.core.content.ContextCompat
import com.android.alarmclock.DigitalAppWidgetProvider
import com.android.deskclock.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBroadcastPendingResult
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 31, 34, 35])
class AsyncProviderTest {
    @Test fun broadcastGoesAsyncAndFinishesAfterQueuedSuccessOrFailure() {
        val app = RuntimeEnvironment.getApplication()
        val wm = AppWidgetManager.getInstance(app)
        val id = shadowOf(wm).createWidget(DigitalAppWidgetProvider::class.java,R.layout.clocky_digital_widget)
        val receiver = DigitalAppWidgetProvider()
        val tasks = mutableListOf<Runnable>()
        val errors = mutableListOf<Throwable>()
        val original = DigitalWidgetUpdater.queue
        DigitalWidgetUpdater.queue = WidgetGenerationQueue(Executor { tasks.add(it) },errors::add)
        ContextCompat.registerReceiver(app, receiver,
            IntentFilter(AppWidgetManager.ACTION_APPWIDGET_UPDATE), ContextCompat.RECEIVER_EXPORTED)
        try {
            // Deliver through registered receiver so Robolectric supplies a genuine pending result.
            app.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,intArrayOf(id)))
            shadowOf(Looper.getMainLooper()).idle()
            val result = shadowOf(receiver).originalPendingResult
            assertTrue(shadowOf(receiver).wentAsync())
            assertNotNull(result)
            assertFalse(Shadow.extract<ShadowBroadcastPendingResult>(result).future.isDone)
            tasks.removeAt(0).run()
            assertTrue(Shadow.extract<ShadowBroadcastPendingResult>(result).future.isDone)
            assertTrue(errors.isEmpty())
            // Capability failure is forced inside the worker; finish must still happen.
            HostFontCapability.probeOverride = { throw IllegalStateException("probe failed") }
            app.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,intArrayOf(id)))
            shadowOf(Looper.getMainLooper()).idle()
            val failed = shadowOf(receiver).originalPendingResult
            tasks.removeAt(0).run()
            assertTrue(Shadow.extract<ShadowBroadcastPendingResult>(failed).future.isDone)
            assertEquals(1,errors.size)
        } finally {
            HostFontCapability.probeOverride = null
            DigitalWidgetUpdater.queue = original
            app.unregisterReceiver(receiver)
        }
    }
}
