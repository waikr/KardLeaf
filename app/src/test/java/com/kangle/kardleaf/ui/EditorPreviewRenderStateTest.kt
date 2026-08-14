package com.kangle.kardleaf.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPreviewRenderStateTest {
    @Test
    fun preparedImagePreviewUsesVisibleRenderedSignature() {
        val raw = "![local](images/photo.png)"
        val prepared = "$raw\n\n[photo]: data:image/png;base64,encoded"
        val request = Triple(raw.length, raw.hashCode(), "notes")
        val visible = prepared.length to prepared.hashCode()

        assertTrue(
            isPreviewRenderReadyForRequest(
                requestSignature = request,
                lastRequestedSignature = request,
                visibleSignature = visible,
                lastRenderedSignature = visible,
            ),
        )
        assertFalse(
            isPreviewRenderReadyForRequest(
                requestSignature = request,
                lastRequestedSignature = request,
                visibleSignature = visible,
                lastRenderedSignature = raw.length to raw.hashCode(),
            ),
        )
        assertFalse(
            isPreviewRenderReadyForRequest(
                requestSignature = request.copy(first = request.first + 1),
                lastRequestedSignature = request,
                visibleSignature = visible,
                lastRenderedSignature = visible,
            ),
        )
    }
}
