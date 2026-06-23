package com.kangle.kardleaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.model.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagementScreen(
    tags: List<String>,
    allNotes: List<Note>,
    onOpenDrawer: () -> Unit,
    onTagClick: (String) -> Unit,
    onRenameTag: (String, String) -> Unit,
    onDeleteTag: (String) -> Unit,
) {
    var renamingTag by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deletingTag by remember { mutableStateOf<String?>(null) }
    val noteCountByTag = remember(allNotes) {
        allNotes
            .flatMap { note -> note.tags.distinct().map { tag -> tag to note.file.path } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, paths) -> paths.distinct().size }
    }

    renamingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { renamingTag = null },
            title = { Text("重命名标签") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("标签名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameText.trim().removePrefix("#").trim()
                    if (newName.isNotBlank() && !newName.equals(tag, ignoreCase = true)) {
                        onRenameTag(tag, newName)
                    }
                    renamingTag = null
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingTag = null }) {
                    Text("取消")
                }
            },
        )
    }

    deletingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { deletingTag = null },
            title = { Text("删除标签") },
            text = { Text("从所有笔记的 YAML tags 中移除“$tag”？") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTag(tag)
                    deletingTag = null
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTag = null }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("标签管理") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = "打开侧边栏")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (tags.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.Label, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("还没有 YAML 标签", style = MaterialTheme.typography.titleMedium)
                Text(
                    "长按笔记后可添加标签，标签会写入 Obsidian 兼容的 YAML tags",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(tags, key = { it }) { tag ->
                    val count = noteCountByTag[tag] ?: 0
                    ListItem(
                        modifier = Modifier.clickable { onTagClick(tag) },
                        leadingContent = { Icon(Icons.Outlined.Label, contentDescription = null) },
                        headlineContent = {
                            Text(
                                text = tag,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = { Text("$count 篇笔记") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    renamingTag = tag
                                    renameText = tag
                                }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "重命名标签")
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(onClick = { deletingTag = tag }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "删除标签")
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
