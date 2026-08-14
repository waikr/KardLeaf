package com.kangle.kardleaf.ui.editor

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.editor.api.EditorFastScrollMetrics
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale

private const val PREVIEW_CHUNK_CHARS = 300
private const val USER_PERF_TRACE_TAG = "KardLeafUserPerf"

internal enum class EditorViewportEdge {
    START,
    CENTER,
    END,
}

internal data class EditorViewportAnchor(
    val offset: Int,
    val viewportFraction: Float,
    val edge: EditorViewportEdge,
)

internal fun EditorViewportAnchor.shifted(
    delta: Int,
    targetLength: Int,
): EditorViewportAnchor =
    copy(
        offset = when (edge) {
            EditorViewportEdge.START -> 0
            EditorViewportEdge.CENTER -> (offset + delta).coerceIn(0, targetLength)
            EditorViewportEdge.END -> targetLength
        },
        viewportFraction = viewportFraction.coerceIn(0f, 1f),
    )

internal fun codeMirrorCrLfCount(content: String): Int {
    var count = 0
    var index = 0
    while (index + 1 < content.length) {
        if (content[index] == '\r' && content[index + 1] == '\n') count++
        index++
    }
    return count
}

internal fun codeMirrorNormalizedLength(content: String): Int =
    content.length - codeMirrorCrLfCount(content)

internal fun EditorViewportAnchor.toCodeMirrorAnchor(content: String): EditorViewportAnchor {
    val normalizedLength = codeMirrorNormalizedLength(content)
    val rawOffset = when (edge) {
        EditorViewportEdge.START -> 0
        EditorViewportEdge.CENTER -> offset.coerceIn(0, content.length)
        EditorViewportEdge.END -> content.length
    }
    var normalizedOffset = 0
    var index = 0
    while (index < rawOffset) {
        if (content[index] == '\r' && content.getOrNull(index + 1) == '\n') {
            index += 2
        } else {
            index++
        }
        normalizedOffset++
    }
    return copy(offset = normalizedOffset.coerceIn(0, normalizedLength))
}

internal fun parseEditorViewportAnchor(raw: String?): EditorViewportAnchor? =
    runCatching {
        val json = JSONObject(raw ?: return null)
        EditorViewportAnchor(
            offset = json.getInt("offset").coerceAtLeast(0),
            viewportFraction = json.optDouble("viewportFraction", 0.5).toFloat().coerceIn(0f, 1f),
            edge = EditorViewportEdge.valueOf(json.optString("edge", "center").uppercase(Locale.ROOT)),
        )
    }.getOrNull()

internal fun EditorViewportAnchor.toJson(): String =
    JSONObject()
        .put("offset", offset.coerceAtLeast(0))
        .put("viewportFraction", viewportFraction.coerceIn(0f, 1f))
        .put("edge", edge.name.lowercase(Locale.ROOT))
        .toString()

internal fun editorMemorySummary(): String {
    val runtime = Runtime.getRuntime()
    val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
    val totalMb = runtime.totalMemory() / 1024 / 1024
    val maxMb = runtime.maxMemory() / 1024 / 1024
    return "mem=${usedMb}MB/${totalMb}MB max=${maxMb}MB"
}

internal fun userPerfNoteSizeTier(length: Int): String = when {
    length < 10_000 -> "lt_1w"
    length < 50_000 -> "1w_5w"
    length < 100_000 -> "5w_10w"
    length < 1_000_000 -> "10w_100w"
    else -> "gte_100w"
}

internal fun Iterable<PrefsManager.EditorTopToolbarItemId>.toEditorTopBarLogText(): String =
    joinToString(prefix = "[", postfix = "]") { it.name }

@Composable
internal fun NoteSearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(if (focused) MaterialTheme.colorScheme.primary else Color.Transparent),
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
            .padding(horizontal = 12.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
internal fun NoteSearchChip(
    text: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val backgroundColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
        )
    }
}


