package com.kangle.kardleaf.widget

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DailyNoteSupport {
    private const val FILE_DATE_FORMAT = "yyyy-MM-dd"

    fun todayTitle(): String = titleFor(Date())

    fun titleFor(date: Date): String =
        SimpleDateFormat(FILE_DATE_FORMAT, Locale.US).format(date)

    fun normalizeFolder(folder: String): String =
        folder
            .trim()
            .replace("\\", "/")
            .split("/")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")

    fun filePath(folder: String, title: String): String {
        val normalizedFolder = normalizeFolder(folder)
        return if (normalizedFolder.isBlank()) "$title.md" else "$normalizedFolder/$title.md"
    }
}
