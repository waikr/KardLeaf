package com.kangle.kardleaf.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.kangle.kardleaf.MainActivity
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.NoteEntity
import com.kangle.kardleaf.data.utils.KardLeafLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NoteListWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                val appContext = context.applicationContext
                appWidgetIds.forEach { appWidgetId -> updateWidget(appContext, appWidgetManager, appWidgetId) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { appWidgetId ->
            editor.remove(folderPrefKey(appWidgetId))
            editor.remove(hideTitlePrefKey(appWidgetId))
            editor.remove(previewLinesPrefKey(appWidgetId))
            WidgetTheme.clear(context, WidgetTheme.Kind.NOTE, appWidgetId)
        }
        editor.apply()
        super.onDeleted(context, appWidgetIds)
    }

    private suspend fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val noteDao = AppDatabase.getDatabase(context).noteDao()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val folder = prefs.getString(folderPrefKey(appWidgetId), null)
        val hasNotes = if (folder == null) {
            noteDao.getWidgetRecentNoteShells(1).isNotEmpty()
        } else {
            noteDao.getWidgetNoteShellsByFolder(folder, folderPrefix(folder), 1).isNotEmpty()
        }
        val views = createRemoteViews(context, appWidgetId, folder, hasNotes)
        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.note_widget_list)
    }

    private fun createRemoteViews(
        context: Context,
        appWidgetId: Int,
        folder: String?,
        hasNotes: Boolean,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_note_list).apply {
        val palette = WidgetTheme.configuredPalette(context, WidgetTheme.Kind.NOTE, appWidgetId)
        KardLeafLog.i(
            WIDGET_THEME_LOG_TAG,
            "widget palette kind=NOTE widgetId=$appWidgetId configured=${palette != null} " +
                "background=${palette?.background?.let(Integer::toHexString) ?: "layout"} " +
                "surface=${palette?.surface?.let(Integer::toHexString) ?: "layout"} " +
                "accent=${palette?.accent?.let(Integer::toHexString) ?: "layout"}",
        )
        WidgetTheme.applyBackground(this, R.id.note_widget_root, palette?.background)
        WidgetTheme.applyText(this, R.id.note_widget_folder, palette?.onSurface)
        WidgetTheme.applyText(this, R.id.note_widget_empty, palette?.muted)
        WidgetTheme.applyBackground(this, R.id.note_widget_add, palette?.accent)
        WidgetTheme.applyIcon(this, R.id.note_widget_more, palette?.onSurface)
        WidgetTheme.applyIcon(this, R.id.note_widget_folder_arrow, palette?.muted)
        setTextViewText(R.id.note_widget_folder, folderTitle(context, folder))
        setOnClickPendingIntent(R.id.note_widget_folder_control, folderPickerPendingIntent(context, appWidgetId))
        setOnClickPendingIntent(R.id.note_widget_open_folder, openFolderPendingIntent(context, appWidgetId, folder))
        setOnClickPendingIntent(R.id.note_widget_add, newNotePendingIntent(context, appWidgetId, folder))
        setOnClickPendingIntent(R.id.note_widget_more, settingsPendingIntent(context, appWidgetId))
        setRemoteAdapter(
            R.id.note_widget_list,
            Intent(context, NoteListWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("kardleaf://note-widget/list/$appWidgetId")
            },
        )
        setEmptyView(R.id.note_widget_list, R.id.note_widget_empty)
        setViewVisibility(R.id.note_widget_empty, if (hasNotes) View.GONE else View.VISIBLE)
        setPendingIntentTemplate(
            R.id.note_widget_list,
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN_NOTE + appWidgetId,
                Intent(context, NoteWidgetQuickAddActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("kardleaf://note-widget/edit/$appWidgetId")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        )
    }

    private fun folderPickerPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, NoteWidgetFolderPickerActivity::class.java).apply {
            data = Uri.parse("kardleaf://note-widget/folder/$appWidgetId")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_PICK_FOLDER + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openFolderPendingIntent(
        context: Context,
        appWidgetId: Int,
        folder: String?,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_OPEN_FOLDER, folder.orEmpty())
            putExtra(EXTRA_OPEN_ALL_NOTES, folder == null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_FOLDER + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun settingsPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, NoteWidgetFolderPickerActivity::class.java).apply {
            data = Uri.parse("kardleaf://note-widget/settings/$appWidgetId")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(NoteWidgetFolderPickerActivity.EXTRA_OPEN_SETTINGS, true)
            putExtra(NoteWidgetFolderPickerActivity.EXTRA_WIDGET_KIND, WidgetTheme.Kind.NOTE.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_SETTINGS + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun newNotePendingIntent(
        context: Context,
        appWidgetId: Int,
        folder: String?,
    ): PendingIntent {
        val intent = Intent(context, NoteWidgetQuickAddActivity::class.java).apply {
            data = Uri.parse("kardleaf://note-widget/quick-add/$appWidgetId")
            putExtra(NoteWidgetQuickAddActivity.EXTRA_TARGET_FOLDER, folder.orEmpty())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_NEW_NOTE + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val PREFS_NAME = "note_list_widget"
        internal const val MAX_NOTES = 100
        private const val REQUEST_PICK_FOLDER = 30_000
        private const val REQUEST_NEW_NOTE = 31_000
        private const val REQUEST_OPEN_NOTE = 32_000
        private const val REQUEST_OPEN_FOLDER = 33_000
        private const val REQUEST_SETTINGS = 34_000
        private const val MIN_PREVIEW_LINES = 1
        private const val MAX_PREVIEW_LINES = 3
        private const val DEFAULT_PREVIEW_LINES = 1
        internal const val EXTRA_OPEN_FOLDER = "kardleaf_widget_open_folder"
        internal const val EXTRA_OPEN_ALL_NOTES = "kardleaf_widget_open_all_notes"
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private fun folderPrefKey(appWidgetId: Int): String = "folder_$appWidgetId"

        private fun hideTitlePrefKey(appWidgetId: Int): String = "hide_title_$appWidgetId"

        private fun previewLinesPrefKey(appWidgetId: Int): String = "preview_lines_$appWidgetId"

        internal fun selectedFolder(context: Context, appWidgetId: Int): String? =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(folderPrefKey(appWidgetId), null)

        internal fun isTitleHidden(context: Context, appWidgetId: Int): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(hideTitlePrefKey(appWidgetId), false)

        internal fun previewLineCount(context: Context, appWidgetId: Int): Int =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(previewLinesPrefKey(appWidgetId), DEFAULT_PREVIEW_LINES)
                .coerceIn(MIN_PREVIEW_LINES, MAX_PREVIEW_LINES)

        internal fun selectFolder(context: Context, appWidgetId: Int, folder: String?) {
            val appContext = context.applicationContext
            val editor = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            if (folder == null) {
                editor.remove(folderPrefKey(appWidgetId))
            } else {
                editor.putString(folderPrefKey(appWidgetId), folder)
            }
            editor.apply()
            widgetScope.launch {
                NoteListWidgetProvider().updateWidget(
                    appContext,
                    AppWidgetManager.getInstance(appContext),
                    appWidgetId,
                )
            }
        }

        internal fun setDisplayOptions(
            context: Context,
            appWidgetId: Int,
            hideTitle: Boolean,
            previewLines: Int,
        ) {
            val appContext = context.applicationContext
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(hideTitlePrefKey(appWidgetId), hideTitle)
                .putInt(
                    previewLinesPrefKey(appWidgetId),
                    previewLines.coerceIn(MIN_PREVIEW_LINES, MAX_PREVIEW_LINES),
                )
                .apply()
            widgetScope.launch {
                NoteListWidgetProvider().updateWidget(
                    appContext,
                    AppWidgetManager.getInstance(appContext),
                    appWidgetId,
                )
            }
        }

        internal fun folderPrefix(folder: String): String = if (folder.isBlank()) "/" else "$folder/%"

        private fun folderTitle(context: Context, folder: String?): String = when {
            folder == null -> context.getString(R.string.widget_folder_all_notes)
            folder.isBlank() -> context.getString(R.string.widget_folder_root)
            else -> folder.substringAfterLast('/')
        }

        internal fun compactTitle(note: NoteEntity): String = note.title
            .ifBlank { note.fileName.removeSuffix(".md") }
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "未命名" }

        internal fun compactBody(note: NoteEntity): String {
            val raw = note.contentPreview.ifBlank { note.content }
            return raw
                .replace(Regex("[#>*_`\\-\\[\\]()!]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { "无正文预览" }
        }

        fun refreshAllWidgets(context: Context) {
            val appContext = context.applicationContext
            widgetScope.launch {
                val manager = AppWidgetManager.getInstance(appContext)
                val widgetIds = manager.getAppWidgetIds(ComponentName(appContext, NoteListWidgetProvider::class.java))
                widgetIds.forEach { widgetId ->
                    NoteListWidgetProvider().updateWidget(appContext, manager, widgetId)
                }
            }
        }
    }
}
