package com.iptvplayerpro.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.iptvplayerpro.core.prefs.Settings
import com.iptvplayerpro.core.prefs.SettingsStore
import com.iptvplayerpro.data.remote.Http
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.domain.model.NowNext
import com.iptvplayerpro.domain.model.PlaybackIssueInfo
import com.iptvplayerpro.domain.model.Playlist
import com.iptvplayerpro.domain.repository.ChannelRepository
import com.iptvplayerpro.domain.repository.EpgRepository
import com.iptvplayerpro.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Estado observable del reproductor. */
data class PlayerUiState(
    val channel: Channel? = null,
    val playlist: Playlist? = null,
    val title: String = "",
    val nowNext: NowNext = NowNext(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val videoSize: VideoSize? = null,
    val issue: PlaybackIssueInfo? = null,
    val isReconnecting: Boolean = false,
    val reconnectAttempt: Int = 0,
    val subtitleEnabled: Boolean = false
)

/** Opción de pista (audio/subtítulo/calidad). */
data class TrackOption(
    val type: Int, // C.TRACK_TYPE_*
    val id: String, // "group:track"
    val label: String,
    val isSelected: Boolean,
    val isAdaptive: Boolean = false
)

data class TracksState(
    val audio: List<TrackOption> = emptyList(),
    val text: List<TrackOption> = emptyList(),
    val video: List<TrackOption> = emptyList(),
    val subtitleEnabled: Boolean = false
)

/**
 * Controlador central del reproductor (Media3/ExoPlayer).
 *
 * - HLS, MPEG-TS y formatos estándar vía DefaultMediaSourceFactory.
 * - Buffer configurable desde Ajustes.
 * - Reconexión automática con retroceso exponencial.
 * - Selección de pista de audio, subtítulos y calidad.
 * - Cambio rápido de canal (mismo ExoPlayer, nuevo MediaItem).
 */
@OptIn(UnstableApi::class)
class PlayerController(
    private val appContext: Context,
    private val channelRepository: ChannelRepository,
    private val playlistRepository: PlaylistRepository,
    private val epgRepository: EpgRepository,
    private val settingsStore: SettingsStore,
    private val okhttp: OkHttpClient
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Estados expuestos a la UI y a MainActivity (para PiP).
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _tracksState = MutableStateFlow(TracksState())
    val tracksState: StateFlow<TracksState> = _tracksState.asStateFlow()

    /** Pantalla del reproductor visible (para auto-PiP al salir de la app). */
    val playerScreenActive = MutableStateFlow(false)

    private var settings: Settings = Settings()
    private var currentJob: Job? = null
    private var epgJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var pendingChannel: Channel? = null
    private var pendingUrl: String? = null

    val hasContent: Boolean get() = _uiState.value.channel != null || pendingUrl != null

    // ---------------------------------------------------------------- player

    @Volatile
    private var playerRef: ExoPlayer? = null

    val player: ExoPlayer
        get() = playerRef ?: createPlayer().also { playerRef = it }

    /** Player actual sin crear uno nuevo (para sondeos de la UI). */
    fun peekPlayer(): ExoPlayer? = playerRef

    private fun createPlayer(): ExoPlayer {
        val s = settings
        val minBuffer = maxOf(s.minBufferMs, s.bufferForPlaybackMs + 500)
        val maxBuffer = maxOf(s.maxBufferMs, minBuffer)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBuffer,
                maxBuffer,
                s.bufferForPlaybackMs,
                maxOf(s.rebufferMs, s.bufferForPlaybackMs)
            )
            .build()

        val httpFactory = OkHttpDataSource.Factory(okhttp)
            .setUserAgent(Http.USER_AGENT)
        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)
        val trackSelector = DefaultTrackSelector(appContext)

        val p = ExoPlayer.Builder(appContext)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        p.addListener(playerListener)
        return p
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            if (isPlaying) {
                reconnectAttempts = 0
                _uiState.value = _uiState.value.copy(isReconnecting = false, issue = null)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.value = _uiState.value.copy(
                isBuffering = playbackState == Player.STATE_BUFFERING
            )
            if (playbackState == Player.STATE_READY) {
                refreshTracks()
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _uiState.value = _uiState.value.copy(videoSize = videoSize)
        }

        override fun onPlayerError(error: PlaybackException) {
            val playlist = _uiState.value.playlist
            val info = PlaybackErrorClassifier.classify(error, playlist)
            _uiState.value = _uiState.value.copy(issue = info)
            if (info.issue.retryable && reconnectAttempts < settings.retryCount) {
                scheduleReconnect()
            } else {
                _uiState.value = _uiState.value.copy(isReconnecting = false)
            }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            refreshTracks()
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectAttempts++
        _uiState.value = _uiState.value.copy(
            isReconnecting = true,
            reconnectAttempt = reconnectAttempts
        )
        reconnectJob = scope.launch {
            delay(1500L * reconnectAttempts.coerceAtMost(5))
            val p = playerRef ?: return@launch
            if (_uiState.value.isReconnecting) {
                mainHandler.post {
                    p.prepare()
                    p.playWhenReady = true
                }
            }
        }
    }

    /** Reconexión manual desde la UI. */
    fun retry() {
        reconnectAttempts = 0
        _uiState.value = _uiState.value.copy(isReconnecting = false, issue = null)
        val p = playerRef ?: return
        mainHandler.post {
            p.prepare()
            p.playWhenReady = true
        }
    }

    // ---------------------------------------------------------------- reproducción

    /** Reproduce un canal de la base de datos por id. */
    fun playChannel(channelId: Long) {
        currentJob?.cancel()
        currentJob = scope.launch {
            val channel = channelRepository.getChannel(channelId) ?: return@launch
            if (channel.type == ChannelType.SERIES && channel.xtreamStreamId != null) {
                // Las series Xtream se abren desde su detalle; aquí no hay stream directo.
                return@launch
            }
            val playlist = playlistRepository.getPlaylist(channel.playlistId)
            _uiState.value = _uiState.value.copy(channel = channel, playlist = playlist, title = channel.name)
            setMedia(channel.streamUrl)
            channelRepository.recordWatch(channel.id)
            observeEpg(channel)
        }
    }

    /** Reproduce una URL directa (p. ej. episodio de serie Xtream). */
    fun playExternal(title: String, url: String, channel: Channel? = null) {
        currentJob?.cancel()
        scope.launch {
            _uiState.value = _uiState.value.copy(
                channel = channel,
                playlist = channel?.let { playlistRepository.getPlaylist(it.playlistId) },
                title = title
            )
            pendingUrl = url
            setMedia(url)
            channel?.let { channelRepository.recordWatch(it.id) }
            observeEpg(channel)
        }
    }

    private fun setMedia(url: String) {
        val p = player
        val mime = when {
            url.contains(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
            url.contains(".mpd", true) -> MimeTypes.APPLICATION_MPD
            url.contains(".ts", true) -> MimeTypes.VIDEO_MP2T
            url.contains(".mp4", true) || url.contains(".m4v", true) -> MimeTypes.VIDEO_MP4
            url.contains(".mkv", true) -> MimeTypes.VIDEO_MATROSKA
            url.contains(".webm", true) -> MimeTypes.VIDEO_WEBM
            url.contains(".aac", true) -> MimeTypes.AUDIO_AAC
            else -> null
        }
        val item = MediaItem.Builder()
            .setUri(url)
            .setMimeType(mime)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(_uiState.value.title).build())
            .build()
        mainHandler.post {
            reconnectAttempts = 0
            p.setMediaItem(item, 0L)
            p.prepare()
            p.playWhenReady = true
        }
    }

    private fun observeEpg(channel: Channel?) {
        epgJob?.cancel()
        if (channel == null) {
            _uiState.value = _uiState.value.copy(nowNext = NowNext())
            return
        }
        epgJob = scope.launch {
            epgRepository.observeNowNext(channel.epgChannelId, channel.name).collect { nowNext ->
                _uiState.value = _uiState.value.copy(nowNext = nowNext)
            }
        }
    }

    fun toggleFavorite() {
        val channel = _uiState.value.channel ?: return
        scope.launch {
            val newFav = !channel.isFavorite
            channelRepository.setFavorite(channel.id, newFav)
            _uiState.value = _uiState.value.copy(
                channel = channel.copy(isFavorite = newFav)
            )
        }
    }

    fun togglePlayPause() {
        val p = playerRef ?: return
        mainHandler.post { p.playWhenReady = !p.playWhenReady }
    }

    fun seekTo(positionMs: Long) {
        val p = playerRef ?: return
        mainHandler.post {
            if (p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                p.seekTo(positionMs)
            }
        }
    }

    fun setVolume(volume: Float) {
        val p = playerRef ?: return
        mainHandler.post { p.volume = volume.coerceIn(0f, 1f) }
    }

    // ---------------------------------------------------------------- pistas

    private fun refreshTracks() {
        val p = playerRef ?: return
        val tracks = p.currentTracks
        val audio = ArrayList<TrackOption>()
        val text = ArrayList<TrackOption>()
        val video = ArrayList<TrackOption>()

        for (group in tracks.groups) {
            val type = group.type
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val supported = group.isTrackSupported(i)
                if (!supported) continue
                val id = "${group.hashCode()}:$i"
                val label = when (type) {
                    C.TRACK_TYPE_AUDIO -> buildString {
                        append(format.label ?: format.language ?: "Audio")
                        format.channelCount.takeIf { it > 0 }?.let { append(" · $it ch") }
                    }
                    C.TRACK_TYPE_TEXT -> format.label ?: format.language ?: "Subtítulo"
                    C.TRACK_TYPE_VIDEO -> {
                        val h = if (format.height > 0) format.height else continue
                        "Calidad ${h}p"
                    }
                    else -> continue
                }
                val option = TrackOption(
                    type = type,
                    id = id,
                    label = label,
                    isSelected = group.isTrackSelected(i),
                    isAdaptive = group.isAdaptiveSupported()
                )
                when (type) {
                    C.TRACK_TYPE_AUDIO -> audio.add(option)
                    C.TRACK_TYPE_TEXT -> text.add(option)
                    C.TRACK_TYPE_VIDEO -> video.add(option)
                }
            }
        }

        val params = p.trackSelectionParameters
        _tracksState.value = TracksState(
            audio = audio,
            text = text,
            video = video,
            subtitleEnabled = !params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        )
        _uiState.value = _uiState.value.copy(subtitleEnabled = !params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }

    private fun applyParams(transform: TrackSelectionParameters.Builder.() -> Unit) {
        val p = playerRef ?: return
        mainHandler.post {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().apply(transform).build()
            refreshTracks()
        }
    }

    fun selectTrack(option: TrackOption) {
        val p = playerRef ?: return
        val group = p.currentTracks.groups.firstOrNull { g ->
            (0 until g.length).any { i -> "${g.hashCode()}:$i" == option.id }
        } ?: return
        val index = (0 until group.length).firstOrNull { i -> "${group.hashCode()}:$i" == option.id } ?: return
        applyParams {
            setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
        }
    }

    fun clearTrackOverride(type: Int) {
        applyParams { clearOverridesOfType(type) }
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        applyParams { setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled) }
    }

    // ---------------------------------------------------------------- ciclo de vida

    /** Carga los ajustes; se llama al abrir la pantalla del reproductor. */
    fun refreshSettings(s: Settings) {
        settings = s
        // Si el buffer cambió se aplicará la próxima vez que se cree el player.
    }

    fun releasePlayer() {
        playerRef?.let { p ->
            mainHandler.post {
                p.removeListener(playerListener)
                p.stop()
                p.release()
            }
        }
        playerRef = null
        currentJob?.cancel()
        epgJob?.cancel()
        reconnectJob?.cancel()
        playerScreenActive.value = false
    }

    fun onScreenVisible(visible: Boolean) {
        playerScreenActive.value = visible
        if (visible) {
            scope.launch { settings = settingsStore.settings.first() }
        }
    }
}
