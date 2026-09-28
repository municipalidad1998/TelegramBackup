package com.iptvplayerpro.data.remote.m3u

import com.iptvplayerpro.data.local.ChannelEntity
import com.iptvplayerpro.domain.model.ChannelType

/**
 * Parser de listas M3U/M3U8 (formato extendido IPTV).
 *
 * Soporta:
 * - `#EXTINF:-1 tvg-id="" tvg-name="" tvg-logo="" group-title="",Nombre`
 * - `#EXTGRP:<grupo>`
 * - `#EXTVLCOPT:...` (ignorado)
 * - Clasificación automática TV en vivo / películas / series por grupo.
 *
 * El parseo es intencionalmente simple y sin dependencias para poder
 * ejecutarse en segundo plano (Dispatchers.IO) con listas de decenas de
 * miles de entradas.
 */
object M3uParser {

    private val ATTR_REGEX = Regex("([a-zA-Z0-9_-]+)=\"([^\"]*)\"")

    /** Palabras clave para clasificar contenido por group-title. */
    private val MOVIE_KEYWORDS = listOf(
        "pelicula", "película", "pelicul", "movie", "movies", "cine", "vod", "4k movie"
    )
    private val SERIES_KEYWORDS = listOf(
        "serie", "series", "temporada", "season", "episode", "episodio", "novela"
    )

    data class M3uEntry(
        val name: String,
        val url: String,
        val tvgId: String?,
        val tvgName: String?,
        val logoUrl: String?,
        val group: String?,
        val type: ChannelType
    )

    /** true si el contenido parece una lista M3U válida. */
    fun isValid(content: String): Boolean {
        val head = content.take(8192)
        return head.contains("#EXTM3U") || head.contains("#EXTINF")
    }

    fun parse(content: String): List<M3uEntry> {
        val entries = ArrayList<M3uEntry>(2048)
        var pendingName: String? = null
        var pendingTvgId: String? = null
        var pendingTvgName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.isEmpty() || line.startsWith("#EXTM3U") -> Unit

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val commaIndex = line.indexOf(',')
                    val attrsPart = if (commaIndex >= 0) line.substring(0, commaIndex) else line
                    val title = if (commaIndex >= 0) line.substring(commaIndex + 1).trim() else ""
                    val attrs = parseAttrs(attrsPart)
                    pendingName = title.ifBlank { attrs["tvg-name"] ?: attrs["tvg-id"] ?: "" }
                    pendingTvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                    pendingTvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                    pendingLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                        ?: attrs["logo"]?.takeIf { it.isNotBlank() }
                    pendingGroup = attrs["group-title"]?.takeIf { it.isNotBlank() }
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pendingGroup = line.substringAfter(':').trim().takeIf { it.isNotBlank() } ?: pendingGroup
                }

                line.startsWith("#") -> Unit // #EXTVLCOPT, #KODIPROP, comentarios…

                else -> {
                    // Línea de URL: cierra la entrada pendiente.
                    if (pendingName != null && looksLikeUrl(line)) {
                        val group = pendingGroup ?: ""
                        entries.add(
                            M3uEntry(
                                name = pendingName,
                                url = line,
                                tvgId = pendingTvgId,
                                tvgName = pendingTvgName,
                                logoUrl = pendingLogo,
                                group = group,
                                type = classify(group, pendingName)
                            )
                        )
                    }
                    pendingName = null
                    pendingTvgId = null
                    pendingTvgName = null
                    pendingLogo = null
                    pendingGroup = null
                }
            }
        }
        return entries
    }

    /** Convierte entradas parseadas a entidades Room listas para insertar. */
    fun toEntities(entries: List<M3uEntry>, playlistId: Long, now: Long): List<ChannelEntity> =
        entries.map { entry ->
            ChannelEntity(
                playlistId = playlistId,
                type = entry.type,
                name = entry.name,
                logoUrl = entry.logoUrl,
                streamUrl = entry.url,
                categoryId = entry.group?.takeIf { it.isNotBlank() },
                categoryName = entry.group?.takeIf { it.isNotBlank() },
                tvgId = entry.tvgId,
                epgChannelId = entry.tvgId,
                addedAt = now
            )
        }

    private fun parseAttrs(part: String): Map<String, String> {
        val map = HashMap<String, String>(8)
        for (match in ATTR_REGEX.findAll(part)) {
            val (key, value) = match.destructured
            map[key.lowercase()] = value
        }
        return map
    }

    private fun looksLikeUrl(line: String): Boolean =
        line.startsWith("http://", true) ||
            line.startsWith("https://", true) ||
            line.startsWith("rtmp://", true) ||
            line.startsWith("rtsp://", true) ||
            line.startsWith("udp://", true) ||
            line.startsWith("rtp://", true)

    /** Heurística de clasificación por group-title / nombre. */
    internal fun classify(group: String?, name: String): ChannelType {
        val haystack = "${group ?: ""} $name".lowercase()
        return when {
            SERIES_KEYWORDS.any { haystack.contains(it) } -> ChannelType.SERIES
            MOVIE_KEYWORDS.any { haystack.contains(it) } -> ChannelType.MOVIE
            else -> ChannelType.LIVE
        }
    }
}
