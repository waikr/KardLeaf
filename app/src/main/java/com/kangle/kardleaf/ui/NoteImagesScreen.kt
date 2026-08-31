package com.kangle.kardleaf.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteImagesScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onNoteClick: (Note) -> Unit,
) {
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    var images by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }

    LaunchedEffect(notes) {
        val activeNotes = notes.filter { !it.isTrashed }
        if (activeNotes.isEmpty()) {
            images = emptyList()
            return@LaunchedEffect
        }

        // The note list already carries the first image reference. Show those
        // immediately; a full-file read must never gate opening this screen.
        val known = linkedSetOf<String>()
        val indexedImages = activeNotes.flatMap { note ->
            note.firstImageReference
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { reference ->
                    known += galleryImageKey(note, reference)
                    listOf(GalleryImage(note, reference))
                }
                ?: emptyList()
        }
        images = indexedImages
        try {
            // Fill in additional references incrementally. This mirrors the
            // reference app's lazy resource list and keeps SAF/provider errors
            // local to one note instead of leaving the whole page spinning.
            for (note in activeNotes) {
                ensureActive()
                val fullNote = try {
                    viewModel.getFullNoteForShare(note.id)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                } ?: continue
                val references = withContext(Dispatchers.Default) { galleryImageReferences(fullNote) }
                val additions = references
                    .map { reference -> GalleryImage(note = fullNote, reference = reference) }
                    .filter { known.add(galleryImageKey(it.note, it.reference)) }
                if (additions.isNotEmpty()) images += additions
                yield()
            }
        } catch (error: CancellationException) {
            throw error
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("图片") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                images.isEmpty() -> Text(
                    text = "当前笔记没有可显示的本地图片",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(images, key = { "${it.note.file.path}:${it.reference}" }) { image ->
                        ImageGalleryCard(
                            image = image,
                            peekThumbnail = { viewModel.peekImageThumbnailBitmap(image.note, image.reference) },
                            loadThumbnail = { viewModel.resolveImageThumbnailBitmap(image.note, image.reference) },
                            onClick = { onNoteClick(image.note) },
                        )
                    }
                }
            }
        }
    }
}

private fun galleryImageKey(note: Note, reference: String): String =
    "${note.file.path}\u0000$reference"

@Composable
private fun ImageGalleryCard(
    image: GalleryImage,
    peekThumbnail: () -> android.graphics.Bitmap?,
    loadThumbnail: suspend () -> android.graphics.Bitmap?,
    onClick: () -> Unit,
) {
    var bitmap by remember(image.note.file.path, image.reference) {
        mutableStateOf(peekThumbnail())
    }
    var loadFinished by remember(image.note.file.path, image.reference) {
        mutableStateOf(false)
    }

    LaunchedEffect(image.note.file.path, image.note.lastModified.time, image.reference) {
        peekThumbnail()?.let { bitmap = it }
        loadFinished = false
        val loadedBitmap = try {
            loadThumbnail()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        bitmap = loadedBitmap
        loadFinished = true
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = image.reference,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (loadFinished) {
                    Text("无法显示", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = image.note.title.ifBlank { image.note.file.nameWithoutExtension },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = image.reference,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun galleryImageReferences(note: Note): List<String> {
    val found = linkedSetOf<String>()
    note.firstImageReference?.trim()?.takeIf { it.isNotBlank() }?.let(found::add)
    extractLocalMarkdownImageReferences(note.content).forEach(found::add)
    return found.toList()
}

private data class GalleryImage(
    val note: Note,
    val reference: String,
)

private fun decodeDataUriBitmap(dataUri: String): android.graphics.Bitmap? =
    runCatching {
        val base64 = dataUri.substringAfter("base64,", "")
        if (base64.isBlank()) return@runCatching null
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
