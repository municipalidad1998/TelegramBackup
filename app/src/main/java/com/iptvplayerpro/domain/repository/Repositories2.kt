package com.iptvplayerpro.domain.repository

import com.iptvplayerpro.domain.model.EpgChannelListItem
import com.iptvplayerpro.domain.model.GuideData
import com.iptvplayerpro.domain.model.NowNext
import com.iptvplayerpro.domain.model.PublicIpInfo
import com.iptvplayerpro.domain.model.UpdateInfo
import com.iptvplayerpro.domain.model.VpnProfile
import kotlinx.coroutines.flow.Flow

/** Guía de programación (XMLTV / Xtream). */
interface EpgRepository {

    /** Programa actual y siguiente para un id EPG (con reserva por nombre). */
    fun observeNowNext(epgChannelId: String?, channelName: String?): Flow<NowNext>

    /** Refresca el EPG de todas las listas que tienen fuente EPG. */
    suspend fun refreshAll(): Result<Int>

    /** Datos de la guía para una ventana de tiempo (24 h). */
    suspend fun guideWindow(from: Long, to: Long, maxChannels: Int = 200): GuideData

    /** Canales EPG disponibles (para coincidencia manual). */
    fun epgChannels(): Flow<List<EpgChannelListItem>>

    /** Coincidencia manual canal ↔ canal EPG. */
    suspend fun setManualMatch(channelId: Long, epgChannelId: String)

    suspend fun clearEpg()
}

/** Almacenamiento de perfiles VPN del usuario. */
interface VpnRepository {

    fun observeProfiles(): Flow<List<VpnProfile>>

    suspend fun getProfile(id: Long): VpnProfile?

    /** Guarda un perfil WireGuard (la configuración se guarda cifrada). */
    suspend fun addProfile(name: String, configText: String, provider: String?): Result<VpnProfile>

    /** Descarga una configuración .conf desde una URL y la guarda. */
    suspend fun addProfileFromUrl(url: String): Result<VpnProfile>

    suspend fun deleteProfile(id: Long)

    /** Marca el momento de la última conexión de un perfil. */
    suspend fun markLastConnected(id: Long)

    /** Devuelve el texto de configuración descifrado (solo para conectar). */
    suspend fun decryptConfig(profileId: Long): String?
}

/** Consulta e instalación de actualizaciones de la APK. */
interface UpdateRepository {

    val currentVersionName: String
    val currentVersionCode: Int

    /** Consulta la fuente oficial de actualizaciones configurada. */
    suspend fun checkForUpdate(updateUrl: String): Result<UpdateInfo?>

    /** Descarga la APK con DownloadManager y abre el instalador oficial. */
    suspend fun downloadAndInstall(update: UpdateInfo): Result<Unit>
}

/** Información de la IP pública del dispositivo (cuando es posible consultarla). */
interface IpInfoRepository {
    suspend fun fetch(): PublicIpInfo?
}
