package com.iptvplayerpro.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iptvplayerpro.domain.model.ChannelType
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    // ---- Consultas paginadas ----

    @Query(
        "SELECT * FROM channels WHERE playlistId IN (:playlistIds) AND type = :type " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId) " +
            "AND (:query = '' OR name LIKE :pattern ESCAPE '\\') " +
            "ORDER BY name"
    )
    fun pagingByTypeAndCategory(
        playlistIds: List<Long>,
        type: ChannelType,
        categoryId: String?,
        query: String,
        pattern: String
    ): PagingSource<Int, ChannelEntity>

    @Query(
        "SELECT * FROM channels WHERE (:type IS NULL OR type = :type) AND isFavorite = 1 " +
            "AND (:query = '' OR name LIKE :pattern ESCAPE '\\') " +
            "ORDER BY name"
    )
    fun favoritesPaging(type: ChannelType?, query: String, pattern: String): PagingSource<Int, ChannelEntity>

    // ---- Listas simples ----

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE id = :id")
    fun observeById(id: Long): Flow<ChannelEntity?>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId")
    suspend fun getByPlaylist(playlistId: Long): List<ChannelEntity>

    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId AND type = :type " +
            "ORDER BY name LIMIT :limit"
    )
    suspend fun byPlaylistAndType(playlistId: Long, type: ChannelType, limit: Int): List<ChannelEntity>

    @Query(
        "SELECT * FROM channels WHERE name LIKE :pattern ESCAPE '\\' " +
            "ORDER BY CASE type WHEN 'LIVE' THEN 0 WHEN 'MOVIE' THEN 1 ELSE 2 END, name LIMIT :limit"
    )
    suspend fun search(pattern: String, limit: Int): List<ChannelEntity>

    @Query(
        "SELECT c.* FROM channels c INNER JOIN history h ON h.channelId = c.id " +
            "GROUP BY c.id ORDER BY MAX(h.watchedAt) DESC LIMIT :limit"
    )
    fun observeRecents(limit: Int): Flow<List<ChannelEntity>>

    @Query(
        "SELECT c.*, h.watchedAt AS watchedAt FROM history h " +
            "INNER JOIN channels c ON c.id = h.channelId " +
            "ORDER BY h.watchedAt DESC LIMIT :limit"
    )
    fun observeHistory(limit: Int): Flow<List<HistoryRow>>

    @Query(
        "SELECT categoryId AS categoryId, categoryName AS categoryName, COUNT(*) AS count " +
            "FROM channels WHERE playlistId IN (:playlistIds) AND type = :type " +
            "AND categoryId IS NOT NULL GROUP BY categoryId, categoryName ORDER BY categoryName"
    )
    fun observeCategories(playlistIds: List<Long>, type: ChannelType): Flow<List<CategoryCountRow>>

    @Query("SELECT * FROM channels WHERE type = :type AND epgChannelId IS NOT NULL ORDER BY name LIMIT :limit")
    suspend fun channelsWithEpg(type: ChannelType, limit: Int): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE type = :type ORDER BY name LIMIT :limit")
    suspend fun channelsByType(type: ChannelType, limit: Int): List<ChannelEntity>

    // ---- Escritura ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>): List<Long>

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int

    @Query("UPDATE channels SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE channels SET epgChannelId = :epgChannelId WHERE id = :id")
    suspend fun setEpgMatch(id: Long, epgChannelId: String)

    @Insert
    suspend fun insertHistory(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY watchedAt DESC LIMIT 500)")
    suspend fun trimHistory()

    @Query("DELETE FROM history WHERE channelId NOT IN (SELECT id FROM channels)")
    suspend fun deleteOrphanHistory()

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isFavorite = 1")
    suspend fun favoritesOf(playlistId: Long): List<ChannelEntity>

    @Query(
        "UPDATE channels SET isFavorite = 1 WHERE playlistId = :playlistId " +
            "AND xtreamStreamId IS NOT NULL AND xtreamStreamId IN (:streamIds)"
    )
    suspend fun restoreFavoritesByStreamId(playlistId: Long, streamIds: List<String>)

    @Query("UPDATE channels SET isFavorite = 1 WHERE playlistId = :playlistId AND streamUrl IN (:urls)")
    suspend fun restoreFavoritesByUrl(playlistId: Long, urls: List<String>)

    @Query("DELETE FROM history WHERE channelId = :channelId")
    suspend fun deleteHistoryOfChannel(channelId: Long)

    /** Historial (canal + última vez visto) antes de reemplazar los canales de una lista. */
    @Query(
        "SELECT h.channelId AS historyChannelId, MAX(h.watchedAt) AS lastWatched, " +
            "c.xtreamStreamId AS xtreamStreamId, c.streamUrl AS streamUrl " +
            "FROM history h INNER JOIN channels c ON c.id = h.channelId " +
            "WHERE c.playlistId = :playlistId GROUP BY h.channelId"
    )
    suspend fun historyKeysOfPlaylist(playlistId: Long): List<HistoryKeyRow>
}

/** Fila del historial: canal embebido + fecha de visualización. */
data class HistoryRow(
    @Embedded val channel: ChannelEntity,
    val watchedAt: Long
)

/** Clave de historial estable ante reemplazos de canales. */
data class HistoryKeyRow(
    val historyChannelId: Long,
    val lastWatched: Long,
    val xtreamStreamId: String?,
    val streamUrl: String
)
