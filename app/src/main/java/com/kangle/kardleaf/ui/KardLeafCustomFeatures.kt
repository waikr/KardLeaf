package com.kangle.kardleaf.ui

import android.content.Context
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.*
import com.kangle.kardleaf.data.repository.PrefsManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SETTINGS_TRACE_TAG = "KardLeafSettingsTrace"
private const val DIAGNOSTIC_LOGCAT_MAX_CHARS = 2_000_000
private const val DIAGNOSTIC_LOGCAT_TIMEOUT_SECONDS = 5L

object KardLeafCustomFeatures {
    const val UseDynamicColor = false
    const val DefaultUnnamedNoteDateFormat = "yyyy.MM.dd.HHmmss"
    const val DefaultUnnamedNoteFileNameTemplate = ""
    val DefaultOpenNoteMode = OpenNoteMode.EDIT
    const val DefaultEditDoubleTapPreview = false

    fun editorKernelIcon(kernel: PrefsManager.EditorKernel): ImageVector = when (kernel) {
        PrefsManager.EditorKernel.AUTO -> Icons.Outlined.Settings
        PrefsManager.EditorKernel.NATIVE -> Icons.Outlined.Edit
        PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW -> Icons.Outlined.Code
        PrefsManager.EditorKernel.QUILLPAD_STYLE -> Icons.Outlined.Description
    }

    fun editorKernelTitle(kernel: PrefsManager.EditorKernel): String = when (kernel) {
        PrefsManager.EditorKernel.AUTO -> "原生Beta内核"
        PrefsManager.EditorKernel.NATIVE -> "原生Alpha内核"
        PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW -> "WebView内核"
        PrefsManager.EditorKernel.QUILLPAD_STYLE -> "原生Beta内核"
    }

    fun editorKernelSubtitle(kernel: PrefsManager.EditorKernel): String = when (kernel) {
        PrefsManager.EditorKernel.AUTO -> "自动切换已取消，旧配置按原生编辑器处理"
        PrefsManager.EditorKernel.NATIVE -> "默认使用原生编辑器，不按字数自动切换"
        PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW -> "所有普通笔记都使用 WebView / CodeMirror"
        PrefsManager.EditorKernel.QUILLPAD_STYLE -> "使用 Quillpad 输入与光标体验，文件和笔记管理仍由 KardLeaf 负责"
    }

    private const val PrefsName = "kardleaf_custom_features"
    private const val KeyUnnamedNoteDateFormat = "unnamed_note_date_format"
    private const val KeyUnnamedNoteFileNameTemplate = "unnamed_note_file_name_template"
    private const val KeyOpenNoteMode = "open_note_mode"
    private const val KeyEditDoubleTapPreview = "edit_double_tap_preview"
    private const val KeyToolbarOrder = "toolbar_order"
    private const val MaxExternalNoteTitleChars = 120
    private const val MaxExternalNoteFolderChars = 240
    private const val MaxExternalNoteContentChars = 50_000
    private const val MaxExternalNoteUrlChars = 2_048
    private val unsafeFileNameChars = Regex("[\\\\/:*?\"<>|]")
    private val unsafeFolderSegmentChars = Regex("[:*?\"<>|]")
    private val looseDatePatternChars = setOf('y', 'Y', 'M', 'L', 'd', 'D', 'H', 'h', 'm', 's', 'S')

    enum class OpenNoteMode(val label: String) {
        PREVIEW("Preview"),
        EDIT("Edit"),
    }

    enum class ToolbarItem(val label: String) {
        PREVIEW("预览"),
        UNDO("撤销"),
        REDO("恢复"),
        IMAGE("图片"),
        DRAWING("绘图"),
        HEADING("一级标题"),
        HEADING2("二级标题"),
        HEADING3("三级标题"),
        RULE("分割线"),
        BOLD("加粗"),
        ITALIC("斜体"),
        UNDERLINE("下划线"),
        STRIKE("删除线"),
        LINK("链接"),
        CODE("行内代码"),
        CODE_BLOCK("代码块"),
        QUOTE("引用"),
        MATH("公式"),
        BULLET("无序列表"),
        NUMBERED("有序列表"),
        INDENT("缩进"),
        OUTDENT("反缩进"),
        CHECKBOX("待办"),
        CHECKBOX_DONE("已完成待办"),
        TABLE("表格"),
    }

    val DefaultToolbarOrder =
        listOf(
            ToolbarItem.UNDO,
            ToolbarItem.REDO,
            ToolbarItem.IMAGE,
            ToolbarItem.DRAWING,
            ToolbarItem.HEADING,
            ToolbarItem.HEADING2,
            ToolbarItem.HEADING3,
            ToolbarItem.PREVIEW,
            ToolbarItem.RULE,
            ToolbarItem.BOLD,
            ToolbarItem.ITALIC,
            ToolbarItem.UNDERLINE,
            ToolbarItem.STRIKE,
            ToolbarItem.LINK,
            ToolbarItem.CODE,
            ToolbarItem.CODE_BLOCK,
            ToolbarItem.QUOTE,
            ToolbarItem.MATH,
            ToolbarItem.BULLET,
            ToolbarItem.NUMBERED,
            ToolbarItem.INDENT,
            ToolbarItem.OUTDENT,
            ToolbarItem.CHECKBOX,
            ToolbarItem.CHECKBOX_DONE,
            ToolbarItem.TABLE,
        )

