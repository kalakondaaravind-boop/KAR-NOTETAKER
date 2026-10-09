package app.margin.domain.editor

/**
 * Bounded undo/redo history. Consecutive small edits (typing a word) made within
 * [coalesceWindowMs] are coalesced into a single undo step so undo feels natural.
 */
class UndoStack(
    private val limit: Int = 200,
    private val coalesceWindowMs: Long = 1_000,
) {
    private val undo = ArrayDeque<TextValue>()
    private val redo = ArrayDeque<TextValue>()
    private var lastRecordAt = Long.MIN_VALUE / 2
    private var lastWasTyping = false

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    /** Record [before] as an undo point for a change that produced [after]. */
    fun record(before: TextValue, after: TextValue, nowMs: Long) {
        if (before.text == after.text) return
        val typing = isSimpleTyping(before, after)
        val coalesce = typing && lastWasTyping && nowMs - lastRecordAt <= coalesceWindowMs && undo.isNotEmpty() &&
            !after.text.endsWith(" ") // a space ends the "word" step
        if (!coalesce) {
            undo.addLast(before)
            if (undo.size > limit) undo.removeFirst()
        }
        redo.clear()
        lastRecordAt = nowMs
        lastWasTyping = typing
    }

    fun undo(current: TextValue): TextValue? {
        val prev = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        lastWasTyping = false
        return prev
    }

    fun redo(current: TextValue): TextValue? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        lastWasTyping = false
        return next
    }

    fun clear() { undo.clear(); redo.clear(); lastWasTyping = false }

    /** Ends the current typing run so the next change becomes its own undo step (e.g. formatting). */
    fun checkpoint() { lastWasTyping = false }

    private fun isSimpleTyping(b: TextValue, a: TextValue): Boolean {
        val d = a.text.length - b.text.length
        return (d == 1 || d == -1) && !b.hasSelection
    }
}
