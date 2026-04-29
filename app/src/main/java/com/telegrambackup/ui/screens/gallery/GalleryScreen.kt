package com.telegrambackup.ui.screens.gallery

import android.app.Activity
import android.view.View
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.ui.components.MediaGridItem
import com.telegrambackup.ui.components.StatusChip
import com.telegrambackup.util.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private fun openDocumentExternally(context: android.content.Context, file: BackupFile) {
    val localFile = File(file.filePath)
    if (!localFile.exists()) return
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", localFile)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mimeType ?: "*/*")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Abrir con"))
    } catch (_: Exception) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onNavigateToVideo: (Long) -> Unit,
    onNavigateToImage: (Long) -> Unit,
    fixedFilter: FileType? = null,          // when set, hides chips and locks the filter
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(fixedFilter) }
    val context = LocalContext.current

    // Apply fixed filter once on entry
    LaunchedEffect(fixedFilter) {
        if (fixedFilter != null) viewModel.setFilter(fixedFilter)
    }

    val screenTitle = when (fixedFilter) {
        FileType.IMAGE -> "Fotos"
        FileType.VIDEO -> "Videos"
        FileType.DOCUMENT -> "Documentos"
        FileType.AUDIO -> "Audio"
        null -> "Galería"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        TopAppBar(
            title = { Text(screenTitle) },
            actions = {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Outlined.Refresh, "Actualizar")
                }
            }
        )

        // Filter chips — only shown when NOT using a fixed filter
        if (fixedFilter == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null; viewModel.setFilter(null) },
                    label = { Text("Todos") }
                )
                FilterChip(
                    selected = selectedFilter == FileType.IMAGE,
                    onClick = { selectedFilter = FileType.IMAGE; viewModel.setFilter(FileType.IMAGE) },
                    label = { Text("Fotos") },
                    leadingIcon = { Icon(Icons.Outlined.Image, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = selectedFilter == FileType.VIDEO,
                    onClick = { selectedFilter = FileType.VIDEO; viewModel.setFilter(FileType.VIDEO) },
                    label = { Text("Videos") },
                    leadingIcon = { Icon(Icons.Outlined.PlayCircle, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = selectedFilter == FileType.DOCUMENT,
                    onClick = { selectedFilter = FileType.DOCUMENT; viewModel.setFilter(FileType.DOCUMENT) },
                    label = { Text("Documentos") },
                    leadingIcon = { Icon(Icons.Outlined.Description, null, Modifier.size(16.dp)) }
                )
            }
        }

        // Timeline / Grid
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            // Show error
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        uiState.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else if (uiState.groupedFiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Collections,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Sin archivos aún",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Escanea tu dispositivo para encontrar archivos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                uiState.groupedFiles.forEach { (dayLabel, files) ->
                    // Date header
                    item(span = { GridItemSpan(3) }) {
                        Text(
                            dayLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(files, key = { it.id }) { file ->
                        MediaGridItem(
                            file = file,
                            onClick = {
                                when (file.fileType) {
                                    FileType.VIDEO -> onNavigateToVideo(file.id)
                                    FileType.IMAGE -> onNavigateToImage(file.id)
                                    FileType.DOCUMENT -> openDocumentExternally(context, file)
                                    else -> onNavigateToImage(file.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    fileId: Long,
    onBack: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // All images (regardless of current filter)
    val images = remember(uiState.allFiles) {
        uiState.allFiles.filter { it.fileType == FileType.IMAGE }
    }
    val initialPage = remember(images, fileId) {
        images.indexOfFirst { it.id == fileId }.coerceAtLeast(0)
    }

    if (images.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = initialPage) { images.size }
    val currentFile = images.getOrNull(pagerState.currentPage)
    var showControls by remember { mutableStateOf(true) }

    // Immersive fullscreen while viewer is open
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val decorView = activity?.window?.decorView
        @Suppress("DEPRECATION")
        decorView?.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        onDispose {
            @Suppress("DEPRECATION")
            decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { images[it].id }
        ) { page ->
            val backupFile = images[page]
            var scale by remember { mutableFloatStateOf(1f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }

            // Reset zoom when page changes
            LaunchedEffect(page) {
                scale = 1f; offsetX = 0f; offsetY = 0f
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showControls = !showControls }
                    .pointerInput(page) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = if (File(backupFile.filePath).exists()) backupFile.filePath
                            else backupFile.telegramFileId,
                    contentDescription = backupFile.fileName,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Top bar overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Text(
                        currentFile?.fileName ?: "",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                actions = {
                    Text(
                        "${pagerState.currentPage + 1} / ${images.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                )
            )
        }

        // Bottom info bar overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            currentFile?.let { backupFile ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        backupFile.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        "${FileUtils.formatFileSize(backupFile.fileSize)} • ${
                            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(backupFile.dateAdded)
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusChip(status = backupFile.uploadStatus)
                    }
                }
            }
        }
    }
}
