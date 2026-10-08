package com.stupidsavacan.clocky.widget.studio.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.stupidsavacan.clocky.studio.TextTarget
import com.stupidsavacan.clocky.studio.canvas.CanvasHitTester
import com.stupidsavacan.clocky.studio.canvas.CanvasOffsets
import com.stupidsavacan.clocky.studio.canvas.ElementGeometry
import com.stupidsavacan.clocky.studio.canvas.Guide
import com.stupidsavacan.clocky.studio.canvas.SnapResult
import com.stupidsavacan.clocky.studio.canvas.SnapSolver

/** What the canvas needs from Studio; every change it makes goes through [moveTo] (an EditSession edit). */
interface CanvasHost {
    /** False where the platform does not draw offsets (API < 31): selection works, dragging does not. */
    val canDrag: Boolean
    val rtl: Boolean
    val selected: TextTarget?
    fun select(target: TextTarget)

    /** Requested (stored) offset of [target] in the current edit scope, in reading-direction dp. */
    fun offsetOf(target: TextTarget): Pair<Float, Float>

    /** Size of the entry currently previewed, in dp; the resolver clamps offsets to half of it. */
    fun entrySizeDp(): Pair<Float, Float>?
    fun paddingDp(): Float

    /** Writes the requested offset as part of the drag in progress (one undo step per gesture). */
    fun moveTo(target: TextTarget, xDp: Float, yDp: Float)
    fun endMove()

    /** The system took the touch away: drop everything the drag changed. */
    fun cancelMove()
}

/**
 * The Studio canvas: an input and guide layer over the applied production RemoteViews. It owns no
 * rendering of the clock. Hit testing, selection frames, baselines and snap targets all come from
 * [AppliedGeometryReader], and a drag only ever calls [CanvasHost.moveTo].
 *
 * Gesture rules (End-State 7): the first finger drags; snapping gives a haptic tick when it
 * engages; placing a second finger switches snapping off for the rest of that gesture. Lifting the
 * dragging finger while another is down ends the gesture.
 */
