package com.kangle.kardleaf.ui.editor.quillpad

import android.graphics.Bitmap
import android.text.Spannable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kangle.kardleaf.ui.editor.EditorViewportAnchor
import com.kangle.kardleaf.ui.editor.EditorViewportEdge
import com.kangle.kardleaf.ui.editor.native.KardLeafEditorSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KardLeafQuillpadEditorViewTest {
    @Test
    fun inlineImageSpanReflowsLayoutWithoutChangingMarkdown() {
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                val markdown = "![image](1.jpg)\nnext line"
                val syntaxEnd = markdown.indexOf('\n')
                val editor =
                    EditText(ApplicationProvider.getApplicationContext()).apply {
                        layoutParams =
                            ViewGroup.LayoutParams(
                                600,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                        setText(markdown)
                        setSingleLine(false)
                        setLineSpacing(8f, 1.5f)
                    }
                val widthSpec = View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                editor.measure(widthSpec, heightSpec)
                editor.layout(0, 0, editor.measuredWidth, editor.measuredHeight)
                val layoutHeightBefore = editor.layout.height
                val bitmap = Bitmap.createBitmap(80, 100, Bitmap.Config.ARGB_8888)
                val reservedHeight = 116
                val span =
                    QuillpadInlineImageLineHeightSpan(
                        reference = "1.jpg",
                        bitmap = bitmap,
                        widthPx = 80,
                        heightPx = 100,
                        reservedHeightPx = reservedHeight,
                        previewGapPx = 8,
                        lineSpacingMultiplier = 1.5f,
                        syntaxStart = 0,
                        syntaxEnd = syntaxEnd,
                        syntaxLineStart = 0,
                        syntaxLineEnd = syntaxEnd,
                    )
                editor.text.setSpan(span, 0, syntaxEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                editor.measure(widthSpec, heightSpec)
                editor.layout(0, 0, editor.measuredWidth, editor.measuredHeight)

                assertTrue(editor.layout.height >= layoutHeightBefore + reservedHeight - 2)
                assertEquals(markdown, editor.text.toString())
                bitmap.recycle()
            } catch (throwable: Throwable) {
                failure = throwable
            }
        }
        failure?.let { throw it }
    }

    @Test
    fun unchangedConfigureAndBindAreNoOps() =
        onMain { view ->
            view.configureForTest()
            view.configureForTest()
            val snapshot = KardLeafEditorSnapshot("title", "body", TextRange.Zero)
            view.bindDocument("a", "title", "body", snapshot)
            view.bindDocument("a", "title", "body", snapshot)

            val counters = view.debugCounters()
            assertEquals(1, counters.configureApplied)
            assertEquals(0, counters.fullTextSnapshots)
            assertEquals("body", view.getContentString())
        }

    @Test
    fun documentSwitchAlwaysReplacesPreviousBody() =
        onMain { view ->
            view.bindDocument("a", "one", "first", KardLeafEditorSnapshot("one", "first", TextRange.Zero))
            view.bindDocument("b", "two", "second", KardLeafEditorSnapshot("two", "second", TextRange(3)))

            assertEquals("second", view.getContentString())
            assertEquals(TextRange(3), view.getContentSelection())
            assertEquals(0, view.debugCounters().fullTextSnapshots)
        }

    @Test
    fun editWithoutVisibleImeDoesNotScheduleReveal() =
        onMain { view ->
            view.bindDocument("a", "", "body", KardLeafEditorSnapshot("", "body", TextRange(4)))
            view.replaceContentSelection("!")

            assertEquals(0, view.debugCounters().revealScheduled)
        }

    @Test
    fun visibleImeWithoutFocusDoesNotRequestReveal() =
        onMain { view ->
            view.configureForTest()
            val body = List(400) { "long line $it" }.joinToString("\n")
            view.bindDocument("a", "", body, KardLeafEditorSnapshot("", body, TextRange(body.length)))
            view.updateComposeImeBottom(800)
            assertEquals(800, view.getChildAt(0).paddingBottom)
            assertEquals(0, view.debugCounters().revealScheduled)

            view.replaceContentSelection("!")

            assertEquals(0, view.debugCounters().revealScheduled)
            view.updateComposeImeBottom(0)
            assertEquals(0, view.getChildAt(0).paddingBottom)
        }

    @Test
    fun focusedEmptyNoteStaysAtTopWhenImeSpaceIsReserved() =
        onMain { view ->
            view.configureForTest()
            view.bindDocument("new", "", "", KardLeafEditorSnapshot("", "", TextRange.Zero))
            val exactWidth = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val exactHeight = View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY)
            view.measure(exactWidth, exactHeight)
            view.layout(0, 0, 1080, 2000)
            val scrollView = view.getChildAt(0) as ViewGroup
            val content = (scrollView.getChildAt(0) as ViewGroup).getChildAt(1) as EditText

            assertTrue(content.requestFocus())
            view.updateComposeImeBottom(800)
            view.measure(exactWidth, exactHeight)
            view.layout(0, 0, 1080, 2000)

            assertEquals(2000, view.height)
            assertEquals(800, scrollView.paddingBottom)
            assertEquals(0, scrollView.scrollY)
        }

    @Test
    fun focusedLongNoteScrollsCursorAboveReservedImeSpace() =
        onMain { view ->
            view.configureForTest()
            val body = List(80) { "long line $it" }.joinToString("\n")
            view.bindDocument("long", "", body, KardLeafEditorSnapshot("", body, TextRange(body.length)))
            val exactWidth = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val exactHeight = View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY)
            view.measure(exactWidth, exactHeight)
            view.layout(0, 0, 1080, 2000)
            val scrollView = view.getChildAt(0) as ViewGroup
            val content = (scrollView.getChildAt(0) as ViewGroup).getChildAt(1) as EditText

            assertTrue(content.requestFocus())
            view.updateComposeImeBottom(800)

            val cursorLine = content.layout.getLineForOffset(body.length)
            val cursorBottom = content.top + content.totalPaddingTop + content.layout.getLineBottom(cursorLine)
            assertTrue(scrollView.scrollY > 0)
            assertTrue(cursorBottom <= scrollView.scrollY + scrollView.height - 800)
        }

    @Test
    fun selectedLongNoteScrollsActiveEndpointIntoView() =
        onMain { view ->
            view.configureForTest()
            val body = List(120) { "long line $it" }.joinToString("\n")
            val endpoint = body.indexOf("long line 90") + "long line 90".length
            view.bindDocument("long", "", body, KardLeafEditorSnapshot("", body, TextRange.Zero))
            val exactWidth = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val exactHeight = View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)
            view.measure(exactWidth, exactHeight)
            view.layout(0, 0, 1080, 900)
            val scrollView = view.getChildAt(0) as ViewGroup
            val content = (scrollView.getChildAt(0) as ViewGroup).getChildAt(1) as EditText

            assertTrue(content.requestFocus())
            view.setContentSelection(0, endpoint)

            val selectionLine = content.layout.getLineForOffset(endpoint)
            val selectionBottom = content.top + content.totalPaddingTop + content.layout.getLineBottom(selectionLine)
            assertTrue(scrollView.scrollY > 0)
            assertTrue(selectionBottom <= scrollView.scrollY + scrollView.height - 8)
        }

    @Test
    fun initialViewportAnchorIsAppliedBeforeFirstDraw() =
        onMain { view ->
            view.configureForTest()
            val body = List(200) { "long line $it" }.joinToString("\n")
            val offset = body.indexOf("long line 120")
            view.setInitialViewportAnchor(EditorViewportAnchor(offset, 0.5f, EditorViewportEdge.CENTER))
            view.bindDocument("long", "", body, KardLeafEditorSnapshot("", body, TextRange(offset)))
            val exactWidth = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val exactHeight = View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY)
            view.measure(exactWidth, exactHeight)
            view.layout(0, 0, 1080, 2000)
            view.viewTreeObserver.dispatchOnPreDraw()

            val scrollView = view.getChildAt(0) as ViewGroup
            assertTrue(scrollView.scrollY > 0)
        }

    @Test
    fun focusedLongNoteFollowsCursorAfterNewLineLayout() =
        onMain { view ->
            view.configureForTest()
            val body = List(80) { "long line $it" }.joinToString("\n")
            view.bindDocument("long", "", body, KardLeafEditorSnapshot("", body, TextRange(body.length)))
            val exactWidth = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val exactHeight = View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY)
            view.measure(exactWidth, exactHeight)
            view.layout(0, 0, 1080, 2000)
            val scrollView = view.getChildAt(0) as ViewGroup
            val content = (scrollView.getChildAt(0) as ViewGroup).getChildAt(1) as EditText

            assertTrue(content.requestFocus())
            view.updateComposeImeBottom(800)
            val scrollBefore = scrollView.scrollY
            view.replaceContentSelection("\n")
            view.measure(exactWidth, exactHeight)
            view.layout(0, 0, 1080, 2000)

            val cursorLine = content.layout.getLineForOffset(content.selectionEnd)
            val cursorBottom = content.top + content.totalPaddingTop + content.layout.getLineBottom(cursorLine)
            assertTrue(scrollView.scrollY > scrollBefore)
            assertTrue(cursorBottom <= scrollView.scrollY + scrollView.height - 800)
        }

    @Test
    fun undoRestoresTextAndSelection() =
        onMain { view ->
            view.bindDocument("a", "", "body", KardLeafEditorSnapshot("", "body", TextRange(4)))
            view.replaceContentSelection("!")
            view.undoContent()

            assertEquals("body", view.getContentString())
            assertEquals(TextRange(4), view.getContentSelection())
        }

    private fun KardLeafQuillpadEditorView.configureForTest() {
        configure(
            titleHint = "title",
            contentHint = "content",
            textColor = 0xff000000.toInt(),
            hintColor = 0xff888888.toInt(),
            titleTextSizeSp = 22f,
            contentTextSizeSp = 16f,
            contentLineHeightMultiplier = 1.5f,
            contentLetterSpacingSp = 0f,
            contentParagraphSpacingDp = 8f,
            contentFontFamily = "system",
            showTitle = true,
            readOnly = false,
            onTitleChanged = {},
            onContentChanged = {},
            onSelectionChanged = { _, _ -> },
            onUndoRedoChanged = {},
            onUserInteraction = {},
            onFastScrollSourceScrolled = {},
        )
    }

    private fun onMain(block: (KardLeafQuillpadEditorView) -> Unit) {
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                block(KardLeafQuillpadEditorView(ApplicationProvider.getApplicationContext()))
            } catch (throwable: Throwable) {
                failure = throwable
            }
        }
        failure?.let { throw it }
    }
}
