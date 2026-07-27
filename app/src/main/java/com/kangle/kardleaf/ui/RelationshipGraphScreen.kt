package com.kangle.kardleaf.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kangle.kardleaf.data.database.NoteLinkEntity
import com.kangle.kardleaf.data.database.NoteLinkResolutionStatus
import com.kangle.kardleaf.data.model.Note
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class RelationshipGraphNode(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val group: String = "",
    val color: String? = null,
)

internal data class RelationshipGraphEdge(
    val id: String,
    val source: String,
    val target: String,
    val label: String = "",
    val directed: Boolean = true,
)

internal data class RelationshipGraphData(
    val nodes: List<RelationshipGraphNode>,
    val edges: List<RelationshipGraphEdge>,
)

private data class CustomRelationshipGraph(
    val id: String,
    val title: String,
    val nodes: List<RelationshipGraphNode> = emptyList(),
    val edges: List<RelationshipGraphEdge> = emptyList(),
)

private data class RelationshipGraphSettings(
    val showOrphans: Boolean = true,
    val showArrows: Boolean = false,
    val nodeScale: Float = 1f,
    val linkScale: Float = 1f,
    val labelFade: Float = 0.55f,
    val centerForce: Float = 1f,
    val repelForce: Float = 1f,
    val linkForce: Float = 1f,
    val linkDistance: Float = 1f,
)

