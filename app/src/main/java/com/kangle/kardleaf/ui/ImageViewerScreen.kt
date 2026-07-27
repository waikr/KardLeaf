package com.kangle.kardleaf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.repository.RoomNoteRepository
import com.kangle.kardleaf.data.utils.KardLeafLog
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

private const val ImageViewerLogTag = "KardLeafImageViewer"
private const val ImageViewerMaxScale = 5f

@Composable
internal fun ImageViewerScreen(
    resource: RoomNoteRepository.ImageViewerResource?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun closeViewer(reason: String) {
        KardLeafLog.d(ImageViewerLogTag, "viewer close reason=$reason")
        onDismiss()
    }

    BackHandler { closeViewer("system-back") }
    Surface(
        modifier = modifier.fillMaxSize().testTag("image_viewer"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { closeViewer("top-back") }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = resource?.reference?.substringAfterLast('/') ?: "图片",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconButton(
                    enabled = resource?.editable == true && resource.bitmap != null,
                    onClick = {
                        KardLeafLog.d(ImageViewerLogTag, "viewer edit type=${resource?.documentType.orEmpty()}")
                        onEdit()
                    },
                    modifier = Modifier.testTag("image_viewer_edit"),
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoading -> CircularProgressIndicator()
                    resource?.errorMessage != null ->
                        Text(
                            text = resource.errorMessage,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(24.dp).testTag("image_viewer_error"),
                        )
                    resource?.bitmap != null -> ZoomableImage(resource)
                    else ->
                        Text(
                            text = resource?.errorMessage ?: "找不到图片",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(24.dp).testTag("image_viewer_error"),
                        )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(resource: RoomNoteRepository.ImageViewerResource) {
    val bitmap = resource.bitmap ?: return
    var viewportSize by remember(resource.reference) { mutableStateOf(IntSize.Zero) }
    var scale by remember(resource.reference) { mutableFloatStateOf(1f) }
    var offset by remember(resource.reference) { mutableStateOf(Offset.Zero) }

    fun fittedSize(): Pair<Float, Float> {
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return 0f to 0f
        val fitScale =
            min(
                viewportSize.width.toFloat() / bitmap.width.coerceAtLeast(1),
                viewportSize.height.toFloat() / bitmap.height.coerceAtLeast(1),
            )
        return bitmap.width * fitScale to bitmap.height * fitScale
    }

    fun clampOffset(
        candidate: Offset,
        candidateScale: Float,
    ): Offset {
        val (fitWidth, fitHeight) = fittedSize()
        val maxX = ((fitWidth * candidateScale - viewportSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((fitHeight * candidateScale - viewportSize.height) / 2f).coerceAtLeast(0f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    LaunchedEffect(viewportSize) {
        if (viewportSize != IntSize.Zero) offset = clampOffset(offset, scale)
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("image_viewer_canvas")
                .onSizeChanged { viewportSize = it }
                .pointerInput(resource.reference, viewportSize) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = (oldScale * zoom).coerceIn(1f, ImageViewerMaxScale)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val scaleRatio = if (oldScale == 0f) 1f else newScale / oldScale
                        val candidate = offset + pan + (centroid - center) * (1f - scaleRatio)
                        scale = newScale
                        offset = clampOffset(candidate, newScale)
                    }
                }
                .pointerInput(resource.reference, viewportSize) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val newScale = 2f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                scale = newScale
                                offset = clampOffset((center - tap) * (newScale - 1f), newScale)
                            }
                            KardLeafLog.d(
                                ImageViewerLogTag,
                                "viewer doubleTap scale=$scale offset=${offset.x},${offset.y}",
                            )
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
        )
    }

    LaunchedEffect(scale, offset) {
        delay(180)
        if (abs(scale - 1f) > 0.01f || offset != Offset.Zero) {
            KardLeafLog.d(ImageViewerLogTag, "viewer transform settled scale=$scale offset=${offset.x},${offset.y}")
        }
    }
}
