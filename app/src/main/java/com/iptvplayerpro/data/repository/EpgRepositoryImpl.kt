package com.iptvplayerpro.data.repository

import androidx.room.withTransaction
import com.iptvplayerpro.core.crypto.CryptoManager
import com.iptvplayerpro.core.util.Urls
import com.iptvplayerpro.data.local.ChannelDao
import com.iptvplayerpro.data.local.ChannelEntity
import com.iptvplayerpro.data.local.EpgChannelEntity
import com.iptvplayerpro.data.local.EpgDao
import com.iptvplayerpro.data.local.EpgProgrammeEntity
import com.iptvplayerpro.data.local.IptvDatabase
import com.iptvplayerpro.data.local.PlaylistDao
import com.iptvplayerpro.data.local.PlaylistEntity
import com.iptvplayerpro.data.remote.Http
import com.iptvplayerpro.data.remote.epg.XmlTvParser
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.domain.model.EpgChannelListItem
import com.iptvplayerpro.domain.model.GuideChannel
import com.iptvplayerpro.domain.model.GuideData
import com.iptvplayerpro.domain.model.NowNext
import com.iptvplayerpro.domain.model.PlaylistType
import com.iptvplayerpro.domain.repository.EpgRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Repositorio EPG: combina XMLTV (por URL) y el EPG que entrega Xtream
 * (xmltv.php). La asociación canal↔EPG es automática por tvg-id / nombre,
 * con corrección manual disponible.
 */
class EpgRepositoryImpl(
    private val database: IptvDatabase,
    private val http: OkHttpClient,
    private val crypto: CryptoManager
) : EpgRepository {

    private val epgDao = database.epgDao()
    private val channelDao = database.channelDao()
    private val playlistDao = database.playlistDao()

    companion object {
        private const val TICK_MS = 30_000L
        private const val PROGRAMME_BATCH = 2000
        private const val PROGRAMME_LIMIT = 200_000
    }

    // ---------------------------------------------------------------- ahora/siguiente

    override fun observeNowNext(epgChannelId: String?, channelName: String?): Flow<NowNext> = flow {
        // Resolución del id EPG: directo, o por nombre del canal (asociación automática).
        var id = epgChannelId?.takeIf { it.isNotBlank() }
        if (id == null && !channelName.isNullOrBlank()) {
            id = epgDao.findChannelByName(channelName)?.epgChannelId
        }
        if (id == null) {
            emit(NowNext(null, null))
            return@flow
        }
        val finalId = id
        // Señal combinada: cambios en la tabla EPG + tick cada 30 s (avanza "ahora").
        val signal = combine(
            epgDao.observeProgrammeCount(),
            ticker()
        ) { _, _ -> System.currentTimeMillis() }
        emitAll(
            signal.map { now ->
                NowNext(
                    now = epgDao.nowProgramme(finalId, now)?.toDomain(),
                    next = epgDao.nextProgramme(finalId, now)?.toDomain()
                )
            }.distinctUntilChanged()
        )
    }

    private fun ticker(): Flow<Long> = flow {
        while (kotlin.coroutines.coroutineContext.isActive) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }

    // ---------------------------------------------------------------- refresco

    override suspend fun refreshAll(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val playlists = playlistDao.getAll()
            val channels = ArrayList<EpgChannelEntity>(1024)
            val programmes = ArrayList<EpgProgrammeEntity>(16384)
            var total = 0

            for (playlist in playlists) {
                val url = epgUrlFor(playlist) ?: continue
                val fetched = try {
                    Http.fetchText(http, url)
                } catch (e: Exception) {
                    continue // Fuente no disponible: se conserva el EPG existente.
                }
                val text = fetched.text ?: continue

                XmlTvParser.parse(text.byteInputStream()) { batchChannels, batchProgrammes ->
                    channels.addAll(batchChannels)
                    programmes.addAll(batchProgrammes)
                    if (programmes.size > PROGRAMME_LIMIT) {
                        throw IllegalStateException("EPG demasiado grande")
                    }
                }
                total += programmes.size
            }

            if (programmes.isNotEmpty() || channels.isNotEmpty()) {
                replaceEpg(channels, programmes)
            }
            epgDao.countProgrammes()
        }
    }

    /** URL XMLTV de una lista: la explícita o la que expone Xtream. */
    private suspend fun epgUrlFor(playlist: PlaylistEntity): String? {
        playlist.epgUrl?.takeIf { it.isNotBlank() }?.let { return Urls.normalize(it) }
        if (playlist.type != PlaylistType.XTREAM) return null
        val server = playlist.serverUrl ?: return null
        val user = playlist.username ?: return null
        val password = crypto.decrypt(playlist.encryptedPassword) ?: return null
        return "$server/xmltv.php?username=" +
            java.net.URLEncoder.encode(user, "UTF-8") +
            "&password=" + java.net.URLEncoder.encode(password, "UTF-8")
    }

    private suspend fun replaceEpg(channels: List<EpgChannelEntity>, programmes: List<EpgProgrammeEntity>) {
        database.withTransaction {
            epgDao.clearProgrammes()
            epgDao.clearEpgChannels()
            epgDao.insertChannels(channels)
            var index = 0
            while (index < programmes.size) {
                val end = minOf(index + PROGRAMME_BATCH, programmes.size)
                epgDao.insertProgrammes(programmes.subList(index, end))
                index = end
            }
        }
    }

    // ---------------------------------------------------------------- guía

    override suspend fun guideWindow(from: Long, to: Long, maxChannels: Int): GuideData =
        withContext(Dispatchers.IO) {
            // Canales en vivo con EPG (asociado o por nombre).
            val withEpg = channelDao.channelsWithEpg(ChannelType.LIVE, maxChannels)
            val nameFallback = HashMap<String, String>(256)
            if (withEpg.isEmpty()) {
                val all = channelDao.channelsByType(ChannelType.LIVE, maxChannels)
                for (c in all) {
                    val id = epgDao.findChannelByName(c.name)?.epgChannelId
                    if (id != null) nameFallback[c.name] = id
                }
            }
            val channels: List<ChannelEntity> = withEpg.ifEmpty {
                channelDao.channelsByType(ChannelType.LIVE, maxChannels)
            }

            val ids = channels.mapNotNull { it.epgChannelId ?: nameFallback[it.name] }.distinct()
            val byChannel = if (ids.isEmpty()) emptyMap()
            else epgDao.programmesWindow(ids, from, to).groupBy { it.epgChannelId }

            GuideData(
                channels = channels.map { entity ->
                    GuideChannel(
                        channel = entity.toDomain(playlistName = ""),
                        programmes = (byChannel[entity.epgChannelId]
                            ?: byChannel[nameFallback[entity.name]]
                            ?: emptyList()).map { it.toDomain() }
                    )
                },
                windowStart = from,
                windowEnd = to
            )
        }

    override fun epgChannels(): Flow<List<EpgChannelListItem>> =
        epgDao.observeEpgChannels(1000).map { list ->
            list.map { EpgChannelListItem(it.epgChannelId, it.displayName) }
        }

    override suspend fun setManualMatch(channelId: Long, epgChannelId: String) {
        channelDao.setEpgMatch(channelId, epgChannelId)
    }

    override suspend fun clearEpg() {
        database.withTransaction {
            epgDao.clearProgrammes()
            epgDao.clearEpgChannels()
        }
    }
}
