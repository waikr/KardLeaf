package com.kangle.kardleaf.ui.editor.native

import androidx.compose.ui.text.TextRange
import com.kangle.kardleaf.data.utils.KardLeafLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KardLeafEditorControllerTest {
    @Test
    fun externalToolbarInsertKeepsSelectionAcrossRecompositionAndUsesRangeUpdate() {
        KardLeafLog.setUserLoggingEnabled(false)
        val controller = KardLeafEditorController()
        controller.acceptInitialSnapshot("doc", "title", "abcdef", TextRange(0))
        controller.updateExternalSelection(4, 4)
        controller.acceptInitialSnapshot("doc", "title", "abcdef", TextRange(0))

        var fullDocumentUpdate = false
        var rangeUpdate: List<Any>? = null
        controller.setExternalContentUpdater { _, _ -> fullDocumentUpdate = true }
        controller.setExternalRangeReplacer { start, end, replacement, selection ->
            rangeUpdate = listOf(start, end, replacement, selection)
            true
        }

        controller.insertAtCursor("X")

        assertEquals(5, controller.getSelection().start)
        assertEquals(listOf(4, 4, "X", TextRange(5)), rangeUpdate)
        assertFalse(fullDocumentUpdate)
    }

    @Test
    fun externalQuickTextReplacementReplacesSelectedText() {
        KardLeafLog.setUserLoggingEnabled(false)
        val controller = KardLeafEditorController()
        controller.acceptInitialSnapshot("doc", "title", "abcdef", TextRange(2, 4))

        var rangeUpdate: List<Any>? = null
        controller.setExternalRangeReplacer { start, end, replacement, selection ->
            rangeUpdate = listOf(start, end, replacement, selection)
            true
        }

        controller.replaceSelection("X")

        assertEquals("abXef", controller.getText())
        assertEquals(TextRange(3), controller.getSelection())
        assertEquals(listOf(2, 4, "X", TextRange(3)), rangeUpdate)
    }
}
