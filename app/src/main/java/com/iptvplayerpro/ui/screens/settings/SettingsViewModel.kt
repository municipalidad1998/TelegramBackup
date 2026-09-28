package com.iptvplayerpro.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvplayerpro.core.prefs.Settings
import com.iptvplayerpro.core.prefs.SettingsStore
import com.iptvplayerpro.di.AppContainer
import com.iptvplayerpro.domain.model.ThemeMode
import com.iptvplayerpro.domain.model.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val settingsStore: SettingsStore = container.settings
    private val updateRepository = container.updateRepository

    val settings: StateFlow<Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _updateError = MutableStateFlow<String?>(null)
    val updateError: StateFlow<String?> = _updateError.asStateFlow()

    val currentVersion: String get() = updateRepository.currentVersionName

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }
    fun setDynamicColors(value: Boolean) = viewModelScope.launch { settingsStore.setDynamicColors(value) }
    fun setBuffer(min: Int, max: Int, playback: Int, rebuffer: Int) =
        viewModelScope.launch { settingsStore.setBuffer(min, max, playback, rebuffer) }
    fun setAutoRefresh(enabled: Boolean) = viewModelScope.launch { settingsStore.setAutoRefresh(enabled) }
    fun setAutoRefreshHours(hours: Int) = viewModelScope.launch { settingsStore.setAutoRefreshHours(hours) }
    fun setEpgRefreshHours(hours: Int) = viewModelScope.launch { settingsStore.setEpgRefreshHours(hours) }
    fun setAutoPip(value: Boolean) = viewModelScope.launch { settingsStore.setAutoPip(value) }
    fun setRetryCount(value: Int) = viewModelScope.launch { settingsStore.setRetryCount(value) }
    fun setUpdateUrl(value: String) = viewModelScope.launch { settingsStore.setUpdateUrl(value) }

    fun checkUpdate() {
        viewModelScope.launch {
            _checkingUpdate.value = true
            _updateError.value = null
            val url = settings.first().updateUrl
            if (url.isBlank()) {
                _updateError.value = "Configura primero la URL de actualizaciones."
            } else {
                updateRepository.checkForUpdate(url).fold(
                    onSuccess = { info ->
                        _updateInfo.value = info
                        if (info == null) _updateError.value = "La fuente no devolvió información."
                    },
                    onFailure = { e -> _updateError.value = e.message ?: "Error al consultar." }
                )
            }
            _checkingUpdate.value = false
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        viewModelScope.launch { updateRepository.downloadAndInstall(info) }
    }

    fun installDownloaded(info: UpdateInfo): Boolean =
        (updateRepository as com.iptvplayerpro.data.repository.UpdateRepositoryImpl)
            .installDownloadedApk(info.newVersionName)
}
