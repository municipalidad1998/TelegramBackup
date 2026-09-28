package com.iptvplayerpro.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvplayerpro.di.AppContainer
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.domain.model.Playlist
import com.iptvplayerpro.domain.model.UpdateInfo
import com.iptvplayerpro.domain.model.VpnConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la pantalla de inicio. */
data class HomeUiState(
    val defaultPlaylist: Playlist? = null,
    val recents: List<Channel> = emptyList(),
    val totalChannels: Int = 0,
    val updateInfo: UpdateInfo? = null,
    val vpnState: VpnConnectionState = VpnConnectionState.DISCONNECTED
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val playlistRepository = container.playlistRepository
    private val channelRepository = container.channelRepository
    private val updateRepository = container.updateRepository
    private val settingsStore = container.settings
    private val vpnController = container.vpnController

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combineStates()

    init {
        viewModelScope.launch { checkForUpdate() }
    }

    private fun combineStates(): StateFlow<HomeUiState> {
        val playlists = playlistRepository.observePlaylists()
        val recents = channelRepository.recents(12)
        return kotlinx.coroutines.flow.combine(
            playlists,
            recents,
            _updateInfo,
            vpnController.state
        ) { lists, recent, update, vpn ->
            HomeUiState(
                defaultPlaylist = lists.firstOrNull { it.isDefault } ?: lists.firstOrNull(),
                recents = recent,
                totalChannels = lists.sumOf { it.channelCount },
                updateInfo = update,
                vpnState = vpn
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
    }

    private suspend fun checkForUpdate() {
        runCatching {
            val url = settingsStore.settings.first().updateUrl
            if (url.isBlank()) return
            updateRepository.checkForUpdate(url).onSuccess { info ->
                if (info?.isUpdateAvailable == true) _updateInfo.value = info
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }

    fun downloadUpdate(info: UpdateInfo) {
        viewModelScope.launch {
            updateRepository.downloadAndInstall(info)
        }
    }

    fun installIfDownloaded(info: UpdateInfo): Boolean =
        (updateRepository as com.iptvplayerpro.data.repository.UpdateRepositoryImpl)
            .installDownloadedApk(info.newVersionName)
}
