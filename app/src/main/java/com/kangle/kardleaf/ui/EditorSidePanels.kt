package com.kangle.kardleaf.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.model.NoteRemark
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.kangle.kardleaf.data.utils.NoteTextStats
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun NoteOutlineSidePanel(
    headings: List<MarkdownHeading>,
    onHeadingClick: (MarkdownHeading) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "目录结构",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            if (headings.isEmpty()) {
                Text(
                    text = "暂无标题",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(headings) { heading ->
                        Text(
                            text = heading.text,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onHeadingClick(heading) }
                                    .padding(
                                        start = (6 + (heading.level - 1).coerceAtLeast(0) * 14).dp,
                                        top = 7.dp,
                                        end = 6.dp,
                                        bottom = 7.dp,
                                    ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NoteRemarkSidePanel(
    frontMatterProperties: List<NoteFormatUtils.FrontMatterProperty>,
    textStats: NoteTextStats?,
    remarks: List<NoteRemark>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onUpdate: (NoteRemark, String) -> Unit,
    onDelete: (NoteRemark) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "备注",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            NoteTextStatsCard(textStats)
            NoteFrontMatterPropertiesCard(frontMatterProperties)
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (draft.isBlank()) {
                            Text(
                                text = "新增一条备注",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已添加 ${remarks.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onAdd, enabled = draft.isNotBlank()) {
                    Text("添加")
                }
            }
            if (remarks.isEmpty()) {
                Text(
                    text = "暂无备注",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(remarks, key = { it.id }) { remark ->
                        NoteRemarkCard(
                            remark = remark,
                            onUpdate = { newContent -> onUpdate(remark, newContent) },
                            onDelete = { onDelete(remark) },
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun NoteTextStatsCard(textStats: NoteTextStats?) {
    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    val pendingText = "统计中…"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "统计",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        NoteStatsRow("字符数", textStats?.let { numberFormat.format(it.characterCount) } ?: pendingText)
        NoteStatsRow("词数", textStats?.let { numberFormat.format(it.wordCountWithPunctuation) } ?: pendingText)
        NoteStatsRow("词数（不带标点）", textStats?.let { numberFormat.format(it.wordCountWithoutPunctuation) } ?: pendingText)
        NoteStatsRow("行数", textStats?.let { numberFormat.format(it.lineCount) } ?: pendingText)
        NoteStatsRow("段落数", textStats?.let { numberFormat.format(it.paragraphCount) } ?: pendingText)
    }
}

@Composable
private fun NoteStatsRow(
    label: String,
    value: String,
) {
    CopyableInfoRow(
        label = label,
        value = value,
        labelWeight = 0.42f,
        valueWeight = 0.58f,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CopyableInfoRow(
    label: String,
    value: String,
    labelWeight: Float,
    valueWeight: Float,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scrollableValue = label == "标题" || label == "位置"
    val valueScrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    clipboard.setText(AnnotatedString(value))
                    Toast.makeText(context, "已复制$value", Toast.LENGTH_SHORT).show()
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(labelWeight),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(valueWeight)
                .then(if (scrollableValue) Modifier.horizontalScroll(valueScrollState) else Modifier),
            maxLines = 1,
            overflow = if (scrollableValue) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NoteFrontMatterPropertiesCard(properties: List<NoteFormatUtils.FrontMatterProperty>) {
    if (properties.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "属性",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        properties.forEach { property ->
            CopyableInfoRow(
                label = frontMatterDisplayName(property.key),
                value = frontMatterDisplayValue(property),
                labelWeight = 0.28f,
                valueWeight = 0.72f,
            )
        }
    }
}

private fun frontMatterDisplayName(key: String): String =
    when (key.trim()) {
        "tags" -> "标签"
        "aliases" -> "别名"
        "kardleaf_id" -> "ID"
        "title" -> "标题"
        "path" -> "位置"
        "created" -> "创建时间"
        "updated" -> "更新时间"
        else -> key.trim()
    }

private fun frontMatterDisplayValue(property: NoteFormatUtils.FrontMatterProperty): String =
    property.values.joinToString("、")

@Composable
private fun NoteRemarkCard(
    remark: NoteRemark,
    onUpdate: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = rememberRemarkTimeFormatter()
    var editing by remember(remark.id) { mutableStateOf(false) }
    var editingContent by remember(remark.id, remark.content) { mutableStateOf(remark.content) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (editing) {
            BasicTextField(
                value = editingContent,
                onValueChange = { editingContent = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    .padding(10.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    editingContent = remark.content
                    editing = false
                }) {
                    Text("取消")
                }
                TextButton(
                    onClick = {
                        onUpdate(editingContent.trim())
                        editing = false
                    },
                    enabled = editingContent.isNotBlank(),
                ) {
                    Text("保存")
                }
            }
        } else {
            Text(
                text = remark.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatter.format(Date(remark.updatedAtMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { editing = true }) {
                        Text("编辑")
                    }
                    TextButton(onClick = onDelete) {
                        Text("删除")
                    }
                }
            }
        }
    }
}


@Composable
private fun rememberRemarkTimeFormatter(): SimpleDateFormat =
    androidx.compose.runtime.remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }

@Composable
internal fun NoteReservedSidePanel(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}
