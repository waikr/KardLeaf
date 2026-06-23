package com.kangle.kardleaf.ui

import android.text.Editable
import android.text.Spanned
import android.text.style.StrikethroughSpan
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.BlockQuoteSpan
import io.noties.markwon.core.spans.CodeBlockSpan
import io.noties.markwon.core.spans.CodeSpan
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.editor.AbstractEditHandler
import io.noties.markwon.editor.EditHandler
import io.noties.markwon.editor.MarkwonEditorUtils
import io.noties.markwon.editor.PersistedSpans

class KardLeafCodeHandler : EditHandler<CodeSpan> {
    private lateinit var theme: MarkwonTheme

    override fun init(markwon: Markwon) {
        theme = markwon.configuration().theme()
    }

    override fun configurePersistedSpans(builder: PersistedSpans.Builder) {
        builder.persistSpan(CodeSpan::class.java) { CodeSpan(theme) }
    }

    override fun handleMarkdownSpan(
        persistedSpans: PersistedSpans,
        editable: Editable,
        input: String,
        span: CodeSpan,
        spanStart: Int,
        spanTextLength: Int,
    ) {
        val match = MarkwonEditorUtils.findDelimited(input, spanStart, "`")
        if (match != null) {
            editable.setSpan(
                persistedSpans.get(markdownSpanType()),
                match.start(),
                match.end(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    override fun markdownSpanType(): Class<CodeSpan> = CodeSpan::class.java
}

class KardLeafCodeBlockHandler : EditHandler<CodeBlockSpan> {
    private lateinit var theme: MarkwonTheme

    override fun init(markwon: Markwon) {
        theme = markwon.configuration().theme()
    }

    override fun configurePersistedSpans(builder: PersistedSpans.Builder) {
        builder.persistSpan(CodeBlockSpan::class.java) { CodeBlockSpan(theme) }
    }

    override fun handleMarkdownSpan(
        persistedSpans: PersistedSpans,
        editable: Editable,
        input: String,
        span: CodeBlockSpan,
        spanStart: Int,
        spanTextLength: Int,
    ) {
        val match = MarkwonEditorUtils.findDelimited(input, spanStart, "```")
        if (match != null) {
            editable.setSpan(
                persistedSpans.get(markdownSpanType()),
                match.start(),
                match.end(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    override fun markdownSpanType(): Class<CodeBlockSpan> = CodeBlockSpan::class.java
}

class KardLeafBlockQuoteHandler : EditHandler<BlockQuoteSpan> {
    private lateinit var theme: MarkwonTheme

    override fun init(markwon: Markwon) {
        theme = markwon.configuration().theme()
    }

    override fun configurePersistedSpans(builder: PersistedSpans.Builder) {
        builder.persistSpan(BlockQuoteSpan::class.java) { BlockQuoteSpan(theme) }
    }

    override fun handleMarkdownSpan(
        persistedSpans: PersistedSpans,
        editable: Editable,
        input: String,
        span: BlockQuoteSpan,
        spanStart: Int,
        spanTextLength: Int,
    ) {
        editable.setSpan(
            persistedSpans.get(markdownSpanType()),
            spanStart,
            spanStart + spanTextLength,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    override fun markdownSpanType(): Class<BlockQuoteSpan> = BlockQuoteSpan::class.java
}

class KardLeafHeadingHandler : EditHandler<HeadingSpan> {
    private lateinit var theme: MarkwonTheme

    override fun init(markwon: Markwon) {
        theme = markwon.configuration().theme()
    }

    override fun configurePersistedSpans(builder: PersistedSpans.Builder) {
        builder
            .persistSpan(Head1::class.java) { Head1(theme) }
            .persistSpan(Head2::class.java) { Head2(theme) }
            .persistSpan(Head3::class.java) { Head3(theme) }
            .persistSpan(Head4::class.java) { Head4(theme) }
            .persistSpan(Head5::class.java) { Head5(theme) }
            .persistSpan(Head6::class.java) { Head6(theme) }
    }

    override fun handleMarkdownSpan(
        persistedSpans: PersistedSpans,
        editable: Editable,
        input: String,
        span: HeadingSpan,
        spanStart: Int,
        spanTextLength: Int,
    ) {
        val type: Class<*>? = when (span.level) {
            1 -> Head1::class.java
            2 -> Head2::class.java
            3 -> Head3::class.java
            4 -> Head4::class.java
            5 -> Head5::class.java
            6 -> Head6::class.java
            else -> null
        }

        if (type != null) {
            val index = input.indexOf('\n', spanStart + spanTextLength)
            val end = if (index < 0) input.length else index
            editable.setSpan(
                persistedSpans[type],
                spanStart,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    override fun markdownSpanType(): Class<HeadingSpan> = HeadingSpan::class.java

    private class Head1(theme: MarkwonTheme) : HeadingSpan(theme, 1)
    private class Head2(theme: MarkwonTheme) : HeadingSpan(theme, 2)
    private class Head3(theme: MarkwonTheme) : HeadingSpan(theme, 3)
    private class Head4(theme: MarkwonTheme) : HeadingSpan(theme, 4)
    private class Head5(theme: MarkwonTheme) : HeadingSpan(theme, 5)
    private class Head6(theme: MarkwonTheme) : HeadingSpan(theme, 6)
}

class KardLeafStrikethroughHandler : AbstractEditHandler<StrikethroughSpan>() {
    override fun configurePersistedSpans(builder: PersistedSpans.Builder) {
        builder.persistSpan(markdownSpanType()) { StrikethroughSpan() }
    }

    override fun handleMarkdownSpan(
        persistedSpans: PersistedSpans,
        editable: Editable,
        input: String,
        span: StrikethroughSpan,
        spanStart: Int,
        spanTextLength: Int,
    ) {
        val match = MarkwonEditorUtils.findDelimited(input, spanStart, "~~")
        if (match != null) {
            editable.setSpan(
                persistedSpans.get(markdownSpanType()),
                match.start(),
                match.end(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    override fun markdownSpanType(): Class<StrikethroughSpan> = StrikethroughSpan::class.java
}
