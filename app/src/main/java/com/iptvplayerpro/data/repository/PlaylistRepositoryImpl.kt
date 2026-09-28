package com.iptvplayerpro.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.iptvplayerpro.core.crypto.CryptoManager
import com.iptvplayerpro.core.util.Urls
import com.iptvplayerpro.data.local.ChannelEntity
import com.iptvplayerpro.data.local.IptvDatabase
import com.iptvplayerpro.data.local.PlaylistEntity
import com.iptvplayerpro.data.remote.Http
import com.iptvplayerpro.data.remote.m3u.M3uParser
import com.iptvplayerpro.data.remote.xtream.XtreamService
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.domain.model.Playlist
import com.iptvplayerpro.domain.model.PlaylistStatus
import com.iptvplayerpro.domain.model.PlaylistType
import com.iptvplayerpro.domain.model.SeriesDetail
import com.iptvplayerpro.domain.model.SeriesEpisode
import com.iptvplayerpro.domain.model.TestConnectionResult
import com.iptvplayerpro.domain.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.net.URLEncoder

/**
 * Implementación de [PlaylistRepository].
 *
 * Reglas de negocio importantes:
 * - Las contraseñas se guardan cifradas (Android Keystore) y nunca se registran.
 * - Cuando una cuenta Xtream expira, la lista se marca EXPIRED pero los canales
 *   ya descargados, favoritos e historial se conservan tal cual.
 * - No se modifican tokens ni parámetros del servidor para revivir cuentas.
 */
