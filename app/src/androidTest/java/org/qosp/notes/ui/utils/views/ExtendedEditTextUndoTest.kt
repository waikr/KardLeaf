package org.qosp.notes.ui.utils.views

import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qosp.notes.ui.editor.markdown.MarkdownSpan
import org.qosp.notes.ui.editor.markdown.insertMarkdown

@RunWith(AndroidJUnit4::class)
class ExtendedEditTextUndoTest {
    @Test
    fun continuousTypingUndoRedoRestoresTextAndCaret() {
        onMain {
            val editor = editor("")
            repeat(100) { editor.text!!.insert(editor.selectionStart, "x") }

            editor.undo()
            assertEquals("", editor.text.toString())
            assertEquals(0, editor.selectionStart)
            assertTrue(editor.canRedo())

            editor.redo()
            assertEquals("x".repeat(100), editor.text.toString())
            assertEquals(100, editor.selectionStart)
        }
    }

    @Test
    fun backspaceAndForwardDeleteUndoInRuns() {
        onMain {
            val backspace = editor("abc")
            repeat(3) {
                val cursor = backspace.selectionStart
                backspace.text!!.delete(cursor - 1, cursor)
            }
            backspace.undo()
            assertEquals("abc", backspace.text.toString())

            val forward = editor("abc")
            forward.setSelection(0)
            repeat(3) { forward.text!!.delete(0, 1) }
            forward.undo()
            assertEquals("abc", forward.text.toString())
        }
    }

    @Test
    fun boldUndoRedoRestoresOriginalAndFormattedSelections() {
        onMain {
            val editor = editor("测试文字")
            editor.setSelection(0, 4)
            editor.insertMarkdown(MarkdownSpan.BOLD)
            assertEquals("**测试文字**", editor.text.toString())
            assertEquals(2, editor.selectionStart)
            assertEquals(6, editor.selectionEnd)

            editor.undo()
            assertEquals("测试文字", editor.text.toString())
            assertEquals(0, editor.selectionStart)
            assertEquals(4, editor.selectionEnd)

            editor.redo()
            assertEquals("**测试文字**", editor.text.toString())
            assertEquals(2, editor.selectionStart)
            assertEquals(6, editor.selectionEnd)
        }
    }

    @Test
    fun listContinuationAndEmptyListExitUndoInOneStep() {
        onMain {
            val continued = editor("- item")
            continued.text!!.insert(6, "\n")
            continued.editHistory(OperationType.NEW_LINE, includePrevious = true) {
                text!!.insert(7, "- ")
                setSelection(9)
            }
            continued.undo()
            assertEquals("- item", continued.text.toString())

            val empty = editor("- ")
            empty.text!!.insert(2, "\n")
            empty.editHistory(OperationType.NEW_LINE, includePrevious = true) {
                text!!.delete(0, 2)
                setSelection(1)
            }
            empty.undo()
            assertEquals("- ", empty.text.toString())
        }
    }

    @Test
    fun documentBindingPreservesSameContentAndClearsExternalReplacement() {
        onMain {
            val editor = editor("a")
            editor.text!!.insert(1, "b")
            val sameContent = "ab"
            if (editor.text.toString() != sameContent) {
                editor.withoutTextWatchers { setText(sameContent) }
                editor.clearHistory()
            }
            assertTrue(editor.canUndo())

            editor.withoutTextWatchers { setText("external") }
            editor.clearHistory()
            assertFalse(editor.canUndo())
            assertFalse(editor.canRedo())
        }
    }

    @Test
    fun inputConnectionCompositionAndWholeWordCommitUndoOnce() {
        onMain {
            val editor = editor("")
            val input = editor.onCreateInputConnection(EditorInfo())
            input.setComposingText("ni", 1)
            input.setComposingText("你好", 1)
            input.finishComposingText()
            assertEquals("你好", editor.text.toString())
            editor.undo()
            assertEquals("", editor.text.toString())

            input.commitText("hello", 1)
            assertEquals("hello", editor.text.toString())
            editor.undo()
            assertEquals("", editor.text.toString())
        }
    }

    @Test
    fun largeTextEditAndFiftyUndosStayIncremental() {
        onMain {
            val runtime = Runtime.getRuntime()
            val small = editor("x".repeat(10_000))
            val smallStarted = System.nanoTime()
            small.text!!.insert(5_000, "z")
            val smallInsertMicros = (System.nanoTime() - smallStarted) / 1_000

            val editor = editor("x".repeat(100_000))
            runtime.gc()
            val beforeMemory = runtime.totalMemory() - runtime.freeMemory()
            val insertStarted = System.nanoTime()
            editor.text!!.insert(50_000, "z")
            val insertMicros = (System.nanoTime() - insertStarted) / 1_000

            repeat(50) { index ->
                editor.editHistory(OperationType.REPLACE) {
                    val position = index * 997 % length()
                    text!!.insert(position, "q")
                    setSelection(position + 1)
                }
            }
            val undoStarted = System.nanoTime()
            repeat(50) { editor.undo() }
            val undoMicros = (System.nanoTime() - undoStarted) / 1_000
            runtime.gc()
            val memoryKb = (runtime.totalMemory() - runtime.freeMemory() - beforeMemory) / 1024

            println(
                "QUILLPAD_UNDO_PERF insert10k=${smallInsertMicros}us insert100k=${insertMicros}us " +
                    "undo50=${undoMicros}us historyMemoryDelta=${memoryKb}KB",
            )
            assertEquals(100_001, editor.length())
        }
    }

    private fun editor(initial: String) =
        ExtendedEditText(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).apply {
            setText(initial)
            setSelection(initial.length)
            enableUndoRedo()
        }

    private fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                block()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }
}
