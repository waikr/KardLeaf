package com.kangle.kardleaf.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kangle.kardleaf.data.model.Note
import java.io.File

internal fun shareSelectedNotes(
    context: Context,
    notes: List<Note>,
) {
    if (notes.isEmpty()) return
    val shareDir = File(context.cacheDir, "shared_notes").apply {
        deleteRecursively()
        mkdirs()
    }
    val uris =
        notes.mapIndexed { index, note ->
            val file = File(shareDir, safeShareFileName(note.title.ifBlank { note.file.nameWithoutExtension }, index))
            file.writeText(note.content, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    val sendIntent =
        if (uris.size == 1) {
            Intent(Intent.ACTION_SEND)
                .setType("text/markdown")
                .putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("text/markdown")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(sendIntent, "分享笔记"))
}

private fun safeShareFileName(
    title: String,
    index: Int,
): String {
    val base =
        title
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "note-${index + 1}" }
            .take(80)
    return if (base.endsWith(".md", ignoreCase = true)) base else "$base.md"
}
