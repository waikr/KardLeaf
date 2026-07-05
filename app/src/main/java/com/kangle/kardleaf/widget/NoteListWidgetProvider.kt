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
        appWidgetIds.forEach { appWidgetId -> updateWidgetAsync(context, appWidgetManager, appWidgetId) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_NEXT_FOLDER) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            widgetScope.launch {
                val appContext = context.applicationContext
                val noteDao = AppDatabase.getDatabase(appContext).noteDao()
                val folders = noteDao.getActiveFoldersSync().distinct().sorted()
                val choices = listOf<String?>(null) + folders
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val current = prefs.getString(folderPrefKey(appWidgetId), null)
                val currentIndex = choices.indexOf(current).takeIf { it >= 0 } ?: 0
                val next = choices[(currentIndex + 1) % choices.size]
                prefs.edit().putString(folderPrefKey(appWidgetId), next).apply()
                updateWidgetAsync(appContext, AppWidgetManager.getInstance(appContext), appWidgetId)
            }
        }
    }

    private fun updateWidgetAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        widgetScope.launch {
            val appContext = context.applicationContext
            val noteDao = AppDatabase.getDatabase(appContext).noteDao()
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val folder = prefs.getString(folderPrefKey(appWidgetId), null)
            val notes = if (folder == null) {
                noteDao.getWidgetRecentNoteShells(MAX_NOTES)
            } else {
                noteDao.getWidgetNoteShellsByFolder(folder, folderPrefix(folder), MAX_NOTES)
            }
            val views = createRemoteViews(appContext, appWidgetId, folder, notes)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun createRemoteViews(
        context: Context,
        appWidgetId: Int,
        folder: String?,
        notes: List<NoteEntity>,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_note_list).apply {
        setTextViewText(R.id.note_widget_folder, folderTitle(context, folder))
        setOnClickPendingIntent(R.id.note_widget_folder, nextFolderPendingIntent(context, appWidgetId))
        setOnClickPendingIntent(R.id.note_widget_add, newNotePendingIntent(context, appWidgetId))
        setViewVisibility(R.id.note_widget_empty, if (notes.isEmpty()) View.VISIBLE else View.GONE)

        noteRows.forEachIndexed { index, row ->
            val note = notes.getOrNull(index)
            setViewVisibility(row.rowId, if (note == null) View.GONE else View.VISIBLE)
            if (note != null) {
                setTextViewText(row.titleId, compactTitle(note))
                setTextViewText(row.bodyId, compactBody(note))
                setOnClickPendingIntent(row.rowId, openNotePendingIntent(context, appWidgetId, index, note.filePath))
            }
        }
    }

    private fun nextFolderPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, NoteListWidgetProvider::class.java).apply {
            action = ACTION_NEXT_FOLDER
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_NEXT_FOLDER + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun newNotePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("kardleaf://new?root=1")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_NEW_NOTE + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openNotePendingIntent(
        context: Context,
        appWidgetId: Int,
        index: Int,
        notePath: String,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("kardleaf://widget-note/${Uri.encode(notePath)}")
            putExtra("note_id", notePath)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_NOTE + appWidgetId * 10 + index,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val ACTION_NEXT_FOLDER = "com.kangle.kardleaf.action.NEXT_NOTE_WIDGET_FOLDER"
        private const val PREFS_NAME = "note_list_widget"
        private const val MAX_NOTES = 5
        private const val REQUEST_NEXT_FOLDER = 30_000
        private const val REQUEST_NEW_NOTE = 31_000
        private const val REQUEST_OPEN_NOTE = 32_000
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private data class NoteRow(
            val rowId: Int,
            val titleId: Int,
            val bodyId: Int,
        )

        private val noteRows = listOf(
            NoteRow(R.id.note_widget_row_1, R.id.note_widget_title_1, R.id.note_widget_body_1),
            NoteRow(R.id.note_widget_row_2, R.id.note_widget_title_2, R.id.note_widget_body_2),
            NoteRow(R.id.note_widget_row_3, R.id.note_widget_title_3, R.id.note_widget_body_3),
            NoteRow(R.id.note_widget_row_4, R.id.note_widget_title_4, R.id.note_widget_body_4),
            NoteRow(R.id.note_widget_row_5, R.id.note_widget_title_5, R.id.note_widget_body_5),
        )

        private fun folderPrefKey(appWidgetId: Int): String = "folder_$appWidgetId"

        private fun folderPrefix(folder: String): String = if (folder.isBlank()) "/" else "$folder/%"

        private fun folderTitle(context: Context, folder: String?): String = when {
            folder == null -> context.getString(R.string.all_notes) + " ▾"
            folder.isBlank() -> context.getString(R.string.root_folder_no_label) + " ▾"
            else -> "$folder ▾"
        }

        private fun compactTitle(note: NoteEntity): String = note.title
            .ifBlank { note.fileName.removeSuffix(".md") }
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "未命名" }

        private fun compactBody(note: NoteEntity): String {
            val raw = note.contentPreview.ifBlank { note.content }
            return raw
                .replace(Regex("[#>*_`\\-\\[\\]()!]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { "无正文预览" }
        }

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(ComponentName(context, NoteListWidgetProvider::class.java))
            widgetIds.forEach { widgetId ->
                NoteListWidgetProvider().updateWidgetAsync(context, manager, widgetId)
            }
        }
    }
}
