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
            _uiState.value = _uiState.value.copy(isLoading = true)

            val file = repository.getFileById(fileId)
            if (file == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "File not found")
                return@launch
            }

            _uiState.value = _uiState.value.copy(currentFile = file)

            // Initialize ExoPlayer on main thread
            viewModelScope.launch(Dispatchers.Main) {
                initExoPlayer(file)
            }
        }
    }

    private suspend fun initExoPlayer(file: BackupFile) {
        val context = getApplication<Application>()

        exoPlayer?.release()

        exoPlayer = ExoPlayer.Builder(context).build().apply {
            val mediaItem = if (File(file.filePath).exists()) {
                MediaItem.fromUri(Uri.fromFile(File(file.filePath)))
            } else {
                // Try to use a content URI or placeholder
                MediaItem.fromUri(Uri.parse("https://example.com/placeholder"))
            }

            setMediaItem(mediaItem)
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
                                duration = exoPlayer?.duration ?: 0L
                            )
                            startPositionUpdates()
                        }
                        Player.STATE_ENDED -> {
                            playNext()
                        }
                        Player.STATE_BUFFERING -> {
                            _uiState.value = _uiState.value.copy(isLoading = true)
                        }
                        Player.STATE_IDLE -> {}
                    }
                }
            })

            play()
        }

        _uiState.value = _uiState.value.copy(exoPlayer = exoPlayer)
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    _uiState.value = _uiState.value.copy(
                        currentPosition = player.currentPosition,
                        duration = player.duration.coerceAtLeast(0)
                    )
                }
                delay(500)
            }
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) player.pause()
            else player.play()
        }
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
        val currentIndex = playlist.indexOfFirst { it.id == currentId }
        if (currentIndex < playlist.size - 1) {
            loadVideo(playlist[currentIndex + 1].id)
        }
    }

    fun playPrevious() {
        val currentId = _uiState.value.currentFile?.id ?: return
        val playlist = _uiState.value.playlist
        val currentIndex = playlist.indexOfFirst { it.id == currentId }
        if (currentIndex > 0) {
            loadVideo(playlist[currentIndex - 1].id)
        }
    }

    fun toggleMiniPlayer() {
        // Mini-player mode: continue playback in a floating window
        // This is handled by the service binding
    }

    override fun onCleared() {
        exoPlayer?.release()
        exoPlayer = null
        super.onCleared()
    }
}
