package com.telegrambackup.ui.screens.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
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
import androidx.compose.material.icons.outlined.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.data.local.entity.Playlist
import com.telegrambackup.service.AudioPlaybackService
import com.telegrambackup.util.FileUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(
    onNavigateToPlaylist: (Long) -> Unit,
    viewModel: AudioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCreatePlaylist by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // ExoPlayer for audio
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var currentPlaying by remember { mutableStateOf<BackupFile?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio") },
                actions = {
                    IconButton(onClick = { showCreatePlaylist = true }) {
                        Icon(Icons.Outlined.Add, "Create Playlist")
                    }
                }
            )
        },
        bottomBar = {
            // Mini player
            currentPlaying?.let { file ->
                MiniAudioPlayer(
                    file = file,
                    isPlaying = isPlaying,
                    onPlayPause = {
                        exoPlayer?.let { player ->
                            if (player.isPlaying) player.pause() else player.play()
                        }
                    },
                    onNext = {
                        val currentIndex = uiState.audioFiles.indexOfFirst { it.id == file.id }
                        if (currentIndex < uiState.audioFiles.size - 1) {
                            val nextFile = uiState.audioFiles[currentIndex + 1]
                            playAudio(context, nextFile) { player, playing ->
                                exoPlayer = player
                                currentPlaying = nextFile
                                isPlaying = playing
                            }
                        }
                    },
                    onPrevious = {
                        val currentIndex = uiState.audioFiles.indexOfFirst { it.id == file.id }
                        if (currentIndex > 0) {
                            val prevFile = uiState.audioFiles[currentIndex - 1]
                            playAudio(context, prevFile) { player, playing ->
                                exoPlayer = player
                                currentPlaying = prevFile
                                isPlaying = playing
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("All Music", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Playlists", modifier = Modifier.padding(12.dp))
                }
            }

            when (selectedTab) {
                0 -> {
                    // All audio files
                    if (uiState.audioFiles.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.MusicNote,
                                    null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "No audio files found",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(uiState.audioFiles, key = { it.id }) { file ->
                                AudioListItem(
                                    file = file,
                                    isPlaying = currentPlaying?.id == file.id && isPlaying,
                                    onClick = {
                                        playAudio(context, file) { player, playing ->
                                            exoPlayer = player
                                            currentPlaying = file
                                            isPlaying = playing
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Playlists
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.playlists, key = { it.id }) { playlist ->
                            PlaylistItem(
                                playlist = playlist,
                                onClick = { onNavigateToPlaylist(playlist.id) },
                                onDelete = { viewModel.deletePlaylist(playlist) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create playlist dialog
    if (showCreatePlaylist) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            viewModel.createPlaylist(playlistName)
                            showCreatePlaylist = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AudioListItem(
    file: BackupFile,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Filled.MusicNote else Icons.Outlined.MusicNote,
                null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.fileName.substringBeforeLast("."),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                FileUtils.formatFileSize(file.fileSize),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isPlaying) {
            Icon(
                Icons.Filled.Equalizer,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = { /* Add to playlist */ }) {
            Icon(
                Icons.Outlined.PlaylistAdd,
                "Add to playlist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.QueueMusic,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Created ${
                        java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                            .format(playlist.createdAt)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun MiniAudioPlayer(
    file: BackupFile,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.fileName.substringBeforeLast("."),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    FileUtils.formatFileSize(file.fileSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(20.dp))
            }

            IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    Modifier.size(28.dp)
                )
            }

            IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.SkipNext, "Next", Modifier.size(20.dp))
            }
        }
    }
}

private fun playAudio(
    context: Context,
    file: BackupFile,
    onResult: (ExoPlayer, Boolean) -> Unit
) {
    val player = ExoPlayer.Builder(context).build()
    val uri = if (File(file.filePath).exists()) {
        Uri.fromFile(File(file.filePath))
    } else {
        Uri.parse("")
    }

    player.setMediaItem(MediaItem.fromUri(uri))
    player.prepare()
    player.play()

    player.addListener(object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            onResult(player, playing)
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    viewModel: AudioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playlistFiles = uiState.playlistFiles[playlistId] ?: emptyList()

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylistFiles(playlistId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(playlistFiles, key = { it.id }) { file ->
                AudioListItem(
                    file = file,
                    isPlaying = false,
                    onClick = { /* play */ }
                )
            }
        }
    }
}
