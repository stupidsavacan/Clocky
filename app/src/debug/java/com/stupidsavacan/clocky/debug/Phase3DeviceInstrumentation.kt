package com.stupidsavacan.clocky.debug

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import com.stupidsavacan.clocky.widget.studio.StudioViewModel
import com.stupidsavacan.clocky.studio.canvas.PreviewCells
import com.stupidsavacan.clocky.design.storage.SharedPreferencesDesignStore
import com.stupidsavacan.clocky.widget.digital.PreviewHost
import com.stupidsavacan.clocky.widget.digital.DigitalWidgetUpdater
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.InfoSource
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.storage.DigitalDesignCodec
import com.stupidsavacan.clocky.studio.DesignEdits
import com.stupidsavacan.clocky.studio.EditScope
import com.stupidsavacan.clocky.studio.TextTarget
import com.stupidsavacan.clocky.widget.studio.StudioActivity
import com.stupidsavacan.clocky.widget.studio.canvas.CanvasOverlayView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** Local moto acceptance runner. Debug-only; no Save, widget writes, or alternative renderer. */
class Phase3DeviceInstrumentation : Instrumentation() {
    private lateinit var arguments: Bundle
    private val results = JSONArray()
    private val trace = JSONArray()
    private lateinit var studio: StudioActivity

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        this.arguments = arguments ?: Bundle()
        start()
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        runOnMainSync { try { result.set(block()) } catch (t: Throwable) { error.set(t) } }
        error.get()?.let { throw it }
        return result.get()
    }

    override fun onStart() {
        val report = Bundle()
        try {
            // `am instrument -e` supplies strings, whereas direct instrumentation callers may use ints.
            val id = arguments.getString("widgetId")?.toIntOrNull() ?: arguments.getInt("widgetId", -1)
            check(id >= 0) { "Existing widgetId required" }
            if (arguments.getString("mode") == "restore") {
                restoreSaved(id)
                report.putString("clocky-summary", "PASS: original restored through Studio Save")
                finish(Activity.RESULT_OK, report)
                return
            }
            studio = startActivitySync(Intent(targetContext, StudioActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as StudioActivity
            waitForIdleSync()
            SystemClock.sleep(500)
            observeTouches()
            val original = onMain { studio.design }
            if (arguments.getString("mode") == "extended") {
                extended(id, original)
                report.putString("clocky-results", results.toString())
                report.putString("clocky-summary", "PASS: handle, drag transition, Save and UI restoration")
                finish(Activity.RESULT_OK, report)
                return
            }
            pinch(TextTarget.TIME, 1.5f)
            pinch(TextTarget.TIME, 0.75f)
            pinch(TextTarget.TIME, 1.5f, cancel = true)
            pinch(TextTarget.DATE, 1.5f)
            for ((cell, cls) in listOf("2×1" to SizeClass.STRIP, "3×2" to SizeClass.CARD,
                "4×3" to SizeClass.LARGE, "4×4" to SizeClass.SQUARE)) {
                onMain { studio.findViewById<View>(R.id.clocky_studio_resize_preview).performClick() }
                waitForIdleSync()
                val node = find(uiAutomation.rootInActiveWindow) { it.text?.toString()?.startsWith("$cell ") == true }
                    ?: error("Cell $cell absent")
                check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitForIdleSync()
                SystemClock.sleep(250)
                onMain {
                    check(studio.previewClass == cls) { "Cell $cell class ${studio.previewClass}" }
                    check(studio.design == original) { "Simulation modified requested design" }
                    fit()
                }
                results.put(JSONObject().put("case", "preview-$cell").put("class", cls.name).put("passed", true))
            }
            // Class-only sizing through the same true multi-pointer input path.
            onMain { studio.findViewById<android.widget.CompoundButton>(R.id.clocky_studio_scope).isChecked = true }
            pinch(TextTarget.TIME, 1.5f)
            check(onMain { studio.design } == original)
            onMain { studio.finish() }
            waitForIdleSync()
            // A draft-only Info fixture uses the existing editor intent contract; no widget write.
            val infoDraft = DesignEdits.setInfoVisible(original.copy(info = original.info.copy(
                source = InfoSource.SECOND_TIMEZONE, timeZoneId = "UTC", visible = true)),
                true, EditScope(SizeClass.LARGE))
            studio = startActivitySync(Intent(targetContext, StudioActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(StudioActivity.EXTRA_DRAFT_JSON, DigitalDesignCodec.encode(infoDraft).toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as StudioActivity
            waitForIdleSync()
            SystemClock.sleep(500)
            pinch(TextTarget.INFO, 1.5f)
            report.putString("clocky-results", results.toString())
            report.putString("clocky-summary", "PASS: system-injected two-pointer gestures; no widget Save")
            onMain { studio.finish() }
            finish(Activity.RESULT_OK, report)
        } catch (t: Throwable) {
            report.putString("clocky-results", results.toString())
            report.putString("clocky-error", t.stackTraceToString())
            report.putString("clocky-trace", trace.toString())
            if (::studio.isInitialized) onMain { studio.finish() }
            finish(Activity.RESULT_CANCELED, report)
        }
    }

    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), predicate)?.let { return it }
        return null
    }

    private fun text(view: View): TextView? {
        if (view.visibility != View.VISIBLE) return null
        if (view is TextView && view.text.isNotEmpty()) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) text(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun overlay(view: View): CanvasOverlayView? {
        if (view is CanvasOverlayView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) overlay(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun observeTouches() = onMain {
        val window = studio.window
        val delegate = window.callback
        window.callback = object : Window.Callback by delegate {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                val row = JSONObject().put("action", event.actionMasked).put("count", event.pointerCount)
                    .put("x", event.x).put("y", event.y)
                row.put("pointers", JSONArray().apply {
                    for (i in 0 until event.pointerCount) put("${event.getPointerId(i)}:${event.getX(i)},${event.getY(i)}")
                })
                val accepted = delegate.dispatchTouchEvent(event)
                row.put("accepted", accepted).put("selected", studio.selected?.name)
                val overlay = overlay(window.decorView)
                val position = IntArray(2)
                overlay?.getLocationOnScreen(position)
                row.put("overlay", "${position.toList()} ${overlay?.width}x${overlay?.height}")
                overlay?.currentGeometry()?.let { geo ->
                    row.put("geometry", geo.elements.joinToString { "${it.target}:${it.box}" })
                    row.put("transform", "${geo.transform.originX},${geo.transform.originY},${geo.transform.pxPerDp}")
                }
                trace.put(row)
                return accepted
            }
        }
    }

    private fun fit() {
        val frame = studio.findViewById<ViewGroup>(R.id.clocky_preview_frame)
        val panel = studio.findViewById<View>(R.id.clocky_preview_panel)
        check(frame.scaleX > 0 && frame.scaleX.isFinite())
        val width = panel.width - panel.paddingLeft - panel.paddingRight
        val height = panel.height - panel.paddingTop - panel.paddingBottom
        check(frame.width * frame.scaleX <= width + 1f) { "Preview exceeds panel width" }
        check(frame.height * frame.scaleY <= height + 1f) { "Preview exceeds panel height" }
        val child = frame.getChildAt(0) ?: error("Production RemoteViews child absent")
        check(child.scaleX > 0 && child.scaleX.isFinite())
        // PreviewHost rounds the displayed frame size down to integer pixels.
        check(child.width * child.scaleX <= frame.width + 1f) { "Rendered child exceeds fitted frame width" }
        check(child.height * child.scaleY <= frame.height + 1f) { "Rendered child exceeds fitted frame height" }
    }

    private fun pinch(target: TextTarget, ratio: Float, cancel: Boolean = false, keepResult: Boolean = false) {
        val before = onMain { studio.design }
        val scope = onMain { studio.scope }
        val point = onMain {
            val slot = studio.findViewById<View>(when (target) {
                TextTarget.TIME -> R.id.clocky_time_slot
                TextTarget.DATE -> R.id.clocky_date_slot
                TextTarget.INFO -> R.id.clocky_info_slot
            })
            val bounds = Rect()
            check(text(slot)?.getGlobalVisibleRect(bounds) == true) { "$target absent from preview" }
            trace.put(JSONObject().put("target", target.name).put("textBounds", bounds.toString()))
            bounds.centerX().toFloat() to bounds.centerY().toFloat()
        }
        val (x, y) = point
        val firstId = arguments.getString("firstId")?.toIntOrNull() ?: 7
        val secondId = arguments.getString("secondId")?.toIntOrNull() ?: 19
        val reordered = arguments.getString("reorder") == "true"
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, intArrayOf(firstId), floatArrayOf(x), y)
        val span = 80f
        send(down, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            intArrayOf(firstId, secondId), floatArrayOf(x, x + span), y)
        val requested = DesignEdits.sizeOf(before, target, scope) * ((x + span * ratio - x) / (x + span - x))
        val expected = DesignEdits.setSize(before, target, requested, scope)
        for (step in 1..6) {
            val distance = span * (1f + (ratio - 1f) * step / 6f)
            send(down, MotionEvent.ACTION_MOVE,
                if (reordered) intArrayOf(secondId, firstId) else intArrayOf(firstId, secondId),
                if (reordered) floatArrayOf(x + distance, x) else floatArrayOf(x, x + distance), y)
            onMain { fit() }
        }
        val during = onMain { studio.design }
        check(during == expected) { "$target expected=$expected actual=$during" }
        if (cancel) {
            send(down, MotionEvent.ACTION_CANCEL, intArrayOf(firstId, secondId), floatArrayOf(x, x + span * ratio), y)
            check(onMain { studio.design } == before) { "Cancel failed to restore draft" }
        } else {
            send(down, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                intArrayOf(firstId, secondId), floatArrayOf(x, x + span * ratio), y)
            send(down, MotionEvent.ACTION_UP, intArrayOf(firstId), floatArrayOf(x), y)
            check(onMain { studio.design } == expected)
            onMain { studio.findViewById<View>(R.id.clocky_studio_undo).performClick() }
            waitForIdleSync()
            check(onMain { studio.design } == before) { "One Undo did not restore whole design" }
            onMain { studio.findViewById<View>(R.id.clocky_studio_redo).performClick() }
            waitForIdleSync()
            check(onMain { studio.design } == expected) { "Redo failed" }
            onMain { studio.findViewById<View>(R.id.clocky_studio_undo).performClick() }
            waitForIdleSync()
            check(onMain { studio.design } == before)
            if (keepResult) {
                onMain { studio.findViewById<View>(R.id.clocky_studio_redo).performClick() }
                waitForIdleSync()
                check(onMain { studio.design } == expected)
            }
        }
        results.put(JSONObject().put("case", "pinch-$target").put("ratio", ratio)
            .put("scope", scope.toString()).put("cancel", cancel).put("requestedSp", requested)
            .put("ids", "$firstId,$secondId").put("reordered", reordered).put("passed", true))
    }

    private fun chooseCells(label: String) {
        onMain { studio.findViewById<View>(R.id.clocky_studio_resize_preview).performClick() }
        waitForIdleSync()
        val deadline = SystemClock.uptimeMillis() + 5000
        var bounds: Rect? = null
        while (bounds == null && SystemClock.uptimeMillis() < deadline) {
            val node = find(uiAutomation.rootInActiveWindow) {
                it.viewIdResourceName == "android:id/text1" && it.text?.toString()?.startsWith("$label ") == true
            }
            node?.let { bounds = Rect().also(it::getBoundsInScreen).takeUnless(Rect::isEmpty) }
            if (bounds == null) SystemClock.sleep(100)
        }
        val point = checkNotNull(bounds) { "Cell $label absent from visible dialog" }
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, intArrayOf(7), floatArrayOf(point.centerX().toFloat()), point.centerY().toFloat())
        send(down, MotionEvent.ACTION_UP, intArrayOf(7), floatArrayOf(point.centerX().toFloat()), point.centerY().toFloat())
        waitForIdleSync()
        SystemClock.sleep(250)
    }

    private fun passed(name: String) { results.put(JSONObject().put("case", name).put("passed", true)) }

    private fun extended(id: Int, original: DigitalDesign) {
        chooseCells("4×3")
        handle(cancel = true, original)
        handle(cancel = false, original)
        chooseCells("4×3")
        dragTransition(original)
        onMain { studio.finish() }
        waitForIdleSync()
        // Saving is separately guarded by exact reversibility of the current stored JSON.
        val prefs = targetContext.getSharedPreferences(SharedPreferencesDesignStore.PREFS_NAME, 0)
        val raw = prefs.getString(SharedPreferencesDesignStore.key(id), null) ?: error("Saved widget required")
        val design = DigitalDesignCodec.decode(JSONObject(raw))
        check(DesignEdits.prepare(design) == design) { "Studio preparation changes baseline; Save trial refused" }
        check(DigitalDesignCodec.encode(design).toString() == raw) { "Baseline is not byte-restorable through Save" }
        val backup = java.io.File(targetContext.filesDir, "phase3-moto-original-$id.json")
        check(!backup.exists() || backup.readText() == raw) { "Unrestored earlier backup; refusing overwrite" }
        backup.writeText(raw)
        try {
            studio = openStudio(id)
            waitForIdleSync()
            SystemClock.sleep(500)
            pinch(TextTarget.TIME, 0.75f, keepResult = true)
            val changed = onMain { studio.design }
            val frame = FrameLayout(targetContext)
            val preview = PreviewHost(frame, id)
            val cls = onMain { preview.hostSizeClass() }
            val spec = onMain { preview.render(changed, cls) }
            val entry = DigitalWidgetUpdater.renderEntry(targetContext, changed, preview.previewEntry(cls), cls,
                DigitalWidgetUpdater.environment(targetContext))
            check(spec == entry.spec) { "Preview and production entry specs differ" }
            passed("saved-design-preview-production-spec-parity")
            onMain { studio.findViewById<View>(R.id.clocky_studio_save).performClick() }
            waitForIdleSync()
            check(SharedPreferencesDesignStore(targetContext).load(id).design == changed)
            check(prefs.getString(SharedPreferencesDesignStore.key(id), null) != raw)
            passed("pinch-save-exact-design")
            val keyDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)
            val keyUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME)
            check(uiAutomation.injectInputEvent(keyDown, true))
            check(uiAutomation.injectInputEvent(keyUp, true))
            SystemClock.sleep(1000)
            check(find(uiAutomation.rootInActiveWindow) {
                it.viewIdResourceName == "com.stupidsavacan.clocky:id/clocky_widget_root"
            } != null) { "Placed widget absent on launcher after Save" }
            passed("placed-widget-visible-after-save")
        } finally {
            restoreSaved(id)
        }
        check(prefs.getString(SharedPreferencesDesignStore.key(id), null) == raw)
        passed("save-restoration-byte-identical")
    }

    private fun openStudio(id: Int, draft: String? = null): StudioActivity {
        val intent = Intent(targetContext, StudioActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        draft?.let { intent.putExtra(StudioActivity.EXTRA_DRAFT_JSON, it) }
        return startActivitySync(intent) as StudioActivity
    }

    private fun restoreSaved(id: Int) {
        val backup = java.io.File(targetContext.filesDir, "phase3-moto-original-$id.json")
        if (!backup.exists()) return
        val raw = backup.readText()
        studio = openStudio(id, raw)
        waitForIdleSync()
        check(onMain { DigitalDesignCodec.encode(studio.design).toString() } == raw)
        onMain { studio.findViewById<View>(R.id.clocky_studio_save).performClick() }
        waitForIdleSync()
        check(targetContext.getSharedPreferences(SharedPreferencesDesignStore.PREFS_NAME, 0)
            .getString(SharedPreferencesDesignStore.key(id), null) == raw)
    }

    private fun handle(cancel: Boolean, original: DigitalDesign) {
        val vm = onMain { ViewModelProvider(studio)[StudioViewModel::class.java] }
        val (x, y, scale) = onMain {
            val bounds = Rect()
            check(studio.findViewById<View>(R.id.clocky_studio_resize_preview).getGlobalVisibleRect(bounds))
            Triple(bounds.centerX().toFloat(), bounds.centerY().toFloat(),
                overlay(studio.window.decorView)!!.currentGeometry()!!.transform.pxPerDp)
        }
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, intArrayOf(7), floatArrayOf(x), y)
        val endX = x - 95f * scale
        val endY = y - 138f * scale
        send(down, MotionEvent.ACTION_MOVE, intArrayOf(7), floatArrayOf(endX), endY)
        onMain {
            check(vm.previewCells == PreviewCells(3, 2)) { "Handle cells ${vm.previewCells}" }
            check(studio.previewClass == SizeClass.CARD)
            check(studio.design == original)
            fit()
        }
        send(down, if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,
            intArrayOf(7), floatArrayOf(endX), endY)
        onMain {
            check(vm.previewCells == if (cancel) PreviewCells(4, 3) else PreviewCells(3, 2))
            check(studio.design == original)
            fit()
        }
        passed(if (cancel) "pseudo-handle-cancel-restores" else "pseudo-handle-drag-class-transition")
    }

    private fun dragTransition(original: DigitalDesign) {
        val (x, y) = onMain {
            val bounds = Rect()
            check(text(studio.findViewById(R.id.clocky_time_slot))!!.getGlobalVisibleRect(bounds))
            bounds.centerX().toFloat() to bounds.centerY().toFloat()
        }
        val down = SystemClock.uptimeMillis()
        send(down, MotionEvent.ACTION_DOWN, intArrayOf(7), floatArrayOf(x), y)
        send(down, MotionEvent.ACTION_MOVE, intArrayOf(7), floatArrayOf(x + 30), y + 20)
        check(onMain { studio.design } != original) { "First-pointer drag unchanged" }
        send(down, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            intArrayOf(7, 9), floatArrayOf(x + 30, x + 110), y + 20)
        send(down, MotionEvent.ACTION_MOVE, intArrayOf(9, 7), floatArrayOf(x + 130, x + 50), y + 20)
        onMain {
            check(DesignEdits.sizeOf(studio.design, TextTarget.TIME, studio.scope) ==
                DesignEdits.sizeOf(original, TextTarget.TIME, studio.scope))
            check(overlay(studio.window.decorView)!!.activeGuides.isEmpty())
        }
        val moved = onMain { studio.design }
        check(moved != original)
        send(down, MotionEvent.ACTION_MOVE, intArrayOf(9, 7), floatArrayOf(x + 170, x + 50), y + 20)
        val expected = onMain { DesignEdits.setSize(moved, TextTarget.TIME,
            DesignEdits.sizeOf(moved, TextTarget.TIME, studio.scope) * 1.5f, studio.scope) }
        check(onMain { studio.design } == expected)
        // Lift the owning pointer; the remaining pointer must not resume dragging.
        send(down, MotionEvent.ACTION_POINTER_UP, intArrayOf(7, 9), floatArrayOf(x + 50, x + 170), y + 20)
        send(down, MotionEvent.ACTION_MOVE, intArrayOf(9), floatArrayOf(x + 190), y + 20)
        send(down, MotionEvent.ACTION_UP, intArrayOf(9), floatArrayOf(x + 190), y + 20)
        check(onMain { studio.design } == expected)
        onMain { studio.findViewById<View>(R.id.clocky_studio_undo).performClick() }
        waitForIdleSync()
        check(onMain { studio.design } == original) { "Drag-to-pinch was not one undo step" }
        passed("drag-unsnapped-to-pinch-owner-up-one-undo")
    }

    private fun send(down: Long, action: Int, ids: IntArray, xs: FloatArray, screenY: Float) {
        val properties = Array(ids.size) { i -> MotionEvent.PointerProperties().apply {
            id = ids[i]; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }
        val coordinates = Array(ids.size) { i -> MotionEvent.PointerCoords().apply {
            x = xs[i]; y = screenY; pressure = 1f; size = 1f
        } }
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, ids.size, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        trace.put(JSONObject().put("injected", event.actionMasked).put("pointers", JSONArray().apply {
            for (i in ids.indices) put("${event.getPointerId(i)}:${event.getX(i)},${event.getY(i)}")
        }))
        try { check(uiAutomation.injectInputEvent(event, true)) { "Input injection refused" } }
        finally { event.recycle() }
        SystemClock.sleep(40)
        waitForIdleSync()
    }
}
