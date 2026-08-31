package com.kangle.kardleaf.ui

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.editor.MindMapDocument
import com.kangle.kardleaf.ui.editor.MindMapNode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

@Composable
internal fun MarkdownMindMapScreen(
    displayTitle: String,
    document: MindMapDocument?,
    isDark: Boolean,
    unavailableTitle: String? = null,
    unavailableMessage: String? = null,
    initialEditNodeIndex: Int? = null,
    onInitialEditConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onBackToHome: () -> Unit,
    onOpenSource: () -> Unit = {},
    onTitleChange: (String) -> Unit = {},
    onNodeMove: (movingIndex: Int, targetParentIndex: Int, targetChildIndex: Int, gestureSequence: Int) -> Unit = { _, _, _, _ -> },
    onNodeAddChild: (parentIndex: Int, title: String, renameIndex: Int, renameTitle: String) -> Unit = { _, _, _, _ -> },
    onNodeAddSibling: (anchorIndex: Int, title: String, renameIndex: Int, renameTitle: String) -> Unit = { _, _, _, _ -> },
    onNodeRename: (nodeIndex: Int, title: String) -> Unit = { _, _ -> },
    onNodeDelete: (nodeIndex: Int) -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    canUndo: Boolean = false,
    canRedo: Boolean = false,
) {
    fun modelIndex(webIndex: Int): Int = if (webIndex < 0) 0 else webIndex

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context as? Activity
    val orientation = configuration.orientation
    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    val prefsManager = remember { PrefsManager(context.applicationContext) }
    var mindMapTheme by remember { mutableStateOf(prefsManager.getMindMapTheme()) }

    fun toggleOrientation() {
        val host = activity ?: return
        val decorView = host.window.decorView
        val currentIsLandscape = if (decorView.width > 0 && decorView.height > 0) {
            decorView.width > decorView.height
        } else {
            host.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        val targetOrientation = if (currentIsLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        KardLeafLog.d(
            MIND_MAP_GESTURE_TRACE_TAG,
            "orientation-toggle currentLandscape=$currentIsLandscape target=$targetOrientation " +
                "config=${host.resources.configuration.orientation} requested=${host.requestedOrientation} " +
                "decor=${decorView.width}x${decorView.height}",
        )
        decorView.post {
            host.setRequestedOrientation(targetOrientation)
            KardLeafLog.d(
                MIND_MAP_GESTURE_TRACE_TAG,
                "orientation-toggle requested target=$targetOrientation",
            )
        }
    }
    var showTitleEditor by remember(displayTitle, document?.root?.text) { mutableStateOf(false) }
    var titleDraft by remember(displayTitle, document?.root?.text) {
        mutableStateOf(displayTitle.ifBlank { document?.root?.text.orEmpty() })
    }
    var showOutline by remember(document?.content) { mutableStateOf(false) }
    var outlineSelectionIndex by remember(document?.content) { mutableStateOf<Int?>(null) }
    val toolbarIconTint = if (isDark) {
        androidx.compose.ui.graphics.Color(0xFFE1E5E8)
    } else {
        androidx.compose.ui.graphics.Color(0xFF4B4F52)
    }
    var pendingExitAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun resetOrientationForExit(source: String) {
        activity?.let { host ->
            KardLeafLog.d(
                MIND_MAP_GESTURE_TRACE_TAG,
                "orientation-reset source=$source config=${host.resources.configuration.orientation} " +
                    "requested=${host.requestedOrientation} decor=${host.window.decorView.width}x${host.window.decorView.height} " +
                    "target=${ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}",
            )
            host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    fun requestExit(action: () -> Unit) {
        if (document == null || unavailableTitle != null) {
            action()
        } else if (pendingExitAction == null) {
            pendingExitAction = action
        }
    }

    fun dismissMindMap(returnToHome: Boolean = false) {
        KardLeafLog.d(
            MIND_MAP_GESTURE_TRACE_TAG,
            "exit-request source=${if (returnToHome) "back-to-home" else "dismiss"} " +
                "pending=${pendingExitAction != null} document=${document != null}",
        )
        requestExit {
            resetOrientationForExit(if (returnToHome) "back-to-home" else "dismiss")
            if (returnToHome) onBackToHome() else onDismiss()
        }
    }

    fun openTitleEditor() {
        titleDraft = displayTitle.ifBlank { document?.root?.text.orEmpty() }
        showTitleEditor = true
    }

    fun openSource() {
        KardLeafLog.d(
            MIND_MAP_GESTURE_TRACE_TAG,
            "exit-request source=open-source pending=${pendingExitAction != null} document=${document != null}",
        )
        requestExit {
            resetOrientationForExit("open-source")
            onOpenSource()
        }
    }

    BackHandler { dismissMindMap(returnToHome = true) }
    DisposableEffect(activity) {
        onDispose {
            resetOrientationForExit("dispose")
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(MaterialTheme.colorScheme.background)
                        .zIndex(1f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { dismissMindMap(returnToHome = true) }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "关闭思维导图",
                                tint = toolbarIconTint,
                            )
                        }
                        Text(
                            text = displayTitle.ifBlank { document?.root?.text ?: "思维导图" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = ::openTitleEditor),
                        )
                        IconButton(onClick = { showOutline = !showOutline }) {
                            Icon(
                                Icons.Outlined.FormatListBulleted,
                                contentDescription = if (showOutline) "关闭大纲" else "打开大纲",
                                tint = toolbarIconTint,
                            )
                        }
                        IconButton(onClick = ::openSource) {
                            Icon(Icons.Outlined.Description, contentDescription = "打开原文", tint = toolbarIconTint)
                        }
                        IconButton(onClick = ::toggleOrientation) {
                            Icon(
                                Icons.Outlined.ScreenRotation,
                                contentDescription = if (isLandscape) "切换为竖屏" else "切换为横屏",
                                tint = toolbarIconTint,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
                    unavailableTitle != null -> {
                        MindMapUnavailableHint(
                            title = displayTitle,
                            reasonTitle = unavailableTitle,
                            message = unavailableMessage.orEmpty(),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    document == null -> {
                        EmptyMindMapHint(
                            title = displayTitle,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        MarkdownMindMapWebView(
                            document = document,
                            isDark = isDark,
                            initialThemeKey = mindMapTheme.key,
                            onThemeChange = { themeKey ->
                                mindMapTheme = PrefsManager.MindMapTheme.fromKey(themeKey)
                                prefsManager.saveMindMapTheme(mindMapTheme)
                            },
                            onNodeMove = { movingIndex, targetParentIndex, targetChildIndex, gestureSequence ->
                                onNodeMove(
                                    modelIndex(movingIndex),
                                    modelIndex(targetParentIndex),
                                    targetChildIndex,
                                    gestureSequence,
                                )
                            },
                            onUndo = onUndo,
                            onRedo = onRedo,
                            canUndo = canUndo,
                            canRedo = canRedo,
                            orientation = orientation,
                            outlineSelectionIndex = outlineSelectionIndex,
                            onOutlineSelectionConsumed = { outlineSelectionIndex = null },
                            initialEditNodeIndex = initialEditNodeIndex,
                            onInitialEditConsumed = onInitialEditConsumed,
                            exitAction = pendingExitAction,
                            onExitActionConsumed = { pendingExitAction = null },
                            onNodeAddChild = { parentIndex, title, renameIndex, renameTitle ->
                                onNodeAddChild(modelIndex(parentIndex), title, renameIndex, renameTitle)
                            },
                            onNodeAddSibling = { anchorIndex, title, renameIndex, renameTitle ->
                                onNodeAddSibling(modelIndex(anchorIndex), title, renameIndex, renameTitle)
                            },
                            onNodeRename = { nodeIndex, title -> onNodeRename(modelIndex(nodeIndex), title) },
                            onNodeDelete = { nodeIndex -> onNodeDelete(modelIndex(nodeIndex)) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (showOutline && document != null) {
                    MindMapOutlinePanel(
                        nodes = document.nodes,
                        onClose = { showOutline = false },
                        onNodeClick = { index ->
                            outlineSelectionIndex = index
                            showOutline = false
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxHeight()
                            .zIndex(2f),
                    )
                }
            }
        }

        if (showTitleEditor) {
            AlertDialog(
                onDismissRequest = { showTitleEditor = false },
                title = { Text("修改名称") },
                text = {
                    OutlinedTextField(
                        value = titleDraft,
                        onValueChange = { titleDraft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("名称") },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showTitleEditor = false
                            onTitleChange(titleDraft.trim())
                        },
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTitleEditor = false }) {
                        Text("取消")
                    }
                },
            )
        }

    }
}

@Composable
private fun MindMapOutlinePanel(
    nodes: List<MindMapNode>,
    onClose: () -> Unit,
    onNodeClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var collapsed by remember(nodes) { mutableStateOf(emptySet<Int>()) }
    val childrenByParent = remember(nodes) { nodes.groupBy { it.parentIndex } }
    val scrollState = rememberScrollState()

    fun isHidden(node: MindMapNode): Boolean {
        var parentIndex = node.parentIndex
        while (parentIndex != null) {
            if (parentIndex in collapsed) return true
            parentIndex = nodes.getOrNull(parentIndex)?.parentIndex
        }
        return false
    }

    Surface(
        modifier = modifier
            .width(300.dp)
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AccountTree, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = "大纲",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭大纲")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(vertical = 6.dp),
            ) {
                nodes.forEach { node ->
                    if (!isHidden(node)) {
                        val hasChildren = childrenByParent[node.index].orEmpty().isNotEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNodeClick(node.index) }
                                .padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.width((node.depth * 16).dp))
                            if (hasChildren) {
                                IconButton(
                                    onClick = {
                                        collapsed = if (node.index in collapsed) {
                                            collapsed - node.index
                                        } else {
                                            collapsed + node.index
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        if (node.index in collapsed) Icons.Outlined.ChevronRight else Icons.Outlined.ExpandMore,
                                        contentDescription = if (node.index in collapsed) "展开子节点" else "折叠子节点",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else {
                                Spacer(Modifier.size(32.dp))
                            }
                            Text(
                                text = node.text,
                                style = if (node.depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
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
    document: MindMapDocument,
    isDark: Boolean,
    initialThemeKey: String,
    onThemeChange: (String) -> Unit,
    onNodeMove: (movingIndex: Int, targetParentIndex: Int, targetChildIndex: Int, gestureSequence: Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    orientation: Int,
    outlineSelectionIndex: Int?,
    onOutlineSelectionConsumed: () -> Unit,
    initialEditNodeIndex: Int?,
    onInitialEditConsumed: () -> Unit,
    exitAction: (() -> Unit)?,
    onExitActionConsumed: () -> Unit,
    onNodeAddChild: (parentIndex: Int, title: String, renameIndex: Int, renameTitle: String) -> Unit,
    onNodeAddSibling: (anchorIndex: Int, title: String, renameIndex: Int, renameTitle: String) -> Unit,
    onNodeRename: (nodeIndex: Int, title: String) -> Unit,
    onNodeDelete: (nodeIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnNodeMove = rememberUpdatedState(onNodeMove)
    val currentOnThemeChange = rememberUpdatedState(onThemeChange)
    val currentOnUndo = rememberUpdatedState(onUndo)
    val currentOnRedo = rememberUpdatedState(onRedo)
    val currentOnNodeAddChild = rememberUpdatedState(onNodeAddChild)
    val currentOnNodeAddSibling = rememberUpdatedState(onNodeAddSibling)
    val currentOnNodeRename = rememberUpdatedState(onNodeRename)
    val currentOnNodeDelete = rememberUpdatedState(onNodeDelete)
    val currentOnInitialEditConsumed = rememberUpdatedState(onInitialEditConsumed)
    val currentOnOutlineSelectionConsumed = rememberUpdatedState(onOutlineSelectionConsumed)
    val currentDocument = rememberUpdatedState(document)
    val html = rememberMindMapHtml(isDark, initialThemeKey)
    val signature = rememberMindMapSignature(document, isDark, canUndo, canRedo)
    val updateScript = rememberMindMapUpdateScript(document, canUndo, canRedo)

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
                isFocusable = true
                isFocusableInTouchMode = true
                isLongClickable = true
                isHapticFeedbackEnabled = true
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
                state.webView = this
                val visibleFrame = Rect()
                viewTreeObserver.addOnGlobalLayoutListener {
                    if (state.released) return@addOnGlobalLayoutListener
                    getWindowVisibleDisplayFrame(visibleFrame)
                    val navigationBottom = ViewCompat.getRootWindowInsets(this)
                        ?.getInsets(WindowInsetsCompat.Type.navigationBars())
                        ?.bottom
                        ?: 0
                    val imeBottom = (rootView.height - visibleFrame.bottom - navigationBottom).coerceAtLeast(0)
                    val density = resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
                    val nextImeBottomCssPx = (imeBottom / density).roundToInt()
                    if (nextImeBottomCssPx != state.imeBottomCssPx) {
                        KardLeafLog.d(
                            MIND_MAP_GESTURE_TRACE_TAG,
                            "ime-native source=global-layout rawBottom=$imeBottom navigationBottom=$navigationBottom " +
                                "visibleFrame=${visibleFrame.left},${visibleFrame.top},${visibleFrame.right},${visibleFrame.bottom} " +
                                "root=${rootView.width}x${rootView.height} webView=${width}x${height} density=$density " +
                                "css=$nextImeBottomCssPx previousCss=${state.imeBottomCssPx} pageReady=${state.pageReady}",
                        )
                        state.imeBottomCssPx = nextImeBottomCssPx
                        if (state.pageReady) {
                            KardLeafLog.d(
                                MIND_MAP_GESTURE_TRACE_TAG,
                                "ime-native dispatch source=global-layout css=${state.imeBottomCssPx}",
                            )
                            evaluateJavascript(
                                "window.KardLeafMindMapSetImeBottom && window.KardLeafMindMapSetImeBottom(${state.imeBottomCssPx});",
                                null,
                            )
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        KardLeafLog.d(
                            MIND_MAP_GESTURE_TRACE_TAG,
                            "console line=${message.lineNumber()} level=${message.messageLevel()} message=${message.message()}",
                        )
                        return true
                    }
                }
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
                        KardLeafLog.d(
                            MIND_MAP_GESTURE_TRACE_TAG,
                            "viewport page-ready orientation=${state.appliedOrientation} url=${url ?: "none"} " +
                                "ime-native-css=${state.imeBottomCssPx} webView=${view.width}x${view.height}",
                        )
                        applyPendingMindMapUpdate(view, state) {
                            currentOnInitialEditConsumed.value()
                        }
                        KardLeafLog.d(
                            MIND_MAP_GESTURE_TRACE_TAG,
                            "ime-native dispatch source=page-ready css=${state.imeBottomCssPx}",
                        )
                        view.evaluateJavascript(
                            "window.KardLeafMindMapSetImeBottom && window.KardLeafMindMapSetImeBottom(${state.imeBottomCssPx});",
                            null,
                        )
                    }
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onNodeMove(
                            movingIndex: Int,
                            targetParentIndex: Int,
                            targetChildIndex: Int,
                            gestureSequence: Int,
                        ) {
                            val bridgeDocument = currentDocument.value
                            val bridgeMoving = bridgeDocument.nodes.getOrNull(movingIndex)
                            val bridgeTargetParent = bridgeDocument.nodes.getOrNull(targetParentIndex)
                            val bridgeOldChildIndex = bridgeMoving?.let { moving ->
                                bridgeDocument.nodes
                                    .filter { it.parentIndex == moving.parentIndex }
                                    .indexOfFirst { it.index == movingIndex }
                            } ?: -1
                            val bridgeTargetChildCount = bridgeTargetParent?.let {
                                bridgeDocument.nodes.count { node ->
                                    node.parentIndex == targetParentIndex && node.index != movingIndex
                                }
                            } ?: -1
                            KardLeafLog.d(
                                MIND_MAP_GESTURE_TRACE_TAG,
                                "bridge move gesture=$gestureSequence movingIndex=$movingIndex " +
                                    "targetParentIndex=$targetParentIndex targetChildIndex=$targetChildIndex " +
                                    "pageReady=${state.pageReady} released=${state.released} nodes=${bridgeDocument.nodes.size} " +
                                    "movingDepth=${bridgeMoving?.depth ?: -1} movingParent=${bridgeMoving?.parentIndex} " +
                                    "oldChildIndex=$bridgeOldChildIndex targetParentDepth=${bridgeTargetParent?.depth ?: -1} " +
                                    "targetChildCount=$bridgeTargetChildCount",
                            )
                            state.postIfActive("move") {
                                KardLeafLog.d(
                                    MIND_MAP_GESTURE_TRACE_TAG,
                                    "bridge move dispatched gesture=$gestureSequence movingIndex=$movingIndex " +
                                        "targetParentIndex=$targetParentIndex targetChildIndex=$targetChildIndex " +
                                        "nodes=${currentDocument.value.nodes.size}",
                                )
                                currentOnNodeMove.value(
                                    movingIndex,
                                    targetParentIndex,
                                    targetChildIndex,
                                    gestureSequence,
                                )
                            }
                        }

                        @JavascriptInterface
                        fun onThemeChange(themeKey: String) {
                            state.postIfActive("theme-change") {
                                currentOnThemeChange.value(themeKey)
                            }
                        }

                        @JavascriptInterface
                        fun onInlineRenameFinished() {
                            KardLeafLog.d(
                                MIND_MAP_GESTURE_TRACE_TAG,
                                "bridge inline-rename-finished received pageReady=${state.pageReady} " +
                                    "released=${state.released} pending=${state.pendingInlineRenameContinuation != null}",
                            )
                            state.postIfActive("inline-rename-finished") {
                                KardLeafLog.d(
                                    MIND_MAP_GESTURE_TRACE_TAG,
                                    "bridge inline-rename-finished dispatched pending=${state.pendingInlineRenameContinuation != null}",
                                )
                                completeMindMapInlineRename(state)
                            }
                        }

                        @JavascriptInterface
                        fun onDragTrace(message: String) {
                            KardLeafLog.d(MIND_MAP_GESTURE_TRACE_TAG, message)
                        }

                        @JavascriptInterface
                        fun onLongPress() {
                            state.postIfActive("long-press-haptic") {
                                state.webView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                        }

                        @JavascriptInterface
                        fun onUndo() {
                            KardLeafLog.d(
                                MIND_MAP_GESTURE_TRACE_TAG,
                                "bridge undo received pageReady=${state.pageReady} released=${state.released}",
                            )
                            state.postIfActive("undo") {
                                currentOnUndo.value()
                            }
                        }

                        @JavascriptInterface
                        fun onRedo() {
                            KardLeafLog.d(
                                MIND_MAP_GESTURE_TRACE_TAG,
                                "bridge redo received pageReady=${state.pageReady} released=${state.released}",
                            )
                            state.postIfActive("redo") {
                                currentOnRedo.value()
                            }
                        }

                        @JavascriptInterface
                        fun onExportImage() {
                            KardLeafLog.d(
                                MIND_MAP_GESTURE_TRACE_TAG,
                                "bridge export received pageReady=${state.pageReady} released=${state.released} " +
                                    "webViewPresent=${state.webView != null}",
                            )
                            state.postIfActive("export") {
                                state.webView?.let(::exportMindMapImage)
                            }
                        }

                        @JavascriptInterface
                        fun onNodeAddChild(parentIndex: Int, title: String, renameIndex: Int, renameTitle: String) {
                            KardLeafLog.d(
                                MIND_MAP_GESTURE_TRACE_TAG,
                                "bridge add-child received parentIndex=$parentIndex titleLen=${title.length} " +
                                    "titleEndsWithHash=${title.endsWith('#')} pageReady=${state.pageReady} released=${state.released}",
                            )
                            state.postIfActive("add-child") {
                                KardLeafLog.d(
                                    MIND_MAP_GESTURE_TRACE_TAG,
                                    "bridge add-child dispatched parentIndex=$parentIndex titleLen=${title.length}",
                                )
                                currentOnNodeAddChild.value(parentIndex, title, renameIndex, renameTitle)
                            }
                        }

                        @JavascriptInterface
                        fun onNodeAddSibling(anchorIndex: Int, title: String, renameIndex: Int, renameTitle: String) {
                            KardLeafLog.d(
                                MIND_MAP_GESTURE_TRACE_TAG,
                                "bridge add-sibling received anchorIndex=$anchorIndex titleLen=${title.length} " +
                                    "titleEndsWithHash=${title.endsWith('#')} pageReady=${state.pageReady} released=${state.released}",
                            )
                            state.postIfActive("add-sibling") {
                                KardLeafLog.d(
                                    MIND_MAP_GESTURE_TRACE_TAG,
                                    "bridge add-sibling dispatched anchorIndex=$anchorIndex titleLen=${title.length}",
                                )
                                currentOnNodeAddSibling.value(anchorIndex, title, renameIndex, renameTitle)
                            }
                        }

                        @JavascriptInterface
                        fun onNodeRename(index: Int, title: String) {
                            KardLeafLog.d(
                                MIND_MAP_GESTURE_TRACE_TAG,
                                "bridge rename received index=$index titleLen=${title.length} " +
                                    "titleEndsWithHash=${title.endsWith('#')} pageReady=${state.pageReady} released=${state.released}",
                            )
                            state.postIfActive("rename") {
                                KardLeafLog.d(
                                    MIND_MAP_GESTURE_TRACE_TAG,
                                    "bridge rename dispatched index=$index titleLen=${title.length}",
                                )
                                currentOnNodeRename.value(index, title)
                            }
                        }

                        @JavascriptInterface
                        fun onNodeDelete(index: Int) {
                            KardLeafLog.d(
                                MIND_MAP_GESTURE_TRACE_TAG,
                                "bridge delete received index=$index pageReady=${state.pageReady} released=${state.released}",
                            )
                            state.postIfActive("delete") {
                                KardLeafLog.d(MIND_MAP_GESTURE_TRACE_TAG, "bridge delete dispatched index=$index")
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
                if (exitAction != null && state.dispatchedExitAction !== exitAction) {
                    KardLeafLog.d(
                        MIND_MAP_GESTURE_TRACE_TAG,
                        "exit-dispatch source=compose-update pageReady=${state.pageReady} released=${state.released} " +
                            "pendingRename=${state.pendingInlineRenameContinuation != null}",
                    )
                    state.dispatchedExitAction = exitAction
                    requestMindMapInlineRenameCommit(webView, state) {
                        KardLeafLog.d(
                            MIND_MAP_GESTURE_TRACE_TAG,
                            "exit-dispatch commit-complete released=${state.released}",
                        )
                        state.dispatchedExitAction = null
                        onExitActionConsumed()
                        exitAction()
                    }
                }
                if (initialEditNodeIndex == null) {
                    state.pendingInitialEditIndex = null
                    state.dispatchedInitialEditIndex = null
                } else if (state.dispatchedInitialEditIndex != initialEditNodeIndex) {
                    state.pendingInitialEditIndex = initialEditNodeIndex
                    KardLeafLog.d(
                        MIND_MAP_GESTURE_TRACE_TAG,
                        "initial-edit pending index=$initialEditNodeIndex pageReady=${state.pageReady}",
                    )
                }
                state.pendingSignature = signature
                state.pendingScript = updateScript
                if (state.themeIsDark != isDark && !state.themeReloadPending) {
                    val nextThemeIsDark = isDark
                    KardLeafLog.d(
                        MIND_MAP_GESTURE_TRACE_TAG,
                        "theme-reload requested dark=$nextThemeIsDark pageReady=${state.pageReady} " +
                            "pendingRename=${state.pendingInlineRenameContinuation != null}",
                    )
                    state.themeReloadPending = true
                    requestMindMapInlineRenameCommit(webView, state) {
                        state.themeReloadPending = false
                        if (!state.released) {
                            state.themeIsDark = nextThemeIsDark
                            state.pageReady = false
                            state.appliedSignature = null
                            state.appliedOrientation = orientation
                            webView.loadDataWithBaseURL(
                                MIND_MAP_BASE_URL,
                                html,
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    }
                } else if (state.themeReloadPending) {
                    return@AndroidView
                } else if (state.pageReady) {
                    applyPendingMindMapUpdate(webView, state) {
                        currentOnInitialEditConsumed.value()
                    }
                    if (outlineSelectionIndex == null) {
                        state.dispatchedOutlineSelectionIndex = null
                    } else if (state.dispatchedOutlineSelectionIndex != outlineSelectionIndex) {
                        val selectedIndex = outlineSelectionIndex
                        state.dispatchedOutlineSelectionIndex = selectedIndex
                        KardLeafLog.d(
                            MIND_MAP_GESTURE_TRACE_TAG,
                            "outline-select dispatch index=$selectedIndex",
                        )
                        webView.evaluateJavascript(
                            "window.KardLeafMindMapSelectNode && window.KardLeafMindMapSelectNode($selectedIndex);",
                            null,
                        )
                        currentOnOutlineSelectionConsumed.value()
                    }
                    val previousOrientation = state.appliedOrientation
                    if (previousOrientation == null) {
                        state.appliedOrientation = orientation
                        KardLeafLog.d(
                            MIND_MAP_GESTURE_TRACE_TAG,
                            "viewport orientation-baseline orientation=$orientation resize=false",
                        )
                    } else if (previousOrientation != orientation) {
                        state.appliedOrientation = orientation
                        KardLeafLog.d(
                            MIND_MAP_GESTURE_TRACE_TAG,
                            "viewport orientation-change from=$previousOrientation to=$orientation resize=true",
                        )
                        webView.evaluateJavascript(
                            "window.KardLeafMindMapResize && window.KardLeafMindMapResize('android-orientation-change');",
                            null,
                        )
                    }
                }
        },
        onRelease = { webView ->
            val state = webView.tag as? MindMapWebViewState
            if (state?.released == true) return@AndroidView
            KardLeafLog.d(
                MIND_MAP_GESTURE_TRACE_TAG,
                "webview-release pageReady=${state?.pageReady ?: false} released=${state?.released ?: false} " +
                    "imeCss=${state?.imeBottomCssPx ?: -1} appliedOrientation=${state?.appliedOrientation} " +
                    "pendingRename=${state?.pendingInlineRenameContinuation != null} dispatchedExit=${state?.dispatchedExitAction != null} " +
                    "webView=${webView.width}x${webView.height}",
            )
            state?.released = true
            state?.pendingInlineRenameContinuation = null
            state?.dispatchedExitAction = null
            state?.mainHandler?.removeCallbacksAndMessages(null)
            state?.webView = null
            webView.stopLoading()
            webView.removeJavascriptInterface(MIND_MAP_BRIDGE_NAME)
            webView.webChromeClient = null
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
    var pendingInitialEditIndex: Int? = null,
    var dispatchedInitialEditIndex: Int? = null,
    var imeBottomCssPx: Int = 0,
    var appliedOrientation: Int? = null,
    var themeReloadPending: Boolean = false,
    var pendingInlineRenameContinuation: (() -> Unit)? = null,
    var dispatchedExitAction: (() -> Unit)? = null,
    var dispatchedOutlineSelectionIndex: Int? = null,
    var webView: WebView? = null,
    @Volatile
    var released: Boolean = false,
) {
    fun postIfActive(event: String = "callback", action: () -> Unit) {
        if (released) {
            KardLeafLog.d(MIND_MAP_GESTURE_TRACE_TAG, "bridge dropped event=$event stage=before-post released=true")
            return
        }
        mainHandler.post {
            if (!released) {
                action()
            } else {
                KardLeafLog.d(MIND_MAP_GESTURE_TRACE_TAG, "bridge dropped event=$event stage=main-post released=true")
            }
        }
    }
}

private const val MIND_MAP_BASE_URL = "https://kardleaf.local/mindmap/"
private const val MIND_MAP_BRIDGE_NAME = "KardLeafMindMap"
private const val MIND_MAP_GESTURE_TRACE_TAG = "KardLeafMindMapGestureTrace"
private val MIND_MAP_BASE_URI: Uri = Uri.parse(MIND_MAP_BASE_URL)

private fun completeMindMapInlineRename(state: MindMapWebViewState) {
    val continuation = state.pendingInlineRenameContinuation
    if (continuation == null) {
        KardLeafLog.d(MIND_MAP_GESTURE_TRACE_TAG, "rename-commit complete skipped pending=false")
        return
    }
    KardLeafLog.d(
        MIND_MAP_GESTURE_TRACE_TAG,
        "rename-commit complete pending=true released=${state.released} pageReady=${state.pageReady}",
    )
    state.pendingInlineRenameContinuation = null
    continuation()
}

private fun requestMindMapInlineRenameCommit(
    webView: WebView,
    state: MindMapWebViewState,
    continuation: () -> Unit,
) {
    if (state.pendingInlineRenameContinuation != null) {
        KardLeafLog.d(MIND_MAP_GESTURE_TRACE_TAG, "rename-commit request skipped pending=true")
        return
    }
    if (state.released || !state.pageReady) {
        KardLeafLog.d(
            MIND_MAP_GESTURE_TRACE_TAG,
            "rename-commit immediate released=${state.released} pageReady=${state.pageReady}",
        )
        continuation()
        return
    }
    state.pendingInlineRenameContinuation = continuation
    KardLeafLog.d(
        MIND_MAP_GESTURE_TRACE_TAG,
        "rename-commit request evaluate pageReady=${state.pageReady} released=${state.released}",
    )
    webView.evaluateJavascript(
        "(function() { " +
            "if (!window.KardLeafMindMapCommitInlineRename) return 'missing'; " +
            "window.KardLeafMindMapCommitInlineRename(); return 'requested'; " +
            "})();",
    ) { result ->
        KardLeafLog.d(
            MIND_MAP_GESTURE_TRACE_TAG,
            "rename-commit evaluate-result result=$result released=${state.released} " +
                "pending=${state.pendingInlineRenameContinuation != null}",
        )
        if (result == "\"missing\"" && !state.released) {
            webView.post { completeMindMapInlineRename(state) }
        }
    }
}

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
    onInitialEditConsumed: () -> Unit,
) {
    val signature = state.pendingSignature ?: return
    val script = state.pendingScript ?: return
    val needsTreeUpdate = state.appliedSignature != signature
    val initialEditIndex = state.pendingInitialEditIndex
    if (!needsTreeUpdate && initialEditIndex == null) {
        return
    }
    KardLeafLog.d(
        MIND_MAP_GESTURE_TRACE_TAG,
        "webview-update apply tree=$needsTreeUpdate initialEdit=${initialEditIndex ?: "none"} " +
            "pageReady=${state.pageReady} released=${state.released} " +
            "signatureChanged=${state.appliedSignature != signature}",
    )
    val dispatchedScript = buildString {
        if (needsTreeUpdate) append(script)
        if (initialEditIndex != null) {
            append("window.KardLeafMindMapBeginRename && window.KardLeafMindMapBeginRename($initialEditIndex);")
        }
    }
    if (needsTreeUpdate) state.appliedSignature = signature
    if (initialEditIndex != null) {
        state.pendingInitialEditIndex = null
        state.dispatchedInitialEditIndex = initialEditIndex
    }
    if (initialEditIndex != null) {
        webView.requestFocus(View.FOCUS_DOWN)
    }
    webView.evaluateJavascript(dispatchedScript) {
        KardLeafLog.d(
            MIND_MAP_GESTURE_TRACE_TAG,
            "webview-update evaluate-complete initialEdit=${initialEditIndex ?: "none"} released=${state.released}",
        )
        if (initialEditIndex != null) {
            webView.post {
                if (!state.released) {
                    webView.requestFocus(View.FOCUS_DOWN)
                    webView.context
                        .getSystemService(Context.INPUT_METHOD_SERVICE)
                        ?.let { (it as? InputMethodManager)?.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT) }
                }
            }
        }
    }
    if (initialEditIndex != null) {
        onInitialEditConsumed()
    }
}

private fun exportMindMapImage(webView: WebView) {
    val context = webView.context
    val restoreScript = "window.KardLeafMindMapRestoreExport && window.KardLeafMindMapRestoreExport();"
    KardLeafLog.d(
        MIND_MAP_GESTURE_TRACE_TAG,
        "export start webView=${webView.width}x${webView.height} measured=${webView.measuredWidth}x${webView.measuredHeight} " +
            "pageScale=${webView.scale} visibility=${webView.visibility}",
    )
    val capture = {
        KardLeafLog.d(
            MIND_MAP_GESTURE_TRACE_TAG,
            "export capture webView=${webView.width}x${webView.height} measured=${webView.measuredWidth}x${webView.measuredHeight}",
        )
        val bitmap = runCatching {
            Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
        if (bitmap != null) {
            try {
                webView.draw(Canvas(bitmap))
                val file = File(
                    context.cacheDir,
                    "shared_notes/mind_map_${System.currentTimeMillis()}.png",
                ).apply { parentFile?.mkdirs() }
                runCatching {
                    file.outputStream().use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val sendIntent = Intent(Intent.ACTION_SEND)
                        .setType("image/png")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val chooser = Intent.createChooser(sendIntent, "导出思维导图")
                    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }.onFailure {
                    KardLeafLog.e(MIND_MAP_GESTURE_TRACE_TAG, "mind map image export failed", it)
                    file.delete()
                    context.showToast("思维导图导出失败")
                }
            } finally {
                bitmap.recycle()
            }
        } else {
            KardLeafLog.e(MIND_MAP_GESTURE_TRACE_TAG, "mind map image export skipped: invalid WebView size")
            context.showToast("思维导图导出失败")
        }
        webView.evaluateJavascript(restoreScript, null)
    }
    webView.evaluateJavascript(
        "window.KardLeafMindMapPrepareExport && window.KardLeafMindMapPrepareExport();",
    ) {
        webView.post { capture() }
    }
}

@Composable
private fun rememberMindMapHtml(
    isDark: Boolean,
    initialThemeKey: String,
): String = androidx.compose.runtime.remember(isDark, initialThemeKey) {
    buildMindMapHtml(isDark, initialThemeKey)
}

@Composable
private fun rememberMindMapUpdateScript(
    document: MindMapDocument,
    canUndo: Boolean,
    canRedo: Boolean,
): String = androidx.compose.runtime.remember(document, canUndo, canRedo) {
    buildMindMapUpdateScript(document, canUndo, canRedo)
}

@Composable
private fun rememberMindMapSignature(
    document: MindMapDocument,
    isDark: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
): String = androidx.compose.runtime.remember(document, isDark, canUndo, canRedo) {
    val nodesSignature = document.nodes.joinToString(separator = "\u0001") { node ->
        "${node.depth}:${node.lineIndex}:${node.sourceOffset}:${node.text.length}:${node.text}"
    }
    "$nodesSignature|$isDark|$canUndo|$canRedo"
}

private fun buildMindMapUpdateScript(
    document: MindMapDocument,
    canUndo: Boolean,
    canRedo: Boolean,
): String {
    val root = document.root
    val rootPayload = JSONObject()
        .put("text", root.text)
        .put("line", root.lineIndex + 1)
        .put("sourceOffset", root.sourceOffset)
        .toString()
    val nodes = JSONArray().apply {
        document.nodes.drop(1).forEach { node ->
            put(
                JSONObject()
                    .put("index", node.index)
                    .put("depth", node.depth)
                    .put("text", node.text)
                    .put("line", node.lineIndex + 1)
                    .put("sourceOffset", node.sourceOffset),
            )
        }
    }.toString()
    return "window.KardLeafMindMapUpdate && window.KardLeafMindMapUpdate($rootPayload, $nodes);" +
        "window.KardLeafMindMapSetHistory && window.KardLeafMindMapSetHistory($canUndo, $canRedo);"
}

private fun buildMindMapHtml(
    isDark: Boolean,
    initialThemeKey: String,
): String {
    val dark = if (isDark) "true" else "false"
    val themeKey = PrefsManager.MindMapTheme.fromKey(initialThemeKey).key
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
  --muted: ${if (isDark) "#aab7c4" else "#9aa5af"};
  --primary: ${if (isDark) "#8ec8ff" else "#2f80ed"};
  --primary2: ${if (isDark) "#223b55" else "#dceeff"};
  --float: ${if (isDark) "rgba(24,33,42,.94)" else "rgba(255,255,255,.94)"};
  --toolbar-icon: ${if (isDark) "#e1e5e8" else "#4b4f52"};
}
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; -webkit-user-select: none; user-select: none; -webkit-touch-callout: none; }
html, body { width: 100%; height: 100%; margin: 0; overflow: hidden; background: var(--bg); font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: var(--text); }
#actionBar { position: fixed; left: 50%; transform: translateX(-50%); bottom: calc(8px + var(--ime-bottom, 0px)); z-index: 6; display: flex; align-items: center; gap: 2px; max-width: calc(100vw - 8px); overflow-x: auto; padding: 3px; border-radius: 16px; background: var(--float); box-shadow: 0 10px 32px rgba(15, 48, 82, .24); backdrop-filter: blur(14px); }
#globalActions, #nodeActions { display: flex; flex: 0 0 auto; align-items: center; gap: 0; }
#nodeActions { gap: 2px; }
#nodeActions:not(:empty) { margin-left: 1px; padding-left: 2px; border-left: 1px solid rgba(100, 130, 160, .28); }
.toolBtn { min-width: 30px; height: 36px; padding: 0 6px; border: 0; border-radius: 10px; background: var(--primary2); color: var(--primary); font-size: 17px; font-weight: 750; touch-action: manipulation; }
.toolBtn.layoutBtn { min-width: 42px; font-size: 12px; letter-spacing: .2px; }
.toolBtn.iconBtn { display: inline-flex; align-items: center; justify-content: center; width: 32px; min-width: 32px; padding: 3px; background: transparent; color: var(--toolbar-icon); }
.toolBtn.iconBtn:active { background: var(--primary2); }
.toolBtn.iconBtn.layoutBtn { width: 34px; min-width: 34px; padding: 2px; }
.mindMapIcon { width: 28px; height: 24px; display: block; fill: none; stroke: currentColor; stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; }
.mindMapIcon circle { vector-effect: non-scaling-stroke; }
.layoutGlyph, .collapseGlyph { display: none; }
.layoutBtn[data-mode="side"] .layoutGlyph-twoWay,
.layoutBtn[data-mode="full"] .layoutGlyph-oneWay,
.collapseBtn[data-action="collapse"] .collapseGlyph-collapse,
.collapseBtn[data-action="expand"] .collapseGlyph-expand { display: block; }
.toolBtn.historyBtn { display: inline-flex; align-items: center; justify-content: center; width: 32px; min-width: 32px; padding: 0; border-radius: 10px; background: transparent; color: var(--toolbar-icon); font-size: 20px; font-weight: 500; line-height: 1; }
.historyIcon { width: 24px; height: 24px; display: block; fill: currentColor; }
.toolBtn:disabled { opacity: .38; }
.toolBtn.historyBtn:not(:disabled) { color: var(--toolbar-icon); }
.toolBtn.historyBtn:not(:disabled):active { background: var(--primary2); color: var(--primary); }
#themeControl { position: relative; }
.themeBtn { display: inline-flex; align-items: center; justify-content: center; width: 34px; min-width: 34px; padding: 0; background: transparent; color: var(--toolbar-icon); }
#actionBar .nodeAction { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 auto; width: 34px; min-width: 34px; height: 34px; margin: 0; padding: 5px; border: 0; border-radius: 10px; background: transparent; color: var(--toolbar-icon); touch-action: manipulation; }
#actionBar .nodeAction:active { background: var(--primary2); color: var(--primary); }
#actionBar .nodeAction:disabled { opacity: .34; }
#actionBar .actionIcon { width: 20px; height: 20px; display: block; margin: 0; fill: none; stroke: currentColor; stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; }
.suppliedActionIcon { stroke-width: 3.6 !important; }
#themeIcon { width: 20px; height: 20px; display: block; fill: currentColor; stroke: none; }
#themeIcon path, #themeIcon circle { fill: currentColor; stroke: none; }
#themeMenu, #themeMenu:active { color: ${if (isDark) "#ffffff" else "#000000"}; background: transparent; }
.trashIcon { fill: currentColor !important; stroke: none !important; }
#themeOptions { position: fixed; left: 0; top: 0; z-index: 1000; display: none; min-width: 126px; padding: 5px; border: 1px solid rgba(100, 130, 160, .25); border-radius: 12px; background: var(--float); box-shadow: 0 8px 26px rgba(20, 80, 140, .2); pointer-events: auto; }
#themeOptions.open { display: grid; gap: 2px; }
.themeOption { display: flex; align-items: center; gap: 8px; width: 100%; padding: 7px 8px; border: 0; border-radius: 8px; background: transparent; color: var(--text); font-size: 12px; text-align: left; white-space: nowrap; }
.themeOption:active, .themeOption.active { background: var(--primary2); color: var(--primary); }
#actionBar .danger { color: ${if (isDark) "#ff9a9a" else "#c62828"}; }
#zoomToast { position: fixed; left: 50%; bottom: calc(62px + var(--ime-bottom, 0px)); z-index: 7; display: none; transform: translateX(-50%); padding: 6px 10px; border-radius: 10px; background: rgba(0, 0, 0, .52); color: #ffffff; font-size: 12px; font-weight: 700; line-height: 1; pointer-events: none; }
#zoomToast.visible { display: block; }
#stage { width: 100%; height: 100%; touch-action: none; }
svg { width: 100%; height: 100%; display: block; }
.link { fill: none; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.2; stroke-opacity: .9; }
.dropPreviewLink { fill: none; stroke: var(--muted); stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.2; stroke-opacity: .52; pointer-events: none; }
.dropGhost { fill: var(--surface); fill-opacity: .72; stroke: var(--muted); stroke-width: 1.5; stroke-dasharray: 6 5; pointer-events: none; }
.node rect.body { filter: drop-shadow(0 8px 16px rgba(31, 91, 156, .13)); }
.node { cursor: grab; }
.node.dragging { opacity: .78; cursor: grabbing; }
.node text { font-size: 14px; font-weight: 650; pointer-events: none; }
.node.root text { font-size: 15px; font-weight: 700; }
.nodeEditor { width: 100%; height: 100%; padding: 0 14px; border: 2px solid var(--primary); border-radius: 16px; outline: none; background: transparent; font: 650 14px system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; text-align: left; -webkit-user-select: text; user-select: text; }
.node.root .nodeEditor { border-radius: 20px; font-size: 15px; font-weight: 700; text-align: center; }
.halo { fill: none; stroke-width: 1.8; opacity: .95; }
.nodeControl { cursor: pointer; }
.nodeControl circle { stroke-width: 2; }
.nodeControl text { font-size: 12px; font-weight: 750; pointer-events: none; }
</style>
</head>
<body>
<div id="zoomToast" role="status" aria-live="polite"></div>
<div id="actionBar">
  <div id="globalActions">
    <button class="toolBtn iconBtn layoutBtn" id="layoutToggle" aria-label="切换为双向布局" title="当前为单向布局，点击切换为双向布局" data-mode="side">
      <svg class="mindMapIcon layoutGlyph layoutGlyph-oneWay" viewBox="0 0 24 24" aria-hidden="true"><path d="M9.3 12H14l3-4M14 12l3 4M17 8h3M17 16h3"/></svg>
      <svg class="mindMapIcon layoutGlyph layoutGlyph-twoWay" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="2.4"/><path d="M9.6 11.2 6 8H3.5M9.6 12.8 6 16H3.5M14.4 11.2 18 8h2.5M14.4 12.8 18 16h2.5"/></svg>
    </button>
    <button class="toolBtn iconBtn collapseBtn" id="collapseAll" aria-label="折叠全部" title="折叠全部节点" data-action="collapse">
      <svg class="mindMapIcon collapseGlyph collapseGlyph-collapse" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="7.5"/><path d="M8.5 12h7"/></svg>
      <svg class="mindMapIcon collapseGlyph collapseGlyph-expand" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="7.5"/><path d="M8.5 12h7M12 8.5v7"/></svg>
    </button>
    <button class="toolBtn historyBtn" id="undo" aria-label="撤销" title="撤销"><svg class="historyIcon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12.5 8c-2.65 0-5.05 .99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z" /></svg></button>
    <button class="toolBtn historyBtn" id="redo" aria-label="恢复" title="恢复"><svg class="historyIcon" viewBox="0 0 24 24" aria-hidden="true"><path d="M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73 .72 5.12 1.88L13 16h9V7l-3.6 3.6z" /></svg></button>
    <div id="themeControl">
      <button class="toolBtn themeBtn" id="themeMenu" aria-label="节点主题" title="节点主题">
        <svg id="themeIcon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 22C6.49 22 2 17.51 2 12S6.49 2 12 2s10 4.04 10 9c0 3.31-2.69 6-6 6h-1.77c-.28 0-.5.22-.5.5 0 .12.05.23.13.33.41.47.64 1.06.64 1.67C14.5 20.88 13.38 22 12 22ZM12 4c-4.41 0-8 3.59-8 8s3.59 8 8 8c.28 0 .5-.22.5-.5 0-.16-.08-.28-.14-.35-.41-.46-.63-1.05-.63-1.65 0-1.38 1.12-2.5 2.5-2.5H16c2.21 0 4-1.79 4-4 0-4.86-3.59-8-8-8Z"/><circle cx="6.5" cy="11.5" r="1.5"/><circle cx="9.5" cy="7.5" r="1.5"/><circle cx="14.5" cy="7.5" r="1.5"/><circle cx="17.5" cy="11.5" r="1.5"/></svg>
      </button>
    </div>
    <button class="toolBtn iconBtn" id="fit" aria-label="适配视图" title="适配全图"><svg class="actionIcon" viewBox="0 0 24 24" aria-hidden="true"><path d="M3.5 11.5 12 4l8.5 7.5M5.5 10.5V20h13v-9.5M9.5 20v-5h5v5"/></svg></button>
    <button class="toolBtn iconBtn" id="exportImage" aria-label="导出图片" title="导出思维导图图片"><svg class="actionIcon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v11m0-11 4 4m-4-4L8 7M5 13v6h14v-6"/></svg></button>
  </div>
  <div id="nodeActions">
    <button class="nodeAction" data-action="addSibling" aria-label="添加同级节点" title="添加同级节点" disabled><svg class="actionIcon suppliedActionIcon" viewBox="0 0 48 48" aria-hidden="true"><g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M8 24H25.5"/><circle cx="36" cy="24" r="10"/><path d="M36 19.5V28.5M31.5 24H40.5"/></g><circle cx="7.5" cy="24" r="3.8" fill="currentColor" stroke="none"/></svg></button>
    <button class="nodeAction" data-action="addChild" aria-label="添加子节点" title="添加子节点" disabled><svg class="actionIcon suppliedActionIcon" viewBox="0 0 48 48" aria-hidden="true"><g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9H33"/><path d="M12 9V29H24"/><circle cx="35" cy="29" r="10"/><path d="M35 24.5V33.5M30.5 29H39.5"/></g><circle cx="36.5" cy="9" r="3.8" fill="currentColor" stroke="none"/></svg></button>
    <button class="nodeAction danger" data-action="delete" aria-label="删除节点" title="删除节点" disabled><svg class="actionIcon trashIcon" viewBox="0 0 24 24" aria-hidden="true"><path d="M16 9v10H8V9h8m-1.5-6h-5l-1 1H5v2h14V4h-3.5l-1-1zM18 7H6v12c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7z"/></svg></button>
  </div>
</div>
<div id="themeOptions" aria-label="节点主题选项">
  <button class="themeOption" data-theme="xmind">XMind</button>
  <button class="themeOption" data-theme="plain">默认</button>
</div>
<div id="stage"><svg id="svg"><g id="viewport"><g id="links"></g><g id="dropIndicator"></g><g id="nodes"></g></g></svg></div>
<script>
const isDark = $dark;
const initialThemeKey = '$themeKey';
const svg = document.getElementById('svg');
const viewport = document.getElementById('viewport');
const linksLayer = document.getElementById('links');
const dropIndicator = document.getElementById('dropIndicator');
const nodesLayer = document.getElementById('nodes');
const actionBar = document.getElementById('actionBar');
const nodeActions = document.getElementById('nodeActions');
const themeControl = document.getElementById('themeControl');
const themeMenu = document.getElementById('themeMenu');
const themeOptions = document.getElementById('themeOptions');
const layoutToggle = document.getElementById('layoutToggle');
const collapseAllBtn = document.getElementById('collapseAll');
const undoBtn = document.getElementById('undo');
const redoBtn = document.getElementById('redo');
const zoomToast = document.getElementById('zoomToast');
let zoomToastHideTimer = 0;
const SVG_NS = 'http://www.w3.org/2000/svg';
let nativeImeInset = null;
let lastImeTrace = '';
function visualViewportImeInset() {
  const visual = window.visualViewport;
  const visualHeight = visual ? visual.height : window.innerHeight;
  const visualOffsetTop = visual ? visual.offsetTop : 0;
  return Math.max(0, window.innerHeight - visualHeight - visualOffsetTop);
}
function effectiveImeInset() {
  return nativeImeInset == null ? visualViewportImeInset() : nativeImeInset;
}
function traceIme(reason, appliedInset) {
  const visual = window.visualViewport;
  const visualHeight = visual ? visual.height : window.innerHeight;
  const visualOffsetTop = visual ? visual.offsetTop : 0;
  const visualInset = visualViewportImeInset();
  const nativeValue = nativeImeInset == null ? 'null' : nativeImeInset.toFixed(1);
  const trace = 'ime reason=' + reason +
    ' native=' + nativeValue +
    ' visual=' + visualInset.toFixed(1) +
    ' effective=' + appliedInset.toFixed(1) +
    ' inner=' + Math.round(window.innerWidth) + 'x' + Math.round(window.innerHeight) +
    ' visualHeight=' + Math.round(visualHeight) +
    ' visualOffsetTop=' + Math.round(visualOffsetTop);
  if (trace !== lastImeTrace) {
    lastImeTrace = trace;
    traceDrag(trace);
  }
}
function updateImeInset(reason) {
  const inset = effectiveImeInset();
  document.documentElement.style.setProperty('--ime-bottom', inset + 'px');
  requestAnimationFrame(() => {
    updateActionBar('ime-' + (reason || 'update'));
    traceIme(reason || 'update', inset);
    if (editingKey) restoreEditingViewport();
  });
}
window.KardLeafMindMapSetImeBottom = value => {
  nativeImeInset = Math.max(0, Number(value) || 0);
  updateImeInset('native-set');
};
updateImeInset('initial');
window.addEventListener('resize', () => updateImeInset('window-resize'));
if (window.visualViewport) {
  window.visualViewport.addEventListener('resize', () => updateImeInset('visual-resize'));
  window.visualViewport.addEventListener('scroll', () => updateImeInset('visual-scroll'));
}
function updateHistoryButtons(canUndo, canRedo) {
  undoBtn.disabled = !canUndo;
  redoBtn.disabled = !canRedo;
  undoBtn.setAttribute('aria-disabled', String(!canUndo));
  redoBtn.setAttribute('aria-disabled', String(!canRedo));
}
window.KardLeafMindMapSetHistory = updateHistoryButtons;
function updateThemeMenu() {
  themeOptions.querySelectorAll('.themeOption').forEach(option => {
    option.classList.toggle('active', option.dataset.theme === activeThemeKey);
  });
  themeMenu.setAttribute('aria-label', '节点主题：' + activeTheme.label);
  themeMenu.title = '节点主题：' + activeTheme.label;
}
function applyNodeTheme(key) {
  const next = MIND_MAP_THEMES[key];
  if (!next) return;
  activeThemeKey = key;
  activeTheme = next;
  document.documentElement.style.setProperty('--bg', activeTheme.backgroundColor || (isDark ? '#101418' : '#f7fbff'));
  if (root) {
    root.color = activeTheme.rootColor;
    root.accent = activeTheme.nodeBorderColor || activeTheme.rootColor;
    root.children.forEach((child, childIndex) => {
      paintBranch(
        child,
        activeTheme.fills[childIndex % activeTheme.fills.length],
        activeTheme.accents[childIndex % activeTheme.accents.length],
        activeTheme.childFills ? activeTheme.childFills[childIndex % activeTheme.childFills.length] : undefined,
        activeTheme.branchTextColors ? activeTheme.branchTextColors[childIndex % activeTheme.branchTextColors.length] : undefined,
      );
    });
    nodes.forEach(prepareNodeMetrics);
    layoutMindMap();
    render();
    resetView('theme-change');
  }
  updateThemeMenu();
  themeOptions.classList.remove('open');
  const api = window.KardLeafMindMap;
  if (api && api.onThemeChange) api.onThemeChange(activeThemeKey);
}
themeMenu.addEventListener('pointerup', event => {
  event.preventDefault();
  event.stopPropagation();
  const willOpen = !themeOptions.classList.contains('open');
  themeOptions.classList.toggle('open', willOpen);
  if (willOpen) positionThemeOptions();
});
themeOptions.addEventListener('pointerup', event => {
  event.preventDefault();
  event.stopPropagation();
  let option = event.target;
  while (option && option !== themeOptions && option.tagName !== 'BUTTON') option = option.parentNode;
  if (option && option !== themeOptions) applyNodeTheme(option.dataset.theme);
});
document.addEventListener('pointerdown', event => {
  if (!themeControl.contains(event.target) && !themeOptions.contains(event.target)) themeOptions.classList.remove('open');
});
function positionThemeOptions() {
  if (!themeOptions.classList.contains('open')) return;
  const button = themeMenu.getBoundingClientRect();
  const width = themeOptions.offsetWidth;
  const left = clamp(button.left + (button.width - width) / 2, 8, window.innerWidth - width - 8);
  const top = Math.max(8, button.top - themeOptions.offsetHeight - 8);
  themeOptions.style.left = Math.round(left) + 'px';
  themeOptions.style.top = Math.round(top) + 'px';
}
window.addEventListener('resize', positionThemeOptions);
if (window.visualViewport) window.visualViewport.addEventListener('resize', positionThemeOptions);
window.KardLeafMindMapPrepareExport = function() {
  const bounds = visibleNodes.length ? mapBounds() : null;
  traceDrag(
    'export-prepare scale=' + scale.toExponential(3) +
      ' tx=' + Math.round(tx) + ' ty=' + Math.round(ty) +
      ' svg=' + Math.round(svg.getBoundingClientRect().width) + 'x' + Math.round(svg.getBoundingClientRect().height) +
      ' visibleNodes=' + visibleNodes.length +
      ' map=' + (bounds ? Math.round(bounds.width) + 'x' + Math.round(bounds.height) : 'none') +
      ' ime=' + effectiveImeInset().toFixed(1)
  );
  actionBar.style.visibility = 'hidden';
  zoomToast.style.visibility = 'hidden';
};
window.KardLeafMindMapRestoreExport = function() {
  actionBar.style.visibility = '';
  zoomToast.style.visibility = '';
  traceDrag('export-restore scale=' + scale.toExponential(3) + ' tx=' + Math.round(tx) + ' ty=' + Math.round(ty));
};
const MIND_MAP_THEMES = {
  xmind: {
    label: 'XMind',
    backgroundColor: isDark ? '#101418' : '#ffffff',
    rootColor: isDark ? '#b9c7ff' : '#000228',
    rootTextColor: isDark ? '#101418' : '#ffffff',
    surfaceColor: isDark ? '#1a2028' : '#ffffff',
    textColor: isDark ? '#edf1f7' : '#493f3a',
    branchTextColor: isDark ? '#f7f9fc' : '#ffffff',
    branchTextColors: isDark
      ? ['#ffffff', '#172025', '#172025', '#ffffff', '#ffffff', '#172025', '#ffffff', '#172025']
      : ['#ffffff', '#493f3a', '#493f3a', '#ffffff', '#ffffff', '#493f3a', '#ffffff', '#493f3a'],
    childFills: isDark
      ? ['#56353a', '#55442f', '#514b2b', '#2e4d40', '#303d63', '#56353a', '#55442f', '#514b2b']
      : ['#f8dad8', '#faeddd', '#fcf6d6', '#e4f6ed', '#e1e7ff', '#f8dad8', '#faeddd', '#fcf6d6'],
    fills: isDark
      ? ['#b4574d', '#ad824d', '#ac9a38', '#478c6a', '#6479d4', '#b4574d', '#ad824d', '#ac9a38']
      : ['#e65244', '#eaa45e', '#eed34e', '#54b981', '#4f68f6', '#e65244', '#eaa45e', '#eed34e'],
    accents: isDark
      ? ['#e2766b', '#d4ab70', '#d9c45c', '#77c69c', '#aab8ff', '#e2766b', '#d4ab70', '#d9c45c']
      : ['#e65244', '#eaa45e', '#eed34e', '#54b981', '#4f68f6', '#e65244', '#eaa45e', '#eed34e'],
    rootRadius: 12,
    branchRadius: 8,
    childRadius: 7,
    childBorder: false,
    nodeShadow: 'none',
    compactDescendants: true,
    compactNodeMinWidth: 72,
    compactNodeMaxWidth: 164,
    compactNodeMinHeight: 36,
    compactNodeFontSize: 12,
    compactNodeLineHeight: 16,
    compactNodeHorizontalPadding: 18,
    compactNodeVerticalPadding: 7,
  },
  plain: {
    label: '默认',
    backgroundColor: '#ffffff',
    rootColor: '#ffffff',
    rootTextColor: '#424242',
    surfaceColor: '#ffffff',
    textColor: '#424242',
    branchTextColor: '#424242',
    fills: ['#ffffff'],
    accents: ['#9e9e9e'],
    childFills: ['#ffffff'],
    nodeBorderColor: '#b0b0b0',
    rootRadius: 8,
    branchRadius: 8,
    childRadius: 6,
    childBorder: true,
    nodeShadow: 'none',
  },
};
let activeThemeKey = MIND_MAP_THEMES[initialThemeKey] ? initialThemeKey : 'plain';
let activeTheme = MIND_MAP_THEMES[activeThemeKey];
const minNodeWidth = 72;
const maxNodeWidth = 280;
const minRootWidth = 140;
const maxRootWidth = 300;
const minNodeHeight = 46;
const textLineHeight = 18;
const textVerticalPadding = 14;
const horizontalGap = 28;
const siblingGap = 14;
const panHorizontalSensitivity = 1.08;
const panVerticalSensitivity = 1.18;
const panFlingFriction = .90;
const panFlingMaxVelocity = 2.4;
const longPressDelayMs = 304;
const longPressMoveSlopPx = 9;
const commitHoldTimeoutMs = 1200;
const editViewportSettleMs = 900;
const textMeasureCanvas = document.createElement('canvas');
const textMeasureContext = textMeasureCanvas.getContext('2d');
let rootData = null;
let nodesData = [];
let root = null;
let nodes = [];
let visibleNodes = [];
let hasMindMapData = false;
let pendingTreeData = null;
let layoutMode = 'side';
let tx = 0, ty = 0, scale = 1;
let drag = null;
let committedDrag = null;
let pinch = null;
let panFlingFrame = 0;
let dropIntent = null;
let lastDropResolution = 'not-resolved';
let lastSiblingCandidates = 'none';
let lastChildHits = 'none';
let animationVersion = 0;
let suppressTapUntil = 0;
let lastBlankTapTime = 0;
let selectedKey = null;
let editingKey = null;
let editViewport = null;
let pendingTransformFrame = 0;
const collapsedKeys = new Set();
const activePointers = new Map();
let dragTraceSequence = 0;
function traceDrag(message) {
  const api = window.KardLeafMindMap;
  if (api && api.onDragTrace) api.onDragTrace('seq=' + dragTraceSequence + ' ' + message);
}
function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
function setTransform() {
  viewport.setAttribute('transform', 'translate(' + tx + ' ' + ty + ') scale(' + scale + ')');
}
function scheduleTransform() {
  if (pendingTransformFrame) return;
  pendingTransformFrame = requestAnimationFrame(() => {
    pendingTransformFrame = 0;
    setTransform();
  });
}
function stopPanFling() {
  if (!panFlingFrame) return;
  cancelAnimationFrame(panFlingFrame);
  panFlingFrame = 0;
}
function startPanFling(velocityX, velocityY) {
  if (Math.hypot(velocityX, velocityY) < .08) return;
  stopPanFling();
  let previousAt = performance.now();
  function step(now) {
    const elapsed = Math.min(32, Math.max(1, now - previousAt));
    previousAt = now;
    tx += velocityX * elapsed;
    ty += velocityY * elapsed;
    setTransform();
    const friction = Math.pow(panFlingFriction, elapsed / 16.67);
    velocityX *= friction;
    velocityY *= friction;
    if (Math.hypot(velocityX, velocityY) < .08) {
      panFlingFrame = 0;
    } else {
      panFlingFrame = requestAnimationFrame(step);
    }
  }
  panFlingFrame = requestAnimationFrame(step);
}
function showZoomToast() {
  zoomToast.textContent = Math.round(scale * 100) + '%';
  zoomToast.classList.add('visible');
  if (zoomToastHideTimer) clearTimeout(zoomToastHideTimer);
  zoomToastHideTimer = setTimeout(() => {
    zoomToast.classList.remove('visible');
    zoomToastHideTimer = 0;
  }, 900);
}
function screenToWorld(clientX, clientY) { return { x: (clientX - tx) / scale, y: (clientY - ty) / scale }; }
function distance(a, b) { const dx = a.x - b.x; const dy = a.y - b.y; return Math.hypot(dx, dy); }
function centerOf(a, b) { return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }; }
function currentTwoPointers() { return Array.from(activePointers.values()).slice(0, 2); }
function nodeVisualWidth(n) { return n && n.width ? n.width : (n && n.root ? minRootWidth : minNodeWidth); }
function nodeVisualHeight(n) { return n && n.height ? n.height : minNodeHeight; }
function nodeCenter(n) { return { x: n.x + nodeVisualWidth(n) / 2, y: n.y + nodeVisualHeight(n) / 2 }; }
function effChildren(n) { return collapsedKeys.has(n.key) ? [] : n.children; }
function isCompactNode(node) {
  return Boolean(activeTheme.compactDescendants && node && !node.root && node.depth > 1);
}
function measureTextWidth(text, isRootNode, compact) {
  const fontSize = isRootNode ? 15 : (compact ? (activeTheme.compactNodeFontSize || 14) : 14);
  if (!textMeasureContext) return Array.from(text || '').length * fontSize;
  textMeasureContext.font = (isRootNode ? '700 15px ' : '650 ' + fontSize + 'px ') + 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
  return textMeasureContext.measureText(text || '').width;
}
function wrapNodeText(text, isRootNode, compact) {
  const value = String(text || '').trim() || '未命名节点';
  const maxTextWidth = isRootNode
    ? maxRootWidth - 30
    : (compact ? (activeTheme.compactNodeMaxWidth || maxNodeWidth) - (activeTheme.compactNodeHorizontalPadding || 30) : maxNodeWidth - 30);
  const lines = [];
  let line = '';
  Array.from(value).forEach(char => {
    const candidate = line + char;
    if (line && measureTextWidth(candidate, isRootNode, compact) > maxTextWidth) {
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
  const compact = isCompactNode(node);
  const widthPadding = compact ? (activeTheme.compactNodeHorizontalPadding || 30) : 30;
  const minWidth = node.root ? minRootWidth : (compact ? (activeTheme.compactNodeMinWidth || minNodeWidth) : minNodeWidth);
  const maxWidth = node.root ? maxRootWidth : (compact ? (activeTheme.compactNodeMaxWidth || maxNodeWidth) : maxNodeWidth);
  const lineHeight = compact ? (activeTheme.compactNodeLineHeight || textLineHeight) : textLineHeight;
  const verticalPadding = compact ? (activeTheme.compactNodeVerticalPadding || textVerticalPadding) : textVerticalPadding;
  const minHeight = compact ? (activeTheme.compactNodeMinHeight || minNodeHeight) : minNodeHeight;
  node.lines = wrapNodeText(node.text, node.root, compact);
  const measuredWidth = Math.max.apply(null, node.lines.map(line => measureTextWidth(line, node.root, compact)));
  node.width = clamp(
    Math.ceil(measuredWidth + widthPadding),
    minWidth,
    maxWidth,
  );
  node.height = Math.max(minHeight, node.lines.length * lineHeight + verticalPadding * 2);
}
function paintBranch(node, fill, accent, childFill, branchTextColor) {
  node.color = fill;
  node.accent = accent;
  node.childColor = childFill;
  node.branchTextColor = branchTextColor;
  node.children.forEach(child => paintBranch(child, fill, accent, childFill, branchTextColor));
}
function buildTree(nextRoot, nextNodes) {
  rootData = nextRoot || { text: '未命名节点', line: 1, sourceOffset: 0 };
  nodesData = Array.isArray(nextNodes) ? nextNodes : [];
  root = { key: 'root', index: 0, depth: 0, text: rootData.text, line: rootData.line, sourceOffset: rootData.sourceOffset, x: 0, y: 0, parent: null, children: [], root: true, direction: 'right', color: activeTheme.rootColor, accent: activeTheme.nodeBorderColor || activeTheme.rootColor };
  const stack = [root];
  const keyCounts = new Map();
  nodes = [root];
  nodesData.forEach(item => {
    const depth = Math.max(1, Number(item.depth) || 1);
    while (stack.length > depth) stack.pop();
    const parent = stack[stack.length - 1] || root;
    const keyBase = String(item.text || '') + '|' + String(depth);
    const keyCount = (keyCounts.get(keyBase) || 0) + 1;
    keyCounts.set(keyBase, keyCount);
    const node = { key: keyBase + '|' + keyCount, index: item.index, depth: depth, text: item.text, line: item.line, sourceOffset: item.sourceOffset, x: 0, y: 0, parent: parent, children: [], root: false, direction: 'right', color: activeTheme.rootColor, accent: activeTheme.rootColor };
    parent.children.push(node);
    nodes.push(node);
    stack[depth] = node;
  });
  root.children.forEach((child, childIndex) => {
    paintBranch(
      child,
      activeTheme.fills[childIndex % activeTheme.fills.length],
      activeTheme.accents[childIndex % activeTheme.accents.length],
      activeTheme.childFills ? activeTheme.childFills[childIndex % activeTheme.childFills.length] : undefined,
      activeTheme.branchTextColors ? activeTheme.branchTextColors[childIndex % activeTheme.branchTextColors.length] : undefined,
    );
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
  const parent = node.parent || root;
  node.x = node.direction === 'left'
    ? parent.x - horizontalGap - nodeVisualWidth(node)
    : parent.x + nodeVisualWidth(parent) + horizontalGap;
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
  if (rootChildren.length) {
    const firstCenter = nodeCenter(rootChildren[0]).y;
    const lastCenter = nodeCenter(rootChildren[rootChildren.length - 1]).y;
    root.y = (firstCenter + lastCenter) / 2 - nodeVisualHeight(root) / 2;
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
  layoutToggle.dataset.mode = isSide ? 'side' : 'full';
  layoutToggle.setAttribute('aria-label', isSide ? '切换到双向布局' : '切换到单向布局');
  layoutToggle.title = isSide ? '当前为单向布局，点击切换为双向布局' : '当前为双向布局，点击切换为单向布局';
}
function updateCollapseAllButton() {
  const willExpand = collapsedKeys.size > 0;
  collapseAllBtn.dataset.action = willExpand ? 'expand' : 'collapse';
  collapseAllBtn.setAttribute('aria-label', willExpand ? '展开全部' : '折叠全部');
  collapseAllBtn.title = willExpand ? '展开全部节点' : '折叠全部节点';
}
function toggleLayoutMode() {
  if (!root) return;
  const previousPositions = capturePositions();
  layoutMode = layoutMode === 'side' ? 'full' : 'side';
  updateLayoutButton();
  animateLayoutFrom(previousPositions, 220, () => resetView('layout-toggle'));
}
function capturePositions() {
  const positions = new Map();
  nodes.forEach(n => positions.set(n.key, { x: n.x, y: n.y }));
  return positions;
}
function animateLayoutFrom(previousPositions, duration, onComplete) {
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
    else if (onComplete) onComplete();
  }
  requestAnimationFrame(step);
}
function sameMindMapData(nextRoot, nextNodes) {
  if (!hasMindMapData || !nextRoot || !rootData || !Array.isArray(nextNodes)) return false;
  if (rootData.text !== nextRoot.text || rootData.line !== nextRoot.line || rootData.sourceOffset !== nextRoot.sourceOffset) return false;
  if (nodesData.length !== nextNodes.length) return false;
  return nextNodes.every((item, index) => {
    const current = nodesData[index];
    return current && current.index === item.index && current.depth === item.depth &&
      current.text === item.text && current.line === item.line && current.sourceOffset === item.sourceOffset;
  });
}
function cancelActivePointerInteraction(reason) {
  if (drag || committedDrag || pinch || activePointers.size) {
    traceDrag(
      'interaction-reset reason=' + reason +
        ' dragging=' + !!drag + ' commitHeld=' + !!committedDrag +
        ' pinching=' + !!pinch + ' activePointers=' + activePointers.size,
    );
  }
  clearDragLongPress(drag);
  stopPanFling();
  restoreDraggedSubtree(drag);
  clearCommittedDrag(reason, false);
  drag = null;
  pinch = null;
  dropIntent = null;
  activePointers.clear();
  animationVersion += 1;
}
function applyMindMapData(nextRoot, nextNodes) {
  if (sameMindMapData(nextRoot, nextNodes)) return;
  if (drag || pinch || activePointers.size) {
    traceDrag('tree-refresh deferred while gesture active activePointers=' + activePointers.size + ' oldNodes=' + nodes.length + ' newNodes=' + (Array.isArray(nextNodes) ? nextNodes.length : -1));
    pendingTreeData = { root: nextRoot, nodes: Array.isArray(nextNodes) ? nextNodes.slice() : null };
    return;
  }
  pendingTreeData = null;
  traceDrag(
    'tree-refresh oldNonRootNodes=' + Math.max(0, nodes.length - 1) +
      ' newNonRootNodes=' + (Array.isArray(nextNodes) ? nextNodes.length : -1) +
      ' commitHeld=' + !!committedDrag,
  );
  cancelActivePointerInteraction('tree-refresh');
  const previousPositions = hasMindMapData ? capturePositions() : new Map();
  const selectedBefore = selectedKey ? nodes.find(item => item.key === selectedKey) : null;
  const collapsedBeforeIndices = Array.from(collapsedKeys).map(key => {
    const item = nodes.find(candidate => candidate.key === key);
    return item ? item.index : -1;
  }).join(',');
  buildTree(nextRoot, nextNodes);
  const validKeys = new Set(nodes.map(n => n.key));
  Array.from(collapsedKeys).forEach(key => { if (!validKeys.has(key)) collapsedKeys.delete(key); });
  if (selectedKey && !validKeys.has(selectedKey)) {
    selectedKey = null;
  }
  const selectedAfter = selectedKey ? nodes.find(item => item.key === selectedKey) : null;
  const collapsedAfterIndices = Array.from(collapsedKeys).map(key => {
    const item = nodes.find(candidate => candidate.key === key);
    return item ? item.index : -1;
  }).join(',');
  traceDrag(
    'tree-state keyStrategy=text-depth-occurrence' +
      ' selectedBefore=' + (selectedBefore ? selectedBefore.index : 'none') +
      ' selectedAfter=' + (selectedAfter ? selectedAfter.index : 'none') +
      ' selectedBeforeTextLen=' + (selectedBefore ? selectedBefore.text.length : 'none') +
      ' selectedAfterTextLen=' + (selectedAfter ? selectedAfter.text.length : 'none') +
      ' collapsedBefore=' + collapsedBeforeIndices +
      ' collapsedAfter=' + collapsedAfterIndices +
      ' collapsedCount=' + collapsedKeys.size,
  );
  updateActionBar();
  updateCollapseAllButton();
  if (!hasMindMapData) {
    layoutMindMap();
    render();
    hasMindMapData = true;
    requestAnimationFrame(() => resetView('initial-fit'));
  } else {
    animateLayoutFrom(previousPositions, 180);
  }
}
function flushPendingTreeData() {
  if (!pendingTreeData || drag || pinch || activePointers.size) return;
  const data = pendingTreeData;
  pendingTreeData = null;
  if (data.nodes) {
    traceDrag('tree-refresh flush oldNodes=' + nodes.length + ' newNodes=' + data.nodes.length);
    applyMindMapData(data.root, data.nodes);
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
function pointInsideNode(node, point) {
  if (!node || !point) return false;
  return point.x >= node.x && point.x <= node.x + nodeVisualWidth(node) &&
    point.y >= node.y && point.y <= node.y + nodeVisualHeight(node);
}
function visibleSubtreeBounds(node) {
  let top = node.y;
  let bottom = node.y + nodeVisualHeight(node);
  effChildren(node).forEach(child => {
    const childBounds = visibleSubtreeBounds(child);
    top = Math.min(top, childBounds.top);
    bottom = Math.max(bottom, childBounds.bottom);
  });
  return { top, bottom };
}
function directChildrenWithout(parent, movingNode) {
  return parent ? parent.children.filter(child => child !== movingNode) : [];
}
function moveIntentRejectionReason(movingNode, intent, allowNoop = false) {
  if (!movingNode) return 'moving-missing';
  if (movingNode.root) return 'moving-root';
  if (!movingNode.parent) return 'moving-parent-missing';
  if (!intent) return 'intent-missing';
  if (!intent.parent) return 'target-parent-missing';
  if (intent.parent === movingNode || isDescendantOf(intent.parent, movingNode)) return 'cycle';
  const oldIndex = movingNode.parent ? movingNode.parent.children.indexOf(movingNode) : -1;
  const childCount = directChildrenWithout(intent.parent, movingNode).length;
  if (!Number.isInteger(intent.childIndex) || intent.childIndex < 0 || intent.childIndex > childCount) {
    return 'child-index-out-of-range-' + intent.childIndex + '-of-' + childCount;
  }
  if (!allowNoop && movingNode.parent === intent.parent && oldIndex === intent.childIndex) return 'same-parent-noop';
  return '';
}
function currentDropIntent(movingNode) {
  const parent = movingNode && movingNode.parent;
  const childIndex = parent ? parent.children.indexOf(movingNode) : -1;
  if (childIndex < 0) return null;
  return {
    parent,
    childIndex,
    source: 'current',
    direction: movingNode.direction,
    x: movingNode.x,
    y: movingNode.y,
  };
}
function finalChildDirection(parent, childIndex, fallback) {
  if (parent.root) return layoutMode === 'full' && childIndex % 2 === 1 ? 'left' : 'right';
  return parent.direction || fallback;
}
function siblingDropIntent(movingNode, anchor, insertBefore) {
  if (!anchor || !anchor.parent) return null;
  const siblings = directChildrenWithout(anchor.parent, movingNode);
  const anchorIndex = siblings.indexOf(anchor);
  if (anchorIndex < 0) return null;
  const bounds = visibleSubtreeBounds(anchor);
  const childIndex = anchorIndex + (insertBefore ? 0 : 1);
  const direction = finalChildDirection(anchor.parent, childIndex, anchor.direction);
  const previous = siblings.slice(0, childIndex).reverse().find(node => node.direction === direction) || null;
  const next = siblings.slice(childIndex).find(node => node.direction === direction) || null;
  const previousBounds = previous ? visibleSubtreeBounds(previous) : null;
  const nextBounds = next ? visibleSubtreeBounds(next) : null;
  const previewY = previousBounds && nextBounds
    ? (previousBounds.bottom + nextBounds.top - nodeVisualHeight(movingNode)) / 2
    : nextBounds
      ? nextBounds.top - siblingGap - nodeVisualHeight(movingNode)
      : previousBounds
        ? previousBounds.bottom + siblingGap
        : insertBefore
          ? bounds.top - siblingGap - nodeVisualHeight(movingNode)
          : bounds.bottom + siblingGap;
  return {
    parent: anchor.parent,
    childIndex,
    source: insertBefore ? 'sibling-before' : 'sibling-after',
    direction,
    x: direction === anchor.direction
      ? anchor.x
      : direction === 'left'
        ? anchor.parent.x - horizontalGap - nodeVisualWidth(movingNode)
        : anchor.parent.x + nodeVisualWidth(anchor.parent) + horizontalGap,
    y: previewY,
  };
}
function resolveSiblingDropIntent(movingNode) {
  const point = nodeCenter(movingNode);
  const maxVerticalDistance = siblingGap + nodeVisualHeight(movingNode) / 2;
  const candidates = visibleNodes
    .filter(node => node !== movingNode && !node.root && node.parent && !isDescendantOf(node, movingNode))
    .filter(node => pointInSiblingZoneX(node, point, movingNode))
    .map(node => {
      const bounds = { top: node.y, bottom: node.y + nodeVisualHeight(node) };
      const verticalDistance = point.y < bounds.top
        ? bounds.top - point.y
        : point.y > bounds.bottom
          ? point.y - bounds.bottom
          : 0;
      return { node, bounds, verticalDistance };
    })
    .filter(candidate => candidate.verticalDistance <= maxVerticalDistance)
    .sort((a, b) =>
      a.verticalDistance - b.verticalDistance ||
      Math.abs(point.x - nodeCenter(a.node).x) - Math.abs(point.x - nodeCenter(b.node).x) ||
      b.node.depth - a.node.depth
    );
  lastSiblingCandidates = candidates.length
    ? candidates.slice(0, 8).map(candidate =>
        candidate.node.index + ':parent-' + candidate.node.parent.index + ':' +
          'body-' + Math.round(candidate.bounds.top) + '-' + Math.round(candidate.bounds.bottom) +
          ':distance-' + Math.round(candidate.verticalDistance)
      ).join(',') + (candidates.length > 8 ? ',more' : '')
    : 'none';
  const anchor = candidates.length ? candidates[0].node : null;
  return anchor ? siblingDropIntent(movingNode, anchor, point.y < nodeCenter(anchor).y) : null;
}
function pointInChildZone(node, point, movingNode) {
  const direction = node.root
    ? (layoutMode === 'full' && point.x < nodeCenter(node).x ? 'left' : 'right')
    : node.direction;
  const reach = horizontalGap + nodeVisualWidth(movingNode);
  const centerX = nodeCenter(node).x;
  const edge = direction === 'left' ? node.x : node.x + nodeVisualWidth(node);
  const insideX = direction === 'left'
    ? point.x <= centerX && point.x >= edge - reach
    : point.x >= centerX && point.x <= edge + reach;
  return insideX && point.y >= node.y && point.y <= node.y + nodeVisualHeight(node);
}
function pointInSiblingZoneX(node, point, movingNode) {
  const direction = node.root ? 'right' : node.direction;
  const reach = horizontalGap + nodeVisualWidth(movingNode);
  const centerX = nodeCenter(node).x;
  return direction === 'left'
    ? point.x >= centerX && point.x <= node.x + nodeVisualWidth(node) + reach
    : point.x <= centerX && point.x >= node.x - reach;
}
function childDropIntent(movingNode, parent, source) {
  const children = directChildrenWithout(parent, movingNode);
  const direction = finalChildDirection(parent, children.length, movingNode.direction);
  const sameDirectionChildren = children.filter(child => child.direction === direction);
  const lastChild = sameDirectionChildren[sameDirectionChildren.length - 1] || null;
  return {
    parent,
    childIndex: children.length,
    source,
    direction,
    x: direction === 'left'
      ? parent.x - horizontalGap - nodeVisualWidth(movingNode)
      : parent.x + nodeVisualWidth(parent) + horizontalGap,
    y: lastChild
      ? visibleSubtreeBounds(lastChild).bottom + siblingGap
      : parent.y + (nodeVisualHeight(parent) - nodeVisualHeight(movingNode)) / 2,
  };
}
function resolveChildDropIntent(movingNode) {
  const point = nodeCenter(movingNode);
  const hits = visibleNodes
    .filter(node => node !== movingNode && !isDescendantOf(node, movingNode))
    .map(node => {
      const body = pointInsideNode(node, point);
      const childZone = pointInChildZone(node, point, movingNode);
      return {
        node,
        body: body && childZone,
        childSide: !body && childZone,
      };
    })
    .filter(hit => hit.body || hit.childSide)
    .sort((a, b) => Number(b.body) - Number(a.body) || b.node.depth - a.node.depth);
  lastChildHits = hits.length
    ? hits.slice(0, 8).map(hit =>
        hit.node.index + ':' + (hit.body ? 'body' : 'side') + ':parent-' +
          (hit.node.parent ? hit.node.parent.index : 'none') + ':y-' +
          Math.round(hit.node.y) + '-' + Math.round(hit.node.y + nodeVisualHeight(hit.node))
      ).join(',') + (hits.length > 8 ? ',more' : '')
    : 'none';
  const hit = hits[0];
  return hit ? childDropIntent(movingNode, hit.node, hit.body ? 'node-body-child' : 'node-child-side') : null;
}
function resolveDropIntent(movingNode, allowNoop = false) {
  if (!movingNode || movingNode.root) {
    lastDropResolution = 'priority=none rejection=' + moveIntentRejectionReason(movingNode, null, allowNoop);
    return null;
  }
  const childIntent = resolveChildDropIntent(movingNode);
  const siblingIntent = resolveSiblingDropIntent(movingNode);
  const childRejection = moveIntentRejectionReason(movingNode, childIntent, allowNoop);
  const siblingRejection = moveIntentRejectionReason(movingNode, siblingIntent, allowNoop);
  let selectedIntent = null;
  let priority = 'none';
  if (childIntent && childIntent.source === 'node-body-child' && !childRejection) {
    selectedIntent = childIntent;
    priority = 'node-body-child';
  } else if (!siblingRejection) {
    selectedIntent = siblingIntent;
    priority = 'sibling-anchor';
  } else if (!childRejection) {
    selectedIntent = childIntent;
    priority = 'node-child-side';
  }
  lastDropResolution =
    'priority=' + priority +
      ' siblingCandidates=[' + lastSiblingCandidates + ']' +
      ' childHits=[' + lastChildHits + ']' +
      ' siblingRejection=' + (siblingRejection || 'none') +
      ' childRejection=' + (childRejection || 'none');
  return selectedIntent;
}
function dropIntentKey(intent) {
  return intent ? intent.parent.index + ':' + intent.childIndex + ':' + intent.source : '';
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
function traceViewportChange(reason, beforeScale, beforeTx, beforeTy, readableFloor) {
  const rect = svg.getBoundingClientRect();
  const b = mapBounds();
  traceDrag(
    'viewport-change reason=' + reason +
      ' scaleBefore=' + beforeScale.toFixed(3) + ' scaleAfter=' + scale.toFixed(3) +
      ' scaleRaw=' + (Number.isFinite(scale) ? scale.toExponential(3) : String(scale)) +
      ' txBefore=' + Math.round(beforeTx) + ' txAfter=' + Math.round(tx) +
      ' tyBefore=' + Math.round(beforeTy) + ' tyAfter=' + Math.round(ty) +
      ' svg=' + Math.round(rect.width) + 'x' + Math.round(rect.height) +
      ' map=' + Math.round(b.width) + 'x' + Math.round(b.height) +
      ' layout=' + layoutMode + ' activePointers=' + activePointers.size +
      ' readableFloor=' + (readableFloor == null ? 'none' : readableFloor) +
      ' dragging=' + !!drag + ' pinching=' + !!pinch,
  );
}
function restoreEditingViewport() {
  if (!editViewport) return false;
  if (Date.now() > editViewport.lockUntil) {
    editViewport = null;
    return false;
  }
  const node = findNodeByIndex(editViewport.nodeIndex);
  if (!node) return false;
  const rect = svg.getBoundingClientRect();
  const center = nodeCenter(node);
  const imeInset = Math.max(0, parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--ime-bottom')) || 0);
  const visual = window.visualViewport;
  const visibleHeight = visual && visual.height > 0 && visual.height < rect.height - 1
    ? visual.height
    : Math.max(1, rect.height - imeInset);
  scale = editViewport.scale;
  tx = rect.width / 2 - center.x * scale;
  ty = visibleHeight / 2 - center.y * scale;
  setTransform();
  return true;
}
function resetView(reason) {
  if (!root || !visibleNodes.length) return;
  if (restoreEditingViewport()) return;
  const beforeScale = scale;
  const beforeTx = tx;
  const beforeTy = ty;
  const rect = svg.getBoundingClientRect();
  const b = mapBounds();
  if (visibleNodes.length === 1) {
    const center = nodeCenter(root);
    const fittedScale = Math.min(
      Math.max(1, rect.width - 56) / (nodeVisualWidth(root) + 20),
      Math.max(1, rect.height - 56) / (nodeVisualHeight(root) + 20),
      1.18,
    );
    scale = Math.min(fittedScale, 1.18);
    tx = rect.width / 2 - center.x * scale;
    ty = rect.height / 2 - center.y * scale;
    setTransform();
    traceViewportChange(reason || 'fit', beforeScale, beforeTx, beforeTy);
    return;
  }
  if (layoutMode === 'side') {
    const fittedScale = Math.min(
      Math.max(1, rect.width - 56) / (b.width + 20),
      Math.max(1, rect.height - 56) / (b.height + 20),
      1.18,
    );
    scale = Math.min(fittedScale, 1.18);
    tx = 28 - b.minX * scale;
    ty = rect.height / 2 - ((b.minY + b.maxY) / 2) * scale;
    setTransform();
    traceViewportChange(reason || 'fit', beforeScale, beforeTx, beforeTy);
    return;
  }
  const center = nodeCenter(root);
  const horizontalExtent = Math.max(center.x - b.minX, b.maxX - center.x, nodeVisualWidth(root) / 2);
  const verticalExtent = Math.max(center.y - b.minY, b.maxY - center.y, nodeVisualHeight(root) / 2);
  const availableHalfW = Math.max(1, rect.width / 2 - 20);
  const availableHalfH = Math.max(1, rect.height / 2 - 20);
  const fittedScale = Math.min(availableHalfW / (horizontalExtent + 20), availableHalfH / (verticalExtent + 20), 1.22);
  // ponytail: keep the initial two-way view readable; use Fit for a full-map overview on very large maps.
  const readableFloor = reason === 'layout-toggle' ? .55 : 0;
  scale = Math.max(readableFloor, Math.min(fittedScale, 1.22));
  tx = rect.width / 2 - center.x * scale;
  ty = rect.height / 2 - center.y * scale;
  setTransform();
  traceViewportChange(reason || 'fit', beforeScale, beforeTx, beforeTy, readableFloor);
}
function resizeMindMap(reason) { requestAnimationFrame(() => resetView(reason || 'window-resize')); }
window.KardLeafMindMapResize = resizeMindMap;
window.addEventListener('resize', () => resizeMindMap('window-resize'));
function pathBetween(a, b, directionOverride) {
  const direction = directionOverride || b.direction;
  const isLeft = direction === 'left';
  const ax = isLeft ? a.x : a.x + nodeVisualWidth(a);
  const bx = isLeft ? b.x + nodeVisualWidth(b) : b.x;
  const ay = a.y + nodeVisualHeight(a) / 2;
  const by = b.y + nodeVisualHeight(b) / 2;
  const trunkX = (ax + bx) / 2;
  return 'M ' + ax + ' ' + ay + ' H ' + trunkX + ' V ' + by + ' H ' + bx;
}
function createSvgElement(name) { return document.createElementNS(SVG_NS, name); }
function descendantCount(node) {
  return node.children.reduce((count, child) => count + 1 + descendantCount(child), 0);
}
function appendNodeControl(g, node, cx, cy, label, className, edgeX) {
  const control = createSvgElement('g');
  control.setAttribute('class', 'nodeControl ' + className);
  control.dataset.key = node.key;
  const direction = cx >= edgeX ? 1 : -1;
  const link = createSvgElement('line');
  link.setAttribute('x1', String(edgeX));
  link.setAttribute('y1', String(cy));
  link.setAttribute('x2', String(cx - direction * 11));
  link.setAttribute('y2', String(cy));
  link.style.stroke = node.accent;
  link.style.strokeWidth = '2';
  control.appendChild(link);
  const circle = createSvgElement('circle');
  circle.setAttribute('cx', String(cx));
  circle.setAttribute('cy', String(cy));
  circle.setAttribute('r', '11');
  circle.style.fill = activeTheme.surfaceColor;
  circle.style.stroke = node.accent;
  control.appendChild(circle);
  const text = createSvgElement('text');
  text.setAttribute('x', String(cx));
  text.setAttribute('y', String(cy + 4));
  text.setAttribute('text-anchor', 'middle');
  text.style.fill = node.accent;
  text.textContent = label;
  control.appendChild(text);
  g.appendChild(control);
}
function renderDropIndicator() {
  const movingNode = drag && drag.mode === 'node' ? drag.node : null;
  if (!movingNode || !dropIntent) return;
  const parent = dropIntent.parent;
  if (!parent || parent === movingNode) return;
  const ghost = {
    x: dropIntent.x,
    y: dropIntent.y,
    width: nodeVisualWidth(movingNode),
    height: nodeVisualHeight(movingNode),
    direction: dropIntent.direction,
  };
  const preview = createSvgElement('path');
  preview.setAttribute('class', 'dropPreviewLink');
  preview.setAttribute('d', pathBetween(parent, ghost, dropIntent.direction));
  dropIndicator.appendChild(preview);
  const outline = createSvgElement('rect');
  outline.setAttribute('class', 'dropGhost');
  outline.setAttribute('x', String(ghost.x));
  outline.setAttribute('y', String(ghost.y));
  outline.setAttribute('width', String(ghost.width));
  outline.setAttribute('height', String(ghost.height));
  outline.setAttribute('rx', String(parent.depth === 0 ? (activeTheme.branchRadius || 16) : (activeTheme.childRadius || 16)));
  outline.setAttribute('ry', String(parent.depth === 0 ? (activeTheme.branchRadius || 16) : (activeTheme.childRadius || 16)));
  dropIndicator.appendChild(outline);
}
function renderNode(n) {
  const isLeftNode = !n.root && n.direction === 'left';
  const isSelected = selectedKey === n.key;
  const w = nodeVisualWidth(n);
  const h = nodeVisualHeight(n);
  const compact = isCompactNode(n);
  const g = createSvgElement('g');
  g.setAttribute('class', 'node' + (n.root ? ' root' : '') + (drag && drag.mode === 'node' && drag.node === n ? ' dragging' : ''));
  g.setAttribute('transform', 'translate(' + n.x + ' ' + n.y + ')');
  g.dataset.index = String(n.index);
  g.dataset.key = n.key;
  if (isSelected) {
    const halo = createSvgElement('rect');
    halo.setAttribute('class', 'halo');
    halo.setAttribute('x', '-3');
    halo.setAttribute('y', '-3');
    halo.setAttribute('width', String(w + 6));
    halo.setAttribute('height', String(h + 6));
    halo.setAttribute('rx', '4');
    halo.setAttribute('ry', '4');
    halo.style.stroke = n.accent;
    g.appendChild(halo);
  }
  const rect = createSvgElement('rect');
  rect.setAttribute('class', 'body');
  rect.setAttribute('width', String(w));
  rect.setAttribute('height', String(h));
  rect.setAttribute('rx', String(n.root ? (activeTheme.rootRadius || 20) : (n.depth === 1 ? (activeTheme.branchRadius || 16) : (activeTheme.childRadius || 16))));
  rect.setAttribute('ry', String(n.root ? (activeTheme.rootRadius || 20) : (n.depth === 1 ? (activeTheme.branchRadius || 16) : (activeTheme.childRadius || 16))));
  if (activeTheme.nodeShadow) rect.style.filter = activeTheme.nodeShadow;
  if (activeTheme.nodeBorderColor) {
    rect.style.fill = activeTheme.surfaceColor;
    rect.style.stroke = activeTheme.nodeBorderColor;
    rect.style.strokeOpacity = isSelected ? '1' : '.9';
    rect.style.strokeWidth = isSelected ? '2' : '1';
  } else if (n.root) {
    rect.style.fill = activeTheme.rootColor;
    rect.style.stroke = 'none';
  } else if (n.depth === 1) {
    rect.style.fill = n.color;
    rect.style.stroke = 'none';
  } else {
    rect.style.fill = n.childColor || activeTheme.surfaceColor;
    if (activeTheme.childBorder === false) {
      rect.style.stroke = 'none';
    } else {
      rect.style.stroke = n.accent;
      rect.style.strokeOpacity = isSelected ? '1' : '.55';
      rect.style.strokeWidth = isSelected ? '2' : '1.4';
    }
  }
  g.appendChild(rect);
  if (editingKey === n.key) {
    const editorBox = createSvgElement('foreignObject');
    editorBox.setAttribute('width', String(w));
    editorBox.setAttribute('height', String(h));
    const input = document.createElement('input');
    input.className = 'nodeEditor';
    input.value = n.text;
    input.setAttribute('aria-label', '节点名称');
    input.enterKeyHint = 'done';
    input.autocomplete = 'off';
    input.spellcheck = false;
    input.style.color = n.text === '输入文本'
      ? 'var(--muted)'
      : (n.root ? activeTheme.rootTextColor : (n.depth === 1 ? (n.branchTextColor || activeTheme.branchTextColor) : activeTheme.textColor));
    if (compact) {
      input.style.fontSize = String(activeTheme.compactNodeFontSize || 14) + 'px';
      input.style.padding = '0 ' + ((activeTheme.compactNodeHorizontalPadding || 30) / 2) + 'px';
    }
    input.addEventListener('pointerdown', event => event.stopPropagation());
    input.addEventListener('click', event => event.stopPropagation());
    input.addEventListener('keydown', event => {
      if (event.key === 'Enter') {
        event.preventDefault();
        input.blur();
      } else if (event.key === 'Escape') {
        event.preventDefault();
        finishInlineRename(false);
      }
    });
    input.addEventListener('blur', () => finishInlineRename(true));
    editorBox.appendChild(input);
    g.appendChild(editorBox);
  } else {
    const text = createSvgElement('text');
    const fontSize = n.root ? 15 : (compact ? (activeTheme.compactNodeFontSize || 14) : 14);
    const lineHeight = compact ? (activeTheme.compactNodeLineHeight || textLineHeight) : textLineHeight;
    const textX = n.root ? w / 2 : (compact ? (activeTheme.compactNodeHorizontalPadding || 30) / 2 : 15);
    const textStartY = h / 2 - ((n.lines.length - 1) * lineHeight) / 2 + (compact ? 4 : 5);
    text.setAttribute('x', String(textX));
    text.setAttribute('y', String(textStartY));
    if (n.root) text.setAttribute('text-anchor', 'middle');
    text.style.fontSize = String(fontSize) + 'px';
    text.style.fill = n.root ? activeTheme.rootTextColor : (n.depth === 1 ? (n.branchTextColor || activeTheme.branchTextColor) : activeTheme.textColor);
    n.lines.forEach((line, lineIndex) => {
      const tspan = createSvgElement('tspan');
      tspan.setAttribute('x', String(textX));
      tspan.setAttribute('dy', lineIndex === 0 ? '0' : String(lineHeight));
      tspan.textContent = line;
      text.appendChild(tspan);
    });
    g.appendChild(text);
  }
  const edgeX = isLeftNode ? 0 : w;
  const controlSide = isLeftNode ? -1 : 1;
  const cy = h / 2;
  if (n.children.length && (isSelected || collapsedKeys.has(n.key))) {
    const label = collapsedKeys.has(n.key) ? String(descendantCount(n)) : '-';
    appendNodeControl(g, n, edgeX + controlSide * 14, cy, label, 'toggle', edgeX);
  }
  nodesLayer.appendChild(g);
}
function render() {
  if (!root) return;
  linksLayer.innerHTML = '';
  dropIndicator.innerHTML = '';
  nodesLayer.innerHTML = '';
  const draggingNode = drag && drag.mode === 'node'
    ? drag.node
    : committedDrag && committedDrag.node
      ? committedDrag.node
      : null;
  visibleNodes.forEach(n => {
    if (n.root || n === draggingNode) return;
    const p = createSvgElement('path');
    p.setAttribute('class', 'link');
    p.setAttribute('d', pathBetween(n.parent || root, n));
    p.setAttribute('stroke', n.accent);
    linksLayer.appendChild(p);
  });
  renderDropIndicator();
  visibleNodes.forEach(renderNode);
}
let pendingRenderFrame = 0;
function scheduleRender() {
  if (pendingRenderFrame) return;
  pendingRenderFrame = requestAnimationFrame(() => {
    pendingRenderFrame = 0;
    render();
  });
}
function findNodeByIndex(index) { return nodes.find(n => n.index === index); }
function findNodeByKey(key) { return key ? nodes.find(n => n.key === key) : null; }
function finishInlineRename(commit, notifyFinished) {
  const api = window.KardLeafMindMap;
  if (!editingKey) {
    traceDrag('rename-finish skipped commit=' + !!commit + ' notify=' + !!notifyFinished + ' editing=false');
    if (notifyFinished && api && api.onInlineRenameFinished) api.onInlineRenameFinished();
    return false;
  }
  const node = findNodeByKey(editingKey);
  const input = nodesLayer.querySelector('input.nodeEditor');
  const title = input ? input.value.trim() : '';
  const changed = !!(commit && node && title && title !== node.text);
  traceDrag(
    'rename-finish commit=' + !!commit +
      ' notify=' + !!notifyFinished +
      ' node=' + (node ? node.index : 'none') +
      ' input=' + !!input +
      ' inputLen=' + (input ? input.value.length : -1) +
      ' titleLen=' + title.length +
      ' titleEndsWithHash=' + title.endsWith('#') +
      ' changed=' + changed +
      ' api=' + !!api,
  );
  editingKey = null;
  if (editViewport) {
    // ponytail: 900ms covers Android IME resize; use visualViewport settling if an OEM keyboard exceeds it.
    editViewport.lockUntil = Date.now() + editViewportSettleMs;
  }
  updateActionBar();
  render();
  if (changed && api && api.onNodeRename) {
    api.onNodeRename(node.index, title);
  }
  if (notifyFinished && api && api.onInlineRenameFinished) api.onInlineRenameFinished();
  return true;
}
function beginInlineRename(node) {
  if (!node || editingKey === node.key) return;
  if (editingKey) finishInlineRename(true);
  traceDrag(
    'rename-begin node=' + node.index +
      ' depth=' + node.depth +
      ' textLen=' + node.text.length +
      ' selectedBefore=' + (selectedKey ? (findNodeByKey(selectedKey) ? findNodeByKey(selectedKey).index : 'stale') : 'none') +
      ' scale=' + scale.toExponential(3),
  );
  animationVersion += 1;
  layoutMindMap();
  selectedKey = node.key;
  editingKey = node.key;
  editViewport = { scale: scale, nodeIndex: node.index, lockUntil: Number.POSITIVE_INFINITY };
  updateActionBar();
  restoreEditingViewport();
  render();
  requestAnimationFrame(() => {
    const input = nodesLayer.querySelector('input.nodeEditor');
    if (!input) return;
    try { input.focus({ preventScroll: true }); } catch (error) { input.focus(); }
    input.select();
    restoreEditingViewport();
  });
}
window.KardLeafMindMapBeginRename = index => beginInlineRename(findNodeByIndex(index));
window.KardLeafMindMapCommitInlineRename = () => finishInlineRename(true, true);
function closestByClass(target, className) {
  while (target && target !== svg) {
    if (target.classList && target.classList.contains(className)) return target;
    target = target.parentNode;
  }
  return null;
}
let lastActionBarLayoutTrace = '';
function traceActionBarLayout(reason) {
  const rect = actionBar.getBoundingClientRect();
  const style = getComputedStyle(actionBar);
  const buttons = Array.from(nodeActions.querySelectorAll('.nodeAction')).map(button => {
    const buttonRect = button.getBoundingClientRect();
    return Math.round(buttonRect.width) + 'x' + Math.round(buttonRect.height) + ':' + (button.disabled ? 'disabled' : 'enabled');
  }).join(',');
  const state = 'actionbar-layout viewport=' + Math.round(window.innerWidth) + 'x' + Math.round(window.innerHeight) +
    ' bar=' + Math.round(rect.width) + 'x' + Math.round(rect.height) +
    ' barRect=' + Math.round(rect.left) + ',' + Math.round(rect.top) + ',' + Math.round(rect.right) + ',' + Math.round(rect.bottom) +
    ' cssBottom=' + style.bottom +
    ' barClient=' + actionBar.clientWidth + 'x' + actionBar.clientHeight +
    ' barScroll=' + actionBar.scrollWidth + 'x' + actionBar.scrollHeight +
    ' nodeClient=' + nodeActions.clientWidth + 'x' + nodeActions.clientHeight +
    ' nodeScroll=' + nodeActions.scrollWidth + 'x' + nodeActions.scrollHeight +
    ' buttons=' + buttons;
  if (state !== lastActionBarLayoutTrace) {
    lastActionBarLayoutTrace = state;
    traceDrag(state + ' reason=' + reason);
  }
}
function updateActionBar(reason) {
  const node = findNodeByKey(selectedKey);
  // Legacy UI contract: keep addSibling root-enabled; runNodeAction below intentionally cross-maps the callbacks.
  nodeActions.querySelectorAll('.nodeAction').forEach(button => {
    const action = button.dataset.action;
    const enabled = !!node && (action === 'addSibling' || !node.root);
    button.disabled = !enabled;
    button.setAttribute('aria-disabled', String(!enabled));
  });
  actionBar.style.display = 'flex';
  traceActionBarLayout(reason || 'state');
}
function selectNode(node) {
  const previousNode = findNodeByKey(selectedKey);
  selectedKey = node ? node.key : null;
  traceDrag(
    'selection-change from=' + (previousNode ? previousNode.index : 'none') +
      ' to=' + (node ? node.index : 'none') +
      ' fromTextLen=' + (previousNode ? previousNode.text.length : 'none') +
      ' toTextLen=' + (node ? node.text.length : 'none'),
  );
  updateActionBar();
  render();
}
window.KardLeafMindMapSelectNode = index => {
  const node = findNodeByIndex(Number(index));
  if (!node) return;
  let parent = node.parent;
  let changed = false;
  while (parent) {
    if (collapsedKeys.delete(parent.key)) changed = true;
    parent = parent.parent;
  }
  if (changed) {
    updateCollapseAllButton();
    layoutMindMap();
  }
  selectNode(node);
  resetView('outline-select');
};
function toggleCollapse(node) {
  if (!node || !node.children.length) return;
  const previousPositions = capturePositions();
  const wasCollapsed = collapsedKeys.has(node.key);
  if (collapsedKeys.has(node.key)) collapsedKeys.delete(node.key);
  else collapsedKeys.add(node.key);
  traceDrag(
    'collapse-toggle node=' + node.index +
      ' depth=' + node.depth +
      ' before=' + wasCollapsed +
      ' after=' + collapsedKeys.has(node.key) +
      ' collapsedCount=' + collapsedKeys.size,
  );
  updateCollapseAllButton();
  animateLayoutFrom(previousPositions, 180);
}
function runNodeAction(action) {
  const node = findNodeByKey(selectedKey);
  const api = window.KardLeafMindMap;
  if (!node || !api) return;
  const input = nodesLayer.querySelector('input.nodeEditor');
  traceDrag(
    'node-action action=' + action +
      ' node=' + node.index +
      ' root=' + node.root +
      ' editingBefore=' + !!editingKey +
      ' inputLen=' + (input ? input.value.length : -1) +
      ' selected=' + node.index +
      ' ime=' + effectiveImeInset().toFixed(1),
  );
  // Keep the DOM input alive. Android applies this rename and node insertion as one
  // editor transaction; a separate rename callback redraws the tree and hides the IME.
  const pendingRenameNode = editingKey ? findNodeByKey(editingKey) : null;
  const pendingRenameIndex = pendingRenameNode && input ? pendingRenameNode.index : -1;
  const pendingRenameTitle = pendingRenameNode && input ? input.value.trim() : '';
  // Preserve the old one-refresh path: clear the state without rendering the DOM here.
  if (editingKey) editingKey = null;
  suppressTapUntil = Date.now() + 320;
  // IMPORTANT: legacy UI contract: addSibling -> onNodeAddChild, addChild -> onNodeAddSibling.
  // Do not swap them based on their names; verify the visible button behavior end to end first.
  if (action === 'addSibling') {
    if (collapsedKeys.has(node.key)) {
      collapsedKeys.delete(node.key);
      updateCollapseAllButton();
    }
    if (api.onNodeAddChild) api.onNodeAddChild(node.index, '输入文本', pendingRenameIndex, pendingRenameTitle);
    traceDrag('node-action callback=onNodeAddChild node=' + node.index + ' requestedAction=' + action);
    window.setTimeout(updateActionBar, 1200);
  } else if (node.root) {
    traceDrag('node-action rejected action=' + action + ' node=' + node.index + ' reason=root');
    return;
  } else if (action === 'addChild') {
    if (api.onNodeAddSibling) api.onNodeAddSibling(node.index, '输入文本', pendingRenameIndex, pendingRenameTitle);
    traceDrag('node-action callback=onNodeAddSibling node=' + node.index + ' requestedAction=' + action);
    window.setTimeout(updateActionBar, 1200);
  } else if (action === 'delete') {
    if (api.onNodeDelete) api.onNodeDelete(node.index);
    traceDrag('node-action callback=onNodeDelete node=' + node.index + ' requestedAction=' + action);
  }
}
actionBar.addEventListener('pointerdown', e => {
  let target = e.target;
  while (target && target !== actionBar && target.tagName !== 'BUTTON') target = target.parentNode;
  if (target && target !== actionBar) e.preventDefault();
});
actionBar.addEventListener('click', e => {
  let target = e.target;
  while (target && target !== actionBar && target.tagName !== 'BUTTON') target = target.parentNode;
  if (!target || target === actionBar) return;
  const action = target.dataset ? target.dataset.action : null;
  if (action && target.classList.contains('nodeAction')) runNodeAction(action);
});
document.addEventListener('contextmenu', e => {
  e.preventDefault();
  e.stopPropagation();
}, true);
function clearDragLongPress(item) {
  if (!item || !item.longPressTimer) return;
  clearTimeout(item.longPressTimer);
  item.longPressTimer = 0;
}
function restoreDraggedSubtree(item) {
  if (!item || !Array.isArray(item.subtreeStarts)) return;
  item.subtreeStarts.forEach(start => {
    start.node.x = start.x;
    start.node.y = start.y;
  });
}
function clearCommittedDrag(reason, restore) {
  const item = committedDrag;
  if (!item) return;
  clearTimeout(item.commitHoldTimer);
  if (restore) restoreDraggedSubtree(item);
  committedDrag = null;
  traceDrag(
    'commit-hold end reason=' + reason + ' moving=' + (item.node ? item.node.index : 'none') +
      ' restored=' + !!restore + ' elapsedMs=' +
      Math.round(performance.now() - (item.commitHoldStartedAt || performance.now())),
  );
}
function holdCommittedDragAtIntent(item, intent) {
  if (!item || !item.node || !intent || !Array.isArray(item.subtreeStarts)) return;
  const deltaX = intent.x - item.node.x;
  const deltaY = intent.y - item.node.y;
  item.subtreeStarts.forEach(start => {
    start.node.x += deltaX;
    start.node.y += deltaY;
  });
  item.commitHoldStartedAt = performance.now();
  committedDrag = item;
  item.commitHoldTimer = setTimeout(() => {
    if (committedDrag !== item) return;
    clearCommittedDrag('tree-refresh-timeout', true);
    render();
  }, commitHoldTimeoutMs);
  traceDrag(
    'commit-hold start moving=' + item.node.index + ' targetParent=' + intent.parent.index +
      ' targetChildIndex=' + intent.childIndex + ' holdX=' + Math.round(item.node.x) +
      ' holdY=' + Math.round(item.node.y) + ' subtreeNodes=' + item.subtreeStarts.length,
  );
}
function activateNodeDrag(item) {
  if (!item || item.mode !== 'press' || !item.node || item.node.root) return false;
  clearDragLongPress(item);
  item.mode = 'node';
  const world = screenToWorld(item.lastX, item.lastY);
  item.worldStartX = world.x;
  item.worldStartY = world.y;
  item.nodeStartX = item.node.x;
  item.nodeStartY = item.node.y;
  item.subtreeStarts = nodes
    .filter(node => node === item.node || isDescendantOf(node, item.node))
    .map(node => ({ node, x: node.x, y: node.y }));
  animationVersion += 1;
  const api = window.KardLeafMindMap;
  if (api && api.onLongPress) api.onLongPress();
  const parent = item.node.parent;
  const bounds = visibleSubtreeBounds(item.node);
  const oldChildIndex = parent ? parent.children.indexOf(item.node) : -1;
  lastDropResolution = 'priority=current';
  lastSiblingCandidates = 'none';
  lastChildHits = 'none';
  traceDrag(
    'drag-start trigger=long-press gesture=' + dragTraceSequence + ' moving=' + item.node.index +
      ' depth=' + item.node.depth + ' oldParent=' + (parent ? parent.index : 'none') +
      ' oldChildIndex=' + oldChildIndex + ' parentChildCount=' + (parent ? parent.children.length : 0) +
      ' subtreeNodes=' + item.subtreeStarts.length +
      ' nodeX=' + Math.round(item.node.x) + ' nodeY=' + Math.round(item.node.y) +
      ' nodeWidth=' + Math.round(nodeVisualWidth(item.node)) +
      ' nodeHeight=' + Math.round(nodeVisualHeight(item.node)) +
      ' subtreeTop=' + Math.round(bounds.top) + ' subtreeBottom=' + Math.round(bounds.bottom) +
      ' direction=' + item.node.direction + ' scale=' + scale.toFixed(3),
  );
  dropIntent = currentDropIntent(item.node);
  render();
  return true;
}
function beginPinchIfNeeded() {
  if (activePointers.size < 2) return;
  const [a, b] = currentTwoPointers();
  const center = centerOf(a, b);
  pinch = {
    startDistance: Math.max(24, distance(a, b)),
    startScale: scale,
    worldCenter: screenToWorld(center.x, center.y),
  };
  clearDragLongPress(drag);
  if (drag && drag.mode === 'node' && drag.node) {
    restoreDraggedSubtree(drag);
    traceDrag('pinch-cancel gesture=' + dragTraceSequence + ' node=' + drag.node.index + ' moved=' + drag.moved + ' nodeRestored=' + !!drag.moved);
  }
  drag = null;
  dropIntent = null;
  suppressTapUntil = Date.now() + 260;
  showZoomToast();
  render();
}
function updatePinch() {
  if (!pinch || activePointers.size < 2) return;
  const [a, b] = currentTwoPointers();
  const center = centerOf(a, b);
  const beforeScale = scale;
  const beforeTx = tx;
  const beforeTy = ty;
  const rawScale = pinch.startScale * distance(a, b) / pinch.startDistance;
  scale = Math.min(2.35, Math.max(Number.MIN_VALUE, rawScale));
  if (!Number.isFinite(scale) || scale <= .1) {
    traceDrag(
      'pinch-scale-risk raw=' + (Number.isFinite(rawScale) ? rawScale.toExponential(3) : String(rawScale)) +
        ' applied=' + (Number.isFinite(scale) ? scale.toExponential(3) : String(scale)) +
        ' min=' + Number.MIN_VALUE + ' max=2.35',
    );
  }
  tx = center.x - pinch.worldCenter.x * scale;
  ty = center.y - pinch.worldCenter.y * scale;
  setTransform();
  traceViewportChange('pinch', beforeScale, beforeTx, beforeTy);
  showZoomToast();
}
svg.addEventListener('pointerdown', (e) => {
  if (closestByClass(e.target, 'nodeEditor')) return;
  if (committedDrag) {
    e.preventDefault();
    traceDrag(
      'down ignored reason=commit-held pointer=' + e.pointerId +
        ' moving=' + (committedDrag.node ? committedDrag.node.index : 'none'),
    );
    return;
  }
  stopPanFling();
  const tappedNodeGroup = closestByClass(e.target, 'node');
  const tappedNode = tappedNodeGroup ? findNodeByKey(tappedNodeGroup.dataset.key) : null;
  if (editingKey) {
    finishInlineRename(true);
    if (tappedNode) {
      selectNode(tappedNode);
      e.preventDefault();
      return;
    }
  }
  editViewport = null;
  e.preventDefault();
  dragTraceSequence += 1;
  try {
    svg.setPointerCapture(e.pointerId);
  } catch (error) {
    traceDrag('pointer-capture-error pointer=' + e.pointerId + ' message=' + String(error));
  }
  activePointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (activePointers.size >= 2) {
    traceDrag('down pointer=' + e.pointerId + ' active=' + activePointers.size + ' mode=pinch');
    beginPinchIfNeeded();
    return;
  }
  dropIntent = null;
  const toggleEl = closestByClass(e.target, 'toggle');
  if (toggleEl) {
    traceDrag('down pointer=' + e.pointerId + ' active=1 mode=collapse-toggle');
    activePointers.delete(e.pointerId);
    suppressTapUntil = Date.now() + 320;
    toggleCollapse(findNodeByKey(toggleEl.dataset.key));
    return;
  }
  const nodeGroup = closestByClass(e.target, 'node');
  const key = nodeGroup ? nodeGroup.dataset.key : null;
  const node = key ? findNodeByKey(key) : null;
  traceDrag(
    'down pointer=' + e.pointerId + ' active=1 node=' + (node ? node.index : 'none') +
      ' root=' + (node ? node.root : 'false') + ' x=' + Math.round(e.clientX) +
      ' y=' + Math.round(e.clientY) + ' scale=' + Math.round(scale * 100) +
      ' nodes=' + nodes.length,
  );
  drag = {
    id: e.pointerId,
    startX: e.clientX,
    startY: e.clientY,
    startedAt: Date.now(),
    lastX: e.clientX,
    lastY: e.clientY,
    lastMoveAt: performance.now(),
    velocityX: 0,
    velocityY: 0,
    moved: false,
    mode: node && !node.root ? 'press' : 'pan',
    node,
    nodeStartX: node ? node.x : 0,
    nodeStartY: node ? node.y : 0,
    worldStartX: 0,
    worldStartY: 0,
    startTx: tx,
    startTy: ty,
    longPressTimer: 0,
    subtreeStarts: null,
    commitSent: false,
  };
  if (drag.mode === 'press') {
    const pressed = drag;
    pressed.longPressTimer = setTimeout(() => {
      if (drag !== pressed || pressed.moved || activePointers.size !== 1) return;
      activateNodeDrag(pressed);
    }, longPressDelayMs);
  }
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
  if (drag.mode === 'press' && activePointers.size === 1 && Date.now() - drag.startedAt >= longPressDelayMs) {
    activateNodeDrag(drag);
  }
  if (Math.hypot(dx, dy) > longPressMoveSlopPx) {
    if (!drag.moved) {
      traceDrag(
        'threshold pointer=' + e.pointerId + ' node=' + (drag.node ? drag.node.index : 'none') +
          ' mode=' + drag.mode + ' dx=' + Math.round(dx) + ' dy=' + Math.round(dy),
      );
    }
    drag.moved = true;
    if (drag.mode === 'press') {
      clearDragLongPress(drag);
      drag.mode = 'pan';
    }
  }
  if (drag.mode === 'press') {
    drag.lastX = e.clientX;
    drag.lastY = e.clientY;
    drag.lastMoveAt = performance.now();
    return;
  }
  if (drag.mode === 'node' && drag.node && !drag.node.root) {
    if (!drag.moved) {
      drag.lastX = e.clientX;
      drag.lastY = e.clientY;
      scheduleRender();
      return;
    }
    const world = screenToWorld(e.clientX, e.clientY);
    const worldDx = world.x - drag.worldStartX;
    const worldDy = world.y - drag.worldStartY;
    drag.subtreeStarts.forEach(start => {
      start.node.x = start.x + worldDx;
      start.node.y = start.y + worldDy;
    });
    const previousIntent = dropIntent;
    const resolvedIntent = resolveDropIntent(drag.node, true);
    if (dropIntentKey(resolvedIntent) !== dropIntentKey(previousIntent)) {
      dropIntent = resolvedIntent;
      const oldParent = drag.node.parent;
      const oldIndex = oldParent ? oldParent.children.indexOf(drag.node) : -1;
      traceDrag(
        'intent-change moving=' + drag.node.index +
          ' source=' + (dropIntent ? dropIntent.source : 'none') +
          ' oldParent=' + (oldParent ? oldParent.index : 'none') +
          ' oldIndex=' + oldIndex +
          ' targetParent=' + (dropIntent ? dropIntent.parent.index : 'none') +
          ' targetChildIndex=' + (dropIntent ? dropIntent.childIndex : -1) +
          ' previewX=' + (dropIntent ? Math.round(dropIntent.x) : 'none') +
          ' previewY=' + (dropIntent ? Math.round(dropIntent.y) : 'none') +
          ' centerX=' + Math.round(nodeCenter(drag.node).x) + ' centerY=' + Math.round(nodeCenter(drag.node).y) +
          ' deltaX=' + Math.round(drag.node.x - drag.nodeStartX) +
          ' deltaY=' + Math.round(drag.node.y - drag.nodeStartY) +
          ' pointerScreenX=' + Math.round(e.clientX) + ' pointerScreenY=' + Math.round(e.clientY) +
          ' pointerWorldX=' + Math.round(world.x) + ' pointerWorldY=' + Math.round(world.y) +
          ' scale=' + scale.toFixed(3) + ' resolver={' + lastDropResolution + '}',
      );
    }
    scheduleRender();
  } else if (drag.moved) {
    const now = performance.now();
    const elapsed = Math.max(1, now - drag.lastMoveAt);
    const stepX = e.clientX - drag.lastX;
    const stepY = e.clientY - drag.lastY;
    drag.velocityX = clamp(
      drag.velocityX * .65 + (stepX / elapsed * panHorizontalSensitivity) * .35,
      -panFlingMaxVelocity,
      panFlingMaxVelocity,
    );
    drag.velocityY = clamp(
      drag.velocityY * .65 + (stepY / elapsed * panVerticalSensitivity) * .35,
      -panFlingMaxVelocity,
      panFlingMaxVelocity,
    );
    tx = drag.startTx + dx * panHorizontalSensitivity;
    ty = drag.startTy + dy * panVerticalSensitivity;
    drag.lastMoveAt = now;
    scheduleTransform();
  }
  drag.lastX = e.clientX;
  drag.lastY = e.clientY;
});
function finishPointer(e) {
  if (activePointers.has(e.pointerId)) activePointers.delete(e.pointerId);
  clearDragLongPress(drag);
  if (e.type === 'pointercancel') {
    const cancelled = drag;
    restoreDraggedSubtree(cancelled);
    traceDrag(
      'finish type=pointercancel pointer=' + e.pointerId + ' result=cancelled moving=' +
        (cancelled && cancelled.node ? cancelled.node.index : 'none') +
        ' moved=' + !!(cancelled && cancelled.moved) + ' activeBeforeClear=' + activePointers.size,
    );
    activePointers.clear();
    pinch = null;
    drag = null;
    dropIntent = null;
    suppressTapUntil = Date.now() + 260;
    render();
    return;
  }
  if (pinch) {
    traceDrag('finish type=' + e.type + ' pointer=' + e.pointerId + ' result=pinch-cancel');
    activePointers.clear();
    pinch = null;
    drag = null;
    dropIntent = null;
    suppressTapUntil = Date.now() + 260;
    showZoomToast();
    render();
    return;
  }
  if (!drag || drag.id !== e.pointerId) {
    traceDrag('finish type=' + e.type + ' pointer=' + e.pointerId + ' result=no-active-drag');
    return;
  }
  const item = drag;
  const finalWorld = screenToWorld(e.clientX, e.clientY);
  const intent = item.mode === 'node' && item.moved && item.node && !item.node.root
    ? dropIntent
    : null;
  const releaseOldParent = item.node ? item.node.parent : null;
  const releaseOldIndex = releaseOldParent && item.node ? releaseOldParent.children.indexOf(item.node) : -1;
  const releaseRejection = !item.node
    ? 'moving-missing'
    : pendingTreeData
      ? 'pending-tree-refresh'
      : moveIntentRejectionReason(item.node, intent);
  const releaseAllowed = !releaseRejection;
  traceDrag(
    'finish-evaluate type=' + e.type + ' gesture=' + dragTraceSequence + ' moving=' +
      (item.node ? item.node.index : 'none') + ' finalWorldX=' + Math.round(finalWorld.x) +
      ' finalWorldY=' + Math.round(finalWorld.y) +
      ' source=' + (intent ? intent.source : 'none') +
      ' oldParent=' + (releaseOldParent ? releaseOldParent.index : 'none') +
      ' oldIndex=' + releaseOldIndex +
      ' targetParent=' + (intent ? intent.parent.index : 'none') +
      ' targetChildIndex=' + (intent ? intent.childIndex : -1) +
      ' previewX=' + (intent ? Math.round(intent.x) : 'none') +
      ' previewY=' + (intent ? Math.round(intent.y) : 'none') +
      ' parentChanged=' + !!(intent && releaseOldParent !== intent.parent) +
      ' pendingTreeRefresh=' + !!pendingTreeData +
      ' releaseAllowed=' + releaseAllowed +
      ' rejection=' + (releaseRejection || 'none') +
      ' intentSource=preview activePointers=' + activePointers.size +
      ' resolver={' + lastDropResolution + '}',
  );
  if (releaseAllowed) {
    holdCommittedDragAtIntent(item, intent);
  } else {
    restoreDraggedSubtree(item);
  }
  drag = null;
  dropIntent = null;
  if (item.mode === 'node' && item.moved && item.node && !item.node.root) {
    if (releaseAllowed && !item.commitSent) {
      item.commitSent = true;
      const movingIndex = item.node.index;
      const targetParentIndex = intent.parent.index;
      const targetChildIndex = intent.childIndex;
      collapsedKeys.delete(intent.parent.key);
      updateCollapseAllButton();
      render();
      traceDrag(
        'move-request gesture=' + dragTraceSequence + ' moving=' + movingIndex +
          ' targetParent=' + targetParentIndex + ' targetChildIndex=' + targetChildIndex +
          ' source=' + intent.source,
      );
      if (window.KardLeafMindMap && window.KardLeafMindMap.onNodeMove) {
        window.KardLeafMindMap.onNodeMove(
          movingIndex,
          targetParentIndex,
          targetChildIndex,
          dragTraceSequence,
        );
      }
      suppressTapUntil = Date.now() + 320;
      return;
    }
    traceDrag(
      'finish type=' + e.type + ' gesture=' + dragTraceSequence + ' moving=' + item.node.index +
        ' source=' + (intent ? intent.source : 'none') +
        ' releaseAllowed=' + releaseAllowed + ' rejection=' + (releaseRejection || 'none') +
        ' result=' + (intent ? 'move-rejected' : 'no-drop-intent') +
        ' resolver={' + lastDropResolution + '}',
    );
    render();
    return;
  }
  if (item.mode === 'pan' && item.moved) {
    startPanFling(item.velocityX, item.velocityY);
  }
  if (!item.moved && item.mode !== 'node') {
    if (item.node && !item.node.root) {
      item.node.x = item.nodeStartX;
      item.node.y = item.nodeStartY;
    }
    traceDrag(
      'finish type=' + e.type + ' node=' + (item.node ? item.node.index : 'none') + ' result=tap',
    );
    if (Date.now() <= suppressTapUntil) {
      render();
      return;
    }
    if (item.node) {
      if (selectedKey === item.node.key) {
        suppressTapUntil = Date.now() + 320;
        beginInlineRename(item.node);
      } else {
        selectNode(item.node);
        return;
      }
    } else {
      if (selectedKey) selectNode(null);
      const now = Date.now();
      if (now - lastBlankTapTime < 300) {
        lastBlankTapTime = 0;
        resetView('blank-double-tap');
        return;
      }
      lastBlankTapTime = now;
    }
  }
  render();
}
svg.addEventListener('pointerup', (e) => { finishPointer(e); flushPendingTreeData(); });
svg.addEventListener('pointercancel', (e) => { finishPointer(e); flushPendingTreeData(); });
undoBtn.onclick = () => {
  if (editingKey) finishInlineRename(true);
  if (window.KardLeafMindMap && window.KardLeafMindMap.onUndo) window.KardLeafMindMap.onUndo();
};
redoBtn.onclick = () => {
  if (editingKey) finishInlineRename(true);
  if (window.KardLeafMindMap && window.KardLeafMindMap.onRedo) window.KardLeafMindMap.onRedo();
};
document.getElementById('exportImage').onclick = () => {
  const api = window.KardLeafMindMap;
  if (api && api.onExportImage) api.onExportImage();
};
document.getElementById('fit').onclick = () => resetView('fit-button');
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
  animateLayoutFrom(previousPositions, 200, () => resetView('collapse-toggle'));
};
layoutToggle.onclick = toggleLayoutMode;
updateThemeMenu();
updateLayoutButton();
updateCollapseAllButton();
traceActionBarLayout('script-ready');
traceDrag('scale-config min=' + Number.MIN_VALUE + ' max=2.35');
</script>
</body>
</html>
""".trimIndent()
}
