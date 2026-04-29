package com.telegrambackup.ui.screens.audio

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.Playlist
import com.telegrambackup.data.local.entity.UploadStatus
import com.telegrambackup.util.FileUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// Spotify brand colors
private val SpotifyGreen = Color(0xFF1DB954)
private val SpotifyDark = Color(0xFF121212)
private val SpotifyCard = Color(0xFF282828)
private val SpotifyGray = Color(0xFFB3B3B3)

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
    var showFullPlayer by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Add-to-playlist sheet
    var fileToAddToPlaylist by remember { mutableStateOf<BackupFile?>(null) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Single ExoPlayer instance to avoid conflicts
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var currentPlaying by remember { mutableStateOf<BackupFile?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var audioDuration by remember { mutableLongStateOf(0L) }
    var isShuffled by remember { mutableStateOf(false) }
    var isRepeating by remember { mutableStateOf(false) }

    // Release player when screen leaves
    DisposableEffect(Unit) {
        onDispose { exoPlayer?.release() }
    }

    // Update position every 500ms while playing
    LaunchedEffect(isPlaying, exoPlayer) {
        while (true) {
            exoPlayer?.let { p ->
                currentPosition = p.currentPosition.coerceAtLeast(0L)
                audioDuration = p.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    // Helper to play audio - always releases previous player first to avoid conflicts
    fun playAudioFile(file: BackupFile) {
        exoPlayer?.release() // STOP and free old player before creating new one
        val player = ExoPlayer.Builder(context).build()
        val uri = if (File(file.filePath).exists()) {
            Uri.fromFile(File(file.filePath))
        } else {
            // File deleted locally but exists in cloud — can't play without download
            return
        }
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
        if (isRepeating) player.repeatMode = Player.REPEAT_MODE_ONE
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && !isRepeating) {
                    // Auto-next
                    val list = uiState.audioFiles
                    val idx = list.indexOfFirst { it.id == file.id }
                    val next = if (isShuffled) list.randomOrNull()
                    else list.getOrNull(idx + 1)
                    next?.let { playAudioFile(it) }
                }
            }
        })
        exoPlayer = player
        currentPlaying = file
        isPlaying = true
    }

    // Full Spotify-style player
    if (showFullPlayer && currentPlaying != null) {
        SpotifyFullPlayer(
            file = currentPlaying!!,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = audioDuration,
            isShuffled = isShuffled,
            isRepeating = isRepeating,
            allFiles = uiState.audioFiles,
            onClose = { showFullPlayer = false },
            onPlayPause = {
                exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
            },
            onSeek = { pos -> exoPlayer?.seekTo(pos) },
            onNext = {
                val list = uiState.audioFiles
                val idx = list.indexOfFirst { it.id == currentPlaying?.id }
                list.getOrNull(idx + 1)?.let { playAudioFile(it) }
            },
            onPrevious = {
                val list = uiState.audioFiles
                val idx = list.indexOfFirst { it.id == currentPlaying?.id }
                if (currentPosition > 3000L) {
                    exoPlayer?.seekTo(0)
                } else {
                    list.getOrNull(idx - 1)?.let { playAudioFile(it) }
                }
            },
            onShuffleToggle = { isShuffled = !isShuffled },
            onRepeatToggle = {
                isRepeating = !isRepeating
                exoPlayer?.repeatMode = if (isRepeating) Player.REPEAT_MODE_ONE
                else Player.REPEAT_MODE_OFF
            },
            onSelectSong = { file ->
                playAudioFile(file)
            }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Música",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                actions = {
                    IconButton(onClick = { showCreatePlaylist = true }) {
                        Icon(Icons.Outlined.Add, "Crear playlist")
                    }
                }
            )
        },
        bottomBar = {
            // Mini Spotify player
            AnimatedVisibility(
                visible = currentPlaying != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                currentPlaying?.let { file ->
                    SpotifyMiniPlayer(
                        file = file,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = audioDuration,
                        onExpand = { showFullPlayer = true },
                        onPlayPause = {
                            exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
                        },
                        onNext = {
                            val list = uiState.audioFiles
                            val idx = list.indexOfFirst { it.id == file.id }
                            list.getOrNull(idx + 1)?.let { playAudioFile(it) }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Todas", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Playlists", modifier = Modifier.padding(12.dp))
                }
            }

            when (selectedTab) {
                0 -> {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Buscar canción…", color = SpotifyGray) },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, null, tint = SpotifyGray, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, "Limpiar", tint = SpotifyGray, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotifyGreen,
                            unfocusedBorderColor = SpotifyGray.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = SpotifyGreen,
                            focusedContainerColor = SpotifyCard,
                            unfocusedContainerColor = SpotifyCard
                        )
                    )

                    val filteredAudio = if (searchQuery.isBlank()) uiState.audioFiles
                    else uiState.audioFiles.filter {
                        it.fileName.contains(searchQuery, ignoreCase = true)
                    }

                    if (filteredAudio.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    if (searchQuery.isBlank()) Icons.Outlined.MusicNote else Icons.Filled.Search,
                                    null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    if (searchQuery.isBlank()) "No hay archivos de audio"
                                    else "Sin resultados para \"$searchQuery\"",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (searchQuery.isBlank()) {
                                    Text(
                                        "Escanea tu dispositivo para encontrar música",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                            items(filteredAudio, key = { it.id }) { file ->
                                SpotifyAudioListItem(
                                    file = file,
                                    isPlaying = currentPlaying?.id == file.id && isPlaying,
                                    onClick = {
                                        playAudioFile(file)
                                        showFullPlayer = true
                                    },
                                    onAddToPlaylist = {
                                        fileToAddToPlaylist = file
                                        showAddToPlaylistSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.playlists, key = { it.id }) { playlist ->
                            PlaylistCard(
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

    // ── Add-to-playlist bottom sheet ──────────────────────────────────────────
    if (showAddToPlaylistSheet && fileToAddToPlaylist != null) {
        val songFile = fileToAddToPlaylist!!
        ModalBottomSheet(
            onDismissRequest = {
                showAddToPlaylistSheet = false
                fileToAddToPlaylist = null
            },
            containerColor = SpotifyCard
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(SpotifyGreen, Color(0xFF0D6B31))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            songFile.fileName.substringBeforeLast("."),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Agregar a lista de reproducción",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpotifyGray
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )
                Spacer(Modifier.height(8.dp))

                if (uiState.playlists.isEmpty()) {
                    // No playlists yet — prompt to create one
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.PlaylistAdd,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = SpotifyGray
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Aún no tienes playlists",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Crea una para empezar a organizar tu música",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyGray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                showAddToPlaylistSheet = false
                                showCreatePlaylist = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Crear playlist", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Option to create new playlist at top
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddToPlaylistSheet = false
                                showCreatePlaylist = true
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.07f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, null, tint = SpotifyGreen, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "Nueva lista de reproducción",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = Color.White.copy(alpha = 0.07f)
                    )

                    // Existing playlists
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(uiState.playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addToPlaylist(playlist.id, songFile.id)
                                        showAddToPlaylistSheet = false
                                        fileToAddToPlaylist = null
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                "\"${songFile.fileName.substringBeforeLast(".")}\" agregado a ${playlist.name}"
                                            )
                                        }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    SpotifyGreen.copy(alpha = 0.6f),
                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.QueueMusic,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylist) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("Nueva Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Nombre de la playlist") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (playlistName.isNotBlank()) {
                        viewModel.createPlaylist(playlistName)
                        showCreatePlaylist = false
                    }
                }) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancelar") }
            }
        )
    }
}

// ─── Spotify Full Screen Player ──────────────────────────────────────────────

@Composable
fun SpotifyFullPlayer(
    file: BackupFile,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isShuffled: Boolean,
    isRepeating: Boolean,
    allFiles: List<BackupFile>,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onSelectSong: (BackupFile) -> Unit
) {
    var showSongList by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        "Minimizar",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    "Reproduciendo ahora",
                    style = MaterialTheme.typography.labelMedium,
                    color = SpotifyGray,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { showSongList = !showSongList }) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        "Lista de canciones",
                        tint = if (showSongList) SpotifyGreen else Color.White
                    )
                }
            }

            if (showSongList) {
                // Song list panel inside the player
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Text(
                        "Cola de reproducción",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(allFiles, key = { it.id }) { song ->
                            SongQueueItem(
                                file = song,
                                isCurrentSong = song.id == file.id,
                                onClick = { onSelectSong(song) }
                            )
                        }
                    }
                }
            } else {
                // Album art + controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(24.dp))

                    // Animated album art (gradient circle)
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        SpotifyGreen.copy(alpha = 0.8f),
                                        Color(0xFF191414),
                                        Color(0xFF0A0A0A)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            null,
                            modifier = Modifier.size(100.dp),
                            tint = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    Spacer(Modifier.height(32.dp))

                    // Song title + cloud badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                file.fileName.substringBeforeLast("."),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                FileUtils.formatFileSize(file.fileSize),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SpotifyGray
                            )
                        }
                        if (file.uploadStatus == UploadStatus.UPLOADED) {
                            Icon(
                                Icons.Filled.CloudDone,
                                "Subido a la nube",
                                modifier = Modifier.size(22.dp),
                                tint = SpotifyGreen
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Progress bar
                    Column(modifier = Modifier.padding(horizontal = 32.dp)) {
                        Slider(
                            value = currentPosition.toFloat()
                                .coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                            onValueChange = { onSeek(it.toLong()) },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatAudioDuration(currentPosition),
                                style = MaterialTheme.typography.labelSmall,
                                color = SpotifyGray
                            )
                            Text(
                                formatAudioDuration(duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = SpotifyGray
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Shuffle + Prev + Play/Pause + Next + Repeat
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onShuffleToggle) {
                            Icon(
                                Icons.Filled.Shuffle,
                                "Aleatorio",
                                tint = if (isShuffled) SpotifyGreen else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = onPrevious,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                "Anterior",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { onPlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (isPlaying) "Pausar" else "Reproducir",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        IconButton(
                            onClick = onNext,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.SkipNext,
                                "Siguiente",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        IconButton(onClick = onRepeatToggle) {
                            Icon(
                                if (isRepeating) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                "Repetir",
                                tint = if (isRepeating) SpotifyGreen else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ─── Song Queue Item (inside full player) ─────────────────────────────────────

@Composable
fun SongQueueItem(
    file: BackupFile,
    isCurrentSong: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrentSong) SpotifyGreen.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SpotifyCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isCurrentSong) Icons.Filled.MusicNote else Icons.Outlined.MusicNote,
                null,
                tint = if (isCurrentSong) SpotifyGreen else SpotifyGray,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.fileName.substringBeforeLast("."),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrentSong) SpotifyGreen else Color.White,
                fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                FileUtils.formatFileSize(file.fileSize),
                style = MaterialTheme.typography.labelSmall,
                color = SpotifyGray
            )
        }
        if (file.uploadStatus == UploadStatus.UPLOADED) {
            Icon(
                Icons.Filled.CloudDone,
                null,
                modifier = Modifier.size(14.dp),
                tint = SpotifyGreen.copy(alpha = 0.6f)
            )
            Spacer(Modifier.width(4.dp))
        }
        if (isCurrentSong) {
            Icon(
                Icons.Filled.Equalizer,
                null,
                modifier = Modifier.size(18.dp),
                tint = SpotifyGreen
            )
        }
    }
}

// ─── Spotify Mini Player ──────────────────────────────────────────────────────

@Composable
fun SpotifyMiniPlayer(
    file: BackupFile,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = SpotifyCard
    ) {
        Column {
            // Thin progress bar at top of mini player
            LinearProgressIndicator(
                progress = {
                    if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    else 0f
                },
                modifier = Modifier.fillMaxWidth(),
                color = SpotifyGreen,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(SpotifyGreen, Color(0xFF0D6B31))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.fileName.substringBeforeLast("."),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatAudioDuration(currentPosition) + " / " + formatAudioDuration(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGray
                    )
                }

                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        "Siguiente",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ─── Song list item ───────────────────────────────────────────────────────────

@Composable
fun SpotifyAudioListItem(
    file: BackupFile,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isPlaying) SpotifyGreen.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        colors = if (isPlaying)
                            listOf(SpotifyGreen, Color(0xFF0D6B31))
                        else
                            listOf(
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
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.fileName.substringBeforeLast("."),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isPlaying) SpotifyGreen else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    FileUtils.formatFileSize(file.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (file.uploadStatus == UploadStatus.UPLOADED) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.CloudDone,
                        "Subido",
                        modifier = Modifier.size(12.dp),
                        tint = SpotifyGreen.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (isPlaying) {
            Icon(
                Icons.Filled.Equalizer,
                null,
                tint = SpotifyGreen,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(4.dp))
        }

        // Add-to-playlist button (⋮) — only shown when not in playlist detail
        onAddToPlaylist?.let { action ->
            IconButton(
                onClick = action,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    "Agregar a lista",
                    tint = SpotifyGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Remove-from-playlist button — only shown inside PlaylistDetailScreen
        onRemoveFromPlaylist?.let { action ->
            IconButton(
                onClick = action,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.RemoveCircleOutline,
                    "Quitar de playlist",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─── Playlist Card ────────────────────────────────────────────────────────────

@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SpotifyCard)
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
                            colors = listOf(SpotifyGreen, MaterialTheme.colorScheme.secondary)
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
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "Creada el ${
                        java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                            .format(playlist.createdAt)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyGray
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    "Eliminar",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ─── Playlist Detail ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    viewModel: AudioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val playlistFiles = uiState.playlistFiles[playlistId] ?: emptyList()

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var currentPlaying by remember { mutableStateOf<BackupFile?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) { viewModel.loadPlaylistFiles(playlistId) }

    DisposableEffect(Unit) { onDispose { exoPlayer?.release() } }

    fun playFile(file: BackupFile) {
        exoPlayer?.release()
        if (!File(file.filePath).exists()) return
        val player = ExoPlayer.Builder(context).build()
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(file.filePath))))
        player.prepare()
        player.play()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        })
        exoPlayer = player
        currentPlaying = file
        isPlaying = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Volver")
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
                SpotifyAudioListItem(
                    file = file,
                    isPlaying = currentPlaying?.id == file.id && isPlaying,
                    onClick = { playFile(file) },
                    onRemoveFromPlaylist = {
                        viewModel.removeFromPlaylist(playlistId, file.id)
                    }
                )
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatAudioDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