    data class ExternalNoteDraft(
        val title: String = "",
        val content: String = "",
        val folder: String = "",
        val isTemporary: Boolean = false,
        val isPinned: Boolean = false,
        val forceRootFolder: Boolean = false,
        val sourceType: String? = null,
        val sourceUrl: String? = null,
    )

    fun parseExternalCreateNoteUri(uri: Uri?): ExternalNoteDraft? {
        if (uri == null) return null
        if (uri.scheme != "kardleaf" || uri.host != "new") return null

        val title = sanitizeTitle(
            uri.getQueryParameter("title").orEmpty().take(MaxExternalNoteTitleChars),
        ).take(MaxExternalNoteTitleChars)
        val content =
            firstQueryParameter(uri, "content", "body", "text")
                .orEmpty()
                .take(MaxExternalNoteContentChars)
        val url = uri.getQueryParameter("url").orEmpty().take(MaxExternalNoteUrlChars)
        val finalContent =
            when {
                content.isNotBlank() && url.isNotBlank() -> "$content\n\n$url"
                content.isNotBlank() -> content
                else -> url
            }.take(MaxExternalNoteContentChars)
        val folder =
            sanitizeFolderPath(
                firstQueryParameter(uri, "path", "folder", "label").orEmpty().take(MaxExternalNoteFolderChars),
            ).take(MaxExternalNoteFolderChars)
        val isPinned = parseBoolean(uri.getQueryParameter("pinned"))
        val forceRootFolder = parseBoolean(uri.getQueryParameter("root"))

        return ExternalNoteDraft(
            title = title,
            content = finalContent,
            folder = folder,
            isPinned = isPinned,
            forceRootFolder = forceRootFolder,
        )
    }

    private fun firstQueryParameter(
        uri: Uri,
        vararg names: String,
    ): String? = names.firstNotNullOfOrNull { uri.getQueryParameter(it) }

    private fun sanitizeFolderPath(path: String): String {
        return path
            .trim()
            .replace("\\", "/")
            .split("/")
            .map { segment ->
                segment
                    .trim()
                    .replace(unsafeFolderSegmentChars, " - ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            .filter { segment ->
                segment.isNotBlank() &&
                    segment != "." &&
                    segment != ".."
            }
            .joinToString("/")
    }

    private fun sanitizeTitle(title: String): String {
        return title
            .trim()
            .replace(unsafeFileNameChars, "_")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseBoolean(value: String?): Boolean {
        return when (value?.trim()?.lowercase(Locale.ROOT)) {
            "1", "true", "yes", "y", "on" -> true
            else -> false
        }
    }

    fun getUnnamedNoteDateFormat(context: Context): String {
        val configured =
            context
                .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
                .getString(KeyUnnamedNoteDateFormat, DefaultUnnamedNoteDateFormat)
                .orEmpty()
                .trim()

        return configured.takeIf { isDateFormatUsable(it) } ?: DefaultUnnamedNoteDateFormat
    }

    fun saveUnnamedNoteDateFormat(
        context: Context,
        dateFormat: String,
    ): Boolean {
        val normalized = dateFormat.trim()
        if (!isDateFormatUsable(normalized)) return false

        context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyUnnamedNoteDateFormat, normalized)
            .apply()
        return true
    }

    fun getUnnamedNoteFileNameTemplate(context: Context): String {
        return context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .getString(KeyUnnamedNoteFileNameTemplate, DefaultUnnamedNoteFileNameTemplate)
            .orEmpty()
    }

    fun saveUnnamedNoteFileNameTemplate(
        context: Context,
        template: String,
    ): Boolean {
        val normalized = template.trim()
        if (!isAutoFileNameTemplateUsable(normalized, getUnnamedNoteDateFormat(context))) return false

        context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyUnnamedNoteFileNameTemplate, normalized)
            .apply()
        return true
    }

    fun getOpenNoteMode(context: Context): OpenNoteMode {
        val configured =
            context
                .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
                .getString(KeyOpenNoteMode, DefaultOpenNoteMode.name)

        return runCatching {
            OpenNoteMode.valueOf(configured ?: DefaultOpenNoteMode.name)
        }.getOrDefault(DefaultOpenNoteMode)
    }

