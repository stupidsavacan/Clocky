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
import com.stupidsavacan.clocky.design.exchange.DesignExchange
import com.stupidsavacan.clocky.design.exchange.ImportOutcome
import com.stupidsavacan.clocky.design.exchange.TextCode
import com.stupidsavacan.clocky.design.storage.DigitalDesignCodec
import com.stupidsavacan.clocky.design.storage.FileDesignRepository
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
    @get:Rule val exchangeFiles = TemporaryFolder()
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

    private class Finger(val x: Float, val y: Float, val id: Int = -1)

    private fun event(action: Int, time: Long, vararg fingers: Finger, actionIndex: Int = 0): MotionEvent {
        val props = Array(fingers.size) { MotionEvent.PointerProperties().apply { id = fingers[it].id.takeIf { id -> id >= 0 } ?: it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
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
        val pointers = if (secondFingerFirst) arrayOf(Finger(from.first + dxDp * s, from.second + 60f * s), Finger(from.first + dxDp * s + 200f, from.second + 60f * s + 200f))
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
            val pointers = arrayOf(Finger(from.first + dxDp * s, from.second + 60f * s), Finger(from.first + dxDp * s + 50f * s + 300f, from.second + 300f))
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

    private fun pinch(a: StudioActivity, target: TextTarget = TextTarget.TIME, cancel: Boolean = false,
        startX: Float? = null,
    ): Float {
        val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
        val (centerX, y) = geo.centerPx(target)
        val x = startX ?: centerX
        assertTrue("first pointer selects the target", overlay.send(MotionEvent.ACTION_DOWN, Finger(x, y, 7)))
        // Read spans at the MotionEvent boundary: (x + distance) - x need not equal distance
        // when float coordinates cross a binade. The expected state must use the injected span.
        val down = event(MotionEvent.ACTION_POINTER_DOWN, SystemClock.uptimeMillis(),
            Finger(x, y, 7), Finger(x + 100f, y, 19), actionIndex = 1)
        val startSpan = down.getX(1) - down.getX(0)
        overlay.onTouchEvent(down)
        down.recycle()
        // Reorder indices: tracking must use stable IDs, not pointer indices.
        overlay.send(MotionEvent.ACTION_MOVE, Finger(x + 125f, y, 19), Finger(x, y, 7))
        val finalMove = event(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis(),
            Finger(x + 150f, y, 19), Finger(x, y, 7))
        val finalSpan = finalMove.getX(0) - finalMove.getX(1)
        overlay.onTouchEvent(finalMove)
        finalMove.recycle()
        overlay.send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_POINTER_UP,
            Finger(x + 150f, y, 19), Finger(x, y, 7), actionIndex = 0)
        overlay.send(MotionEvent.ACTION_UP, Finger(x, y, 7))
        idle()
        return finalSpan / startSpan
    }

    @Test fun pinchTracksIdsAndIsOneUndoStepWithNumericEquality() {
        open(widget()).use { c ->
            val a = c.get(); val before = a.design
            val ratio = pinch(a)
            val expected = DesignEdits.setSize(before, TextTarget.TIME,
                DesignEdits.sizeOf(before, TextTarget.TIME, EditScope.ALL) * ratio, EditScope.ALL)
            assertEquals(expected, a.design)
            assertEquals(1, a.session().historySize)
            a.undo(); assertEquals(before, a.design)
            assertTrue(a.session().redo()); assertEquals(expected, a.design)
        }
    }

    @Test @Config(qualifiers = "mdpi")
    fun fractionalPointerCoordinatesReproduceLinuxSizeWithoutChangingOtherFields() {
        open(widget()).use { c ->
            val a = c.get(); val before = a.design
            // Keep the chosen fractional coordinate inside the applied Time view regardless of layout.
            val centerX = a.layoutPreview().geometry!!.centerPx(TextTarget.TIME).first
            a.findViewById<android.widget.FrameLayout>(R.id.clocky_preview_frame).translationX = Math.nextDown(512f) - centerX
            val ratio = pinch(a, startX = Math.nextDown(512f))
            assertEquals(Math.nextDown(1.5f), ratio, 0f)
            // At this origin the injected spans are 100.0000305px and 150.0000305px.
            // 64sp scales to the float immediately below 96sp, not an idealized 96sp.
            assertEquals(Math.nextDown(96f), a.design.time.style.sizeSp, 0f)
            val expected = DesignEdits.setSize(before, TextTarget.TIME,
                DesignEdits.sizeOf(before, TextTarget.TIME, EditScope.ALL) * ratio, EditScope.ALL)
            assertEquals(expected, a.design)
            assertEquals(1, a.session().historySize)
            a.undo(); assertEquals(before, a.design)
            assertTrue(a.session().redo()); assertEquals(expected, a.design)
        }
    }

    @Test @Config(sdk = [23, 30]) fun pinchWorksBelowApi31WhenDraggingIsDisabled() {
        open(widget()).use { c ->
            val a = c.get(); val before = a.design
            val ratio = pinch(a)
            assertEquals(DesignEdits.setSize(before, TextTarget.TIME,
                DesignEdits.sizeOf(before, TextTarget.TIME, EditScope.ALL) * ratio, EditScope.ALL), a.design)
            assertEquals(0f, a.design.time.style.xDp, 0f)
        }
    }

    @Test fun datePinchUsesTheSelectedClassPatchAndLeavesBaseUntouched() {
        open(widget()).use { c ->
            val a = c.get()
            a.findViewById<MaterialSwitch>(R.id.clocky_studio_scope).isChecked = true
            val before = a.design
            val ratio = pinch(a, TextTarget.DATE)
            assertEquals(TextTarget.DATE, a.selected)
            assertEquals(before.date, a.design.date)
            assertEquals(DesignEdits.setSize(before, TextTarget.DATE,
                DesignEdits.sizeOf(before, TextTarget.DATE, a.scope) * ratio, a.scope), a.design)
        }
    }

    @Test fun infoPinchUsesItsOwnRequestedSize() {
        val id = widget()
        val original = DigitalDesign().let { it.copy(info = it.info.copy(source = com.stupidsavacan.clocky.design.model.InfoSource.SECOND_TIMEZONE)) }
        store.save(WidgetInstance(id, original))
        open(id).use { c ->
            val a = c.get(); val before = a.design
            val ratio = pinch(a, TextTarget.INFO)
            assertEquals(TextTarget.INFO, a.selected)
            assertEquals(DesignEdits.setSize(before, TextTarget.INFO,
                DesignEdits.sizeOf(before, TextTarget.INFO, EditScope.ALL) * ratio, EditScope.ALL), a.design)
        }
    }

    @Test fun pinchCancelRestoresEarlierEditsAndDoesNotCreateRedo() {
        open(widget()).use { c ->
            val a = c.get()
            a.edit("prior", null, true) { DesignEdits.setSize(it, TextTarget.TIME, 80f, EditScope.ALL) }
            idle(); val before = a.design
            pinch(a, cancel = true)
            assertEquals(before, a.design)
            assertEquals(1, a.session().historySize)
            assertFalse(a.session().canRedo)
        }
    }

    @Test fun dragThenPinchIsOneTransactionAndFreezesOffsetWhilePinching() {
        open(widget()).use { c ->
            val a = c.get(); val before = a.design
            val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
            val (x, y) = geo.centerPx(TextTarget.TIME)
            overlay.send(MotionEvent.ACTION_DOWN, Finger(x, y, 7))
            overlay.send(MotionEvent.ACTION_MOVE, Finger(x + 40f, y + 20f, 7))
            val offset = a.timeOffset()
            overlay.send(MotionEvent.ACTION_POINTER_DOWN, Finger(x + 40f, y + 20f, 7), Finger(x + 140f, y + 20f, 19), actionIndex = 1)
            overlay.send(MotionEvent.ACTION_MOVE, Finger(x + 60f, y + 30f, 7), Finger(x + 260f, y + 30f, 19))
            assertEquals(offset, a.timeOffset())
            assertTrue(overlay.activeGuides.isEmpty())
            overlay.send(MotionEvent.ACTION_POINTER_UP, Finger(x + 60f, y + 30f, 7), Finger(x + 260f, y + 30f, 19), actionIndex = 0)
            // Remaining finger must not start another drag.
            overlay.send(MotionEvent.ACTION_MOVE, Finger(x + 300f, y, 19))
            overlay.send(MotionEvent.ACTION_UP, Finger(x + 300f, y, 19))
            assertEquals(1, a.session().historySize)
            a.undo(); assertEquals(before, a.design)
        }
    }

    @Test fun aThirdPointerDoesNotReplaceThePinchPairAndMissingIdsCancel() {
        open(widget()).use { c ->
            val a = c.get(); val before = a.design
            val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
            val (x, y) = geo.centerPx(TextTarget.TIME)
            overlay.send(MotionEvent.ACTION_DOWN, Finger(x, y, 7))
            overlay.send(MotionEvent.ACTION_POINTER_DOWN, Finger(x, y, 7), Finger(x + 100f, y, 19), actionIndex = 1)
            overlay.send(MotionEvent.ACTION_POINTER_DOWN, Finger(x, y, 7), Finger(x + 100f, y, 19), Finger(x + 900f, y, 23), actionIndex = 2)
            overlay.send(MotionEvent.ACTION_MOVE, Finger(x, y, 7), Finger(x + 150f, y, 19), Finger(x + 2000f, y, 23))
            assertEquals(before.time.style.sizeSp * 1.5f, a.design.time.style.sizeSp, 0.001f)
            overlay.send(MotionEvent.ACTION_POINTER_UP, Finger(x, y, 7), Finger(x + 150f, y, 19), Finger(x + 2000f, y, 23), actionIndex = 2)
            overlay.send(MotionEvent.ACTION_MOVE, Finger(x, y, 7)) // malformed/lost pointer cancels
            assertEquals(before, a.design)
            assertFalse(a.canUndo())
        }
    }

    @Test fun liftingSecondFingerBeforePinchKeepsSnappingOffAndDragAlive() {
        open(widget()).use { c ->
            val a = c.get()
            val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
            val (x, y) = geo.centerPx(TextTarget.TIME)
            overlay.send(MotionEvent.ACTION_DOWN, Finger(x, y, 7))
            overlay.send(MotionEvent.ACTION_POINTER_DOWN, Finger(x, y, 7), Finger(x + 100f, y, 19), actionIndex = 1)
            overlay.send(MotionEvent.ACTION_POINTER_UP, Finger(x, y, 7), Finger(x + 100f, y, 19), actionIndex = 1)
            overlay.send(MotionEvent.ACTION_MOVE, Finger(x + 40f, y + 20f, 7))
            assertTrue(overlay.activeGuides.isEmpty())
            assertEquals(40f / geo.transform.pxPerDp, a.timeOffset().first, 0.001f)
            overlay.send(MotionEvent.ACTION_UP, Finger(x + 40f, y + 20f, 7))
            assertEquals(1, a.session().historySize)
        }
    }

    @Test fun twoPinchesAreTwoUndoStepsAndShrinkingClampsThroughDesignEdits() {
        open(widget()).use { c ->
            val a = c.get(); val before = a.design
            pinch(a); pinch(a)
            assertEquals(2, a.session().historySize)
            a.undo(); a.undo(); assertEquals(before, a.design)
            val (geo, overlay) = a.layoutPreview().let { it.geometry!! to it.overlay }
            val (x, y) = geo.centerPx(TextTarget.TIME)
            overlay.send(MotionEvent.ACTION_DOWN, Finger(x, y, 7))
            overlay.send(MotionEvent.ACTION_POINTER_DOWN, Finger(x, y, 7), Finger(x + 100f, y, 19), actionIndex = 1)
            overlay.send(MotionEvent.ACTION_MOVE, Finger(x, y, 7), Finger(x, y, 19))
            overlay.send(MotionEvent.ACTION_UP, Finger(x, y, 7))
            assertEquals(DesignEdits.setSize(before, TextTarget.TIME, 0f, EditScope.ALL), a.design)
        }
    }

    private fun chooseCells(a: StudioActivity, cells: com.stupidsavacan.clocky.studio.canvas.PreviewCells) {
        a.findViewById<Button>(R.id.clocky_studio_resize_preview).performClick()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        val index = com.stupidsavacan.clocky.studio.canvas.PreviewCells.ALL.indexOf(cells)
        dialog.listView.performItemClick(dialog.listView.getChildAt(index), index, index.toLong())
        idle()
    }

    @Test fun classScopedPinchesRoundTripThroughFileAndTextExchangeWithoutChangingWidget() {
        val id = widget()
        val stored = store.load(id).design
        val options = AppWidgetManager.getInstance(app).getAppWidgetOptions(id)
        open(id).use { c ->
            val a = c.get()
            val before = a.design
            a.findViewById<MaterialSwitch>(R.id.clocky_studio_scope).isChecked = true
            for (cells in listOf(4 to 1, 4 to 4)) {
                val grid = com.stupidsavacan.clocky.studio.canvas.PreviewCells(cells.first, cells.second)
                chooseCells(a, grid)
                val prior = a.design
                val ratio = pinch(a)
                assertEquals(DesignEdits.setSize(prior, TextTarget.TIME,
                    DesignEdits.sizeOf(prior, TextTarget.TIME, EditScope(grid.sizeClass)) * ratio,
                    EditScope(grid.sizeClass)), a.design)
            }
            assertEquals(before.time, a.design.time)
            assertEquals(2, a.session().historySize)
            val repository = FileDesignRepository(exchangeFiles.newFolder("designs"))
            val saved = repository.create("Pinched classes", a.design)
            val document = repository.rawDesign(saved.id)!!
            val original = document.toString()
            val code = DesignExchange.toTextCode(saved.name, document) as TextCode.Ready
            val outcomes = listOf(
                DesignExchange.parseFile(DesignExchange.toFileBytes(saved.name, document)),
                DesignExchange.parseTextCode(code.code),
            )
            for (outcome in outcomes) {
                val imported = outcome as ImportOutcome.Success
                assertTrue(imported.unknownFonts.isEmpty())
                assertEquals(a.design, DigitalDesignCodec.decode(imported.design))
                val copy = repository.createFromDocument(imported.name!!, imported.design)
                assertFalse(saved.id == copy.id)
                assertEquals(a.design, copy.design)
            }
            assertEquals(original, repository.rawDesign(saved.id)!!.toString())
            assertEquals(stored, store.load(id).design)
            val afterOptions = AppWidgetManager.getInstance(app).getAppWidgetOptions(id)
            assertEquals(options.keySet(), afterOptions.keySet())
            options.keySet().forEach { assertEquals(options.get(it), afterOptions.get(it)) }
            a.undo(); a.undo(); assertEquals(before, a.design)
            assertEquals(saved.design, repository.get(saved.id)!!.design)
        }
    }

    @Test fun accessiblePseudoResizeChangesClassAndScopeWithoutSavingOrUndo() {
        val id = widget(); val options = AppWidgetManager.getInstance(app).getAppWidgetOptions(id)
        val stored = store.load(id).design
        open(id).use { c ->
            val a = c.get(); val before = a.design
            a.findViewById<MaterialSwitch>(R.id.clocky_studio_scope).isChecked = true
            for (cells in listOf(4 to 1, 4 to 2, 4 to 3, 4 to 4)) {
                val grid = com.stupidsavacan.clocky.studio.canvas.PreviewCells(cells.first, cells.second)
                chooseCells(a, grid)
                assertEquals(grid.sizeClass, a.previewClass)
                assertEquals(EditScope(grid.sizeClass), a.scope)
                assertEquals(before, a.design)
                assertFalse(a.canUndo())
                assertTrue(a.findViewById<MaterialSwitch>(R.id.clocky_studio_scope).text.contains(a.getString(com.stupidsavacan.clocky.widget.digital.SizeClassLabels.label(grid.sizeClass))))
            }
            pinch(a)
            assertEquals(before.time, a.design.time)
            assertFalse(a.design.layout.patchFor(SizeClass.SQUARE).isEmpty)
            assertEquals(stored, store.load(id).design)
            val afterOptions = AppWidgetManager.getInstance(app).getAppWidgetOptions(id)
            assertEquals(options.keySet(), afterOptions.keySet())
            options.keySet().forEach { assertEquals(options.get(it), afterOptions.get(it)) }
        }
    }

    @Test fun pseudoResizeSaveOnlyLeavesWidgetDesignUnchangedAndClassButtonsExitSimulation() {
        val id = widget()
        open(id).use { c ->
            val a = c.get(); val before = a.design
            chooseCells(a, com.stupidsavacan.clocky.studio.canvas.PreviewCells(5, 4))
            val vm = androidx.lifecycle.ViewModelProvider(a)[StudioViewModel::class.java]
            assertEquals(com.stupidsavacan.clocky.studio.canvas.PreviewCells(5, 4), vm.previewCells)
            a.findViewById<View>(R.id.clocky_preview_strip).performClick()
            idle(); assertNull(vm.previewCells)
            chooseCells(a, com.stupidsavacan.clocky.studio.canvas.PreviewCells(4, 4))
            a.findViewById<Button>(R.id.clocky_studio_save).performClick()
            assertEquals(before, store.load(id).design)
        }
    }

    @Test fun recreationKeepsPreviewCellsAndClassWithoutPersistingSimulation() {
        open(widget()).use { c ->
            chooseCells(c.get(), com.stupidsavacan.clocky.studio.canvas.PreviewCells(5, 3))
            c.recreate(); idle()
            val a = c.get()
            assertEquals(SizeClass.LARGE, a.previewClass)
            assertEquals(com.stupidsavacan.clocky.studio.canvas.PreviewCells(5, 3),
                androidx.lifecycle.ViewModelProvider(a)[StudioViewModel::class.java].previewCells)
            assertFalse(a.canUndo())
        }
    }

    @Test fun pseudoHandleDragAndCancelRestoreThePriorSimulation() {
        open(widget()).use { c ->
            val a = c.get(); chooseCells(a, com.stupidsavacan.clocky.studio.canvas.PreviewCells(4, 2))
            a.layoutPreview()
            val handle = a.findViewById<Button>(R.id.clocky_studio_resize_preview)
            handle.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 0, Finger(20f, 20f)))
            handle.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 10, Finger(20f, 700f)))
            assertEquals(SizeClass.SQUARE, a.previewClass)
            handle.dispatchTouchEvent(event(MotionEvent.ACTION_CANCEL, 20, Finger(20f, 700f)))
            assertEquals(SizeClass.CARD, a.previewClass)
            assertFalse(a.canUndo())
        }
    }

    @Test @Config(qualifiers = "ar-ldrtl")
    fun pseudoHandleExpandsOutwardFromTheEndEdgeInRtl() {
        open(widget()).use { c ->
            val a = c.get(); chooseCells(a, com.stupidsavacan.clocky.studio.canvas.PreviewCells(4, 2))
            a.layoutPreview()
            val handle = a.findViewById<Button>(R.id.clocky_studio_resize_preview)
            handle.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 0, Finger(20f, 20f, 7)))
            handle.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 10, Finger(-500f, 20f, 7)))
            handle.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 20, Finger(-500f, 20f, 7)))
            assertEquals(com.stupidsavacan.clocky.studio.canvas.PreviewCells(5, 2),
                androidx.lifecycle.ViewModelProvider(a)[StudioViewModel::class.java].previewCells)
            assertFalse(a.canUndo())
        }
    }

    private fun StudioActivity.session() = sessionOf(this)

    private fun sessionOf(a: StudioActivity): com.stupidsavacan.clocky.studio.EditSession {
        val vm = androidx.lifecycle.ViewModelProvider(a)[StudioViewModel::class.java]
        return vm.session!!
    }
}
