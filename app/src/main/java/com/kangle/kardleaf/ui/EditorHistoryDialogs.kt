package com.kangle.kardleaf.ui

import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteHistory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun NoteInfoDialog(
    title: String,
    content: String,
    allNotes: List<Note>,
    onDismiss: () -> Unit,
    onHeadingClick: (MarkdownHeading) -> Unit = {},
) {
    val headings = remember(content) { extractMarkdownHeadings(content) }
    val links = remember(content) { extractObsidianLinks(content) }
    val tags = remember(content) { extractObsidianTags(content) }
    val backlinks = remember(allNotes, title) {
        allNotes.filter { note ->
            note.title != title && extractObsidianLinks(note.content).any { noteMatchesObsidianTarget(Note(File("", title), title, "", Date(), color = 0), it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("笔记信息") },
        text = {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    NoteInfoSection("大纲") {
                        if (headings.isEmpty()) {
                            Text("暂无标题", style = MaterialTheme.typography.bodySmall)
                        } else {
                            headings.forEach { heading ->
                                Text(
                                    text = "${"  ".repeat((heading.level - 1).coerceAtLeast(0))}${heading.text}",
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onHeadingClick(heading) }
                                            .padding(vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                item {
                    NoteInfoSection("标签") {
                        if (tags.isEmpty()) {
                            Text("暂无标签", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(tags.joinToString(" ") { "#$it" }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    NoteInfoSection("出链") {
                        if (links.isEmpty()) {
                            Text("暂无双链", style = MaterialTheme.typography.bodySmall)
                        } else {
                            links.forEach { Text("[[$it]]", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                item {
                    NoteInfoSection("反向链接") {
                        if (backlinks.isEmpty()) {
                            Text("暂无反向链接", style = MaterialTheme.typography.bodySmall)
                        } else {
                            backlinks.forEach { note ->
                                Text(note.title, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
    )
}

@Composable
private fun NoteInfoSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun NoteHistoryDialog(
    histories: List<NoteHistory>,
    currentContent: String,
    onDismiss: () -> Unit,
    onRestore: (NoteHistory) -> Unit,
    onDelete: (NoteHistory) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val context = LocalContext.current
    val versions = remember(histories, currentContent) {
        buildHistoryVersionItems(
            histories = histories,
            currentContent = currentContent,
            dateFormat = dateFormat,
            timeFormat = timeFormat,
        )
    }
    var selectedKey by remember { mutableStateOf(HistoryVersionItem.CURRENT_KEY) }
    var query by remember { mutableStateOf("") }
    var showCompare by remember { mutableStateOf(false) }
    var compareMode by remember { mutableStateOf(HistoryCompareMode.CHANGES) }
    val selected = versions.firstOrNull { it.key == selectedKey } ?: versions.first()
    val selectedDiffModel = remember(selected.content, currentContent, selected.current) {
        if (selected.current) {
            HistoryDiffModel.empty()
        } else {
            buildHistoryDiffModel(oldContent = selected.content, newContent = currentContent)
        }
    }
    val filteredVersions = remember(versions, query) {
        if (query.isBlank()) {
            versions
        } else {
            versions.filter { version ->
                listOf(version.title, version.meta, version.badge, version.content)
                    .any { it.contains(query, ignoreCase = true) }
            }
        }
    }

    BackHandler {
        if (showCompare) {
            showCompare = false
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (showCompare) {
                showCompare = false
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = HistoryUiColors.PageBackground,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HistoryUiColors.PageBackground),
            ) {
                if (showCompare) {
                    HistoryComparePage(
                        selected = selected,
                        currentContent = currentContent,
                        diffModel = selectedDiffModel,
                        compareMode = compareMode,
                        onCompareModeChange = { compareMode = it },
                        onBack = { showCompare = false },
                        onDone = { showCompare = false },
                        onRestore = {
                            selected.history?.let(onRestore)
                                ?: android.widget.Toast
                                    .makeText(context, "当前版本无需恢复", android.widget.Toast.LENGTH_SHORT)
                                    .show()
                        },
                    )
                } else {
                    HistoryListPage(
                        versions = versions,
                        filteredVersions = filteredVersions,
                        selected = selected,
                        currentContent = currentContent,
                        diffModel = selectedDiffModel,
                        query = query,
                        onQueryChange = { query = it },
                        onClearQuery = { query = "" },
                        onSelected = { selectedKey = it.key },
                        onBack = onDismiss,
                        onDone = onDismiss,
                        onOpenCompare = {
                            compareMode = HistoryCompareMode.CHANGES
                            showCompare = true
                        },
                        onRestore = {
                            selected.history?.let(onRestore)
                                ?: android.widget.Toast
                                    .makeText(context, "当前版本无需恢复", android.widget.Toast.LENGTH_SHORT)
                                    .show()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryListPage(
    versions: List<HistoryVersionItem>,
    filteredVersions: List<HistoryVersionItem>,
    selected: HistoryVersionItem,
    currentContent: String,
    diffModel: HistoryDiffModel,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSelected: (HistoryVersionItem) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenCompare: () -> Unit,
    onRestore: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HistoryTopBar(
                title = "历史版本",
                subtitle = "当前笔记 · ${versions.size} 个版本",
                onBack = onBack,
                onDone = onDone,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 92.dp),
            ) {
                item {
                    HistorySearchRow(
                        query = query,
                        onQueryChange = onQueryChange,
                        onClearQuery = onClearQuery,
                    )
                }
                item {
                    CompareSourceStrip(
                        selected = selected,
                        currentContent = currentContent,
                        diffModel = diffModel,
                    )
                }
                item {
                    SelectedVersionPanel(selected = selected)
                }
                item {
                    VersionListHeader(count = filteredVersions.size)
                }
                items(filteredVersions, key = { it.key }) { version ->
                    val isSelected = version.key == selected.key
                    VersionCard(
                        version = version,
                        selected = isSelected,
                        diffModel = if (isSelected) diffModel else null,
                        onClick = { onSelected(version) },
                    )
                }
            }
        }
        HistoryBottomActions(
            modifier = Modifier.align(Alignment.BottomCenter),
            restoreText = "恢复${selected.title}",
            restoreEnabled = !selected.current,
            onCompare = onOpenCompare,
            onRestore = onRestore,
        )
    }
}

@Composable
private fun HistoryComparePage(
    selected: HistoryVersionItem,
    currentContent: String,
    diffModel: HistoryDiffModel,
    compareMode: HistoryCompareMode,
    onCompareModeChange: (HistoryCompareMode) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onRestore: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HistoryTopBar(
                title = "版本对比",
                subtitle = "${selected.title} → 当前版本",
                onBack = onBack,
                onDone = onDone,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(selected.current, compareMode) {
                        if (!selected.current) {
                            var totalHorizontalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalHorizontalDrag = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    totalHorizontalDrag += dragAmount
                                },
                                onDragEnd = {
                                    val threshold = 72.dp.toPx()
                                    when {
                                        totalHorizontalDrag <= -threshold -> {
                                            onCompareModeChange(compareMode.shift(1))
                                        }
                                        totalHorizontalDrag >= threshold -> {
                                            onCompareModeChange(compareMode.shift(-1))
                                        }
                                    }
                                },
                                onDragCancel = { totalHorizontalDrag = 0f },
                            )
                        }
                    },
                contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 92.dp),
            ) {
                item {
                    CompareSourceStrip(
                        selected = selected,
                        currentContent = currentContent,
                        diffModel = diffModel,
                    )
                }
                item {
                    CompareModeSegment(
                        selected = compareMode,
                        onSelected = onCompareModeChange,
                    )
                }
                item {
                    if (selected.current) {
                        FoldLine("当前版本无需和自己对比，请返回列表选择一个历史版本")
                    } else {
                        CompareModeContent(
                            selected = selected,
                            currentContent = currentContent,
                            diffModel = diffModel,
                            compareMode = compareMode,
                        )
                    }
                }
            }
        }
        CompareBottomActions(
            modifier = Modifier.align(Alignment.BottomCenter),
            restoreText = "恢复${selected.title}",
            restoreEnabled = !selected.current,
            onBackToList = onBack,
            onRestore = onRestore,
        )
    }
}

@Composable
private fun HistoryTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(HistoryUiColors.TopBarBackground)
            .border(1.dp, HistoryUiColors.Border)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(HistoryUiColors.IconButtonBackground)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                color = HistoryUiColors.TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = HistoryUiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = HistoryUiColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(HistoryUiColors.DarkButton)
                .clickable(onClick = onDone)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "完成",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun HistorySearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = HistoryUiColors.TextPrimary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, HistoryUiColors.Border, RoundedCornerShape(16.dp))
                .padding(horizontal = 13.dp),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "搜索版本内容、保存时间或备注",
                                color = HistoryUiColors.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, HistoryUiColors.Border, RoundedCornerShape(16.dp))
                .clickable(onClick = onClearQuery),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "×",
                color = HistoryUiColors.TextTertiary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CompareSourceStrip(
    selected: HistoryVersionItem,
    currentContent: String,
    diffModel: HistoryDiffModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, HistoryUiColors.Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected.title,
                    color = HistoryUiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "→",
                    color = HistoryUiColors.TextMuted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "当前版本",
                    color = HistoryUiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SourceChip(text = "+${diffModel.addCount}", type = HistoryDiffType.ADD)
                SourceChip(text = "-${diffModel.removeCount}", type = HistoryDiffType.REMOVE)
                SourceChip(text = "${diffModel.changeCount} 改写", type = HistoryDiffType.CHANGE)
            }
        }
        Text(
            text = "${selected.sourceMeta} → 正在使用 · 约 ${currentContent.length} 字",
            color = HistoryUiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SourceChip(
    text: String,
    type: HistoryDiffType,
) {
    val colors = diffColors(type)
    Box(
        modifier = Modifier
            .height(20.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.background)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun SelectedVersionPanel(selected: HistoryVersionItem) {
    val previewScrollState = remember(selected.key) { androidx.compose.foundation.ScrollState(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, HistoryUiColors.Border, RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前选中：${selected.title}",
                    color = HistoryUiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = selected.meta,
                    color = HistoryUiColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 5.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            VersionBadge(
                text = if (selected.current) "当前" else "已选中",
                current = selected.current,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HistoryUiColors.PageBackground)
                .border(1.dp, HistoryUiColors.SoftBorder, RoundedCornerShape(16.dp))
                .verticalScroll(previewScrollState)
                .padding(12.dp),
        ) {
            Text(
                text = selected.content.ifBlank { "空内容" },
                color = HistoryUiColors.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun VersionListHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 3.dp, top = 4.dp, end = 3.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "版本列表",
            color = HistoryUiColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "$count 个",
            color = HistoryUiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun VersionCard(
    version: HistoryVersionItem,
    selected: Boolean,
    diffModel: HistoryDiffModel?,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else HistoryUiColors.Border
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = version.title,
                    color = HistoryUiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = version.meta,
                    color = HistoryUiColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 5.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            VersionBadge(
                text = if (version.current) "当前" else version.badge,
                current = version.current,
            )
        }
        Text(
            text = version.content.ifBlank { "空内容" }.replace('\n', ' '),
            color = HistoryUiColors.TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 10.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected && diffModel != null && !version.current) {
            MiniDiffChips(diffModel = diffModel)
        }
    }
}

@Composable
private fun MiniDiffChips(diffModel: HistoryDiffModel) {
    Row(
        modifier = Modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SourceChip(text = "+${diffModel.addCount}", type = HistoryDiffType.ADD)
        SourceChip(text = "-${diffModel.removeCount}", type = HistoryDiffType.REMOVE)
        SourceChip(text = "${diffModel.changeCount} 改写", type = HistoryDiffType.CHANGE)
    }
}

@Composable
private fun VersionBadge(
    text: String,
    current: Boolean,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else HistoryUiColors.NeutralPill)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (current) MaterialTheme.colorScheme.primary else HistoryUiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun HistoryBottomActions(
    modifier: Modifier = Modifier,
    restoreText: String,
    restoreEnabled: Boolean,
    onCompare: () -> Unit,
    onRestore: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HistoryUiColors.TopBarBackground)
            .border(1.dp, HistoryUiColors.Border)
            .navigationBarsPadding()
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HistoryActionButton(
            text = "查看对比",
            background = HistoryUiColors.DarkButton,
            contentColor = Color.White,
            onClick = onCompare,
            modifier = Modifier.weight(1f),
        )
        HistoryActionButton(
            text = restoreText,
            background = if (restoreEnabled) MaterialTheme.colorScheme.primary else HistoryUiColors.DisabledButton,
            contentColor = if (restoreEnabled) Color.White else HistoryUiColors.TextSecondary,
            enabled = restoreEnabled,
            onClick = onRestore,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompareBottomActions(
    modifier: Modifier = Modifier,
    restoreText: String,
    restoreEnabled: Boolean,
    onBackToList: () -> Unit,
    onRestore: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HistoryUiColors.TopBarBackground)
            .border(1.dp, HistoryUiColors.Border)
            .navigationBarsPadding()
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HistoryActionButton(
            text = "返回列表",
            background = HistoryUiColors.IconButtonBackground,
            contentColor = HistoryUiColors.TextPrimary,
            onClick = onBackToList,
            modifier = Modifier.weight(1f),
        )
        HistoryActionButton(
            text = restoreText,
            background = if (restoreEnabled) MaterialTheme.colorScheme.primary else HistoryUiColors.DisabledButton,
            contentColor = if (restoreEnabled) Color.White else HistoryUiColors.TextSecondary,
            enabled = restoreEnabled,
            onClick = onRestore,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HistoryActionButton(
    text: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompareModeSegment(
    selected: HistoryCompareMode,
    onSelected: (HistoryCompareMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White)
            .border(1.dp, HistoryUiColors.Border, RoundedCornerShape(15.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        HistoryCompareMode.entries.forEach { mode ->
            val active = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) HistoryUiColors.DarkButton else Color.Transparent)
                    .clickable { onSelected(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.label,
                    color = if (active) Color.White else HistoryUiColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun CompareModeContent(
    selected: HistoryVersionItem,
    currentContent: String,
    diffModel: HistoryDiffModel,
    compareMode: HistoryCompareMode,
) {
    AnimatedContent(
        targetState = compareMode,
        transitionSpec = {
            val forward = HistoryCompareMode.entries.indexOf(targetState) >=
                HistoryCompareMode.entries.indexOf(initialState)
            (slideInHorizontally(animationSpec = tween(180)) { fullWidth ->
                if (forward) fullWidth / 3 else -fullWidth / 3
            } + fadeIn(animationSpec = tween(180))) togetherWith
                (slideOutHorizontally(animationSpec = tween(180)) { fullWidth ->
                    if (forward) -fullWidth / 3 else fullWidth / 3
                } + fadeOut(animationSpec = tween(120)))
        },
        label = "HistoryCompareModeContent",
    ) { mode ->
        when (mode) {
            HistoryCompareMode.CHANGES -> ChangesModePanel(diffModel = diffModel)
            HistoryCompareMode.FULL -> FullModePanel(diffModel = diffModel)
            HistoryCompareMode.SPLIT -> SplitModePanel(
                selected = selected,
                currentContent = currentContent,
                diffModel = diffModel,
            )
        }
    }
}

@Composable
private fun ChangesModePanel(diffModel: HistoryDiffModel) {
    Column {
        SectionTitle(title = "正文变化", trailing = "未变化内容已折叠")
        if (diffModel.groups.isEmpty()) {
            FoldLine("两个版本正文没有差异")
        } else {
            diffModel.groups.forEach { group ->
                DiffGroupCard(group = group)
            }
            FoldLine("未变化正文默认不占空间，只在需要时展开查看")
        }
    }
}

@Composable
private fun DiffGroupCard(group: HistoryDiffGroup) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, HistoryUiColors.Border, RoundedCornerShape(20.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HistoryUiColors.PageBackground)
                .border(1.dp, HistoryUiColors.SoftBorder)
                .padding(horizontal = 11.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    color = HistoryUiColors.TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = group.subtitle,
                    color = HistoryUiColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HistoryDiffPill(type = group.type)
        }
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (group.type == HistoryDiffType.CHANGE) {
                group.rows.forEach { row -> RewriteBox(row = row) }
            } else {
                group.rows.forEach { row -> DiffLineRow(row = row, showSameBackground = false) }
            }
        }
    }
}

@Composable
private fun RewriteBox(row: HistoryDiffDisplayRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HistoryUiColors.YellowBackground)
            .border(1.dp, HistoryUiColors.YellowBorder, RoundedCornerShape(14.dp)),
    ) {
        RewriteRow(label = "旧", text = row.oldText.orEmpty(), old = true)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HistoryUiColors.YellowBorder.copy(alpha = 0.9f)),
        )
        RewriteRow(label = "新", text = row.newText.orEmpty(), old = false)
    }
}

@Composable
private fun RewriteRow(
    label: String,
    text: String,
    old: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = HistoryUiColors.YellowText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.size(width = 34.dp, height = 18.dp),
        )
        Text(
            text = text.ifBlank { "空行" },
            color = if (old) HistoryUiColors.RedText else HistoryUiColors.GreenText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (old) FontWeight.Normal else FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DiffLineRow(
    row: HistoryDiffDisplayRow,
    showSameBackground: Boolean,
) {
    val colors = diffColors(row.type)
    val mark = when (row.type) {
        HistoryDiffType.ADD -> "+"
        HistoryDiffType.REMOVE -> "−"
        HistoryDiffType.CHANGE -> "~"
        HistoryDiffType.SAME -> ""
    }
    val lineNumber = when (row.type) {
        HistoryDiffType.ADD -> row.newLineNumber
        HistoryDiffType.REMOVE -> row.oldLineNumber
        HistoryDiffType.CHANGE -> row.oldLineNumber ?: row.newLineNumber
        HistoryDiffType.SAME -> row.oldLineNumber ?: row.newLineNumber
    }
    val content = when (row.type) {
        HistoryDiffType.CHANGE -> "${row.oldText.orEmpty()} → ${row.newText.orEmpty()}"
        else -> row.newText ?: row.oldText.orEmpty()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (row.type == HistoryDiffType.SAME && !showSameBackground) Color.Transparent else colors.background)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = lineNumber?.toString().orEmpty(),
            color = HistoryUiColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            modifier = Modifier.size(width = 30.dp, height = 20.dp),
        )
        Text(
            text = mark,
            color = colors.content,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.size(width = 18.dp, height = 20.dp),
        )
        Text(
            text = content.ifBlank { "空行" },
            color = colors.content,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FoldLine(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(HistoryUiColors.PageBackground)
            .border(1.dp, HistoryUiColors.Border, RoundedCornerShape(11.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = HistoryUiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FullModePanel(diffModel: HistoryDiffModel) {
    Column {
        SectionTitle(title = "完整正文", trailing = "~ 改写，+ 新增，− 删除")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .border(1.dp, HistoryUiColors.Border, RoundedCornerShape(20.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HistoryUiColors.PageBackground)
                    .border(1.dp, HistoryUiColors.SoftBorder)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "当前版本 · 合并显示",
                    color = HistoryUiColors.TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                VersionBadge(text = "推荐手机端", current = true)
            }
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                diffModel.displayRows.forEach { row ->
                    DiffLineRow(row = row, showSameBackground = true)
                }
            }
        }
    }
}

@Composable
private fun SplitModePanel(
    selected: HistoryVersionItem,
    currentContent: String,
    diffModel: HistoryDiffModel,
) {
    Column {
        SectionTitle(title = "并排对比", trailing = "短文本可用")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SplitTextCard(
                title = "旧版本 · ${selected.title}",
                rows = diffModel.displayRows,
                oldSide = true,
                fallbackText = selected.content,
                modifier = Modifier.weight(1f),
            )
            SplitTextCard(
                title = "新版本 · 当前版本",
                rows = diffModel.displayRows,
                oldSide = false,
                fallbackText = currentContent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SplitTextCard(
    title: String,
    rows: List<HistoryDiffDisplayRow>,
    oldSide: Boolean,
    fallbackText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, HistoryUiColors.Border, RoundedCornerShape(18.dp)),
    ) {
        Text(
            text = title,
            color = HistoryUiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .fillMaxWidth()
                .background(HistoryUiColors.PageBackground)
                .border(1.dp, HistoryUiColors.SoftBorder)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (rows.isEmpty()) {
                Text(
                    text = fallbackText.ifBlank { "空内容" },
                    color = HistoryUiColors.TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(10.dp),
                )
            } else {
                rows.forEach { row ->
                    val text = if (oldSide) row.oldText else row.newText
                    if (text != null) {
                        val type = when {
                            row.type == HistoryDiffType.CHANGE -> HistoryDiffType.CHANGE
                            oldSide && row.type == HistoryDiffType.REMOVE -> HistoryDiffType.REMOVE
                            !oldSide && row.type == HistoryDiffType.ADD -> HistoryDiffType.ADD
                            else -> HistoryDiffType.SAME
                        }
                        SplitLine(text = text, type = type)
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitLine(
    text: String,
    type: HistoryDiffType,
) {
    val colors = diffColors(type)
    Text(
        text = text.ifBlank { "空行" },
        color = colors.content,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (type == HistoryDiffType.SAME) Color.Transparent else colors.background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun SectionTitle(
    title: String,
    trailing: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 3.dp, top = 4.dp, end = 3.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = HistoryUiColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = trailing,
            color = HistoryUiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HistoryDiffPill(type: HistoryDiffType) {
    val colors = diffColors(type)
    val text = when (type) {
        HistoryDiffType.ADD -> "新增"
        HistoryDiffType.REMOVE -> "删除"
        HistoryDiffType.CHANGE -> "改写"
        HistoryDiffType.SAME -> "未变"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.background)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

private fun buildHistoryVersionItems(
    histories: List<NoteHistory>,
    currentContent: String,
    dateFormat: SimpleDateFormat,
    timeFormat: SimpleDateFormat,
): List<HistoryVersionItem> {
    val current = HistoryVersionItem(
        key = HistoryVersionItem.CURRENT_KEY,
        title = "当前版本",
        meta = "正在使用 · 约 ${currentContent.length} 字",
        sourceMeta = "正在使用 · 约 ${currentContent.length} 字",
        badge = "当前",
        content = currentContent,
        current = true,
        history = null,
    )
    val historyItems = histories.mapIndexed { index, history ->
        val versionNumber = histories.size - index
        HistoryVersionItem(
            key = "history-${history.id}",
            title = "版本 $versionNumber",
            meta = "${dateFormat.format(history.savedAt)} · 历史保存 · 约 ${history.content.length} 字",
            sourceMeta = "${timeFormat.format(history.savedAt)} 保存 · 约 ${history.content.length} 字",
            badge = "可对比",
            content = history.content,
            current = false,
            history = history,
        )
    }
    return listOf(current) + historyItems
}

private fun buildHistoryDiffModel(
    oldContent: String,
    newContent: String,
): HistoryDiffModel {
    val ops = buildLineDiffOps(oldContent.lines(), newContent.lines())
    val displayRows = compactLineDiffOps(ops)
    return HistoryDiffModel(
        groups = buildHistoryDiffGroups(displayRows),
        displayRows = displayRows,
        addCount = displayRows.count { it.type == HistoryDiffType.ADD },
        removeCount = displayRows.count { it.type == HistoryDiffType.REMOVE },
        changeCount = displayRows.count { it.type == HistoryDiffType.CHANGE },
    )
}

private fun compactLineDiffOps(ops: List<LineDiffOp>): List<HistoryDiffDisplayRow> {
    val rows = mutableListOf<HistoryDiffDisplayRow>()
    var index = 0
    while (index < ops.size) {
        val op = ops[index]
        if (op.type == LineDiffType.SAME) {
            rows += HistoryDiffDisplayRow(
                type = HistoryDiffType.SAME,
                oldText = op.line,
                newText = op.line,
                oldLineNumber = op.oldLineNumber,
                newLineNumber = op.newLineNumber,
            )
            index++
        } else {
            val chunk = mutableListOf<LineDiffOp>()
            while (index < ops.size && ops[index].type != LineDiffType.SAME) {
                chunk += ops[index]
                index++
            }
            val deleted = chunk.filter { it.type == LineDiffType.DELETED }
            val added = chunk.filter { it.type == LineDiffType.ADDED }
            val pairCount = minOf(deleted.size, added.size)
            repeat(pairCount) { pairIndex ->
                val old = deleted[pairIndex]
                val new = added[pairIndex]
                rows += HistoryDiffDisplayRow(
                    type = HistoryDiffType.CHANGE,
                    oldText = old.line,
                    newText = new.line,
                    oldLineNumber = old.oldLineNumber,
                    newLineNumber = new.newLineNumber,
                )
            }
            deleted.drop(pairCount).forEach { deletedOp ->
                rows += HistoryDiffDisplayRow(
                    type = HistoryDiffType.REMOVE,
                    oldText = deletedOp.line,
                    newText = null,
                    oldLineNumber = deletedOp.oldLineNumber,
                    newLineNumber = null,
                )
            }
            added.drop(pairCount).forEach { addedOp ->
                rows += HistoryDiffDisplayRow(
                    type = HistoryDiffType.ADD,
                    oldText = null,
                    newText = addedOp.line,
                    oldLineNumber = null,
                    newLineNumber = addedOp.newLineNumber,
                )
            }
        }
    }
    return rows
}

private fun buildHistoryDiffGroups(rows: List<HistoryDiffDisplayRow>): List<HistoryDiffGroup> {
    val groups = mutableListOf<HistoryDiffGroup>()
    var index = 0
    while (index < rows.size) {
        val row = rows[index]
        if (row.type == HistoryDiffType.SAME) {
            index++
            continue
        }
        val sameTypeRows = mutableListOf<HistoryDiffDisplayRow>()
        val type = row.type
        while (index < rows.size && rows[index].type == type) {
            sameTypeRows += rows[index]
            index++
        }
        val lineNumber = sameTypeRows.firstOrNull()?.oldLineNumber ?: sameTypeRows.firstOrNull()?.newLineNumber ?: 1
        val title = when (type) {
            HistoryDiffType.CHANGE -> "正文 · 第 $lineNumber 行"
            HistoryDiffType.ADD -> "正文 · 新增内容"
            HistoryDiffType.REMOVE -> "正文 · 已删除内容"
            HistoryDiffType.SAME -> "正文 · 未变化"
        }
        val subtitle = when (type) {
            HistoryDiffType.CHANGE -> if (sameTypeRows.size == 1) "这就是上面统计的“1 处改写”" else "旧内容已改写为新内容"
            HistoryDiffType.ADD -> "当前版本新增了 ${sameTypeRows.size} 行"
            HistoryDiffType.REMOVE -> "旧版本存在，当前版本已删除"
            HistoryDiffType.SAME -> "未变化内容"
        }
        groups += HistoryDiffGroup(
            type = type,
            title = title,
            subtitle = subtitle,
            rows = sameTypeRows,
        )
    }
    return groups
}

private fun diffColors(type: HistoryDiffType): DiffBlockColors =
    when (type) {
        HistoryDiffType.ADD -> DiffBlockColors(
            background = HistoryUiColors.GreenBackground,
            border = HistoryUiColors.GreenBorder,
            content = HistoryUiColors.GreenText,
        )
        HistoryDiffType.REMOVE -> DiffBlockColors(
            background = HistoryUiColors.RedBackground,
            border = HistoryUiColors.RedBorder,
            content = HistoryUiColors.RedText,
        )
        HistoryDiffType.CHANGE -> DiffBlockColors(
            background = HistoryUiColors.YellowBackground,
            border = HistoryUiColors.YellowBorder,
            content = HistoryUiColors.YellowText,
        )
        HistoryDiffType.SAME -> DiffBlockColors(
            background = Color.White,
            border = HistoryUiColors.Border,
            content = HistoryUiColors.TextTertiary,
        )
    }

private object HistoryUiColors {
    val PageBackground = Color(0xFFF8FAFC)
    val TopBarBackground = Color(0xF0F8FAFC)
    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF64748B)
    val TextTertiary = Color(0xFF475569)
    val TextMuted = Color(0xFF94A3B8)
    val Border = Color(0xFFE2E8F0)
    val SoftBorder = Color(0xFFEEF2F7)
    val IconButtonBackground = Color(0xFFF1F5F9)
    val DarkButton = Color(0xFF111827)
    val NeutralPill = Color(0xFFF1F5F9)
    val DisabledButton = Color(0xFFCBD5E1)
    val GreenBackground = Color(0xFFECFDF5)
    val GreenBorder = Color(0xFFBBF7D0)
    val GreenText = Color(0xFF047857)
    val RedBackground = Color(0xFFFFF1F2)
    val RedBorder = Color(0xFFFECDD3)
    val RedText = Color(0xFFBE123C)
    val YellowBackground = Color(0xFFFFFBEB)
    val YellowBorder = Color(0xFFFDE68A)
    val YellowText = Color(0xFF92400E)
}

private data class HistoryVersionItem(
    val key: String,
    val title: String,
    val meta: String,
    val sourceMeta: String,
    val badge: String,
    val content: String,
    val current: Boolean,
    val history: NoteHistory?,
) {
    companion object {
        const val CURRENT_KEY = "current"
    }
}

private enum class HistoryCompareMode(val label: String) {
    CHANGES("只看改动"),
    FULL("完整正文"),
    SPLIT("并排对比"),
}

private fun HistoryCompareMode.shift(offset: Int): HistoryCompareMode {
    val modes = HistoryCompareMode.entries
    val targetIndex = (modes.indexOf(this) + offset).coerceIn(0, modes.lastIndex)
    return modes[targetIndex]
}

private enum class HistoryDiffType {
    ADD,
    REMOVE,
    CHANGE,
    SAME,
}

private data class HistoryDiffModel(
    val groups: List<HistoryDiffGroup>,
    val displayRows: List<HistoryDiffDisplayRow>,
    val addCount: Int,
    val removeCount: Int,
    val changeCount: Int,
) {
    companion object {
        fun empty(): HistoryDiffModel =
            HistoryDiffModel(
                groups = emptyList(),
                displayRows = emptyList(),
                addCount = 0,
                removeCount = 0,
                changeCount = 0,
            )
    }
}

private data class HistoryDiffGroup(
    val type: HistoryDiffType,
    val title: String,
    val subtitle: String,
    val rows: List<HistoryDiffDisplayRow>,
)

private data class HistoryDiffDisplayRow(
    val type: HistoryDiffType,
    val oldText: String?,
    val newText: String?,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
)

private data class DiffBlockColors(
    val background: Color,
    val border: Color,
    val content: Color,
)

private enum class LineDiffType {
    SAME,
    DELETED,
    ADDED,
}

private data class LineDiffOp(
    val type: LineDiffType,
    val line: String,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
)

private fun buildLineDiffOps(
    oldLines: List<String>,
    newLines: List<String>,
): List<LineDiffOp> {
    val rows = oldLines.size
    val cols = newLines.size
    val dp = Array(rows + 1) { IntArray(cols + 1) }
    for (i in rows - 1 downTo 0) {
        for (j in cols - 1 downTo 0) {
            dp[i][j] =
                if (oldLines[i] == newLines[j]) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
        }
    }

    val ops = mutableListOf<LineDiffOp>()
    var i = 0
    var j = 0
    while (i < rows && j < cols) {
        when {
            oldLines[i] == newLines[j] -> {
                ops += LineDiffOp(LineDiffType.SAME, oldLines[i], i + 1, j + 1)
                i++
                j++
            }
            dp[i + 1][j] >= dp[i][j + 1] -> {
                ops += LineDiffOp(LineDiffType.DELETED, oldLines[i], i + 1, null)
                i++
            }
            else -> {
                ops += LineDiffOp(LineDiffType.ADDED, newLines[j], null, j + 1)
                j++
            }
        }
    }
    while (i < rows) {
        ops += LineDiffOp(LineDiffType.DELETED, oldLines[i], i + 1, null)
        i++
    }
    while (j < cols) {
        ops += LineDiffOp(LineDiffType.ADDED, newLines[j], null, j + 1)
        j++
    }
    return ops
}
