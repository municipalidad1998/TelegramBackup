package com.telegrambackup.ui.screens.video

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.util.FileUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    fileId: Long,
    onBack: () -> Unit,
    viewModel: VideoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showControls by remember { mutableStateOf(true) }
    var showPlaylist by remember { mutableStateOf(false) }

    LaunchedEffect(fileId) {
        viewModel.loadVideo(fileId)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.pausePlayback() }
    }

    Scaffold(
        topBar = {
            if (showControls) {
                TopAppBar(
                    title = { Text(uiState.currentFile?.fileName ?: "Video", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showPlaylist = !showPlaylist }) {
                            Icon(Icons.Filled.PlaylistPlay, "Playlist")
                        }
                        // Mini player toggle
                        IconButton(onClick = { viewModel.toggleMiniPlayer() }) {
                            Icon(Icons.Filled.PictureInPictureAlt, "Mini Player")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable { showControls = !showControls }
        ) {
            // ExoPlayer View
            uiState.exoPlayer?.let { player ->
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            this.player = player
                            useController = false // We use custom controls
                            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Custom Controls Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    // Center play/pause
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 5s
                        IconButton(
                            onClick = { viewModel.seekRelative(-5000L) },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Filled.Replay5,
                                "Rewind 5s",
                                modifier = Modifier.size(40.dp),
                                tint = Color.White
                            )
                        }

                        // Play/Pause
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (uiState.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(56.dp),
                                tint = Color.White
                            )
                        }

                        // Forward 5s
                        IconButton(
                            onClick = { viewModel.seekRelative(5000L) },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Filled.Forward5,
                                "Forward 5s",
                                modifier = Modifier.size(40.dp),
                                tint = Color.White
                            )
                        }
                    }

                    // Bottom progress bar
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Progress slider
                        Slider(
                            value = uiState.currentPosition.toFloat().coerceIn(0f, uiState.duration.toFloat().coerceAtLeast(1f)),
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..uiState.duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFF2AABEE),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatDuration(uiState.currentPosition),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                            Text(
                                formatDuration(uiState.duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }

                    // Video info
                    uiState.currentFile?.let { file ->
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 16.dp, top = 80.dp)
                        ) {
                            Text(
                                file.fileName,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                FileUtils.formatFileSize(file.fileSize),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // Playlist drawer
            if (showPlaylist) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(280.dp),
                    color = Color.Black.copy(alpha = 0.9f)
                ) {
                    Column {
                        Text(
                            "Playlist",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Divider(color = Color.White.copy(alpha = 0.2f))

                        LazyColumn {
                            items(uiState.playlist) { file ->
                                PlaylistItem(
                                    file = file,
                                    isPlaying = file.id == fileId,
                                    onClick = {
                                        viewModel.loadVideo(file.id)
                                        showPlaylist = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Loading
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF2AABEE)
                )
            }
        }
    }
}

@Composable
fun PlaylistItem(
    file: BackupFile,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isPlaying) Color(0xFF2AABEE).copy(alpha = 0.2f)
                else Color.Transparent
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (File(file.filePath).exists()) {
                AsyncImage(
                    model = file.filePath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Filled.PlayCircle, null, tint = Color.White.copy(alpha = 0.6f))
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = if (isPlaying) Color(0xFF2AABEE) else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                FileUtils.formatFileSize(file.fileSize),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        if (isPlaying) {
            Icon(
                Icons.Filled.VolumeUp,
                null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF2AABEE)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
