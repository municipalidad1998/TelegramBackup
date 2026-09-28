package com.iptvplayerpro.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<EpgChannelEntity>)

    @Insert
    suspend fun insertProgrammes(programmes: List<EpgProgrammeEntity>)

    @Query("DELETE FROM epg_programmes")
    suspend fun clearProgrammes()

    @Query("DELETE FROM epg_channels")
    suspend fun clearEpgChannels()

    @Query("SELECT COUNT(*) FROM epg_programmes")
    suspend fun countProgrammes(): Int

    /** Señal de invalidación: se reemite cuando cambia la tabla de programas. */
    @Query("SELECT COUNT(*) FROM epg_programmes")
    fun observeProgrammeCount(): Flow<Long>

    @Query(
        "SELECT * FROM epg_programmes WHERE epgChannelId = :epgChannelId " +
            "AND start <= :now AND end > :now LIMIT 1"
    )
    suspend fun nowProgramme(epgChannelId: String, now: Long): EpgProgrammeEntity?

    @Query(
        "SELECT * FROM epg_programmes WHERE epgChannelId = :epgChannelId AND start > :now " +
            "ORDER BY start LIMIT 1"
    )
    suspend fun nextProgramme(epgChannelId: String, now: Long): EpgProgrammeEntity?

    @Query(
        "SELECT * FROM epg_programmes WHERE epgChannelId IN (:epgChannelIds) " +
            "AND start < :to AND end > :from ORDER BY epgChannelId, start"
    )
    suspend fun programmesWindow(epgChannelIds: List<String>, from: Long, to: Long): List<EpgProgrammeEntity>

    @Query("SELECT * FROM epg_channels WHERE displayName = :name COLLATE NOCASE LIMIT 1")
    suspend fun findChannelByName(name: String): EpgChannelEntity?

    @Query("SELECT * FROM epg_channels ORDER BY displayName LIMIT :limit")
    fun observeEpgChannels(limit: Int): Flow<List<EpgChannelEntity>>

    @Transaction
    suspend fun replaceAll(channels: List<EpgChannelEntity>, programmes: List<EpgProgrammeEntity>) {
        clearProgrammes()
        clearEpgChannels()
        insertChannels(channels)
        insertProgrammes(programmes)
    }
}
