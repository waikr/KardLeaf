package com.kangle.kardleaf.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.repository.PrefsManager
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    isSelected: Boolean,
    cardDensity: PrefsManager.CardDensity,
    showFolderTag: Boolean,
    showYamlTags: Boolean = false,
    searchQuery: String = "",
    searchMatch: SearchMatch? = null,
    showImagePreview: Boolean = false,
    loadImageThumbnail: suspend (Note) -> Bitmap? = { null },
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val cardShape = RoundedCornerShape(16.dp)
    val containerColor = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface
    val isCompact = cardDensity == PrefsManager.CardDensity.COMPACT
    val cardPadding = if (isCompact) 10.dp else 16.dp
    val titleBottomPadding = if (isCompact) 4.dp else 8.dp
    val contentMaxLines = if (isCompact) 3 else 8
    val titleStyle =
        (if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
            .copy(fontWeight = FontWeight.SemiBold)
    val contentStyle =
        (if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
            .copy(color = contentColor)
    val folderTag = remember(note.folder, showFolderTag) {
        if (showFolderTag) {
            note.folder
                .replace("\\", "/")
                .trim()
                .takeIf { it.isNotBlank() && it != "Unknown" }
                ?: "obsidian"
        } else {
            null
        }
    }
    val imageReference = note.firstImageReference?.takeIf { it.isNotBlank() }
    val shouldLoadThumbnail = showImagePreview && searchMatch == null && imageReference != null
    var thumbnailBitmap by remember(note.id, imageReference, note.lastModified.time) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(shouldLoadThumbnail, note.id, imageReference, note.lastModified.time) {
        thumbnailBitmap = null
        if (shouldLoadThumbnail) {
            thumbnailBitmap = withTimeoutOrNull(2000L) {
                runCatching { loadImageThumbnail(note) }.getOrNull()
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val onLongClickAction = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongClick()
    }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material.ripple.rememberRipple(),
                    onClick = onClick,
                    onLongClick = onLongClickAction,
                )
                .then(
                    if (isSelected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, cardShape)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), cardShape)
                    },
                ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (note.title.isNotEmpty()) {
                        Text(
                            text =
                                if (searchQuery.isNotBlank()) {
                                    highlightedText(note.title, searchQuery)
                                } else {
                                    buildAnnotatedString { append(note.title) }
                                },
                            style = titleStyle,
                            modifier = Modifier.padding(bottom = titleBottomPadding),
                        )
                    }

                    if (searchMatch != null) {
                        Text(
                            text = searchMatch.scope,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Text(
                            text = note.folder.replace("\\", "/").ifBlank { "根目录" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Text(
                            text = highlightedText(searchMatch.snippet, searchQuery),
                            style = contentStyle,
                            maxLines = if (isCompact) 4 else 7,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    } else if (note.content.isNotEmpty()) {
                        Box {
                            val previewContent = remember(note.content, shouldLoadThumbnail) {
                                plainCardPreview(note.content, hideImagePlaceholders = shouldLoadThumbnail)
                            }
                            val previewMaxLines = if (shouldLoadThumbnail) 3 else contentMaxLines

                            Text(
                                text = previewContent,
                                style = contentStyle,
                                maxLines = previewMaxLines,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            // Invisible overlay to capture clicks on the markdown text area
                            Box(
                                modifier =
                                    Modifier
                                        .matchParentSize()
                                        .combinedClickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = onClick,
                                            onLongClick = onLongClickAction,
                                        ),
                            )
                        }
                    }
                }

                if (shouldLoadThumbnail) {
                    val thumbnailShape = RoundedCornerShape(8.dp)
                    Box(
                        modifier =
                            Modifier
                                .width(84.dp)
                                .height(68.dp)
                                .shadow(1.5.dp, thumbnailShape, clip = false)
                                .clip(thumbnailShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    ) {
                        thumbnailBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = imageReference,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }

            val visibleYamlTags = if (!isCompact && showYamlTags) note.tags.take(3) else emptyList()
            if (folderTag != null || visibleYamlTags.isNotEmpty()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    folderTag?.let { tag ->
                        NoteCardTagChip(tag)
                    }
                    visibleYamlTags.forEach { tag ->
                        NoteCardTagChip("#$tag")
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCardTagChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun highlightedText(
    text: String,
    query: String,
) = buildAnnotatedString {
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    val index = text.indexOf(query, ignoreCase = true)
    if (index < 0) {
        append(text)
        return@buildAnnotatedString
    }
    append(text.substring(0, index))
    withStyle(
        SpanStyle(
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            background = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        append(text.substring(index, index + query.length))
    }
    append(text.substring(index + query.length))
}


private fun plainCardPreview(
    content: String,
    hideImagePlaceholders: Boolean = false,
): String =
    content
        .lineSequence()
        .map { line ->
            val withoutImages =
                if (hideImagePlaceholders) {
                    line
                        .replace(Regex("""!\[\[([^|\]]+)(?:\|[^\]]*)?]]"""), "")
                        .replace(Regex("""!\[[^]]*]\(([^)]+)\)"""), "")
                } else {
                    line
                        .replace(Regex("""!\[\[([^|\]]+)(?:\|[^\]]*)?]]"""), "[图片: $1]")
                        .replace(Regex("""!\[[^]]*]\(([^)]+)\)"""), "[图片]")
                }
            withoutImages
                .replace(Regex("""^#{1,6}\s+"""), "")
                .replace(Regex("""^\s*[-*+]\s+\[[ xX]]\s+"""), "")
                .replace(Regex("""^\s*[-*+]\s+"""), "")
                .replace(Regex("""^\s*\d+\.\s+"""), "")
                .replace(Regex("""[*_`~>#]"""), "")
                .trim()
        }
        .filter { it.isNotBlank() }
        .take(10)
        .joinToString("\n")
        .take(500)


@Composable
fun PermissionRequestState(
    onSelectFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Button(onClick = onSelectFolder) {
            Text(stringResource(R.string.select_folder))
        }
    }
}
