package com.iptvplayerpro.ui.screens.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.iptvplayerpro.LocalActivity
import com.iptvplayerpro.LocalPipMode
import com.iptvplayerpro.core.util.TimeFmt
import com.iptvplayerpro.di.containerViewModel
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.player.TrackOption
import com.iptvplayerpro.ui.components.ChannelRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Reproductor a pantalla completa:
 * gestos (brillo/volumen/avance), PiP, pistas, subtítulos, calidad,
 * EPG ahora/siguiente, lista lateral de canales y reconexión automática.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channelId: Long,
    externalUrl: String,
    externalName: String,
    onBack: () -> Unit,
    onOpenVpn: () -> Unit
) {
    val viewModel = containerViewModel(PlayerViewModel::class, key = "player") { container, extras ->
        PlayerViewModel(container, extras.createSavedStateHandle())
    }
    val controller = viewModel.controller
    val uiState by controller.uiState.collectAsStateWithLifecycle()
    val tracksState by controller.tracksState.collectAsStateWithLifecycle()
    val sideChannels by viewModel.sideChannels.collectAsStateWithLifecycle()
    val sideVisible by viewModel.sideVisible.collectAsStateWithLifecycle()
    val inPip = LocalPipMode.current
    val activity = LocalActivity.current

    var controlsVisible by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var trackDialog by remember { mutableStateOf<Int?>(null) } // 0 audio, 1 texto, 2 video
    var showTechnical by remember { mutableStateOf(false) }
    var toastOverlay by remember { mutableStateOf<String?>(null) }

    // Posición/duración para la barra de avance (cuando el formato lo permite).
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isLive by remember { mutableStateOf(false) }
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }

    val audioManager = remember(activity) {
        activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    var volumeLevel by remember { mutableFloatStateOf(-1f) }
    var brightnessLevel by remember { mutableFloatStateOf(-1f) }

    // Mantener pantalla encendida durante la reproducción.
    val view = LocalView.current
    DisposableEffect(uiState.isPlaying) {
        view.keepScreenOn = uiState.isPlaying || uiState.isBuffering
        onDispose { view.keepScreenOn = false }
    }

    // Sondeo de posición cada 500 ms.
    LaunchedEffect(Unit) {
        while (isActive) {
            controller.peekPlayer()?.let { player ->
                positionMs = player.currentPosition.coerceAtLeast(0)
                durationMs = if (player.duration > 0) player.duration else 0L
                isLive = player.isCurrentMediaItemLive
            }
            delay(500)
        }
    }

    // Rotación automática según la relación de aspecto del video.
    LaunchedEffect(uiState.videoSize) {
        val size = uiState.videoSize
        if (size != null && size.width > size.height && !isFullscreen && !inPip) {
            isFullscreen = true
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // Restaurar orientación y barras del sistema al salir.
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Ocultar controles automáticamente.
    LaunchedEffect(controlsVisible, uiState.isPlaying) {
        if (controlsVisible && !inPip) {
            delay(4000)
            controlsVisible = false
        }
    }

    // Barras del sistema según pantalla completa.
    LaunchedEffect(isFullscreen, inPip) {
        val window = activity?.window ?: return@LaunchedEffect
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreen || inPip) {
            insets.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        activity?.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            val size = uiState.videoSize
            val params = PictureInPictureParams.Builder()
            if (size != null && size.width > 0 && size.height > 0) {
                runCatching { params.setAspectRatio(Rational(size.width, size.height)) }
            }
            runCatching { activity.enterPictureInPictureMode(params.build()) }
        }
    }

    fun seekBy(deltaMs: Long) {
        controller.peekPlayer()?.let { player ->
            if (durationMs > 0) {
                controller.seekTo((positionMs + deltaMs).coerceIn(0, durationMs))
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Superficie de video
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { playerView ->
                playerView.player = controller.peekPlayer()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gestos
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (!inPip) controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (!inPip) {
                                if (offset.x < size.width / 2) seekBy(-10_000) else seekBy(10_000)
                            }
                        }
                    )
                }
                .pointerInput(Unit, inPip) {
                    if (inPip) return@pointerInput
                    var verticalDrag: Int? = null // 0 brillo | 1 volumen
                    var horizontalAccum = 0f
                    var started = false
                    detectDragGestures(
                        onDragStart = { offset ->
                            started = true
                            horizontalAccum = 0f
                            verticalDrag = if (offset.x < size.width / 2) 0 else 1
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dy = dragAmount.y
                            val dx = dragAmount.x
                            if (kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                                when (verticalDrag) {
                                    0 -> { // Brillo (mitad izquierda)
                                        val current = if (brightnessLevel >= 0) brightnessLevel
                                        else activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                                        val next = (current - dy / 800f).coerceIn(0.02f, 1f)
                                        brightnessLevel = next
                                        activity?.window?.attributes = activity?.window?.attributes?.apply {
                                            screenBrightness = next
                                        }
                                        toastOverlay = "Brillo ${(next * 100).toInt()}%"
                                    }
                                    1 -> { // Volumen (mitad derecha)
                                        val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                                        val current = if (volumeLevel >= 0) volumeLevel
                                        else (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 10).toFloat()
                                        val next = (current - dy / 30f).coerceIn(0f, max.toFloat())
                                        volumeLevel = next
                                        audioManager?.setStreamVolume(
                                            AudioManager.STREAM_MUSIC, next.toInt(), 0
                                        )
                                        controller.setVolume(next / max.toFloat())
                                        toastOverlay = "Volumen ${(next.toInt() * 100 / max)}%"
                                    }
                                }
                            } else if (durationMs > 0) {
                                horizontalAccum += dx
                                if (kotlin.math.abs(horizontalAccum) > 40f) {
                                    val target = (positionMs + (horizontalAccum * 120).toLong())
                                        .coerceIn(0, durationMs)
                                    seekPreviewMs = target
                                }
                            }
                        },
                        onDragEnd = {
                            seekPreviewMs?.let { controller.seekTo(it) }
                            seekPreviewMs = null
                            verticalDrag = null
                        }
                    )
                }
        )

        // Indicador flotante (brillo/volumen/avance)
        val overlayMessage = seekPreviewMs?.let {
            "▶ ${TimeFmt.time(it)} / ${TimeFmt.time(durationMs)}"
        } ?: toastOverlay
        if (overlayMessage != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            ) {
                Text(
                    overlayMessage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Medium
                )
            }
            LaunchedEffect(overlayMessage) {
                delay(900)
                if (toastOverlay == overlayMessage) toastOverlay = null
            }
        }

        // Buffering
        if (uiState.isBuffering && uiState.issue == null && !uiState.isReconnecting) {
            CircularProgressIndicator(
                Modifier
                    .align(Alignment.Center)
                    .size(52.dp),
                color = Color.White
            )
        }

        // Reconexión automática
        if (uiState.isReconnecting) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(Modifier.size(34.dp), color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Text("Reconectando… (intento ${uiState.reconnectAttempt})")
                }
            }
        }

        // Panel de error
        val issue = uiState.issue
        if (issue != null && !uiState.isReconnecting) {
            ErrorPanel(
                title = issue.issue.title,
                bullets = issue.issue.bullets,
                technical = issue.technicalDetail,
                showTechnical = showTechnical,
                onToggleTechnical = { showTechnical = !showTechnical },
                onRetry = { viewModel.retry() },
                onVpn = onOpenVpn,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Controles
        if (!inPip) {
            AnimatedVisibility(
                visible = controlsVisible && !uiState.isReconnecting,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                PlayerControls(
                    state = uiState,
                    isFullscreen = isFullscreen,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isLive = isLive,
                    onBack = onBack,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onFullscreen = { toggleFullscreen() },
                    onPip = { enterPip() },
                    onFavorite = { viewModel.toggleFavorite() },
                    onChannels = { viewModel.toggleSidePanel() },
                    onAudio = { trackDialog = 0 },
                    onSubtitles = { trackDialog = 1 },
                    onQuality = { trackDialog = 2 },
                    onSeek = { viewModel.controller.seekTo(it) }
                )
            }

            // Lista lateral de canales (zapeo rápido)
            AnimatedVisibility(
                visible = sideVisible,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                ChannelSidePanel(
                    channels = sideChannels,
                    currentId = uiState.channel?.id,
                    onSelect = { viewModel.switchChannel(it) },
                    onClose = { viewModel.toggleSidePanel() }
                )
            }
        }
    }

    // Diálogos de pistas
    when (trackDialog) {
        0 -> TrackDialog(
            title = "Pista de audio",
            options = tracksState.audio,
            allowAuto = true,
            onSelect = { viewModel.selectTrack(it) },
            onAuto = { viewModel.clearTrackOverride(androidx.media3.common.C.TRACK_TYPE_AUDIO) },
            onDismiss = { trackDialog = null }
        )
        1 -> SubtitleDialog(
            enabled = tracksState.subtitleEnabled,
            options = tracksState.text,
            onToggle = { viewModel.setSubtitlesEnabled(it) },
            onSelect = { viewModel.selectTrack(it) },
            onDismiss = { trackDialog = null }
        )
        2 -> TrackDialog(
            title = "Calidad",
            options = tracksState.video,
            allowAuto = true,
            onSelect = { viewModel.selectTrack(it) },
            onAuto = { viewModel.clearTrackOverride(androidx.media3.common.C.TRACK_TYPE_VIDEO) },
            onDismiss = { trackDialog = null }
        )
    }
}

