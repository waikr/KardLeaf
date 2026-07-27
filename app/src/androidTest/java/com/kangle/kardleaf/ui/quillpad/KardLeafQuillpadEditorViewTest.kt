package com.kangle.kardleaf.ui.editor.quillpad

import android.graphics.Bitmap
import android.text.Spannable
import android.view.View
import android.widget.EditText
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
            assertEquals(0, view.debugCounters().revealScheduled)

            view.replaceContentSelection("!")

            assertEquals(0, view.debugCounters().revealScheduled)
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