class PlaylistRepositoryImpl(
    private val database: IptvDatabase,
    private val http: OkHttpClient,
    private val xtream: XtreamService,
    private val crypto: CryptoManager
) : PlaylistRepository {

    companion object {
        private const val TAG = "PlaylistRepo"
        private const val INSERT_CHUNK = 1500
    }

    private val playlistDao = database.playlistDao()
    private val channelDao = database.channelDao()

    // ---------------------------------------------------------------- observables

    override fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observePlaylist(id: Long): Flow<Playlist?> =
        playlistDao.observeById(id).map { it?.toDomain() }

    override suspend fun getPlaylist(id: Long): Playlist? =
        playlistDao.getById(id)?.toDomain()

    // ---------------------------------------------------------------- alta de listas

    override suspend fun addM3U(
        name: String,
        url: String?,
        epgUrl: String?,
        content: String?
    ): Result<Playlist> = withContext(Dispatchers.IO) {
        runCatching {
            val finalUrl = url?.let { Urls.normalize(it) }?.takeIf { Urls.isValidHttpUrl(it) }
            require(!finalUrl.isNullOrEmpty() || !content.isNullOrEmpty()) {
                "Debes indicar una URL válida o importar un archivo M3U/M3U8."
            }

            var text = content
            var etag: String? = null
            var lastModified: String? = null
            if (finalUrl != null && text == null) {
                val fetched = Http.fetchText(http, finalUrl)
                check(fetched.notModified.not()) { "El servidor respondió 304 sin contenido previo." }
                check(!fetched.text.isNullOrEmpty()) { "No se pudo descargar la lista (HTTP ${fetched.httpCode})." }
                text = fetched.text
                etag = fetched.etag
                lastModified = fetched.lastModified
            }
            require(text!!.isNotBlank()) { "La lista está vacía." }
            require(M3uParser.isValid(text)) { "El contenido no parece una lista M3U/M3U8 válida." }

            val entries = M3uParser.parse(text)
            require(entries.isNotEmpty()) { "La lista no contiene canales." }

            val isDefault = playlistDao.getAll().isEmpty()
            val id = playlistDao.upsert(
                PlaylistEntity(
                    name = name.ifBlank { "Lista M3U" },
                    type = PlaylistType.M3U,
                    sourceUrl = finalUrl,
                    epgUrl = epgUrl?.takeIf { it.isNotBlank() }?.let { Urls.normalize(it) },
                    status = PlaylistStatus.ACTIVE,
                    isDefault = isDefault,
                    lastUpdated = System.currentTimeMillis(),
                    etag = etag,
                    lastModifiedHeader = lastModified
                )
            )

            replaceChannels(id, M3uParser.toEntities(entries, id, System.currentTimeMillis()))
            val count = channelDao.countByPlaylist(id)
            playlistDao.updateSyncMeta(
                id = id,
                lastUpdated = System.currentTimeMillis(),
                channelCount = count,
                etag = etag,
                lastModified = lastModified,
                status = PlaylistStatus.ACTIVE,
                expiresAt = null,
                lastError = null
            )
            checkNotNull(playlistDao.getById(id)).toDomain()
        }
    }

    override suspend fun addXtream(
        name: String,
        server: String,
        user: String,
        password: String
    ): Result<Playlist> = withContext(Dispatchers.IO) {
        runCatching {
            val serverUrl = Urls.normalizeXtreamServer(server)
            require(Urls.isValidHttpUrl(serverUrl)) { "La URL del servidor no es válida." }
            require(user.isNotBlank()) { "El usuario es obligatorio." }
            require(password.isNotBlank()) { "La contraseña es obligatoria." }

            val auth = authenticate(serverUrl, user, password)

            val isDefault = playlistDao.getAll().isEmpty()
            val id = playlistDao.upsert(
                PlaylistEntity(
                    name = name.ifBlank { "Xtream ${Urls.hostOf(serverUrl) ?: ""}".trim() },
                    type = PlaylistType.XTREAM,
                    serverUrl = serverUrl,
                    username = user,
                    encryptedPassword = crypto.encrypt(password),
                    status = auth.status,
                    isDefault = isDefault,
                    lastUpdated = System.currentTimeMillis(),
                    expiresAt = auth.expiresAt
                )
            )

            if (auth.status == PlaylistStatus.ACTIVE) {
                importXtreamContent(id, serverUrl, user, password)
            } else {
                // Cuenta vencida: se guarda y se marca claramente. Sin canales nuevos.
                playlistDao.updateStatus(id, auth.status, "Cuenta vencida o inactiva según el servidor.", auth.expiresAt)
            }
            checkNotNull(playlistDao.getById(id)).toDomain()
        }
    }

    // ---------------------------------------------------------------- actualización

    override suspend fun refresh(id: Long): Result<Playlist> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = playlistDao.getById(id)
                ?: return@runCatching error("La lista no existe.")
            when (entity.type) {
                PlaylistType.M3U -> refreshM3U(entity)
                PlaylistType.XTREAM -> refreshXtream(entity)
            }
            checkNotNull(playlistDao.getById(id)).toDomain()
        }
    }

    private suspend fun refreshM3U(entity: PlaylistEntity) {
        val url = entity.sourceUrl
            ?: throw IllegalStateException("La lista fue importada desde un archivo local; impórtala de nuevo para actualizarla.")
        val fetched = Http.fetchText(http, url, entity.etag, entity.lastModifiedHeader)
        if (fetched.notModified) {
            // La fuente no cambió: no se vuelve a procesar.
            playlistDao.updateSyncMeta(
                entity.id, System.currentTimeMillis(), entity.channelCount,
                entity.etag, entity.lastModifiedHeader, PlaylistStatus.ACTIVE,
                entity.expiresAt, null
            )
            return
        }
        val text = fetched.text
            ?: throw IllegalStateException("No se pudo descargar la lista (HTTP ${fetched.httpCode}).")
        if (!M3uParser.isValid(text)) throw IllegalStateException("El contenido descargado no es una lista M3U válida.")
        val entries = M3uParser.parse(text)
        if (entries.isEmpty()) throw IllegalStateException("La lista descargada no contiene canales.")

        replaceChannels(entity.id, M3uParser.toEntities(entries, entity.id, System.currentTimeMillis()))
        playlistDao.updateSyncMeta(
            entity.id, System.currentTimeMillis(), channelDao.countByPlaylist(entity.id),
            fetched.etag, fetched.lastModified, PlaylistStatus.ACTIVE, entity.expiresAt, null
        )
    }

    private suspend fun refreshXtream(entity: PlaylistEntity) {
        val user = entity.username ?: throw IllegalStateException("La cuenta no tiene usuario.")
        val password = crypto.decrypt(entity.encryptedPassword)
            ?: throw IllegalStateException("No se pudo descifrar la contraseña guardada. Edita la lista.")
        val server = Urls.normalizeXtreamServer(entity.serverUrl ?: throw IllegalStateException("Sin servidor."))

        val auth = authenticate(server, user, password)
        if (auth.status != PlaylistStatus.ACTIVE) {
            // Vencida: se conserva todo lo local, solo cambia el estado.
            playlistDao.updateStatus(
                entity.id, auth.status,
                "La cuenta está vencida o inactiva según el servidor.", auth.expiresAt
            )
            return
        }
        importXtreamContent(entity.id, server, user, password, previous = entity)
        playlistDao.updateStatus(entity.id, PlaylistStatus.ACTIVE, null, auth.expiresAt)
    }

    // ---------------------------------------------------------------- edición / borrado

    override suspend fun update(
        id: Long,
        name: String,
        url: String?,
        epgUrl: String?,
        server: String?,
        user: String?,
        password: String?
    ): Result<Playlist> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = playlistDao.getById(id) ?: error("La lista no existe.")
            playlistDao.rename(id, name.ifBlank { entity.name })

            if (entity.type == PlaylistType.M3U) {
                val newUrl = url?.takeIf { it.isNotBlank() }?.let { Urls.normalize(it) }
                if (newUrl != null && !Urls.isValidHttpUrl(newUrl)) error("La URL no es válida.")
                playlistDao.updateM3USource(
                    id,
                    newUrl ?: entity.sourceUrl,
                    epgUrl?.takeIf { it.isNotBlank() }?.let { Urls.normalize(it) } ?: entity.epgUrl
                )
            } else {
                val newServer = server?.takeIf { it.isNotBlank() }?.let { Urls.normalizeXtreamServer(it) }
                if (newServer != null && !Urls.isValidHttpUrl(newServer)) error("La URL del servidor no es válida.")
                val newUser = user?.takeIf { it.isNotBlank() } ?: entity.username
                val newPassword = password?.takeIf { it.isNotBlank() }
                    ?: crypto.decrypt(entity.encryptedPassword)
                playlistDao.updateXtreamCredentials(
                    id,
                    newServer ?: entity.serverUrl,
                    newUser,
                    newPassword?.let { crypto.encrypt(it) } ?: entity.encryptedPassword
                )
            }
            // Re-sincroniza con las credenciales nuevas.
            refresh(id).onFailure { throw it }
            checkNotNull(playlistDao.getById(id)).toDomain()
        }
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        playlistDao.delete(id)
        channelDao.deleteByPlaylist(id)
        channelDao.deleteOrphanHistory()
    }

    override suspend fun setDefault(id: Long) = withContext(Dispatchers.IO) {
        playlistDao.makeDefault(id)
    }

    // ---------------------------------------------------------------- prueba de conexión

    override suspend fun testConnection(id: Long): TestConnectionResult = withContext(Dispatchers.IO) {
        val entity = playlistDao.getById(id)
            ?: return@withContext TestConnectionResult(false, "La lista no existe.")
        when (entity.type) {
            PlaylistType.M3U -> {
                val url = entity.sourceUrl
                    ?: return@withContext TestConnectionResult(
                        true,
                        "Lista importada localmente con ${entity.channelCount} canales guardados."
                    )
                val code = Http.probe(http, url)
                if (code in 200..399) {
                    TestConnectionResult(true, "Conexión correcta (HTTP $code). ${entity.channelCount} canales guardados.")
                } else {
                    TestConnectionResult(
                        false,
                        "No se pudo conectar (HTTP $code).",
                        details = "Verifica la URL o el estado del servidor."
                    )
                }
            }
            PlaylistType.XTREAM -> {
                val user = entity.username ?: ""
                val password = crypto.decrypt(entity.encryptedPassword) ?: ""
                val server = entity.serverUrl ?: ""
                return@withContext try {
                    val auth = authenticate(server, user, password)
                    val expiry = auth.expiresAt
                    val details = buildString {
                        auth.maxConnections?.let { append("Conexiones máximas: $it. ") }
                        auth.activeConnections?.let { append("Conexiones activas: $it.") }
                    }
                    when (auth.status) {
                        PlaylistStatus.ACTIVE -> TestConnectionResult(
                            true, "Conectado. La cuenta está activa.", expiry, details
                        )
                        PlaylistStatus.EXPIRED -> TestConnectionResult(
                            false, "La cuenta está vencida o inactiva según el servidor.", expiry, details
                        )
                        else -> TestConnectionResult(
                            false, "El servidor rechazó el acceso. Revisa usuario y contraseña.", null, details
                        )
                    }
                } catch (e: Exception) {
                    TestConnectionResult(false, "No se pudo conectar con el servidor (${e.message ?: "error"}).")
                }
            }
        }
    }

    // ---------------------------------------------------------------- Xtream

    private data class AuthResult(
        val status: PlaylistStatus,
        val expiresAt: Long?,
        val maxConnections: String? = null,
        val activeConnections: String? = null
    )

    private suspend fun authenticate(server: String, user: String, password: String): AuthResult {
        val apiUrl = "$server/player_api.php"
        val response = try {
            xtream.authenticate(apiUrl, user, password)
        } catch (e: retrofit2.HttpException) {
            throw IllegalStateException("El servidor respondió HTTP ${e.code()}. Revisa la URL.")
        } catch (e: Exception) {
            throw IllegalStateException("No se pudo conectar con el servidor (${e.javaClass.simpleName}).")
        }
        val info = response.user_info
            ?: throw IllegalStateException("El servidor no devolvió información de la cuenta.")
        val expDate = info.exp_date?.toLongOrNull()?.times(1000L)
        val status = when {
            info.status?.equals("Active", ignoreCase = true) == true &&
                (expDate == null || expDate > System.currentTimeMillis()) -> PlaylistStatus.ACTIVE
            info.status?.equals("Banned", true) == true -> PlaylistStatus.EXPIRED
            expDate != null && expDate <= System.currentTimeMillis() -> PlaylistStatus.EXPIRED
            else -> PlaylistStatus.ERROR
        }
        return AuthResult(status, expDate, info.max_connections, info.active_cons)
    }

    private suspend fun importXtreamContent(
        playlistId: Long,
        server: String,
        user: String,
        password: String,
        previous: PlaylistEntity? = null
    ) {
        val apiUrl = "$server/player_api.php"
        val auth = authenticate(server, user, password)
        if (auth.status != PlaylistStatus.ACTIVE) {
            throw IllegalStateException("La cuenta no está activa; no se pueden descargar los canales.")
        }

        val now = System.currentTimeMillis()
        val channels = ArrayList<ChannelEntity>(4096)

        // Nombres de categorías (live + vod + series) para etiquetar canales.
        val catNameById = HashMap<String, String>(256)
        for (action in listOf("get_live_categories", "get_vod_categories", "get_series_categories")) {
            runCatching { xtream.categories(apiUrl, user, password, action) }
                .getOrDefault(emptyList())
                .forEach { cat ->
                    val id = cat.category_id ?: return@forEach
                    catNameById[id] = cat.category_name ?: id
                }
        }

        xtream.liveStreams(apiUrl, user, password).forEach { stream ->
            val sid = stream.stream_id ?: return@forEach
            channels.add(
                ChannelEntity(
                    playlistId = playlistId,
                    xtreamStreamId = sid.toString(),
                    type = ChannelType.LIVE,
                    name = stream.name ?: "Canal $sid",
                    logoUrl = stream.stream_icon?.takeIf { it.isNotBlank() },
                    streamUrl = buildXtreamUrl(server, user, password, "live", sid, "m3u8", stream.direct_source),
                    categoryId = stream.category_id,
                    categoryName = catNameById[stream.category_id] ?: stream.category_id,
                    tvgId = stream.epg_channel_id?.takeIf { it.isNotBlank() },
                    epgChannelId = stream.epg_channel_id?.takeIf { it.isNotBlank() },
                    addedAt = now
                )
            )
        }

        xtream.vodStreams(apiUrl, user, password).forEach { movie ->
            val sid = movie.stream_id ?: return@forEach
            channels.add(
                ChannelEntity(
                    playlistId = playlistId,
                    xtreamStreamId = sid.toString(),
                    type = ChannelType.MOVIE,
                    name = movie.name ?: "Película $sid",
                    logoUrl = movie.stream_icon?.takeIf { it.isNotBlank() },
                    streamUrl = buildXtreamUrl(
                        server, user, password, "movie", sid,
                        movie.container_extension ?: "mp4", movie.direct_source
                    ),
                    categoryId = movie.category_id,
                    categoryName = catNameById[movie.category_id] ?: movie.category_id,
                    addedAt = now,
                    containerExtension = movie.container_extension ?: "mp4"
                )
            )
        }

        xtream.seriesList(apiUrl, user, password).forEach { series ->
            val sid = series.series_id ?: return@forEach
            channels.add(
                ChannelEntity(
                    playlistId = playlistId,
                    xtreamStreamId = sid.toString(),
                    type = ChannelType.SERIES,
                    name = series.name ?: "Serie $sid",
                    logoUrl = series.cover?.takeIf { it.isNotBlank() },
                    // Las series no se reproducen directamente: primero se listan episodios.
                    streamUrl = "",
                    categoryId = series.category_id,
                    categoryName = catNameById[series.category_id] ?: series.category_id,
                    addedAt = now
                )
            )
        }

        replaceChannels(playlistId, channels, previous)
        playlistDao.updateSyncMeta(
            playlistId, System.currentTimeMillis(), channelDao.countByPlaylist(playlistId),
            previous?.etag, previous?.lastModifiedHeader, PlaylistStatus.ACTIVE,
            auth.expiresAt, null
        )
    }

    /** URL de stream Xtream. Si el proveedor entrega direct_source se respeta tal cual. */
    private fun buildXtreamUrl(
        server: String,
        user: String,
        password: String,
        kind: String,
        streamId: Long,
        extension: String,
        directSource: String?
    ): String {
        if (!directSource.isNullOrBlank()) {
            return if (directSource.startsWith("http", true)) directSource
            else "$server/$directSource"
        }
        return "$server/$kind/" +
            "${URLEncoder.encode(user, "UTF-8")}/" +
            "${URLEncoder.encode(password, "UTF-8")}/" +
            "$streamId.$extension"
    }

    // ---------------------------------------------------------------- series

    override suspend fun fetchSeriesDetail(channel: Channel): Result<SeriesDetail> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entity = playlistDao.getById(channel.playlistId)
                    ?: error("La lista de la serie ya no existe.")
                require(entity.type == PlaylistType.XTREAM) { "Esta serie no pertenece a una lista Xtream." }
                val user = entity.username ?: error("Cuenta sin usuario.")
                val password = crypto.decrypt(entity.encryptedPassword) ?: error("Credenciales no disponibles.")
                val server = Urls.normalizeXtreamServer(entity.serverUrl ?: error("Sin servidor."))
                val seriesId = channel.xtreamStreamId ?: error("Serie sin id.")

                val api = "$server/player_api.php"
                val detail = xtream.seriesInfo(api, user, password, seriesId = seriesId)
                val episodes = ArrayList<SeriesEpisode>(64)
                detail.episodes?.forEach { (season, list) ->
                    val seasonNum = season.toIntOrNull() ?: 1
                    list.forEach { ep ->
                        val id = ep.id ?: return@forEach
                        episodes.add(
                            SeriesEpisode(
                                id = id.toString(),
                                season = ep.season ?: seasonNum,
                                episode = ep.episode_num ?: episodes.size + 1,
                                title = ep.title ?: "Episodio ${ep.episode_num ?: ""}",
                                extension = ep.container_extension ?: "mp4",
                                plot = ep.info?.plot,
                                poster = ep.info?.movie_image
                            )
                        )
                    }
                }
                SeriesDetail(
                    seriesName = detail.info?.name ?: channel.name,
                    plot = detail.info?.plot,
                    poster = detail.info?.cover ?: channel.logoUrl,
                    cast = detail.info?.cast,
                    genre = detail.info?.genre,
                    rating = detail.info?.rating,
                    releaseDate = detail.info?.releaseDate ?: detail.info?.release_date,
                    episodes = episodes.sortedWith(compareBy({ it.season }, { it.episode }))
                )
            }
        }

    override suspend fun buildEpisodeUrl(
        channel: Channel,
        episodeId: String,
        extension: String?
    ): String? = withContext(Dispatchers.IO) {
        val entity = playlistDao.getById(channel.playlistId) ?: return@withContext null
        val user = entity.username ?: return@withContext null
        val password = crypto.decrypt(entity.encryptedPassword) ?: return@withContext null
        val server = Urls.normalizeXtreamServer(entity.serverUrl ?: return@withContext null)
        "$server/series/" +
            "${URLEncoder.encode(user, "UTF-8")}/" +
            "${URLEncoder.encode(password, "UTF-8")}/" +
            "$episodeId.${extension ?: "mp4"}"
    }

    // ---------------------------------------------------------------- interior

    /**
     * Sustituye los canales de una lista conservando favoritos e historial:
     * las filas nuevas heredan favorito si coinciden por stream-id o URL, y el
     * historial se remapea a los ids nuevos.
     */
    private suspend fun replaceChannels(
        playlistId: Long,
        newChannels: List<ChannelEntity>,
        previous: PlaylistEntity? = null
    ) {
        database.withTransaction {
            // 1. Favoritos actuales (claves estables).
            val favoriteKeys = channelDao.favoritesOf(playlistId)
            val favStreamIds = favoriteKeys.mapNotNull { it.xtreamStreamId }.distinct()
            val favUrls = favoriteKeys.map { it.streamUrl }.filter { it.isNotBlank() }.distinct()

            // 2. Historial con claves estables y marca de tiempo original.
            val oldHistory = channelDao.historyKeysOfPlaylist(playlistId)

            // 3. Reemplazo de canales en lotes (listas de miles de entradas).
            channelDao.deleteByPlaylist(playlistId)
            channelDao.deleteOrphanHistory()
            val insertedIds = ArrayList<Long>(newChannels.size)
            var index = 0
            while (index < newChannels.size) {
                val chunk = newChannels.subList(index, minOf(index + INSERT_CHUNK, newChannels.size))
                insertedIds.addAll(channelDao.insertAll(chunk))
                index += INSERT_CHUNK
            }

            // 4. Restaurar favoritos.
            if (favStreamIds.isNotEmpty()) channelDao.restoreFavoritesByStreamId(playlistId, favStreamIds)
            if (favUrls.isNotEmpty()) channelDao.restoreFavoritesByUrl(playlistId, favUrls)

            // 5. Remapear historial sobre los ids nuevos (conservando fecha).
            val newByKey = HashMap<String, Long>(newChannels.size)
            newChannels.forEachIndexed { i, entity ->
                insertedIds.getOrNull(i)?.let { newId ->
                    newByKey[entity.xtreamStreamId ?: entity.streamUrl] = newId
                }
            }
            for (row in oldHistory) {
                val newId = newByKey[row.xtreamStreamId ?: row.streamUrl] ?: continue
                channelDao.insertHistory(
                    com.iptvplayerpro.data.local.HistoryEntity(
                        channelId = newId,
                        watchedAt = row.lastWatched
                    )
                )
            }
            channelDao.trimHistory()
        }
    }

    /** Marca listas cuyo tiempo de expiración ya pasó (sin tocar el servidor). */
    suspend fun revalidateExpirations() = withContext(Dispatchers.IO) {
        runCatching {
            playlistDao.markExpiredByTime(
                active = PlaylistStatus.ACTIVE,
                expired = PlaylistStatus.EXPIRED,
                now = System.currentTimeMillis()
            )
        }.onFailure { Log.w(TAG, "No se pudieron revalidar expiraciones: ${it.message}") }
    }

    private fun error(message: String): Nothing = throw IllegalStateException(message)
}