private enum class RelationshipGraphTab {
    NOTE,
    CUSTOM,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationshipGraphScreen(
    notes: List<Note>,
    loadNoteLinks: suspend () -> List<NoteLinkEntity>,
    onOpenDrawer: () -> Unit,
    onNoteClick: (Note) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { CustomRelationshipGraphStore(context.applicationContext) }
    val graphSaveMutex = remember { Mutex() }
    val graphSaveRevision = remember { AtomicLong(0L) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var selectedTab by remember { mutableStateOf(RelationshipGraphTab.NOTE) }
    var noteLinks by remember { mutableStateOf<List<NoteLinkEntity>>(emptyList()) }
    var noteGraphData by remember { mutableStateOf(RelationshipGraphData(emptyList(), emptyList())) }
    var noteGraphLoading by remember { mutableStateOf(true) }
    var noteGraphRevision by remember { mutableStateOf(0) }
    var settings by remember { mutableStateOf(RelationshipGraphSettings()) }
    var showSettings by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var customGraphs by remember { mutableStateOf<List<CustomRelationshipGraph>>(emptyList()) }
    var selectedGraphId by remember { mutableStateOf<String?>(null) }
    var customLoaded by remember { mutableStateOf(false) }
    var showGraphMenu by remember { mutableStateOf(false) }
    var showCreateGraphDialog by remember { mutableStateOf(false) }
    var showRenameGraphDialog by remember { mutableStateOf(false) }
    var showDeleteGraphDialog by remember { mutableStateOf(false) }
    var showNodeDialog by remember { mutableStateOf(false) }
    var editingNodeId by remember { mutableStateOf<String?>(null) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var showRelationDialog by remember { mutableStateOf(false) }
    var showRelationsManager by remember { mutableStateOf(false) }
    var preselectedRelationSourceId by remember { mutableStateOf<String?>(null) }

    val activeNotes = remember(notes) { notes.filter { !it.isTrashed } }
    val noteByPath = remember(activeNotes) { activeNotes.associateBy { normalizeGraphPath(it.file.path) } }

    LaunchedEffect(activeNotes, noteGraphRevision) {
        noteGraphLoading = true
        noteLinks = runCatching { withContext(Dispatchers.IO) { loadNoteLinks() } }.getOrDefault(emptyList())
        noteGraphData = withContext(Dispatchers.Default) {
            buildNoteRelationshipGraph(activeNotes, noteLinks)
        }
        noteGraphLoading = false
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { store.load() }
        customGraphs = loaded.ifEmpty { listOf(defaultCustomRelationshipGraph()) }
        selectedGraphId = customGraphs.firstOrNull()?.id
        customLoaded = true
    }

    fun persistGraphs(next: List<CustomRelationshipGraph>, keepSelection: String? = selectedGraphId) {
        val safeNext = next.ifEmpty { listOf(defaultCustomRelationshipGraph()) }
        customGraphs = safeNext
        selectedGraphId = keepSelection?.takeIf { id -> safeNext.any { it.id == id } } ?: safeNext.first().id
        val saveRevision = graphSaveRevision.incrementAndGet()
        scope.launch(Dispatchers.IO) {
            graphSaveMutex.withLock {
                if (graphSaveRevision.get() == saveRevision) store.save(safeNext)
            }
        }
    }

    val selectedGraph = customGraphs.firstOrNull { it.id == selectedGraphId }
    val customGraphData = remember(selectedGraph) {
        selectedGraph?.let { RelationshipGraphData(it.nodes, it.edges) }
            ?: RelationshipGraphData(emptyList(), emptyList())
    }
    val selectedCustomNode = selectedGraph?.nodes?.firstOrNull { it.id == selectedNodeId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关系图") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "打开侧边栏")
                    }
                },
                actions = {
                    if (selectedTab == RelationshipGraphTab.NOTE) {
                        IconButton(onClick = { noteGraphRevision += 1 }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新关系图")
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showGraphMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "管理自制关系图")
                            }
                            DropdownMenu(
                                expanded = showGraphMenu,
                                onDismissRequest = { showGraphMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("新建关系图") },
                                    leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                                    onClick = {
                                        showGraphMenu = false
                                        showCreateGraphDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("重命名当前关系图") },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                    enabled = selectedGraph != null,
                                    onClick = {
                                        showGraphMenu = false
                                        showRenameGraphDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("删除当前关系图") },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                    enabled = selectedGraph != null && customGraphs.size > 1,
                                    onClick = {
                                        showGraphMenu = false
                                        showDeleteGraphDialog = true
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "关系图设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == RelationshipGraphTab.NOTE,
                    onClick = {
                        selectedTab = RelationshipGraphTab.NOTE
                        searchQuery = ""
                    },
                    text = { Text("笔记关系图") },
                    icon = { Icon(Icons.Outlined.AccountTree, contentDescription = null) },
                )
                Tab(
                    selected = selectedTab == RelationshipGraphTab.CUSTOM,
                    onClick = {
                        selectedTab = RelationshipGraphTab.CUSTOM
                        searchQuery = ""
                    },
                    text = { Text("自制关系图") },
                    icon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                )
            }

            if (selectedTab == RelationshipGraphTab.CUSTOM && customGraphs.size > 1) {
                CustomGraphSelector(
                    graphs = customGraphs,
                    selectedGraphId = selectedGraphId,
                    onSelect = {
                        selectedGraphId = it
                        selectedNodeId = null
                    },
                )
            }

            RelationshipGraphSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                nodeCount = if (selectedTab == RelationshipGraphTab.NOTE) noteGraphData.nodes.size else customGraphData.nodes.size,
                edgeCount = if (selectedTab == RelationshipGraphTab.NOTE) noteGraphData.edges.size else customGraphData.edges.size,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                when {
                    selectedTab == RelationshipGraphTab.NOTE && noteGraphLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    selectedTab == RelationshipGraphTab.NOTE && noteGraphData.nodes.isEmpty() -> {
                        GraphEmptyHint(
                            title = "还没有可显示的笔记",
                            message = "笔记关系图会读取现有笔记和 [[双向链接]]。",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    selectedTab == RelationshipGraphTab.CUSTOM && !customLoaded -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    selectedTab == RelationshipGraphTab.CUSTOM && customGraphData.nodes.isEmpty() -> {
                        GraphEmptyHint(
                            title = "开始制作人物关系图",
                            message = "先添加人物节点，再创建人物之间的关系。",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    else -> {
                        val graph = if (selectedTab == RelationshipGraphTab.NOTE) noteGraphData else customGraphData
                        RelationshipGraphWebView(
                            graph = graph,
                            settings = settings,
                            query = searchQuery,
                            isDark = isDark,
                            showEdgeLabels = selectedTab == RelationshipGraphTab.CUSTOM,
                            onNodeClick = { nodeId ->
                                if (selectedTab == RelationshipGraphTab.NOTE) {
                                    noteByPath[normalizeGraphPath(nodeId)]?.let(onNoteClick)
                                } else {
                                    selectedNodeId = nodeId
                                }
                            },
                            onNodeLongPress = { nodeId ->
                                if (selectedTab == RelationshipGraphTab.CUSTOM) {
                                    selectedNodeId = nodeId
                                    editingNodeId = nodeId
                                    showNodeDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                if (selectedTab == RelationshipGraphTab.CUSTOM && customLoaded) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FloatingActionButton(
                            onClick = { showRelationsManager = true },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Icon(Icons.Outlined.Link, contentDescription = "管理关系")
                        }
                        FloatingActionButton(
                            onClick = {
                                editingNodeId = null
                                showNodeDialog = true
                            },
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "添加人物节点")
                        }
                    }
                }

                if (selectedTab == RelationshipGraphTab.CUSTOM && selectedCustomNode != null) {
                    SelectedCustomNodeCard(
                        node = selectedCustomNode,
                        onEdit = {
                            editingNodeId = selectedCustomNode.id
                            showNodeDialog = true
                        },
                        onAddRelation = {
                            preselectedRelationSourceId = selectedCustomNode.id
                            showRelationDialog = true
                        },
                        onDelete = {
                            selectedGraph?.let { graph ->
                                val nextGraph = graph.copy(
                                    nodes = graph.nodes.filterNot { it.id == selectedCustomNode.id },
                                    edges = graph.edges.filterNot { it.source == selectedCustomNode.id || it.target == selectedCustomNode.id },
                                )
                                persistGraphs(customGraphs.map { if (it.id == graph.id) nextGraph else it })
                                selectedNodeId = null
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                    )
                }
            }
        }
    }

    if (showSettings) {
        RelationshipGraphSettingsDialog(
            settings = settings,
            onSettingsChange = { settings = it },
            onDismiss = { showSettings = false },
        )
    }

    if (showCreateGraphDialog) {
        TextValueDialog(
            title = "新建自制关系图",
            label = "关系图名称",
            initialValue = "人物关系图",
            confirmText = "新建",
            onDismiss = { showCreateGraphDialog = false },
            onConfirm = { title ->
                val graph = CustomRelationshipGraph(id = UUID.randomUUID().toString(), title = title)
                persistGraphs(customGraphs + graph, graph.id)
                showCreateGraphDialog = false
            },
        )
    }

    if (showRenameGraphDialog && selectedGraph != null) {
        TextValueDialog(
            title = "重命名关系图",
            label = "关系图名称",
            initialValue = selectedGraph.title,
            confirmText = "保存",
            onDismiss = { showRenameGraphDialog = false },
            onConfirm = { title ->
                persistGraphs(customGraphs.map { if (it.id == selectedGraph.id) it.copy(title = title) else it })
                showRenameGraphDialog = false
            },
        )
    }

    if (showDeleteGraphDialog && selectedGraph != null) {
        AlertDialog(
            onDismissRequest = { showDeleteGraphDialog = false },
            title = { Text("删除关系图") },
            text = { Text("将删除「${selectedGraph.title}」及其中的全部人物和关系。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        persistGraphs(customGraphs.filterNot { it.id == selectedGraph.id }, null)
                        selectedNodeId = null
                        showDeleteGraphDialog = false
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGraphDialog = false }) { Text("取消") }
            },
        )
    }

    if (showNodeDialog && selectedGraph != null) {
        val editingNode = selectedGraph.nodes.firstOrNull { it.id == editingNodeId }
        NodeEditorDialog(
            node = editingNode,
            onDismiss = {
                showNodeDialog = false
                editingNodeId = null
            },
            onConfirm = { name, group ->
                val nextNode = editingNode?.copy(title = name, group = group)
                    ?: RelationshipGraphNode(
                        id = UUID.randomUUID().toString(),
                        title = name,
                        group = group,
                    )
                val nextNodes = if (editingNode == null) {
                    selectedGraph.nodes + nextNode
                } else {
                    selectedGraph.nodes.map { if (it.id == editingNode.id) nextNode else it }
                }
                persistGraphs(customGraphs.map { if (it.id == selectedGraph.id) it.copy(nodes = nextNodes) else it })
                selectedNodeId = nextNode.id
                showNodeDialog = false
                editingNodeId = null
            },
        )
    }

    if (showRelationDialog && selectedGraph != null) {
        RelationEditorDialog(
            graph = selectedGraph,
            initialSourceId = preselectedRelationSourceId,
            onDismiss = {
                showRelationDialog = false
                preselectedRelationSourceId = null
            },
            onConfirm = { sourceId, targetId, label, directed ->
                val edge = RelationshipGraphEdge(
                    id = UUID.randomUUID().toString(),
                    source = sourceId,
                    target = targetId,
                    label = label,
                    directed = directed,
                )
                persistGraphs(customGraphs.map { if (it.id == selectedGraph.id) it.copy(edges = it.edges + edge) else it })
                showRelationDialog = false
                preselectedRelationSourceId = null
            },
        )
    }

    if (showRelationsManager && selectedGraph != null) {
        RelationsManagerDialog(
            graph = selectedGraph,
            onDismiss = { showRelationsManager = false },
            onAdd = {
                showRelationsManager = false
                preselectedRelationSourceId = null
                showRelationDialog = true
            },
            onDelete = { edgeId ->
                persistGraphs(
                    customGraphs.map {
                        if (it.id == selectedGraph.id) it.copy(edges = it.edges.filterNot { edge -> edge.id == edgeId }) else it
                    },
                )
            },
        )
    }
}

@Composable
private fun RelationshipGraphSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    nodeCount: Int,
    edgeCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            label = { Text("筛选节点") },
            singleLine = true,
        )
        Text(
            text = "$nodeCount 点 · $edgeCount 线",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CustomGraphSelector(
    graphs: List<CustomRelationshipGraph>,
    selectedGraphId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = graphs.firstOrNull { it.id == selectedGraphId } ?: graphs.firstOrNull()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selected?.title.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            graphs.forEach { graph ->
                DropdownMenuItem(
                    text = { Text(graph.title) },
                    onClick = {
                        expanded = false
                        onSelect(graph.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun GraphEmptyHint(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.AccountTree,
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectedCustomNodeCard(
    node: RelationshipGraphNode,
    onEdit: () -> Unit,
    onAddRelation: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(node.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            if (node.group.isNotBlank()) {
                Text(
                    node.group,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onAddRelation) { Text("连线") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun RelationshipGraphSettingsDialog(
    settings: RelationshipGraphSettings,
    onSettingsChange: (RelationshipGraphSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关系图设置") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SettingSwitchRow(
                        title = "显示孤立节点",
                        checked = settings.showOrphans,
                        onCheckedChange = { onSettingsChange(settings.copy(showOrphans = it)) },
                    )
                }
                item {
                    SettingSwitchRow(
                        title = "显示连线方向",
                        checked = settings.showArrows,
                        onCheckedChange = { onSettingsChange(settings.copy(showArrows = it)) },
                    )
                }
                item {
                    GraphSliderSetting("节点大小", settings.nodeScale, 0.6f..2.2f) {
                        onSettingsChange(settings.copy(nodeScale = it))
                    }
                }
                item {
                    GraphSliderSetting("连线粗细", settings.linkScale, 0.5f..2.5f) {
                        onSettingsChange(settings.copy(linkScale = it))
                    }
                }
                item {
                    GraphSliderSetting("文本淡化", settings.labelFade, 0f..1f) {
                        onSettingsChange(settings.copy(labelFade = it))
                    }
                }
                item { HorizontalDivider() }
                item {
                    GraphSliderSetting("图谱向心力", settings.centerForce, 0.2f..2.4f) {
                        onSettingsChange(settings.copy(centerForce = it))
                    }
                }
                item {
                    GraphSliderSetting("节点排斥力", settings.repelForce, 0.2f..2.8f) {
                        onSettingsChange(settings.copy(repelForce = it))
                    }
                }
                item {
                    GraphSliderSetting("相连节点吸引力", settings.linkForce, 0.2f..2.4f) {
                        onSettingsChange(settings.copy(linkForce = it))
                    }
                }
                item {
                    GraphSliderSetting("连线长度", settings.linkDistance, 0.5f..2.4f) {
                        onSettingsChange(settings.copy(linkDistance = it))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = { onSettingsChange(RelationshipGraphSettings()) }) { Text("恢复默认") }
        },
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun GraphSliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = String.format("%.1f", value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun TextValueDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = { onConfirm(value.trim()) },
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NodeEditorDialog(
    node: RelationshipGraphNode?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, group: String) -> Unit,
) {
    var name by remember(node?.id) { mutableStateOf(node?.title.orEmpty()) }
    var group by remember(node?.id) { mutableStateOf(node?.group.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (node == null) "添加人物" else "编辑人物") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("人物名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text("分组（可选）") },
                    supportingText = { Text("例如：主角阵营、王室、反派") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), group.trim()) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RelationEditorDialog(
    graph: CustomRelationshipGraph,
    initialSourceId: String?,
    onDismiss: () -> Unit,
    onConfirm: (sourceId: String, targetId: String, label: String, directed: Boolean) -> Unit,
) {
    var sourceId by remember(graph.id, initialSourceId) {
        mutableStateOf(initialSourceId ?: graph.nodes.firstOrNull()?.id.orEmpty())
    }
    var targetId by remember(graph.id, initialSourceId) {
        mutableStateOf(graph.nodes.firstOrNull { it.id != sourceId }?.id.orEmpty())
    }
    var label by remember(graph.id, initialSourceId) { mutableStateOf("") }
    var directed by remember(graph.id, initialSourceId) { mutableStateOf(true) }
    val valid = sourceId.isNotBlank() && targetId.isNotBlank() && sourceId != targetId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加人物关系") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GraphNodeChoiceField(
                    label = "起点人物",
                    nodes = graph.nodes,
                    selectedId = sourceId,
                    onSelect = { selected ->
                        sourceId = selected
                        if (targetId == selected) {
                            targetId = graph.nodes.firstOrNull { it.id != selected }?.id.orEmpty()
                        }
                    },
                )
                GraphNodeChoiceField(
                    label = "终点人物",
                    nodes = graph.nodes,
                    selectedId = targetId,
                    onSelect = { targetId = it },
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("关系名称（可选）") },
                    supportingText = { Text("例如：朋友、父女、敌对、暗恋") },
                    singleLine = true,
                )
                SettingSwitchRow(
                    title = "有方向关系",
                    checked = directed,
                    onCheckedChange = { directed = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(sourceId, targetId, label.trim(), directed) },
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun GraphNodeChoiceField(
    label: String,
    nodes: List<RelationshipGraphNode>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = nodes.firstOrNull { it.id == selectedId }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected?.title ?: "请选择", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            nodes.forEach { node ->
                DropdownMenuItem(
                    text = { Text(node.title) },
                    onClick = {
                        expanded = false
                        onSelect(node.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun RelationsManagerDialog(
    graph: CustomRelationshipGraph,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val nodeTitles = remember(graph.nodes) { graph.nodes.associate { it.id to it.title } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("人物关系") },
        text = {
            if (graph.edges.isEmpty()) {
                Text("还没有人物关系。")
            } else {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(graph.edges, key = { it.id }) { edge ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${nodeTitles[edge.source].orEmpty()} ${if (edge.directed) "→" else "—"} ${nodeTitles[edge.target].orEmpty()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (edge.label.isNotBlank()) {
                                    Text(
                                        edge.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { onDelete(edge.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除关系")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = graph.nodes.size >= 2,
                onClick = onAdd,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("添加关系")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun buildNoteRelationshipGraph(
    notes: List<Note>,
    links: List<NoteLinkEntity>,
): RelationshipGraphData {
    val noteByPath = notes.associateBy { normalizeGraphPath(it.file.path) }
    val edges = links.asSequence()
        .filter { it.resolutionStatus == NoteLinkResolutionStatus.RESOLVED }
        .mapNotNull { link ->
            val source = normalizeGraphPath(link.sourcePath)
            val target = normalizeGraphPath(link.targetPath.orEmpty())
            if (source == target || source !in noteByPath || target !in noteByPath) return@mapNotNull null
            RelationshipGraphEdge(
                id = "$source->$target",
                source = source,
                target = target,
                directed = true,
            )
        }
        .distinctBy { it.id }
        .toList()
    val nodes = notes.map { note ->
        val path = normalizeGraphPath(note.file.path)
        RelationshipGraphNode(
            id = path,
            title = note.title.ifBlank { note.file.nameWithoutExtension },
            subtitle = path,
            group = normalizeGraphPath(note.folder).substringBefore('/'),
        )
    }
    return RelationshipGraphData(nodes = nodes, edges = edges)
}

private fun normalizeGraphPath(path: String): String = path.replace('\\', '/').trim('/')

private fun defaultCustomRelationshipGraph(): CustomRelationshipGraph =
    CustomRelationshipGraph(
        id = UUID.randomUUID().toString(),
        title = "人物关系图",
    )

private class CustomRelationshipGraphStore(context: Context) {
    private val atomicFile = AtomicFile(File(context.filesDir, "custom_relationship_graphs.json"))

    fun load(): List<CustomRelationshipGraph> = runCatching {
        if (!atomicFile.baseFile.exists()) return emptyList()
        val raw = atomicFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val root = JSONObject(raw)
        val array = root.optJSONArray("graphs") ?: JSONArray()
        buildList {
            for (index in 0 until array.length()) {
                val graphObject = array.optJSONObject(index) ?: continue
                val graphId = graphObject.optString("id").ifBlank { UUID.randomUUID().toString() }
                val title = graphObject.optString("title").ifBlank { "人物关系图" }
                val nodesArray = graphObject.optJSONArray("nodes") ?: JSONArray()
                val nodes = buildList {
                    for (nodeIndex in 0 until nodesArray.length()) {
                        val node = nodesArray.optJSONObject(nodeIndex) ?: continue
                        val id = node.optString("id").ifBlank { UUID.randomUUID().toString() }
                        val name = node.optString("title").trim()
                        if (name.isBlank()) continue
                        add(
                            RelationshipGraphNode(
                                id = id,
                                title = name,
                                subtitle = node.optString("subtitle"),
                                group = node.optString("group"),
                                color = node.optString("color").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
                val nodeIds = nodes.map { it.id }.toSet()
                val edgesArray = graphObject.optJSONArray("edges") ?: JSONArray()
                val edges = buildList {
                    for (edgeIndex in 0 until edgesArray.length()) {
                        val edge = edgesArray.optJSONObject(edgeIndex) ?: continue
                        val source = edge.optString("source")
                        val target = edge.optString("target")
                        if (source !in nodeIds || target !in nodeIds || source == target) continue
                        add(
                            RelationshipGraphEdge(
                                id = edge.optString("id").ifBlank { UUID.randomUUID().toString() },
                                source = source,
                                target = target,
                                label = edge.optString("label"),
                                directed = edge.optBoolean("directed", true),
                            ),
                        )
                    }
                }
                add(CustomRelationshipGraph(id = graphId, title = title, nodes = nodes, edges = edges))
            }
        }
    }.getOrDefault(emptyList())

    fun save(graphs: List<CustomRelationshipGraph>) {
        val root = JSONObject().put(
            "graphs",
            JSONArray().apply {
                graphs.forEach { graph ->
                    put(
                        JSONObject()
                            .put("id", graph.id)
                            .put("title", graph.title)
                            .put(
                                "nodes",
                                JSONArray().apply {
                                    graph.nodes.forEach { node ->
                                        put(
                                            JSONObject()
                                                .put("id", node.id)
                                                .put("title", node.title)
                                                .put("subtitle", node.subtitle)
                                                .put("group", node.group)
                                                .put("color", node.color ?: ""),
                                        )
                                    }
                                },
                            )
                            .put(
                                "edges",
                                JSONArray().apply {
                                    graph.edges.forEach { edge ->
                                        put(
                                            JSONObject()
                                                .put("id", edge.id)
                                                .put("source", edge.source)
                                                .put("target", edge.target)
                                                .put("label", edge.label)
                                                .put("directed", edge.directed),
                                        )
                                    }
                                },
                            ),
                    )
                }
            },
        )
        val bytes = root.toString().toByteArray(StandardCharsets.UTF_8)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            output.flush()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RelationshipGraphWebView(
    graph: RelationshipGraphData,
    settings: RelationshipGraphSettings,
    query: String,
    isDark: Boolean,
    showEdgeLabels: Boolean,
    onNodeClick: (String) -> Unit,
    onNodeLongPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnNodeClick = rememberUpdatedState(onNodeClick)
    val currentOnNodeLongPress = rememberUpdatedState(onNodeLongPress)
    val html = remember(isDark) { buildRelationshipGraphHtml(isDark) }
    val updateScript = remember(graph, settings, query, showEdgeLabels) {
        buildRelationshipGraphUpdateScript(graph, settings, query, showEdgeLabels)
    }
    val signature = remember(graph, settings, query, isDark, showEdgeLabels) {
        graph.nodes.joinToString("|") { it.id + it.title + it.group }.hashCode().toString() + ":" +
            graph.edges.joinToString("|") { it.id + it.source + it.target + it.label }.hashCode() + ":" +
            settings.hashCode() + ":" + query.hashCode() + ":" + isDark + ":" + showEdgeLabels
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(0)
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isLongClickable = false
                this.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    textZoom = 100
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                }
                val state = RelationshipGraphWebViewState(Handler(Looper.getMainLooper()))
                tag = state
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.isForMainFrame && !isAllowedRelationshipGraphNavigation(request.url)

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                        !isAllowedRelationshipGraphNavigation(Uri.parse(url))

                    override fun onPageFinished(view: WebView, url: String?) {
                        state.pageReady = true
                        applyPendingRelationshipGraphUpdate(view, state)
                    }
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onNodeClick(nodeId: String) {
                            state.postIfActive { currentOnNodeClick.value(nodeId) }
                        }

                        @JavascriptInterface
                        fun onNodeLongPress(nodeId: String) {
                            state.postIfActive { currentOnNodeLongPress.value(nodeId) }
                        }
                    },
                    RELATIONSHIP_GRAPH_BRIDGE_NAME,
                )
            }
        },
        update = { webView ->
            val state = webView.tag as? RelationshipGraphWebViewState ?: return@AndroidView
            state.pendingSignature = signature
            state.pendingScript = updateScript
            if (state.themeIsDark != isDark) {
                state.themeIsDark = isDark
                state.pageReady = false
                state.appliedSignature = null
                webView.loadDataWithBaseURL(
                    RELATIONSHIP_GRAPH_BASE_URL,
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            } else if (state.pageReady) {
                applyPendingRelationshipGraphUpdate(webView, state)
            }
        },
        onRelease = { webView ->
            val state = webView.tag as? RelationshipGraphWebViewState
            if (state?.released == true) return@AndroidView
            state?.released = true
            state?.mainHandler?.removeCallbacksAndMessages(null)
            webView.stopLoading()
            webView.removeJavascriptInterface(RELATIONSHIP_GRAPH_BRIDGE_NAME)
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        },
    )
}

private data class RelationshipGraphWebViewState(
    val mainHandler: Handler,
    var themeIsDark: Boolean? = null,
    var pageReady: Boolean = false,
    var pendingSignature: String? = null,
    var appliedSignature: String? = null,
    var pendingScript: String? = null,
    @Volatile
    var released: Boolean = false,
) {
    fun postIfActive(action: () -> Unit) {
        if (released) return
        mainHandler.post { if (!released) action() }
    }
}

private const val RELATIONSHIP_GRAPH_BASE_URL = "https://kardleaf.local/relationship-graph/"
private const val RELATIONSHIP_GRAPH_BRIDGE_NAME = "KardLeafRelationshipGraph"
private val RELATIONSHIP_GRAPH_BASE_URI: Uri = Uri.parse(RELATIONSHIP_GRAPH_BASE_URL)

private fun isAllowedRelationshipGraphNavigation(uri: Uri): Boolean {
    val basePath = RELATIONSHIP_GRAPH_BASE_URI.path.orEmpty()
    return uri.scheme.equals(RELATIONSHIP_GRAPH_BASE_URI.scheme, ignoreCase = true) &&
        uri.host.equals(RELATIONSHIP_GRAPH_BASE_URI.host, ignoreCase = true) &&
        uri.userInfo == null &&
        (uri.port == -1 || uri.port == 443) &&
        uri.path.orEmpty() == basePath
}

private fun applyPendingRelationshipGraphUpdate(
    webView: WebView,
    state: RelationshipGraphWebViewState,
) {
    val signature = state.pendingSignature ?: return
    val script = state.pendingScript ?: return
    if (state.appliedSignature == signature) return
    state.appliedSignature = signature
    webView.evaluateJavascript(script, null)
}

private fun buildRelationshipGraphUpdateScript(
    graph: RelationshipGraphData,
    settings: RelationshipGraphSettings,
    query: String,
    showEdgeLabels: Boolean,
): String {
    val nodes = JSONArray().apply {
        graph.nodes.forEach { node ->
            put(
                JSONObject()
                    .put("id", node.id)
                    .put("title", node.title)
                    .put("subtitle", node.subtitle)
                    .put("group", node.group)
                    .put("color", node.color ?: ""),
            )
        }
    }
    val edges = JSONArray().apply {
        graph.edges.forEach { edge ->
            put(
                JSONObject()
                    .put("id", edge.id)
                    .put("source", edge.source)
                    .put("target", edge.target)
                    .put("label", edge.label)
                    .put("directed", edge.directed),
            )
        }
    }
    val payload = JSONObject()
        .put("nodes", nodes)
        .put("edges", edges)
        .put("query", query)
        .put("showEdgeLabels", showEdgeLabels)
        .put(
            "settings",
            JSONObject()
                .put("showOrphans", settings.showOrphans)
                .put("showArrows", settings.showArrows)
                .put("nodeScale", settings.nodeScale.toDouble())
                .put("linkScale", settings.linkScale.toDouble())
                .put("labelFade", settings.labelFade.toDouble())
                .put("centerForce", settings.centerForce.toDouble())
                .put("repelForce", settings.repelForce.toDouble())
                .put("linkForce", settings.linkForce.toDouble())
                .put("linkDistance", settings.linkDistance.toDouble()),
        )
    return "window.KardLeafRelationshipGraphUpdate && window.KardLeafRelationshipGraphUpdate(${payload});"
}

private fun buildRelationshipGraphHtml(isDark: Boolean): String {
    val background = if (isDark) "#111418" else "#fbfbfc"
    val foreground = if (isDark) "#d8dde4" else "#30343a"
    val nodeColor = if (isDark) "#aeb6c2" else "#63676d"
    val edgeColor = if (isDark) "rgba(171,181,194,.34)" else "rgba(85,92,103,.24)"
    val panel = if (isDark) "rgba(34,39,46,.90)" else "rgba(255,255,255,.92)"
    val selected = if (isDark) "#8ab4f8" else "#3f6fd9"
    return """
<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
<title>KardLeaf Relationship Graph</title>
<style>
:root {
  color-scheme: ${if (isDark) "dark" else "light"};
  --bg: $background;
  --fg: $foreground;
  --node: $nodeColor;
  --edge: $edgeColor;
  --panel: $panel;
  --selected: $selected;
}
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; -webkit-user-select: none; user-select: none; }
html, body { width: 100%; height: 100%; margin: 0; overflow: hidden; background: var(--bg); font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
#stage { position: relative; width: 100%; height: 100%; touch-action: none; overflow: hidden; }
canvas { display: block; width: 100%; height: 100%; }
#toolbar { position: absolute; right: 12px; top: 12px; display: flex; gap: 7px; padding: 6px; border-radius: 16px; background: var(--panel); box-shadow: 0 8px 28px rgba(0,0,0,.14); backdrop-filter: blur(12px); }
#toolbar button { width: 34px; height: 34px; border: 0; border-radius: 11px; background: transparent; color: var(--fg); font-size: 19px; font-weight: 650; }
#toolbar button:active { background: rgba(127,127,127,.18); }
#status { position: absolute; left: 12px; bottom: 10px; padding: 5px 9px; border-radius: 10px; background: var(--panel); color: var(--fg); font-size: 11px; opacity: .72; }
</style>
</head>
<body>
<div id="stage">
  <canvas id="canvas"></canvas>
  <div id="toolbar">
    <button id="zoomOut" aria-label="缩小">−</button>
    <button id="zoomIn" aria-label="放大">+</button>
    <button id="fit" aria-label="适应屏幕">⌂</button>
  </div>
  <div id="status">0 点 · 0 线</div>
</div>
<script>
(() => {
  const canvas = document.getElementById('canvas');
  const stage = document.getElementById('stage');
  const status = document.getElementById('status');
  const ctx = canvas.getContext('2d');
  const dpr = Math.max(1, Math.min(2, window.devicePixelRatio || 1));
  const palette = ['#5b8ff9','#61d9a3','#f6bd5a','#e8688a','#9270ca','#6dc8ec','#ff9d4d','#5ad8a6'];
  let width = 1, height = 1;
  let allNodes = [], allEdges = [], nodes = [], edges = [], nodeById = new Map();
  let settings = { showOrphans:true, showArrows:false, nodeScale:1, linkScale:1, labelFade:.55, centerForce:1, repelForce:1, linkForce:1, linkDistance:1 };
  let query = '';
  let showEdgeLabels = false;
  let tx = 0, ty = 0, scale = 1;
  let selectedId = null, hoverId = null;
  let alpha = 0, frameRequested = false, lastVisibleKey = '';
  let pointerStart = null, dragNode = null, panStart = null, pinchStart = null;
  let longPressTimer = null, longPressTriggered = false;
  const pointers = new Map();

  function resize() {
    const rect = stage.getBoundingClientRect();
    width = Math.max(1, rect.width);
    height = Math.max(1, rect.height);
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    canvas.style.width = width + 'px';
    canvas.style.height = height + 'px';
    requestDraw();
  }

  function hash(value) {
    let h = 2166136261;
    for (let i = 0; i < value.length; i++) { h ^= value.charCodeAt(i); h = Math.imul(h, 16777619); }
    return h >>> 0;
  }

  function groupColor(node) {
    if (node.color) return node.color;
    if (!node.group) return getComputedStyle(document.documentElement).getPropertyValue('--node').trim();
    return palette[hash(node.group) % palette.length];
  }

  function worldToScreen(x, y) { return { x: x * scale + tx, y: y * scale + ty }; }
  function screenToWorld(x, y) { return { x: (x - tx) / scale, y: (y - ty) / scale }; }
  function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
  function nodeRadius(node) { return (3.2 + Math.sqrt(Math.max(0, node.degree || 0)) * 1.35) * settings.nodeScale; }
  function isOrphan(node) { return (node.degree || 0) === 0; }

  function buildAdjacency() {
    const adjacency = new Map(nodes.map(node => [node.id, []]));
    for (const edge of edges) {
      const source = nodeById.get(edge.source);
      const target = nodeById.get(edge.target);
      if (!source || !target) continue;
      adjacency.get(source.id).push(target);
      adjacency.get(target.id).push(source);
    }
    return adjacency;
  }

  function initializeLayout() {
    if (!nodes.length) return;
    const targetDistance = 76 * settings.linkDistance;
    const adjacency = buildAdjacency();
    const visited = new Set();
    const components = [];
    const orphans = [];

    for (const node of nodes) {
      node.manual = false;
      if (isOrphan(node)) {
        orphans.push(node);
        continue;
      }
      if (visited.has(node.id)) continue;
      const component = [];
      const queue = [node];
      let queueIndex = 0;
      visited.add(node.id);
      while (queueIndex < queue.length) {
        const current = queue[queueIndex++];
        component.push(current);
        for (const neighbor of adjacency.get(current.id) || []) {
          if (visited.has(neighbor.id)) continue;
          visited.add(neighbor.id);
          queue.push(neighbor);
        }
      }
      components.push(component);
    }

    components.sort((a, b) => b.length - a.length);
    const goldenAngle = Math.PI * (3 - Math.sqrt(5));
    let connectedExtent = targetDistance * 1.4;

    components.forEach((component, componentIndex) => {
      const componentRadius = targetDistance * (1.05 + Math.sqrt(component.length) * .52);
      const centerAngle = componentIndex * goldenAngle;
      const centerRadius = componentIndex === 0 ? 0 : targetDistance * 2.7 * Math.sqrt(componentIndex);
      const centerX = Math.cos(centerAngle) * centerRadius;
      const centerY = Math.sin(centerAngle) * centerRadius;
      connectedExtent = Math.max(connectedExtent, centerRadius + componentRadius);

      const componentIds = new Set(component.map(item => item.id));
      const hub = component.reduce((best, candidate) =>
        (candidate.degree || 0) > (best.degree || 0) ? candidate : best,
      component[0]);
      const levelById = new Map([[hub.id, 0]]);
      const queue = [hub];
      let queueIndex = 0;
      while (queueIndex < queue.length) {
        const current = queue[queueIndex++];
        const nextLevel = (levelById.get(current.id) || 0) + 1;
        for (const neighbor of adjacency.get(current.id) || []) {
          if (!componentIds.has(neighbor.id) || levelById.has(neighbor.id)) continue;
          levelById.set(neighbor.id, nextLevel);
          queue.push(neighbor);
        }
      }
      const byLevel = new Map();
      for (const item of component) {
        const level = levelById.get(item.id) || 1;
        if (!byLevel.has(level)) byLevel.set(level, []);
        byLevel.get(level).push(item);
      }
      hub.x = centerX;
      hub.y = centerY;
      hub.vx = 0;
      hub.vy = 0;
      hub.componentX = centerX;
      hub.componentY = centerY;
      hub.componentIndex = componentIndex;

      for (const [level, levelNodes] of byLevel.entries()) {
        if (level === 0) continue;
        levelNodes.sort((a, b) => hash(a.id) - hash(b.id));
        const ringRadius = targetDistance * (.78 + level * .72);
        const angleOffset = (hash(hub.id + ':' + level) % 6283) / 1000;
        levelNodes.forEach((item, itemIndex) => {
          const angle = angleOffset + itemIndex / Math.max(1, levelNodes.length) * Math.PI * 2;
          item.x = centerX + Math.cos(angle) * ringRadius;
          item.y = centerY + Math.sin(angle) * ringRadius;
          item.vx = 0;
          item.vy = 0;
          item.componentX = centerX;
          item.componentY = centerY;
          item.componentIndex = componentIndex;
        });
      }
    });

    const orphanGap = Math.max(24, 19 + settings.nodeScale * 10);
    const orphanStartRadius = components.length ? targetDistance * 1.25 : 0;
    orphans.sort((a, b) => hash(a.id) - hash(b.id));
    orphans.forEach((node, index) => {
      const radius = orphanStartRadius + orphanGap * Math.sqrt(index + 1);
      const angle = index * goldenAngle + (hash(node.id) % 997) / 997;
      node.x = Math.cos(angle) * radius;
      node.y = Math.sin(angle) * radius;
      node.vx = 0;
      node.vy = 0;
      node.orbitX = node.x;
      node.orbitY = node.y;
      node.componentIndex = -1;
    });
  }

  function rebuildVisibleGraph(resetLayout) {
    const degree = new Map();
    allNodes.forEach(n => degree.set(n.id, 0));
    allEdges.forEach(e => {
      if (degree.has(e.source) && degree.has(e.target)) {
        degree.set(e.source, (degree.get(e.source) || 0) + 1);
        degree.set(e.target, (degree.get(e.target) || 0) + 1);
      }
    });
    const needle = query.trim().toLowerCase();
    const previous = new Map(nodes.map(n => [n.id, n]));
    nodes = allNodes.filter(n => {
      const d = degree.get(n.id) || 0;
      if (!settings.showOrphans && d === 0) return false;
      if (!needle) return true;
      return (n.title + ' ' + n.subtitle + ' ' + n.group).toLowerCase().includes(needle);
    }).map(raw => {
      const old = previous.get(raw.id);
      return {
        ...raw,
        degree: degree.get(raw.id) || 0,
        x: old ? old.x : 0,
        y: old ? old.y : 0,
        vx: old ? old.vx : 0,
        vy: old ? old.vy : 0,
        manual: old ? !!old.manual : false,
      };
    });
    nodeById = new Map(nodes.map(n => [n.id, n]));
    edges = allEdges.filter(e => nodeById.has(e.source) && nodeById.has(e.target));
    if (selectedId && !nodeById.has(selectedId)) selectedId = null;

    const visibleKey = nodes.map(node => node.id).sort().join('\u0001') + '\u0002' +
      edges.map(edge => edge.source + '>' + edge.target).sort().join('\u0001');
    const layoutChanged = resetLayout || visibleKey !== lastVisibleKey || nodes.some(node => !Number.isFinite(node.x) || !Number.isFinite(node.y));
    lastVisibleKey = visibleKey;
    alpha = 1;
    if (layoutChanged) {
      initializeLayout();
      const settleIterations = nodes.length > 900 ? 48 : nodes.length > 450 ? 66 : 92;
      for (let index = 0; index < settleIterations; index++) simulate();
      fitGraph(false);
    }
    status.textContent = nodes.length + ' 点 · ' + edges.length + ' 线';
    requestDraw();
  }

  function simulate() {
    if (alpha < .002 || !nodes.length) return;
    const centerK = .00055 * settings.centerForce * alpha;
    const componentK = .0018 * settings.centerForce * alpha;
    const orbitK = .018 * alpha;
    const repelK = 620 * settings.repelForce * alpha;
    const springK = .0058 * settings.linkForce * alpha;
    const targetDistance = 76 * settings.linkDistance;
    const cellSize = Math.max(48, targetDistance * .82);
    const grid = new Map();

    for (const node of nodes) {
      if (!node.manual) {
        if (isOrphan(node) && Number.isFinite(node.orbitX)) {
          node.vx += (node.orbitX - node.x) * orbitK;
          node.vy += (node.orbitY - node.y) * orbitK;
        } else if (Number.isFinite(node.componentX)) {
          node.vx += (node.componentX - node.x) * componentK;
          node.vy += (node.componentY - node.y) * componentK;
        } else {
          node.vx += -node.x * centerK;
          node.vy += -node.y * centerK;
        }
      }
      const gx = Math.floor(node.x / cellSize);
      const gy = Math.floor(node.y / cellSize);
      const key = gx + ',' + gy;
      if (!grid.has(key)) grid.set(key, []);
      grid.get(key).push(node);
    }

    for (const node of nodes) {
      const gx = Math.floor(node.x / cellSize);
      const gy = Math.floor(node.y / cellSize);
      for (let dx = -2; dx <= 2; dx++) {
        for (let dy = -2; dy <= 2; dy++) {
          const bucket = grid.get((gx + dx) + ',' + (gy + dy));
          if (!bucket) continue;
          for (const other of bucket) {
            if (other === node || other.id < node.id) continue;
            let vx = node.x - other.x;
            let vy = node.y - other.y;
            let dist2 = vx * vx + vy * vy;
            if (dist2 < .01) { vx = .1; vy = .1; dist2 = .02; }
            const dist = Math.sqrt(dist2);
            const labelPadding = isOrphan(node) || isOrphan(other) ? 6 : 13;
            const minDist = nodeRadius(node) + nodeRadius(other) + labelPadding;
            const charge = repelK / Math.max(dist2, minDist * minDist * .24);
            const collision = dist < minDist ? (minDist - dist) * .075 * alpha : 0;
            const force = charge + collision;
            const fx = vx / dist * force;
            const fy = vy / dist * force;
            if (!node.manual) { node.vx += fx; node.vy += fy; }
            if (!other.manual) { other.vx -= fx; other.vy -= fy; }
          }
        }
      }
    }

    for (const edge of edges) {
      const source = nodeById.get(edge.source);
      const target = nodeById.get(edge.target);
      if (!source || !target) continue;
      const dx = target.x - source.x;
      const dy = target.y - source.y;
      const dist = Math.max(.001, Math.hypot(dx, dy));
      const delta = (dist - targetDistance) * springK;
      const totalDegree = Math.max(1, (source.degree || 0) + (target.degree || 0));
      const bias = (source.degree || 0) / totalDegree;
      const fx = dx / dist * delta;
      const fy = dy / dist * delta;
      if (!source.manual) {
        source.vx += fx * (1 - bias);
        source.vy += fy * (1 - bias);
      }
      if (!target.manual) {
        target.vx -= fx * bias;
        target.vy -= fy * bias;
      }
    }

    for (const node of nodes) {
      if (dragNode && node.id === dragNode.id) continue;
      if (node.manual) {
        node.vx = 0;
        node.vy = 0;
        continue;
      }
      node.vx *= .82;
      node.vy *= .82;
      node.x += node.vx;
      node.y += node.vy;
    }
    alpha *= .965;
  }

  function connectedToSelected(id) {
    if (!selectedId) return false;
    return edges.some(e => (e.source === selectedId && e.target === id) || (e.target === selectedId && e.source === id));
  }

  function drawArrow(source, target, color, widthPx) {
    const dx = target.x - source.x;
    const dy = target.y - source.y;
    const dist = Math.max(.001, Math.hypot(dx, dy));
    const targetRadius = nodeRadius(target) + 2;
    const endX = target.x - dx / dist * targetRadius;
    const endY = target.y - dy / dist * targetRadius;
    const size = 5.5 / Math.max(.7, Math.sqrt(scale));
    const angle = Math.atan2(dy, dx);
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.moveTo(endX, endY);
    ctx.lineTo(endX - Math.cos(angle - .5) * size, endY - Math.sin(angle - .5) * size);
    ctx.lineTo(endX - Math.cos(angle + .5) * size, endY - Math.sin(angle + .5) * size);
    ctx.closePath();
    ctx.fill();
  }

  function rectanglesOverlap(first, second, padding) {
    return !(first.right + padding <= second.left || first.left >= second.right + padding || first.bottom + padding <= second.top || first.top >= second.bottom + padding);
  }

  function ellipsizeText(text, maxWidth) {
    const value = String(text || '');
    if (ctx.measureText(value).width <= maxWidth) return value;
    let low = 0;
    let high = value.length;
    while (low < high) {
      const middle = Math.ceil((low + high) / 2);
      if (ctx.measureText(value.slice(0, middle) + '…').width <= maxWidth) low = middle;
      else high = middle - 1;
    }
    return value.slice(0, Math.max(1, low)) + '…';
  }

  function drawNodeLabels(candidates, foreground, background) {
    const occupied = [];
    const nodeBoxes = nodes.map(node => {
      const point = worldToScreen(node.x, node.y);
      const radius = Math.max(3, nodeRadius(node) * scale) + 2;
      return { id:node.id, left:point.x-radius, top:point.y-radius, right:point.x+radius, bottom:point.y+radius };
    });
    const smallGraph = nodes.length <= 48;
    const baseThreshold = .84 + settings.labelFade * 1.5;

    candidates.sort((a, b) => b.priority - a.priority);
    for (const candidate of candidates) {
      const node = candidate.node;
      const orphan = isOrphan(node);
      const degreeBonus = Math.min(.58, Math.sqrt(Math.max(0, node.degree || 0)) * .17);
      const visible = candidate.mustShow || candidate.highlighted ||
        (smallGraph && scale >= .46) ||
        (!orphan && scale >= baseThreshold - degreeBonus) ||
        (orphan && scale >= baseThreshold + .9);
      if (!visible) continue;

      const point = worldToScreen(node.x, node.y);
      const radius = Math.max(3, nodeRadius(node) * scale);
      const fontSize = candidate.mustShow ? 12.5 : orphan ? 9.5 : smallGraph ? 11.5 : 10.5;
      ctx.font = (candidate.selected ? '600 ' : '500 ') + fontSize + 'px system-ui, sans-serif';
      const maxWidth = Math.max(72, Math.min(190, width * .44));
      const text = ellipsizeText(node.title, maxWidth);
      const textWidth = ctx.measureText(text).width;
      const textHeight = fontSize * 1.22;
      const gap = 4;
      const placements = [
        { x:point.x, y:point.y + radius + gap, align:'center', baseline:'top', box:{ left:point.x-textWidth/2-3, top:point.y+radius+gap-2, right:point.x+textWidth/2+3, bottom:point.y+radius+gap+textHeight+2 } },
        { x:point.x, y:point.y - radius - gap, align:'center', baseline:'bottom', box:{ left:point.x-textWidth/2-3, top:point.y-radius-gap-textHeight-2, right:point.x+textWidth/2+3, bottom:point.y-radius-gap+2 } },
        { x:point.x + radius + gap, y:point.y, align:'left', baseline:'middle', box:{ left:point.x+radius+gap-2, top:point.y-textHeight/2-2, right:point.x+radius+gap+textWidth+4, bottom:point.y+textHeight/2+2 } },
        { x:point.x - radius - gap, y:point.y, align:'right', baseline:'middle', box:{ left:point.x-radius-gap-textWidth-4, top:point.y-textHeight/2-2, right:point.x-radius-gap+2, bottom:point.y+textHeight/2+2 } },
      ];

      let chosen = null;
      for (const placement of placements) {
        const box = placement.box;
        if (box.left < 2 || box.top < 2 || box.right > width - 2 || box.bottom > height - 2) continue;
        const overlapsLabel = occupied.some(other => rectanglesOverlap(box, other, 2));
        const overlapsNode = nodeBoxes.some(other => other.id !== node.id && rectanglesOverlap(box, other, 1));
        if (!overlapsLabel && !overlapsNode) {
          chosen = placement;
          break;
        }
      }
      if (!chosen && candidate.mustShow) chosen = placements[0];
      if (!chosen) continue;

      occupied.push(chosen.box);
      ctx.textAlign = chosen.align;
      ctx.textBaseline = chosen.baseline;
      ctx.lineJoin = 'round';
      ctx.lineWidth = 3.4;
      ctx.strokeStyle = background;
      ctx.fillStyle = foreground;
      ctx.globalAlpha = candidate.muted ? .18 : candidate.highlighted || candidate.mustShow ? 1 : clamp((scale - baseThreshold + .55) / .55, .28, .9);
      ctx.strokeText(text, chosen.x, chosen.y);
      ctx.fillText(text, chosen.x, chosen.y);
    }
  }

  function draw() {
    frameRequested = false;
    simulate();
    const rootStyle = getComputedStyle(document.documentElement);
    const foreground = rootStyle.getPropertyValue('--fg').trim();
    const background = rootStyle.getPropertyValue('--bg').trim();
    const selectedColor = rootStyle.getPropertyValue('--selected').trim();
    const defaultEdgeColor = rootStyle.getPropertyValue('--edge').trim();
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, width, height);
    ctx.save();
    ctx.translate(tx, ty);
    ctx.scale(scale, scale);

    for (const edge of edges) {
      const source = nodeById.get(edge.source);
      const target = nodeById.get(edge.target);
      if (!source || !target) continue;
      const highlighted = selectedId && (edge.source === selectedId || edge.target === selectedId);
      const muted = selectedId && !highlighted;
      const color = highlighted ? selectedColor : defaultEdgeColor;
      const dx = target.x - source.x;
      const dy = target.y - source.y;
      const distance = Math.max(.001, Math.hypot(dx, dy));
      const ux = dx / distance;
      const uy = dy / distance;
      const startX = source.x + ux * (nodeRadius(source) + 1.5);
      const startY = source.y + uy * (nodeRadius(source) + 1.5);
      const endX = target.x - ux * (nodeRadius(target) + 1.5);
      const endY = target.y - uy * (nodeRadius(target) + 1.5);
      ctx.globalAlpha = muted ? .1 : highlighted ? .96 : .82;
      ctx.strokeStyle = color;
      ctx.lineWidth = (highlighted ? 1.5 : .78) * settings.linkScale / Math.max(.65, Math.sqrt(scale));
      ctx.beginPath();
      ctx.moveTo(startX, startY);
      ctx.lineTo(endX, endY);
      ctx.stroke();
      if (settings.showArrows && edge.directed) drawArrow(source, target, color, ctx.lineWidth);
      if (showEdgeLabels && edge.label && (scale > 1.05 || highlighted)) {
        const mx = (source.x + target.x) / 2;
        const my = (source.y + target.y) / 2;
        ctx.font = (10.5 / scale) + 'px system-ui, sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.lineWidth = 3 / scale;
        ctx.strokeStyle = background;
        ctx.fillStyle = foreground;
        ctx.globalAlpha = muted ? .14 : .82;
        ctx.strokeText(edge.label, mx, my - 5 / scale);
        ctx.fillText(edge.label, mx, my - 5 / scale);
      }
    }

    const labelCandidates = [];
    for (const node of nodes) {
      const selected = node.id === selectedId;
      const hovered = node.id === hoverId;
      const neighbor = connectedToSelected(node.id);
      const muted = selectedId && !selected && !neighbor;
      const radius = nodeRadius(node);
      ctx.globalAlpha = muted ? .16 : 1;
      ctx.fillStyle = selected ? selectedColor : groupColor(node);
      ctx.beginPath();
      ctx.arc(node.x, node.y, radius, 0, Math.PI * 2);
      ctx.fill();
      if (selected || hovered) {
        ctx.strokeStyle = foreground;
        ctx.lineWidth = 1.2 / scale;
        ctx.stroke();
      }
      labelCandidates.push({
        node,
        selected,
        muted,
        mustShow:selected || hovered,
        highlighted:neighbor || !!query,
        priority:(selected ? 10000 : hovered ? 9000 : neighbor ? 8000 : query ? 7000 : 0) + (node.degree || 0) * 20 - (isOrphan(node) ? 1000 : 0),
      });
    }
    ctx.restore();
    ctx.globalAlpha = 1;
    drawNodeLabels(labelCandidates, foreground, background);
    ctx.globalAlpha = 1;
    if (alpha >= .002 || dragNode) requestDraw();
  }

  function requestDraw() {
    if (frameRequested) return;
    frameRequested = true;
    requestAnimationFrame(draw);
  }

  function fitGraph(animated) {
    if (!nodes.length) { tx = width / 2; ty = height / 2; scale = 1; requestDraw(); return; }
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (const node of nodes) {
      const r = nodeRadius(node) + 22;
      minX = Math.min(minX, node.x - r); maxX = Math.max(maxX, node.x + r);
      minY = Math.min(minY, node.y - r); maxY = Math.max(maxY, node.y + r);
    }
    const graphW = Math.max(40, maxX - minX);
    const graphH = Math.max(40, maxY - minY);
    const nextScale = clamp(Math.min((width - 40) / graphW, (height - 40) / graphH), .18, 2.4);
    const nextTx = width / 2 - (minX + maxX) / 2 * nextScale;
    const nextTy = height / 2 - (minY + maxY) / 2 * nextScale;
    if (!animated) { scale = nextScale; tx = nextTx; ty = nextTy; requestDraw(); return; }
    const startScale = scale, startTx = tx, startTy = ty, start = performance.now();
    function step(now) {
      const t = Math.min(1, (now - start) / 240);
      const eased = 1 - Math.pow(1 - t, 3);
      scale = startScale + (nextScale - startScale) * eased;
      tx = startTx + (nextTx - startTx) * eased;
      ty = startTy + (nextTy - startTy) * eased;
      requestDraw();
      if (t < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
  }

  function zoomAt(factor, sx, sy) {
    const before = screenToWorld(sx, sy);
    scale = clamp(scale * factor, .12, 8);
    tx = sx - before.x * scale;
    ty = sy - before.y * scale;
    requestDraw();
  }

  function hitNode(sx, sy) {
    const point = screenToWorld(sx, sy);
    for (let i = nodes.length - 1; i >= 0; i--) {
      const node = nodes[i];
      const radius = nodeRadius(node) + 9 / scale;
      const dx = point.x - node.x;
      const dy = point.y - node.y;
      if (dx * dx + dy * dy <= radius * radius) return node;
    }
    return null;
  }

  function clearLongPress() {
    if (longPressTimer) clearTimeout(longPressTimer);
    longPressTimer = null;
  }

  stage.addEventListener('pointerdown', event => {
    stage.setPointerCapture(event.pointerId);
    pointers.set(event.pointerId, { x:event.clientX, y:event.clientY });
    longPressTriggered = false;
    pointerStart = { x:event.clientX, y:event.clientY, time:performance.now() };
    if (pointers.size === 2) {
      clearLongPress();
      const values = Array.from(pointers.values());
      const dx = values[1].x - values[0].x;
      const dy = values[1].y - values[0].y;
      pinchStart = { distance:Math.hypot(dx,dy), scale, tx, ty, center:{ x:(values[0].x+values[1].x)/2, y:(values[0].y+values[1].y)/2 }, world:screenToWorld((values[0].x+values[1].x)/2, (values[0].y+values[1].y)/2) };
      dragNode = null; panStart = null;
      return;
    }
    const hit = hitNode(event.clientX, event.clientY);
    if (hit) {
      dragNode = hit;
      hit.vx = 0; hit.vy = 0;
      longPressTimer = setTimeout(() => {
        longPressTriggered = true;
        if (window.KardLeafRelationshipGraph && window.KardLeafRelationshipGraph.onNodeLongPress) {
          window.KardLeafRelationshipGraph.onNodeLongPress(hit.id);
        }
      }, 520);
    } else {
      panStart = { x:event.clientX, y:event.clientY, tx, ty };
    }
  });

  stage.addEventListener('pointermove', event => {
    if (!pointers.has(event.pointerId)) {
      const hit = hitNode(event.clientX, event.clientY);
      hoverId = hit ? hit.id : null;
      requestDraw();
      return;
    }
    pointers.set(event.pointerId, { x:event.clientX, y:event.clientY });
    if (pointerStart && Math.hypot(event.clientX - pointerStart.x, event.clientY - pointerStart.y) > 9) clearLongPress();
    if (pointers.size >= 2 && pinchStart) {
      const values = Array.from(pointers.values()).slice(0,2);
      const dx = values[1].x - values[0].x;
      const dy = values[1].y - values[0].y;
      const distance = Math.max(1, Math.hypot(dx,dy));
      const center = { x:(values[0].x+values[1].x)/2, y:(values[0].y+values[1].y)/2 };
      scale = clamp(pinchStart.scale * distance / Math.max(1, pinchStart.distance), .12, 8);
      tx = center.x - pinchStart.world.x * scale;
      ty = center.y - pinchStart.world.y * scale;
      requestDraw();
      return;
    }
    if (dragNode) {
      const point = screenToWorld(event.clientX, event.clientY);
      dragNode.x = point.x; dragNode.y = point.y; dragNode.vx = 0; dragNode.vy = 0; dragNode.manual = true;
      alpha = Math.max(alpha, .12);
      requestDraw();
    } else if (panStart) {
      tx = panStart.tx + event.clientX - panStart.x;
      ty = panStart.ty + event.clientY - panStart.y;
      requestDraw();
    }
  });

  function finishPointer(event) {
    clearLongPress();
    const wasDragNode = dragNode;
    const duration = pointerStart ? performance.now() - pointerStart.time : 999;
    const distance = pointerStart ? Math.hypot(event.clientX - pointerStart.x, event.clientY - pointerStart.y) : 999;
    pointers.delete(event.pointerId);
    if (pointers.size < 2) pinchStart = null;
    if (wasDragNode && !longPressTriggered && duration < 380 && distance < 10) {
      selectedId = wasDragNode.id;
      if (window.KardLeafRelationshipGraph && window.KardLeafRelationshipGraph.onNodeClick) {
        window.KardLeafRelationshipGraph.onNodeClick(wasDragNode.id);
      }
    } else if (!wasDragNode && panStart && distance < 10) {
      selectedId = null;
    }
    dragNode = null;
    panStart = null;
    pointerStart = null;
    alpha = Math.max(alpha, .08);
    requestDraw();
  }
  stage.addEventListener('pointerup', finishPointer);
  stage.addEventListener('pointercancel', finishPointer);
  stage.addEventListener('wheel', event => {
    event.preventDefault();
    zoomAt(event.deltaY < 0 ? 1.12 : .89, event.clientX, event.clientY);
  }, { passive:false });

  document.getElementById('zoomIn').addEventListener('click', () => zoomAt(1.22, width/2, height/2));
  document.getElementById('zoomOut').addEventListener('click', () => zoomAt(.82, width/2, height/2));
  document.getElementById('fit').addEventListener('click', () => fitGraph(true));

  window.KardLeafRelationshipGraphUpdate = payload => {
    const oldNodeKey = allNodes.map(node => node.id).sort().join('\u0001');
    allNodes = Array.isArray(payload.nodes) ? payload.nodes : [];
    allEdges = Array.isArray(payload.edges) ? payload.edges : [];
    settings = { ...settings, ...(payload.settings || {}) };
    query = String(payload.query || '');
    showEdgeLabels = !!payload.showEdgeLabels;
    const newNodeKey = allNodes.map(node => node.id).sort().join('\u0001');
    rebuildVisibleGraph(oldNodeKey !== newNodeKey || !nodes.length);
  };

  resize();
  new ResizeObserver(resize).observe(stage);
})();
</script>
</body>
</html>
""".trimIndent()
}
