package com.iptvplayerpro.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.iptvplayerpro.data.local.ChannelDao
import com.iptvplayerpro.data.local.IptvDatabase
import com.iptvplayerpro.data.local.PlaylistDao
import com.iptvplayerpro.domain.model.CategoryCount
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.domain.model.WatchedEntry
import com.iptvplayerpro.domain.repository.ChannelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Consulta de canales con Paging 3: listas de miles de canales se cargan por
 * páginas sin congelar la interfaz.
 */
class ChannelRepositoryImpl(
    private val database: IptvDatabase,
    private val channelDao: ChannelDao,
    private val playlistDao: PlaylistDao
) : ChannelRepository {

    override fun channelsPager(
        type: ChannelType,
        playlistIds: List<Long>,
        categoryId: String?,
        query: String
    ): Flow<PagingData<Channel>> {
        val ids = playlistIds.ifEmpty { listOf(-1L) }
        val (safeQuery, pattern) = likePattern(query)
        return Pager(
            config = PagingConfig(
                pageSize = 60,
                prefetchDistance = 120,
                enablePlaceholders = false,
                maxSize = 900,
                initialLoadSize = 120
            )
        ) {
            channelDao.pagingByTypeAndCategory(ids, type, categoryId, safeQuery, pattern)
        }.flow.map { paging -> paging.map { it.toDomain(playlistName = "") } }
    }

    override fun favoritesPager(type: ChannelType?): Flow<PagingData<Channel>> {
        return Pager(
            config = PagingConfig(pageSize = 60, prefetchDistance = 120, maxSize = 600)
        ) {
            channelDao.favoritesPaging(type, "", "%")
        }.flow.map { paging -> paging.map { it.toDomain(playlistName = "") } }
    }

    override fun recents(limit: Int): Flow<List<Channel>> =
        channelDao.observeRecents(limit).map { list ->
            list.map { it.toDomain(playlistName = "") }
        }

    override fun history(limit: Int): Flow<List<WatchedEntry>> =
        channelDao.observeHistory(limit).map { rows ->
            rows.map { it.toEntry(playlistName = "") }
        }

    override fun observeChannel(id: Long): Flow<Channel?> =
        channelDao.observeById(id).map { it?.toDomain(playlistName = "") }

    override suspend fun getChannel(id: Long): Channel? =
        channelDao.getById(id)?.toDomain(playlistName = "")

    override suspend fun playlistChannels(playlistId: Long, type: ChannelType, limit: Int): List<Channel> =
        channelDao.byPlaylistAndType(playlistId, type, limit).map { it.toDomain(playlistName = "") }

    override suspend fun setFavorite(id: Long, favorite: Boolean) =
        channelDao.setFavorite(id, favorite)

    override suspend fun recordWatch(id: Long) {
        channelDao.insertHistory(
            com.iptvplayerpro.data.local.HistoryEntity(channelId = id, watchedAt = System.currentTimeMillis())
        )
        channelDao.trimHistory()
    }

    override fun categories(type: ChannelType, playlistIds: List<Long>): Flow<List<CategoryCount>> {
        val ids = playlistIds.ifEmpty { listOf(-1L) }
        return channelDao.observeCategories(ids, type).map { rows ->
            rows.filter { !it.categoryId.isNullOrBlank() }.map {
                CategoryCount(it.categoryId.orEmpty(), it.categoryName.orEmpty(), it.count)
            }
        }
    }

    override suspend fun search(query: String, limit: Int): List<Channel> {
        if (query.isBlank()) return emptyList()
        val (_, pattern) = likePattern(query)
        return channelDao.search(pattern, limit).map { it.toDomain(playlistName = "") }
    }

    /** Escapa comodines SQL LIKE y construye el patrón %query%. */
    private fun likePattern(query: String): Pair<String, String> {
        if (query.isBlank()) return "" to "%"
        val escaped = query.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return query.trim() to "%$escaped%"
    }
}
