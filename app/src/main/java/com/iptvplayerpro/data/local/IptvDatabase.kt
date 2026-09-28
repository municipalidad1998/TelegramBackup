package com.iptvplayerpro.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.domain.model.PlaylistStatus
import com.iptvplayerpro.domain.model.PlaylistType

/** Conversores enum ↔ texto para Room. */
class Converters {
    @TypeConverter fun playlistTypeToString(value: PlaylistType?): String? = value?.name
    @TypeConverter fun stringToPlaylistType(value: String?): PlaylistType? =
        value?.let { runCatching { PlaylistType.valueOf(it) }.getOrNull() }

    @TypeConverter fun playlistStatusToString(value: PlaylistStatus?): String? = value?.name
    @TypeConverter fun stringToPlaylistStatus(value: String?): PlaylistStatus? =
        value?.let { runCatching { PlaylistStatus.valueOf(it) }.getOrNull() }

    @TypeConverter fun channelTypeToString(value: ChannelType?): String? = value?.name
    @TypeConverter fun stringToChannelType(value: String?): ChannelType? =
        value?.let { runCatching { ChannelType.valueOf(it) }.getOrNull() }
}

/**
 * Base de datos local (Room). Almacena listas, canales, historial, EPG y
 * perfiles VPN (credenciales cifradas con Android Keystore).
 */
@Database(
    entities = [
        PlaylistEntity::class,
        ChannelEntity::class,
        HistoryEntity::class,
        EpgChannelEntity::class,
        EpgProgrammeEntity::class,
        VpnProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class IptvDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun epgDao(): EpgDao
    abstract fun vpnDao(): VpnDao

    companion object {
        const val NAME = "iptv_player_pro.db"
    }
}
