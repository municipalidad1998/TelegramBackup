package com.iptvplayerpro.data.repository

import com.iptvplayerpro.core.crypto.CryptoManager
import com.iptvplayerpro.core.util.Urls
import com.iptvplayerpro.data.local.VpnDao
import com.iptvplayerpro.data.local.VpnProfileEntity
import com.iptvplayerpro.data.remote.Http
import com.iptvplayerpro.domain.model.VpnProfile
import com.iptvplayerpro.domain.repository.VpnRepository
import com.iptvplayerpro.vpn.WgConfigInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Almacenamiento de perfiles VPN WireGuard del usuario.
 * La configuración (con claves privadas) se guarda cifrada con Android Keystore
 * y nunca se registra en logs.
 */
class VpnRepositoryImpl(
    private val vpnDao: VpnDao,
    private val crypto: CryptoManager,
    private val http: OkHttpClient
) : VpnRepository {

    override fun observeProfiles(): Flow<List<VpnProfile>> =
        vpnDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getProfile(id: Long): VpnProfile? =
        vpnDao.getById(id)?.toDomain()

    override suspend fun addProfile(
        name: String,
        configText: String,
        provider: String?
    ): Result<VpnProfile> = withContext(Dispatchers.IO) {
        runCatching {
            require(name.isNotBlank()) { "Ponle un nombre al perfil." }
            val normalized = WgConfigInspector.normalize(configText)
            require(WgConfigInspector.isValid(normalized)) {
                "La configuración no es un archivo WireGuard válido (falta [Interface]/[Peer] o PrivateKey)."
            }
            val summary = WgConfigInspector.inspect(normalized)
            val country = WgConfigInspector.guessCountry(name)
            val id = vpnDao.upsert(
                VpnProfileEntity(
                    name = name.trim(),
                    country = country?.first,
                    countryCode = country?.second,
                    provider = provider?.takeIf { it.isNotBlank() },
                    serverHost = summary.endpointHost,
                    encryptedConfig = crypto.encrypt(normalized)
                        ?: throw IllegalStateException("No se pudo cifrar la configuración.")
                )
            )
            checkNotNull(vpnDao.getById(id)).toDomain()
        }
    }

    override suspend fun addProfileFromUrl(url: String): Result<VpnProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalizedUrl = Urls.normalize(url)
                require(Urls.isValidHttpUrl(normalizedUrl)) { "URL no válida." }
                val fetched = Http.fetchText(http, normalizedUrl)
                val text = fetched.text
                    ?: throw IllegalStateException("No se pudo descargar la configuración (HTTP ${fetched.httpCode}).")
                val name = Urls.fileNameOf(normalizedUrl).removeSuffix(".conf").ifBlank { "Servidor VPN" }
                addProfile(name, text, null).getOrThrow()
            }
        }

    override suspend fun deleteProfile(id: Long) = withContext(Dispatchers.IO) {
        vpnDao.delete(id)
    }

    override suspend fun markLastConnected(id: Long) = withContext(Dispatchers.IO) {
        vpnDao.setLastConnected(id, System.currentTimeMillis())
    }

    override suspend fun decryptConfig(profileId: Long): String? =
        withContext(Dispatchers.IO) {
            val entity = vpnDao.getById(profileId) ?: return@withContext null
            crypto.decrypt(entity.encryptedConfig)
        }
}
