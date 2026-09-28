package com.iptvplayerpro.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.iptvplayerpro.domain.model.PlaylistStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY isDefault DESC, name")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists")
    suspend fun getAll(): List<PlaylistEntity>

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity): Long

    @Query(
        "UPDATE playlists SET status = :status, lastError = :lastError, expiresAt = :expiresAt " +
            "WHERE id = :id"
    )
    suspend fun updateStatus(id: Long, status: PlaylistStatus, lastError: String?, expiresAt: Long?)

    @Query(
        "UPDATE playlists SET lastUpdated = :lastUpdated, channelCount = :channelCount, " +
            "etag = :etag, lastModifiedHeader = :lastModified, status = :status, " +
            "expiresAt = :expiresAt, lastError = :lastError WHERE id = :id"
    )
    suspend fun updateSyncMeta(
        id: Long,
        lastUpdated: Long,
        channelCount: Int,
        etag: String?,
        lastModified: String?,
        status: PlaylistStatus,
        expiresAt: Long?,
        lastError: String?
    )

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE playlists SET sourceUrl = :url, epgUrl = :epgUrl WHERE id = :id")
    suspend fun updateM3USource(id: Long, url: String?, epgUrl: String?)

    @Query("UPDATE playlists SET serverUrl = :server, username = :user, encryptedPassword = :password WHERE id = :id")
    suspend fun updateXtreamCredentials(id: Long, server: String?, user: String?, password: String?)

    @Query("UPDATE playlists SET autoRefresh = :enabled WHERE id = :id")
    suspend fun setAutoRefresh(id: Long, enabled: Boolean)

    @Query("UPDATE playlists SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE playlists SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM playlists")
    fun observeCount(): Flow<Int>

    /** Marca todas las listas con sesión expirada por tiempo. */
    @Query("UPDATE playlists SET status = :expired WHERE status = :active AND expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun markExpiredByTime(active: PlaylistStatus, expired: PlaylistStatus, now: Long)

    @Transaction
    suspend fun makeDefault(id: Long) {
        clearDefault()
        setDefault(id)
    }
}
