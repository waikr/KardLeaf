package org.qosp.notes.ui.utils.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {
    @Test
    fun continuousTypingMergesWithinWindow() {
        val history = EditHistory()
        repeat(100) { index -> history.record(insert(index, "x", index.toLong())) }

        assertEquals(1, history.undoSize)
        assertEquals(100, history.historyChars)
        assertEquals("x".repeat(100), history.undo()!!.changes.single().insertedText)
    }

    @Test
    fun typingBreaksOnTimeoutCursorMoveAndDifferentPosition() {
        val history = EditHistory()
        history.record(insert(0, "a", 0))
        history.record(insert(1, "b", 401))
        history.record(insert(0, "c", 402, before = TextSelection(0, 0)))
        history.breakMerge()
        history.record(insert(1, "d", 403, before = TextSelection(1, 1)))

        assertEquals(4, history.undoSize)
    }

    @Test
    fun backspaceAndForwardDeleteMergeSeparately() {
        val backspace = EditHistory()
        repeat(100) { index ->
            val start = 99 - index
            backspace.record(delete(start, "x", OperationType.BACKSPACE, index.toLong(), start + 1))
        }
        assertEquals(1, backspace.undoSize)
        assertEquals("x".repeat(100), backspace.undo()!!.changes.single().deletedText)

        val forward = EditHistory()
        repeat(100) { index -> forward.record(delete(0, "x", OperationType.FORWARD_DELETE, index.toLong(), 0)) }
        assertEquals(1, forward.undoSize)
        assertEquals("x".repeat(100), forward.undo()!!.changes.single().deletedText)

        val mixed = EditHistory()
        mixed.record(delete(1, "a", OperationType.BACKSPACE, 0, 2))
        mixed.record(delete(1, "b", OperationType.FORWARD_DELETE, 1, 1))
        assertEquals(2, mixed.undoSize)
    }

    @Test
    fun selectionReplacementRestoresTextAndBothSelections() {
        val history = EditHistory()
        history.record(
            change(
                start = 0,
                deleted = "测试文字",
                inserted = "**测试文字**",
                before = TextSelection(0, 4),
                after = TextSelection(2, 6),
                type = OperationType.TOOLBAR,
            ),
        )

        val undone = history.undo()
        assertNotNull(undone)
        assertEquals("测试文字", apply("**测试文字**", undone!!, undo = true))
        assertEquals(TextSelection(0, 4), undone.selectionBefore)

        val redone = history.redo()!!
        assertEquals("**测试文字**", apply("测试文字", redone, undo = false))
        assertEquals(TextSelection(2, 6), redone.selectionAfter)
    }

    @Test
    fun toolbarAndListContinuationsAreSingleEntries() {
        val toolbar = EditHistory()
        toolbar.beginBatch(TextSelection(0, 4), includePrevious = false)
        toolbar.record(change(0, "测试文字", "**测试文字**", type = OperationType.TOOLBAR))
        toolbar.endBatch(TextSelection(2, 6))
        assertEquals(1, toolbar.undoSize)

        val list = EditHistory()
        list.record(insert(6, "\n", 0, type = OperationType.NEW_LINE))
        list.beginBatch(TextSelection(7, 7), includePrevious = true)
        list.record(insert(7, "- ", 1, before = TextSelection(7, 7), type = OperationType.NEW_LINE))
        list.endBatch(TextSelection(9, 9))
        val listEntry = list.undo()!!
        assertEquals(1, list.redoSize)
        assertEquals("- item", apply("- item\n- ", listEntry, undo = true))

        val task = EditHistory()
        task.record(insert(9, "\n", 0, type = OperationType.NEW_LINE))
        task.beginBatch(TextSelection(10, 10), includePrevious = true)
        task.record(insert(10, "- [ ] ", 1, before = TextSelection(10, 10), type = OperationType.NEW_LINE))
        task.endBatch(TextSelection(16, 16))
        assertEquals("- [ ] one", apply("- [ ] one\n- [ ] ", task.undo()!!, undo = true))

        val emptyList = EditHistory()
        emptyList.record(insert(2, "\n", 0, type = OperationType.NEW_LINE))
        emptyList.beginBatch(TextSelection(3, 3), includePrevious = true)
        emptyList.record(delete(0, "- ", OperationType.NEW_LINE, 1, 3))
        emptyList.endBatch(TextSelection(1, 1))
        assertEquals("- ", apply("\n", emptyList.undo()!!, undo = true))
    }

    @Test
    fun historyLimitsRedoInvalidationAndClearStayConsistent() {
        val countLimited = EditHistory(maxEntries = 3)
        repeat(5) { index ->
            countLimited.breakMerge()
            countLimited.record(insert(index, "x", index.toLong()))
        }
        assertEquals(3, countLimited.undoSize)

        countLimited.undo()
        assertTrue(countLimited.canRedo())
        countLimited.record(insert(9, "y", 10))
        assertFalse(countLimited.canRedo())

        countLimited.clear()
        assertFalse(countLimited.canUndo())
        assertFalse(countLimited.canRedo())

        val charLimited = EditHistory(maxEntries = 10, maxTotalChars = 10)
        repeat(3) { index ->
            charLimited.breakMerge()
            charLimited.record(insert(index * 4, "xxxx", index.toLong()))
        }
        assertEquals(2, charLimited.undoSize)
        assertEquals(8, charLimited.historyChars)

        val operationLimited = EditHistory(maxOperationChars = 5)
        operationLimited.record(insert(0, "123456", 0))
        assertFalse(operationLimited.canUndo())
        assertEquals(0, operationLimited.historyChars)
    }

    @Test
    fun composingUpdatesCollapseToFinalCommittedWord() {
        val history = EditHistory()
        history.record(change(0, "", "n", after = TextSelection(1, 1), type = OperationType.COMPOSING))
        history.record(
            change(
                0,
                "n",
                "ni",
                before = TextSelection(1, 1),
                after = TextSelection(2, 2),
                timestamp = 1,
                type = OperationType.COMPOSING,
            ),
        )
        history.record(
            change(
                0,
                "ni",
                "你好",
                before = TextSelection(2, 2),
                after = TextSelection(2, 2),
                timestamp = 2,
                type = OperationType.REPLACE,
            ),
        )

        assertEquals(1, history.undoSize)
        val entry = history.undo()!!
        assertEquals("你好", entry.changes.single().insertedText)
        assertEquals("", apply("你好", entry, undo = true))
    }

    private fun insert(
        start: Int,
        text: String,
        timestamp: Long,
        before: TextSelection = TextSelection(start, start),
        type: OperationType = OperationType.INSERT,
    ) = change(
        start = start,
        inserted = text,
        before = before,
        after = TextSelection(start + text.length, start + text.length),
        timestamp = timestamp,
        type = type,
    )

    private fun delete(
        start: Int,
        text: String,
        type: OperationType,
        timestamp: Long,
        cursorBefore: Int,
    ) =
        change(
            start = start,
            deleted = text,
            before = TextSelection(cursorBefore, cursorBefore),
            after = TextSelection(start, start),
            timestamp = timestamp,
            type = type,
        )

    private fun change(
        start: Int,
        deleted: String = "",
        inserted: String = "",
        before: TextSelection = TextSelection(start, start),
        after: TextSelection = TextSelection(start + inserted.length, start + inserted.length),
        timestamp: Long = 0,
        type: OperationType,
    ) = TextChange(start, deleted, inserted, before, after, timestamp, type)

    private fun apply(
        text: String,
        entry: HistoryEntry,
        undo: Boolean,
    ): String {
        val value = StringBuilder(text)
        val changes = if (undo) entry.changes.asReversed() else entry.changes
        changes.forEach { change ->
            val length = if (undo) change.insertedText.length else change.deletedText.length
            value.replace(
                change.start,
                change.start + length,
                if (undo) change.deletedText else change.insertedText,
            )
        }
        return value.toString()
    }
}
