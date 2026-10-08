package com.stupidsavacan.clocky.studio.canvas

import com.stupidsavacan.clocky.studio.DesignEdits
import com.stupidsavacan.clocky.studio.EditScope
import com.stupidsavacan.clocky.studio.EditSession
import com.stupidsavacan.clocky.studio.TextTarget
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasMathTest {
    private val w = 360f
    private val h = 130f
    private val pad = 8f

    private fun box(cx: Float, cy: Float, bw: Float = 100f, bh: Float = 40f) =
        Box(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2)

    private fun solve(moving: Box, baseline: Float? = null, others: List<ElementGeometry> = emptyList(), threshold: Float = 6f) =
        SnapSolver.solve(moving, baseline, w, h, pad, others, threshold)

    // ---- snap solver ----

    @Test fun snapsCenterToWidgetCenterLines() {
        val r = solve(box(cx = 183f, cy = 62f))
        assertEquals(-3f, r.dx, 1e-4f)
        assertEquals(3f, r.dy, 1e-4f)
        assertEquals(setOf(Guide(true, 180f, GuideKind.WIDGET_CENTER), Guide(false, 65f, GuideKind.WIDGET_CENTER)), r.guides.toSet())
    }

    @Test fun snapsEdgesToPaddingEdges() {
        // left edge 11 -> padding 8; bottom 120.5 -> 122
        val r = solve(Box(11f, 82f, 111f, 120.5f))
        assertEquals(-3f, r.dx, 1e-4f)
        assertEquals(1.5f, r.dy, 1e-4f)
        assertTrue(r.guides.all { it.kind == GuideKind.PADDING_EDGE })
        // right edge to the far padding edge
        val right = solve(Box(246f, 20f, 349f, 40f))
        assertEquals(3f, right.dx, 1e-4f)
        assertEquals(Guide(true, 352f, GuideKind.PADDING_EDGE), right.guides.single { it.vertical })
    }

    @Test fun snapsToOtherElementsCenterAndBaseline() {
        val date = ElementGeometry(TextTarget.DATE, Box(20f, 90f, 80f, 110f), baseline = 105f)
        val moving = Box(30f, 10f, 90f, 40f) // center (60, 25): no widget line within reach
        // x: moving center 60 vs date center 50 is 10 away -> no snap; use center 53
        val x = solve(moving.shifted(-7f, 0f), others = listOf(date))
        assertEquals(Guide(true, 50f, GuideKind.ELEMENT_CENTER), x.guides.single { it.vertical })
        // y: baseline 102 -> date baseline 105
        val y = solve(Box(150f, 62f, 250f, 100f).shifted(0f, 0f), baseline = 102f, others = listOf(date))
        assertEquals(3f, y.dy, 1e-4f)
        assertEquals(Guide(false, 105f, GuideKind.ELEMENT_BASELINE), y.guides.single { !it.vertical })
    }

    @Test fun nothingSnapsOutsideTheThreshold() {
        val r = solve(box(cx = 187f, cy = 72f))
        assertEquals(SnapResult.NONE, r)
        assertFalse(r.snapped)
        assertEquals(SnapResult.NONE, solve(box(cx = 180.1f, cy = 65f), threshold = 0f))
    }

    @Test fun nearestTargetWinsPerAxis() {
        val other = ElementGeometry(TextTarget.INFO, box(cx = 176f, cy = 20f), baseline = null)
        // widget center 180 is 1.5 away from 178.5; other's center 176 is 2.5 away
        val r = solve(box(cx = 178.5f, cy = 5f), others = listOf(other))
        assertEquals(1.5f, r.dx, 1e-4f)
    }

    @Test fun thresholdWidensWithPreviewDownscale() {
        // 6 screen dp at a 0.5 preview scale is 12 widget dp
        assertEquals(6f, solve(box(cx = 186f, cy = 0f), threshold = 12f).dx.let { -it }, 1e-4f)
    }

    // ---- gesture -> requested offset ----

    @Test fun requestedOffsetFollowsReadingDirection() {
        assertEquals(15f to 4f, CanvasOffsets.requested(10f, 1f, 5f, 3f, rtl = false))
        assertEquals(5f to 4f, CanvasOffsets.requested(10f, 1f, 5f, 3f, rtl = true))
    }

    @Test fun clampKeepsGestureInsideResolverAndControlRange() {
        assertEquals(100f to -30f, CanvasOffsets.clamp(400f, -90f, 0f, 0f, 200f, 60f))
        assertEquals(-200f to 200f, CanvasOffsets.clamp(-999f, 999f, 0f, 0f, 0f, 0f))
    }

    @Test fun anAlreadyLargeStoredOffsetIsNotPulledInByTouchingIt() {
        // saved +150 on a 200dp-wide entry: bound 100, but the stored magnitude keeps being allowed
        assertEquals(150f, CanvasOffsets.clamp(150f, 0f, 150f, 0f, 200f, 100f).first, 0f)
        assertEquals(150f, CanvasOffsets.clamp(190f, 0f, 150f, 0f, 200f, 100f).first, 0f)
    }

    @Test fun nudgeMovesOneDpOnScreenAndMirrorsInRtl() {
        assertEquals(6f to 2f, CanvasOffsets.nudged(5f, 2f, CanvasOffsets.Nudge.RIGHT, rtl = false))
        assertEquals(4f to 2f, CanvasOffsets.nudged(5f, 2f, CanvasOffsets.Nudge.RIGHT, rtl = true))
        assertEquals(5f to 1f, CanvasOffsets.nudged(5f, 2f, CanvasOffsets.Nudge.UP, rtl = true))
        assertEquals(200f to 0f, CanvasOffsets.nudged(200f, 0f, CanvasOffsets.Nudge.RIGHT, rtl = false))
    }

    // ---- hit testing ----

    @Test fun hitTestGrowsSmallBoxesToATouchTarget() {
        val small = ElementGeometry(TextTarget.INFO, Box(100f, 100f, 130f, 112f), baseline = 110f) // 30x12
        assertEquals(TextTarget.INFO, CanvasHitTester.hit(115f, 95f, listOf(small))?.target) // 6.5 dp above its top edge
        assertNull(CanvasHitTester.hit(115f, 60f, listOf(small)))
    }

    @Test fun overlappingTargetsGoToTheNearestCenter() {
        val time = ElementGeometry(TextTarget.TIME, Box(40f, 20f, 320f, 100f), 90f)
        val info = ElementGeometry(TextTarget.INFO, Box(150f, 98f, 210f, 112f), 110f)
        assertEquals(TextTarget.INFO, CanvasHitTester.hit(180f, 106f, listOf(time, info))?.target)
        assertEquals(TextTarget.TIME, CanvasHitTester.hit(180f, 60f, listOf(time, info))?.target)
    }

    // ---- gesture -> Edit (through the real DesignEdits / EditSession) ----

    @Test fun oneDragIsOneUndoStepAndEqualsTheTypedValue() {
        val session = EditSession(DigitalDesign())
        val key = "canvas.move.TIME"
        var x = 0f
        repeat(25) {
            x += 1.5f
            session.apply(key, key) { DesignEdits.setOffset(it, TextTarget.TIME, x, x / 2, EditScope.ALL) }
        }
        session.endGesture()
        assertEquals(1, session.historySize)
        val typed = DesignEdits.setOffset(DigitalDesign(), TextTarget.TIME, x, x / 2, EditScope.ALL)
        assertEquals("canvas result is byte-for-byte what typing the same values produces", typed, session.design)
        assertTrue(session.undo())
        assertEquals(DigitalDesign(), session.design)
    }

    @Test fun scopedDragWritesOnlyThatClassPatch() {
        val base = DigitalDesign()
        val scoped = DesignEdits.setOffset(base, TextTarget.DATE, 7f, -3f, EditScope(SizeClass.SQUARE))
        assertEquals(base.date.style.xDp, scoped.date.style.xDp, 0f)
        assertEquals(7f to -3f, DesignEdits.offsetOf(scoped, TextTarget.DATE, EditScope(SizeClass.SQUARE)))
        assertEquals(base.date.style.xDp to base.date.style.yDp, DesignEdits.offsetOf(scoped, TextTarget.DATE, EditScope.ALL))
        assertNotNull(scoped.layout.patchFor(SizeClass.SQUARE))
    }
}
