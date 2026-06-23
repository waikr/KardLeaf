package com.kangle.kardleaf.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kangle.kardleaf.R
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

private val toggleTaskRegex = Regex("- \\[[ xX]\\]")
private data class PreviewRenderState(
    val contentLength: Int,
    val contentHash: Int,
    val isDark: Boolean,
)

/** Toggle a markdown checkbox at [index] to [checked] state. */
fun toggleTask(
    markdown: String,
    index: Int,
    checked: Boolean,
): String {
    var matchIndex = 0
    return toggleTaskRegex.replace(markdown) { matchResult ->
        if (matchIndex++ == index) {
            if (checked) "- [x]" else "- [ ]"
        } else {
            matchResult.value
        }
    }
}

/**
 * WebView-based rendered Markdown preview.
 *
 * Two details are intentional here:
 * 1. The parent Compose side-panel gesture is kept on narrow edge handles only, so
 *    normal vertical WebView scrolling is not intercepted by the editor shell.
 * 2. The tag stores only a compact render state instead of the full Markdown string,
 *    avoiding an extra long-string copy and an O(n) Pair equality check on every update.
 */
@Composable
fun PreviewWebView(
    content: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    headingScrollText: String = "",
    headingScrollLevel: Int = 0,
    headingScrollToken: Int = 0,
    onDoubleTap: () -> Unit = {},
    onUserInteraction: () -> Unit = {},
    onScrollRatioChanged: (Float) -> Unit = {},
    onCheckboxToggled: (Int, Boolean) -> Unit,
    doubleTapIntervalMs: Int = 260,
) {
    val contentRef = remember { AtomicReference(content) }
    contentRef.set(content)
    val currentOnDoubleTap = rememberUpdatedState(onDoubleTap)
    val currentOnUserInteraction = rememberUpdatedState(onUserInteraction)
    val currentOnScrollRatioChanged = rememberUpdatedState(onScrollRatioChanged)
    val currentOnCheckboxToggled = rememberUpdatedState(onCheckboxToggled)
    val currentDoubleTapIntervalMs = rememberUpdatedState(doubleTapIntervalMs.coerceIn(120, 600))
    val currentSearchQuery = rememberUpdatedState(searchQuery)
    val currentHeadingScrollText = rememberUpdatedState(headingScrollText)
    val currentHeadingScrollLevel = rememberUpdatedState(headingScrollLevel)
    val currentHeadingScrollToken = rememberUpdatedState(headingScrollToken)

    fun WebView.applyPreviewSearch() {
        evaluateJavascript("applySearchHighlight(${JSONObject.quote(currentSearchQuery.value)})", null)
    }

    fun WebView.applyPreviewHeadingScroll() {
        if (currentHeadingScrollToken.value > 0 && currentHeadingScrollText.value.isNotBlank()) {
            evaluateJavascript(
                "scrollToHeading(${JSONObject.quote(currentHeadingScrollText.value)}, ${currentHeadingScrollLevel.value})",
                null,
            )
        }
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
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                isNestedScrollingEnabled = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                isFocusable = true
                isFocusableInTouchMode = true

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.loadsImagesAutomatically = true
                settings.defaultTextEncodingName = "utf-8"
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.textZoom = 100
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false

                val previewWebView = this
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun getMarkdown(): String = contentRef.get()

                        @JavascriptInterface
                        fun onCheckboxToggled(
                            index: Int,
                            checked: Boolean,
                        ) {
                            Handler(Looper.getMainLooper()).post {
                                (previewWebView.getTag(R.id.preview_control_touch_tag) as? AtomicReference<Long>)
                                    ?.set(SystemClock.elapsedRealtime())
                                currentOnCheckboxToggled.value(index, checked)
                            }
                        }

                        @JavascriptInterface
                        fun onPreviewControlTouched() {
                            Handler(Looper.getMainLooper()).post {
                                (previewWebView.getTag(R.id.preview_control_touch_tag) as? AtomicReference<Long>)
                                    ?.set(SystemClock.elapsedRealtime())
                            }
                        }

                        @JavascriptInterface
                        fun copyCodeBlock(text: String) {
                            Handler(Looper.getMainLooper()).post {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("KardLeaf 代码块", text))
                            }
                        }
                    },
                    "Android",
                )

                val lastTapUpMs = AtomicReference(0L)
                val lastPreviewControlTouchMs = AtomicReference(0L)
                setTag(R.id.preview_control_touch_tag, lastPreviewControlTouchMs)

                setOnScrollChangeListener { view, _, scrollY, _, _ ->
                    val webView = view as? WebView ?: return@setOnScrollChangeListener
                    val maxScrollY = (webView.contentHeight * webView.scale - webView.height).coerceAtLeast(1f)
                    currentOnScrollRatioChanged.value((scrollY / maxScrollY).coerceIn(0f, 1f))
                }

                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            currentOnUserInteraction.value()
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            false
                        }
                        MotionEvent.ACTION_UP -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            val now = SystemClock.elapsedRealtime()
                            val controlTouchedRecently = now - lastPreviewControlTouchMs.get() < currentDoubleTapIntervalMs.value + 220L
                            val previousTap = lastTapUpMs.get()
                            val isDoubleTap = previousTap > 0L && now - previousTap <= currentDoubleTapIntervalMs.value
                            if (isDoubleTap && !controlTouchedRecently) {
                                lastTapUpMs.set(0L)
                                currentOnDoubleTap.value()
                                true
                            } else {
                                lastTapUpMs.set(if (controlTouchedRecently) 0L else now)
                                false
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            false
                        }
                        else -> false
                    }
                }

                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            val webView = view ?: return
                            webView.evaluateJavascript("updateContentFromAndroid($isDark)") {
                                webView.applyPreviewSearch()
                                webView.applyPreviewHeadingScroll()
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                return true
                            }
                            return false
                        }
                    }

                loadUrl("file:///android_asset/preview/preview.html?dark=$isDark")
            }
        },
        update = { view ->
            val previewState = PreviewRenderState(
                contentLength = content.length,
                contentHash = content.hashCode(),
                isDark = isDark,
            )
            val lastSearchQuery = view.getTag(R.id.preview_search_query_tag) as? String
            val lastHeadingToken = view.getTag(R.id.preview_heading_scroll_token_tag) as? Int ?: 0
            if (view.tag != previewState) {
                view.tag = previewState
                contentRef.set(content)
                view.post {
                    view.evaluateJavascript("updateContentFromAndroid($isDark)") {
                        view.setTag(R.id.preview_search_query_tag, currentSearchQuery.value)
                        view.applyPreviewSearch()
                        view.setTag(R.id.preview_heading_scroll_token_tag, currentHeadingScrollToken.value)
                        view.applyPreviewHeadingScroll()
                    }
                }
            } else {
                if (lastSearchQuery != currentSearchQuery.value) {
                    view.setTag(R.id.preview_search_query_tag, currentSearchQuery.value)
                    view.post { view.applyPreviewSearch() }
                }
                if (lastHeadingToken != currentHeadingScrollToken.value) {
                    view.setTag(R.id.preview_heading_scroll_token_tag, currentHeadingScrollToken.value)
                    view.post { view.applyPreviewHeadingScroll() }
                }
            }
        },
    )
}