    fun saveOpenNoteMode(
        context: Context,
        mode: OpenNoteMode,
    ) {
        context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyOpenNoteMode, mode.name)
            .apply()
    }

    fun isEditDoubleTapPreviewEnabled(context: Context): Boolean {
        return context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .getBoolean(KeyEditDoubleTapPreview, DefaultEditDoubleTapPreview)
    }

    fun saveEditDoubleTapPreviewEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KeyEditDoubleTapPreview, enabled)
            .apply()
    }

    fun getToolbarOrder(context: Context): List<ToolbarItem> {
        val configured =
            context
                .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
                .getString(KeyToolbarOrder, null)
                ?.split(",")
                ?.mapNotNull { name -> runCatching { ToolbarItem.valueOf(name) }.getOrNull() }
                .orEmpty()

        return (configured + DefaultToolbarOrder).distinct()
    }

    fun saveToolbarOrder(
        context: Context,
        order: List<ToolbarItem>,
    ) {
        context
            .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyToolbarOrder, order.distinct().joinToString(",") { it.name })
            .apply()
    }

    fun formatUnnamedNoteTitle(
        context: Context,
        date: Date = Date(),
        locale: Locale = Locale.getDefault(),
        existingTitles: Set<String> = emptySet(),
    ): String {
        val dateFormat = getUnnamedNoteDateFormat(context)
        val template = getUnnamedNoteFileNameTemplate(context)
        return runCatching {
            formatAutoFileNameTitle(template, dateFormat, date, locale, existingTitles)
        }.getOrElse {
            formatDateForFileName(DefaultUnnamedNoteDateFormat, date, locale)
        }
    }

    fun previewUnnamedNoteTitle(dateFormat: String): String {
        return runCatching {
            formatDateForFileName(dateFormat.trim(), Date(), Locale.getDefault())
        }.getOrDefault("")
    }

    fun previewUnnamedNoteFileNameTemplate(
        template: String,
        dateFormat: String,
    ): String {
        return runCatching {
            formatAutoFileNameTitle(template, dateFormat, Date(), Locale.getDefault(), emptySet())
        }.getOrDefault("")
    }

    fun isDateFormatUsable(dateFormat: String): Boolean {
        if (dateFormat.isBlank()) return false
        return runCatching {
            SimpleDateFormat(dateFormat, Locale.getDefault()).format(Date())
        }.isSuccess
    }

    fun isAutoFileNameTemplateUsable(
        template: String,
        dateFormat: String,
    ): Boolean {
        val safeDateFormat = dateFormat.trim().takeIf { isDateFormatUsable(it) } ?: DefaultUnnamedNoteDateFormat
        return runCatching {
            formatAutoFileNameTitle(template, safeDateFormat, Date(), Locale.getDefault(), emptySet()).isNotBlank()
        }.getOrDefault(false)
    }

    private fun formatAutoFileNameTitle(
        template: String,
        dateFormat: String,
        date: Date,
        locale: Locale,
        existingTitles: Set<String>,
    ): String {
        val safeDateFormat = dateFormat.trim().takeIf { isDateFormatUsable(it) } ?: DefaultUnnamedNoteDateFormat
        val rawTemplate = template.trim().ifBlank { safeDateFormat }
        val hasCounter = rawTemplate.contains("{1}")
        val existing = existingTitles.map { it.trim() }.filter { it.isNotBlank() }.toSet()

        if (!hasCounter) {
            return sanitizeAutoFileNameTitle(renderAutoFileNameTemplate(rawTemplate, date, locale, 1), date, locale)
        }

        for (counter in 1..9999) {
            val candidate = sanitizeAutoFileNameTitle(
                renderAutoFileNameTemplate(rawTemplate, date, locale, counter),
                date,
                locale,
            )
            if (candidate !in existing) return candidate
        }

        return sanitizeAutoFileNameTitle(
            renderAutoFileNameTemplate(rawTemplate, date, locale, System.currentTimeMillis().rem(100000).toInt()),
            date,
            locale,
        )
    }

    private fun renderAutoFileNameTemplate(
        template: String,
        date: Date,
        locale: Locale,
        counter: Int,
    ): String {
        val withCounter = template.replace("{1}", counter.toString())
        return runCatching {
            SimpleDateFormat(withCounter, locale).format(date)
        }.getOrElse {
            formatLooseDateTemplate(withCounter, date, locale)
        }
    }

    private fun formatLooseDateTemplate(
        template: String,
        date: Date,
        locale: Locale,
    ): String {
        val builder = StringBuilder()
        var index = 0
        while (index < template.length) {
            val ch = template[index]
            if (ch in looseDatePatternChars) {
                var end = index + 1
                while (end < template.length && template[end] == ch) end++
                val token = template.substring(index, end)
                if (token.length >= 2) {
                    builder.append(runCatching { SimpleDateFormat(token, locale).format(date) }.getOrDefault(token))
                } else {
                    builder.append(token)
                }
                index = end
            } else {
                builder.append(ch)
                index++
            }
        }
        return builder.toString()
    }

    private fun sanitizeAutoFileNameTitle(
        title: String,
        date: Date,
        locale: Locale,
    ): String {
        return title
            .replace(unsafeFileNameChars, "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { SimpleDateFormat(DefaultUnnamedNoteDateFormat, locale).format(date) }
    }

    private fun formatDateForFileName(
        dateFormat: String,
        date: Date,
        locale: Locale,
    ): String {
        val formatted = SimpleDateFormat(dateFormat, locale).format(date)
        return formatted
            .replace(unsafeFileNameChars, "_")
            .trim()
            .ifBlank { SimpleDateFormat(DefaultUnnamedNoteDateFormat, locale).format(date) }
    }
}
