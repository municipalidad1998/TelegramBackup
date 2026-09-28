package com.iptvplayerpro.data.repository

import com.iptvplayerpro.data.local.ChannelEntity
import com.iptvplayerpro.data.local.EpgProgrammeEntity
import com.iptvplayerpro.data.local.HistoryRow
import com.iptvplayerpro.data.local.PlaylistEntity
import com.iptvplayerpro.data.local.VpnProfileEntity
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.domain.model.EpgProgramme
import com.iptvplayerpro.domain.model.Playlist
import com.iptvplayerpro.domain.model.VpnProfile
import com.iptvplayerpro.domain.model.WatchedEntry

/** Mapeos entidad Room ↔ modelo de dominio. */

fun PlaylistEntity.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    type = type,
    sourceUrl = sourceUrl,
    epgUrl = epgUrl,
    serverUrl = serverUrl,
    username = username,
    status = status,
    isDefault = isDefault,
    autoRefresh = autoRefresh,
    lastUpdated = lastUpdated,
    expiresAt = expiresAt,
    channelCount = channelCount,
    lastError = lastError
)

fun ChannelEntity.toDomain(playlistName: String): Channel = Channel(
    id = id,
    playlistId = playlistId,
    playlistName = playlistName,
    type = type,
    name = name,
    logoUrl = logoUrl,
    streamUrl = streamUrl,
    categoryId = categoryId,
    categoryName = categoryName,
    tvgId = tvgId,
    epgChannelId = epgChannelId,
    isFavorite = isFavorite,
    xtreamStreamId = xtreamStreamId,
    containerExtension = containerExtension
)

fun EpgProgrammeEntity.toDomain(): EpgProgramme = EpgProgramme(
    id = id,
    epgChannelId = epgChannelId,
    title = title,
    description = description,
    start = start,
    end = end
)

fun HistoryRow.toEntry(playlistName: String): WatchedEntry = WatchedEntry(
    channel = channel.toDomain(playlistName),
    watchedAt = watchedAt
)

fun VpnProfileEntity.toDomain(): VpnProfile = VpnProfile(
    id = id,
    name = name,
    country = country,
    countryCode = countryCode,
    provider = provider,
    serverHost = serverHost,
    lastConnectedAt = lastConnectedAt
)
