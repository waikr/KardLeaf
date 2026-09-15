package com.kangle.kardleaf.ui.editor.codemirror

import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.os.SystemClock
import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kangle.kardleaf.ui.editor.EditorViewportAnchor
import com.kangle.kardleaf.ui.editor.EditorViewportEdge
import com.kangle.kardleaf.ui.editor.native.KardLeafEditorController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class KardLeafCodeMirrorInitialViewportTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun kernelHandoffCompletesOnceAndEnablesTouchAfterVisualConfirmation() {
        val harness = showEditor(
            testBody(), null, livePreview = true, startsInteractive = false,
            startsOpening = true, title = "切换标题",
        )
        composeRule.waitUntil(20_000) {
            evaluate("typeof window.KardLeafEditor?.prepareInitialRender === 'function'") == true
        }
        // A background target must not expose its fixed Compose title through the source editor.
        composeRule.onNodeWithText("切换标题").assertDoesNotExist()
        composeRule.runOnIdle {
            assertFalse(findWebView().isEnabled)
            assertEquals(0f, findWebView().alpha, 0f)
            assertEquals(0, harness.readyCount.get())
            harness.opening.value = false
        }
        composeRule.waitUntil(20_000) { harness.readyCount.get() == 1 }
        composeRule.onNodeWithText("切换标题").assertDoesNotExist()
        assertEquals("切换标题", evaluate("document.querySelector('.kl-editor-title-input').value"))
        composeRule.runOnIdle {
            assertTrue(findWebView().isEnabled)
            assertEquals(1f, findWebView().alpha, 0f)
        }
        // Deliver an actual gesture to the WebView, not a JS scroll command.
        swipeWebView()
        composeRule.waitUntil(10_000) { harness.scrollController.getScrollTop() > 0 }
        assertEquals(true, evaluate("document.querySelector('.kl-editor-title-header').getBoundingClientRect().top < 0"))
        composeRule.runOnIdle { harness.active.value = false }
        composeRule.runOnIdle { assertFalse(findWebView().isEnabled) }
        composeRule.runOnIdle { harness.active.value = true }
        composeRule.runOnIdle { assertTrue(findWebView().isEnabled) }
        assertEquals(1, harness.readyCount.get())
        assertEquals(0, harness.edits.get())
    }

    @Test
    fun openingCachePreloadsButOnlyCompleteBodyBecomesInteractive() {
        val harness = showEditor(
            "# 摘要", null, livePreview = true, startsActive = true, startsOpening = true, title = "首帧标题",
        )
        composeRule.waitUntil(20_000) {
            evaluate("typeof window.KardLeafEditor?.prepareInitialRender === 'function'") == true
        }
        // Direct open also keeps the title inside the hidden WebView until its first complete frame.
        composeRule.onNodeWithText("首帧标题").assertDoesNotExist()
        composeRule.runOnIdle {
            assertFalse(findWebView().isEnabled)
            assertEquals(0f, findWebView().alpha, 0f)
            assertEquals(0, harness.readyCount.get())
            harness.content.value = "# 完整正文\n\n" + testBody()
            harness.opening.value = false
        }
        composeRule.waitUntil(20_000) { harness.readyCount.get() == 1 }
        composeRule.onNodeWithText("首帧标题").assertDoesNotExist()
        assertEquals("首帧标题", evaluate("document.querySelector('.kl-editor-title-input').value"))
        assertEquals(harness.content.value, evaluate("window.KardLeafEditor.getText()"))
        composeRule.runOnIdle { assertTrue(findWebView().isEnabled) }
        assertEquals(0, harness.edits.get())
    }

    private fun swipeWebView() {
        val downAt = SystemClock.uptimeMillis()
        for (step in 0..12) {
            composeRule.runOnIdle {
                val view = findWebView()
                val action = when (step) {
                    0 -> MotionEvent.ACTION_DOWN
                    12 -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }
                val event = MotionEvent.obtain(
                    downAt, downAt + step * 16L, action,
                    view.width * 0.5f, view.height * (0.8f - step * 0.05f), 0,
                )
                try { view.dispatchTouchEvent(event) } finally { event.recycle() }
            }
        }
    }

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

    @Test
    fun firstLivePreviewIsRenderedWithoutChangingBodyOrHistory() {
        val body = "# 一级标题\r\n\r\n开头 😀\r\n\r\n## 标题\r\n\r\n**加粗** 和 ${'$'}x^2${'$'}\r\n\r\n" +
            "${'$'}${'$'}\r\nx^2 + y^2\r\n${'$'}${'$'}\r\n\r\n```mermaid\r\ngraph LR; A-->B\r\n```\r\n\r\n" +
            "| 左 | 右 |\r\n| --- | --- |\r\n| 一 | 二 |\r\n"
        val normalized = body.replace("\r\n", "\n")
        val harness = showEditor(body, EditorViewportAnchor(0, 0f, EditorViewportEdge.START), livePreview = true, startsActive = true)
        composeRule.onNodeWithText(body).assertDoesNotExist()
        composeRule.waitUntil(20_000) { harness.result.get() != null }
        assertEquals("ok:START:0:0", harness.result.get())
        assertFalse(harness.scrollController.hasFocus())
        assertEquals(normalized, evaluate("window.KardLeafEditor.getText()"))
        assertEquals(false, evaluate("document.querySelector('.cm-content')?.innerText?.includes('# 一级标题') === true"))
        assertEquals("empty", evaluate("window.KardLeafEditor.undo()"))
        composeRule.waitUntil(10_000) {
            evaluate("!!document.querySelector('.cm-math-inline .katex') && !!document.querySelector('.cm-math-block .katex') && !!document.querySelector('.cm-mermaid-block svg')") == true
        }
        assertEquals(0, harness.edits.get())

        assertEquals("ok", evaluate("window.KardLeafEditor.selectRange(0, 0)"))
        assertEquals(true, evaluate("document.querySelector('.cm-content')?.innerText?.includes('# 一级标题') === true"))

        evaluate("window.KardLeafEditor.replaceRangeFromAndroid(0, 0, '输入', 2, 2)")
        assertEquals("输入$normalized", evaluate("window.KardLeafEditor.getText()"))
        assertEquals("ok", evaluate("window.KardLeafEditor.undo()"))
        assertEquals(normalized, evaluate("window.KardLeafEditor.getText()"))
        assertEquals("ok", evaluate("window.KardLeafEditor.redo()"))
        assertEquals("输入$normalized", evaluate("window.KardLeafEditor.getText()"))
        evaluate("window.KardLeafEditor.execCommand('setSearchState', '标题', false, false, false, 'test')")
        assertEquals(true, evaluate("!!document.querySelector('.cm-searchMatch')"))
        val snapshot = AtomicReference<String?>(null)
        composeRule.runOnIdle { harness.controller.requestExternalSnapshot { snapshot.set(it.content) } }
        composeRule.waitUntil(10_000) { snapshot.get() != null }
        assertEquals("输入$normalized", snapshot.get())
    }

    @Test
    fun emptyLivePreviewBecomesReady() {
        val harness = showEditor("", EditorViewportAnchor(0, 0f, EditorViewportEdge.START), livePreview = true)
        composeRule.waitUntil(20_000) { harness.result.get() != null }
        assertEquals("", evaluate("window.KardLeafEditor.getText()"))
        assertEquals("empty", evaluate("window.KardLeafEditor.undo()"))
        assertFalse(harness.scrollController.hasFocus())
    }

    @Test
    fun longLivePreviewParsesBeforeRevealingTheEnd() {
        val body = "开头\n\n" + "正文 **强调** 内容\n\n".repeat(15_000) +
            "${'$'}${'$'}\nx^2 + y^2\n${'$'}${'$'}\n\n| 左 | 右 |\n| --- | --- |\n| 一 | 二 |\n"
        val harness = showEditor(body, EditorViewportAnchor(body.length, 1f, EditorViewportEdge.END), livePreview = true)
        composeRule.waitUntil(30_000) { harness.result.get() != null }
        assertTrue(harness.scrollController.getScrollTop() > 0)
        assertEquals(body, evaluate("window.KardLeafEditor.getText()"))
        composeRule.waitUntil(10_000) {
            evaluate("!!document.querySelector('.cm-math-block .katex') && !!document.querySelector('.cm-table-widget table')") == true
        }
        assertEquals("empty", evaluate("window.KardLeafEditor.undo()"))
        assertEquals(0, harness.edits.get())
    }

    @Test
    fun pendingSearchJumpWinsOverInitialViewportAnchor() {
        val body = testBody()
        val offset = body.indexOf("line 320")
        val harness = showEditor(body, EditorViewportAnchor(0, 0f, EditorViewportEdge.START), livePreview = true)
        composeRule.runOnIdle {
            harness.controller.executeCommand("setSearchState", "line 320", false, false, 0, 1)
            harness.controller.executeCommand("selectRange", offset, offset + 8)
        }
        composeRule.waitUntil(20_000) { harness.result.get() != null }
        assertTrue(harness.scrollController.getScrollTop() > 0)
        assertEquals(TextRange(offset, offset + 8), harness.controller.getSelection())
        assertEquals(true, evaluate("!!document.querySelector('.cm-searchMatch')"))
    }

    private fun findWebView(): WebView {
        fun find(view: View): WebView? = when (view) {
            is WebView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
            else -> null
        }
        val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
        return requireNotNull(find(activity.window.decorView))
    }

    private fun evaluate(script: String): Any? {
        val result = AtomicReference<String?>(null)
        composeRule.runOnIdle { findWebView().evaluateJavascript(script, result::set) }
        composeRule.waitUntil(10_000) { result.get() != null }
        return JSONTokener(result.get()).nextValue()
    }

    private fun showEditor(
        body: String,
        anchor: EditorViewportAnchor?,
        livePreview: Boolean = false,
        startsActive: Boolean = false,
        startsInteractive: Boolean = true,
        startsOpening: Boolean = false,
        title: String = "",
    ): Harness {
        val controller = KardLeafEditorController()
        val content = mutableStateOf(body)
        val opening = mutableStateOf(startsOpening)
        val interactive = mutableStateOf(startsInteractive)
        val readyCount = java.util.concurrent.atomic.AtomicInteger(0)
        val scrollController = CodeMirrorWebViewScrollController()
        val active = mutableStateOf(startsActive)
        val result = AtomicReference<String?>(null)
        val edits = java.util.concurrent.atomic.AtomicInteger(0)
        composeRule.setContent {
            controller.acceptInitialSnapshot("test", title, content.value, TextRange(anchor?.offset ?: 0))
            MaterialTheme {
                KardLeafCodeMirrorEditor(
                    initialTitle = title,
                    initialContent = content.value,
                    documentKey = "test",
                    controller = controller,
                    scrollController = scrollController,
                    active = active.value,
                    interactive = interactive.value,
                    openingCacheVisible = opening.value,
                    livePreviewEnabled = livePreview,
                    onTitleChanged = {},
                    onContentChanged = { edits.incrementAndGet() },
                    onUserInteraction = {},
                    titleHint = "",
                    textColor = Color.Black,
                    hintColor = Color.Gray,
                    titleTextSize = 22.sp,
                    contentTextSize = 16.sp,
                    isDark = false,
                    showTitle = title.isNotEmpty(),
                    preferredFocusSelection = TextRange(anchor?.offset ?: 0),
                    initialViewportAnchor = anchor,
                    onInitialViewportAnchorApplied = { _, appliedResult ->
                        result.set(appliedResult)
                        active.value = true
                    },
                    onInitialSurfaceReady = {
                        readyCount.incrementAndGet()
                        active.value = true
                        interactive.value = true
                    },
                    modifier = Modifier,
                )
            }
        }
        return Harness(controller, scrollController, active, result, edits, readyCount, content, opening)
    }

    private fun testBody(): String =
        List(500) { index -> "line $index ${"content ".repeat(12)}" }.joinToString("\n")

    private data class Harness(
        val controller: KardLeafEditorController,
        val scrollController: CodeMirrorWebViewScrollController,
        val active: androidx.compose.runtime.MutableState<Boolean>,
        val result: AtomicReference<String?>,
        val edits: java.util.concurrent.atomic.AtomicInteger,
        val readyCount: java.util.concurrent.atomic.AtomicInteger,
        val content: androidx.compose.runtime.MutableState<String>,
        val opening: androidx.compose.runtime.MutableState<Boolean>,
    )
}
