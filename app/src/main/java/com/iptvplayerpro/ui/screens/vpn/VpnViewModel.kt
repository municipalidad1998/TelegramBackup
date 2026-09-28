package com.iptvplayerpro.ui.screens.vpn

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvplayerpro.di.AppContainer
import com.iptvplayerpro.domain.model.PublicIpInfo
import com.iptvplayerpro.domain.model.VpnConnectionState
import com.iptvplayerpro.domain.model.VpnProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VpnUiState(
    val ipInfo: PublicIpInfo? = null,
    val ipLoading: Boolean = false,
    val message: String? = null
)

/** Lógica de la pantalla VPN: perfiles, conexión WireGuard e IP pública. */
class VpnViewModel(private val container: AppContainer) : ViewModel() {

    private val vpnRepository = container.vpnRepository
    private val vpnController = container.vpnController
    private val ipInfoRepository = container.ipInfoRepository

    val profiles = vpnRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionState: StateFlow<VpnConnectionState> = vpnController.state
    val activeProfile: StateFlow<VpnProfile?> = vpnController.activeProfile
    val lastError: StateFlow<String?> = vpnController.lastError

    private val _ui = MutableStateFlow(VpnUiState())
    val ui: StateFlow<VpnUiState> = _ui.asStateFlow()

    init {
        refreshIp()
        // Al conectar/desconectar se vuelve a consultar la IP pública.
        viewModelScope.launch {
            vpnController.state.collect { state ->
                if (state == VpnConnectionState.CONNECTED ||
                    state == VpnConnectionState.DISCONNECTED
                ) {
                    refreshIp()
                }
            }
        }
    }

    fun refreshIp() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(ipLoading = true)
            val info = runCatching { ipInfoRepository.fetch() }.getOrNull()
            _ui.value = _ui.value.copy(ipInfo = info, ipLoading = false)
        }
    }

    /** Intent de permiso del sistema, o null si ya está concedido. */
    fun prepareIntent(): Intent? = vpnController.prepareIntent()

    fun connect(profile: VpnProfile) {
        viewModelScope.launch { vpnController.connect(profile) }
    }

    fun disconnect() {
        viewModelScope.launch { vpnController.disconnect() }
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch {
            if (activeProfile.value?.id == id) vpnController.disconnect()
            vpnRepository.deleteProfile(id)
        }
    }

    fun addProfile(name: String, configText: String, provider: String?, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            vpnRepository.addProfile(name, configText, provider).fold(
                onSuccess = { onDone(true, "Servidor «${it.name}» agregado.") },
                onFailure = { onDone(false, it.message ?: "No se pudo agregar el perfil.") }
            )
        }
    }

    fun addProfileFromUrl(url: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            vpnRepository.addProfileFromUrl(url).fold(
                onSuccess = { onDone(true, "Servidor «${it.name}» agregado desde URL.") },
                onFailure = { onDone(false, it.message ?: "No se pudo descargar la configuración.") }
            )
        }
    }

    fun showMessage(message: String?) {
        _ui.value = _ui.value.copy(message = message)
    }
}
