package com.iptvplayerpro.data.remote.xtream

import kotlinx.serialization.Serializable

// ---- DTOs de la player_api.php de Xtream Codes ----
// Todos los campos son opcionales: los proveedores devuelven formatos dispares.

@Serializable
data class XtreamAuthResponse(
    val user_info: XtreamUserInfo? = null,
    val server_info: XtreamServerInfo? = null
)

@Serializable
data class XtreamUserInfo(
    val username: String? = null,
    val password: String? = null,
    val message: String? = null,
    val status: String? = null,
    val exp_date: String? = null,
    val is_trial: String? = null,
    val active_cons: String? = null,
    val max_connections: String? = null,
    val created_at: String? = null,
    val allowed_output_formats: List<String>? = null
)

@Serializable
data class XtreamServerInfo(
    val url: String? = null,
    val port: String? = null,
    val https_port: String? = null,
    val server_protocol: String? = null
)

@Serializable
data class XtreamCategory(
    val category_id: String? = null,
    val category_name: String? = null,
    val parent_id: Int? = null
)

@Serializable
data class XtreamStream(
    val num: Int? = null,
    val name: String? = null,
    val stream_type: String? = null,
    val stream_id: Long? = null,
    val stream_icon: String? = null,
    val epg_channel_id: String? = null,
    val added: String? = null,
    val category_id: String? = null,
    val tv_archive: Int? = null,
    val direct_source: String? = null,
    val container_extension: String? = null,
    val rating: String? = null,
    val plot: String? = null,
    val custom_sid: String? = null,
    val tv_archive_duration: Int? = null
)

@Serializable
data class XtreamSeries(
    val series_id: Long? = null,
    val name: String? = null,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val release_date: String? = null,
    val last_modified: String? = null,
    val rating: String? = null,
    val rating_5based: Double? = null,
    val category_id: String? = null,
    val backdrop_path: List<String>? = null,
    val youtube_trailer: String? = null
)

@Serializable
data class XtreamSeriesInfo(
    val info: XtreamSeriesInfoDetail? = null,
    val episodes: Map<String, List<XtreamEpisode>>? = null
)

@Serializable
data class XtreamSeriesInfoDetail(
    val name: String? = null,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val release_date: String? = null,
    val rating: String? = null,
    val last_modified: String? = null,
    val series_id: Long? = null
)

@Serializable
data class XtreamEpisode(
    val id: Long? = null,
    val episode_num: Int? = null,
    val title: String? = null,
    val container_extension: String? = null,
    val season: Int? = null,
    val info: XtreamEpisodeInfo? = null
)

@Serializable
data class XtreamEpisodeInfo(
    val movie_image: String? = null,
    val plot: String? = null,
    val duration: String? = null,
    val rating: String? = null,
    val releasedate: String? = null
)

@Serializable
data class XtreamEpgList(
    val epg_listings: List<XtreamEpgListing>? = null
)

@Serializable
data class XtreamEpgListing(
    val id: String? = null,
    val epg_id: String? = null,
    val title: String? = null,
    val lang: String? = null,
    val start: String? = null,
    val end: String? = null,
    val description: String? = null,
    val channel_id: String? = null,
    val start_timestamp: String? = null,
    val stop_timestamp: String? = null
)
