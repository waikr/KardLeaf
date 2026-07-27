package com.kangle.kardleaf.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.KardLeafLog

private const val SHARE_QUICK_MEMO_LOG_TAG = "KardLeafShareQuickMemo"
private const val MAX_SHARED_TITLE_CHARS = 120
private const val MAX_SHARED_CONTENT_CHARS = 50_000

class ShareToQuickMemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        if (intent.action != Intent.ACTION_SEND) {
            KardLeafLog.w(SHARE_QUICK_MEMO_LOG_TAG, "ignored action=${intent.action}")
            finishWithoutTransition()
            return
        }

        val sharedTitle = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
            ?.toString()
            .orEmpty()
            .take(MAX_SHARED_TITLE_CHARS)
        val sharedContent = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            .orEmpty()
            .take(MAX_SHARED_CONTENT_CHARS)

        KardLeafLog.i(
            SHARE_QUICK_MEMO_LOG_TAG,
            "share received mime=${intent.type} titleChars=${sharedTitle.length} contentChars=${sharedContent.length}",
        )

        startActivity(
            Intent(this, NoteWidgetQuickAddActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(
                    NoteWidgetQuickAddActivity.EXTRA_TARGET_FOLDER,
                    PrefsManager.DEFAULT_QUICK_NOTE_FOLDER_NAME,
                )
                putExtra(NoteWidgetQuickAddActivity.EXTRA_INITIAL_TITLE, sharedTitle)
                putExtra(NoteWidgetQuickAddActivity.EXTRA_INITIAL_CONTENT, sharedContent)
                flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
            },
        )
        finishWithoutTransition()
    }

    private fun finishWithoutTransition() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
