package com.kangle.kardleaf.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
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
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.kangle.kardleaf.ui.theme.LocalKardLeafGlobalCornerRadiusDp
import com.kangle.kardleaf.ui.theme.LocalKardLeafHomeCornerRadiusDp
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.withTimeoutOrNull

private const val USER_PERF_TRACE_TAG = "KardLeafUserPerf"
private const val OPEN_PATH_PROBE_TAG = "KardLeafOpenPathProbe"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    isSelected: Boolean,
    cardDensity: PrefsManager.CardDensity,
    showFolderTag: Boolean,
    showYamlTags: Boolean = false,
    showModifiedDate: Boolean = false,
    modifiedDateFormat: String = PrefsManager.DEFAULT_CARD_MODIFIED_DATE_FORMAT,
    showDeletedDate: Boolean = false,
    showNoteTitle: Boolean = true,
    showDateFilenameTitle: Boolean = true,
    customHiddenFilenamePatterns: List<String> = emptyList(),
    unnamedNoteDateFormat: String = KardLeafCustomFeatures.DefaultUnnamedNoteDateFormat,
    searchQuery: String = "",
    searchMatch: SearchMatch? = null,
    showImagePreview: Boolean = false,
    loadImageThumbnail: suspend (Note) -> Bitmap? = { null },
    onSearchJump: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val themeStyle = LocalKardLeafThemeStyle.current
    val isModern = themeStyle != PrefsManager.AppThemeStyle.CLASSIC
    val isDracula = themeStyle == PrefsManager.AppThemeStyle.DRACULA
    val isCleanList = themeStyle == PrefsManager.AppThemeStyle.CLEAN_LIST
    val isDarkCardTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isCleanListLight = isCleanList && !isDarkCardTheme
    val homeCornerRadiusDp = LocalKardLeafHomeCornerRadiusDp.current.takeIf { it >= 0 }
        ?: LocalKardLeafGlobalCornerRadiusDp.current.takeIf { it >= 0 }
    val isCompact = cardDensity == PrefsManager.CardDensity.COMPACT
    val cardShape = RoundedCornerShape(
        (homeCornerRadiusDp ?: when {
            isDracula -> if (isCompact) 10 else 14
            isModern -> if (isCompact) 20 else 28
            else -> 16
        }).dp,
    )
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue =
            when {
                isDracula -> MaterialTheme.colorScheme.surfaceContainer
                isCleanListLight -> Color.White
                isCleanList -> MaterialTheme.colorScheme.surface
                isModern -> MaterialTheme.colorScheme.surfaceContainerLow
                else -> MaterialTheme.colorScheme.surface
            },
        label = "NoteCardContainerColor",
    )
    val contentColor = MaterialTheme.colorScheme.onSurface
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
                .takeIf { it.isNotBlank() }
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
    val safeModifiedDateFormat = remember(modifiedDateFormat) {
        modifiedDateFormat.trim().takeIf { it.isNotBlank() } ?: PrefsManager.DEFAULT_CARD_MODIFIED_DATE_FORMAT
    }
    val defaultModifiedDateFormatter = remember {
        SimpleDateFormat(PrefsManager.DEFAULT_CARD_MODIFIED_DATE_FORMAT, Locale.getDefault())
    }
    val modifiedDateFormatter = remember(safeModifiedDateFormat) {
        runCatching { SimpleDateFormat(safeModifiedDateFormat, Locale.getDefault()) }.getOrNull()
    }
    val modifiedDateText = remember(note.lastModified.time, modifiedDateFormatter, defaultModifiedDateFormatter) {
        val formatter = modifiedDateFormatter ?: defaultModifiedDateFormatter
        runCatching { formatter.format(note.lastModified) }
            .getOrElse { defaultModifiedDateFormatter.format(note.lastModified) }
    }
    val deletedDateFormatter = remember {
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    }
    val deletedDateText = remember(note.deletedAt?.time, deletedDateFormatter) {
        note.deletedAt?.let { deletedDateFormatter.format(it) }
    }
    val imageReference = note.firstImageReference?.takeIf { it.isNotBlank() }
    val shouldLoadThumbnail = showImagePreview && searchMatch == null && imageReference != null
    val showSearchJump = searchQuery.isNotBlank() && searchMatch != null && searchMatch.startOffset >= 0 && onSearchJump != null
    var thumbnailBitmap by remember(note.id, imageReference, note.lastModified.time) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(shouldLoadThumbnail, note.id, imageReference, note.lastModified.time, loadImageThumbnail) {
        if (!shouldLoadThumbnail) {
            thumbnailBitmap = null
            return@LaunchedEffect
        }

        val thumbnailStartMs = SystemClock.elapsedRealtime()
        val loadedBitmap = withTimeoutOrNull(2000L) {
            runCatching { loadImageThumbnail(note) }.getOrNull()
        }
        val thumbnailElapsedMs = SystemClock.elapsedRealtime() - thumbnailStartMs
        if (thumbnailElapsedMs >= 32L || loadedBitmap == null) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "dashboard thumbnailLoad elapsed=${thumbnailElapsedMs}ms ok=${loadedBitmap != null} " +
                    "folder=${note.folder} path=${note.file.path} imageRefLen=${imageReference?.length ?: 0}",
            )
            KardLeafLog.d(
                USER_PERF_TRACE_TAG,
                "dashboardThumbnailLoad elapsed=${thumbnailElapsedMs}ms ok=${loadedBitmap != null} " +
                    "noteId=${note.id} folder=${note.folder} path=${note.file.path} imageRefLen=${imageReference?.length ?: 0} " +
                    "modified=${note.lastModified.time}",
            )
        }
        if (loadedBitmap != null || thumbnailBitmap == null) {
            thumbnailBitmap = loadedBitmap
        }
    }

    val cardElevation by animateDpAsState(
        targetValue = if (isModern) {
            when {
                isCleanListLight -> 0.dp
                isDracula -> 1.dp
                isSelected -> 0.dp
                else -> 3.dp
            }
        } else {
            1.dp
        },
        label = "NoteCardElevation",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = if (isModern) 0.82f else 1f)
        } else if (isDracula) {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        } else if (isCleanListLight) {
            Color(0xFFE5E7EB)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isModern) 0.48f else 0.5f)
        },
        label = "NoteCardBorderColor",
    )
    val borderWidth = if (isDracula) 1.5.dp else if (isSelected) if (isModern) 1.5.dp else 3.dp else 1.dp
    val selectedRailColor = MaterialTheme.colorScheme.primary
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
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClickAction,
                )
                .then(
                    Modifier.border(borderWidth, borderColor, cardShape),
                ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        drawContent()
                        if (isModern && isSelected) {
                            val railWidth = 5.dp.toPx()
                            drawRoundRect(
                                color = selectedRailColor,
                                size = Size(railWidth, size.height),
                                cornerRadius = CornerRadius(railWidth, railWidth),
                            )
                        }
                    },
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        start = cardPadding + if (isModern && isSelected) 4.dp else 0.dp,
                        top = cardPadding,
                        end = cardPadding,
                        bottom = cardPadding,
                    ),
            ) {
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
                    } else if (note.contentPreview.isNotEmpty()) {
                        Box {
                            val previewContent = remember(note.contentPreview, shouldLoadThumbnail) {
                                plainCardPreview(note.contentPreview, hideImagePlaceholders = shouldLoadThumbnail)
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
                    val thumbnailShape = RoundedCornerShape(
                        (homeCornerRadiusDp ?: when {
                            isDracula -> 8
                            isModern -> 16
                            else -> 8
                        }).dp,
                    )
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
            if (folderTag != null || visibleYamlTags.isNotEmpty() || showModifiedDateText || showDeletedDateText || showSearchJump) {
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
                    if (showSearchJump) {
                        TextButton(onClick = { onSearchJump?.invoke() }) {
                            Text("跳转")
                        }
                    }
                }
            }
            }

            if (note.isFavorite) {
                NoteCardBookmarkBadge(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = if (isCompact) 8.dp else 10.dp, end = if (isCompact) 10.dp else 12.dp)
                            .size(if (isCompact) 16.dp else 18.dp),
                )
            }
        }
    }
}

