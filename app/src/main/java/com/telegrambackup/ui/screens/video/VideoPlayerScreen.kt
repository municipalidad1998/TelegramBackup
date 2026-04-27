package com.telegrambackup.ui.screens.video

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.UploadStatus
import com.telegrambackup.util.FileUtils
import java.io.File

// HBO Max brand color
private val HboRed = Color(0xFF0099E6)
private val HboGradientTop = Color(0x99000000)
private val HboGradientBottom = Color(0xDD000000)

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
    var isLandscape by remember { mutableStateOf(false) }

    LaunchedEffect(fileId) {
        viewModel.loadVideo(fileId)
    }

    // Reset orientation when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.pausePlayback()
            (context as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls }
    ) {
        // ExoPlayer View
        uiState.exoPlayer?.let { player ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(HboGradientBottom, Color.Transparent)
                    )
                )
        )

        // Bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, HboGradientBottom)
                    )
                )
        )

        // Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // Top bar with back + title + options
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            uiState.currentFile?.fileName ?: "Video",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                        uiState.currentFile?.let { file ->
                            Text(
                                FileUtils.formatFileSize(file.fileSize),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    // Upload status badge
                    uiState.currentFile?.let { file ->
                        if (file.uploadStatus == UploadStatus.UPLOADED) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF34D058).copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.CloudDone,
                                        null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFF34D058)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "En la nube",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF34D058)
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    // Rotation toggle
                    IconButton(onClick = {
                        isLandscape = !isLandscape
                        (context as? Activity)?.requestedOrientation = if (isLandscape)
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }) {
                        Icon(
                            if (isLandscape) Icons.Filled.StayCurrentLandscape
                            else Icons.Filled.StayCurrentPortrait,
                            "Rotate",
                            tint = Color.White
                        )
                    }
                    // Playlist button
                    IconButton(onClick = { showPlaylist = !showPlaylist }) {
                        Icon(Icons.Filled.PlaylistPlay, "Playlist", tint = Color.White)
                    }
                }

                // Center play controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous video
                    IconButton(
                        onClick = { viewModel.playPrevious() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            "Previous",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    // Rewind 10s
                    IconButton(
                        onClick = { viewModel.seekRelative(-10_000L) },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Filled.Replay10,
                            "Rewind 10s",
                            modifier = Modifier.size(40.dp),
                            tint = Color.White
                        )
                    }

                    // Play/Pause (HBO-style large button)
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { viewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (uiState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(48.dp),
                            tint = Color.White
                        )
                    }

                    // Forward 10s
                    IconButton(
                        onClick = { viewModel.seekRelative(10_000L) },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Filled.Forward10,
                            "Forward 10s",
                            modifier = Modifier.size(40.dp),
                            tint = Color.White
                        )
                    }

                    // Next video
                    IconButton(
                        onClick = { viewModel.playNext() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            "Next",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // Bottom progress bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Slider(
                        value = uiState.currentPosition.toFloat()
                            .coerceIn(0f, uiState.duration.toFloat().coerceAtLeast(1f)),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..uiState.duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = HboRed,
                            activeTrackColor = HboRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatDuration(uiState.currentPosition),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Text(
                            formatDuration(uiState.duration),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Playlist side drawer
        AnimatedVisibility(
            visible = showPlaylist,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp),
                color = Color(0xFF0E1621).copy(alpha = 0.97f)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Lista de videos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showPlaylist = false }) {
                            Icon(Icons.Filled.Close, "Close", tint = Color.White)
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                    LazyColumn {
                        items(uiState.playlist) { file ->
                            VideoPlaylistItem(
                                file = file,
                                isPlaying = file.id == uiState.currentFile?.id,
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

        // Loading indicator
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = HboRed,
                strokeWidth = 3.dp
            )
        }

        // Error state
        uiState.error?.let { error ->
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun VideoPlaylistItem(
    file: BackupFile,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isPlaying) HboRed.copy(alpha = 0.15f) else Color.Transparent
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
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
                Icon(
                    if (file.uploadStatus == UploadStatus.UPLOADED)
                        Icons.Filled.CloudDone else Icons.Filled.PlayCircle,
                    null,
                    tint = if (file.uploadStatus == UploadStatus.UPLOADED)
                        Color(0xFF34D058) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
            }

            // Playing indicator overlay
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = HboRed
                    )
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = if (isPlaying) HboRed else Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    FileUtils.formatFileSize(file.fileSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
                if (file.uploadStatus == UploadStatus.UPLOADED) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Cloud,
                        null,
                        modifier = Modifier.size(10.dp),
                        tint = Color(0xFF34D058).copy(alpha = 0.7f)
                    )
                }
            }
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
