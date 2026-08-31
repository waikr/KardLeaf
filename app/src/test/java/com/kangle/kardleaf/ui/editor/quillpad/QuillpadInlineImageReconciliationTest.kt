package com.kangle.kardleaf.ui.editor.quillpad

import org.junit.Assert.assertEquals
import org.junit.Test

class QuillpadInlineImageReconciliationTest {
    @Test
    fun onlyChangedImagesAreLoadedOrRemoved() {
        val first = QuillpadInlineImageIdentity(10, 20, "attachments/first.jpg")
        val second = QuillpadInlineImageIdentity(30, 40, "attachments/second.jpg")
        val replacement = QuillpadInlineImageIdentity(30, 45, "attachments/replacement.jpg")

        assertEquals(
            QuillpadInlineImageReconciliation(missing = listOf(second), obsolete = emptyList()),
            reconcileQuillpadInlineImages(requested = listOf(first, second), applied = listOf(first)),
        )
        assertEquals(
            QuillpadInlineImageReconciliation(missing = listOf(replacement), obsolete = listOf(second)),
            reconcileQuillpadInlineImages(requested = listOf(first, replacement), applied = listOf(first, second)),
        )
        assertEquals(
            QuillpadInlineImageReconciliation(missing = emptyList(), obsolete = listOf(second)),
            reconcileQuillpadInlineImages(requested = listOf(first), applied = listOf(first, second)),
        )
    }
}
