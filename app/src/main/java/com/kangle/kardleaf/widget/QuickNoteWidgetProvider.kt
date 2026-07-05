package com.kangle.kardleaf.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.kangle.kardleaf.MainActivity
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.repository.PrefsManager

class QuickNoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(
                appWidgetId,
                createRemoteViews(context),
            )
        }
    }

    private fun createRemoteViews(context: Context): RemoteViews {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_QUICK_NOTE
            data = quickNoteUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return RemoteViews(context.packageName, R.layout.widget_quick_note).apply {
            setOnClickPendingIntent(R.id.quick_note_widget_root, pendingIntent)
            setOnClickPendingIntent(R.id.quick_note_widget_add_button, pendingIntent)
        }
    }

    private fun quickNoteUri(): Uri =
        Uri.Builder()
            .scheme("kardleaf")
            .authority("new")
            .appendQueryParameter("folder", PrefsManager.DEFAULT_DRAFT_FOLDER_NAME)
            .build()

    companion object {
        private const val ACTION_QUICK_NOTE = "com.kangle.kardleaf.action.QUICK_NOTE"
    }
}
