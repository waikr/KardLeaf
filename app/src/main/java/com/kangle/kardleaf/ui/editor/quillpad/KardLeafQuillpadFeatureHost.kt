package com.kangle.kardleaf.ui.editor.quillpad

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.model.NoteRemark
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.kangle.kardleaf.data.utils.NoteTextStats
import com.kangle.kardleaf.ui.DrawingPadScreen
import com.kangle.kardleaf.ui.NoteHistoryDialog
import com.kangle.kardleaf.ui.editor.host.NoteRemarkSidePanel
import java.util.Locale
import kotlinx.coroutines.launch

internal sealed interface KardLeafQuillpadFeature {
    val snapshot: KardLeafQuillpadFeatureSnapshot

    data class Tags(override val snapshot: KardLeafQuillpadFeatureSnapshot) : KardLeafQuillpadFeature
    data class History(override val snapshot: KardLeafQuillpadFeatureSnapshot) : KardLeafQuillpadFeature
    data class Remarks(override val snapshot: KardLeafQuillpadFeatureSnapshot) : KardLeafQuillpadFeature
    data class Drawing(
        override val snapshot: KardLeafQuillpadFeatureSnapshot,
        val reference: String? = null,
        val source: String? = null,
    ) : KardLeafQuillpadFeature
}

internal sealed interface KardLeafQuillpadFeatureResult {
    data object Close : KardLeafQuillpadFeatureResult
    data object Reload : KardLeafQuillpadFeatureResult
    data class InsertMarkdown(val markdown: String) : KardLeafQuillpadFeatureResult
}

