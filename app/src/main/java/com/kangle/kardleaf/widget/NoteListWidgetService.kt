package com.kangle.kardleaf.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.NoteEntity
import com.kangle.kardleaf.data.utils.KardLeafLog
import kotlinx.coroutines.runBlocking

class NoteListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return NoteFactory(applicationContext, appWidgetId)
    }

    private class NoteFactory(
        private val context: Context,
        private val appWidgetId: Int,
    ) : RemoteViewsFactory {
        private var notes: List<NoteEntity> = emptyList()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                notes = emptyList()
                KardLeafLog.w(WIDGET_CLICK_LOG_TAG, "note adapter invalid widgetId")
                return
            }
            notes = runBlocking {
                val noteDao = AppDatabase.getDatabase(context).noteDao()
                val folder = NoteListWidgetProvider.selectedFolder(context, appWidgetId)
                if (folder == null) {
                    noteDao.getWidgetRecentNoteShells(NoteListWidgetProvider.MAX_NOTES)
                } else {
                    noteDao.getWidgetNoteShellsByFolder(
                        folder,
                        NoteListWidgetProvider.folderPrefix(folder),
                        NoteListWidgetProvider.MAX_NOTES,
                    )
                }
            }
            KardLeafLog.i(
                WIDGET_CLICK_LOG_TAG,
                "note adapter ready widgetId=$appWidgetId count=${notes.size} " +
                    "hideTitle=${NoteListWidgetProvider.isTitleHidden(context, appWidgetId)} " +
                    "previewLines=${NoteListWidgetProvider.previewLineCount(context, appWidgetId)}",
            )
        }

        override fun onDestroy() {
            notes = emptyList()
        }

        override fun getCount(): Int = notes.size

        override fun getViewAt(position: Int): RemoteViews {
            val note = notes.getOrNull(position)
            val hideTitle = NoteListWidgetProvider.isTitleHidden(context, appWidgetId)
            val previewLines = NoteListWidgetProvider.previewLineCount(context, appWidgetId)
            val layoutId = if (hideTitle) {
                R.layout.widget_note_list_item_title_hidden
            } else {
                R.layout.widget_note_list_item
            }
            return RemoteViews(context.packageName, layoutId).apply {
                if (note != null) {
                    val clickIntent = Intent().apply {
                        putExtra(NoteWidgetQuickAddActivity.EXTRA_NOTE_ID, note.filePath)
                    }
                    if (!hideTitle) {
                        setTextViewText(R.id.note_widget_item_title, NoteListWidgetProvider.compactTitle(note))
                        setOnClickFillInIntent(R.id.note_widget_item_title, clickIntent)
                    }
                    setTextViewText(R.id.note_widget_item_body, NoteListWidgetProvider.compactBody(note))
                    setInt(R.id.note_widget_item_body, "setMaxLines", previewLines)
                    setOnClickFillInIntent(R.id.note_widget_item_body, clickIntent)
                    setOnClickFillInIntent(R.id.note_widget_item, clickIntent)
                }
            }
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 2

        override fun getItemId(position: Int): Long = notes.getOrNull(position)?.filePath?.hashCode()?.toLong()
            ?: position.toLong()

        override fun hasStableIds(): Boolean = true
    }

    companion object {
        private const val WIDGET_CLICK_LOG_TAG = "KardLeafWidgetClick"
    }
}
