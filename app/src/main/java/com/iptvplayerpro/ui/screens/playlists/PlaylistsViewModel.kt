package com.iptvplayerpro.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvplayerpro.di.AppContainer
import com.iptvplayerpro.domain.model.Playlist
import com.iptvplayerpro.domain.model.TestConnectionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado por lista en la pantalla «Mis listas». */
data class PlaylistItem(
    val playlist: Playlist,
    val isRefreshing: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: TestConnectionResult? = null
)

class PlaylistsViewModel(private val container: AppContainer) : ViewModel() {

    private val playlistRepository = container.playlistRepository

    private val extras = MutableStateFlow<Map<Long, PlaylistItem>>(emptyMap())

    val items: StateFlow<List<PlaylistItem>> = combine(
        playlistRepository.observePlaylists(),
        extras
    ) { lists, extraMap ->
        lists.map { playlist ->
            extraMap[playlist.id]?.copy(playlist = playlist)
                ?: PlaylistItem(playlist)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh(id: Long) {
        viewModelScope.launch {
            updateExtra(id) { it.copy(isRefreshing = true) }
            val result = playlistRepository.refresh(id)
            updateExtra(id) { extra ->
                extra.copy(
                    isRefreshing = false,
                    playlist = result.getOrNull() ?: extra.playlist,
                    // El error queda reflejado en el estado de la lista (lastError).
                )
            }
        }
    }

    fun testConnection(id: Long) {
        viewModelScope.launch {
            updateExtra(id) { it.copy(isTesting = true) }
            val result = playlistRepository.testConnection(id)
            updateExtra(id) { it.copy(isTesting = false, testResult = result) }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { playlistRepository.delete(id) }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch { playlistRepository.setDefault(id) }
    }

    fun setAutoRefresh(playlist: Playlist, enabled: Boolean) {
        viewModelScope.launch {
            container.database.playlistDao().setAutoRefresh(playlist.id, enabled)
        }
    }

    private fun updateExtra(id: Long, transform: (PlaylistItem) -> PlaylistItem) {
        extras.value = extras.value.toMutableMap().apply {
            val current = this[id] ?: PlaylistItem(Playlist(id = id, name = ""))
            put(id, transform(current))
        }
    }
}
