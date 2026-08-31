package com.kangle.kardleaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import java.util.Locale

internal enum class FileTreeSelectionMode {
    FILE,
    FOLDER,
    FILE_OR_FOLDER,
}

internal data class FileTreePickerNode<T>(
    val id: String,
    val label: String,
    val value: T,
    val depth: Int = 0,
    val parentId: String? = null,
    val isFolder: Boolean = true,
    val hasChildren: Boolean = false,
    val selectable: Boolean = true,
)

internal fun <T> buildFileTreePickerNodes(entries: List<Pair<String, T>>): List<FileTreePickerNode<T>> {
    val valuesByPath = linkedMapOf<String, T>()
    entries.forEach { (rawPath, value) ->
        normalizeFolderPathForUi(rawPath).takeIf(String::isNotBlank)?.let { path ->
            if (path !in valuesByPath) valuesByPath[path] = value
        }
    }
    val paths = valuesByPath.keys.toList()
    val children =
        paths.groupBy { path ->
            path.substringBeforeLast('/', missingDelimiterValue = "")
                .takeIf(paths::contains)
                .orEmpty()
        }
    return buildList {
        fun append(
            parentPath: String,
            depth: Int,
        ) {
            children[parentPath].orEmpty().forEach { path ->
                add(
                    FileTreePickerNode(
                        id = path,
                        label = path.substringAfterLast('/'),
                        value = valuesByPath.getValue(path),
                        depth = depth,
                        parentId = parentPath.takeIf(String::isNotBlank),
                        hasChildren = children[path].orEmpty().isNotEmpty(),
                    ),
                )
                append(path, depth + 1)
            }
        }
        append(parentPath = "", depth = 0)
    }
}

internal fun buildFileTreePickerFolderNodes(paths: Collection<String>): List<FileTreePickerNode<String>> {
    val folderPaths = linkedSetOf<String>()
    paths.forEach { rawPath ->
        val path = normalizeFolderPathForUi(rawPath)
        if (path.isNotBlank()) {
            val segments = path.split('/')
            segments.indices.forEach { index ->
                folderPaths += segments.take(index + 1).joinToString("/")
            }
        }
    }
    return buildFileTreePickerNodes(
        folderPaths
            .toList()
            .sortedBy { it.lowercase(Locale.getDefault()) }
            .map { it to it },
    )
}

@Composable
internal fun <T> FileTreePickerDialog(
    title: String,
    nodes: List<FileTreePickerNode<T>>?,
    selectedId: String?,
    selectionMode: FileTreeSelectionMode = FileTreeSelectionMode.FOLDER,
    forceAboveAnchor: Boolean = false,
    loadingText: String = "正在读取…",
    emptyText: String = "暂无可选项",
    onSelect: (FileTreePickerNode<T>) -> Unit,
    onDismiss: () -> Unit,
) {
    var expandedIds by remember(nodes) {
        mutableStateOf(nodes.orEmpty().filter { it.hasChildren }.map { it.id }.toSet())
    }
    val visibleNodes =
        remember(nodes, expandedIds) {
        val nodeById = nodes.orEmpty().associateBy { it.id }
        nodes.orEmpty().filter { node ->
            var parentId = node.parentId
            var visible = true
            while (parentId != null) {
                if (parentId !in expandedIds) {
                    visible = false
                    break
                }
                parentId = nodeById[parentId]?.parentId
            }
            visible
        }
    }

    fun isSelectable(node: FileTreePickerNode<T>): Boolean =
        node.selectable &&
            when (selectionMode) {
                FileTreeSelectionMode.FILE -> !node.isFolder
                FileTreeSelectionMode.FOLDER -> node.isFolder
                FileTreeSelectionMode.FILE_OR_FOLDER -> true
            }

    KardLeafDropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 220.dp, max = 320.dp).heightIn(max = 360.dp),
        forceAboveAnchor = forceAboveAnchor,
        properties =
            PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = true,
            ),
    ) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        when {
            nodes == null -> Text(loadingText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            nodes.isEmpty() -> Text(
                text = emptyText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            else -> visibleNodes.forEach { node ->
                val selectable = isSelectable(node)
                val selected = node.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = selectable) { onSelect(node) }
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width((node.depth * 20).dp))
                    if (node.hasChildren) {
                        IconButton(
                            onClick = {
                                expandedIds =
                                    if (node.id in expandedIds) {
                                        expandedIds - node.id
                                    } else {
                                        expandedIds + node.id
                                    }
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector =
                                    if (node.id in expandedIds) {
                                        Icons.Outlined.ExpandMore
                                    } else {
                                        Icons.Outlined.ChevronRight
                                    },
                                contentDescription =
                                    if (node.id in expandedIds) "折叠" else "展开",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        Spacer(Modifier.size(40.dp))
                    }
                    Text(
                        text = node.label,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(vertical = 6.dp),
                        color =
                            if (selectable) {
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "已选择",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
