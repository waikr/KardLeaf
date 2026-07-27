package com.kangle.kardleaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WikilinkPromptDialog(
    prompt: MainViewModel.WikilinkPrompt,
    onDismiss: () -> Unit,
    onCreate: (target: String, sourcePath: String) -> Unit,
    onCandidate: (path: String) -> Unit,
) {
    when (prompt) {
        is MainViewModel.WikilinkPrompt.Unresolved -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("未找到目标笔记") },
                text = { Text("未找到“${prompt.target}”。") },
                confirmButton = {
                    TextButton(onClick = { onCreate(prompt.target, prompt.sourcePath) }) { Text("创建笔记") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("取消") }
                },
            )
        }
        is MainViewModel.WikilinkPrompt.Ambiguous -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("选择目标笔记") },
                text = {
                    LazyColumn {
                        items(prompt.candidates, key = { it.id }) { candidate ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCandidate(candidate.path) }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(candidate.title, color = MaterialTheme.colorScheme.primary)
                                Text(candidate.path, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("取消") }
                },
            )
        }
    }
}
