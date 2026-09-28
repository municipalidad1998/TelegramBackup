package com.iptvplayerpro.domain.model

/** Tipo de fuente de una lista. */
enum class PlaylistType { M3U, XTREAM }

/** Estado de una lista o cuenta Xtream. */
enum class PlaylistStatus {
    /** Se desconoce (recién agregada, sin verificar). */
    UNKNOWN,

    /** Activa y verificada. */
    ACTIVE,

    /** La cuenta/lista expiró según el proveedor. Los datos locales se conservan. */
    EXPIRED,

    /** Error de red o credenciales inválidas. */
    ERROR
}

/** Tipo de contenido del canal. */
enum class ChannelType { LIVE, MOVIE, SERIES }

/** Estado de la conexión VPN. */
enum class VpnConnectionState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }

/** Modo de tema de la aplicación. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Lista de contenido (M3U remota, M3U local importada o cuenta Xtream).
 * Nunca contiene la contraseña: vive cifrada en el almacén local.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val type: PlaylistType,
    val sourceUrl: String? = null,
    val epgUrl: String? = null,
    val serverUrl: String? = null,
    val username: String? = null,
    val status: PlaylistStatus = PlaylistStatus.UNKNOWN,
    val isDefault: Boolean = false,
    val autoRefresh: Boolean = true,
    val lastUpdated: Long? = null,
    val expiresAt: Long? = null,
    val channelCount: Int = 0,
    val lastError: String? = null
) {
    val isExpired: Boolean get() = status == PlaylistStatus.EXPIRED
}

/** Canal, película o serie disponible para reproducción. */
data class Channel(
    val id: Long,
    val playlistId: Long,
    val playlistName: String,
    val type: ChannelType,
    val name: String,
    val logoUrl: String? = null,
    val streamUrl: String,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val tvgId: String? = null,
    val epgChannelId: String? = null,
    val isFavorite: Boolean = false,
    val xtreamStreamId: String? = null,
    val containerExtension: String? = null
)

/** Categoría con la cantidad de canales que contiene. */
data class CategoryCount(
    val categoryId: String,
    val categoryName: String,
    val count: Int
)

/** Entrada del historial de reproducción. */
data class WatchedEntry(
    val channel: Channel,
    val watchedAt: Long
)

/** Programa de la guía EPG (XMLTV o Xtream). */
data class EpgProgramme(
    val id: Long,
    val epgChannelId: String,
    val title: String,
    val description: String? = null,
    val start: Long,
    val end: Long
) {
    val progress: Float
        get() {
            val total = (end - start).coerceAtLeast(1L)
            return (((System.currentTimeMillis() - start).toFloat()) / total).coerceIn(0f, 1f)
        }
}

/** Programa actual y siguiente de un canal. */
data class NowNext(
    val now: EpgProgramme? = null,
    val next: EpgProgramme? = null
)

/** Canal de la guía con sus programas dentro de una ventana de tiempo. */
data class GuideChannel(
    val channel: Channel,
    val programmes: List<EpgProgramme>
)

/** Datos listos para pintar la guía de programación. */
data class GuideData(
    val channels: List<GuideChannel>,
    val windowStart: Long,
    val windowEnd: Long
)

/** Canal EPG disponible (para coincidencia manual). */
data class EpgChannelListItem(
    val epgChannelId: String,
    val displayName: String?
)

/** Perfil VPN (WireGuard) guardado por el usuario. */
data class VpnProfile(
    val id: Long,
    val name: String,
    val country: String? = null,
    val countryCode: String? = null,
    val provider: String? = null,
    val serverHost: String? = null,
    val lastConnectedAt: Long? = null
)

/** Información de IP pública (cuando el servicio responde). */
data class PublicIpInfo(
    val ip: String,
    val country: String? = null,
    val countryCode: String? = null,
    val city: String? = null
)

/** Resultado de la comprobación de conexión de una lista. */
data class TestConnectionResult(
    val ok: Boolean,
    val message: String,
    val expiresAt: Long? = null,
    val details: String? = null
)

/** Episodio de una serie (Xtream). */
data class SeriesEpisode(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val extension: String? = null,
    val plot: String? = null,
    val poster: String? = null
)

/** Detalle completo de una serie (Xtream). */
data class SeriesDetail(
    val seriesName: String,
    val plot: String? = null,
    val poster: String? = null,
    val cast: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val releaseDate: String? = null,
    val episodes: List<SeriesEpisode> = emptyList()
)

/** Información de una actualización disponible. */
data class UpdateInfo(
    val newVersionName: String,
    val newVersionCode: Int,
    val changelog: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)
