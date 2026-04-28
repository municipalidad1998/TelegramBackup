package com.telegrambackup.ui.screens.video

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class VideoUiState(
    val isLoading: Boolean = false,
    val currentFile: BackupFile? = null,
    val playlist: List<BackupFile> = emptyList(),
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val exoPlayer: ExoPlayer? = null,
    val error: String? = null
)

@HiltViewModel
class VideoViewModel @Inject constructor(
    application: Application,
    private val repository: BackupRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VideoUiState())
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var positionUpdateJob: Job? = null

    init {
        loadVideoPlaylist()
    }

    private fun loadVideoPlaylist() {
        viewModelScope.launch {
            repository.getFilesByType(FileType.VIDEO).collect { videos ->
                _uiState.value = _uiState.value.copy(playlist = videos)
            }
        }
    }

    fun loadVideo(fileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                currentPosition = 0L,
                duration = 0L,
                isPlaying = false
            )

            val file = repository.getFileById(fileId)
            if (file == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Archivo no encontrado")
                return@launch
            }

            _uiState.value = _uiState.value.copy(currentFile = file)

            viewModelScope.launch(Dispatchers.Main) {
                initExoPlayer(file)
            }
        }
    }

    private fun initExoPlayer(file: BackupFile) {
        // Cancel stale position polling from the previous video
        positionUpdateJob?.cancel()
        positionUpdateJob = null

        val context = getApplication<Application>()
        exoPlayer?.release()

        if (!File(file.filePath).exists()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "El archivo no existe en este dispositivo"
            )
            return
        }

        exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(file.filePath))))
            prepare()

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                duration = this@apply.duration.coerceAtLeast(0L)
                            )
                            startPositionUpdates()
                        }
                        Player.STATE_ENDED -> playNext()
                        Player.STATE_BUFFERING -> _uiState.value = _uiState.value.copy(isLoading = true)
                        Player.STATE_IDLE -> {}
                    }
                }
            })

            play()
        }

        _uiState.value = _uiState.value.copy(exoPlayer = exoPlayer)
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    _uiState.value = _uiState.value.copy(
                        currentPosition = player.currentPosition,
                        duration = player.duration.coerceAtLeast(0L)
                    )
                }
                delay(500)
            }
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun pausePlayback() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun seekRelative(deltaMs: Long) {
        exoPlayer?.let { player ->
            val newPos = (player.currentPosition + deltaMs).coerceIn(0, player.duration)
            player.seekTo(newPos)
        }
    }

    fun playNext() {
        val currentId = _uiState.value.currentFile?.id ?: return
        val playlist = _uiState.value.playlist
        val idx = playlist.indexOfFirst { it.id == currentId }
        if (idx < playlist.size - 1) loadVideo(playlist[idx + 1].id)
    }

    fun playPrevious() {
        val currentId = _uiState.value.currentFile?.id ?: return
        val playlist = _uiState.value.playlist
        val idx = playlist.indexOfFirst { it.id == currentId }
        if (idx > 0) loadVideo(playlist[idx - 1].id)
    }

    override fun onCleared() {
        positionUpdateJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        super.onCleared()
    }
}