@Composable
internal fun KardLeafQuillpadFeatureHost(
    feature: KardLeafQuillpadFeature,
    bridge: KardLeafQuillpadActionBridge,
    onResult: (KardLeafQuillpadFeatureResult) -> Unit,
) {
    when (feature) {
        is KardLeafQuillpadFeature.Tags -> QuillpadTagsScreen(feature.snapshot, bridge, onResult)
        is KardLeafQuillpadFeature.History -> QuillpadHistoryScreen(feature.snapshot, bridge, onResult)
        is KardLeafQuillpadFeature.Remarks -> QuillpadRemarksScreen(feature.snapshot, bridge, onResult)
        is KardLeafQuillpadFeature.Drawing -> QuillpadDrawingScreen(feature, bridge, onResult)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuillpadTagsScreen(
    snapshot: KardLeafQuillpadFeatureSnapshot,
    bridge: KardLeafQuillpadActionBridge,
    onResult: (KardLeafQuillpadFeatureResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val availableTags by bridge.availableTags().collectAsState(initial = emptyList())
    var text by remember(snapshot.path) { mutableStateOf(snapshot.tags.joinToString("，")) }
    var saving by remember { mutableStateOf(false) }
    val selected = remember(text) { parseQuillpadTags(text) }

    BackHandler(enabled = !saving) { onResult(KardLeafQuillpadFeatureResult.Close) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("标签") },
                navigationIcon = {
                    IconButton(enabled = !saving, onClick = { onResult(KardLeafQuillpadFeatureResult.Close) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "关闭标签管理")
                    }
                },
                actions = {
                    TextButton(
                        enabled = !saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                val saved = runCatching { bridge.updateTags(snapshot, selected) }.getOrDefault(false)
                                saving = false
                                if (saved) {
                                    onResult(KardLeafQuillpadFeatureResult.Reload)
                                } else {
                                    Toast.makeText(context, "标签保存失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    ) { Text(if (saving) "保存中…" else "保存") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("标签，多个用逗号或换行分隔") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("当前 ${selected.size} 个标签", style = MaterialTheme.typography.bodySmall)
            if (availableTags.isNotEmpty()) {
                Text("已有标签，点击添加", style = MaterialTheme.typography.bodySmall)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    items(availableTags, key = { it }) { tag ->
                        AssistChip(
                            onClick = { text = appendQuillpadTag(text, tag) },
                            label = { Text("#$tag") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuillpadHistoryScreen(
    snapshot: KardLeafQuillpadFeatureSnapshot,
    bridge: KardLeafQuillpadActionBridge,
    onResult: (KardLeafQuillpadFeatureResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val histories by bridge.histories(snapshot).collectAsState(initial = emptyList())
    var restoring by remember { mutableStateOf(false) }

    NoteHistoryDialog(
        histories = histories,
        currentContent = snapshot.content,
        onDismiss = { if (!restoring) onResult(KardLeafQuillpadFeatureResult.Close) },
        onRestore = { history ->
            if (!restoring) {
                restoring = true
                scope.launch {
                    val restored = runCatching { bridge.restoreHistory(snapshot, history.id) }.getOrDefault(false)
                    if (restored) {
                        onResult(KardLeafQuillpadFeatureResult.Reload)
                    } else {
                        restoring = false
                        Toast.makeText(context, "历史版本恢复失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        onDelete = { history -> scope.launch { bridge.deleteHistory(history.id) } },
    )
}

@Composable
private fun QuillpadRemarksScreen(
    snapshot: KardLeafQuillpadFeatureSnapshot,
    bridge: KardLeafQuillpadActionBridge,
    onResult: (KardLeafQuillpadFeatureResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val remarks by bridge.remarks(snapshot).collectAsState(initial = emptyList())
    var properties by remember { mutableStateOf(emptyList<NoteFormatUtils.FrontMatterProperty>()) }
    var stats by remember { mutableStateOf<NoteTextStats?>(null) }
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(snapshot.path) {
        properties = bridge.frontMatterProperties(snapshot)
        stats = bridge.textStats(snapshot)
    }
    BackHandler { onResult(KardLeafQuillpadFeatureResult.Close) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            NoteRemarkSidePanel(
                frontMatterProperties = properties,
                textStats = stats,
                remarks = remarks,
                draft = draft,
                onDraftChange = { draft = it },
                onAdd = {
                    val content = draft.trim()
                    if (content.isNotEmpty()) scope.launch {
                        draft = ""
                        if (!bridge.addRemark(snapshot, content)) {
                            draft = content
                            Toast.makeText(context, "备注添加失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onUpdate = { remark: NoteRemark, content: String ->
                    scope.launch { bridge.updateRemark(remark.id, content.trim()) }
                },
                onDelete = { remark -> scope.launch { bridge.deleteRemark(remark.id) } },
            )
            IconButton(
                onClick = { onResult(KardLeafQuillpadFeatureResult.Close) },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭备注")
            }
        }
    }
}

@Composable
private fun QuillpadDrawingScreen(
    feature: KardLeafQuillpadFeature.Drawing,
    bridge: KardLeafQuillpadActionBridge,
    onResult: (KardLeafQuillpadFeatureResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    DrawingPadScreen(
        initialDrawingSource = feature.source,
        onDismiss = { if (!saving) onResult(KardLeafQuillpadFeatureResult.Close) },
        onSave = { bitmap: Bitmap, drawingSource: String ->
            if (!saving) {
                saving = true
                scope.launch {
                    val result = runCatching {
                        feature.reference?.let { reference ->
                            if (bridge.updateDrawing(feature.snapshot, reference, bitmap, drawingSource)) "" else null
                        } ?: bridge.importDrawing(feature.snapshot, bitmap, drawingSource).takeIf { it.isNotBlank() }
                    }.getOrNull()
                    saving = false
                    when {
                        result == null -> Toast.makeText(context, "画图保存失败", Toast.LENGTH_SHORT).show()
                        feature.reference != null -> onResult(KardLeafQuillpadFeatureResult.Close)
                        else -> onResult(KardLeafQuillpadFeatureResult.InsertMarkdown(result))
                    }
                }
            }
        },
    )
}

internal fun parseQuillpadTags(text: String): List<String> =
    text.split(',', '，', '\n')
        .map { it.trim().removePrefix("#").trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase(Locale.ROOT) }

private fun appendQuillpadTag(text: String, tag: String): String {
    val normalized = tag.trim().removePrefix("#").trim()
    if (normalized.isEmpty() || parseQuillpadTags(text).any { it.equals(normalized, ignoreCase = true) }) return text
    return if (text.isBlank()) normalized else text.trimEnd().trimEnd(',', '，') + "，" + normalized
}