@Composable
internal fun LargePlainTextPreview(
    title: String,
    content: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    searchCurrentStart: Int = -1,
    searchCurrentEnd: Int = -1,
    onUserInteraction: () -> Unit = {},
    onFastScrollSourceScrolled: () -> Unit = {},
    onFirstContentLaidOut: () -> Unit = {},
    contentTextSizeSp: Float = 16f,
    contentLineHeightMultiplier: Float = 1.55f,
    contentLetterSpacingSp: Float = 0f,
    contentParagraphSpacingDp: Float = 8f,
    contentFontFamily: String = "system",
) {
    val chunkCount = largePlainTextPreviewChunkCount(content.length)
    val searchHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    var scrollPerfInitialized by remember(content.length) { mutableStateOf(false) }
    var scrollPerfStartMs by remember(content.length) { mutableStateOf(0L) }
    var scrollPerfLastMs by remember(content.length) { mutableStateOf(0L) }
    var scrollPerfFrames by remember(content.length) { mutableStateOf(0) }
    var scrollPerfSlowFrames by remember(content.length) { mutableStateOf(0) }
    var scrollPerfMaxFrameMs by remember(content.length) { mutableStateOf(0L) }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (!scrollPerfInitialized) {
            scrollPerfInitialized = true
            return@LaunchedEffect
        }
        val now = SystemClock.elapsedRealtime()
        if (scrollPerfStartMs <= 0L) {
            scrollPerfStartMs = now
            scrollPerfLastMs = now
            scrollPerfFrames = 0
            scrollPerfSlowFrames = 0
            scrollPerfMaxFrameMs = 0L
            KardLeafLog.d(
                USER_PERF_TRACE_TAG,
                "editorScroll humanStart mode=largePlainPreview contentLen=${content.length} " +
                    "sizeTier=${userPerfNoteSizeTier(content.length)} firstIndex=${listState.firstVisibleItemIndex} " +
                    "offset=${listState.firstVisibleItemScrollOffset}",
            )
        } else {
            val frameMs = now - scrollPerfLastMs
            if (frameMs > 0L) {
                scrollPerfFrames++
                scrollPerfMaxFrameMs = maxOf(scrollPerfMaxFrameMs, frameMs)
                if (frameMs > 32L) scrollPerfSlowFrames++
            }
            scrollPerfLastMs = now
        }
        onFastScrollSourceScrolled()
        delay(180L)
        if (scrollPerfLastMs == now && scrollPerfStartMs > 0L) {
            val elapsed = (scrollPerfLastMs - scrollPerfStartMs).coerceAtLeast(0L)
            val avgFrame = if (scrollPerfFrames > 0) elapsed.toFloat() / scrollPerfFrames else 0f
            val smooth = scrollPerfSlowFrames == 0 && scrollPerfMaxFrameMs <= 32L
            KardLeafLog.d(
                USER_PERF_TRACE_TAG,
                "editorScroll humanSettled mode=largePlainPreview elapsed=${elapsed}ms " +
                    "frames=$scrollPerfFrames slowFrames=$scrollPerfSlowFrames " +
                    "maxFrame=${scrollPerfMaxFrameMs}ms avgFrame=${String.format("%.1f", avgFrame)}ms " +
                    "smooth=$smooth contentLen=${content.length} sizeTier=${userPerfNoteSizeTier(content.length)} " +
                    "firstIndex=${listState.firstVisibleItemIndex} offset=${listState.firstVisibleItemScrollOffset}",
            )
            scrollPerfStartMs = 0L
            scrollPerfLastMs = 0L
            scrollPerfFrames = 0
            scrollPerfSlowFrames = 0
            scrollPerfMaxFrameMs = 0L
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(content.length) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        onUserInteraction()
                    }
                }
            },
    ) {
        item(key = "large_plain_text_preview_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title.ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "当前笔记过大，已切换为纯文本快速预览，正文会按需分块显示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(
            count = chunkCount,
            key = { index -> "large_plain_text_chunk_$index" },
        ) { index ->
            val start = index * PREVIEW_CHUNK_CHARS
            val end = minOf(start + PREVIEW_CHUNK_CHARS, content.length)
            val chunkText = content.substring(start, end)
            val highlightStart = maxOf(searchCurrentStart, start)
            val highlightEnd = minOf(searchCurrentEnd, end)
            val chunkDisplayText: AnnotatedString = if (
                searchCurrentStart >= 0 &&
                searchCurrentEnd > searchCurrentStart &&
                highlightStart < highlightEnd
            ) {
                buildAnnotatedString {
                    append(chunkText)
                    addStyle(
                        style = SpanStyle(background = searchHighlightColor),
                        start = highlightStart - start,
                        end = highlightEnd - start,
                    )
                }
            } else {
                AnnotatedString(chunkText)
            }
            Text(
                text = chunkDisplayText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = contentTextSizeSp.sp,
                    lineHeight = (contentTextSizeSp * contentLineHeightMultiplier).sp,
                    letterSpacing = contentLetterSpacingSp.sp,
                    fontFamily = editorComposeFontFamily(contentFontFamily),
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (index == 0) {
                            Modifier.onGloballyPositioned { onFirstContentLaidOut() }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = (contentParagraphSpacingDp / 4f).dp),
            )
        }
    }
}

internal fun editorComposeFontFamily(fontFamily: String): FontFamily? =
    when (fontFamily.trim().lowercase(Locale.ROOT)) {
        "", "system" -> null
        "sans-serif" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        else -> null
    }

internal fun largePlainTextPreviewChunkCount(textLength: Int): Int =
    if (textLength <= 0) 0 else ((textLength - 1) / PREVIEW_CHUNK_CHARS) + 1

internal fun largePlainTextPreviewFastScrollMetrics(
    listState: LazyListState,
    chunkCount: Int,
): EditorFastScrollMetrics {
    val totalItems = chunkCount + 1
    if (totalItems <= 1) return EditorFastScrollMetrics()
    val visibleItems = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val maxFirstIndex = (totalItems - visibleItems).coerceAtLeast(1)
    return EditorFastScrollMetrics(
        canScroll = true,
        ratio = (listState.firstVisibleItemIndex.toFloat() / maxFirstIndex).coerceIn(0f, 1f),
        thumbFraction = (visibleItems.toFloat() / totalItems).coerceIn(0f, 1f),
    )
}