// ------------------------------------------------------------------ controles

@Composable
private fun PlayerControls(
    state: com.iptvplayerpro.player.PlayerUiState,
    isFullscreen: Boolean,
    positionMs: Long,
    durationMs: Long,
    isLive: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onFullscreen: () -> Unit,
    onPip: () -> Unit,
    onFavorite: () -> Unit,
    onChannels: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onQuality: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.65f),
                    0.12f to Color.Transparent,
                    0.85f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.75f)
                )
            )
    ) {
        // Superior
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    state.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                state.channel?.categoryName?.let {
                    Text(
                        it,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (state.channel?.isFavorite == true) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    "Favorito",
                    tint = if (state.channel?.isFavorite == true) Color(0xFFFFC107) else Color.White
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                IconButton(onClick = onPip) {
                    Icon(Icons.Filled.PictureInPictureAlt, "PiP", tint = Color.White)
                }
            }
            IconButton(onClick = onFullscreen) {
                Icon(
                    if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    "Pantalla completa",
                    tint = Color.White
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Inferior
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // EPG ahora / siguiente con barra de progreso
            state.nowNext.now?.let { now ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "AHORA",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        "${now.title} · hasta ${TimeFmt.time(now.end)}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(now.progress * 100).toInt()}%",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                LinearProgressIndicator(
                    progress = { now.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
            }
            state.nowNext.next?.let { next ->
                Text(
                    "Después (${TimeFmt.time(next.start)}): ${next.title}",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }

            // Barra de avance (solo si el formato lo permite)
            if (durationMs > 0 && !isLive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        TimeFmt.time(if (positionMs > 0) positionMs else 0),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                        onValueChange = { fraction ->
                            onSeek((fraction * durationMs).toLong())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        TimeFmt.time(durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "● EN VIVO",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // Botonera
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "Reproducir/Pausar",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = onReplay(onSeek = onSeek, positionMs = positionMs)) {
                    Icon(Icons.Filled.Replay, "Atrás 10s", tint = Color.White)
                }
                IconButton(onClick = onChannels) {
                    Icon(Icons.Filled.List, "Canales", tint = Color.White)
                }
                IconButton(onClick = onAudio) {
                    Icon(Icons.Filled.Audiotrack, "Audio", tint = Color.White)
                }
                IconButton(onClick = onSubtitles) {
                    Icon(
                        Icons.Filled.Subtitles,
                        "Subtítulos",
                        tint = if (state.subtitleEnabled) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
                IconButton(onClick = onQuality) {
                    Icon(Icons.Filled.HighQuality, "Calidad", tint = Color.White)
                }
            }
        }
    }
}

private fun onReplay(onSeek: (Long) -> Unit, positionMs: Long): () -> Unit = {
    onSeek((positionMs - 10_000).coerceAtLeast(0))
}

// ------------------------------------------------------------------ paneles

@Composable
private fun ChannelSidePanel(
    channels: List<Channel>,
    currentId: Long?,
    onSelect: (Channel) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.85f),
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Canales",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, "Cerrar", tint = Color.White)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
            ) {
                items(channels, key = { it.id }) { channel ->
                    ChannelRow(
                        channel = channel,
                        selected = channel.id == currentId,
                        onClick = { onSelect(channel) }
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorPanel(
    title: String,
    bullets: List<String>,
    technical: String?,
    showTechnical: Boolean,
    onToggleTechnical: () -> Unit,
    onRetry: () -> Unit,
    onVpn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "No se pudo reproducir el canal",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(10.dp))
            bullets.forEach { bullet ->
                Text(
                    "• $bullet",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            if (technical != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onToggleTechnical) {
                    Text(if (showTechnical) "Ocultar detalles técnicos" else "Detalles técnicos")
                }
                if (showTechnical) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            technical,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                    Text("Probar nuevamente")
                }
                OutlinedButton(onClick = onVpn, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.VpnKey, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("VPN")
                }
            }
        }
    }
}

// ------------------------------------------------------------------ diálogos

@Composable
private fun TrackDialog(
    title: String,
    options: List<TrackOption>,
    allowAuto: Boolean,
    onSelect: (TrackOption) -> Unit,
    onAuto: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (allowAuto) {
                    Text(
                        "Automático",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAuto()
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
                if (options.isEmpty()) {
                    Text(
                        "No hay pistas disponibles en este stream.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            option.label,
                            modifier = Modifier.weight(1f)
                        )
                        if (option.isSelected) {
                            Text(
                                "✓",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun SubtitleDialog(
    enabled: Boolean,
    options: List<TrackOption>,
    onToggle: (Boolean) -> Unit,
    onSelect: (TrackOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtítulos") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(!enabled) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (enabled) "Subtítulos activados" else "Subtítulos desactivados",
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (enabled) "Activado" else "Desactivado",
                        color = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (options.isEmpty() && enabled) {
                    Text(
                        "Este stream no declara pistas de subtítulos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(option.label, modifier = Modifier.weight(1f))
                        if (option.isSelected) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
