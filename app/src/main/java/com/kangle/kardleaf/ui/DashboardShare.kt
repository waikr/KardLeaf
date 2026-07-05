package com.kangle.kardleaf.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.utils.KardLeafContentLimits
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal enum class ShareSelectedNotesMode {
    TEXT_FILE,
    TEXT_IMAGE,
    WORD,
}

internal fun shareSelectedNotes(
    context: Context,
    notes: List<Note>,
    mode: ShareSelectedNotesMode = ShareSelectedNotesMode.TEXT_FILE,
) {
    if (notes.isEmpty()) return
    val blockMessage = when (mode) {
        ShareSelectedNotesMode.TEXT_FILE -> null
        ShareSelectedNotesMode.TEXT_IMAGE -> imageExportBlockMessage(notes)
        ShareSelectedNotesMode.WORD -> wordExportBlockMessage(notes)
    }
    if (blockMessage != null) {
        Toast.makeText(context, blockMessage, Toast.LENGTH_LONG).show()
        return
    }
    val shareDir = File(context.cacheDir, "shared_notes").apply {
        deleteRecursively()
        mkdirs()
    }
    val uris =
        notes.mapIndexed { index, note ->
            val file = when (mode) {
                ShareSelectedNotesMode.TEXT_FILE -> createTextShareFile(shareDir, note, index)
                ShareSelectedNotesMode.TEXT_IMAGE -> createImageShareFile(shareDir, note, index)
                ShareSelectedNotesMode.WORD -> createWordShareFile(shareDir, note, index)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    val sendIntent =
        if (uris.size == 1) {
            Intent(Intent.ACTION_SEND)
                .setType(mode.mimeType)
                .putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType(mode.mimeType)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(sendIntent, "分享笔记"))
}

private val ShareSelectedNotesMode.mimeType: String
    get() = when (this) {
        ShareSelectedNotesMode.TEXT_FILE -> "text/plain"
        ShareSelectedNotesMode.TEXT_IMAGE -> "image/png"
        ShareSelectedNotesMode.WORD -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }

internal fun imageExportBlockMessage(notes: List<Note>): String? {
    if (notes.size > KardLeafContentLimits.EXPORT_IMAGE_MAX_NOTE_COUNT) {
        return "一次导出图片数量过多（${notes.size} 篇），请减少选择后再试。"
    }
    val tooLarge = notes.firstOrNull { it.content.length > KardLeafContentLimits.EXPORT_IMAGE_MAX_CONTENT_CHARS }
    if (tooLarge != null) {
        return "内容过大，不建议导出为图片。\n“${tooLarge.shareTitle(0)}”约 ${formatCharCount(tooLarge.content.length.toLong())}，图片导出建议不超过 ${formatCharCount(KardLeafContentLimits.EXPORT_IMAGE_MAX_CONTENT_CHARS.toLong())}。请改用文本或 Word 导出。"
    }
    return null
}

internal fun wordExportBlockMessage(notes: List<Note>): String? {
    val tooLarge = notes.firstOrNull { it.content.length > KardLeafContentLimits.WORD_EXPORT_MAX_CONTENT_CHARS }
    if (tooLarge != null) {
        return "当前笔记过大，无法保证完整导出为 Word。\n“${tooLarge.shareTitle(0)}”约 ${formatCharCount(tooLarge.content.length.toLong())}，请拆分后再导出。"
    }
    val totalChars = notes.sumOf { it.content.length.toLong() }
    if (totalChars > KardLeafContentLimits.WORD_EXPORT_MAX_TOTAL_CHARS) {
        return "本次选择内容过大，无法保证完整导出为 Word。\n合计约 ${formatCharCount(totalChars)}，请减少选择后再试。"
    }
    return null
}

private fun createTextShareFile(
    shareDir: File,
    note: Note,
    index: Int,
): File {
    val file = File(shareDir, safeShareFileName(note.shareTitle(index), index, "txt"))
    file.writeText(note.content, Charsets.UTF_8)
    return file
}

private fun createImageShareFile(
    shareDir: File,
    note: Note,
    index: Int,
): File {
    val file = File(shareDir, safeShareFileName(note.shareTitle(index), index, "png"))
    val bitmap = renderNoteTextImage(note)
    file.outputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    bitmap.recycle()
    return file
}

private fun createWordShareFile(
    shareDir: File,
    note: Note,
    index: Int,
): File {
    val file = File(shareDir, safeShareFileName(note.shareTitle(index), index, "docx"))
    ZipOutputStream(file.outputStream()).use { zip ->
        zip.putTextEntry(
            "[Content_Types].xml",
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
            """.trimIndent(),
        )
        zip.putTextEntry(
            "_rels/.rels",
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
            """.trimIndent(),
        )
        zip.putTextEntry("word/document.xml", buildWordDocumentXml(note))
    }
    return file
}

private fun ZipOutputStream.putTextEntry(name: String, text: String) {
    putNextEntry(ZipEntry(name))
    write(text.toByteArray(Charsets.UTF_8))
    closeEntry()
}

private fun renderNoteTextImage(note: Note): Bitmap {
    val width = KardLeafContentLimits.EXPORT_IMAGE_WIDTH_PX
    val horizontalPadding = 72f
    val verticalPadding = 64f
    val bodyMaxLines = KardLeafContentLimits.EXPORT_IMAGE_MAX_BODY_LINES
    val title = note.shareTitle(0)
    val body = note.content.ifBlank { "（空白笔记）" }
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 43, 39)
        textSize = 46f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(43, 52, 49)
        textSize = 34f
    }
    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(110, 121, 116)
        textSize = 28f
    }
    val textWidth = (width - horizontalPadding * 2).toInt()
    val titleLayout = StaticLayout.Builder
        .obtain(title, 0, title.length, titlePaint, textWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .build()
    val bodyText = body.take(KardLeafContentLimits.EXPORT_IMAGE_MAX_CONTENT_CHARS)
    val bodyLayout = StaticLayout.Builder
        .obtain(bodyText, 0, bodyText.length, bodyPaint, textWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(6f, 1f)
        .setIncludePad(false)
        .setMaxLines(bodyMaxLines)
        .build()
    val truncated = body.length > bodyText.length || bodyLayout.lineCount >= bodyMaxLines
    val footer = if (truncated) "内容过长，图片已截断" else "KardLeaf"
    val footerHeight = 56f
    val height = (verticalPadding * 2 + titleLayout.height + 32f + bodyLayout.height + footerHeight)
        .toInt()
        .coerceIn(KardLeafContentLimits.EXPORT_IMAGE_MIN_HEIGHT_PX, KardLeafContentLimits.EXPORT_IMAGE_MAX_HEIGHT_PX)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.rgb(250, 252, 249))
    canvas.translate(horizontalPadding, verticalPadding)
    titleLayout.draw(canvas)
    canvas.translate(0f, titleLayout.height + 32f)
    bodyLayout.draw(canvas)
    canvas.translate(0f, bodyLayout.height + 28f)
    canvas.drawText(footer, 0f, 0f, footerPaint)
    return bitmap
}

