package com.kangle.kardleaf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import com.kangle.kardleaf.AppUpdateCheckResult
import com.kangle.kardleaf.BuildConfig
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin

internal fun createAppReleaseNotesMarkwon(context: Context): Markwon =
    Markwon.builder(context)
        .usePlugin(HtmlPlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(ImagesPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .build()

internal fun createAppReleaseNotesTextView(context: Context): TextView =
    TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setLineSpacing(0f, 1.15f)
        movementMethod = LinkMovementMethod.getInstance()
        linksClickable = true
    }

@Composable
internal fun AppReleaseNotes(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val markwon = remember(context) { createAppReleaseNotesMarkwon(context) }
    AndroidView(
        modifier = modifier,
        factory = ::createAppReleaseNotesTextView,
        update = { textView ->
            textView.setTextColor(textColor)
            markwon.setMarkdown(textView, markdown)
        },
    )
}

@Composable
internal fun AppUpdateDialog(
    result: AppUpdateCheckResult,
    settingsEnglish: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val update = result as? AppUpdateCheckResult.UpdateAvailable
    fun text(chinese: String, english: String) = if (settingsEnglish) english else chinese

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 640.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                when (result) {
                    is AppUpdateCheckResult.UpdateAvailable -> text("发现新版本", "Update available")
                    is AppUpdateCheckResult.UpToDate -> text("已是最新版本", "Up to date")
                    is AppUpdateCheckResult.Failed -> text("检查更新失败", "Update check failed")
                },
            )
        },
        text = {
            when (result) {
                is AppUpdateCheckResult.UpdateAvailable -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            buildString {
                                append(text("当前版本：", "Current version: "))
                                append(BuildConfig.VERSION_NAME)
                                append("\n")
                                append(text("最新版本：", "Latest version: "))
                                append(result.release.tagName)
                                if (result.release.publishedDate.isNotBlank()) {
                                    append("\n")
                                    append(text("发布日期：", "Published: "))
                                    append(result.release.publishedDate)
                                }
                            },
                        )
                        if (result.release.releaseNotes.isNotBlank()) {
                            AppReleaseNotes(
                                markdown = result.release.releaseNotes,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                is AppUpdateCheckResult.UpToDate -> Text(
                    text = text(
                        "当前 ${BuildConfig.VERSION_NAME} 已是最新正式版本。",
                        "Version ${BuildConfig.VERSION_NAME} is the latest stable release.",
                    ),
                )
                is AppUpdateCheckResult.Failed -> Text(result.message)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    update?.let { available ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available.release.downloadUrl)))
                        }.onFailure {
                            context.showToast(text("无法打开下载链接", "Unable to open download link"))
                        }
                    }
                },
            ) {
                Text(text(if (update != null) "下载更新" else "确定", if (update != null) "Download" else "OK"))
            }
        },
        dismissButton = if (update != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(text("稍后", "Later"))
                }
            }
        } else {
            null
        },
    )
}