@Composable
private fun NoteCardBookmarkBadge(modifier: Modifier = Modifier) {
    val isModern = LocalKardLeafThemeStyle.current != PrefsManager.AppThemeStyle.CLASSIC
    Icon(
        imageVector = Icons.Filled.Bookmark,
        contentDescription = null,
        tint = if (isModern) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
        },
        modifier = modifier,
    )
}

@Composable
private fun NoteCardTagChip(text: String) {
    val themeStyle = LocalKardLeafThemeStyle.current
    val isModern = themeStyle != PrefsManager.AppThemeStyle.CLASSIC
    val isDracula = themeStyle == PrefsManager.AppThemeStyle.DRACULA
    val shape = RoundedCornerShape(if (isDracula) 7.dp else if (isModern) 999.dp else 4.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (isDracula) MaterialTheme.colorScheme.onSurface else if (isModern) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .background(
                    if (isDracula) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else if (isModern) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    },
                    shape,
                )
                .border(
                    1.dp,
                    if (isDracula) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.44f)
                    } else if (isModern) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    },
                    shape,
                )
                .padding(horizontal = if (isModern) 8.dp else 6.dp, vertical = if (isModern) 3.dp else 2.dp),
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
    NoteFormatUtils.buildPlainTextPreview(
        content = content,
        maxChars = 500,
        maxLines = 10,
        hideImagePlaceholders = hideImagePlaceholders,
    )


@Composable
fun PermissionRequestState(
    onCreateSampleVault: () -> Unit,
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
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = "请选择新建笔记库，或导入已有笔记库。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        Button(onClick = onCreateSampleVault) {
            Text("新建笔记库")
        }
        OutlinedButton(
            onClick = onSelectFolder,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Text("导入笔记库")
        }
    }
}
