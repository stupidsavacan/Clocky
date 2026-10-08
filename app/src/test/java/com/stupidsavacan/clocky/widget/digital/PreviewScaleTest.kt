package com.stupidsavacan.clocky.widget.digital

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.studio.DesignEdits
import com.stupidsavacan.clocky.studio.EditScope
import com.stupidsavacan.clocky.studio.TextTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class PreviewScaleTest {
    @Test fun replacementIsAlreadyScaledBeforePostedWorkForEveryEdit() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            val parent = FrameLayout(activity)
            val frame = FrameLayout(activity)
            parent.addView(frame, FrameLayout.LayoutParams(120, 100))
            activity.setContentView(parent)
            parent.measure(View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
            parent.layout(0, 0, 120, 900)
            val preview = PreviewHost(frame, AppWidgetManager.INVALID_APPWIDGET_ID, 100)
            val design = DigitalDesign()
            val edits = listOf<(DigitalDesign) -> DigitalDesign>(
                { DesignEdits.setOffset(it, TextTarget.TIME, 12f, 8f, EditScope.ALL) },
                { DesignEdits.setSize(it, TextTarget.TIME, 72f, EditScope.ALL) },
                { DesignEdits.setOpacity(it, TextTarget.TIME, 0.5f) },
                { DesignEdits.setWeight(it, TextTarget.TIME, 700, EditScope.ALL) },
            )
            SizeClass.entries.forEach { sizeClass ->
                preview.render(design, sizeClass)
                shadowOf(Looper.getMainLooper()).idle()
                val scale = frame.getChildAt(0).scaleX
                assertTrue(scale < 1f)
                edits.forEach { edit ->
                    preview.render(edit(design), sizeClass)
                    // The intermediate state is drawable before View.post runs, not just the settled view.
                    assertEquals(scale, frame.getChildAt(0).scaleX, 0.0001f)
                    assertEquals(scale, frame.getChildAt(0).scaleY, 0.0001f)
                    shadowOf(Looper.getMainLooper()).idle()
                    assertEquals(scale, frame.getChildAt(0).scaleX, 0.0001f)
                }
            }
        }
    }
    @Test fun deferredFitIgnoresAReplacedChildAndUsesLatestParentWidth() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val parent = FrameLayout(controller.get())
            val frame = FrameLayout(controller.get())
            parent.addView(frame)
            controller.get().setContentView(parent)
            var fitted = 0
            val preview = PreviewHost(frame, AppWidgetManager.INVALID_APPWIDGET_ID, 100,
                onFitted = { fitted++ })
            preview.render(DigitalDesign(), SizeClass.LARGE)
            preview.render(DigitalDesign(), SizeClass.STRIP)
            val current = frame.getChildAt(0)
            parent.layout(0, 0, 120, 900)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(3, fitted) // two synchronous fits, only the latest deferred fit
            assertTrue(current.scaleX < 1f)
            assertEquals((current.layoutParams.width * current.scaleX).toInt(), frame.layoutParams.width)
        }
    }

}
