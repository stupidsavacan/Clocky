package com.stupidsavacan.clocky.studio.canvas

import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.resolve.SizeContext
import com.stupidsavacan.clocky.studio.*
import org.junit.Assert.*
import org.junit.Test

class CanvasResizeTest {
    @Test fun pinchUsesRequestedStartAndRejectsInvalidSpans() {
        assertEquals(120f, CanvasPinch.size(80f, 100f, 150f)!!, 0f)
        assertEquals(40f, CanvasPinch.size(80f, 100f, 50f)!!, 0f)
        assertNull(CanvasPinch.size(80f, 0f, 150f))
        assertNull(CanvasPinch.size(80f, Float.NaN, 150f))
        assertNull(CanvasPinch.size(80f, 100f, Float.POSITIVE_INFINITY))
        assertNull(CanvasPinch.size(80f, 100f, -1f))
    }
    @Test fun resizingEqualsNumericEntryForEveryTargetAndScopeAndClamps() {
        for (target in TextTarget.entries) for (scope in listOf(EditScope.ALL) + SizeClass.entries.map(::EditScope)) {
            val base = DigitalDesign()
            val session = EditSession(base)
            session.beginGesture()
            for (span in listOf(100f, 120f, 200f, 100000f)) {
                val sp = CanvasPinch.size(DesignEdits.sizeOf(base, target, scope), 100f, span)!!
                session.apply("pinch", "pinch") { DesignEdits.setSize(it, target, sp, scope) }
            }
            val expected = DesignEdits.setSize(base, target, 100000f, scope)
            assertEquals(expected, session.design)
            session.endGesture()
            assertEquals(1, session.historySize)
            assertTrue(session.undo())
            assertEquals(base, session.design)
        }
    }
    @Test fun cancelRestoresPriorUndoRedoEvenAtHistoryLimit() {
        val s = EditSession(DigitalDesign(), limit = 1)
        s.apply("first") { DesignEdits.setSize(it, TextTarget.TIME, 80f, EditScope.ALL) }
        val before = s.design
        s.apply("second") { DesignEdits.setSize(it, TextTarget.TIME, 90f, EditScope.ALL) }
        s.undo()
        val redo = s.redoId
        s.beginGesture()
        s.apply("drag", "canvas") { DesignEdits.setOffset(it, TextTarget.TIME, 12f, 13f, EditScope.ALL) }
        s.apply("pinch", "canvas") { DesignEdits.setSize(it, TextTarget.TIME, 110f, EditScope.ALL) }
        s.cancelGesture()
        assertEquals(before, s.design)
        assertEquals(redo, s.redoId)
        assertFalse(s.canUndo)
        assertTrue(s.redo())
        assertEquals(90f, s.design.time.style.sizeSp, 0f)
    }
    @Test fun noOpCancellationDoesNotUndoAnEarlierEdit() {
        val s = EditSession(DigitalDesign())
        s.apply("first") { DesignEdits.setSize(it, TextTarget.TIME, 80f, EditScope.ALL) }
        val before = s.design
        s.beginGesture(); s.cancelGesture()
        assertEquals(before, s.design)
        assertEquals(1, s.historySize)
        assertFalse(s.canRedo)
    }
    @Test fun measuredPairsAndShapePriorityArePreserved() {
        assertEquals(SizeContext(173, 58, 325, 122), PreviewCells(2, 1).size)
        assertEquals(SizeContext(363, 132, 667, 260), PreviewCells(4, 2).size)
        assertEquals(SizeContext(363, 281, 667, 537), PreviewCells(4, 4).size)
        assertEquals(SizeClass.STRIP, PreviewCells(5, 1).sizeClass)
        assertEquals(SizeClass.CARD, PreviewCells(3, 2).sizeClass)
        assertEquals(SizeClass.LARGE, PreviewCells(4, 3).sizeClass)
        assertEquals(SizeClass.SQUARE, PreviewCells(4, 4).sizeClass)
        assertEquals(16, PreviewCells.ALL.size)
    }
    @Test fun allRecordedMotoPairsRemainExact() {
        val recorded = listOf(
            PreviewCells(2, 1) to SizeContext(173, 58, 325, 122),
            PreviewCells(2, 2) to SizeContext(173, 132, 325, 260),
            PreviewCells(3, 1) to SizeContext(268, 58, 496, 122),
            PreviewCells(3, 2) to SizeContext(268, 132, 496, 260),
            PreviewCells(3, 3) to SizeContext(268, 206, 496, 398),
            PreviewCells(4, 1) to SizeContext(363, 58, 667, 122),
            PreviewCells(4, 2) to SizeContext(363, 132, 667, 260),
            PreviewCells(4, 3) to SizeContext(363, 206, 667, 398),
            PreviewCells(4, 4) to SizeContext(363, 281, 667, 537),
        )
        recorded.forEach { (cells, measured) -> assertEquals(measured, cells.size) }
        assertEquals(PreviewCells(5, 3), PreviewCells(4, 2).dragged(171f, 74f, landscape = true))
    }
    @Test fun handleSnapsToCellsAndClampsAtBothLimits() {
        assertEquals(PreviewCells(5, 4), PreviewCells(4, 2).dragged(10000f, 10000f))
        assertEquals(PreviewCells(2, 1), PreviewCells(4, 2).dragged(-10000f, -10000f))
        assertEquals(PreviewCells(4, 2), PreviewCells(4, 2).dragged(40f, 60f))
        assertEquals(PreviewCells(5, 3), PreviewCells(4, 2).dragged(95f, 138f))
    }
}
