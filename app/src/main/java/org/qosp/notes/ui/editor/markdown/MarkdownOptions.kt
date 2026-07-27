package org.qosp.notes.ui.editor.markdown

import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import io.noties.markwon.Markwon
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Node

class MarkdownOptions {
    var maximumTableColumns: Int = 100
    var tableReplacement: () -> Node = { Code("...") }
}

inline fun Markwon.applyTo(textView: TextView, content: String, withOptions: MarkdownOptions.() -> Unit = {}) {
    val totalStart = SystemClock.uptimeMillis()
    val options = MarkdownOptions()
    withOptions(options)

    val parseStart = SystemClock.uptimeMillis()
    val node = parse(content)
    val parseEnd = SystemClock.uptimeMillis()
    Log.d(
        "KardLeafQuillpadPerf",
        "markwon phase=parse contentLen=${content.length} elapsed=${parseEnd - parseStart}ms",
    )

    val visitStart = SystemClock.uptimeMillis()
    val visitor = OptionsVisitor(options)
    node.accept(visitor)
    val visitEnd = SystemClock.uptimeMillis()
    Log.d(
        "KardLeafQuillpadPerf",
        "markwon phase=options contentLen=${content.length} elapsed=${visitEnd - visitStart}ms",
    )

    val renderStart = SystemClock.uptimeMillis()
    val rendered = render(node)
    val renderEnd = SystemClock.uptimeMillis()
    Log.d(
        "KardLeafQuillpadPerf",
        "markwon phase=render contentLen=${content.length} renderedLen=${rendered.length} " +
            "elapsed=${renderEnd - renderStart}ms",
    )

    val setStart = SystemClock.uptimeMillis()
    setParsedMarkdown(textView, rendered)
    val setEnd = SystemClock.uptimeMillis()
    Log.d(
        "KardLeafQuillpadPerf",
        "markwon phase=setParsedMarkdown contentLen=${content.length} renderedLen=${rendered.length} " +
            "elapsed=${setEnd - setStart}ms total=${setEnd - totalStart}ms " +
            "layoutReady=${textView.layout != null} lineCount=${textView.layout?.lineCount ?: -1}",
    )
}

class OptionsVisitor(private val options: MarkdownOptions) : AbstractVisitor() {
    override fun visit(customBlock: CustomBlock?) {
        if (customBlock is TableBlock) {
            val visitor = TableRowVisitor()
            customBlock.firstChild?.firstChild?.accept(visitor)

            if (visitor.cellCount > options.maximumTableColumns) {
                val replacement = options.tableReplacement()
                customBlock.insertAfter(replacement)
                customBlock.unlink()
            }
        } else {
            visitChildren(customBlock)
        }
    }

    private class TableRowVisitor : AbstractVisitor() {
        var cellCount = 0

        override fun visit(customNode: CustomNode?) {
            when (customNode) {
                is TableRow -> visitChildren(customNode)
                is TableCell -> cellCount++
            }
        }
    }
}
