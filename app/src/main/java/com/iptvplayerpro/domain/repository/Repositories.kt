package com.iptvplayerpro.domain.repository

import androidx.paging.PagingData
import com.iptvplayerpro.domain.model.CategoryCount
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.domain.model.Playlist
import com.iptvplayerpro.domain.model.SeriesDetail
import com.iptvplayerpro.domain.model.TestConnectionResult
import com.iptvplayerpro.domain.model.WatchedEntry
import kotlinx.coroutines.flow.Flow

/** Operaciones sobre listas M3U/M3U8 y cuentas Xtream. */
interface PlaylistRepository {

    fun observePlaylists(): Flow<List<Playlist>>

    fun observePlaylist(id: Long): Flow<Playlist?>

    suspend fun getPlaylist(id: Long): Playlist?

    /**
     * Agrega una lista M3U/M3U8.
     * @param content contenido ya descargado/leído (importación local) o null para
     * descargar [url].
     */
    suspend fun addM3U(name: String, url: String?, epgUrl: String?, content: String?): Result<Playlist>

    /** Agrega una cuenta Xtream (URL del servidor, usuario y contraseña). */
    suspend fun addXtream(name: String, server: String, user: String, password: String): Result<Playlist>

    /** Vuelve a descargar/reautenticar la lista. Conserva favoritos e historial. */
    suspend fun refresh(id: Long): Result<Playlist>

    /** Edita nombre/URL/EPG/credenciales. Campos null o vacíos no se modifican. */
    suspend fun update(
        id: Long,
        name: String,
        url: String? = null,
        epgUrl: String? = null,
        server: String? = null,
        user: String? = null,
        password: String? = null
    ): Result<Playlist>

    suspend fun delete(id: Long)

    suspend fun setDefault(id: Long)

    /** Prueba la conexión con la fuente y reporta estado/expiración. */
    suspend fun testConnection(id: Long): TestConnectionResult

    /** Obtiene el detalle (temporadas y episodios) de una serie Xtream. */
    suspend fun fetchSeriesDetail(channel: Channel): Result<SeriesDetail>

    /** Construye la URL de reproducción de un episodio de serie (Xtream). */
    suspend fun buildEpisodeUrl(channel: Channel, episodeId: String, extension: String?): String?
}

/** Consulta de canales, categorías, favoritos e historial. */
interface ChannelRepository {

    fun channelsPager(
        type: ChannelType,
        playlistIds: List<Long>,
        categoryId: String? = null,
        query: String = ""
    ): Flow<PagingData<Channel>>

    fun favoritesPager(type: ChannelType?): Flow<PagingData<Channel>>

    fun recents(limit: Int = 30): Flow<List<Channel>>

    fun history(limit: Int = 200): Flow<List<WatchedEntry>>

    fun observeChannel(id: Long): Flow<Channel?>

    suspend fun getChannel(id: Long): Channel?

    /** Canales de una lista por tipo (panel lateral del reproductor). */
    suspend fun playlistChannels(playlistId: Long, type: ChannelType, limit: Int = 500): List<Channel>

    suspend fun setFavorite(id: Long, favorite: Boolean)

    suspend fun recordWatch(id: Long)

    fun categories(type: ChannelType, playlistIds: List<Long>): Flow<List<CategoryCount>>

    /** Búsqueda global por nombre en todos los tipos de contenido. */
    suspend fun search(query: String, limit: Int = 150): List<Channel>
}
