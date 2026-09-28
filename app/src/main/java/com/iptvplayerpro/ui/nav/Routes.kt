package com.iptvplayerpro.ui.nav

import android.net.Uri
import com.iptvplayerpro.domain.model.ChannelType

/** Rutas de navegación. */
object Routes {
    const val HOME = "home"
    const val CHANNELS = "channels/{type}"
    const val SERIES = "series"
    const val SERIES_DETAIL = "seriesDetail/{channelId}"
    const val LIBRARY = "library?tab={tab}"
    const val EPG = "epg"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST_EDIT = "playlistEdit?id={id}"
    const val PLAYER = "player/{channelId}?url={url}&name={name}"
    const val VPN = "vpn"
    const val SETTINGS = "settings"
    const val SEARCH = "search"

    fun channels(type: ChannelType): String = "channels/${type.name}"

    fun seriesDetail(channelId: Long): String = "seriesDetail/$channelId"

    /** 0 = Favoritos, 1 = Recientes, 2 = Historial. */
    fun library(tab: Int = 0): String = "library?tab=$tab"

    /** id = -1 significa «nueva lista». */
    fun playlistEdit(id: Long = -1L): String = "playlistEdit?id=$id"

    fun player(channelId: Long): String = "player/$channelId?url=&name="

    fun playerExternal(url: String, name: String): String =
        "player/-1?url=${Uri.encode(url)}&name=${Uri.encode(name)}"
}
