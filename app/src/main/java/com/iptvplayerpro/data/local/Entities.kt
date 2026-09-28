package com.iptvplayerpro.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.domain.model.PlaylistStatus
import com.iptvplayerpro.domain.model.PlaylistType

/**
 * Lista de contenido. Las contraseñas Xtream se guardan cifradas
 * (Android Keystore) en [encryptedPassword], nunca en claro.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    /** URL de la lista M3U (si aplica). */
    val sourceUrl: String? = null,
    /** URL XMLTV independiente (opcional). */
    val epgUrl: String? = null,
    /** Servidor Xtream normalizado (si aplica). */
    val serverUrl: String? = null,
    val username: String? = null,
    val encryptedPassword: String? = null,
    val status: PlaylistStatus = PlaylistStatus.UNKNOWN,
    val isDefault: Boolean = false,
    val autoRefresh: Boolean = true,
    val lastUpdated: Long? = null,
    /** Fecha de expiración (epoch ms) reportada por la fuente. */
    val expiresAt: Long? = null,
    val channelCount: Int = 0,
    val lastError: String? = null,
    /** ETag de la última descarga para no repetir peticiones sin cambios. */
    val etag: String? = null,
    val lastModifiedHeader: String? = null
)

@Entity(
    tableName = "channels",
    indices = [
        Index("playlistId"),
        Index(value = ["playlistId", "type", "categoryId"]),
        Index("name"),
        Index("epgChannelId")
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    /** stream_id / series_id en Xtream. */
    val xtreamStreamId: String? = null,
    val type: ChannelType,
    val name: String,
    val logoUrl: String? = null,
    val streamUrl: String,
    val categoryId: String? = null,
    val categoryName: String? = null,
    /** tvg-id del M3U o epg_channel_id de Xtream. */
    val tvgId: String? = null,
    /** Canal EPG asociado (auto por tvg-id / manual). */
    val epgChannelId: String? = null,
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val containerExtension: String? = null
)

@Entity(tableName = "history", indices = [Index("channelId"), Index("watchedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val watchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "epg_channels")
data class EpgChannelEntity(
    @PrimaryKey val epgChannelId: String,
    val displayName: String? = null
)

@Entity(tableName = "epg_programmes", indices = [Index("epgChannelId", "start")])
data class EpgProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epgChannelId: String,
    val title: String,
    val description: String? = null,
    val start: Long,
    val end: Long
)

/** Perfil VPN WireGuard; la configuración completa se guarda cifrada. */
@Entity(tableName = "vpn_profiles")
data class VpnProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val country: String? = null,
    val countryCode: String? = null,
    val provider: String? = null,
    val serverHost: String? = null,
    val encryptedConfig: String,
    val lastConnectedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Conteo por categoría (consulta de categorías). */
data class CategoryCountRow(
    val categoryId: String?,
    val categoryName: String?,
    val count: Int
)
