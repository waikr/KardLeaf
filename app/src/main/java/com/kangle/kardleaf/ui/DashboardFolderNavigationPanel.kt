package com.kangle.kardleaf.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun FolderNavigationPanel(
    labels: List<String>,
    currentFilter: MainViewModel.NoteFilter,
    dragProgress: Float = 1f,
    onDismiss: () -> Unit,
    onSelect: (MainViewModel.NoteFilter) -> Unit,
) {
    val normalizedLabels = remember(labels) {
        labels
            .map(::normalizeFolderPathForUi)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val currentPath = (currentFilter as? MainViewModel.NoteFilter.Label)?.name
        ?.let(::normalizeFolderPathForUi)
        .orEmpty()
    val folderSections = remember(normalizedLabels) { buildFolderNavigationSections(normalizedLabels) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val panelHeightRatio = 0.58f
    val fallbackPanelHeightPx = remember(configuration, density) {
        with(density) { (configuration.screenHeightDp.dp * panelHeightRatio).toPx() }
    }
    val targetProgress = dragProgress.coerceIn(0f, 1f)
    val panelProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = if (targetProgress >= 1f) 160 else 0, easing = FastOutSlowInEasing),
        label = "FolderNavigationPanelProgress",
    )
    var panelHeightPx by remember { mutableStateOf(0) }
    val chipColumns = if (configuration.screenWidthDp >= 600) 4 else 3

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f * panelProgress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
        )
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(panelHeightRatio)
                    .align(Alignment.TopCenter)
                    .onSizeChanged { panelHeightPx = it.height }
                    .graphicsLayer {
                        val measuredHeight = if (panelHeightPx > 0) panelHeightPx.toFloat() else fallbackPanelHeightPx
                        translationY = -measuredHeight * (1f - panelProgress)
                    },
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                FolderNavigationHeader(onDismiss = onDismiss)

                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item("all_notes") {
                        FolderNavigationChipGrid(
                            items = listOf(FolderNavigationChipItem(title = "全部笔记", path = "")),
                            columns = chipColumns,
                            selectedPath = currentPath.takeIf { currentFilter is MainViewModel.NoteFilter.Label },
                            allSelected = currentFilter is MainViewModel.NoteFilter.All,
                            onSelectPath = { onSelect(MainViewModel.NoteFilter.All) },
                        )
                    }
                    if (folderSections.isEmpty()) {
                        item("empty_folders") {
                            Text(
                                text = "还没有文件夹",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            )
                        }
                    }
                    lazyColumnItems(
                        items = folderSections,
                        key = { it.path },
                    ) { section ->
                        FolderNavigationSectionView(
                            section = section,
                            columns = chipColumns,
                            selectedPath = currentPath,
                            onSelectPath = { path -> onSelect(MainViewModel.NoteFilter.Label(path)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderNavigationHeader(onDismiss: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .pointerInput(onDismiss) {
                    val triggerDistancePx = 24.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var pointerPressed = true
                        while (pointerPressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            pointerPressed = change?.pressed == true
                            if (change != null && pointerPressed) {
                                val dx = kotlin.math.abs(change.position.x - down.position.x)
                                val dy = change.position.y - down.position.y
                                if (dy < -triggerDistancePx && kotlin.math.abs(dy) > dx * 1.2f) {
                                    change.consume()
                                    onDismiss()
                                    pointerPressed = false
                                }
                            }
                        }
                    }
                }
                .padding(top = 8.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)),
        )
    }
}

@Composable
private fun FolderNavigationSectionView(
    section: FolderNavigationSection,
    columns: Int,
    selectedPath: String,
    onSelectPath: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        FolderNavigationChipGrid(
            items = section.chips,
            columns = columns,
            selectedPath = selectedPath,
            allSelected = false,
            onSelectPath = onSelectPath,
        )
    }
}

@Composable
private fun FolderNavigationChipGrid(
    items: List<FolderNavigationChipItem>,
    columns: Int,
    selectedPath: String?,
    allSelected: Boolean,
    onSelectPath: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowItems.forEach { item ->
                    FolderNavigationChip(
                        text = item.title,
                        selected = allSelected || item.path == selectedPath,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectPath(item.path) },
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FolderNavigationChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        modifier =
            modifier
                .heightIn(min = 34.dp)
                .border(
                    width = if (selected) 1.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = shape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class FolderNavigationSection(
    val title: String,
    val path: String,
    val chips: List<FolderNavigationChipItem>,
)

private data class FolderNavigationChipItem(
    val title: String,
    val path: String,
)

private fun buildFolderNavigationSections(labels: List<String>): List<FolderNavigationSection> {
    val sections = mutableListOf<FolderNavigationSection>()

    fun addSection(folder: FolderChipData) {
        val children = panelDirectChildFolders(labels, folder.path)
        sections += FolderNavigationSection(
            title = folder.name,
            path = folder.path,
            chips = listOf(FolderNavigationChipItem(title = "全部", path = folder.path)) +
                children.map { child -> FolderNavigationChipItem(title = child.name, path = child.path) },
        )
        children.forEach { child ->
            if (panelDirectChildFolders(labels, child.path).isNotEmpty()) {
                addSection(child)
            }
        }
    }

    panelDirectChildFolders(labels, parent = "").forEach(::addSection)
    return sections
}

private fun panelDirectChildFolders(
    labels: List<String>,
    parent: String,
): List<FolderChipData> {
    val prefix = parent.takeIf { it.isNotBlank() }?.let { "$it/" }.orEmpty()
    return labels
        .asSequence()
        .filter { it.startsWith(prefix) && it != parent }
        .map { it.removePrefix(prefix) }
        .filter { it.isNotBlank() && !it.contains("/") }
        .distinct()
        .sorted()
        .map { child ->
            FolderChipData(
                name = child,
                path = if (parent.isBlank()) child else "$parent/$child",
            )
        }
        .toList()
}
