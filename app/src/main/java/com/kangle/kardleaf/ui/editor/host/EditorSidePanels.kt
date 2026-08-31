package com.kangle.kardleaf.ui.editor.host

import com.kangle.kardleaf.R
import com.kangle.kardleaf.ui.MarkdownHeading
import com.kangle.kardleaf.ui.NoteTimestampPickerDialog
import com.kangle.kardleaf.ui.fileTreeHierarchyGuide
import com.kangle.kardleaf.ui.showToast
import com.kangle.kardleaf.ui.visibleMarkdownHeadings
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.database.NoteLinkEntity
import com.kangle.kardleaf.data.database.NoteLinkResolutionStatus
import com.kangle.kardleaf.data.model.NoteRemark
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.kangle.kardleaf.data.utils.NoteTextStats
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.toMutableStateList
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class OutlineHeadingEntry(
    val heading: MarkdownHeading,
    val depth: Int,
    val hasChildren: Boolean,
)

private fun buildOutlineHeadingDepths(headings: List<MarkdownHeading>): Map<Int, Int> {
    val depths = mutableMapOf<Int, Int>()
    val stack = mutableListOf<Int>()
    headings.forEach { heading ->
        while (stack.lastOrNull()?.let { it >= heading.level } == true) {
            stack.removeAt(stack.lastIndex)
        }
        depths[heading.startOffset] = stack.size
        stack += heading.level
    }
    return depths
}

