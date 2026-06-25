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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteHistory
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val headings = remember(content) { extractHistoryMarkdownHeadings(content) }
    val links = remember(content) { extractHistoryObsidianLinks(content) }
    val tags = remember(content) { extractHistoryObsidianTags(content) }
    val backlinks = remember(allNotes, title) {
        allNotes.filter { note ->
            note.title != title && extractHistoryObsidianLinks(note.content).any { target -> historyNoteMatchesObsidianTarget(Note(File("", title), title, "", Date(), color = 0), target) }
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

private val historyHeadingRegex = Regex("""^(#{1,6})\s+(.+?)\s*#*\s*$""")
private val historyWikiLinkRegex = Regex("""!?\[\[([^\]]+)]]""")
private val historyTagRegex = Regex("""(?<![\w/])#([A-Za-z0-9_\-/\u4e00-\u9fa5]+)""")

private fun extractHistoryMarkdownHeadings(content: String): List<MarkdownHeading> {
    val headings = mutableListOf<MarkdownHeading>()
    var offset = 0
    var lineIndex = 0
    while (offset <= content.length) {
        val newlineIndex = content.indexOfAny(charArrayOf('\n', '\r'), startIndex = offset)
        val lineEnd = if (newlineIndex >= 0) newlineIndex else content.length
        val line = content.substring(offset, lineEnd)
        val leadingWhitespace = line.length - line.trimStart().length
        val match = historyHeadingRegex.find(line.trim())
        if (match != null) {
            headings += MarkdownHeading(
                level = match.groupValues[1].length,
                text = match.groupValues[2].trim(),
                startOffset = offset + leadingWhitespace,
                lineIndex = lineIndex,
            )
        }
        if (newlineIndex < 0) break
        offset = if (content[newlineIndex] == '\r' && content.getOrNull(newlineIndex + 1) == '\n') {
            newlineIndex + 2
        } else {
            newlineIndex + 1
        }
        lineIndex++
    }
    return headings
}

private fun extractHistoryObsidianLinks(content: String): List<String> =
    historyWikiLinkRegex.findAll(content)
        .map { match ->
            match.groupValues[1]
                .substringBefore("|")
                .substringBefore("#")
                .substringBefore("^")
                .trim()
        }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

private fun extractHistoryObsidianTags(content: String): List<String> =
    historyTagRegex.findAll(content)
        .map { it.groupValues[1].trim('/') }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

private fun historyNoteMatchesObsidianTarget(
    note: Note,
    target: String,
): Boolean {
    val normalizedTarget = normalizeHistoryObsidianName(target)
    if (normalizedTarget.isBlank()) return false
    return normalizeHistoryObsidianName(note.title) == normalizedTarget ||
        normalizeHistoryObsidianName(note.file.nameWithoutExtension) == normalizedTarget ||
        normalizeHistoryObsidianName(note.file.path.replace("\\", "/").removeSuffix(".md")).endsWith("/$normalizedTarget")
}

private fun normalizeHistoryObsidianName(value: String): String =
    value
        .replace("\\", "/")
        .substringAfterLast("/")
        .removeSuffix(".md")
        .trim()
        .lowercase(Locale.getDefault())

private const val HISTORY_DIALOG_LIGHTWEIGHT_CHAR_LIMIT = 80_000
private const val HISTORY_DIALOG_PREVIEW_CHAR_LIMIT = 200
private const val HISTORY_DIALOG_DIFF_LINE_LIMIT = 3_000

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
    val currentContentPreviewKey = currentContent.take(HISTORY_DIALOG_PREVIEW_CHAR_LIMIT)
    val versions = remember(histories, currentContent.length, currentContentPreviewKey) {
        buildHistoryVersionItems(
            histories = histories,
            currentContent = currentContent,
            dateFormat = dateFormat,
            timeFormat = timeFormat,
        )
    }
    val versionKeySignature = remember(versions) { versions.joinToString("|") { it.key } }
    val defaultLeftKey = versions.firstOrNull { !it.current }?.key ?: HistoryVersionItem.CURRENT_KEY
    var leftKey by remember(versionKeySignature) { mutableStateOf(defaultLeftKey) }
    var rightKey by remember(versionKeySignature) { mutableStateOf(HistoryVersionItem.CURRENT_KEY) }
    var query by remember { mutableStateOf("") }
    var showCompare by remember { mutableStateOf(false) }
    var compareMode by remember { mutableStateOf(HistoryCompareMode.CHANGES) }
    var expandedPicker by remember { mutableStateOf<HistoryCompareSide?>(null) }
    val leftVersion = versions.firstOrNull { it.key == leftKey }
        ?: versions.firstOrNull { !it.current }
        ?: versions.first()
    val rightVersion = versions.firstOrNull { it.key == rightKey } ?: versions.first()
    val compareEnabled = remember(
        leftVersion.key,
        rightVersion.key,
        leftVersion.contentIsPreview,
        rightVersion.contentIsPreview,
        leftVersion.contentLength,
        rightVersion.contentLength,
    ) {
        leftVersion.key != rightVersion.key &&
            !leftVersion.contentIsPreview &&
            !rightVersion.contentIsPreview &&
            canBuildHistoryDiff(leftVersion.content, rightVersion.content)
    }
    val leftContentDiffKey = if (compareEnabled) leftVersion.content else ""
    val rightContentDiffKey = if (compareEnabled) rightVersion.content else ""
    val diffModel = remember(compareEnabled, leftVersion.key, rightVersion.key, leftContentDiffKey, rightContentDiffKey) {
        if (compareEnabled) {
            buildHistoryDiffModel(oldContent = leftVersion.content, newContent = rightVersion.content)
        } else {
            HistoryDiffModel.empty()
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
    val pickVersion: (HistoryCompareSide, HistoryVersionItem) -> Unit = { side, version ->
        when (side) {
            HistoryCompareSide.LEFT -> leftKey = version.key
            HistoryCompareSide.RIGHT -> rightKey = version.key
        }
        expandedPicker = null
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
                        versions = versions,
                        leftVersion = leftVersion,
                        rightVersion = rightVersion,
                        diffModel = diffModel,
                        compareEnabled = compareEnabled,
                        compareMode = compareMode,
                        expandedPicker = expandedPicker,
                        onCompareModeChange = { compareMode = it },
                        onPickerChange = { expandedPicker = if (expandedPicker == it) null else it },
                        onVersionPicked = pickVersion,
                        onBack = { showCompare = false },
                        onDone = { showCompare = false },
                        onRestore = {
                            leftVersion.history?.let(onRestore)
                                ?: android.widget.Toast
                                    .makeText(context, "当前版本无需恢复", android.widget.Toast.LENGTH_SHORT)
                                    .show()
                        },
                    )
                } else {
                    HistoryListPage(
                        versions = versions,
                        filteredVersions = filteredVersions,
                        leftVersion = leftVersion,
                        rightVersion = rightVersion,
                        diffModel = diffModel,
                        compareEnabled = compareEnabled,
                        expandedPicker = expandedPicker,
                        query = query,
                        onQueryChange = { query = it },
                        onClearQuery = { query = "" },
                        onSelected = {
                            leftKey = it.key
                            expandedPicker = null
                        },
                        onPickerChange = { expandedPicker = if (expandedPicker == it) null else it },
                        onVersionPicked = pickVersion,
                        onBack = onDismiss,
                        onDone = onDismiss,
                        onOpenCompare = {
                            if (compareEnabled) {
                                compareMode = HistoryCompareMode.CHANGES
                                showCompare = true
                            }
                        },
                        onRestore = {
                            leftVersion.history?.let(onRestore)
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
    leftVersion: HistoryVersionItem,
    rightVersion: HistoryVersionItem,
    diffModel: HistoryDiffModel,
    compareEnabled: Boolean,
    expandedPicker: HistoryCompareSide?,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSelected: (HistoryVersionItem) -> Unit,
    onPickerChange: (HistoryCompareSide) -> Unit,
    onVersionPicked: (HistoryCompareSide, HistoryVersionItem) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenCompare: () -> Unit,
    onRestore: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HistorySearchTopBar(
                query = query,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
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
                    CompareSourceStrip(
                        leftVersion = leftVersion,
                        rightVersion = rightVersion,
                        diffModel = diffModel,
                        onLeftClick = { onPickerChange(HistoryCompareSide.LEFT) },
                        onRightClick = { onPickerChange(HistoryCompareSide.RIGHT) },
                    )
                }
                expandedPicker?.let { picker ->
                    item {
                        CompareVersionChooserPanel(
                            title = if (picker == HistoryCompareSide.LEFT) "选择左侧版本" else "选择右侧版本",
                            versions = versions,
                            selectedKey = if (picker == HistoryCompareSide.LEFT) leftVersion.key else rightVersion.key,
                            onSelected = { onVersionPicked(picker, it) },
                        )
                    }
                }
                if (!compareEnabled && leftVersion.key != rightVersion.key) {
                    item {
                        FoldLine("版本内容过大时只显示预览，不立即计算全文对比")
                    }
                }
                item {
                    SelectedVersionPanel(selected = leftVersion)
                }
                item {
                    VersionListHeader(count = filteredVersions.size)
                }
                items(filteredVersions, key = { it.key }) { version ->
                    val isSelected = version.key == leftVersion.key
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
            restoreText = "恢复${leftVersion.title}",
            restoreEnabled = !leftVersion.current,
            compareEnabled = compareEnabled,
            onCompare = onOpenCompare,
            onRestore = onRestore,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryComparePage(
    versions: List<HistoryVersionItem>,
    leftVersion: HistoryVersionItem,
    rightVersion: HistoryVersionItem,
    diffModel: HistoryDiffModel,
    compareEnabled: Boolean,
    compareMode: HistoryCompareMode,
    expandedPicker: HistoryCompareSide?,
    onCompareModeChange: (HistoryCompareMode) -> Unit,
    onPickerChange: (HistoryCompareSide) -> Unit,
    onVersionPicked: (HistoryCompareSide, HistoryVersionItem) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onRestore: () -> Unit,
) {
    val compareModes = HistoryCompareMode.entries
    val initialPage = compareModes.indexOf(compareMode).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { compareModes.size }

    LaunchedEffect(compareEnabled, compareMode) {
        if (!compareEnabled) return@LaunchedEffect
        val targetPage = compareModes.indexOf(compareMode)
        if (targetPage >= 0 && pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(compareEnabled, pagerState) {
        if (!compareEnabled) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val settledMode = compareModes.getOrNull(page) ?: return@collect
                if (settledMode != compareMode) {
                    onCompareModeChange(settledMode)
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HistoryTopBar(
                title = "版本对比",
                subtitle = "${leftVersion.title} → ${rightVersion.title}",
                onBack = onBack,
                onDone = onDone,
            )
            if (!compareEnabled) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 92.dp),
                ) {
                    item {
                        CompareSourceStrip(
                            leftVersion = leftVersion,
                            rightVersion = rightVersion,
                            diffModel = diffModel,
                            onLeftClick = { onPickerChange(HistoryCompareSide.LEFT) },
                            onRightClick = { onPickerChange(HistoryCompareSide.RIGHT) },
                        )
                    }
                    expandedPicker?.let { picker ->
                        item {
                            CompareVersionChooserPanel(
                                title = if (picker == HistoryCompareSide.LEFT) "选择左侧版本" else "选择右侧版本",
                                versions = versions,
                                selectedKey = if (picker == HistoryCompareSide.LEFT) leftVersion.key else rightVersion.key,
                                onSelected = { onVersionPicked(picker, it) },
                            )
                        }
                    }
                    item {
                        CompareModeSegment(
                            selected = compareMode,
                            onSelected = onCompareModeChange,
                        )
                    }
                    item {
                        FoldLine(
                            if (leftVersion.key == rightVersion.key) {
                                "请选择两个不同版本进行对比"
                            } else {
                                "版本内容过大时只显示预览，不立即计算全文对比"
                            },
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    userScrollEnabled = true,
                    key = { page -> compareModes.getOrNull(page)?.name ?: "history_compare_page_$page" },
                ) { page ->
                    val pageMode = compareModes.getOrNull(page) ?: HistoryCompareMode.CHANGES
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 92.dp),
                    ) {
                        item {
                            CompareSourceStrip(
                                leftVersion = leftVersion,
                                rightVersion = rightVersion,
                                diffModel = diffModel,
                                onLeftClick = { onPickerChange(HistoryCompareSide.LEFT) },
                                onRightClick = { onPickerChange(HistoryCompareSide.RIGHT) },
                            )
                        }
                        expandedPicker?.let { picker ->
                            item {
                                CompareVersionChooserPanel(
                                    title = if (picker == HistoryCompareSide.LEFT) "选择左侧版本" else "选择右侧版本",
                                    versions = versions,
                                    selectedKey = if (picker == HistoryCompareSide.LEFT) leftVersion.key else rightVersion.key,
                                    onSelected = { onVersionPicked(picker, it) },
                                )
                            }
                        }
                        item {
                            CompareModeSegment(
                                selected = pageMode,
                                onSelected = onCompareModeChange,
                            )
                        }
                        item {
                            CompareModePageContent(
                                leftVersion = leftVersion,
                                rightVersion = rightVersion,
                                diffModel = diffModel,
                                compareMode = pageMode,
                            )
                        }
                    }
                }
            }
        }
        CompareBottomActions(
            modifier = Modifier.align(Alignment.BottomCenter),
            restoreText = "恢复${leftVersion.title}",
            restoreEnabled = !leftVersion.current,
            onBackToList = onBack,
            onRestore = onRestore,
        )
    }
}

@Composable
private fun HistorySearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
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
                color = HistoryUiColors.TextSecondary,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = HistoryUiColors.TextPrimary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HistoryUiColors.CardBackground)
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
                    if (query.isNotEmpty()) {
                        Text(
                            text = "×",
                            color = HistoryUiColors.TextTertiary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onClearQuery)
                                .padding(horizontal = 6.dp),
                        )
                    }
                }
            },
        )
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
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
        }
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
                color = HistoryUiColors.TextSecondary,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = HistoryUiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
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
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
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
                .background(HistoryUiColors.CardBackground)
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
                .background(HistoryUiColors.CardBackground)
                .border(1.dp, HistoryUiColors.Border, RoundedCornerShape(16.dp))
                .clickable(onClick = onClearQuery),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "×",
                color = HistoryUiColors.TextTertiary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun CompareSourceStrip(
    leftVersion: HistoryVersionItem,
    rightVersion: HistoryVersionItem,
    diffModel: HistoryDiffModel,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HistoryUiColors.PanelBackground)
            .border(1.dp, HistoryUiColors.SoftBorder, RoundedCornerShape(16.dp))
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
                CompareVersionButton(
                    text = leftVersion.title,
                    selected = true,
                    onClick = onLeftClick,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "→",
                    color = HistoryUiColors.TextMuted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                )
                CompareVersionButton(
                    text = rightVersion.title,
                    selected = true,
                    onClick = onRightClick,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SourceChip(text = "+${diffModel.addCount}", type = HistoryDiffType.ADD)
                SourceChip(text = "-${diffModel.removeCount}", type = HistoryDiffType.REMOVE)
                SourceChip(text = "${diffModel.changeCount} 改写", type = HistoryDiffType.CHANGE)
            }
        }
        Text(
            text = "${leftVersion.sourceMeta} → ${rightVersion.sourceMeta}",
            color = HistoryUiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompareVersionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) HistoryUiColors.SelectedPanelBackground else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = HistoryUiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompareVersionChooserPanel(
    title: String,
    versions: List<HistoryVersionItem>,
    selectedKey: String,
    onSelected: (HistoryVersionItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HistoryUiColors.PanelBackground)
            .border(1.dp, HistoryUiColors.SoftBorder, RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = title,
            color = HistoryUiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        versions.forEach { version ->
            val active = version.key == selectedKey
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) HistoryUiColors.SelectedPanelBackground else HistoryUiColors.SubPanelBackground)
                    .border(1.dp, if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else HistoryUiColors.SoftBorder, RoundedCornerShape(14.dp))
                    .clickable { onSelected(version) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = version.title,
                        color = HistoryUiColors.TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = version.meta,
                        color = HistoryUiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                VersionBadge(
                    text = if (version.current) "当前" else version.badge,
                    current = version.current,
                )
            }
        }
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
            fontWeight = FontWeight.Medium,
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
            .background(HistoryUiColors.PanelBackground)
            .border(1.dp, HistoryUiColors.SoftBorder, RoundedCornerShape(22.dp))
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
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
                .background(HistoryUiColors.SubPanelBackground)
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
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "$count 个",
            color = HistoryUiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Normal,
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
    val borderColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else HistoryUiColors.Border
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HistoryUiColors.PanelBackground)
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
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
            .background(if (current) HistoryUiColors.SelectedPanelBackground else HistoryUiColors.NeutralPill)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (current) MaterialTheme.colorScheme.primary else HistoryUiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun HistoryBottomActions(
    modifier: Modifier = Modifier,
    restoreText: String,
    restoreEnabled: Boolean,
    compareEnabled: Boolean,
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
            text = if (compareEnabled || !restoreEnabled) "查看对比" else "轻量模式",
            background = if (compareEnabled) HistoryUiColors.DarkButton else HistoryUiColors.DisabledButton,
            contentColor = if (compareEnabled) MaterialTheme.colorScheme.onPrimary else HistoryUiColors.TextSecondary,
            enabled = compareEnabled,
            onClick = onCompare,
            modifier = Modifier.weight(1f),
        )
        HistoryActionButton(
            text = restoreText,
            background = if (restoreEnabled) MaterialTheme.colorScheme.primary else HistoryUiColors.DisabledButton,
            contentColor = if (restoreEnabled) MaterialTheme.colorScheme.onPrimary else HistoryUiColors.TextSecondary,
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
            contentColor = if (restoreEnabled) MaterialTheme.colorScheme.onPrimary else HistoryUiColors.TextSecondary,
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
            fontWeight = FontWeight.Medium,
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
            .background(HistoryUiColors.CardBackground)
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
                    .background(if (active) HistoryUiColors.SelectedPanelBackground else Color.Transparent)
                    .clickable { onSelected(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.label,
                    color = if (active) MaterialTheme.colorScheme.primary else HistoryUiColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompareModePageContent(
    leftVersion: HistoryVersionItem,
    rightVersion: HistoryVersionItem,
    diffModel: HistoryDiffModel,
    compareMode: HistoryCompareMode,
) {
    when (compareMode) {
        HistoryCompareMode.CHANGES -> ChangesModePanel(diffModel = diffModel)
        HistoryCompareMode.FULL -> FullModePanel(
            leftVersion = leftVersion,
            rightVersion = rightVersion,
            diffModel = diffModel,
        )
        HistoryCompareMode.SPLIT -> SplitModePanel(
            leftVersion = leftVersion,
            rightVersion = rightVersion,
            diffModel = diffModel,
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun CompareModeContent(
    leftVersion: HistoryVersionItem,
    rightVersion: HistoryVersionItem,
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
            HistoryCompareMode.FULL -> FullModePanel(
                leftVersion = leftVersion,
                rightVersion = rightVersion,
                diffModel = diffModel,
            )
            HistoryCompareMode.SPLIT -> SplitModePanel(
                leftVersion = leftVersion,
                rightVersion = rightVersion,
                diffModel = diffModel,
            )
        }
    }
}

@Composable
private fun ChangesModePanel(diffModel: HistoryDiffModel) {
    Column {
        SectionTitle(title = "只看改动", trailing = "+ 新增  − 删除  ~ 改写")
        DiffLegendCard()
        if (diffModel.groups.isEmpty()) {
            FoldLine("两个版本正文没有差异")
        } else {
            diffModel.groups.forEach { group ->
                DiffGroupCard(group = group)
            }
            FoldLine("这里只显示发生变化的段落，未变化内容已省略")
        }
    }
}

@Composable
private fun DiffLegendCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HistoryUiColors.PanelBackground)
            .border(1.dp, HistoryUiColors.SoftBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HistoryDiffPill(type = HistoryDiffType.ADD)
        HistoryDiffPill(type = HistoryDiffType.REMOVE)
        HistoryDiffPill(type = HistoryDiffType.CHANGE)
        Text(
            text = "上方版本到下方版本的变化",
            color = HistoryUiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DiffGroupCard(group: HistoryDiffGroup) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HistoryUiColors.PanelBackground)
            .border(1.dp, HistoryUiColors.SoftBorder, RoundedCornerShape(20.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HistoryUiColors.SubPanelBackground)
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
                    fontWeight = FontWeight.Medium,
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
        RewriteRow(label = "左侧", text = row.oldText.orEmpty(), old = true)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HistoryUiColors.YellowBorder.copy(alpha = 0.9f)),
        )
        RewriteRow(label = "右侧", text = row.newText.orEmpty(), old = false)
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
            fontWeight = FontWeight.Medium,
            modifier = Modifier.size(width = 38.dp, height = 18.dp),
        )
        Text(
            text = text.ifBlank { "空行" },
            color = if (old) HistoryUiColors.RedText else HistoryUiColors.GreenText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
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
            fontWeight = FontWeight.Medium,
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
private fun FullModePanel(
    leftVersion: HistoryVersionItem,
    rightVersion: HistoryVersionItem,
    diffModel: HistoryDiffModel,
) {
    Column {
        SectionTitle(title = "完整正文", trailing = "按行标记差异")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HistoryUiColors.PanelBackground)
                .border(1.dp, HistoryUiColors.SoftBorder, RoundedCornerShape(20.dp)),
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
                    text = "${leftVersion.title} → ${rightVersion.title}",
                    color = HistoryUiColors.TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
    leftVersion: HistoryVersionItem,
    rightVersion: HistoryVersionItem,
    diffModel: HistoryDiffModel,
) {
    Column {
        SectionTitle(title = "并排对比")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SplitTextCard(
                title = "左侧 · ${leftVersion.title}",
                rows = diffModel.displayRows,
                oldSide = true,
                fallbackText = leftVersion.content,
                modifier = Modifier.weight(1f),
            )
            SplitTextCard(
                title = "右侧 · ${rightVersion.title}",
                rows = diffModel.displayRows,
                oldSide = false,
                fallbackText = rightVersion.content,
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
            .background(HistoryUiColors.PanelBackground)
            .border(1.dp, HistoryUiColors.SoftBorder, RoundedCornerShape(18.dp)),
    ) {
        Text(
            text = title,
            color = HistoryUiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .background(HistoryUiColors.SubPanelBackground)
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
    trailing: String? = null,
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
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = HistoryUiColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
            fontWeight = FontWeight.Medium,
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
    val currentContentIsPreview = currentContent.length > HISTORY_DIALOG_LIGHTWEIGHT_CHAR_LIMIT
    val current = HistoryVersionItem(
        key = HistoryVersionItem.CURRENT_KEY,
        title = "当前版本",
        meta = "正在使用 · 约 ${currentContent.length} 字",
        sourceMeta = "正在使用 · 约 ${currentContent.length} 字",
        badge = "当前",
        content = if (currentContentIsPreview) historyPreviewText(currentContent, currentContent.length) else currentContent,
        contentLength = currentContent.length,
        contentIsPreview = currentContentIsPreview,
        current = true,
        history = null,
    )
    val historyItems = histories.mapIndexed { index, history ->
        val versionNumber = histories.size - index
        HistoryVersionItem(
            key = "history-${history.id}",
            title = "版本 $versionNumber",
            meta = "${dateFormat.format(history.savedAt)} · 历史保存 · 约 ${history.contentLength} 字",
            sourceMeta = "${timeFormat.format(history.savedAt)} 保存 · 约 ${history.contentLength} 字",
            badge = if (history.contentIsPreview) "预览" else "可对比",
            content = if (history.contentIsPreview) historyPreviewText(history.content, history.contentLength) else history.content,
            contentLength = history.contentLength,
            contentIsPreview = history.contentIsPreview,
            current = false,
            history = history,
        )
    }
    return listOf(current) + historyItems
}

private fun historyPreviewText(
    content: String,
    originalLength: Int,
): String {
    val preview = content.take(HISTORY_DIALOG_PREVIEW_CHAR_LIMIT)
    return if (originalLength > preview.length) {
        "$preview\n\n……仅显示前 ${HISTORY_DIALOG_PREVIEW_CHAR_LIMIT} 字预览，恢复历史版本时仍会使用完整正文"
    } else {
        preview
    }
}

private fun canBuildHistoryDiff(
    oldContent: String,
    newContent: String,
): Boolean {
    if (oldContent.length > HISTORY_DIALOG_LIGHTWEIGHT_CHAR_LIMIT ||
        newContent.length > HISTORY_DIALOG_LIGHTWEIGHT_CHAR_LIMIT
    ) {
        return false
    }
    return oldContent.lineSequence().take(HISTORY_DIALOG_DIFF_LINE_LIMIT + 1).count() <= HISTORY_DIALOG_DIFF_LINE_LIMIT &&
        newContent.lineSequence().take(HISTORY_DIALOG_DIFF_LINE_LIMIT + 1).count() <= HISTORY_DIALOG_DIFF_LINE_LIMIT
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
            HistoryDiffType.CHANGE -> "第 $lineNumber 行：内容改写"
            HistoryDiffType.ADD -> "新增内容"
            HistoryDiffType.REMOVE -> "删除内容"
            HistoryDiffType.SAME -> "未变化内容"
        }
        val subtitle = when (type) {
            HistoryDiffType.CHANGE -> "上面是左侧版本，下面是右侧版本"
            HistoryDiffType.ADD -> "右侧版本新增了 ${sameTypeRows.size} 行"
            HistoryDiffType.REMOVE -> "左侧版本有，右侧版本已删除"
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

@Composable
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
            background = HistoryUiColors.CardBackground,
            border = HistoryUiColors.Border,
            content = HistoryUiColors.TextTertiary,
        )
    }

private object HistoryUiColors {
    val PageBackground: Color
        @Composable get() = MaterialTheme.colorScheme.background
    val TopBarBackground: Color
        @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    val CardBackground: Color
        @Composable get() = MaterialTheme.colorScheme.surface
    val TextPrimary: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
    val TextSecondary: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    val TextTertiary: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
    val TextMuted: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
    val Border: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    val SoftBorder: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val PanelBackground: Color
        @Composable get() = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f)
    val SubPanelBackground: Color
        @Composable get() = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.06f)
    val SelectedPanelBackground: Color
        @Composable get() = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    val IconButtonBackground: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    val DarkButton: Color
        @Composable get() = MaterialTheme.colorScheme.primary
    val NeutralPill: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
    val DisabledButton: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant
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
    val contentLength: Int,
    val contentIsPreview: Boolean,
    val current: Boolean,
    val history: NoteHistory?,
) {
    companion object {
        const val CURRENT_KEY = "current"
    }
}

private enum class HistoryCompareSide {
    LEFT,
    RIGHT,
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
