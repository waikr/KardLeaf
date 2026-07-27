package com.kangle.kardleaf.ui

import com.kangle.kardleaf.ui.editor.history.*

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.model.NoteHistory
import com.kangle.kardleaf.localizedText
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Locale

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
                                ?: context.showToast(localizedText("当前版本无需恢复", "The current version does not need restoring"))
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
                                ?: context.showToast(localizedText("当前版本无需恢复", "The current version does not need restoring"))
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
                            title = if (picker == HistoryCompareSide.LEFT) localizedText("选择左侧版本", "Choose left version") else localizedText("选择右侧版本", "Choose right version"),
                            versions = versions,
                            selectedKey = if (picker == HistoryCompareSide.LEFT) leftVersion.key else rightVersion.key,
                            onSelected = { onVersionPicked(picker, it) },
                        )
                    }
                }
                if (!compareEnabled && leftVersion.key != rightVersion.key) {
                    item {
                        FoldLine(localizedText("版本内容过大时只显示预览，不立即计算全文对比", "Large versions show a preview without calculating the full diff"))
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
            restoreText = localizedText("恢复${leftVersion.title}", "Restore ${leftVersion.title}"),
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
                title = localizedText("版本对比", "Compare versions"),
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
                                title = if (picker == HistoryCompareSide.LEFT) localizedText("选择左侧版本", "Choose left version") else localizedText("选择右侧版本", "Choose right version"),
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
                                localizedText("请选择两个不同版本进行对比", "Choose two different versions to compare")
                            } else {
                                localizedText("版本内容过大时只显示预览，不立即计算全文对比", "Large versions show a preview without calculating the full diff")
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
                                    title = if (picker == HistoryCompareSide.LEFT) localizedText("选择左侧版本", "Choose left version") else localizedText("选择右侧版本", "Choose right version"),
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
            restoreText = localizedText("恢复${leftVersion.title}", "Restore ${leftVersion.title}"),
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
                                text = localizedText("搜索版本内容、保存时间或备注", "Search content, save time or remarks"),
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
                text = localizedText("完成", "Done"),
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
                text = localizedText("完成", "Done"),
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
                                text = localizedText("搜索版本内容、保存时间或备注", "Search content, save time or remarks"),
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
                SourceChip(text = localizedText("${diffModel.changeCount} 改写", "${diffModel.changeCount} changed"), type = HistoryDiffType.CHANGE)
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
                    text = if (version.current) localizedText("当前", "Current") else version.badge,
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
                    text = localizedText("当前选中：${selected.title}", "Selected: ${selected.title}"),
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
                text = if (selected.current) localizedText("当前", "Current") else localizedText("已选中", "Selected"),
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
            HistoryCopyableTextBlock(
                content = selected.content.ifBlank { localizedText("空内容", "Empty content") },
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
            text = localizedText("版本列表", "Version list"),
            color = HistoryUiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = localizedText("$count 个", "$count"),
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
                text = if (version.current) localizedText("当前", "Current") else version.badge,
                current = version.current,
            )
        }
        Text(
            text = version.content.ifBlank { localizedText("空内容", "Empty content") }.replace('\n', ' '),
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
        SourceChip(text = localizedText("${diffModel.changeCount} 改写", "${diffModel.changeCount} changed"), type = HistoryDiffType.CHANGE)
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
            text = if (compareEnabled || !restoreEnabled) localizedText("查看对比", "View comparison") else localizedText("轻量模式", "Lightweight mode"),
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
            text = localizedText("返回列表", "Back to list"),
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
        SectionTitle(title = localizedText("只看改动", "Changes only"), trailing = localizedText("+ 新增  − 删除  ~ 改写", "+ Added  − Removed  ~ Changed"))
        DiffLegendCard()
        if (diffModel.groups.isEmpty()) {
            FoldLine(localizedText("两个版本正文没有差异", "The two versions have no content differences"))
        } else {
            diffModel.groups.forEach { group ->
                DiffGroupCard(group = group)
            }
            FoldLine(localizedText("这里只显示发生变化的段落，未变化内容已省略", "Only changed paragraphs are shown; unchanged content is omitted"))
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
            text = localizedText("上方版本到下方版本的变化", "Changes from the upper version to the lower version"),
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
        RewriteRow(label = localizedText("左侧", "Left"), text = row.oldText.orEmpty(), old = true)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HistoryUiColors.YellowBorder.copy(alpha = 0.9f)),
        )
        RewriteRow(label = localizedText("右侧", "Right"), text = row.newText.orEmpty(), old = false)
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
        HistoryCopyableText(
            text = text,
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
        HistoryCopyableText(
            text = content,
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
        SectionTitle(title = localizedText("完整正文", "Full content"), trailing = localizedText("按行标记差异", "Line-by-line differences"))
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
        SectionTitle(title = localizedText("并排对比", "Side-by-side comparison"))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SplitTextCard(
                title = localizedText("左侧 · ${leftVersion.title}", "Left · ${leftVersion.title}"),
                rows = diffModel.displayRows,
                oldSide = true,
                fallbackText = leftVersion.content,
                modifier = Modifier.weight(1f),
            )
            SplitTextCard(
                title = localizedText("右侧 · ${rightVersion.title}", "Right · ${rightVersion.title}"),
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
                HistoryCopyableTextBlock(
                    content = fallbackText.ifBlank { localizedText("空内容", "Empty content") },
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
    HistoryCopyableText(
        text = text,
        color = colors.content,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (type == HistoryDiffType.SAME) Color.Transparent else colors.background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun HistoryCopyableTextBlock(
    content: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val lines = remember(content) { content.lines().ifEmpty { listOf("") } }
    Column(modifier = modifier.fillMaxWidth()) {
        lines.forEach { line ->
            HistoryCopyableText(
                text = line,
                color = color,
                style = style,
                modifier = Modifier.fillMaxWidth(),
                blankLabel = "",
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCopyableText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    blankLabel: String = localizedText("空行", "Empty line"),
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Text(
        text = if (text.isBlank() && blankLabel.isNotEmpty()) blankLabel else text,
        color = color,
        style = style,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                clipboard.setText(AnnotatedString(text))
                context.showToast(if (text.isBlank()) localizedText("已复制空行", "Empty line copied") else localizedText("已复制", "Copied"))
            },
        ),
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
        HistoryDiffType.ADD -> localizedText("新增", "Added")
        HistoryDiffType.REMOVE -> localizedText("删除", "Removed")
        HistoryDiffType.CHANGE -> localizedText("改写", "Changed")
        HistoryDiffType.SAME -> localizedText("未变", "Unchanged")
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
