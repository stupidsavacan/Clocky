package com.stupidsavacan.clocky.studio

import com.stupidsavacan.clocky.design.model.DigitalDesign

/**
 * Studio's editing session (CLOCKY_END_STATE.md 7; docs/architecture/PHASE_2_STUDIO.md 3).
 *
 * Edits operate on a draft: nothing here touches persistence. Undo history holds meaningful user
 * operations (a typed value, a chip choice, a whole slider drag), each as the immutable design
 * before and after it. [DigitalDesign] is a tree of immutable data classes, so a snapshot is a
 * cheap reference and equality is structural; no JSON round trip is involved.
 *
 * Phase 3 can add canvas gestures by calling [apply] with the same shape.
 */
class EditSession(
    /** The design the session opened with: the "last saved" state, and what [isDirty] compares to. */
    val baseline: DigitalDesign,
    /**
     * What per-slot and per-property "reset" returns to: the built-in design the draft came from,
     * or [baseline] for a design that has no preset.
     */
    val reference: DigitalDesign = baseline,
    val referenceIsPreset: Boolean = false,
    private val limit: Int = DEFAULT_LIMIT,
) {
    /** One user operation. [key] is non-null for continuous gestures that should undo as one step. */
    private data class Entry(val id: String, val key: String?, val before: DigitalDesign, val after: DigitalDesign)

    var design: DigitalDesign = baseline
        private set

    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()

    /** The coalescing key of the gesture in progress, until [endGesture] or a different edit. */
    private var openKey: String? = null
    private data class Checkpoint(val design: DigitalDesign, val undo: List<Entry>, val redo: List<Entry>)
    private var checkpoint: Checkpoint? = null

    /** Canvas cancellation restores the whole transaction, including pre-existing redo history. */
    fun beginGesture() {
        endGesture()
        checkpoint = Checkpoint(design, undoStack.toList(), redoStack.toList())
    }

    fun cancelGesture() {
        checkpoint?.let {
            design = it.design
            undoStack.clear(); undoStack.addAll(it.undo)
            redoStack.clear(); redoStack.addAll(it.redo)
        }
        endGesture()
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Id of the edit [undo] would revert / [redo] would reapply, for announcements. */
    val undoId: String? get() = undoStack.lastOrNull()?.id
    val redoId: String? get() = redoStack.lastOrNull()?.id

    val isDirty: Boolean get() = design != baseline
    val historySize: Int get() = undoStack.size

    /**
     * Applies [transform] as one named operation. Returns false (and records nothing) when it
     * changes nothing. Consecutive edits with the same non-null [key] merge into one undo step
     * until [endGesture]; a different key or a keyless edit always starts a new step.
     */
    fun apply(id: String, key: String? = null, transform: (DigitalDesign) -> DigitalDesign): Boolean {
        val before = design
        val after = transform(before)
        if (after == before) return false

        val last = undoStack.lastOrNull()
        if (key != null && key == openKey && last != null && last.key == key) {
            undoStack.removeLast()
            undoStack.addLast(last.copy(after = after))
        } else {
            undoStack.addLast(Entry(id, key, before, after))
            while (undoStack.size > limit) undoStack.removeFirst()
        }
        openKey = key
        redoStack.clear()
        design = after
        return true
    }

    /** Ends the current continuous gesture so the next edit with the same key is a new step. */
    fun endGesture() {
        openKey = null
        checkpoint = null
    }

    fun undo(): Boolean {
        endGesture()
        val entry = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(entry)
        design = entry.before
        openKey = null
        return true
    }

    fun redo(): Boolean {
        endGesture()
        val entry = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(entry)
        design = entry.after
        openKey = null
        return true
    }

    /** Discards history after the draft was replaced from outside (e.g. restored after process death). */
    fun adopt(restored: DigitalDesign) {
        design = restored
        undoStack.clear()
        redoStack.clear()
        openKey = null
        checkpoint = null
    }

    companion object {
        /** End-State 7: 50 steps per session. */
        const val DEFAULT_LIMIT = 50
    }
}
