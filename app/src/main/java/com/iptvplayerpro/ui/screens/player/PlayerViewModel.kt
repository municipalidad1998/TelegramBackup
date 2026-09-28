package com.iptvplayerpro.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvplayerpro.di.AppContainer
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.player.PlayerController
import com.iptvplayerpro.player.TrackOption
import com.iptvplayerpro.player.TracksState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del reproductor: envuelve [PlayerController] (única instancia de
 * ExoPlayer) y alimenta la lista lateral de canales para el zapeo rápido.
 */
class PlayerViewModel(
    container: AppContainer,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val controller: PlayerController = container.playerController
    private val channelRepository = container.channelRepository

    val uiState = controller.uiState
    val tracksState: StateFlow<TracksState> = controller.tracksState

    private val _sideChannels = MutableStateFlow<List<Channel>>(emptyList())
    val sideChannels: StateFlow<List<Channel>> = _sideChannels.asStateFlow()

    private val _sideVisible = MutableStateFlow(false)
    val sideVisible: StateFlow<Boolean> = _sideVisible.asStateFlow()

    init {
        val channelId = savedStateHandle.get<Long>("channelId") ?: -1L
        val url = savedStateHandle.get<String>("url").orEmpty()
        val name = savedStateHandle.get<String>("name").orEmpty()

        if (channelId > 0) {
            controller.playChannel(channelId)
            loadSideChannels(channelId)
        } else if (url.isNotBlank()) {
            controller.playExternal(name.ifBlank { "Reproduciendo" }, url)
        }
        controller.onScreenVisible(true)
    }

    private fun loadSideChannels(channelId: Long) {
        viewModelScope.launch {
            val channel = channelRepository.getChannel(channelId) ?: return@launch
            _sideChannels.value = runCatching {
                channelRepository.playlistChannels(channel.playlistId, channel.type, 400)
            }.getOrDefault(emptyList())
        }
    }

    fun toggleSidePanel() {
        _sideVisible.value = !_sideVisible.value
    }

    fun switchChannel(channel: Channel) {
        controller.playChannel(channel.id)
        _sideVisible.value = false
    }

    fun toggleFavorite() = controller.toggleFavorite()
    fun retry() = controller.retry()
    fun togglePlayPause() = controller.togglePlayPause()
    fun selectTrack(option: TrackOption) = controller.selectTrack(option)
    fun clearTrackOverride(type: Int) = controller.clearTrackOverride(type)
    fun setSubtitlesEnabled(enabled: Boolean) = controller.setSubtitlesEnabled(enabled)

    override fun onCleared() {
        controller.onScreenVisible(false)
        controller.releasePlayer()
        super.onCleared()
    }
}
