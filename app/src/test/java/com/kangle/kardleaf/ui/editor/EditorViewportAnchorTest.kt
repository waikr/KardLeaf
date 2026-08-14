package com.kangle.kardleaf.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorViewportAnchorTest {
    @Test
    fun titlePrefixShiftPreservesViewportEdges() {
        val center = EditorViewportAnchor(2_000, 0.5f, EditorViewportEdge.CENTER)
        assertEquals(2_012, center.shifted(delta = 12, targetLength = 10_012).offset)
        assertEquals(2_000, center.shifted(delta = 12, targetLength = 10_012).shifted(delta = -12, targetLength = 10_000).offset)
        assertEquals(0, center.copy(edge = EditorViewportEdge.START).shifted(12, 10_012).offset)
        assertEquals(10_012, center.copy(edge = EditorViewportEdge.END).shifted(12, 10_012).offset)
    }

    @Test
    fun codeMirrorAnchorUsesNormalizedLineEndingOffsets() {
        val content = "a\r\nb\r\nc"
        assertEquals(5, codeMirrorNormalizedLength(content))
        assertEquals(2, codeMirrorCrLfCount(content))
        assertEquals(3, EditorViewportAnchor(4, 0.5f, EditorViewportEdge.CENTER).toCodeMirrorAnchor(content).offset)
        assertEquals(5, EditorViewportAnchor(0, 0.5f, EditorViewportEdge.END).toCodeMirrorAnchor(content).offset)
    }
}
