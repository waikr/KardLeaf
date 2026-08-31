package com.kangle.kardleaf.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.ui.theme.KardLeafTheme

object WidgetPinHelper {
    fun showPicker(activity: Activity) {
        activity.startActivity(Intent(activity, WidgetPickerActivity::class.java))
    }
}

class WidgetPickerActivity : ComponentActivity() {
    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var pickerShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handleQuickMemoAction(intent)) return
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        val prefsManager = PrefsManager(this)
        if (prefsManager.hasSeenWidgetShortcutPermissionHint()) {
            showPicker()
        } else {
            prefsManager.markWidgetShortcutPermissionHintSeen()
            showShortcutPermissionGate()
        }
    }

    private fun showShortcutPermissionGate() {
        setContent {
            KardLeafTheme(styleSystemBars = false) {
                ShortcutPermissionDialog(
                    onConfirm = ::showPicker,
                    onDismiss = ::finishWithoutTransition,
                )
            }
        }
    }

    private fun showPicker() {
        if (pickerShown) return
        pickerShown = true
        val widgetPinningSupported = isWidgetPinningSupported()
        val quickMemoPinningSupported = isQuickMemoPinningSupported()
        setContent {
            KardLeafTheme(styleSystemBars = false) {
                if (widgetPinningSupported || quickMemoPinningSupported) {
                    WidgetPickerDialog(
                        showWidgetChoices = widgetPinningSupported,
                        showQuickMemoShortcut = quickMemoPinningSupported,
                        onDismiss = ::finishWithoutTransition,
                        onSelectTaskWidget = { requestWidgetPin(TaskListWidgetProvider::class.java) },
                        onSelectNoteWidget = { requestWidgetPin(NoteListWidgetProvider::class.java) },
                        onSelectDailyNoteWidget = { requestWidgetPin(DailyNoteWidgetProvider::class.java) },
                        onSelectQuickMemoShortcut = ::requestQuickMemoShortcut,
                    )
                } else {
                    UnsupportedWidgetDialog(
                        onDismiss = ::finishWithoutTransition,
                        onOpenSettings = ::openAppPermissionSettings,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickMemoAction(intent)
    }

    private fun handleQuickMemoAction(intent: Intent): Boolean {
        if (intent.action != ACTION_OPEN_QUICK_MEMO) return false
        launchQuickMemo()
        return true
    }

    private fun isWidgetPinningSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported

    private fun isQuickMemoPinningSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true
    }

    private fun requestWidgetPin(provider: Class<out AppWidgetProvider>) {
        val accepted = appWidgetManager.requestPinAppWidget(
            ComponentName(this, provider),
            null,
            null,
        )
        if (!accepted) {
            Toast.makeText(this, R.string.widget_pin_request_failed, Toast.LENGTH_LONG).show()
        }
        finishWithoutTransition()
    }

    private fun requestQuickMemoShortcut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(ShortcutManager::class.java) ?: return
        val shortcutIntent = Intent(this, WidgetPickerActivity::class.java).apply {
            action = ACTION_OPEN_QUICK_MEMO
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val shortcut = ShortcutInfo.Builder(this, QUICK_MEMO_SHORTCUT_ID)
            .setShortLabel(getString(R.string.shortcut_quick_memo_short))
            .setLongLabel(getString(R.string.shortcut_quick_memo_long))
            .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_quick_memo))
            .setIntent(shortcutIntent)
            .build()
        if (!manager.requestPinShortcut(shortcut, null)) {
            Toast.makeText(this, R.string.shortcut_quick_memo_pin_failed, Toast.LENGTH_LONG).show()
        }
        finishWithoutTransition()
    }

    private fun launchQuickMemo() {
        startActivity(
            Intent(this, NoteWidgetQuickAddActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(
                    NoteWidgetQuickAddActivity.EXTRA_TARGET_FOLDER,
                    PrefsManager.DEFAULT_QUICK_NOTE_FOLDER_NAME,
                )
                flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
            },
        )
        finishWithoutTransition()
    }

    private fun openAppPermissionSettings() {
        val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(appDetailsIntent) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) }
            .onFailure {
                Toast.makeText(this, R.string.widget_permission_settings_failed, Toast.LENGTH_LONG).show()
            }
        finishWithoutTransition()
    }

    private fun finishWithoutTransition() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val ACTION_OPEN_QUICK_MEMO = "com.kangle.kardleaf.action.OPEN_QUICK_MEMO"
        const val QUICK_MEMO_SHORTCUT_ID = "quick_memo"
    }
}

@Composable
private fun ShortcutPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_shortcut_permission_title)) },
        text = { Text(stringResource(R.string.widget_shortcut_permission_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.widget_shortcut_permission_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun WidgetPickerDialog(
    showWidgetChoices: Boolean,
    showQuickMemoShortcut: Boolean,
    onDismiss: () -> Unit,
    onSelectTaskWidget: () -> Unit,
    onSelectNoteWidget: () -> Unit,
    onSelectDailyNoteWidget: () -> Unit,
    onSelectQuickMemoShortcut: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.widget_picker_title),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                if (showWidgetChoices) {
                    WidgetChoice(
                        icon = Icons.Outlined.Checklist,
                        title = stringResource(R.string.widget_task_list_label),
                        description = stringResource(R.string.widget_task_list_desc),
                        onClick = onSelectTaskWidget,
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    WidgetChoice(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.widget_note_list_label),
                        description = stringResource(R.string.widget_note_list_desc),
                        onClick = onSelectNoteWidget,
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    WidgetChoice(
                        icon = Icons.Outlined.EventNote,
                        title = stringResource(R.string.widget_daily_note_label),
                        description = stringResource(R.string.widget_daily_note_desc),
                        onClick = onSelectDailyNoteWidget,
                    )
                }
                if (showWidgetChoices && showQuickMemoShortcut) {
                    Spacer(modifier = Modifier.size(10.dp))
                }
                if (showQuickMemoShortcut) {
                    WidgetChoice(
                        icon = Icons.Outlined.EditNote,
                        title = stringResource(R.string.shortcut_quick_memo_long),
                        description = stringResource(R.string.shortcut_quick_memo_desc),
                        onClick = onSelectQuickMemoShortcut,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun UnsupportedWidgetDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_shortcut_widget),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.widget_picker_title)) },
        text = {
            Text(
                text = stringResource(R.string.widget_pin_unsupported),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.widget_open_permission_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun WidgetChoice(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(25.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(13.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