@Composable
private fun OutlineHeadingRow(
    entry: OutlineHeadingEntry,
    collapsedHeadingOffsets: Set<Int>,
    selectedHeadingStartOffset: Int?,
    guideIndent: Int,
    guideColor: Color,
    outlineEditing: Boolean,
    editorEditing: Boolean,
    onHeadingClick: (MarkdownHeading) -> Unit,
    onToggleHeading: (MarkdownHeading) -> Unit,
    dragHandleModifier: Modifier,
    onEditHeading: (MarkdownHeading) -> Unit,
) {
    val heading = entry.heading
    val hasChildren = entry.hasChildren
    val isCollapsed = heading.startOffset in collapsedHeadingOffsets
    val isSelected = heading.startOffset == selectedHeadingStartOffset
    val guideModifier = (0 until entry.depth).fold<Int, Modifier>(Modifier) { modifier, depth ->
        modifier.fileTreeHierarchyGuide(depth, guideIndent, guideColor)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .then(guideModifier)
                .padding(
                    start = (12 + entry.depth * guideIndent).dp,
                    end = 4.dp,
                    top = 2.dp,
                    bottom = 2.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasChildren) {
                IconButton(
                    onClick = { onToggleHeading(heading) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (isCollapsed) {
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight
                        } else {
                            Icons.Outlined.KeyboardArrowDown
                        },
                        contentDescription = if (isCollapsed) "展开${heading.text}" else "折叠${heading.text}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(32.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(dragHandleModifier)
                    .clickable {
                        if (outlineEditing && editorEditing) {
                            onEditHeading(heading)
                        } else {
                            onHeadingClick(heading)
                        }
                    }
                    .padding(start = 2.dp, top = 5.dp, end = 6.dp, bottom = 5.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = heading.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun NoteOutlineSidePanel(
    headings: List<MarkdownHeading>,
    onHeadingClick: (MarkdownHeading) -> Unit,
    selectedHeadingStartOffset: Int? = null,
    editorEditing: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onMove: ((MarkdownHeading, MarkdownHeading, Boolean) -> Boolean)? = null,
    onRename: ((MarkdownHeading, String) -> Unit)? = null,
    onDelete: ((MarkdownHeading) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val expandableHeadingOffsets = remember(headings) {
        headings.indices
            .filter { index ->
                headings.getOrNull(index + 1)?.level?.let { it > headings[index].level } == true
            }
            .mapTo(hashSetOf()) { index -> headings[index].startOffset }
    }
    var collapsedHeadingOffsets by remember(headings) { mutableStateOf<Set<Int>>(emptySet()) }
    val allHeadingsCollapsed =
        expandableHeadingOffsets.isNotEmpty() && collapsedHeadingOffsets.containsAll(expandableHeadingOffsets)
    val visibleHeadings = remember(headings, collapsedHeadingOffsets) {
        visibleMarkdownHeadings(headings, collapsedHeadingOffsets)
    }
    val depthByOffset = remember(headings) { buildOutlineHeadingDepths(headings) }
    val orderedHeadings = remember(visibleHeadings) { visibleHeadings.toMutableStateList() }
    val listState = rememberLazyListState()
    fun visibleSubtreeEnd(items: List<MarkdownHeading>, startIndex: Int): Int {
        val depth = depthByOffset[items[startIndex].startOffset] ?: 0
        return (startIndex + 1 until items.size)
            .firstOrNull { (depthByOffset[items[it].startOffset] ?: 0) <= depth }
            ?: items.size
    }
    var outlineEditing by remember { mutableStateOf(false) }
    var editingHeading by remember { mutableStateOf<MarkdownHeading?>(null) }
    var dragStartOrder by remember { mutableStateOf<List<MarkdownHeading>?>(null) }
    var draggingHeadingStartOffset by remember { mutableStateOf<Int?>(null) }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index == to.index) return@rememberReorderableLazyListState
        val moving = orderedHeadings.getOrNull(from.index) ?: return@rememberReorderableLazyListState
        val target = orderedHeadings.getOrNull(to.index) ?: return@rememberReorderableLazyListState
        val blockEnd = visibleSubtreeEnd(orderedHeadings, from.index)
        if (to.index in from.index until blockEnd || moving.level != target.level) {
            return@rememberReorderableLazyListState
        }
        val block = orderedHeadings.subList(from.index, blockEnd).toList()
        orderedHeadings.subList(from.index, blockEnd).clear()
        val insertionIndex = if (to.index < from.index) {
            to.index
        } else {
            to.index - block.size + 1
        }
        orderedHeadings.addAll(insertionIndex.coerceIn(0, orderedHeadings.size), block)
    }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (reorderableState.isAnyItemDragging) return@LaunchedEffect
        val startOrder = dragStartOrder ?: return@LaunchedEffect
        val movingOffset = draggingHeadingStartOffset
        val fromIndex = startOrder.indexOfFirst { it.startOffset == movingOffset }
        val toIndex = orderedHeadings.indexOfFirst { it.startOffset == movingOffset }
        if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
            val moving = startOrder[fromIndex]
            val blockEnd = visibleSubtreeEnd(orderedHeadings, toIndex)
            val target = if (toIndex > fromIndex) {
                (toIndex - 1 downTo 0)
                    .firstOrNull { orderedHeadings[it].level == moving.level }
                    ?.let { orderedHeadings[it] }
            } else {
                (blockEnd until orderedHeadings.size)
                    .firstOrNull { orderedHeadings[it].level == moving.level }
                    ?.let { orderedHeadings[it] }
            }
            if (target == null || onMove?.invoke(moving, target, toIndex > fromIndex) != true) {
                orderedHeadings.clear()
                orderedHeadings.addAll(startOrder)
            }
        }
        dragStartOrder = null
        draggingHeadingStartOffset = null
    }
    val guideIndent = if (LocalKardLeafThemeStyle.current == PrefsManager.AppThemeStyle.CLASSIC) 16 else 12
    val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "目录",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = {
                        collapsedHeadingOffsets =
                            if (allHeadingsCollapsed) emptySet() else expandableHeadingOffsets
                    },
                    enabled = expandableHeadingOffsets.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = if (allHeadingsCollapsed) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess,
                        contentDescription = if (allHeadingsCollapsed) "全部展开" else "全部折叠",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        if (!outlineEditing) {
                            outlineEditing = true
                            if (!editorEditing) onEdit?.invoke()
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (outlineEditing) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(12.dp),
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_folder_navigation_edit),
                        contentDescription = "编辑目录",
                        tint = if (outlineEditing) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
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
                    state = listState,
                ) {
                    items(
                        items = orderedHeadings,
                        key = { it.startOffset },
                    ) { heading ->
                        val entry = OutlineHeadingEntry(
                            heading = heading,
                            depth = depthByOffset[heading.startOffset] ?: 0,
                            hasChildren = heading.startOffset in expandableHeadingOffsets,
                        )
                        ReorderableItem(
                            state = reorderableState,
                            key = heading.startOffset,
                            enabled = outlineEditing && editorEditing && onMove != null,
                        ) { isDragging ->
                            val elevation by animateDpAsState(
                                targetValue = if (isDragging) 8.dp else 0.dp,
                                label = "outlineHeadingElevation",
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDragging) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                } else {
                                    Color.Transparent
                                },
                                shadowElevation = elevation,
                            ) {
                                val dragHandleModifier = Modifier.longPressDraggableHandle(
                                    enabled = outlineEditing && editorEditing && onMove != null,
                                    onDragStarted = {
                                        if (dragStartOrder == null) {
                                            dragStartOrder = orderedHeadings.toList()
                                        }
                                        draggingHeadingStartOffset = heading.startOffset
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                )
                                OutlineHeadingRow(
                                    entry = entry,
                                    collapsedHeadingOffsets = collapsedHeadingOffsets,
                                    selectedHeadingStartOffset = selectedHeadingStartOffset,
                                    guideIndent = guideIndent,
                                    guideColor = guideColor,
                                    outlineEditing = outlineEditing,
                                    editorEditing = editorEditing,
                                    onHeadingClick = onHeadingClick,
                                    onToggleHeading = { currentHeading ->
                                        collapsedHeadingOffsets =
                                            if (currentHeading.startOffset in collapsedHeadingOffsets) {
                                                collapsedHeadingOffsets - currentHeading.startOffset
                                            } else {
                                                collapsedHeadingOffsets + currentHeading.startOffset
                                            }
                                    },
                                    dragHandleModifier = dragHandleModifier,
                                    onEditHeading = { currentHeading ->
                                        editingHeading = currentHeading
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editingHeading?.let { heading ->
        var name by remember(heading.startOffset) { mutableStateOf(heading.text) }
        val trimmedName = name.trim()
        AlertDialog(
            onDismissRequest = { editingHeading = null },
            title = { Text("编辑标题") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("标题名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = trimmedName.isNotBlank() && !trimmedName.contains('\n') && !trimmedName.contains('\r'),
                    onClick = {
                        onRename?.invoke(heading, trimmedName)
                        editingHeading = null
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        enabled = onDelete != null,
                        onClick = {
                            onDelete?.invoke(heading)
                            editingHeading = null
                        },
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { editingHeading = null }) {
                        Text("取消")
                    }
                }
            },
        )
    }
}

@Composable
internal fun NoteRemarkSidePanel(
    frontMatterProperties: List<NoteFormatUtils.FrontMatterProperty>,
    textStats: NoteTextStats?,
    outgoingLinks: List<NoteLinkEntity> = emptyList(),
    backlinkLinks: List<NoteLinkEntity> = emptyList(),
    onLinkClick: (String) -> Unit = {},
    remarks: List<NoteRemark>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onUpdate: (NoteRemark, String) -> Unit,
    onDelete: (NoteRemark) -> Unit,
    onTimeChange: (String, Long) -> Unit = { _, _ -> },
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
            NoteFrontMatterPropertiesCard(frontMatterProperties, onTimeChange)
            NoteWikilinkSummary(
                outgoingLinks = outgoingLinks,
                backlinkLinks = backlinkLinks,
                onLinkClick = onLinkClick,
            )
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
private fun NoteWikilinkSummary(
    outgoingLinks: List<NoteLinkEntity>,
    backlinkLinks: List<NoteLinkEntity>,
    onLinkClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "双链",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "出链 ${outgoingLinks.size}    反链 ${backlinkLinks.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        outgoingLinks.take(4).forEach { link ->
            val path = link.targetPath
            NoteWikilinkRow(
                label = link.alias?.takeIf { it.isNotBlank() } ?: link.targetRaw,
                path = path ?: "未解析",
                status = link.resolutionStatus,
                enabled = !path.isNullOrBlank(),
                onClick = { path?.let(onLinkClick) },
            )
        }
        backlinkLinks.take(4).forEach { link ->
            NoteWikilinkRow(
                label = "反链",
                path = link.sourcePath,
                status = NoteLinkResolutionStatus.RESOLVED,
                enabled = link.sourcePath.isNotBlank(),
                onClick = { onLinkClick(link.sourcePath) },
            )
        }
    }
}

@Composable
private fun NoteWikilinkRow(
    label: String,
    path: String,
    status: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val statusText = when (status) {
        NoteLinkResolutionStatus.RESOLVED -> ""
        NoteLinkResolutionStatus.UNRESOLVED -> " · 未解析"
        NoteLinkResolutionStatus.AMBIGUOUS -> " · 歧义"
        else -> ""
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = "$label$statusText",
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = path,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    onClick: () -> Unit = {},
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
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    clipboard.setText(AnnotatedString(value))
                    context.showToast("已复制$value")
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
private fun NoteFrontMatterPropertiesCard(
    properties: List<NoteFormatUtils.FrontMatterProperty>,
    onTimeChange: (String, Long) -> Unit,
) {
    val visibleProperties = remember(properties) {
        properties.filterNot { property ->
            property.key.equals(NoteFormatUtils.SOURCE_TYPE_KEY, ignoreCase = true) ||
                property.key.equals(NoteFormatUtils.SOURCE_URL_KEY, ignoreCase = true)
        }
    }
    if (visibleProperties.isEmpty()) return
    var editingKey by remember(properties) { mutableStateOf<String?>(null) }

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
        visibleProperties.forEach { property ->
            val isCreated = property.key.equals("created", ignoreCase = true)
            CopyableInfoRow(
                label = frontMatterDisplayName(property.key),
                value = frontMatterDisplayValue(property),
                labelWeight = 0.28f,
                valueWeight = 0.72f,
                onClick = { if (isCreated) editingKey = property.key },
            )
        }
    }

    visibleProperties.firstOrNull {
        it.key.equals(editingKey, ignoreCase = true) && it.key.equals("created", ignoreCase = true)
    }?.let { property ->
        NoteTimestampPickerDialog(
            initialTimestamp = property.values.firstOrNull()?.let(NoteFormatUtils::parseYamlDateTime)
                ?: System.currentTimeMillis(),
            title = "修改创建时间",
            onDismiss = { editingKey = null },
            onConfirm = { timestamp ->
                onTimeChange(property.key, timestamp)
                editingKey = null
            },
        )
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
        "updated" -> "修改时间"
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
