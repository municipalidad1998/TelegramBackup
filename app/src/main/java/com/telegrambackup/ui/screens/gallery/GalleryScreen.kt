package com.telegrambackup.ui.screens.gallery

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    fileId: Long,
    onBack: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val file by viewModel.getFileById(fileId).collectAsStateWithLifecycle(null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file?.fileName ?: "Image") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        file?.let { backupFile ->
            var scale by remember { mutableFloatStateOf(1f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = if (File(backupFile.filePath).exists()) backupFile.filePath
                            else backupFile.telegramFileId,
                    contentDescription = backupFile.fileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentScale = ContentScale.Fit
                )

                // Bottom info bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp)
                ) {
                    Text(
                        backupFile.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        "${FileUtils.formatFileSize(backupFile.fileSize)} • ${
                            SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(backupFile.dateAdded)
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusChip(status = backupFile.uploadStatus)
                    }
                }
            }
        }
    }
}
