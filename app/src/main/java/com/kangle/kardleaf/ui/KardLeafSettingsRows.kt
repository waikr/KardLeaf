package com.kangle.kardleaf.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.model.HistoryCleanupPreview
import com.kangle.kardleaf.data.model.NoteRecordSummary
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.theme.LocalKardLeafGlobalCornerRadiusDp
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle

private const val SETTINGS_TRACE_TAG = "KardLeafSettingsTrace"

private val LocalSettingsListGroup = staticCompositionLocalOf { false }

@Composable
internal fun SettingsListGroup(content: @Composable () -> Unit) {
    if (LocalKardLeafThemeStyle.current == PrefsManager.AppThemeStyle.CLEAN_LIST) {
        val cornerRadiusDp = LocalKardLeafGlobalCornerRadiusDp.current.takeIf { it >= 0 } ?: 28
        val shape = RoundedCornerShape(cornerRadiusDp.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f), shape),
        ) {
            CompositionLocalProvider(LocalSettingsListGroup provides true) {
                content()
            }
        }
    } else {
        content()
    }
}

@Composable
internal fun SettingsSectionTitle(
    text: String,
    subtitle: String? = null,
) {
    val themeStyle = LocalKardLeafThemeStyle.current
    val isCleanList = themeStyle == PrefsManager.AppThemeStyle.CLEAN_LIST
    val isModern = themeStyle != PrefsManager.AppThemeStyle.CLASSIC
    Column(
        modifier = Modifier.padding(
            start = if (isCleanList) 8.dp else if (isModern) 4.dp else 0.dp,
            top = if (isCleanList) 22.dp else if (isModern) 18.dp else 12.dp,
            bottom = if (isCleanList) 9.dp else if (isModern) 8.dp else 6.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(if (isModern) 3.dp else 2.dp),
    ) {
        Text(
            text = text,
            style = if (isCleanList) MaterialTheme.typography.labelLarge else if (isModern) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            color = if (isCleanList) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsSectionDivider() {
    if (LocalKardLeafThemeStyle.current != PrefsManager.AppThemeStyle.CLASSIC) {
        Spacer(modifier = Modifier.height(14.dp))
    } else {
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun SettingsPageText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 0.dp, end = 4.dp, bottom = 6.dp),
    )
}

@Composable
internal fun NoteRecordSummarySettingsPage(
    title: String,
    emptyText: String,
    summaries: List<NoteRecordSummary>,
    isLoading: Boolean,
    onOpenNote: (String) -> Unit,
) {
    SettingsSectionTitle(title, "点击条目进入对应笔记")
    when {
        isLoading -> SettingsPageText("正在读取记录...")
        summaries.isEmpty() -> SettingsPageText(emptyText)
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                summaries.forEach { item ->
                    val previewText = item.contentPreview
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { "无正文预览" }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenNote(item.noteId) }
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = item.title.ifBlank { "无标题" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryCleanupPreviewContent(
    keep: Int,
    preview: List<HistoryCleanupPreview>,
    isLoading: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (keep == 0) {
                "每篇笔记的历史版本都会被清空，此操作不可撤销"
            } else {
                "每篇笔记将只保留最新 $keep 个历史版本，更早的历史版本会被删除，此操作不可撤销"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        when {
            isLoading -> {
                Text(
                    text = "正在读取历史版本...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            preview.isEmpty() -> {
                Text(
                    text = "当前没有需要清理的历史版本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Text(
                    text = "将清理以下文件的旧历史版本：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    preview.take(50).forEach { item ->
                        Text(
                            text = "${item.noteId}：共 ${item.versionCount} 个，将删除 ${item.deleteCount} 个",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (preview.size > 50) {
                        Text(
                            text = "还有 ${preview.size - 50} 个文件未显示",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val isCleanList = LocalKardLeafThemeStyle.current == PrefsManager.AppThemeStyle.CLEAN_LIST
    SettingsBaseRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = if (isCleanList) {
            {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.64f),
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            null
        },
    )
}

@Composable
internal fun SettingsChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SettingsBaseRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        selected = false,
        onClick = onClick,
        trailing = { RadioButton(selected = selected, onClick = null) },
    )
}

@Composable
internal fun SettingsColorChoiceRow(
    title: String,
    subtitle: String,
    swatchColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SettingsBaseRow(
        icon = Icons.Outlined.Palette,
        title = title,
        subtitle = subtitle,
        selected = false,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(swatchColor)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(13.dp)),
                )
                RadioButton(selected = selected, onClick = null)
            }
        },
    )
}

@Composable
internal fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsBaseRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        selected = false,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
internal fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsBaseRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        selected = false,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingsBaseRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentHorizontalPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val themeStyle = LocalKardLeafThemeStyle.current
    val isCleanList = themeStyle == PrefsManager.AppThemeStyle.CLEAN_LIST
    val isModern = themeStyle != PrefsManager.AppThemeStyle.CLASSIC
    val cleanListFeatureIconStyle = remember(context) { PrefsManager(context).getCleanListFeatureIconStyle() }
    val inCleanListGroup = LocalSettingsListGroup.current
    val tracedOnClick = {
        KardLeafLog.d(
            SETTINGS_TRACE_TAG,
            "SettingsBaseRow click title=$title modern=$isModern selected=$selected hasTrailing=${trailing != null} " +
                "hasLongClick=${onLongClick != null}",
        )
        onClick()
    }
    val tracedOnLongClick = onLongClick?.let { originalOnLongClick ->
        {
            KardLeafLog.d(
                SETTINGS_TRACE_TAG,
                "SettingsBaseRow longClick title=$title modern=$isModern selected=$selected hasTrailing=${trailing != null}",
            )
            originalOnLongClick()
        }
    }
    LaunchedEffect(title, subtitle, isModern, selected, contentHorizontalPadding, trailing != null, onLongClick != null) {
        KardLeafLog.d(
            SETTINGS_TRACE_TAG,
            "SettingsBaseRow compose title=$title modern=$isModern selected=$selected subtitleBlank=${subtitle.isBlank()} " +
                "horizontalPadding=$contentHorizontalPadding hasTrailing=${trailing != null} hasLongClick=${onLongClick != null}",
        )
    }
    if (!isModern) {
        val interactionSource = remember { MutableInteractionSource() }
        val clickModifier = if (onLongClick == null) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = tracedOnClick,
            )
        } else {
            Modifier.combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = tracedOnClick,
                onLongClick = tracedOnLongClick,
            )
        }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(clickModifier)
                .padding(horizontal = contentHorizontalPadding, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            }
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        var traceWasPressed by remember { mutableStateOf(false) }
        val cornerRadiusDp = LocalKardLeafGlobalCornerRadiusDp.current.takeIf { it >= 0 }
        val rowShape = RoundedCornerShape((cornerRadiusDp ?: if (isCleanList) 28 else 22).dp)
        val iconShape = RoundedCornerShape((cornerRadiusDp ?: if (isCleanList) 14 else 16).dp)
        val targetBackgroundColor = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else if (isCleanList && inCleanListGroup) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surface
        }
        val targetBorderColor = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
        } else if (isCleanList && inCleanListGroup) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
        }
        val targetIconBackgroundColor = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else if (isCleanList) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f)
        }
        val targetElevation = 0.dp
        LaunchedEffect(title, selected, targetBackgroundColor, targetBorderColor, targetIconBackgroundColor, targetElevation) {
            KardLeafLog.d(
                SETTINGS_TRACE_TAG,
                "SettingsBaseRow modernStyle title=$title selected=$selected " +
                    "background=$targetBackgroundColor border=$targetBorderColor iconBackground=$targetIconBackgroundColor " +
                    "elevation=$targetElevation shadowClip=true ripple=null",
            )
        }
        LaunchedEffect(isPressed) {
            if (isPressed) {
                traceWasPressed = true
                KardLeafLog.d(SETTINGS_TRACE_TAG, "SettingsBaseRow pressed title=$title selected=$selected")
            } else if (traceWasPressed) {
                traceWasPressed = false
                KardLeafLog.d(SETTINGS_TRACE_TAG, "SettingsBaseRow released title=$title selected=$selected")
            }
        }
        val backgroundColor by animateColorAsState(
            targetValue = targetBackgroundColor,
            label = "SettingsRowBackground",
        )
        val borderColor by animateColorAsState(
            targetValue = targetBorderColor,
            label = "SettingsRowBorder",
        )
        val iconBackgroundColor by animateColorAsState(
            targetValue = targetIconBackgroundColor,
            label = "SettingsRowIconBackground",
        )
        val elevation by animateDpAsState(
            targetValue = targetElevation,
            label = "SettingsRowElevation",
        )
        val clickModifier = if (onLongClick == null) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = tracedOnClick,
            )
        } else {
            Modifier.combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = tracedOnClick,
                onLongClick = tracedOnLongClick,
            )
        }

        val rowContainerModifier = if (isCleanList && inCleanListGroup) {
            Modifier
                .fillMaxWidth()
                .heightIn(min = 78.dp)
                .background(backgroundColor)
        } else {
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (isCleanList) 78.dp else 0.dp)
                .padding(vertical = if (isCleanList) 4.dp else 5.dp)
                .shadow(elevation = elevation, shape = rowShape, clip = true)
                .clip(rowShape)
                .background(backgroundColor)
                .border(1.dp, borderColor, rowShape)
        }

        Row(
            modifier = modifier
                .then(rowContainerModifier)
                .then(clickModifier)
                .padding(
                    horizontal = contentHorizontalPadding + if (isCleanList) 18.dp else 14.dp,
                    vertical = if (isCleanList) 14.dp else 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconColor = if (isCleanList) {
                if (cleanListFeatureIconStyle == PrefsManager.CleanListFeatureIconStyle.MODERN) {
                    cleanListIconColor(title)
                } else {
                    MaterialTheme.colorScheme.primary
                }
            } else if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(if (isCleanList) 40.dp else 42.dp)
                    .clip(iconShape)
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(if (isCleanList) 24.dp else 22.dp),
                )
            }
            Spacer(modifier = Modifier.width(if (isCleanList) 12.dp else 14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (isCleanList) 4.dp else 3.dp),
            ) {
                Text(
                    text = title,
                    style = if (isCleanList) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(if (isCleanList) 12.dp else 10.dp))
                trailing()
            }
        }
    }
}

private val CleanListIconPalette = listOf(
    Color(0xFF3B82F6),
    Color(0xFF16A34A),
    Color(0xFFF59E0B),
    Color(0xFFEC4899),
    Color(0xFF8B5CF6),
    Color(0xFF0EA5E9),
    Color(0xFFEF4444),
)

private fun cleanListIconColor(title: String): Color =
    CleanListIconPalette[(title.hashCode() and Int.MAX_VALUE) % CleanListIconPalette.size]
