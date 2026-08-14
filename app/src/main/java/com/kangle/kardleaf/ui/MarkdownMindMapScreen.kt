package com.kangle.kardleaf.ui

import android.app.Activity
import android.annotation.SuppressLint
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
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    onOpenSource: () -> Unit,
    onMindMapNodeClick: (MindMapNode) -> Unit,
    onNodeReparent: (movingIndex: Int, parentIndex: Int, gestureSequence: Int) -> Unit = { _, _, _ -> },
    onNodeAddChild: (parentIndex: Int, title: String) -> Unit = { _, _ -> },
    onNodeAddSibling: (anchorIndex: Int, title: String) -> Unit = { _, _ -> },
    onNodeRename: (nodeIndex: Int, title: String) -> Unit = { _, _ -> },
    onNodeMove: (nodeIndex: Int, moveUp: Boolean) -> Unit = { _, _ -> },
    onNodeDelete: (nodeIndex: Int) -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    canUndo: Boolean = false,
    canRedo: Boolean = false,
) {
    fun modelIndex(webIndex: Int): Int = if (webIndex < 0) 0 else webIndex

    val configuration = LocalConfiguration.current
    val activity = LocalContext.current as? Activity
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun dismissMindMap(returnToHome: Boolean = false) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (returnToHome) onBackToHome() else onDismiss()
    }

    fun openSource() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onOpenSource()
    }

    BackHandler { dismissMindMap(returnToHome = true) }
    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { dismissMindMap(returnToHome = true) }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "关闭思维导图")
                        }
                        Text(
                            text = displayTitle.ifBlank { document?.root?.text ?: "思维导图" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = ::openSource) {
                            Text("原文")
                        }
                        TextButton(
                            onClick = {
                                val currentOrientation = activity?.resources?.configuration?.orientation
                                    ?: configuration.orientation
                                activity?.requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                            },
                        ) {
                            Text(if (isLandscape) "竖屏" else "横屏")
                        }
                    }
                }
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
                            onMindMapNodeClick = { node ->
                                dismissMindMap()
                                onMindMapNodeClick(node)
                            },
                            onNodeReparent = { movingIndex, parentIndex, gestureSequence ->
                                onNodeReparent(modelIndex(movingIndex), modelIndex(parentIndex), gestureSequence)
                            },
                            onUndo = onUndo,
                            onRedo = onRedo,
                            canUndo = canUndo,
                            canRedo = canRedo,
                            orientation = configuration.orientation,
                            initialEditNodeIndex = initialEditNodeIndex,
                            onInitialEditConsumed = onInitialEditConsumed,
                            onNodeAddChild = { parentIndex, title ->
                                onNodeAddChild(modelIndex(parentIndex), title)
                            },
                            onNodeAddSibling = { anchorIndex, title ->
                                onNodeAddSibling(modelIndex(anchorIndex), title)
                            },
                            onNodeRename = { nodeIndex, title -> onNodeRename(modelIndex(nodeIndex), title) },
                            onNodeMove = { nodeIndex, moveUp -> onNodeMove(modelIndex(nodeIndex), moveUp) },
                            onNodeDelete = { nodeIndex -> onNodeDelete(modelIndex(nodeIndex)) },
                            modifier = Modifier.fillMaxSize(),
                        )
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
    onMindMapNodeClick: (MindMapNode) -> Unit,
    onNodeReparent: (movingIndex: Int, parentIndex: Int, gestureSequence: Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    orientation: Int,
    initialEditNodeIndex: Int?,
    onInitialEditConsumed: () -> Unit,
    onNodeAddChild: (parentIndex: Int, title: String) -> Unit,
    onNodeAddSibling: (anchorIndex: Int, title: String) -> Unit,
    onNodeRename: (nodeIndex: Int, title: String) -> Unit,
    onNodeMove: (nodeIndex: Int, moveUp: Boolean) -> Unit,
    onNodeDelete: (nodeIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnMindMapNodeClick = rememberUpdatedState(onMindMapNodeClick)
    val currentOnNodeReparent = rememberUpdatedState(onNodeReparent)
    val currentOnUndo = rememberUpdatedState(onUndo)
    val currentOnRedo = rememberUpdatedState(onRedo)
    val currentOnNodeAddChild = rememberUpdatedState(onNodeAddChild)
    val currentOnNodeAddSibling = rememberUpdatedState(onNodeAddSibling)
    val currentOnNodeRename = rememberUpdatedState(onNodeRename)
    val currentOnNodeMove = rememberUpdatedState(onNodeMove)
    val currentOnNodeDelete = rememberUpdatedState(onNodeDelete)
    val currentOnInitialEditConsumed = rememberUpdatedState(onInitialEditConsumed)
    val currentDocument = rememberUpdatedState(document)
    val html = rememberMindMapHtml(isDark)
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
                ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                    val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    val imeBottom = if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                        (imeInsets.bottom - navigationInsets.bottom).coerceAtLeast(0)
                    } else {
                        0
                    }
                    val density = resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
                    state.imeBottomCssPx = (imeBottom / density).roundToInt()
                    if (state.pageReady) {
                        (view as? WebView)?.evaluateJavascript(
                            "window.KardLeafMindMapSetImeBottom && window.KardLeafMindMapSetImeBottom(${state.imeBottomCssPx});",
                            null,
                        )
                    }
                    insets
                }
                ViewCompat.requestApplyInsets(this)
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
                        state.imeBottomCssPx = nextImeBottomCssPx
                        if (state.pageReady) {
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
                            "viewport page-ready orientation=${state.appliedOrientation} url=${url ?: "none"}",
                        )
                        applyPendingMindMapUpdate(view, state) {
                            currentOnInitialEditConsumed.value()
                        }
                        view.evaluateJavascript(
                            "window.KardLeafMindMapSetImeBottom && window.KardLeafMindMapSetImeBottom(${state.imeBottomCssPx});",
                            null,
                        )
                    }
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onNodeClick(index: Int) {
                            state.postIfActive {
                                val modelIndex = if (index < 0) 0 else index
                                currentDocument.value.nodes.getOrNull(modelIndex)?.let { currentOnMindMapNodeClick.value(it) }
                            }
                        }

                        @JavascriptInterface
                        fun onNodeReparent(movingIndex: Int, parentIndex: Int, gestureSequence: Int) {
                            KardLeafLog.d(
                                MIND_MAP_GESTURE_TRACE_TAG,
                                "bridge reparent gesture=$gestureSequence movingIndex=$movingIndex parentIndex=$parentIndex " +
                                    "pageReady=${state.pageReady} released=${state.released} nodes=${currentDocument.value.nodes.size}",
                            )
                            state.postIfActive("reparent") {
                                KardLeafLog.d(
                                    MIND_MAP_GESTURE_TRACE_TAG,
                                    "bridge reparent dispatched gesture=$gestureSequence movingIndex=$movingIndex parentIndex=$parentIndex " +
                                        "nodes=${currentDocument.value.nodes.size}",
                                )
                                currentOnNodeReparent.value(movingIndex, parentIndex, gestureSequence)
                            }
                        }

                        @JavascriptInterface
                        fun onDragTrace(message: String) {
                            KardLeafLog.d(MIND_MAP_GESTURE_TRACE_TAG, message)
                        }

                        @JavascriptInterface
                        fun onLongPress() {
                            state.postIfActive("long-press-haptic") {
                                val performed = state.webView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) == true
                                KardLeafLog.d(MIND_MAP_GESTURE_TRACE_TAG, "long-press-haptic performed=$performed")
                            }
                        }

                        @JavascriptInterface
                        fun onUndo() {
                            state.postIfActive {
                                currentOnUndo.value()
                            }
                        }

                        @JavascriptInterface
                        fun onRedo() {
                            state.postIfActive {
                                currentOnRedo.value()
                            }
                        }

                        @JavascriptInterface
                        fun onExportImage() {
                            state.postIfActive {
                                state.webView?.let(::exportMindMapImage)
                            }
                        }

                        @JavascriptInterface
                        fun onNodeAddChild(parentIndex: Int, title: String) {
                            state.postIfActive {
                                currentOnNodeAddChild.value(parentIndex, title)
                            }
                        }

                        @JavascriptInterface
                        fun onNodeAddSibling(anchorIndex: Int, title: String) {
                            state.postIfActive {
                                currentOnNodeAddSibling.value(anchorIndex, title)
                            }
                        }

                        @JavascriptInterface
                        fun onNodeRename(index: Int, title: String) {
                            state.postIfActive {
                                currentOnNodeRename.value(index, title)
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
                if (initialEditNodeIndex == null) {
                    state.pendingInitialEditIndex = null
                    state.dispatchedInitialEditIndex = null
                } else if (state.dispatchedInitialEditIndex != initialEditNodeIndex) {
                    state.pendingInitialEditIndex = initialEditNodeIndex
                }
                state.pendingSignature = signature
                state.pendingScript = updateScript
                if (state.themeIsDark != isDark) {
                    state.themeIsDark = isDark
                    state.pageReady = false
                    state.appliedSignature = null
                    state.appliedOrientation = orientation
                    KardLeafLog.d(
                        MIND_MAP_GESTURE_TRACE_TAG,
                        "viewport page-reload reason=theme orientation-baseline=$orientation",
                    )
                    webView.loadDataWithBaseURL(
                    MIND_MAP_BASE_URL,
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
                } else if (state.pageReady) {
                    applyPendingMindMapUpdate(webView, state) {
                        currentOnInitialEditConsumed.value()
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
            state?.released = true
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
        KardLeafLog.d(
            MIND_MAP_GESTURE_TRACE_TAG,
            "tree-update skip signatureHash=${signature.hashCode()} pageReady=${state.pageReady}",
        )
        return
    }
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
    KardLeafLog.d(
        MIND_MAP_GESTURE_TRACE_TAG,
        "tree-update dispatch signatureHash=${signature.hashCode()} scriptLen=${dispatchedScript.length} " +
            "initialEdit=${initialEditIndex ?: "none"} pageReady=${state.pageReady}",
    )
    webView.evaluateJavascript(dispatchedScript, null)
    if (initialEditIndex != null) {
        onInitialEditConsumed()
        webView.post {
            if (!state.released) webView.requestFocus(View.FOCUS_DOWN)
        }
    }
}

private fun exportMindMapImage(webView: WebView) {
    val context = webView.context
    val restoreScript = "window.KardLeafMindMapRestoreExport && window.KardLeafMindMapRestoreExport();"
    val capture = {
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
): String = androidx.compose.runtime.remember(isDark) {
    buildMindMapHtml(isDark)
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
  --muted: ${if (isDark) "#aab7c4" else "#9aa5af"};
  --primary: ${if (isDark) "#8ec8ff" else "#2f80ed"};
  --primary2: ${if (isDark) "#223b55" else "#dceeff"};
  --float: ${if (isDark) "rgba(24,33,42,.94)" else "rgba(255,255,255,.94)"};
}
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; -webkit-user-select: none; user-select: none; -webkit-touch-callout: none; }
html, body { width: 100%; height: 100%; margin: 0; overflow: hidden; background: var(--bg); font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: var(--text); }
#toolbar { position: fixed; right: 12px; top: 8px; z-index: 4; display: flex; align-items: center; gap: 4px; padding: 5px; border-radius: 16px; background: var(--float); box-shadow: 0 8px 28px rgba(20, 80, 140, .16); backdrop-filter: blur(12px); }
.toolBtn { min-width: 30px; height: 30px; padding: 0 6px; border: 0; border-radius: 10px; background: var(--primary2); color: var(--primary); font-size: 17px; font-weight: 750; }
.toolBtn.layoutBtn { min-width: 42px; font-size: 12px; letter-spacing: .2px; }
.toolBtn.iconBtn { width: 34px; min-width: 34px; padding: 4px; background: transparent; color: #050505; }
.toolBtn.iconBtn:active { background: var(--primary2); }
.toolBtn.iconBtn.layoutBtn { width: 38px; min-width: 38px; padding: 3px; }
.mindMapIcon { width: 28px; height: 24px; display: block; fill: none; stroke: currentColor; stroke-width: 3.2; stroke-linecap: round; stroke-linejoin: round; }
.mindMapIcon circle { vector-effect: non-scaling-stroke; }
.layoutGlyph, .collapseGlyph { display: none; }
.layoutBtn[data-mode="side"] .layoutGlyph-twoWay,
.layoutBtn[data-mode="full"] .layoutGlyph-oneWay,
.collapseBtn[data-action="collapse"] .collapseGlyph-collapse,
.collapseBtn[data-action="expand"] .collapseGlyph-expand { display: block; }
.toolBtn.historyBtn { width: 34px; min-width: 34px; padding: 0; border-radius: 10px; background: transparent; color: var(--muted); font-size: 20px; font-weight: 500; line-height: 1; }
.historyIcon { width: 24px; height: 24px; display: block; fill: currentColor; }
.toolBtn:disabled { opacity: .38; }
.toolBtn.historyBtn:not(:disabled) { color: var(--text); }
.toolBtn.historyBtn:not(:disabled):active { background: var(--primary2); color: var(--primary); }
#themeControl { position: relative; }
.themeBtn { width: 34px; min-width: 34px; padding: 0; background: transparent; }
.themeSwatch { display: inline-grid; grid-template-columns: repeat(3, 6px); gap: 2px; vertical-align: middle; }
.themeSwatch i { width: 6px; height: 15px; display: block; border-radius: 3px; }
#themeOptions { position: absolute; top: 38px; right: 0; display: none; min-width: 126px; padding: 5px; border: 1px solid rgba(100, 130, 160, .25); border-radius: 12px; background: var(--float); box-shadow: 0 8px 26px rgba(20, 80, 140, .2); }
#themeOptions.open { display: grid; gap: 2px; }
.themeOption { display: flex; align-items: center; gap: 8px; width: 100%; padding: 7px 8px; border: 0; border-radius: 8px; background: transparent; color: var(--text); font-size: 12px; text-align: left; white-space: nowrap; }
.themeOption:active, .themeOption.active { background: var(--primary2); color: var(--primary); }
.themeSwatch i:nth-child(1) { background: #4263eb; }
.themeSwatch i:nth-child(2) { background: #0ca678; }
.themeSwatch i:nth-child(3) { background: #f76707; }
.themeOption[data-theme="xmind"] .themeSwatch i:nth-child(1) { background: #e65244; }
.themeOption[data-theme="xmind"] .themeSwatch i:nth-child(2) { background: #eaa45e; }
.themeOption[data-theme="xmind"] .themeSwatch i:nth-child(3) { background: #4f68f6; }
#actionBar { position: fixed; left: 50%; transform: translateX(-50%); bottom: calc(8px + var(--ime-bottom, 0px)); z-index: 6; display: none; align-items: center; gap: 1px; max-width: calc(100vw - 8px); overflow-x: auto; padding: 4px; border-radius: 16px; background: var(--float); box-shadow: 0 10px 32px rgba(15, 48, 82, .24); backdrop-filter: blur(14px); }
#actionBar button { flex: 0 0 auto; height: 36px; padding: 0 8px; border: 0; border-radius: 10px; background: transparent; color: var(--text); font-size: 12px; font-weight: 650; white-space: nowrap; }
#actionBar button:active { background: var(--primary2); color: var(--primary); }
#actionBar .danger { color: ${if (isDark) "#ff9a9a" else "#c62828"}; }
#zoomToast { position: fixed; left: 50%; bottom: calc(62px + var(--ime-bottom, 0px)); z-index: 7; display: none; transform: translateX(-50%); padding: 6px 10px; border-radius: 10px; background: rgba(0, 0, 0, .52); color: #ffffff; font-size: 12px; font-weight: 700; line-height: 1; pointer-events: none; }
#zoomToast.visible { display: block; }
#stage { width: 100%; height: 100%; touch-action: none; }
svg { width: 100%; height: 100%; display: block; }
.link { fill: none; stroke-linecap: round; }
.node rect.body { filter: drop-shadow(0 8px 16px rgba(31, 91, 156, .13)); }
.node { cursor: grab; }
.node.dragging { opacity: .85; }
.node text { font-size: 14px; font-weight: 650; pointer-events: none; }
.node.root text { font-size: 15px; font-weight: 700; }
.nodeEditor { width: 100%; height: 100%; padding: 0 14px; border: 2px solid var(--primary); border-radius: 16px; outline: none; background: transparent; font: 650 14px system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; text-align: left; -webkit-user-select: text; user-select: text; }
.node.root .nodeEditor { border-radius: 20px; font-size: 15px; font-weight: 700; text-align: center; }
.halo { fill: none; stroke-width: 2.2; opacity: .95; }
.nodeControl { cursor: pointer; }
.nodeControl circle { stroke-width: 2; }
.nodeControl text { font-size: 12px; font-weight: 750; pointer-events: none; }
</style>
</head>
<body>
<div id="toolbar">
  <button class="toolBtn iconBtn layoutBtn" id="layoutToggle" aria-label="切换为双向布局" title="当前为单向布局，点击切换为双向布局" data-mode="side">
    <svg class="mindMapIcon layoutGlyph layoutGlyph-oneWay" viewBox="0 0 56 32" aria-hidden="true"><g><circle cx="8" cy="16" r="3.5" fill="currentColor"/><path d="M12 16H25L35 8M25 16L35 24"/><circle cx="40" cy="8" r="3.5" fill="currentColor"/><circle cx="40" cy="24" r="3.5" fill="currentColor"/></g></svg>
    <svg class="mindMapIcon layoutGlyph layoutGlyph-twoWay" viewBox="0 0 56 32" aria-hidden="true"><g><circle cx="28" cy="16" r="4" fill="currentColor"/><path d="M24 14L14 8M24 18L14 24M32 14L42 8M32 18L42 24"/><circle cx="10" cy="8" r="3.5" fill="currentColor"/><circle cx="10" cy="24" r="3.5" fill="currentColor"/><circle cx="46" cy="8" r="3.5" fill="currentColor"/><circle cx="46" cy="24" r="3.5" fill="currentColor"/></g></svg>
  </button>
  <button class="toolBtn iconBtn collapseBtn" id="collapseAll" aria-label="折叠全部" title="折叠全部节点" data-action="collapse">
    <svg class="mindMapIcon collapseGlyph collapseGlyph-collapse" viewBox="0 0 28 32" aria-hidden="true"><path d="M9 7L19 16L9 25"/></svg>
    <svg class="mindMapIcon collapseGlyph collapseGlyph-expand" viewBox="0 0 28 32" aria-hidden="true"><path d="M19 7L9 16L19 25"/></svg>
  </button>
  <button class="toolBtn historyBtn" id="undo" aria-label="撤销" title="撤销"><svg class="historyIcon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12.5 8c-2.65 0-5.05 .99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z" /></svg></button>
  <button class="toolBtn historyBtn" id="redo" aria-label="恢复" title="恢复"><svg class="historyIcon" viewBox="0 0 24 24" aria-hidden="true"><path d="M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73 .72 5.12 1.88L13 16h9V7l-3.6 3.6z" /></svg></button>
  <div id="themeControl">
    <button class="toolBtn themeBtn" id="themeMenu" aria-label="节点主题" title="节点主题">
      <span class="themeSwatch" aria-hidden="true"><i></i><i></i><i></i></span>
    </button>
    <div id="themeOptions" aria-label="节点主题选项">
      <button class="themeOption" data-theme="classic"><span class="themeSwatch"><i></i><i></i><i></i></span><span>经典彩色</span></button>
      <button class="themeOption" data-theme="xmind"><span class="themeSwatch"><i></i><i></i><i></i></span><span>XMind</span></button>
    </div>
  </div>
  <button class="toolBtn" id="fit" aria-label="适配视图" title="适配全图">⌂</button>
  <button class="toolBtn layoutBtn" id="exportImage" aria-label="导出图片" title="导出思维导图图片">导出</button>
</div>
<div id="zoomToast" role="status" aria-live="polite"></div>
<div id="actionBar"></div>
<div id="stage"><svg id="svg"><g id="viewport"><g id="links"></g><g id="nodes"></g></g></svg></div>
<script>
const isDark = $dark;
const svg = document.getElementById('svg');
const viewport = document.getElementById('viewport');
const linksLayer = document.getElementById('links');
const nodesLayer = document.getElementById('nodes');
const toolbar = document.getElementById('toolbar');
const actionBar = document.getElementById('actionBar');
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
let nativeImeInset = 0;
function updateImeInset() {
  const visual = window.visualViewport;
  const visualHeight = visual ? visual.height : window.innerHeight;
  const visualOffsetTop = visual ? visual.offsetTop : 0;
  const visualInset = Math.max(0, window.innerHeight - visualHeight - visualOffsetTop);
  const inset = Math.max(nativeImeInset, visualInset);
  document.documentElement.style.setProperty('--ime-bottom', inset + 'px');
  requestAnimationFrame(() => {
    if (editingKey) restoreEditingViewport();
  });
}
window.KardLeafMindMapSetImeBottom = value => {
  nativeImeInset = Math.max(0, Number(value) || 0);
  updateImeInset();
};
updateImeInset();
window.addEventListener('resize', updateImeInset);
if (window.visualViewport) {
  window.visualViewport.addEventListener('resize', updateImeInset);
  window.visualViewport.addEventListener('scroll', updateImeInset);
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
    root.accent = activeTheme.rootColor;
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
}
themeMenu.onclick = event => {
  event.stopPropagation();
  themeOptions.classList.toggle('open');
};
themeOptions.addEventListener('click', event => {
  let option = event.target;
  while (option && option !== themeOptions && option.tagName !== 'BUTTON') option = option.parentNode;
  if (option && option !== themeOptions) applyNodeTheme(option.dataset.theme);
});
document.addEventListener('pointerdown', event => {
  if (!themeControl.contains(event.target)) themeOptions.classList.remove('open');
});
window.KardLeafMindMapPrepareExport = function() {
  toolbar.style.visibility = 'hidden';
  actionBar.style.visibility = 'hidden';
};
window.KardLeafMindMapRestoreExport = function() {
  toolbar.style.visibility = '';
  actionBar.style.visibility = '';
};
const MIND_MAP_THEMES = {
  classic: {
    label: '经典彩色',
    rootColor: isDark ? '#8ec8ff' : '#2f80ed',
    rootTextColor: isDark ? '#10233a' : '#ffffff',
    surfaceColor: isDark ? '#18212a' : '#ffffff',
    textColor: isDark ? '#e5edf5' : '#17212b',
    branchTextColor: '#ffffff',
    fills: ['#4263eb', '#0ca678', '#f76707', '#ae3ec9', '#f08c00', '#1c7ed6', '#d6336c', '#37b24d'],
    accents: isDark
      ? ['#91a7ff', '#63e6be', '#ffa94d', '#e599f7', '#ffd43b', '#74c0fc', '#faa2c1', '#8ce99a']
      : ['#4263eb', '#0ca678', '#f76707', '#ae3ec9', '#f08c00', '#1c7ed6', '#d6336c', '#37b24d'],
  },
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
    orthogonalLinks: true,
    linkWidth: 3,
    linkOpacity: 1,
    linkLineJoin: 'round',
    rootLinkAnchors: true,
    compactDescendants: true,
    compactNodeMinWidth: 84,
    compactNodeMaxWidth: 164,
    compactNodeMinHeight: 36,
    compactNodeFontSize: 12,
    compactNodeLineHeight: 16,
    compactNodeHorizontalPadding: 18,
    compactNodeVerticalPadding: 7,
  },
};
let activeThemeKey = 'classic';
let activeTheme = MIND_MAP_THEMES[activeThemeKey];
const minNodeWidth = 112;
const maxNodeWidth = 236;
const minRootWidth = 150;
const maxRootWidth = 252;
const minNodeHeight = 46;
const textLineHeight = 18;
const textVerticalPadding = 14;
const horizontalGap = 28;
const siblingGap = 14;
const panVerticalSensitivity = 1.14;
const dropExitSlopPx = 18;
const longPressDelayMs = 520;
const longPressMoveSlopPx = 9;
const editViewportSettleMs = 900;
const textMeasureCanvas = document.createElement('canvas');
const textMeasureContext = textMeasureCanvas.getContext('2d');
let rootData = null;
let nodesData = [];
let root = null;
let nodes = [];
let visibleNodes = [];
let hasMindMapData = false;
let localTreeDirty = false;
let pendingTreeData = null;
let layoutMode = 'side';
let tx = 0, ty = 0, scale = 1;
let drag = null;
let pinch = null;
let dropTarget = null;
let animationVersion = 0;
let suppressTapUntil = 0;
let lastBlankTapTime = 0;
let selectedKey = null;
let editingKey = null;
let editViewport = null;
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
  root = { key: 'root', index: -1, depth: 0, text: rootData.text, line: rootData.line, sourceOffset: rootData.sourceOffset, x: 0, y: 0, parent: null, children: [], root: true, direction: 'right', color: activeTheme.rootColor, accent: activeTheme.rootColor };
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
  if (drag || pinch || activePointers.size) {
    traceDrag('interaction-reset reason=' + reason);
  }
  clearDragLongPress(drag);
  if (drag && drag.mode === 'node' && drag.node && Number.isFinite(drag.nodeStartX) && Number.isFinite(drag.nodeStartY)) {
    drag.node.x = drag.nodeStartX;
    drag.node.y = drag.nodeStartY;
  }
  drag = null;
  pinch = null;
  dropTarget = null;
  activePointers.clear();
  animationVersion += 1;
}
function applyMindMapData(nextRoot, nextNodes) {
  if (sameMindMapData(nextRoot, nextNodes) && !localTreeDirty) return;
  if (drag || pinch || activePointers.size) {
    traceDrag('tree-refresh deferred while gesture active activePointers=' + activePointers.size + ' oldNodes=' + nodes.length + ' newNodes=' + (Array.isArray(nextNodes) ? nextNodes.length : -1));
    pendingTreeData = { root: nextRoot, nodes: Array.isArray(nextNodes) ? nextNodes.slice() : null };
    return;
  }
  pendingTreeData = null;
  traceDrag('tree-refresh dirty=' + localTreeDirty + ' oldNodes=' + nodes.length + ' newNodes=' + (Array.isArray(nextNodes) ? nextNodes.length : -1));
  cancelActivePointerInteraction('tree-refresh');
  const previousPositions = hasMindMapData ? capturePositions() : new Map();
  buildTree(nextRoot, nextNodes);
  localTreeDirty = false;
  const validKeys = new Set(nodes.map(n => n.key));
  Array.from(collapsedKeys).forEach(key => { if (!validKeys.has(key)) collapsedKeys.delete(key); });
  if (selectedKey && !validKeys.has(selectedKey)) selectedKey = null;
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
function updateSubtreeDepth(node, depth) {
  node.depth = depth;
  node.children.forEach(child => updateSubtreeDepth(child, depth + 1));
}
function canReparent(movingNode, targetNode) {
  if (!movingNode || movingNode.root || !targetNode) return false;
  if (movingNode === targetNode || isDescendantOf(targetNode, movingNode)) return false;
  if (targetNode === movingNode.parent) return false;
  return true;
}
function dropGeometry(movingNode, targetNode, pointerWorld, extraScreenPx) {
  const mw = nodeVisualWidth(movingNode);
  const mh = nodeVisualHeight(movingNode);
  const extraWorld = (extraScreenPx || 0) / Math.max(scale, .01);
  const marginX = Math.max(42, mw * .28) + extraWorld;
  const marginY = Math.max(34, mh * .45) + extraWorld;
  const w = nodeVisualWidth(targetNode);
  const h = nodeVisualHeight(targetNode);
  const bodyLeft = targetNode.x;
  const bodyTop = targetNode.y;
  const bodyRight = bodyLeft + w;
  const bodyBottom = bodyTop + h;
  const movingRight = movingNode.x + mw;
  const movingBottom = movingNode.y + mh;
  const overlapWidth = Math.max(0, Math.min(movingRight, bodyRight) - Math.max(movingNode.x, bodyLeft));
  const overlapHeight = Math.max(0, Math.min(movingBottom, bodyBottom) - Math.max(movingNode.y, bodyTop));
  const bodyOverlapArea = overlapWidth * overlapHeight;
  const movingCenter = nodeCenter(movingNode);
  const pointerBodyHit = !!pointerWorld &&
    pointerWorld.x >= bodyLeft && pointerWorld.x <= bodyRight &&
    pointerWorld.y >= bodyTop && pointerWorld.y <= bodyBottom;
  const centerBodyHit = movingCenter.x >= bodyLeft && movingCenter.x <= bodyRight &&
    movingCenter.y >= bodyTop && movingCenter.y <= bodyBottom;
  const pointerHit = !!pointerWorld &&
    pointerWorld.x >= targetNode.x - marginX && pointerWorld.x <= targetNode.x + w + marginX &&
    pointerWorld.y >= targetNode.y - marginY && pointerWorld.y <= targetNode.y + h + marginY;
  const centerHit = movingCenter.x >= targetNode.x - marginX && movingCenter.x <= targetNode.x + w + marginX &&
    movingCenter.y >= targetNode.y - marginY && movingCenter.y <= targetNode.y + h + marginY;
  const overlap =
    movingNode.x < targetNode.x + w + marginX && movingNode.x + mw > targetNode.x - marginX &&
    movingNode.y < targetNode.y + h + marginY && movingNode.y + mh > targetNode.y - marginY;
  return {
    pointerBodyHit,
    centerBodyHit,
    bodyOverlapArea,
    pointerHit,
    centerHit,
    overlap,
    hit: pointerHit || centerHit || overlap,
    marginX,
    marginY,
  };
}
function findDropTargetFor(movingNode, pointerWorld) {
  if (!movingNode || movingNode.root) return null;
  const points = pointerWorld ? [pointerWorld, nodeCenter(movingNode)] : [nodeCenter(movingNode)];
  let best = null;
  let bestRank = Infinity;
  let bestOverlapArea = -1;
  let bestScore = Infinity;
  visibleNodes.forEach(n => {
    if (!canReparent(movingNode, n)) return;
    const geometry = dropGeometry(movingNode, n, pointerWorld, 0);
    if (!geometry.hit) return;
    const nc = nodeCenter(n);
    const score = Math.min.apply(null, points.map(point => {
      if (point.x < n.x - geometry.marginX || point.x > n.x + nodeVisualWidth(n) + geometry.marginX ||
        point.y < n.y - geometry.marginY || point.y > n.y + nodeVisualHeight(n) + geometry.marginY) return Infinity;
      return Math.abs(point.x - nc.x) + Math.abs(point.y - nc.y);
    }));
    const score2 = geometry.overlap
      ? Math.min(score, Math.abs(nodeCenter(movingNode).x - nc.x) + Math.abs(nodeCenter(movingNode).y - nc.y))
      : score;
    const rank = geometry.centerBodyHit
      ? 0
      : geometry.pointerBodyHit
        ? 1
        : geometry.bodyOverlapArea > 0
          ? 2
          : 3;
    if (Number.isFinite(score2) && (
      rank < bestRank ||
      (rank === bestRank && geometry.bodyOverlapArea > bestOverlapArea) ||
      (rank === bestRank && geometry.bodyOverlapArea === bestOverlapArea && score2 < bestScore)
    )) {
      best = n;
      bestRank = rank;
      bestOverlapArea = geometry.bodyOverlapArea;
      bestScore = score2;
    }
  });
  return best;
}
function resolveDropTarget(movingNode, pointerWorld, currentTarget) {
  if (!movingNode || movingNode.root) return null;
  const preferredTarget = findDropTargetFor(movingNode, pointerWorld);
  if (preferredTarget) return preferredTarget;
  if (currentTarget && canReparent(movingNode, currentTarget)) {
    const retained = dropGeometry(movingNode, currentTarget, pointerWorld, dropExitSlopPx);
    if (retained.hit) return currentTarget;
    traceDrag(
      'target retention-failed moving=' + movingNode.index + ' target=' + currentTarget.index +
        ' pointerWorldX=' + Math.round(pointerWorld.x) + ' pointerWorldY=' + Math.round(pointerWorld.y) +
        ' centerX=' + Math.round(nodeCenter(movingNode).x) + ' centerY=' + Math.round(nodeCenter(movingNode).y) +
        ' marginX=' + Math.round(retained.marginX) + ' marginY=' + Math.round(retained.marginY),
    );
  }
  return findDropTargetFor(movingNode, pointerWorld);
}
function rejectedTargetDetails(movingNode, pointerWorld) {
  if (!movingNode || movingNode.root) return null;
  const pointer = pointerWorld || null;
  const parts = [];
  visibleNodes.forEach(n => {
    if (canReparent(movingNode, n)) return;
    const geometry = dropGeometry(movingNode, n, pointer, 0);
    if (!geometry.hit) return;
    let reason;
    if (n === movingNode) reason = 'self';
    else if (isDescendantOf(n, movingNode)) reason = 'descendant';
    else if (n === movingNode.parent) reason = 'current-parent';
    else reason = 'invalid';
    parts.push(n.index + ':' + reason);
  });
  return parts.length ? parts.join(' ') : null;
}
function reparentNodeLocally(movingNode, targetNode) {
  if (!canReparent(movingNode, targetNode)) return false;
  const previousPositions = capturePositions();
  collapsedKeys.delete(targetNode.key);
  const oldParent = movingNode.parent;
  const oldParentIndex = oldParent ? oldParent.index : -1;
  const oldSiblings = oldParent.children;
  const oldIndex = oldSiblings.indexOf(movingNode);
  if (oldIndex >= 0) oldSiblings.splice(oldIndex, 1);
  targetNode.children.push(movingNode);
  movingNode.parent = targetNode;
  updateSubtreeDepth(movingNode, targetNode.depth + 1);
  localTreeDirty = true;
  traceDrag(
    'reparent-local gesture=' + dragTraceSequence + ' moving=' + movingNode.index + ' target=' + targetNode.index +
      ' oldParent=' + oldParentIndex + ' oldSibling=' + oldIndex + ' newParent=' + targetNode.index +
      ' dirty=' + localTreeDirty,
  );
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
function traceViewportChange(reason, beforeScale, beforeTx, beforeTy, readableFloor) {
  const rect = svg.getBoundingClientRect();
  const b = mapBounds();
  traceDrag(
    'viewport-change reason=' + reason +
      ' scaleBefore=' + beforeScale.toFixed(3) + ' scaleAfter=' + scale.toFixed(3) +
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
function linkAnchorY(parent, child) {
  const centerY = parent.y + nodeVisualHeight(parent) / 2;
  if (!activeTheme.rootLinkAnchors || !parent.root || child.depth !== 1) return centerY;
  const siblings = parent.children.filter(item => item.direction === child.direction);
  const childIndex = siblings.indexOf(child);
  if (childIndex < 0 || siblings.length < 2) return centerY;
  return parent.y + nodeVisualHeight(parent) * (childIndex + 1) / (siblings.length + 1);
}
function pathBetween(a, b) {
  const isLeft = b.direction === 'left';
  const ax = isLeft ? a.x : a.x + nodeVisualWidth(a);
  const bx = isLeft ? b.x + nodeVisualWidth(b) : b.x;
  const ay = linkAnchorY(a, b);
  const by = b.y + nodeVisualHeight(b) / 2;
  if (activeTheme.orthogonalLinks && b.depth > 1) {
    const trunkX = (ax + bx) / 2;
    return 'M ' + ax + ' ' + ay + ' H ' + trunkX + ' V ' + by + ' H ' + bx;
  }
  const bend = Math.max(18, Math.abs(bx - ax) * .45);
  const c1x = isLeft ? ax - bend : ax + bend;
  const c2x = isLeft ? bx + bend : bx - bend;
  return 'M ' + ax + ' ' + ay + ' C ' + c1x + ' ' + ay + ', ' + c2x + ' ' + by + ', ' + bx + ' ' + by;
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
function renderNode(n) {
  const isLeftNode = !n.root && n.direction === 'left';
  const isSelected = selectedKey === n.key;
  const isDropTarget = dropTarget === n;
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
  rect.setAttribute('rx', String(n.root ? (activeTheme.rootRadius || 20) : (n.depth === 1 ? (activeTheme.branchRadius || 16) : (activeTheme.childRadius || 16))));
  rect.setAttribute('ry', String(n.root ? (activeTheme.rootRadius || 20) : (n.depth === 1 ? (activeTheme.branchRadius || 16) : (activeTheme.childRadius || 16))));
  if (activeTheme.nodeShadow) rect.style.filter = activeTheme.nodeShadow;
  if (n.root) {
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
  if (isDropTarget) {
    rect.style.stroke = n.accent;
    rect.style.strokeOpacity = '1';
    rect.style.strokeWidth = '3';
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
  nodesLayer.innerHTML = '';
  visibleNodes.forEach(n => {
    if (n.root) return;
    const p = createSvgElement('path');
    p.setAttribute('class', 'link');
    p.setAttribute('d', pathBetween(n.parent || root, n));
    p.setAttribute('stroke', n.accent);
    p.setAttribute('stroke-width', String(activeTheme.linkWidth || (n.depth <= 1 ? 3 : 2)));
    p.setAttribute('stroke-opacity', String(activeTheme.linkOpacity !== undefined ? activeTheme.linkOpacity : (isDark ? .82 : .75)));
    if (activeTheme.linkLineJoin) p.style.strokeLinejoin = activeTheme.linkLineJoin;
    linksLayer.appendChild(p);
  });
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
function finishInlineRename(commit) {
  if (!editingKey) return;
  const node = findNodeByKey(editingKey);
  const input = nodesLayer.querySelector('input.nodeEditor');
  const title = input ? input.value.trim() : '';
  editingKey = null;
  if (editViewport) {
    // ponytail: 900ms covers Android IME resize; use visualViewport settling if an OEM keyboard exceeds it.
    editViewport.lockUntil = Date.now() + editViewportSettleMs;
  }
  updateActionBar();
  render();
  const api = window.KardLeafMindMap;
  if (commit && node && title && title !== node.text && api && api.onNodeRename) {
    api.onNodeRename(node.index, title);
  }
}
function beginInlineRename(node) {
  if (!node || editingKey === node.key) return;
  if (editingKey) finishInlineRename(true);
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
function closestByClass(target, className) {
  while (target && target !== svg) {
    if (target.classList && target.classList.contains(className)) return target;
    target = target.parentNode;
  }
  return null;
}
function updateActionBar() {
  const node = findNodeByKey(selectedKey);
  if (!node || editingKey) {
    actionBar.style.display = 'none';
    actionBar.innerHTML = '';
    return;
  }
  let html = '<button data-action="addChild">＋子节点</button>';
  html += '<button data-action="rename">重命名</button>';
  if (!node.root) {
    html += '<button data-action="addSibling">＋同级</button>';
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
  if (!node || !node.children.length) return;
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
    actionBar.style.display = 'none';
    if (api.onNodeAddChild) api.onNodeAddChild(node.index, '输入文本');
    window.setTimeout(updateActionBar, 1200);
  } else if (action === 'rename') {
    beginInlineRename(node);
  } else if (node.root) {
    return;
  } else if (action === 'addSibling') {
    actionBar.style.display = 'none';
    if (api.onNodeAddSibling) api.onNodeAddSibling(node.index, '输入文本');
    window.setTimeout(updateActionBar, 1200);
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
function clearDragLongPress(item) {
  if (!item || !item.longPressTimer) return;
  clearTimeout(item.longPressTimer);
  item.longPressTimer = 0;
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
    if (drag.moved && Number.isFinite(drag.nodeStartX) && Number.isFinite(drag.nodeStartY)) {
      drag.node.x = drag.nodeStartX;
      drag.node.y = drag.nodeStartY;
    }
    traceDrag('pinch-cancel gesture=' + dragTraceSequence + ' node=' + drag.node.index + ' moved=' + drag.moved + ' nodeRestored=' + !!drag.moved);
  }
  drag = null;
  dropTarget = null;
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
  scale = Math.min(2.35, Math.max(Number.MIN_VALUE, pinch.startScale * distance(a, b) / pinch.startDistance));
  tx = center.x - pinch.worldCenter.x * scale;
  ty = center.y - pinch.worldCenter.y * scale;
  setTransform();
  traceViewportChange('pinch', beforeScale, beforeTx, beforeTy);
  showZoomToast();
}
svg.addEventListener('pointerdown', (e) => {
  if (closestByClass(e.target, 'nodeEditor')) return;
  if (editingKey) {
    finishInlineRename(true);
    return;
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
  dropTarget = null;
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
      ' dirty=' + localTreeDirty + ' nodes=' + nodes.length,
  );
  drag = {
    id: e.pointerId,
    startX: e.clientX,
    startY: e.clientY,
    startedAt: Date.now(),
    lastX: e.clientX,
    lastY: e.clientY,
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
  };
  if (drag.mode === 'press') {
    const pressed = drag;
    pressed.longPressTimer = setTimeout(() => {
      if (drag !== pressed || pressed.moved || activePointers.size !== 1) return;
      pressed.longPressTimer = 0;
      pressed.mode = 'node';
      const world = screenToWorld(pressed.lastX, pressed.lastY);
      pressed.worldStartX = world.x;
      pressed.worldStartY = world.y;
      pressed.nodeStartX = pressed.node.x;
      pressed.nodeStartY = pressed.node.y;
      animationVersion += 1;
      const api = window.KardLeafMindMap;
      if (api && api.onLongPress) api.onLongPress();
      traceDrag('long-press gesture=' + dragTraceSequence + ' node=' + pressed.node.index);
      render();
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
    drag.node.x = drag.nodeStartX + (world.x - drag.worldStartX);
    drag.node.y = drag.nodeStartY + (world.y - drag.worldStartY);
    const previousTarget = dropTarget;
    const resolvedTarget = resolveDropTarget(drag.node, world, previousTarget);
    if (resolvedTarget !== previousTarget) {
      dropTarget = resolvedTarget;
      const geometry = dropTarget
        ? dropGeometry(drag.node, dropTarget, world, 0)
        : (previousTarget ? dropGeometry(drag.node, previousTarget, world, dropExitSlopPx) : null);
      traceDrag(
        'target moving=' + drag.node.index + ' target=' + (dropTarget ? dropTarget.index : 'none') +
        ' centerX=' + Math.round(nodeCenter(drag.node).x) + ' centerY=' + Math.round(nodeCenter(drag.node).y) +
          ' pointerScreenX=' + Math.round(e.clientX) + ' pointerScreenY=' + Math.round(e.clientY) +
          ' pointerWorldX=' + Math.round(world.x) + ' pointerWorldY=' + Math.round(world.y) +
          ' scale=' + scale.toFixed(3) +
          ' pointerBodyHit=' + (geometry ? geometry.pointerBodyHit : false) +
          ' centerBodyHit=' + (geometry ? geometry.centerBodyHit : false) +
          ' overlapArea=' + Math.round(geometry ? geometry.bodyOverlapArea : 0) +
          ' pointerHit=' + (geometry ? geometry.pointerHit : false) +
          ' centerHit=' + (geometry ? geometry.centerHit : false) +
          ' overlap=' + (geometry ? geometry.overlap : false) +
          ' dirty=' + localTreeDirty,
      );
    }
    scheduleRender();
  } else if (drag.moved) {
    tx = drag.startTx + dx;
    ty = drag.startTy + dy * panVerticalSensitivity;
    setTransform();
  }
  drag.lastX = e.clientX;
  drag.lastY = e.clientY;
});
function finishPointer(e) {
  if (activePointers.has(e.pointerId)) activePointers.delete(e.pointerId);
  clearDragLongPress(drag);
  if (e.type === 'pointercancel') {
    const cancelled = drag;
    if (cancelled && cancelled.mode === 'node' && cancelled.node && !cancelled.node.root) {
      cancelled.node.x = cancelled.nodeStartX;
      cancelled.node.y = cancelled.nodeStartY;
    }
    traceDrag(
      'finish type=pointercancel pointer=' + e.pointerId + ' result=cancelled moving=' +
        (cancelled && cancelled.node ? cancelled.node.index : 'none') +
        ' moved=' + !!(cancelled && cancelled.moved) + ' activeBeforeClear=' + activePointers.size,
    );
    activePointers.clear();
    pinch = null;
    drag = null;
    dropTarget = null;
    suppressTapUntil = Date.now() + 260;
    render();
    return;
  }
  if (pinch) {
    traceDrag('finish type=' + e.type + ' pointer=' + e.pointerId + ' result=pinch-cancel');
    activePointers.clear();
    pinch = null;
    drag = null;
    dropTarget = null;
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
  const cachedTarget = dropTarget;
  const target = item.mode === 'node' && item.moved && item.node && !item.node.root
    ? resolveDropTarget(item.node, finalWorld, cachedTarget)
    : null;
  const targetSource = target
    ? (target === cachedTarget ? 'active' : 'release')
    : 'none';
  traceDrag(
    'finish-evaluate type=' + e.type + ' gesture=' + dragTraceSequence + ' moving=' +
      (item.node ? item.node.index : 'none') + ' finalWorldX=' + Math.round(finalWorld.x) +
      ' finalWorldY=' + Math.round(finalWorld.y) + ' cachedTarget=' + (cachedTarget ? cachedTarget.index : 'none') +
      ' selectedTarget=' + (target ? target.index : 'none') + ' targetSource=' + targetSource +
      ' activePointers=' + activePointers.size + ' dirty=' + localTreeDirty,
  );
  drag = null;
  dropTarget = null;
  if (item.mode === 'node' && item.moved && item.node && !item.node.root) {
    if (target && reparentNodeLocally(item.node, target)) {
      const movingIndex = item.node.index;
      const parentIndex = target.index;
      traceDrag(
        'finish type=' + e.type + ' gesture=' + dragTraceSequence + ' moving=' + movingIndex +
          ' target=' + parentIndex + ' targetSource=' + targetSource + ' result=reparent-commit',
      );
      if (window.KardLeafMindMap && window.KardLeafMindMap.onNodeReparent) {
        window.KardLeafMindMap.onNodeReparent(movingIndex, parentIndex, dragTraceSequence);
      }
      suppressTapUntil = Date.now() + 320;
      return;
    }
    traceDrag(
      'finish type=' + e.type + ' gesture=' + dragTraceSequence + ' moving=' + item.node.index + ' target=' +
        (target ? target.index : 'none') + ' targetSource=' + targetSource + ' result=' +
        (target ? 'reparent-rejected' : 'no-drop-target') +
        (target ? '' : ' rejected=' + (rejectedTargetDetails(item.node, finalWorld) || 'none')),
    );
    item.node.x = item.nodeStartX;
    item.node.y = item.nodeStartY;
    render();
    return;
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
  if (window.KardLeafMindMap && window.KardLeafMindMap.onUndo) window.KardLeafMindMap.onUndo();
};
redoBtn.onclick = () => {
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
</script>
</body>
</html>
""".trimIndent()
}