private fun buildWordDocumentXml(note: Note): String {
    val title = note.shareTitle(0)
    val paragraphs = buildList {
        add(title)
        add("")
        note.content.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            if (line.length <= KardLeafContentLimits.WORD_PARAGRAPH_CHUNK_SIZE) {
                add(line)
            } else {
                addAll(line.chunked(KardLeafContentLimits.WORD_PARAGRAPH_CHUNK_SIZE))
            }
        }
    }.joinToString("\n") { line ->
        "<w:p><w:r><w:t xml:space=\"preserve\">${line.cleanXmlText()}</w:t></w:r></w:p>"
    }
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:body>
            $paragraphs
            <w:sectPr>
              <w:pgSz w:w="11906" w:h="16838"/>
              <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/>
            </w:sectPr>
          </w:body>
        </w:document>
    """.trimIndent()
}

private fun Note.shareTitle(index: Int): String =
    title.ifBlank { file.nameWithoutExtension }.ifBlank { "note-${index + 1}" }

private fun String.cleanXmlText(): String =
    asSequence()
        .filter { ch -> ch == '\n' || ch == '\t' || ch.code >= 0x20 }
        .joinToString("")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

private fun formatCharCount(count: Long): String =
    if (count >= 10_000) {
        "${count / 10_000}.${(count % 10_000) / 1_000} 万字"
    } else {
        "$count 字"
    }

private fun safeShareFileName(
    title: String,
    index: Int,
    extension: String,
): String {
    val normalized =
        title
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "note-${index + 1}" }
    val base = normalized
        .substringBeforeLast('.', normalized)
        .take(80)
        .ifBlank { "note-${index + 1}" }
    return "$base.$extension"
}
