package com.telegrambackup.ui.screens.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.data.local.entity.Playlist
import com.telegrambackup.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AudioUiState(
    val audioFiles: List<BackupFile> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val playlistFiles: Map<Long, List<BackupFile>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AudioViewModel @Inject constructor(
    private val repository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioUiState())
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()

    init {
        loadAudioFiles()
        loadPlaylists()
    }

    private fun loadAudioFiles() {
        viewModelScope.launch {
            repository.getFilesByType(FileType.AUDIO).collect { files ->
                _uiState.value = _uiState.value.copy(audioFiles = files)
            }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            repository.getAllPlaylists().collect { playlists ->
                _uiState.value = _uiState.value.copy(playlists = playlists)
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun loadPlaylistFiles(playlistId: Long) {
        viewModelScope.launch {
            repository.getFilesForPlaylist(playlistId).collect { files ->
                _uiState.value = _uiState.value.copy(
                    playlistFiles = _uiState.value.playlistFiles + (playlistId to files)
                )
            }
        }
    }

    fun addToPlaylist(playlistId: Long, fileId: Long) {
        viewModelScope.launch {
            repository.addToPlaylist(playlistId, fileId)
        }
    }

    fun removeFromPlaylist(playlistId: Long, fileId: Long) {
        viewModelScope.launch {
            repository.removeFromPlaylist(playlistId, fileId)
        }
    }
}
