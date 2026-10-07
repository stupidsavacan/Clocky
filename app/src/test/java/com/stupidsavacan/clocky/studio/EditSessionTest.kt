package com.stupidsavacan.clocky.studio

import com.stupidsavacan.clocky.design.model.DigitalDesign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditSessionTest {
    private val base = DigitalDesign()
    private val scope = EditScope.ALL

    private fun EditSession.size(sp: Float, key: String? = null) =
        apply("time.size", key) { DesignEdits.setSize(it, TextTarget.TIME, sp, scope) }

    @Test
    fun editsChangeOnlyTheDraftAndTrackDirtiness() {
        val s = EditSession(base)
        assertFalse(s.isDirty)
        assertTrue(s.size(80f))
        assertTrue(s.isDirty)
        assertEquals(80f, s.design.time.style.sizeSp, 0f)
        assertEquals("the baseline is never mutated", base, s.baseline)
    }

    @Test
    fun anEditThatChangesNothingIsNotRecorded() {
        val s = EditSession(base)
        assertFalse(s.size(base.time.style.sizeSp))
        assertFalse(s.canUndo)
    }

    @Test
    fun undoAndRedoWalkTheHistory() {
        val s = EditSession(base)
        s.size(70f)
        s.apply("time.weight") { DesignEdits.setWeight(it, TextTarget.TIME, 700, scope) }
        assertEquals("time.weight", s.undoId)

        assertTrue(s.undo())
        assertEquals(400, s.design.time.style.weight)
        assertEquals(70f, s.design.time.style.sizeSp, 0f)
        assertEquals("time.weight", s.redoId)

        assertTrue(s.undo())
        assertEquals(base, s.design)
        assertFalse(s.undo())
        assertFalse(s.isDirty)

        assertTrue(s.redo())
        assertTrue(s.redo())
        assertEquals(700, s.design.time.style.weight)
        assertFalse(s.redo())
    }

    @Test
    fun aNewEditClearsTheRedoBranch() {
        val s = EditSession(base)
        s.size(70f)
        s.undo()
        assertTrue(s.canRedo)
        s.size(90f)
        assertFalse(s.canRedo)
    }

    @Test
    fun aSliderDragIsOneUndoStepButSeparateGesturesAreNot() {
        val s = EditSession(base)
        listOf(66f, 68f, 70f, 72f).forEach { s.size(it, key = "time.size") }
        assertEquals(1, s.historySize)
        s.endGesture()
        s.size(90f, key = "time.size")
        assertEquals(2, s.historySize)

        s.undo()
        assertEquals("undo returns to where the drag started, not to the previous frame", 72f, s.design.time.style.sizeSp, 0f)
        s.undo()
        assertEquals(base.time.style.sizeSp, s.design.time.style.sizeSp, 0f)
    }

    @Test
    fun interleavedDifferentKeysDoNotMerge() {
        val s = EditSession(base)
        s.size(70f, key = "a")
        s.apply("date.size", key = "b") { DesignEdits.setSize(it, TextTarget.DATE, 20f, scope) }
        s.size(75f, key = "a")
        assertEquals(3, s.historySize)
    }

    @Test
    fun historyIsBoundedToFiftySteps() {
        val s = EditSession(base)
        repeat(80) { s.size(20f + it) }
        assertEquals(EditSession.DEFAULT_LIMIT, s.historySize)
        repeat(60) { s.undo() }
        assertEquals("the oldest steps fell off the history", 20f + 29f, s.design.time.style.sizeSp, 0f)
    }

    @Test
    fun undoingBackToTheBaselineIsClean() {
        val s = EditSession(base)
        s.size(100f)
        s.undo()
        assertFalse(s.isDirty)
    }

    @Test
    fun adoptReplacesTheDraftWithoutHistory() {
        val s = EditSession(base)
        s.size(100f)
        val restored = s.design
        val fresh = EditSession(base)
        fresh.adopt(restored)
        assertEquals(restored, fresh.design)
        assertFalse(fresh.canUndo)
        assertTrue(fresh.isDirty)
        assertNull(fresh.undoId)
    }
}
