package com.stupidsavacan.clocky.studio.canvas

import com.stupidsavacan.clocky.studio.TextTarget
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure geometry behind the Studio canvas (Phase 3B-1). Everything here is in the widget's own dp
 * space with its origin at the widget's top-left corner and X growing to the right on screen
 * (physical, not reading direction). Android types are deliberately absent so the solver, the
 * hit test and the gesture-to-offset mapping can be unit tested without a device.
 */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun shifted(dx: Float, dy: Float) = Box(left + dx, top + dy, right + dx, bottom + dy)
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/** One element as it is actually laid out in the applied RemoteViews. [baseline] is an absolute Y. */
data class ElementGeometry(val target: TextTarget, val box: Box, val baseline: Float?)

enum class GuideKind { WIDGET_CENTER, PADDING_EDGE, ELEMENT_CENTER, ELEMENT_BASELINE }

/** A line to draw while snapped: vertical (constant [position] = X) or horizontal (position = Y). */
data class Guide(val vertical: Boolean, val position: Float, val kind: GuideKind)

data class SnapResult(val dx: Float, val dy: Float, val guides: List<Guide>) {
    val snapped: Boolean get() = guides.isNotEmpty()

    companion object { val NONE = SnapResult(0f, 0f, emptyList()) }
}

object SnapSolver {
    /** Snap distance on screen, in dp; the caller divides by the preview scale for widget dp. */
    const val THRESHOLD_SCREEN_DP = 6f

    /**
     * Returns the extra shift that makes [moving] (already at its proposed position) coincide with
     * the nearest snap line on each axis, within [thresholdDp]. Targets:
     * - widget center lines, and the padding edges (moving left/right/top/bottom edge);
     * - the center line of every other element (moving center);
     * - the baseline of every other element (moving baseline).
     * X and Y are independent; ties go to the smaller correction, then to a stable declared order.
     */
    fun solve(
        moving: Box,
        movingBaseline: Float?,
        widgetWidth: Float,
        widgetHeight: Float,
        paddingDp: Float,
        others: List<ElementGeometry>,
        thresholdDp: Float,
    ): SnapResult {
        if (!(thresholdDp > 0f)) return SnapResult.NONE

        var bestX: Pair<Float, Guide>? = null
        fun offerX(movingX: Float, targetX: Float, kind: GuideKind) {
            val delta = targetX - movingX
            if (abs(delta) > thresholdDp) return
            if (bestX == null || abs(delta) < abs(bestX!!.first)) bestX = delta to Guide(true, targetX, kind)
        }
        offerX(moving.centerX, widgetWidth / 2f, GuideKind.WIDGET_CENTER)
        offerX(moving.left, paddingDp, GuideKind.PADDING_EDGE)
        offerX(moving.right, widgetWidth - paddingDp, GuideKind.PADDING_EDGE)
        others.forEach { offerX(moving.centerX, it.box.centerX, GuideKind.ELEMENT_CENTER) }

        var bestY: Pair<Float, Guide>? = null
        fun offerY(movingY: Float, targetY: Float, kind: GuideKind) {
            val delta = targetY - movingY
            if (abs(delta) > thresholdDp) return
            if (bestY == null || abs(delta) < abs(bestY!!.first)) bestY = delta to Guide(false, targetY, kind)
        }
        offerY(moving.centerY, widgetHeight / 2f, GuideKind.WIDGET_CENTER)
        offerY(moving.top, paddingDp, GuideKind.PADDING_EDGE)
        offerY(moving.bottom, widgetHeight - paddingDp, GuideKind.PADDING_EDGE)
        others.forEach { offerY(moving.centerY, it.box.centerY, GuideKind.ELEMENT_CENTER) }
        if (movingBaseline != null) {
            others.forEach { other -> other.baseline?.let { offerY(movingBaseline, it, GuideKind.ELEMENT_BASELINE) } }
        }

        val x = bestX
        val y = bestY
        return SnapResult(
            dx = x?.first ?: 0f,
            dy = y?.first ?: 0f,
            guides = listOfNotNull(x?.second, y?.second),
        )
    }
}

/** Maps screen movement to the requested offset the model stores, and clamps what a gesture may request. */
object CanvasOffsets {
    /** Same range as the numeric entry / sliders ([com.stupidsavacan.clocky.studio.DesignEdits.offsetDp]). */
    const val RANGE_DP = 200f

    /**
     * Requested X is stored in reading direction (the resolver mirrors it for RTL), so a physical
     * rightward move is +x in LTR and -x in RTL. Y is not mirrored.
     */
    fun requested(startX: Float, startY: Float, dxDp: Float, dyDp: Float, rtl: Boolean): Pair<Float, Float> =
        (startX + if (rtl) -dxDp else dxDp) to (startY + dyDp)

    /**
     * The resolver clamps effective offsets to half the entry's size; requesting more would leave
     * the preview still while the stored value kept growing. Gestures therefore stop at the
     * smaller of that bound and the control range. A stored value already beyond the bound
     * ([startX]/[startY]) keeps its own magnitude as the limit, so touching the element never
     * pulls it in.
     */
    fun clamp(
        x: Float, y: Float, startX: Float, startY: Float, entryWidthDp: Float, entryHeightDp: Float,
    ): Pair<Float, Float> = axis(x, startX, entryWidthDp) to axis(y, startY, entryHeightDp)

    private fun axis(value: Float, start: Float, extentDp: Float): Float {
        val bound = if (extentDp > 0f) min(RANGE_DP, extentDp / 2f) else RANGE_DP
        val limit = max(bound, min(abs(start), RANGE_DP))
        return value.coerceIn(-limit, limit)
    }

    /** Direction of a 1 dp nudge in physical screen terms. */
    enum class Nudge(val dx: Float, val dy: Float) { LEFT(-1f, 0f), RIGHT(1f, 0f), UP(0f, -1f), DOWN(0f, 1f) }

    /** One 1 dp step on screen, as a new requested offset kept inside the control range. */
    fun nudged(x: Float, y: Float, nudge: Nudge, rtl: Boolean): Pair<Float, Float> {
        val (nx, ny) = requested(x, y, nudge.dx, nudge.dy, rtl)
        return clamp(nx, ny, x, y, 0f, 0f)
    }
}

object CanvasHitTester {
    /** Minimum touch target edge (End-State: every interactive element is at least 48 dp). */
    const val MIN_TARGET_DP = 48f

    /**
     * The element under the point, comparing against each box grown to at least [minTargetDp] around
     * its center. When boxes overlap the nearest center wins, so a small Info line stays reachable
     * next to a large Time.
     */
    fun hit(x: Float, y: Float, elements: List<ElementGeometry>, minTargetDp: Float = MIN_TARGET_DP): ElementGeometry? =
        elements.filter { grown(it.box, minTargetDp).contains(x, y) }
            .minByOrNull { el ->
                val dx = x - el.box.centerX
                val dy = y - el.box.centerY
                dx * dx + dy * dy
            }

    fun grown(box: Box, minDp: Float): Box {
        val growX = max(0f, (minDp - box.width) / 2f)
        val growY = max(0f, (minDp - box.height) / 2f)
        return Box(box.left - growX, box.top - growY, box.right + growX, box.bottom + growY)
    }
}
