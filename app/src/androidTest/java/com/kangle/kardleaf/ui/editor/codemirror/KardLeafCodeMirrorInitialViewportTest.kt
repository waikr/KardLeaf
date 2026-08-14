package com.kangle.kardleaf.ui.editor.codemirror

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kangle.kardleaf.ui.editor.EditorViewportAnchor
import com.kangle.kardleaf.ui.editor.EditorViewportEdge
import com.kangle.kardleaf.ui.editor.native.KardLeafEditorController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class KardLeafCodeMirrorInitialViewportTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstCenterAnchorIsAppliedBeforeEditorBecomesActive() {
        val body = testBody()
        val offset = body.indexOf("line 320")
        val harness = showEditor(body, EditorViewportAnchor(offset, 0.5f, EditorViewportEdge.CENTER))

        composeRule.waitUntil(20_000) { harness.result.get() != null }
        composeRule.waitForIdle()

        assertTrue(harness.result.get().orEmpty().contains("ok:CENTER:$offset:"))
        assertTrue(harness.active.value)
        assertTrue(harness.scrollController.getScrollTop() > 0)
        assertFalse(harness.scrollController.hasFocus())
        assertEquals(body, harness.controller.getCachedSnapshot().content)

        val laterOffset = body.indexOf("line 120")
        val laterResult = AtomicReference<String?>(null)
        composeRule.runOnIdle {
            harness.scrollController.scrollViewportToAnchor(
                EditorViewportAnchor(laterOffset, 0.5f, EditorViewportEdge.CENTER),
                laterResult::set,
            )
        }
        composeRule.waitUntil(10_000) { laterResult.get() != null }
        assertTrue(laterResult.get().orEmpty().contains("ok:CENTER:$laterOffset:"))
        assertTrue(harness.scrollController.getScrollTop() > 0)
    }

    @Test
    fun firstEndAnchorScrollsToEnd() {
        val body = testBody()
        val harness = showEditor(body, EditorViewportAnchor(body.length, 1f, EditorViewportEdge.END))

        composeRule.waitUntil(20_000) { harness.result.get() != null }
        composeRule.waitForIdle()

        assertTrue(harness.result.get().orEmpty().contains("ok:END:${body.length}:"))
        assertTrue(harness.scrollController.getScrollTop() > 0)
        assertFalse(harness.scrollController.hasFocus())
    }

    @Test
    fun firstStartAnchorStaysAtTop() {
        val body = testBody()
        val harness = showEditor(body, EditorViewportAnchor(0, 0f, EditorViewportEdge.START))

        composeRule.waitUntil(20_000) { harness.result.get() != null }
        composeRule.waitForIdle()

        assertTrue(harness.result.get().orEmpty().contains("ok:START:0:0"))
        assertEquals(0, harness.scrollController.getScrollTop())
        assertFalse(harness.scrollController.hasFocus())
    }

    private fun showEditor(
        body: String,
        anchor: EditorViewportAnchor,
    ): Harness {
        val controller = KardLeafEditorController()
        controller.acceptInitialSnapshot("test", "", body, TextRange(anchor.offset))
        val scrollController = CodeMirrorWebViewScrollController()
        val active = mutableStateOf(false)
        val result = AtomicReference<String?>(null)
        composeRule.setContent {
            MaterialTheme {
                KardLeafCodeMirrorEditor(
                    initialTitle = "",
                    initialContent = body,
                    documentKey = "test",
                    controller = controller,
                    scrollController = scrollController,
                    active = active.value,
                    onTitleChanged = {},
                    onContentChanged = {},
                    onUserInteraction = {},
                    titleHint = "",
                    textColor = Color.Black,
                    hintColor = Color.Gray,
                    titleTextSize = 22.sp,
                    contentTextSize = 16.sp,
                    isDark = false,
                    showTitle = false,
                    preferredFocusSelection = TextRange(anchor.offset),
                    initialViewportAnchor = anchor,
                    onInitialViewportAnchorApplied = { _, appliedResult ->
                        result.set(appliedResult)
                        active.value = true
                    },
                    modifier = Modifier,
                )
            }
        }
        return Harness(controller, scrollController, active, result)
    }

    private fun testBody(): String =
        List(500) { index -> "line $index ${"content ".repeat(12)}" }.joinToString("\n")

    private data class Harness(
        val controller: KardLeafEditorController,
        val scrollController: CodeMirrorWebViewScrollController,
        val active: androidx.compose.runtime.MutableState<Boolean>,
        val result: AtomicReference<String?>,
    )
}
