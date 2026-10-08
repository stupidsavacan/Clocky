package com.stupidsavacan.clocky.widget.studio

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.android.alarmclock.DigitalAppWidgetProvider
import com.android.deskclock.R
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.WidgetInstance
import com.stupidsavacan.clocky.design.storage.SharedPreferencesDesignStore
import com.stupidsavacan.clocky.studio.DesignEdits
import com.stupidsavacan.clocky.studio.EditScope
import com.stupidsavacan.clocky.studio.Slot
import com.stupidsavacan.clocky.studio.TextTarget
import com.stupidsavacan.clocky.widget.digital.HostFontCapability
import com.stupidsavacan.clocky.widget.studio.canvas.AppliedGeometry
import com.stupidsavacan.clocky.widget.studio.canvas.CanvasOverlayView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The Studio canvas through real MotionEvents on the real StudioActivity: selection, hit testing on
 * the applied RemoteViews, drag -> DesignEdits -> EditSession (undo coalescing, scope, RTL), snap and
 * the second-finger rule, cancel, and the API < 31 contract. Launchers and haptics are not covered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CanvasStudioTest {
    private val app = RuntimeEnvironment.getApplication()
    private val store = SharedPreferencesDesignStore(app)
    private val density get() = app.resources.displayMetrics.density

    @Before fun capableHost() {
        HostFontCapability.probeOverride = { HostFontCapability.Support(bundledFonts = true, latinAmPmMarker = true) }
    }

    @After fun resetHost() {
        HostFontCapability.probeOverride = null
    }

    private fun widget(): Int = shadowOf(AppWidgetManager.getInstance(app))
        .createWidget(DigitalAppWidgetProvider::class.java, R.layout.clocky_digital_widget)

    private fun open(id: Int, draft: DigitalDesign? = null): ActivityController<StudioActivity> {
        val intent = Intent(app, StudioActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        return Robolectric.buildActivity(StudioActivity::class.java, intent).setup().also { idle() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun ViewGroup.all(): List<View> = (0 until childCount).flatMap { i ->
        val c = getChildAt(i)
        listOf(c) + ((c as? ViewGroup)?.all() ?: emptyList())
    }

    private fun StudioActivity.panelView(): ViewGroup = findViewById(R.id.clocky_preview_panel)
    private fun StudioActivity.overlay(): CanvasOverlayView = panelView().all().filterIsInstance<CanvasOverlayView>().single()

    /** Robolectric does not run a window traversal on its own; lay the preview out like the device would. */
    private fun StudioActivity.layoutPreview(): AppGeo {
        val panel = panelView()
        panel.measure(
            View.MeasureSpec.makeMeasureSpec((1000 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((2000 * density).toInt(), View.MeasureSpec.AT_MOST), // the panel is wrap_content
        )
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        val geo = overlay().currentGeometry()
        return AppGeo(geo, overlay())
    }

    private class AppGeo(val geometry: AppliedGeometry?, val overlay: CanvasOverlayView)

    private class Finger(val x: Float, val y: Float)

    private fun event(action: Int, time: Long, vararg fingers: Finger, actionIndex: Int = 0): MotionEvent {
        val props = Array(fingers.size) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(fingers.size) { MotionEvent.PointerCoords().apply { x = fingers[it].x; y = fingers[it].y } }
        val masked = if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        } else action
        return MotionEvent.obtain(time, time, masked, fingers.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun CanvasOverlayView.send(action: Int, vararg fingers: Finger, actionIndex: Int = 0) =
        onTouchEvent(event(action, SystemClock.uptimeMillis(), *fingers, actionIndex = actionIndex))

    private fun AppliedGeometry.centerPx(target: TextTarget): Pair<Float, Float> {
        val b = element(target)!!.box
        return transform.toPxX(b.centerX) to transform.toPxY(b.centerY)
    }

    private fun StudioActivity.undo() {
        findViewById<View>(R.id.clocky_studio_undo).performClick()
        idle()
    }

    private fun StudioActivity.canUndo() = findViewById<View>(R.id.clocky_studio_undo).isEnabled
    private fun StudioActivity.timeOffset() = DesignEdits.offsetOf(design, TextTarget.TIME, scope)

    /** Drags the Time element by (dxPx, dyPx) screen pixels in a few steps and lifts. */
    private fun CanvasOverlayView.drag(from: Pair<Float, Float>, dxPx: Float, dyPx: Float, steps: Int = 8) {
        send(MotionEvent.ACTION_DOWN, Finger(from.first, from.second))
        for (i in 1..steps) {
            send(MotionEvent.ACTION_MOVE, Finger(from.first + dxPx * i / steps, from.second + dyPx * i / steps))
        }
        send(MotionEvent.ACTION_UP, Finger(from.first + dxPx, from.second + dyPx))
        idle()
    }

    // ---- geometry comes from the applied tree ----

    @Test fun geometryIsReadFromTheAppliedRemoteViews() {
        open(widget()).use { c ->
            val g = c.get().layoutPreview().geometry
            assertNotNull("preview was laid out", g)
            val time = g!!.element(TextTarget.TIME)!!
            assertTrue("time has real bounds: ${time.box}", time.box.width > 0f && time.box.height > 10f)
            assertNotNull("baseline comes from the TextView", time.baseline)
            assertTrue("baseline is inside the box", time.baseline!! in time.box.top..time.box.bottom)
            assertTrue("widget size is the rendered entry", g.widthDp > 100f && g.heightDp > 40f)
        }
    }

    @Test fun theOverlayCoversThePreviewRowWithoutInflatingThePanel() {
        open(widget()).use { c ->
            val a = c.get()
            // The way a device does it: the first pass runs before the preview has its size, and the
            // frame is resized afterwards; the overlay must follow without anyone remeasuring by hand.
            val panel0 = a.panelView()
            fun pass() {
                panel0.measure(
                    View.MeasureSpec.makeMeasureSpec((1000 * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((2000 * density).toInt(), View.MeasureSpec.AT_MOST),
                )
                panel0.layout(0, 0, panel0.measuredWidth, panel0.measuredHeight)
            }
            pass()
            idle()
            pass()
            assertTrue("no manual layout was needed: ${a.overlay().height}", a.overlay().height > 0)
            a.layoutPreview()
            val panel = a.panelView()
            val frame = a.findViewById<View>(R.id.clocky_preview_frame)
            assertTrue("overlay is touchable: ${a.overlay().width}x${a.overlay().height}", a.overlay().width > 0 && a.overlay().height > 0)
            assertEquals("overlay height is the preview height", frame.measuredHeight, a.overlay().height)
            assertEquals("panel = preview + its own padding, not the available height",
                frame.measuredHeight + panel.paddingTop + panel.paddingBottom, panel.measuredHeight)
        }
    }

    // ---- selection ----

    @Test fun tapSelectsTheElementUnderTheFingerWithoutEditing() {
        open(widget()).use { c ->
            val a = c.get()
            val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
            val date = geo.element(TextTarget.DATE)
            if (date != null) {
                val (x, y) = geo.centerPx(TextTarget.DATE)
                overlay.send(MotionEvent.ACTION_DOWN, Finger(x, y))
                overlay.send(MotionEvent.ACTION_UP, Finger(x, y))
                idle()
                assertEquals(TextTarget.DATE, a.selected)
                assertEquals("selecting a text element opens its tab", Slot.DATE.ordinal,
                    a.findViewById<ViewGroup>(R.id.clocky_studio_tabs).all().filterIsInstance<Chip>().indexOfFirst { it.isChecked })
            }
            val (tx, ty) = geo.centerPx(TextTarget.TIME)
            overlay.send(MotionEvent.ACTION_DOWN, Finger(tx, ty))
            overlay.send(MotionEvent.ACTION_UP, Finger(tx, ty))
            idle()
            assertEquals(TextTarget.TIME, a.selected)
            assertFalse("a tap is not an edit", a.canUndo())
            assertFalse(a.session().isDirty)
        }
    }

    @Test fun touchOutsideEveryElementIsNotConsumed() {
        open(widget()).use { c ->
            val a = c.get()
            val geo = a.layoutPreview().geometry!!
            val overlay = a.overlay()
            val consumed = overlay.send(MotionEvent.ACTION_DOWN, Finger(geo.transform.toPxX(-60f), geo.transform.toPxY(-60f)))
            assertFalse(consumed)
        }
    }

    @Test fun selectingATabMovesTheCanvasSelection() {
        open(widget()).use { c ->
            val a = c.get()
            a.findViewById<ViewGroup>(R.id.clocky_studio_tabs).all().filterIsInstance<Chip>()[Slot.DATE.ordinal].performClick()
            idle()
            assertEquals(TextTarget.DATE, a.selected)
        }
    }

    // ---- drag ----

    @Test fun dragMovesTheRequestedOffsetAsOneUndoStep() {
        val id = widget()
        open(id).use { c ->
            val a = c.get()
            val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
            val start = a.timeOffset()
            // far from any snap line: sideways by 37.3dp and down by 11.7dp, in widget dp
            val s = geo.transform.pxPerDp
            overlay.drag(geo.centerPx(TextTarget.TIME), 37.3f * s, 11.7f * s)
            val (x, y) = a.timeOffset()
            assertTrue("moved right: $x", x > start.first + 20f)
            assertTrue("moved down: $y", y > start.second)
            assertTrue(a.canUndo())
            a.undo()
            assertEquals("the whole drag is a single undo step", start, a.timeOffset())
            assertFalse(a.canUndo())
        }
    }

    @Test fun previewShowsTheDragAndMatchesWhatSaveWrites() {
        val id = widget()
        store.save(WidgetInstance(id, DigitalDesign()))
        open(id).use { c ->
            val a = c.get()
            val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
            val before = geo.element(TextTarget.TIME)!!.box
            val s = geo.transform.pxPerDp
            overlay.drag(geo.centerPx(TextTarget.TIME), 30f * s, 0f)
            val moved = a.layoutPreview().geometry!!.element(TextTarget.TIME)!!.box
            assertTrue("the applied view really moved: ${before.left} -> ${moved.left}", moved.left > before.left + 10f)
            val (x, y) = a.timeOffset()
            a.findViewById<Button>(R.id.clocky_studio_save).performClick()
            val saved = store.load(id).design
            assertEquals(x, saved.time.style.xDp, 0f)
            assertEquals(y, saved.time.style.yDp, 0f)
        }
    }

    @Test fun cancellingTheStudioAfterADragLeavesTheSavedWidgetUntouched() {
        val id = widget()
        store.save(WidgetInstance(id, DigitalDesign()))
        val before = store.load(id).design
        open(id).use { c ->
            val a = c.get()
            val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
            overlay.drag(geo.centerPx(TextTarget.TIME), 30f * geo.transform.pxPerDp, 10f)
            @Suppress("DEPRECATION")
            a.onBackPressed()
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            assertTrue(a.isFinishing)
            assertEquals(before, store.load(id).design)
        }
    }

    @Test fun systemCancelDropsTheDrag() {
        open(widget()).use { c ->
            val a = c.get()
            val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
            val from = geo.centerPx(TextTarget.TIME)
            val s = geo.transform.pxPerDp
            overlay.send(MotionEvent.ACTION_DOWN, Finger(from.first, from.second))
            for (i in 1..5) overlay.send(MotionEvent.ACTION_MOVE, Finger(from.first + 8f * s * i, from.second))
            assertTrue("drag edited the draft", a.session().isDirty)
            overlay.send(MotionEvent.ACTION_CANCEL, Finger(from.first + 40f * s, from.second))
            idle()
            assertFalse("cancel restores the draft", a.session().isDirty)
            assertFalse("and leaves no undo step", a.canUndo())
        }
    }

    @Test fun dragWithThisSizeOnlyWritesTheClassPatchNotTheBase() {
        open(widget()).use { c ->
            val a = c.get()
            val scope = a.findViewById<MaterialSwitch>(R.id.clocky_studio_scope)
            a.findViewById<ViewGroup>(R.id.clocky_studio_tabs).all().filterIsInstance<Chip>()[Slot.TIME.ordinal].performClick()
            idle()
            scope.isChecked = true
            idle()
            val baseBefore = a.design.time.style.xDp
            val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
            overlay.drag(geo.centerPx(TextTarget.TIME), 33.3f * geo.transform.pxPerDp, 0f)
            assertEquals("base untouched", baseBefore, a.design.time.style.xDp, 0f)
            val cls: SizeClass = a.previewClass
            assertTrue(DesignEdits.offsetOf(a.design, TextTarget.TIME, EditScope(cls)).first > baseBefore + 10f)
            assertFalse(a.design.layout.patchFor(cls).isEmpty)
        }
    }

    // ---- snap and second finger ----

    /** Drags Time so its center ends [overshootDp] right of the widget center line, on a path that avoids slop. */
    private fun dragNearCenterLine(a: StudioActivity, overshootDp: Float, secondFingerFirst: Boolean): Pair<Int, Pair<Float, Float>> {
        val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
        var ticks = 0
        overlay.haptic = { ticks++ }
        val t = geo.element(TextTarget.TIME)!!
        val dxDp = geo.widthDp / 2f - t.box.centerX + overshootDp
        val from = geo.centerPx(TextTarget.TIME)
        val s = geo.transform.pxPerDp
        val start = a.timeOffset()
        overlay.send(MotionEvent.ACTION_DOWN, Finger(from.first, from.second))
        if (secondFingerFirst) {
            overlay.send(MotionEvent.ACTION_POINTER_DOWN, Finger(from.first, from.second), Finger(from.first + 200f, from.second + 200f), actionIndex = 1)
        }
        val pointers = if (secondFingerFirst) arrayOf(Finger(from.first + dxDp * s, from.second + 60f * s), Finger(from.first + 200f, from.second + 200f))
            else arrayOf(Finger(from.first + dxDp * s, from.second + 60f * s))
        overlay.send(MotionEvent.ACTION_MOVE, *pointers)
        val end = a.timeOffset()
        overlay.send(if (secondFingerFirst) MotionEvent.ACTION_POINTER_UP else MotionEvent.ACTION_UP, *pointers, actionIndex = 0)
        idle()
        return ticks to (end.first - start.first to dxDp)
    }

    @Test fun nearACenterLineTheDragSnapsAndTicks() {
        open(widget()).use { c ->
            val a = c.get()
            val (ticks, moved) = dragNearCenterLine(a, overshootDp = 2f, secondFingerFirst = false)
            assertEquals("snapped exactly onto the line, not 2dp past it", moved.second - 2f, moved.first, 0.01f)
            assertTrue("haptic tick on engage", ticks >= 1)
        }
    }

    @Test fun aSecondFingerDisablesSnapping() {
        open(widget()).use { c ->
            val a = c.get()
            val (ticks, moved) = dragNearCenterLine(a, overshootDp = 2f, secondFingerFirst = true)
            assertEquals("no snap: the offset follows the finger", moved.second, moved.first, 0.01f)
            assertEquals("no haptic without snapping", 0, ticks)
        }
    }

    @Test fun aSecondFingerPlacedMidDragStopsSnappingForTheRestOfTheGesture() {
        open(widget()).use { c ->
            val a = c.get()
            val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
            val s = geo.transform.pxPerDp
            val t = geo.element(TextTarget.TIME)!!
            val from = geo.centerPx(TextTarget.TIME)
            val start = a.timeOffset()
            overlay.send(MotionEvent.ACTION_DOWN, Finger(from.first, from.second))
            overlay.send(MotionEvent.ACTION_MOVE, Finger(from.first - 50f * s, from.second + 60f * s))
            overlay.send(MotionEvent.ACTION_POINTER_DOWN, Finger(from.first - 50f * s, from.second + 60f * s),
                Finger(from.first + 300f, from.second + 300f), actionIndex = 1)
            val dxDp = geo.widthDp / 2f - t.box.centerX + 2f
            val pointers = arrayOf(Finger(from.first + dxDp * s, from.second + 60f * s), Finger(from.first + 300f, from.second + 300f))
            overlay.send(MotionEvent.ACTION_MOVE, *pointers)
            assertEquals(dxDp, a.timeOffset().first - start.first, 0.01f)
        }
    }

    // ---- API < 31 ----

    @Test @Config(sdk = [30])
    fun belowApi31SelectionWorksButDragIsOffAndTheReasonIsShown() {
        val id = widget()
        open(id).use { c ->
            val a = c.get()
            val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
            val notice = a.findViewById<TextView>(R.id.clocky_preview_notice)
            assertEquals(View.VISIBLE, notice.visibility)
            assertEquals(a.getString(R.string.clocky_studio_canvas_unavailable), notice.text.toString())
            assertFalse(a.canDrag)
            overlay.drag(geo.centerPx(TextTarget.TIME), 40f * geo.transform.pxPerDp, 20f)
            assertFalse("no edit at all", a.canUndo())
            assertEquals(TextTarget.TIME, a.selected)
        }
    }

    @Test fun hintIsShownWhereDragWorks() {
        open(widget()).use { c ->
            val notice = c.get().findViewById<TextView>(R.id.clocky_preview_notice)
            assertEquals(c.get().getString(R.string.clocky_studio_canvas_hint), notice.text.toString())
        }
    }

    // ---- RTL ----

    @Test @Config(sdk = [34], qualifiers = "ar-ldrtl")
    fun inRtlADragToTheRightStoresANegativeRequestedX() {
        open(widget()).use { c ->
            val a = c.get()
            val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
            val start = a.timeOffset().first
            overlay.drag(geo.centerPx(TextTarget.TIME), 31.7f * geo.transform.pxPerDp, 0f)
            assertTrue("requested X follows reading direction: ${a.timeOffset().first}", a.timeOffset().first < start - 20f)
            val moved = a.layoutPreview().geometry!!.element(TextTarget.TIME)!!.box.centerX
            assertTrue("and the applied view moved right on screen", moved > geo.element(TextTarget.TIME)!!.box.centerX + 20f)
        }
    }

    // ---- nudge ----

    private fun StudioActivity.nudge(description: String): Button =
        findViewById<ViewGroup>(R.id.clocky_studio_panel).all().filterIsInstance<Button>()
            .first { it.contentDescription?.toString() == description }

    @Test fun nudgeButtonsMoveOneDpAndEachTapIsOneUndoStep() {
        open(widget()).use { c ->
            val a = c.get()
            a.findViewById<View>(R.id.clocky_studio_mode_advanced).performClick()
            idle()
            a.findViewById<ViewGroup>(R.id.clocky_studio_tabs).all().filterIsInstance<Chip>()[Slot.LAYOUT.ordinal].performClick()
            idle()
            val start = a.timeOffset()
            val right = a.getString(R.string.clocky_studio_nudge_right, a.getString(R.string.clocky_element_time))
            repeat(3) { a.nudge(right).performClick(); idle() }
            assertEquals(start.first + 3f, a.timeOffset().first, 0f)
            assertEquals(start.second, a.timeOffset().second, 0f)
            a.undo()
            assertEquals(start.first + 2f, a.timeOffset().first, 0f)
        }
    }

    @Test @Config(sdk = [30])
    fun nudgeIsDisabledBelowApi31() {
        open(widget()).use { c ->
            val a = c.get()
            a.findViewById<View>(R.id.clocky_studio_mode_advanced).performClick()
            idle()
            a.findViewById<ViewGroup>(R.id.clocky_studio_tabs).all().filterIsInstance<Chip>()[Slot.LAYOUT.ordinal].performClick()
            idle()
            val up = a.getString(R.string.clocky_studio_nudge_up, a.getString(R.string.clocky_element_time))
            assertFalse(a.nudge(up).isEnabled)
        }
    }

    private fun StudioActivity.session() = sessionOf(this)

    private fun sessionOf(a: StudioActivity): com.stupidsavacan.clocky.studio.EditSession {
        val vm = androidx.lifecycle.ViewModelProvider(a)[StudioViewModel::class.java]
        return vm.session!!
    }
}
