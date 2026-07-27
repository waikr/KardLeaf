package com.kangle.kardleaf.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownMindMapScreen(
    title: String,
    headings: List<MarkdownHeading>,
    isDark: Boolean,
    unavailableTitle: String? = null,
    unavailableMessage: String? = null,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onHeadingClick: (MarkdownHeading) -> Unit,
    onNodeReparent: (movingIndex: Int, parentIndex: Int) -> Unit = { _, _ -> },
    onNodeAddChild: (parentIndex: Int, title: String) -> Unit = { _, _ -> },
    onNodeAddSibling: (anchorIndex: Int, title: String) -> Unit = { _, _ -> },
    onNodeRename: (nodeIndex: Int, title: String) -> Unit = { _, _ -> },
    onNodeMove: (nodeIndex: Int, moveUp: Boolean) -> Unit = { _, _ -> },
    onNodeDelete: (nodeIndex: Int) -> Unit = {},
) {
    var pendingParentIndex by remember { mutableStateOf<Int?>(null) }
    var pendingSiblingIndex by remember { mutableStateOf<Int?>(null) }
    var pendingRenameIndex by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var newNodeTitle by remember { mutableStateOf("") }
    var newSiblingTitle by remember { mutableStateOf("") }
    var renamedNodeTitle by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "思维导图",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "关闭思维导图")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
                    unavailableTitle != null -> {
                        MindMapUnavailableHint(
                            title = title,
                            reasonTitle = unavailableTitle,
                            message = unavailableMessage.orEmpty(),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    headings.isEmpty() -> {
                        EmptyMindMapHint(
                            title = title,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        MarkdownMindMapWebView(
                            title = title,
                            headings = headings,
                            isDark = isDark,
                            onHeadingClick = onHeadingClick,
                            onNodeReparent = onNodeReparent,
                            onNodeAddChild = { parentIndex ->
                                pendingParentIndex = parentIndex
                                newNodeTitle = ""
                            },
                            onNodeAddSibling = { anchorIndex ->
                                pendingSiblingIndex = anchorIndex
                                newSiblingTitle = ""
                            },
                            onNodeRename = { nodeIndex ->
                                pendingRenameIndex = nodeIndex
                                renamedNodeTitle = headings.getOrNull(nodeIndex)?.text.orEmpty()
                            },
                            onNodeMove = onNodeMove,
                            onNodeDelete = { nodeIndex -> pendingDeleteIndex = nodeIndex },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        pendingParentIndex?.let { parentIndex ->
            val parentTitle = if (parentIndex >= 0) {
                headings.getOrNull(parentIndex)?.text?.ifBlank { "未命名节点" } ?: "当前节点"
            } else {
                title.ifBlank { "根节点" }
            }
            AlertDialog(
                onDismissRequest = { pendingParentIndex = null },
                title = { Text("添加子节点") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "添加到「$parentTitle」",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = newNodeTitle,
                            onValueChange = { newNodeTitle = it },
                            label = { Text("节点名称") },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = newNodeTitle.isNotBlank(),
                        onClick = {
                            onNodeAddChild(parentIndex, newNodeTitle)
                            pendingParentIndex = null
                        },
                    ) {
                        Text("添加")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingParentIndex = null }) {
                        Text("取消")
                    }
                },
            )
        }

        pendingSiblingIndex?.let { anchorIndex ->
            val anchorTitle = headings.getOrNull(anchorIndex)?.text?.ifBlank { "未命名节点" } ?: "当前节点"
            AlertDialog(
                onDismissRequest = { pendingSiblingIndex = null },
                title = { Text("添加同级节点") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "将插入到「$anchorTitle」之后",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = newSiblingTitle,
                            onValueChange = { newSiblingTitle = it },
                            label = { Text("节点名称") },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = newSiblingTitle.isNotBlank(),
                        onClick = {
                            onNodeAddSibling(anchorIndex, newSiblingTitle)
                            pendingSiblingIndex = null
                        },
                    ) {
                        Text("添加")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingSiblingIndex = null }) {
                        Text("取消")
                    }
                },
            )
        }

        pendingRenameIndex?.let { nodeIndex ->
            AlertDialog(
                onDismissRequest = { pendingRenameIndex = null },
                title = { Text("重命名节点") },
                text = {
                    OutlinedTextField(
                        value = renamedNodeTitle,
                        onValueChange = { renamedNodeTitle = it },
                        label = { Text("节点名称") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = renamedNodeTitle.isNotBlank(),
                        onClick = {
                            onNodeRename(nodeIndex, renamedNodeTitle)
                            pendingRenameIndex = null
                        },
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRenameIndex = null }) {
                        Text("取消")
                    }
                },
            )
        }

        pendingDeleteIndex?.let { nodeIndex ->
            val nodeTitle = headings.getOrNull(nodeIndex)?.text?.ifBlank { "未命名节点" } ?: "当前节点"
            AlertDialog(
                onDismissRequest = { pendingDeleteIndex = null },
                title = { Text("删除节点") },
                text = { Text("将删除「$nodeTitle」及其全部子节点，同时删除对应的 Markdown 内容。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onNodeDelete(nodeIndex)
                            pendingDeleteIndex = null
                        },
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteIndex = null }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptyMindMapHint(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
    ) {
        Text(
            text = title.ifBlank { "未命名笔记" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "思维导图会根据 Markdown 标题生成。你可以在笔记里添加 #、##、### 这类标题后再打开。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MindMapUnavailableHint(
    title: String,
    reasonTitle: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
    ) {
        Text(
            text = title.ifBlank { "未命名笔记" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = reasonTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = message.ifBlank { "当前内容不适合直接生成思维导图。" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MarkdownMindMapWebView(
    title: String,
    headings: List<MarkdownHeading>,
    isDark: Boolean,
    onHeadingClick: (MarkdownHeading) -> Unit,
    onNodeReparent: (movingIndex: Int, parentIndex: Int) -> Unit,
    onNodeAddChild: (parentIndex: Int) -> Unit,
    onNodeAddSibling: (anchorIndex: Int) -> Unit,
    onNodeRename: (nodeIndex: Int) -> Unit,
    onNodeMove: (nodeIndex: Int, moveUp: Boolean) -> Unit,
    onNodeDelete: (nodeIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnHeadingClick = rememberUpdatedState(onHeadingClick)
    val currentOnNodeReparent = rememberUpdatedState(onNodeReparent)
    val currentOnNodeAddChild = rememberUpdatedState(onNodeAddChild)
    val currentOnNodeAddSibling = rememberUpdatedState(onNodeAddSibling)
    val currentOnNodeRename = rememberUpdatedState(onNodeRename)
    val currentOnNodeMove = rememberUpdatedState(onNodeMove)
    val currentOnNodeDelete = rememberUpdatedState(onNodeDelete)
    val currentHeadings = rememberUpdatedState(headings)
    val html = rememberMindMapHtml(isDark)
    val signature = rememberMindMapSignature(title, headings, isDark)
    val updateScript = rememberMindMapUpdateScript(title, headings)

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
                isLongClickable = true
                isHapticFeedbackEnabled = false
                setOnLongClickListener { true }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.textZoom = 100
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                val state = MindMapWebViewState(Handler(Looper.getMainLooper()))
                tag = state
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = request.isForMainFrame && !isAllowedMindMapNavigation(request.url)

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        url: String,
                    ): Boolean = !isAllowedMindMapNavigation(Uri.parse(url))

                    override fun onPageFinished(view: WebView, url: String?) {
                        state.pageReady = true
                        applyPendingMindMapUpdate(view, state)
                    }
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onNodeClick(index: Int) {
                            state.postIfActive {
                                currentHeadings.value.getOrNull(index)?.let { currentOnHeadingClick.value(it) }
                            }
                        }

                        @JavascriptInterface
                        fun onNodeReparent(movingIndex: Int, parentIndex: Int) {
                            state.postIfActive {
                                currentOnNodeReparent.value(movingIndex, parentIndex)
                            }
                        }

                        @JavascriptInterface
                        fun onNodeAddChild(parentIndex: Int) {
                            state.postIfActive {
                                currentOnNodeAddChild.value(parentIndex)
                            }
                        }

                        @JavascriptInterface
                        fun onNodeAddSibling(anchorIndex: Int) {
                            state.postIfActive {
                                currentOnNodeAddSibling.value(anchorIndex)
                            }
                        }

                        @JavascriptInterface
                        fun onNodeRename(index: Int) {
                            state.postIfActive {
                                currentOnNodeRename.value(index)
                            }
                        }

                        @JavascriptInterface
                        fun onNodeMove(index: Int, moveUp: Boolean) {
                            state.postIfActive {
                                currentOnNodeMove.value(index, moveUp)
                            }
                        }

                        @JavascriptInterface
                        fun onNodeDelete(index: Int) {
                            state.postIfActive {
                                currentOnNodeDelete.value(index)
                            }
                        }
                    },
                    MIND_MAP_BRIDGE_NAME,
                )
            }
        },
        update = { webView ->
            val state = webView.tag as? MindMapWebViewState ?: return@AndroidView
            state.pendingSignature = signature
            state.pendingScript = updateScript
            if (state.themeIsDark != isDark) {
                state.themeIsDark = isDark
                state.pageReady = false
                state.appliedSignature = null
                webView.loadDataWithBaseURL(
                    MIND_MAP_BASE_URL,
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            } else if (state.pageReady) {
                applyPendingMindMapUpdate(webView, state)
            }
        },
        onRelease = { webView ->
            val state = webView.tag as? MindMapWebViewState
            if (state?.released == true) return@AndroidView
            state?.released = true
            state?.mainHandler?.removeCallbacksAndMessages(null)
            webView.stopLoading()
            webView.removeJavascriptInterface(MIND_MAP_BRIDGE_NAME)
            webView.setOnLongClickListener(null)
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        },
    )
}

private data class MindMapWebViewState(
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
        mainHandler.post {
            if (!released) action()
        }
    }
}

private const val MIND_MAP_BASE_URL = "https://kardleaf.local/mindmap/"
private const val MIND_MAP_BRIDGE_NAME = "KardLeafMindMap"
private val MIND_MAP_BASE_URI: Uri = Uri.parse(MIND_MAP_BASE_URL)

private fun isAllowedMindMapNavigation(uri: Uri): Boolean {
    val path = uri.path.orEmpty()
    val basePath = MIND_MAP_BASE_URI.path.orEmpty()
    return uri.scheme.equals(MIND_MAP_BASE_URI.scheme, ignoreCase = true) &&
        uri.host.equals(MIND_MAP_BASE_URI.host, ignoreCase = true) &&
        uri.userInfo == null &&
        (uri.port == -1 || uri.port == 443) &&
        path == basePath
}

private fun applyPendingMindMapUpdate(
    webView: WebView,
    state: MindMapWebViewState,
) {
    val signature = state.pendingSignature ?: return
    val script = state.pendingScript ?: return
    if (state.appliedSignature == signature) return
    state.appliedSignature = signature
    webView.evaluateJavascript(script, null)
}

@Composable
private fun rememberMindMapHtml(
    isDark: Boolean,
): String = androidx.compose.runtime.remember(isDark) {
    buildMindMapHtml(isDark)
}

@Composable
private fun rememberMindMapUpdateScript(
    title: String,
    headings: List<MarkdownHeading>,
): String = androidx.compose.runtime.remember(title, headings) {
    buildMindMapUpdateScript(title, headings)
}

@Composable
private fun rememberMindMapSignature(
    title: String,
    headings: List<MarkdownHeading>,
    isDark: Boolean,
): String = androidx.compose.runtime.remember(title, headings, isDark) {
    // 顺序敏感的散列：同级节点交换顺序后必须得到不同签名，否则移动操作不会刷新 WebView。
    val headingsHash = headings.fold(0) { acc, heading ->
        acc * 31 + (heading.text.hashCode() * 31 + heading.level) * 31 + heading.lineIndex
    }
    title.hashCode().toString() + ":" + headings.size + ":" + headingsHash + ":" + isDark
}

private fun buildMindMapUpdateScript(
    title: String,
    headings: List<MarkdownHeading>,
): String {
    val nodes = JSONArray().apply {
        headings.forEachIndexed { index, heading ->
            put(
                JSONObject()
                    .put("index", index)
                    .put("level", heading.level.coerceIn(1, 6))
                    .put("text", heading.text)
                    .put("line", heading.lineIndex + 1),
            )
        }
    }.toString()
    val pageTitle = JSONObject.quote(title.ifBlank { "未命名笔记" })
    return "window.KardLeafMindMapUpdate && window.KardLeafMindMapUpdate($pageTitle, $nodes);"
}

private fun buildMindMapHtml(
    isDark: Boolean,
): String {
    val dark = if (isDark) "true" else "false"
    return """
<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
<title>KardLeaf Mind Map</title>
<style>
:root {
  color-scheme: ${if (isDark) "dark" else "light"};
  --bg: ${if (isDark) "#101418" else "#f7fbff"};
  --surface: ${if (isDark) "#18212a" else "#ffffff"};
  --surface2: ${if (isDark) "#1f2b36" else "#eef6ff"};
  --text: ${if (isDark) "#e5edf5" else "#17212b"};
  --primary: ${if (isDark) "#8ec8ff" else "#2f80ed"};
  --primary2: ${if (isDark) "#223b55" else "#dceeff"};
  --float: ${if (isDark) "rgba(24,33,42,.94)" else "rgba(255,255,255,.94)"};
}
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; -webkit-user-select: none; user-select: none; -webkit-touch-callout: none; }
html, body { width: 100%; height: 100%; margin: 0; overflow: hidden; background: var(--bg); font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: var(--text); }
#toolbar { position: fixed; right: 12px; top: 8px; z-index: 4; display: flex; align-items: center; gap: 5px; padding: 6px; border-radius: 18px; background: var(--float); box-shadow: 0 8px 28px rgba(20, 80, 140, .16); backdrop-filter: blur(12px); }
.toolBtn { min-width: 32px; height: 32px; padding: 0 8px; border: 0; border-radius: 12px; background: var(--primary2); color: var(--primary); font-size: 17px; font-weight: 750; }
.toolBtn.layoutBtn { min-width: 40px; font-size: 15px; letter-spacing: .5px; }
.toolBtn.zoomLabel { min-width: 46px; background: transparent; color: var(--text); font-size: 12px; font-weight: 700; opacity: .9; }
#actionBar { position: fixed; left: 50%; transform: translateX(-50%); bottom: 14px; z-index: 6; display: none; align-items: center; gap: 2px; max-width: calc(100vw - 16px); overflow-x: auto; padding: 6px; border-radius: 20px; background: var(--float); box-shadow: 0 10px 32px rgba(15, 48, 82, .24); backdrop-filter: blur(14px); }
#actionBar button { flex: 0 0 auto; height: 42px; padding: 0 13px; border: 0; border-radius: 14px; background: transparent; color: var(--text); font-size: 13px; font-weight: 650; white-space: nowrap; }
#actionBar button:active { background: var(--primary2); color: var(--primary); }
#actionBar .danger { color: ${if (isDark) "#ff9a9a" else "#c62828"}; }
#stage { width: 100%; height: 100%; touch-action: none; }
svg { width: 100%; height: 100%; display: block; }
.link { fill: none; stroke-linecap: round; }
.node rect.body { filter: drop-shadow(0 8px 16px rgba(31, 91, 156, .13)); }
.node { cursor: grab; }
.node.dragging { opacity: .85; }
.node text { font-size: 14px; font-weight: 650; pointer-events: none; }
.node.root text { font-size: 15px; font-weight: 700; }
.halo { fill: none; stroke-width: 2.2; opacity: .95; }
.toggle { cursor: pointer; }
.toggle text { font-size: 11px; font-weight: 750; pointer-events: none; }
</style>
</head>
<body>
<div id="toolbar">
  <button class="toolBtn layoutBtn" id="layoutToggle" aria-label="切换布局" title="切换单向或双向布局">→</button>
  <button class="toolBtn" id="collapseAll" aria-label="收起全部" title="收起到主分支">⊟</button>
  <button class="toolBtn" id="zoomOut" aria-label="缩小">−</button>
  <button class="toolBtn zoomLabel" id="zoomLabel" aria-label="恢复 100% 缩放" title="点击恢复 100%">100%</button>
  <button class="toolBtn" id="zoomIn" aria-label="放大">+</button>
  <button class="toolBtn" id="fit" aria-label="适配视图" title="适配全图">⌂</button>
</div>
<div id="actionBar"></div>
<div id="stage"><svg id="svg"><g id="viewport"><g id="links"></g><g id="nodes"></g></g></svg></div>
<script>
const isDark = $dark;
const svg = document.getElementById('svg');
const viewport = document.getElementById('viewport');
const linksLayer = document.getElementById('links');
const nodesLayer = document.getElementById('nodes');
const actionBar = document.getElementById('actionBar');
const layoutToggle = document.getElementById('layoutToggle');
const collapseAllBtn = document.getElementById('collapseAll');
const zoomLabel = document.getElementById('zoomLabel');
const SVG_NS = 'http://www.w3.org/2000/svg';
const PRIMARY_COLOR = isDark ? '#8ec8ff' : '#2f80ed';
const ROOT_TEXT_COLOR = isDark ? '#10233a' : '#ffffff';
const SURFACE_COLOR = isDark ? '#18212a' : '#ffffff';
const TEXT_COLOR = isDark ? '#e5edf5' : '#17212b';
const BRANCH_FILLS = ['#4263eb', '#0ca678', '#f76707', '#ae3ec9', '#f08c00', '#1c7ed6', '#d6336c', '#37b24d'];
const BRANCH_ACCENTS = isDark
  ? ['#91a7ff', '#63e6be', '#ffa94d', '#e599f7', '#ffd43b', '#74c0fc', '#faa2c1', '#8ce99a']
  : ['#4263eb', '#0ca678', '#f76707', '#ae3ec9', '#f08c00', '#1c7ed6', '#d6336c', '#37b24d'];
const minNodeWidth = 112;
const maxNodeWidth = 236;
const minRootWidth = 150;
const maxRootWidth = 252;
const minNodeHeight = 46;
const textLineHeight = 18;
const textVerticalPadding = 14;
const horizontalGap = 72;
const siblingGap = 20;
const textMeasureCanvas = document.createElement('canvas');
const textMeasureContext = textMeasureCanvas.getContext('2d');
let docTitle = '未命名笔记';
let nodesData = [];
let root = null;
let nodes = [];
let visibleNodes = [];
let hasMindMapData = false;
let layoutMode = 'side';
let tx = 0, ty = 0, scale = 1;
let drag = null;
let pinch = null;
let dropTarget = null;
let animationVersion = 0;
let suppressTapUntil = 0;
let lastBlankTapTime = 0;
let selectedKey = null;
const collapsedKeys = new Set();
const activePointers = new Map();
function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
function setTransform() {
  viewport.setAttribute('transform', 'translate(' + tx + ' ' + ty + ') scale(' + scale + ')');
  zoomLabel.textContent = Math.round(scale * 100) + '%';
}
function screenToWorld(clientX, clientY) { return { x: (clientX - tx) / scale, y: (clientY - ty) / scale }; }
function distance(a, b) { const dx = a.x - b.x; const dy = a.y - b.y; return Math.hypot(dx, dy); }
function centerOf(a, b) { return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }; }
function currentTwoPointers() { return Array.from(activePointers.values()).slice(0, 2); }
function nodeVisualWidth(n) { return n && n.width ? n.width : (n && n.root ? minRootWidth : minNodeWidth); }
function nodeVisualHeight(n) { return n && n.height ? n.height : minNodeHeight; }
function nodeCenter(n) { return { x: n.x + nodeVisualWidth(n) / 2, y: n.y + nodeVisualHeight(n) / 2 }; }
function effChildren(n) { return collapsedKeys.has(n.key) ? [] : n.children; }
function descendantCount(n) { return n.children.reduce(function(sum, child) { return sum + 1 + descendantCount(child); }, 0); }
function measureTextWidth(text, isRootNode) {
  if (!textMeasureContext) return Array.from(text || '').length * (isRootNode ? 15 : 14);
  textMeasureContext.font = (isRootNode ? '700 15px ' : '650 14px ') + 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
  return textMeasureContext.measureText(text || '').width;
}
function wrapNodeText(text, isRootNode) {
  const value = String(text || '').trim() || '未命名节点';
  const maxTextWidth = isRootNode ? maxRootWidth - 30 : maxNodeWidth - 30;
  const lines = [];
  let line = '';
  Array.from(value).forEach(char => {
    const candidate = line + char;
    if (line && measureTextWidth(candidate, isRootNode) > maxTextWidth) {
      lines.push(line.trimEnd());
      line = char.trimStart();
    } else {
      line = candidate;
    }
  });
  if (line || !lines.length) lines.push(line || value);
  return lines;
}
function prepareNodeMetrics(node) {
  node.lines = wrapNodeText(node.text, node.root);
  const measuredWidth = Math.max.apply(null, node.lines.map(line => measureTextWidth(line, node.root)));
  node.width = clamp(
    Math.ceil(measuredWidth + 30),
    node.root ? minRootWidth : minNodeWidth,
    node.root ? maxRootWidth : maxNodeWidth,
  );
  node.height = Math.max(minNodeHeight, node.lines.length * textLineHeight + textVerticalPadding * 2);
}
function paintBranch(node, fill, accent) {
  node.color = fill;
  node.accent = accent;
  node.children.forEach(child => paintBranch(child, fill, accent));
}
function buildTree(nextTitle, nextNodes) {
  docTitle = nextTitle || '未命名笔记';
  nodesData = Array.isArray(nextNodes) ? nextNodes : [];
  root = { key: 'root', index: -1, level: 0, depth: 0, text: docTitle, line: 1, x: 0, y: 0, parent: null, children: [], root: true, direction: 'right', color: PRIMARY_COLOR, accent: PRIMARY_COLOR };
  const stack = [root];
  const keyCounts = new Map();
  nodes = [root];
  nodesData.forEach(item => {
    const level = Math.max(1, Math.min(6, item.level || 1));
    while (stack.length > level) stack.pop();
    const parent = stack[stack.length - 1] || root;
    const keyBase = String(item.text || '') + '|' + String(level);
    const keyCount = (keyCounts.get(keyBase) || 0) + 1;
    keyCounts.set(keyBase, keyCount);
    const node = { key: keyBase + '|' + keyCount, index: item.index, level: level, depth: parent.depth + 1, text: item.text, line: item.line, x: 0, y: 0, parent: parent, children: [], root: false, direction: 'right', color: PRIMARY_COLOR, accent: PRIMARY_COLOR };
    parent.children.push(node);
    nodes.push(node);
    stack[level] = node;
  });
  root.children.forEach((child, childIndex) => {
    paintBranch(child, BRANCH_FILLS[childIndex % BRANCH_FILLS.length], BRANCH_ACCENTS[childIndex % BRANCH_ACCENTS.length]);
  });
  nodes.forEach(prepareNodeMetrics);
}
function measureSubtree(node) {
  const children = effChildren(node);
  if (!children.length) {
    node.subtreeHeight = nodeVisualHeight(node);
    return node.subtreeHeight;
  }
  const childrenHeight = children.reduce((sum, child) => sum + measureSubtree(child), 0)
    + siblingGap * Math.max(0, children.length - 1);
  node.subtreeHeight = Math.max(nodeVisualHeight(node), childrenHeight);
  return node.subtreeHeight;
}
function assignDirection(node, direction) {
  node.direction = direction;
  node.children.forEach(child => assignDirection(child, direction));
}
function shiftSubtreeY(node, delta) {
  node.y += delta;
  effChildren(node).forEach(child => shiftSubtreeY(child, delta));
}
function layoutBranch(node, depth, top) {
  node.x = node.direction === 'left'
    ? -(horizontalGap + nodeVisualWidth(node)) - (depth - 1) * (maxNodeWidth + horizontalGap)
    : nodeVisualWidth(root) + horizontalGap + (depth - 1) * (maxNodeWidth + horizontalGap);
  const children = effChildren(node);
  if (!children.length) {
    node.y = top + (node.subtreeHeight - nodeVisualHeight(node)) / 2;
    return;
  }
  let childTop = top;
  children.forEach(child => {
    layoutBranch(child, depth + 1, childTop);
    childTop += child.subtreeHeight + siblingGap;
  });
  const firstCenter = children[0].y + nodeVisualHeight(children[0]) / 2;
  const lastChild = children[children.length - 1];
  const lastCenter = lastChild.y + nodeVisualHeight(lastChild) / 2;
  node.y = (firstCenter + lastCenter) / 2 - nodeVisualHeight(node) / 2;
}
function collectVisible(node) {
  effChildren(node).forEach(child => {
    visibleNodes.push(child);
    collectVisible(child);
  });
}
function layoutMindMap() {
  if (!root) return;
  const rootChildren = effChildren(root);
  rootChildren.forEach(measureSubtree);
  const left = [];
  const right = [];
  let leftHeight = 0;
  let rightHeight = 0;
  rootChildren.forEach((child, childIndex) => {
    if (layoutMode === 'side' || childIndex % 2 === 0) {
      right.push(child);
      rightHeight += child.subtreeHeight + siblingGap;
    } else {
      left.push(child);
      leftHeight += child.subtreeHeight + siblingGap;
    }
  });
  leftHeight = Math.max(0, leftHeight - siblingGap);
  rightHeight = Math.max(0, rightHeight - siblingGap);
  const mapHeight = Math.max(nodeVisualHeight(root), leftHeight, rightHeight);
  root.x = 0;
  root.y = (mapHeight - nodeVisualHeight(root)) / 2;
  let leftTop = (mapHeight - leftHeight) / 2;
  left.forEach(child => {
    assignDirection(child, 'left');
    layoutBranch(child, 1, leftTop);
    leftTop += child.subtreeHeight + siblingGap;
  });
  let rightTop = (mapHeight - rightHeight) / 2;
  right.forEach(child => {
    assignDirection(child, 'right');
    layoutBranch(child, 1, rightTop);
    rightTop += child.subtreeHeight + siblingGap;
  });
  if (rootChildren.length === 1) {
    const onlyChild = rootChildren[0];
    const delta = nodeCenter(root).y - nodeCenter(onlyChild).y;
    if (Math.abs(delta) > .01) shiftSubtreeY(onlyChild, delta);
  }
  visibleNodes = [root];
  collectVisible(root);
  if (selectedKey && !visibleNodes.some(n => n.key === selectedKey)) {
    selectedKey = null;
    updateActionBar();
  }
}
function updateLayoutButton() {
  const isSide = layoutMode === 'side';
  layoutToggle.textContent = isSide ? '→' : '↔';
  layoutToggle.setAttribute('aria-label', isSide ? '切换到双向布局' : '切换到单向布局');
  layoutToggle.title = isSide ? '当前为单向布局，点击切换双向' : '当前为双向布局，点击切换单向';
}
function updateCollapseAllButton() {
  const willExpand = collapsedKeys.size > 0;
  collapseAllBtn.textContent = willExpand ? '⊞' : '⊟';
  collapseAllBtn.setAttribute('aria-label', willExpand ? '展开全部' : '收起全部');
  collapseAllBtn.title = willExpand ? '展开全部节点' : '收起到主分支';
}
function toggleLayoutMode() {
  if (!root) return;
  const previousPositions = capturePositions();
  layoutMode = layoutMode === 'side' ? 'full' : 'side';
  updateLayoutButton();
  animateLayoutFrom(previousPositions, 220);
  setTimeout(resetView, 230);
}
function capturePositions() {
  const positions = new Map();
  nodes.forEach(n => positions.set(n.key, { x: n.x, y: n.y }));
  return positions;
}
function animateLayoutFrom(previousPositions, duration) {
  const version = ++animationVersion;
  layoutMindMap();
  const targets = new Map();
  nodes.forEach(n => targets.set(n.key, { x: n.x, y: n.y }));
  const starts = new Map();
  nodes.forEach(n => {
    const previous = previousPositions.get(n.key);
    starts.set(n.key, previous || targets.get(n.key));
    n.x = starts.get(n.key).x;
    n.y = starts.get(n.key).y;
  });
  render();
  const startedAt = performance.now();
  function step(now) {
    if (version !== animationVersion) return;
    const progress = clamp((now - startedAt) / duration, 0, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    nodes.forEach(n => {
      const start = starts.get(n.key);
      const target = targets.get(n.key);
      n.x = start.x + (target.x - start.x) * eased;
      n.y = start.y + (target.y - start.y) * eased;
    });
    render();
    if (progress < 1) requestAnimationFrame(step);
  }
  requestAnimationFrame(step);
}
function applyMindMapData(nextTitle, nextNodes) {
  const previousPositions = hasMindMapData ? capturePositions() : new Map();
  buildTree(nextTitle, nextNodes);
  const validKeys = new Set(nodes.map(n => n.key));
  Array.from(collapsedKeys).forEach(key => { if (!validKeys.has(key)) collapsedKeys.delete(key); });
  if (selectedKey && !validKeys.has(selectedKey)) selectedKey = null;
  updateActionBar();
  updateCollapseAllButton();
  if (!hasMindMapData) {
    layoutMindMap();
    render();
    hasMindMapData = true;
    requestAnimationFrame(resetView);
  } else {
    animateLayoutFrom(previousPositions, 180);
  }
}
window.KardLeafMindMapUpdate = applyMindMapData;
function isDescendantOf(node, ancestor) {
  let p = node ? node.parent : null;
  while (p) {
    if (p === ancestor) return true;
    p = p.parent;
  }
  return false;
}
function nodeDepth(node) {
  let depth = 0;
  let current = node;
  while (current && !current.root) {
    depth++;
    current = current.parent;
  }
  return depth;
}
function subtreeDepth(node) {
  if (!node || !node.children.length) return 1;
  return 1 + Math.max.apply(null, node.children.map(subtreeDepth));
}
function canReparent(movingNode, targetNode) {
  if (!movingNode || movingNode.root || !targetNode) return false;
  if (movingNode === targetNode || isDescendantOf(targetNode, movingNode)) return false;
  if (targetNode === movingNode.parent) return false;
  return nodeDepth(targetNode) + subtreeDepth(movingNode) <= 6;
}
function findDropTargetFor(movingNode) {
  if (!movingNode || movingNode.root) return null;
  const c = nodeCenter(movingNode);
  let best = null;
  let bestScore = Infinity;
  visibleNodes.forEach(n => {
    if (!canReparent(movingNode, n)) return;
    const w = nodeVisualWidth(n);
    const marginX = 34;
    const marginY = 26;
    if (c.x < n.x - marginX || c.x > n.x + w + marginX || c.y < n.y - marginY || c.y > n.y + nodeVisualHeight(n) + marginY) return;
    const nc = nodeCenter(n);
    const score = Math.abs(c.x - nc.x) + Math.abs(c.y - nc.y);
    if (score < bestScore) {
      best = n;
      bestScore = score;
    }
  });
  return best;
}
function reparentNodeLocally(movingNode, targetNode) {
  if (!canReparent(movingNode, targetNode)) return false;
  const previousPositions = capturePositions();
  collapsedKeys.delete(targetNode.key);
  const oldSiblings = movingNode.parent.children;
  const oldIndex = oldSiblings.indexOf(movingNode);
  if (oldIndex >= 0) oldSiblings.splice(oldIndex, 1);
  targetNode.children.push(movingNode);
  movingNode.parent = targetNode;
  updateCollapseAllButton();
  animateLayoutFrom(previousPositions, 180);
  return true;
}
function mapBounds() {
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  visibleNodes.forEach(n => {
    const isLeftNode = !n.root && n.direction === 'left';
    minX = Math.min(minX, n.x - (isLeftNode ? 32 : 0));
    minY = Math.min(minY, n.y);
    maxX = Math.max(maxX, n.x + nodeVisualWidth(n) + (isLeftNode ? 0 : 32));
    maxY = Math.max(maxY, n.y + nodeVisualHeight(n));
  });
  return { minX, minY, maxX, maxY, width: Math.max(1, maxX - minX), height: Math.max(1, maxY - minY) };
}
function resetView() {
  if (!root || !visibleNodes.length) return;
  const rect = svg.getBoundingClientRect();
  const b = mapBounds();
  if (layoutMode === 'side') {
    const fittedScale = Math.min(
      Math.max(1, rect.width - 56) / (b.width + 20),
      Math.max(1, rect.height - 56) / (b.height + 20),
      1.18,
    );
    scale = clamp(fittedScale, .72, 1.18);
    tx = 28 - b.minX * scale;
    ty = rect.height / 2 - ((b.minY + b.maxY) / 2) * scale;
    setTransform();
    return;
  }
  const center = nodeCenter(root);
  const horizontalExtent = Math.max(center.x - b.minX, b.maxX - center.x, nodeVisualWidth(root) / 2);
  const verticalExtent = Math.max(center.y - b.minY, b.maxY - center.y, nodeVisualHeight(root) / 2);
  const availableHalfW = Math.max(1, rect.width / 2 - 20);
  const availableHalfH = Math.max(1, rect.height / 2 - 20);
  const fittedScale = Math.min(availableHalfW / (horizontalExtent + 20), availableHalfH / (verticalExtent + 20), 1.22);
  scale = clamp(fittedScale, .72, 1.22);
  tx = rect.width / 2 - center.x * scale;
  ty = rect.height / 2 - center.y * scale;
  setTransform();
}
function pathBetween(a, b) {
  const siblings = effChildren(a);
  const siblingIndex = Math.max(0, siblings.indexOf(b));
  const siblingCount = Math.max(1, siblings.length);
  const isLeft = b.direction === 'left';
  const ax = isLeft ? a.x : a.x + nodeVisualWidth(a);
  const bx = isLeft ? b.x + nodeVisualWidth(b) : b.x;
  const by = b.y + nodeVisualHeight(b) / 2;
  if (a.root && siblingCount === 1) {
    return 'M ' + ax + ' ' + (a.y + nodeVisualHeight(a) / 2) + ' L ' + bx + ' ' + by;
  }
  const spread = Math.min(nodeVisualHeight(a) * .62, Math.max(0, (siblingCount - 1) * 6));
  const sourceOffset = siblingCount === 1 ? 0 : (siblingIndex / (siblingCount - 1) - .5) * spread;
  const ay = a.y + nodeVisualHeight(a) / 2 + sourceOffset;
  const bend = Math.max(36, Math.abs(bx - ax) * .45);
  const c1x = isLeft ? ax - bend : ax + bend;
  const c2x = isLeft ? bx + bend : bx - bend;
  return 'M ' + ax + ' ' + ay + ' C ' + c1x + ' ' + ay + ', ' + c2x + ' ' + by + ', ' + bx + ' ' + by;
}
function createSvgElement(name) { return document.createElementNS(SVG_NS, name); }
function renderNode(n) {
  const isLeftNode = !n.root && n.direction === 'left';
  const isSelected = selectedKey === n.key;
  const isDropTarget = dropTarget === n;
  const w = nodeVisualWidth(n);
  const h = nodeVisualHeight(n);
  const g = createSvgElement('g');
  g.setAttribute('class', 'node' + (n.root ? ' root' : '') + (drag && drag.node === n ? ' dragging' : ''));
  g.setAttribute('transform', 'translate(' + n.x + ' ' + n.y + ')');
  g.dataset.index = String(n.index);
  g.dataset.key = n.key;
  if (isSelected) {
    const halo = createSvgElement('rect');
    halo.setAttribute('class', 'halo');
    halo.setAttribute('x', '-5');
    halo.setAttribute('y', '-5');
    halo.setAttribute('width', String(w + 10));
    halo.setAttribute('height', String(h + 10));
    halo.setAttribute('rx', '21');
    halo.setAttribute('ry', '21');
    halo.style.stroke = n.accent;
    g.appendChild(halo);
  }
  const rect = createSvgElement('rect');
  rect.setAttribute('class', 'body');
  rect.setAttribute('width', String(w));
  rect.setAttribute('height', String(h));
  rect.setAttribute('rx', n.root ? '20' : '16');
  rect.setAttribute('ry', n.root ? '20' : '16');
  if (n.root) {
    rect.style.fill = PRIMARY_COLOR;
    rect.style.stroke = 'none';
  } else if (n.depth === 1) {
    rect.style.fill = n.color;
    rect.style.stroke = 'none';
  } else {
    rect.style.fill = SURFACE_COLOR;
    rect.style.stroke = n.accent;
    rect.style.strokeOpacity = isSelected ? '1' : '.55';
    rect.style.strokeWidth = isSelected ? '2' : '1.4';
  }
  if (isDropTarget) {
    rect.style.stroke = n.accent;
    rect.style.strokeOpacity = '1';
    rect.style.strokeWidth = '3';
  }
  g.appendChild(rect);
  const text = createSvgElement('text');
  const textX = n.root ? w / 2 : 15;
  const textStartY = h / 2 - ((n.lines.length - 1) * textLineHeight) / 2 + 5;
  text.setAttribute('x', String(textX));
  text.setAttribute('y', String(textStartY));
  if (n.root) text.setAttribute('text-anchor', 'middle');
  text.style.fill = n.root ? ROOT_TEXT_COLOR : (n.depth === 1 ? '#ffffff' : TEXT_COLOR);
  n.lines.forEach((line, lineIndex) => {
    const tspan = createSvgElement('tspan');
    tspan.setAttribute('x', String(textX));
    tspan.setAttribute('dy', lineIndex === 0 ? '0' : String(textLineHeight));
    tspan.textContent = line;
    text.appendChild(tspan);
  });
  g.appendChild(text);
  if (!n.root && n.children.length) {
    const collapsed = collapsedKeys.has(n.key);
    const toggle = createSvgElement('g');
    toggle.setAttribute('class', 'toggle');
    toggle.dataset.key = n.key;
    const cx = isLeftNode ? -16 : w + 16;
    const cy = h / 2;
    const hit = createSvgElement('circle');
    hit.setAttribute('cx', String(cx));
    hit.setAttribute('cy', String(cy));
    hit.setAttribute('r', '14');
    hit.style.fill = 'rgba(0,0,0,0)';
    toggle.appendChild(hit);
    if (collapsed) {
      const badge = createSvgElement('circle');
      badge.setAttribute('cx', String(cx));
      badge.setAttribute('cy', String(cy));
      badge.setAttribute('r', '11');
      badge.style.fill = n.accent;
      badge.style.stroke = SURFACE_COLOR;
      badge.style.strokeWidth = '2';
      toggle.appendChild(badge);
      const count = descendantCount(n);
      const countText = createSvgElement('text');
      countText.setAttribute('x', String(cx));
      countText.setAttribute('y', String(cy + 4));
      countText.setAttribute('text-anchor', 'middle');
      countText.style.fill = '#ffffff';
      countText.textContent = count > 99 ? '99+' : String(count);
      toggle.appendChild(countText);
    } else {
      const dot = createSvgElement('circle');
      dot.setAttribute('cx', String(cx));
      dot.setAttribute('cy', String(cy));
      dot.setAttribute('r', '4');
      dot.style.fill = n.accent;
      dot.style.opacity = '.9';
      toggle.appendChild(dot);
    }
    g.appendChild(toggle);
  }
  nodesLayer.appendChild(g);
}
function render() {
  if (!root) return;
  linksLayer.innerHTML = '';
  nodesLayer.innerHTML = '';
  visibleNodes.forEach(n => {
    if (n.root) return;
    const p = createSvgElement('path');
    p.setAttribute('class', 'link');
    p.setAttribute('d', pathBetween(n.parent || root, n));
    p.setAttribute('stroke', n.accent);
    p.setAttribute('stroke-width', n.depth <= 1 ? '3' : '2');
    p.setAttribute('stroke-opacity', isDark ? '.82' : '.75');
    linksLayer.appendChild(p);
  });
  visibleNodes.forEach(renderNode);
}
function findNodeByIndex(index) { return nodes.find(n => n.index === index); }
function findNodeByKey(key) { return key ? nodes.find(n => n.key === key) : null; }
function closestByClass(target, className) {
  while (target && target !== svg) {
    if (target.classList && target.classList.contains(className)) return target;
    target = target.parentNode;
  }
  return null;
}
function updateActionBar() {
  const node = findNodeByKey(selectedKey);
  if (!node) {
    actionBar.style.display = 'none';
    actionBar.innerHTML = '';
    return;
  }
  let html = '<button data-action="addChild">＋子节点</button>';
  if (!node.root) {
    html += '<button data-action="addSibling">＋同级</button>';
    html += '<button data-action="rename">重命名</button>';
    html += '<button data-action="moveUp">上移</button>';
    html += '<button data-action="moveDown">下移</button>';
    html += '<button data-action="jump">原文</button>';
    html += '<button data-action="delete" class="danger">删除</button>';
  }
  actionBar.innerHTML = html;
  actionBar.style.display = 'flex';
}
function selectNode(node) {
  selectedKey = node ? node.key : null;
  updateActionBar();
  render();
}
function toggleCollapse(node) {
  if (!node || node.root || !node.children.length) return;
  const previousPositions = capturePositions();
  if (collapsedKeys.has(node.key)) collapsedKeys.delete(node.key);
  else collapsedKeys.add(node.key);
  updateCollapseAllButton();
  animateLayoutFrom(previousPositions, 180);
}
function runNodeAction(action) {
  const node = findNodeByKey(selectedKey);
  const api = window.KardLeafMindMap;
  if (!node || !api) return;
  suppressTapUntil = Date.now() + 320;
  if (action === 'addChild') {
    if (collapsedKeys.has(node.key)) {
      collapsedKeys.delete(node.key);
      updateCollapseAllButton();
    }
    if (api.onNodeAddChild) api.onNodeAddChild(node.index);
  } else if (node.root) {
    return;
  } else if (action === 'addSibling') {
    if (api.onNodeAddSibling) api.onNodeAddSibling(node.index);
  } else if (action === 'rename') {
    if (api.onNodeRename) api.onNodeRename(node.index);
  } else if (action === 'moveUp') {
    if (api.onNodeMove) api.onNodeMove(node.index, true);
  } else if (action === 'moveDown') {
    if (api.onNodeMove) api.onNodeMove(node.index, false);
  } else if (action === 'jump') {
    if (api.onNodeClick) api.onNodeClick(node.index);
  } else if (action === 'delete') {
    if (api.onNodeDelete) api.onNodeDelete(node.index);
  }
}
actionBar.addEventListener('click', e => {
  let target = e.target;
  while (target && target !== actionBar && target.tagName !== 'BUTTON') target = target.parentNode;
  if (!target || target === actionBar) return;
  const action = target.dataset ? target.dataset.action : null;
  if (action) runNodeAction(action);
});
document.addEventListener('contextmenu', e => {
  e.preventDefault();
  e.stopPropagation();
}, true);
function beginPinchIfNeeded() {
  if (activePointers.size < 2) return;
  const [a, b] = currentTwoPointers();
  const center = centerOf(a, b);
  pinch = {
    startDistance: Math.max(24, distance(a, b)),
    startScale: scale,
    worldCenter: screenToWorld(center.x, center.y),
  };
  drag = null;
  dropTarget = null;
  suppressTapUntil = Date.now() + 260;
  render();
}
function updatePinch() {
  if (!pinch || activePointers.size < 2) return;
  const [a, b] = currentTwoPointers();
  const center = centerOf(a, b);
  scale = clamp(pinch.startScale * distance(a, b) / pinch.startDistance, .42, 2.35);
  tx = center.x - pinch.worldCenter.x * scale;
  ty = center.y - pinch.worldCenter.y * scale;
  setTransform();
}
svg.addEventListener('pointerdown', (e) => {
  e.preventDefault();
  svg.setPointerCapture(e.pointerId);
  activePointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (activePointers.size >= 2) {
    beginPinchIfNeeded();
    return;
  }
  dropTarget = null;
  const toggleEl = closestByClass(e.target, 'toggle');
  if (toggleEl) {
    activePointers.delete(e.pointerId);
    suppressTapUntil = Date.now() + 320;
    toggleCollapse(findNodeByKey(toggleEl.dataset.key));
    return;
  }
  const nodeGroup = closestByClass(e.target, 'node');
  const world = screenToWorld(e.clientX, e.clientY);
  const key = nodeGroup ? nodeGroup.dataset.key : null;
  const node = key ? findNodeByKey(key) : null;
  drag = {
    id: e.pointerId,
    startX: e.clientX,
    startY: e.clientY,
    startedAt: Date.now(),
    lastX: e.clientX,
    lastY: e.clientY,
    moved: false,
    mode: node && !node.root ? 'node' : 'pan',
    node,
    nodeStartX: node ? node.x : 0,
    nodeStartY: node ? node.y : 0,
    worldStartX: world.x,
    worldStartY: world.y,
  };
  render();
});
svg.addEventListener('pointermove', (e) => {
  if (!activePointers.has(e.pointerId)) return;
  e.preventDefault();
  activePointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (activePointers.size >= 2) {
    updatePinch();
    return;
  }
  if (!drag || drag.id !== e.pointerId) return;
  const dx = e.clientX - drag.startX;
  const dy = e.clientY - drag.startY;
  if (Math.abs(dx) + Math.abs(dy) > 5) drag.moved = true;
  if (drag.mode === 'node' && drag.node && !drag.node.root) {
    const world = screenToWorld(e.clientX, e.clientY);
    drag.node.x = drag.nodeStartX + (world.x - drag.worldStartX);
    drag.node.y = drag.nodeStartY + (world.y - drag.worldStartY);
    dropTarget = findDropTargetFor(drag.node);
    render();
  } else {
    tx += e.clientX - drag.lastX;
    ty += e.clientY - drag.lastY;
    setTransform();
  }
  drag.lastX = e.clientX;
  drag.lastY = e.clientY;
});
function finishPointer(e) {
  if (activePointers.has(e.pointerId)) activePointers.delete(e.pointerId);
  if (pinch) {
    pinch = null;
    drag = null;
    suppressTapUntil = Date.now() + 260;
    render();
    return;
  }
  if (!drag || drag.id !== e.pointerId) return;
  const item = drag;
  const target = dropTarget;
  drag = null;
  dropTarget = null;
  if (item.moved && item.node && !item.node.root) {
    if (target && reparentNodeLocally(item.node, target)) {
      const movingIndex = item.node.index;
      const parentIndex = target.index;
      if (window.KardLeafMindMap && window.KardLeafMindMap.onNodeReparent) {
        window.KardLeafMindMap.onNodeReparent(movingIndex, parentIndex);
      }
      suppressTapUntil = Date.now() + 320;
      return;
    }
    item.node.x = item.nodeStartX;
    item.node.y = item.nodeStartY;
    render();
    return;
  }
  if (!item.moved) {
    if (Date.now() <= suppressTapUntil) {
      render();
      return;
    }
    if (item.node) {
      if (selectedKey === item.node.key) {
        if (!item.node.root && window.KardLeafMindMap && window.KardLeafMindMap.onNodeRename) {
          suppressTapUntil = Date.now() + 320;
          window.KardLeafMindMap.onNodeRename(item.node.index);
        }
      } else {
        selectNode(item.node);
        return;
      }
    } else {
      if (selectedKey) selectNode(null);
      const now = Date.now();
      if (now - lastBlankTapTime < 300) {
        lastBlankTapTime = 0;
        resetView();
        return;
      }
      lastBlankTapTime = now;
    }
  }
  render();
}
svg.addEventListener('pointerup', finishPointer);
svg.addEventListener('pointercancel', finishPointer);
function zoomAt(clientX, clientY, factor) {
  const world = screenToWorld(clientX, clientY);
  scale = clamp(scale * factor, .42, 2.35);
  tx = clientX - world.x * scale;
  ty = clientY - world.y * scale;
  setTransform();
}
function zoomBy(factor) {
  const rect = svg.getBoundingClientRect();
  zoomAt(rect.width / 2, rect.height / 2, factor);
}
document.getElementById('zoomOut').onclick = () => zoomBy(.86);
document.getElementById('zoomIn').onclick = () => zoomBy(1.16);
document.getElementById('fit').onclick = resetView;
zoomLabel.onclick = () => {
  const rect = svg.getBoundingClientRect();
  const world = screenToWorld(rect.width / 2, rect.height / 2);
  scale = 1;
  tx = rect.width / 2 - world.x;
  ty = rect.height / 2 - world.y;
  setTransform();
};
collapseAllBtn.onclick = () => {
  if (!root) return;
  const previousPositions = capturePositions();
  if (collapsedKeys.size) {
    collapsedKeys.clear();
  } else {
    root.children.forEach(child => {
      if (child.children.length) collapsedKeys.add(child.key);
    });
  }
  updateCollapseAllButton();
  animateLayoutFrom(previousPositions, 200);
  setTimeout(resetView, 210);
};
layoutToggle.onclick = toggleLayoutMode;
updateLayoutButton();
updateCollapseAllButton();
</script>
</body>
</html>
""".trimIndent()
}
