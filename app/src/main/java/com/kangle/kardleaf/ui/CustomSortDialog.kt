package com.kangle.kardleaf.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.model.Note
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun CustomSortDialog(
    folderName: String,
    notes: List<Note>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val orderedNotes = remember { mutableStateListOf<Note>() }
    val notesKey = remember(notes) { notes.joinToString("|") { it.file.path } }

    LaunchedEffect(notesKey) {
        orderedNotes.clear()
        orderedNotes.addAll(notes)
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (from.index == to.index) return@rememberReorderableLazyListState
        orderedNotes.add(to.index, orderedNotes.removeAt(from.index))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("简洁自定义顺序") },
        text = {
            Column {
                Text(
                    text = folderName.ifBlank { "当前目录" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                if (orderedNotes.isEmpty()) {
                    Text(
                        text = "当前目录没有可排序的笔记",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = orderedNotes,
                            key = { it.file.path },
                        ) { note ->
                            ReorderableItem(
                                state = reorderableState,
                                key = note.file.path,
                            ) { isDragging ->
                                val elevation = animateDpAsState(
                                    targetValue = if (isDragging) 8.dp else 1.dp,
                                    label = "customSortItemElevation",
                                )
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = if (isDragging) 2.dp else 0.dp,
                                    shadowElevation = elevation.value,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .longPressDraggableHandle(
                                                onDragStarted = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                            )
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "长按拖动",
                                            modifier = Modifier
                                                .padding(end = 10.dp)
                                                .size(22.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = noteDisplayTitle(note),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = notePreviewText(note),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(orderedNotes.map { it.file.path })
                },
                enabled = orderedNotes.isNotEmpty(),
            ) {
                Text("完成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun noteDisplayTitle(note: Note): String =
    note.title.ifBlank { note.file.nameWithoutExtension }.ifBlank { "未命名笔记" }

private fun notePreviewText(note: Note): String {
    val source = note.contentPreview.ifBlank { note.content }
    return source
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "无正文预览" }
}
