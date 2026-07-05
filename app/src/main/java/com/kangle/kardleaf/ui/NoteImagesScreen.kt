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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteImagesScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onNoteClick: (Note) -> Unit,
) {
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    var images by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(notes) {
        val activeNotes = notes.filter { !it.isTrashed }
        if (activeNotes.isEmpty()) {
            images = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            images = withContext(Dispatchers.Default) {
                activeNotes.flatMap { note ->
                    ensureActive()
                    galleryImageReferences(note).map { reference ->
                        GalleryImage(note = note, reference = reference)
                    }
                }
            }
        } catch (_: Exception) {
            images = emptyList()
        } finally {
            isLoading = false
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
                isLoading && images.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                    items(images, key = { "${it.note.file.path}:${it.reference}:${it.note.lastModified.time}" }) { image ->
                        ImageGalleryCard(
                            image = image,
                            loadThumbnail = { viewModel.resolveImageThumbnailBitmap(image.note, image.reference) },
                            onClick = { onNoteClick(image.note) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageGalleryCard(
    image: GalleryImage,
    loadThumbnail: suspend () -> android.graphics.Bitmap?,
    onClick: () -> Unit,
) {
    var bitmap by remember(image.note.file.path, image.note.lastModified.time, image.reference) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    var loadFinished by remember(image.note.file.path, image.note.lastModified.time, image.reference) {
        mutableStateOf(false)
    }

    LaunchedEffect(image.note.file.path, image.note.lastModified.time, image.reference) {
        loadFinished = false
        bitmap = withTimeoutOrNull(2500L) { loadThumbnail() }
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
