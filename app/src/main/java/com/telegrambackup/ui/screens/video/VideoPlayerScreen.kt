package com.telegrambackup.ui.screens.video

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
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

// ── Brand colours ──────────────────────────────────────────────────────────────
private val PlayerBg    = Color(0xFF000000)
private val Accent      = Color(0xFF4D9FFF)
private val GradTop     = Color(0xBB000000)
private val GradBottom  = Color(0xDD000000)
private val TextPrimary = Color(0xFFE8EDF5)
private val TextSecond  = Color(0xFF8A97B0)
private val NavSurface  = Color(0xEE0D1117)

@Composable
fun VideoPlayerScreen(
    fileId: Long,
    onBack: () -> Unit,
    viewModel: VideoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showControls by remember { mutableStateOf(true) }
    var showPlaylist  by remember { mutableStateOf(false) }

    LaunchedEffect(fileId) { viewModel.loadVideo(fileId) }

    // Auto fullscreen landscape + immersive mode when entering the player
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val decorView = activity?.window?.decorView

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
            viewModel.pausePlayback()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            @Suppress("DEPRECATION")
            decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerBg)
            .clickable { showControls = !showControls }
    ) {
        // ── ExoPlayer surface ─────────────────────────────────────────────────
        uiState.exoPlayer?.let { player ->
            key(uiState.currentFile?.id) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        }
                    },
                    update = { pv -> pv.player = player },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── Gradients ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(GradTop, Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, GradBottom)))
        )

        // ── Controls overlay ──────────────────────────────────────────────────
        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {

                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Volver", tint = TextPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            uiState.currentFile?.fileName ?: "Video",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                        uiState.currentFile?.let { f ->
                            Text(
                                FileUtils.formatFileSize(f.fileSize),
                                fontSize = 11.sp,
                                color = TextSecond
                            )
                        }
                    }
                    // Cloud badge
                    if (uiState.currentFile?.uploadStatus == UploadStatus.UPLOADED) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1DB954).copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CloudDone, null, modifier = Modifier.size(13.dp), tint = Color(0xFF1DB954))
                                Spacer(Modifier.width(3.dp))
                                Text("En la nube", fontSize = 11.sp, color = Color(0xFF1DB954))
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    // Playlist toggle
                    IconButton(onClick = { showPlaylist = !showPlaylist }) {
                        Icon(Icons.Filled.PlaylistPlay, "Lista", tint = TextPrimary)
                    }
                }

                // Center transport controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.playPrevious() }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.SkipPrevious, "Anterior", modifier = Modifier.size(30.dp), tint = TextPrimary.copy(0.8f))
                    }
                    IconButton(onClick = { viewModel.seekRelative(-10_000L) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.Replay10, "Rewind", modifier = Modifier.size(38.dp), tint = TextPrimary)
                    }

                    // Play/Pause button
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Accent.copy(alpha = 0.35f), Color.Transparent)
                                )
                            )
                            .clickable { viewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                null,
                                modifier = Modifier.size(34.dp),
                                tint = TextPrimary
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.seekRelative(10_000L) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.Forward10, "Forward", modifier = Modifier.size(38.dp), tint = TextPrimary)
                    }
                    IconButton(onClick = { viewModel.playNext() }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.SkipNext, "Siguiente", modifier = Modifier.size(30.dp), tint = TextPrimary.copy(0.8f))
                    }
                }

                // Bottom seek bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Slider(
                        value = uiState.currentPosition.toFloat().coerceIn(0f, uiState.duration.toFloat().coerceAtLeast(1f)),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..uiState.duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(uiState.currentPosition), fontSize = 12.sp, color = TextPrimary)
                        Text(formatDuration(uiState.duration), fontSize = 12.sp, color = TextSecond)
                    }
                }
            }
        }

        // ── Playlist drawer ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showPlaylist,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit  = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier.fillMaxHeight().width(290.dp),
                color = NavSurface
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Lista de videos",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showPlaylist = false }) {
                            Icon(Icons.Filled.Close, "Cerrar", tint = TextSecond)
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    LazyColumn {
                        items(uiState.playlist) { file ->
                            VideoPlaylistItem(
                                file = file,
                                isPlaying = file.id == uiState.currentFile?.id,
                                onClick = { viewModel.loadVideo(file.id); showPlaylist = false }
                            )
                        }
                    }
                }
            }
        }

        // ── Loading ───────────────────────────────────────────────────────────
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Accent,
                strokeWidth = 3.dp
            )
        }

        // ── Error ─────────────────────────────────────────────────────────────
        uiState.error?.let { error ->
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.ErrorOutline, null, modifier = Modifier.size(48.dp), tint = TextSecond)
                Spacer(Modifier.height(8.dp))
                Text(error, fontSize = 14.sp, color = TextSecond)
            }
        }
    }
}

@Composable
fun VideoPlaylistItem(file: BackupFile, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isPlaying) Accent.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.07f)),
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
                Icon(Icons.Filled.PlayCircle, null, tint = TextSecond, modifier = Modifier.size(26.dp))
            }
            if (isPlaying) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.VolumeUp, null, modifier = Modifier.size(18.dp), tint = Accent)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.fileName,
                fontSize = 12.sp,
                color = if (isPlaying) Accent else TextPrimary,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(FileUtils.formatFileSize(file.fileSize), fontSize = 10.sp, color = TextSecond)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