class CanvasOverlayView(
    context: Context,
    private val host: CanvasHost,
    private val appliedRoot: () -> View?,
    /** The preview frame; its measured height is the height this overlay covers. */
    private val sizeSource: () -> View? = appliedRoot,
) : View(context) {
    /** Overridable so tests can observe snap ticks without a haptic motor. */
    var haptic: (View) -> Unit = { it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }

    private val density = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private val haloPaint = paint(0xCCFFFFFF.toInt(), 4f)
    private val selectionPaint = paint(0xFF00B8D4.toInt(), 2f).apply { pathEffect = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f) }
    private val guidePaint = paint(0xFFFF4081.toInt(), 1f)

    private var geometry: AppliedGeometry? = null
    private var guides: List<Guide> = emptyList()

    private sealed interface Gesture {
        data class Pressed(val target: TextTarget, val pointerId: Int, val downX: Float, val downY: Float, val start: AppliedGeometry) : Gesture
        class Dragging(
            val target: TextTarget, val pointerId: Int, val downX: Float, val downY: Float, val start: AppliedGeometry,
            val startOffset: Pair<Float, Float>, var snapEnabled: Boolean, var lastGuides: List<Guide> = emptyList(),
            var edited: Boolean = false,
        ) : Gesture
    }

    private var gesture: Gesture? = null

    /** Current snap guides, for tests and accessibility; empty when not snapped. */
    val activeGuides: List<Guide> get() = guides

    init {
        // The panel already describes the preview; the same values stay reachable through the numeric controls.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /**
     * The overlay must never size its parent: a plain MATCH_PARENT view under a wrap_content panel
     * would take all available height, and FrameLayout does not re-measure a lone match-parent child.
     * It covers the panel's content row instead: the parent's width and the preview frame's height.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val sibling = sizeSource()
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(widthMeasureSpec) else sibling?.measuredWidth ?: 0
        setMeasuredDimension(w, sibling?.measuredHeight ?: 0)
    }

    /** Re-reads the applied tree and repaints; call after every preview render. */
    fun refresh() {
        geometry = null
        invalidate()
    }

    /** Fresh geometry from the applied tree, or null before it has been laid out. */
    fun currentGeometry(): AppliedGeometry? {
        val root = appliedRoot() ?: return null
        val ancestor = parent as? ViewGroup ?: return null
        return AppliedGeometryReader.read(root, ancestor, density, left.toFloat(), top.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val geo = currentGeometry().also { geometry = it } ?: return
        val selected = host.selected
        geo.elements.firstOrNull { it.target == selected }?.let { el ->
            val pad = 4f * density
            val r = RectF(
                geo.transform.toPxX(el.box.left) - pad, geo.transform.toPxY(el.box.top) - pad,
                geo.transform.toPxX(el.box.right) + pad, geo.transform.toPxY(el.box.bottom) + pad,
            )
            canvas.drawRoundRect(r, 4f * density, 4f * density, haloPaint)
            canvas.drawRoundRect(r, 4f * density, 4f * density, selectionPaint)
        }
        guides.forEach { g ->
            if (g.vertical) {
                val x = geo.transform.toPxX(g.position)
                canvas.drawLine(x, geo.transform.toPxY(0f), x, geo.transform.toPxY(geo.heightDp), guidePaint)
            } else {
                val y = geo.transform.toPxY(g.position)
                canvas.drawLine(geo.transform.toPxX(0f), y, geo.transform.toPxX(geo.widthDp), y, guidePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return down(event)
            MotionEvent.ACTION_POINTER_DOWN -> {
                (gesture as? Gesture.Dragging)?.let {
                    it.snapEnabled = false
                    clearGuides()
                }
                return gesture != null
            }
            MotionEvent.ACTION_MOVE -> {
                move(event)
                return gesture != null
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val g = gesture
                val liftedId = event.getPointerId(event.actionIndex)
                val owner = when (g) { is Gesture.Pressed -> g.pointerId; is Gesture.Dragging -> g.pointerId; null -> -1 }
                if (g != null && liftedId == owner) finish(commit = true)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val had = gesture != null
                finish(commit = true)
                return had
            }
            MotionEvent.ACTION_CANCEL -> {
                val had = gesture != null
                finish(commit = false)
                return had
            }
        }
        return false
    }

    private fun down(event: MotionEvent): Boolean {
        val geo = currentGeometry() ?: return false
        geometry = geo
        val x = geo.transform.toDpX(event.x)
        val y = geo.transform.toDpY(event.y)
        // The 48 dp target is a screen size, so convert it to widget dp through the preview scale.
        val minTargetDp = CanvasHitTester.MIN_TARGET_DP * density / geo.transform.pxPerDp
        val hit = CanvasHitTester.hit(x, y, geo.elements, minTargetDp) ?: return false
        host.select(hit.target)
        parent?.requestDisallowInterceptTouchEvent(true)
        gesture = Gesture.Pressed(hit.target, event.getPointerId(0), event.x, event.y, geo)
        invalidate()
        return true
    }

    private fun move(event: MotionEvent) {
        var g = gesture ?: return
        val index = event.findPointerIndex(
            when (g) { is Gesture.Pressed -> g.pointerId; is Gesture.Dragging -> g.pointerId },
        )
        if (index < 0) return
        val px = event.getX(index)
        val py = event.getY(index)

        if (g is Gesture.Pressed) {
            if (!host.canDrag) return
            val dx = px - g.downX
            val dy = py - g.downY
            if (dx * dx + dy * dy < slop * slop) return
            g = Gesture.Dragging(
                g.target, g.pointerId, g.downX, g.downY, g.start, host.offsetOf(g.target),
                snapEnabled = event.pointerCount == 1,
            )
            gesture = g
        }
        val drag = g as Gesture.Dragging
        if (event.pointerCount > 1) drag.snapEnabled = false

        val start = drag.start
        val startElement = start.element(drag.target) ?: return
        val dxDp = (px - drag.downX) / start.transform.pxPerDp
        val dyDp = (py - drag.downY) / start.transform.pxPerDp

        var snap = SnapResult.NONE
        if (drag.snapEnabled) {
            snap = SnapSolver.solve(
                moving = startElement.box.shifted(dxDp, dyDp),
                movingBaseline = startElement.baseline?.plus(dyDp),
                widgetWidth = start.widthDp,
                widgetHeight = start.heightDp,
                paddingDp = host.paddingDp(),
                others = start.elements.filter { it.target != drag.target },
                thresholdDp = SnapSolver.THRESHOLD_SCREEN_DP * density / start.transform.pxPerDp,
            )
        }
        val (rawX, rawY) = CanvasOffsets.requested(
            drag.startOffset.first, drag.startOffset.second, dxDp + snap.dx, dyDp + snap.dy, host.rtl,
        )
        val entry = host.entrySizeDp()
        val (x, y) = CanvasOffsets.clamp(
            rawX, rawY, drag.startOffset.first, drag.startOffset.second, entry?.first ?: 0f, entry?.second ?: 0f,
        )
        // A guide only means something if the element really ended up on it.
        val shown = if (x == rawX && y == rawY) snap.guides else emptyList()
        if (shown.isNotEmpty() && shown != drag.lastGuides) haptic(this)
        drag.lastGuides = shown
        guides = shown

        host.moveTo(drag.target, x, y)
        drag.edited = true
        invalidate()
    }

    private fun clearGuides() {
        (gesture as? Gesture.Dragging)?.lastGuides = emptyList()
        guides = emptyList()
        invalidate()
    }

    private fun finish(commit: Boolean) {
        val g = gesture
        gesture = null
        guides = emptyList()
        if (g is Gesture.Dragging && g.edited) {
            if (commit) host.endMove() else host.cancelMove()
        }
        invalidate()
    }

    private fun paint(color: Int, widthDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = widthDp * resources.displayMetrics.density
    }
}
