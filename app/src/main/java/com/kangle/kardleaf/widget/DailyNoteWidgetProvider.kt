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
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.KardLeafLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DailyNoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                val appContext = context.applicationContext
                appWidgetIds.forEach { appWidgetId ->
                    updateWidget(appContext, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            WidgetTheme.clear(context, WidgetTheme.Kind.DAILY, appWidgetId)
        }
        super.onDeleted(context, appWidgetIds)
    }

    private suspend fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val prefsManager = PrefsManager(context)
        val title = DailyNoteSupport.todayTitle()
        val folder = prefsManager.getDailyNoteFolder()
        val notePath = DailyNoteSupport.filePath(folder, title)
        val shell = if (prefsManager.getRootUri().isNullOrBlank()) {
            null
        } else {
            AppDatabase.getDatabase(context).noteDao().getNoteShellByPath(notePath)
        }
        val palette = WidgetTheme.configuredPalette(context, WidgetTheme.Kind.DAILY, appWidgetId)
        KardLeafLog.i(
            WIDGET_THEME_LOG_TAG,
            "widget palette kind=DAILY widgetId=$appWidgetId configured=${palette != null} " +
                "background=${palette?.background?.let(Integer::toHexString) ?: "layout"} " +
                "surface=${palette?.surface?.let(Integer::toHexString) ?: "layout"} " +
                "accent=${palette?.accent?.let(Integer::toHexString) ?: "layout"}",
        )
        val views = RemoteViews(context.packageName, R.layout.widget_daily_note).apply {
            WidgetTheme.applyBackground(this, R.id.daily_widget_root, palette?.background)
            WidgetTheme.applyText(this, R.id.daily_widget_title, palette?.onSurface)
            WidgetTheme.applyText(this, R.id.daily_widget_content, palette?.muted)
            WidgetTheme.applyIcon(this, R.id.daily_widget_more, palette?.onSurface)
            setTextViewText(R.id.daily_widget_title, title)
            setTextViewText(
                R.id.daily_widget_content,
                when {
                    prefsManager.getRootUri().isNullOrBlank() -> "请先在应用中选择笔记库"
                    shell == null -> "点击打开并创建今日笔记"
                    shell.contentPreview.isBlank() -> "今日还没有内容"
                    else -> NoteListWidgetProvider.compactBody(shell)
                },
            )
            setViewVisibility(R.id.daily_widget_content, View.VISIBLE)
            setOnClickPendingIntent(R.id.daily_widget_title, editPendingIntent(context, appWidgetId, folder, title, notePath, shell != null))
            setOnClickPendingIntent(R.id.daily_widget_content, editPendingIntent(context, appWidgetId, folder, title, notePath, shell != null))
            setOnClickPendingIntent(R.id.daily_widget_more, settingsPendingIntent(context, appWidgetId))
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun editPendingIntent(
        context: Context,
        appWidgetId: Int,
        folder: String,
        title: String,
        notePath: String,
        exists: Boolean,
    ): PendingIntent {
        val intent = Intent(context, NoteWidgetQuickAddActivity::class.java).apply {
            data = Uri.parse("kardleaf://daily-note/$appWidgetId/$title")
            if (exists) {
                putExtra(NoteWidgetQuickAddActivity.EXTRA_NOTE_ID, notePath)
            } else {
                putExtra(NoteWidgetQuickAddActivity.EXTRA_TARGET_FOLDER, folder)
                putExtra(NoteWidgetQuickAddActivity.EXTRA_INITIAL_TITLE, title)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_EDIT + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun settingsPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, NoteWidgetFolderPickerActivity::class.java).apply {
            data = Uri.parse("kardleaf://daily-note/settings/$appWidgetId")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(NoteWidgetFolderPickerActivity.EXTRA_OPEN_SETTINGS, true)
            putExtra(NoteWidgetFolderPickerActivity.EXTRA_WIDGET_KIND, WidgetTheme.Kind.DAILY.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_SETTINGS + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val REQUEST_EDIT = 35_000
        private const val REQUEST_SETTINGS = 36_000
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refreshAllWidgets(context: Context) {
            val appContext = context.applicationContext
            widgetScope.launch {
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(
                    ComponentName(appContext, DailyNoteWidgetProvider::class.java),
                )
                ids.forEach { id ->
                    DailyNoteWidgetProvider().updateWidget(appContext, manager, id)
                }
            }
        }
    }
}
