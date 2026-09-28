package com.iptvplayerpro.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.iptvplayerpro.domain.model.VpnConnectionState
import com.iptvplayerpro.domain.model.VpnProfile
import com.iptvplayerpro.domain.repository.VpnRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Controlador VPN basado en WireGuard (librería oficial tunnel) y en
 * Android VpnService. Ciclo:
 *
 * 1. La UI llama a [prepareIntent] para pedir consentimiento al usuario
 *    (diálogo del sistema de Android) la primera vez.
 * 2. [connect] descifra la config del perfil (Keystore), aplica la protección
 *    de fugas DNS y levanta el túnel con GoBackend.
 * 3. [disconnect] lo baja limpiamente.
 *
 * No se envían credenciales ni tráfico a servidores propios de la app.
 */
class VpnController(
    private val appContext: Context,
    private val vpnRepository: VpnRepository
) {

    companion object {
        private const val TAG = "VpnController"
        private const val TUNNEL_NAME = "IPTVPlayerPro"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(VpnConnectionState.DISCONNECTED)
    val state: StateFlow<VpnConnectionState> = _state.asStateFlow()

    private val _activeProfile = MutableStateFlow<VpnProfile?>(null)
    val activeProfile: StateFlow<VpnProfile?> = _activeProfile.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastBackendVersion = MutableStateFlow<String?>(null)
    val lastBackendVersion: StateFlow<String?> = _lastBackendVersion.asStateFlow()

    /** Túnel WireGuard: recibe los cambios de estado del backend. */
    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            _state.value = when (newState) {
                Tunnel.State.UP -> VpnConnectionState.CONNECTED
                Tunnel.State.DOWN -> {
                    if (_state.value == VpnConnectionState.DISCONNECTING ||
                        _state.value == VpnConnectionState.CONNECTED
                    ) {
                        VpnConnectionState.DISCONNECTED
                    } else {
                        VpnConnectionState.DISCONNECTED
                    }
                }
                else -> _state.value
            }
        }
    }

    @Volatile
    private var backend: Backend? = null

    private fun obtainBackend(): Backend =
        backend ?: synchronized(this) {
            backend ?: GoBackend(appContext).also {
                backend = it
                _lastBackendVersion.value = try {
                    it.version
                } catch (e: Exception) {
                    null
                }
            }
        }

    /**
     * Intent de autorización del sistema (VpnService.prepare). Si devuelve null
     * ya hay permiso y se puede conectar directamente.
     */
    fun prepareIntent(): Intent? = VpnService.prepare(appContext)

    /** Conecta con el perfil indicado. Debe haber permiso VPN ya concedido. */
    suspend fun connect(profile: VpnProfile) {
        mutex.withLock {
            _lastError.value = null
            _state.value = VpnConnectionState.CONNECTING
            try {
                val configText = vpnRepository.decryptConfig(profile.id)
                    ?: throw IllegalStateException("No se pudo leer la configuración del perfil.")
                // Protección de fugas DNS cuando la config no declara DNS.
                val protected = WgConfigInspector.ensureDns(configText)
                val config = withContext(Dispatchers.IO) {
                    Config.parse(protected.byteInputStream())
                }
                val be = obtainBackend()
                withContext(Dispatchers.IO) {
                    be.setState(tunnel, Tunnel.State.UP, config)
                }
                _activeProfile.value = profile
                scope.launch {
                    runCatching { vpnRepository.markLastConnected(profile.id) }
                }
            } catch (e: BackendException) {
                Log.w(TAG, "Error del backend VPN: ${e.reason}")
                _state.value = VpnConnectionState.ERROR
                _lastError.value = when (e.reason) {
                    BackendException.Reason.VPN_NOT_AUTHORIZED ->
                        "Falta autorizar la VPN en el sistema. Concede el permiso e inténtalo de nuevo."
                    BackendException.Reason.UNABLE_TO_START_VPN ->
                        "Android no permitió iniciar el servicio VPN."
                    BackendException.Reason.DNS_RESOLUTION_FAILURE ->
                        "No se pudo resolver el servidor VPN. Revisa tu conexión a Internet."
                    BackendException.Reason.TUN_CREATION_ERROR ->
                        "No se pudo crear la interfaz del túnel."
                    else -> "No se pudo conectar a la VPN (${e.reason})."
                }
            } catch (e: LinkageError) {
                // La librería nativa de WireGuard no está disponible en este dispositivo.
                Log.w(TAG, "Librería nativa VPN no disponible")
                _state.value = VpnConnectionState.ERROR
                _lastError.value = "El motor WireGuard no está disponible en este dispositivo."
            } catch (e: Exception) {
                Log.w(TAG, "Error al conectar VPN: ${e.javaClass.simpleName}")
                _state.value = VpnConnectionState.ERROR
                _lastError.value = e.message ?: "No se pudo conectar a la VPN."
            }
        }
    }

    /** Desconecta el túnel activo. */
    suspend fun disconnect() {
        mutex.withLock {
            try {
                val be = backend ?: run {
                    _state.value = VpnConnectionState.DISCONNECTED
                    _activeProfile.value = null
                    return
                }
                withContext(Dispatchers.IO) {
                    be.setState(tunnel, Tunnel.State.DOWN, null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error al desconectar VPN: ${e.javaClass.simpleName}")
            } finally {
                _state.value = VpnConnectionState.DISCONNECTED
                _activeProfile.value = null
            }
        }
    }
}
