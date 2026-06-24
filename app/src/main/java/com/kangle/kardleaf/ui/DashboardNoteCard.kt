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
import androidx.compose.foundation.layout.Spacer
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
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.withTimeoutOrNull

private val CardPreviewWikiImageRegex = Regex("""!\[\[([^|\]]+)(?:\|[^\]]*)?]]""")
private val CardPreviewMarkdownImageRegex = Regex("""!\[[^]]*]\(([^)]+)\)""")
private val CardPreviewHeadingRegex = Regex("""^#{1,6}\s+""")
private val CardPreviewTaskRegex = Regex("""^\s*[-*+]\s+\[[ xX]]\s+""")
private val CardPreviewBulletRegex = Regex("""^\s*[-*+]\s+""")
private val CardPreviewOrderedListRegex = Regex("""^\s*\d+\.\s+""")
private val CardPreviewMarkdownMarksRegex = Regex("""[*_`~>#]""")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    isSelected: Boolean,
    cardDensity: PrefsManager.CardDensity,
    showFolderTag: Boolean,
    showYamlTags: Boolean = false,
    showModifiedDate: Boolean = false,
    showDeletedDate: Boolean = false,
    showNoteTitle: Boolean = true,
    showDateFilenameTitle: Boolean = true,
    customHiddenFilenamePatterns: List<String> = emptyList(),
    unnamedNoteDateFormat: String = KardLeafCustomFeatures.DefaultUnnamedNoteDateFormat,
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
    val displayTitle = remember(note.title, showNoteTitle, showDateFilenameTitle, customHiddenFilenamePatterns, unnamedNoteDateFormat) {
        note.title
            .takeIf { it.isNotBlank() && showNoteTitle }
            ?.takeUnless { title ->
                !showDateFilenameTitle && shouldHideDateFilenameTitle(
                    title = title,
                    dateFormat = unnamedNoteDateFormat,
                    hiddenFilenamePatterns = customHiddenFilenamePatterns,
                )
            }
    }
    val modifiedDateText = remember(note.lastModified.time) {
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(note.lastModified)
    }
    val deletedDateText = remember(note.deletedAt?.time) {
        note.deletedAt?.let { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(it) }
    }
    val imageReference = note.firstImageReference?.takeIf { it.isNotBlank() }
    val shouldLoadThumbnail = showImagePreview && searchMatch == null && imageReference != null
    var thumbnailBitmap by remember(note.id, imageReference, note.lastModified.time) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(shouldLoadThumbnail, note.id, imageReference, note.lastModified.time, loadImageThumbnail) {
        if (!shouldLoadThumbnail) {
            thumbnailBitmap = null
            return@LaunchedEffect
        }

        val loadedBitmap = withTimeoutOrNull(2000L) {
            runCatching { loadImageThumbnail(note) }.getOrNull()
        }
        if (loadedBitmap != null || thumbnailBitmap == null) {
            thumbnailBitmap = loadedBitmap
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
                    displayTitle?.let { title ->
                        Text(
                            text =
                                if (searchQuery.isNotBlank()) {
                                    highlightedText(title, searchQuery)
                                } else {
                                    buildAnnotatedString { append(title) }
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
            val showModifiedDateText = !isCompact && showModifiedDate
            val showDeletedDateText = showDeletedDate && deletedDateText != null
            if (folderTag != null || visibleYamlTags.isNotEmpty() || showModifiedDateText || showDeletedDateText) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (folderTag != null || visibleYamlTags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.weight(1f),
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
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    val rightDateText = if (showDeletedDateText) deletedDateText else if (showModifiedDateText) modifiedDateText else null
                    if (rightDateText != null) {
                        Text(
                            text = rightDateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showDeletedDateText) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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


private fun shouldHideDateFilenameTitle(
    title: String,
    dateFormat: String,
    hiddenFilenamePatterns: List<String>,
): Boolean {
    val patterns = hiddenFilenamePatterns.ifEmpty {
        listOf(
            PrefsManager.DEFAULT_HIDDEN_DATE_FILENAME_PATTERN,
            PrefsManager.DEFAULT_HIDDEN_COPY_FILENAME_PATTERN,
        )
    }
    return isPureDateTitle(title, dateFormat) || patterns.any { pattern ->
        isHiddenFilenamePatternMatch(title, pattern)
    }
}

private fun isHiddenFilenamePatternMatch(
    title: String,
    pattern: String,
): Boolean {
    val trimmedTitle = title.trim()
    val trimmedPattern = pattern.trim()
    if (trimmedTitle.isBlank() || trimmedPattern.isBlank()) return false
    if (trimmedTitle == trimmedPattern) return true
    if (isPureDateTitle(trimmedTitle, trimmedPattern)) return true

    val copyMarkerIndex = trimmedPattern.indexOf("~副本")
    if (copyMarkerIndex <= 0) return false

    val datePattern = trimmedPattern.substring(0, copyMarkerIndex)
    return runCatching {
        val formatter = SimpleDateFormat(datePattern, Locale.getDefault()).apply { isLenient = false }
        val position = ParsePosition(0)
        val parsedDate = formatter.parse(trimmedTitle, position)
        if (parsedDate == null || position.index <= 0) {
            false
        } else {
            val suffix = trimmedTitle.substring(position.index)
            val expectedSuffix = trimmedPattern.substring(copyMarkerIndex)
            if (expectedSuffix.endsWith("*")) {
                suffix.startsWith(expectedSuffix.removeSuffix("*"))
            } else {
                suffix == "~副本" || suffix.matches(Regex("""~副本(?:\d+)?(?:~\d+)*"""))
            }
        }
    }.getOrDefault(false)
}

private fun isPureDateTitle(
    title: String,
    dateFormat: String,
): Boolean {
    val trimmed = title.trim()
    if (trimmed.isBlank() || dateFormat.isBlank()) return false
    return runCatching {
        val formatter = SimpleDateFormat(dateFormat, Locale.getDefault()).apply { isLenient = false }
        val position = ParsePosition(0)
        formatter.parse(trimmed, position) != null && position.index == trimmed.length
    }.getOrDefault(false)
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
                        .replace(CardPreviewWikiImageRegex, "")
                        .replace(CardPreviewMarkdownImageRegex, "")
                } else {
                    line
                        .replace(CardPreviewWikiImageRegex, "[图片: $1]")
                        .replace(CardPreviewMarkdownImageRegex, "[图片]")
                }
            withoutImages
                .replace(CardPreviewHeadingRegex, "")
                .replace(CardPreviewTaskRegex, "")
                .replace(CardPreviewBulletRegex, "")
                .replace(CardPreviewOrderedListRegex, "")
                .replace(CardPreviewMarkdownMarksRegex, "")
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
