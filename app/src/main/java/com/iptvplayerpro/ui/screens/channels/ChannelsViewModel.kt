package com.iptvplayerpro.ui.screens.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.iptvplayerpro.di.AppContainer
import com.iptvplayerpro.domain.model.CategoryCount
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.domain.model.Playlist
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de TV en vivo / Películas: selector de lista, categorías,
 * búsqueda con debounce y paginación (miles de canales sin congelar la UI).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ChannelsViewModel(
    container: AppContainer,
    private val type: ChannelType
) : ViewModel() {

    private val channelRepository = container.channelRepository
    private val playlistRepository = container.playlistRepository

    /** null = todas las listas. */
    val selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedCategory = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")

    val playlists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryCount>> = combine(
        playlists, selectedPlaylistId
    ) { lists, selected ->
        lists.filter { selected == null || it.id == selected }.map { it.id }
    }.flatMapLatest { ids ->
        channelRepository.categories(type, ids)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val channels: Flow<PagingData<Channel>> = combine(
        playlists,
        selectedPlaylistId,
        selectedCategory,
        query.debounce(250)
    ) { lists, selected, category, q ->
        Selection(lists.filter { selected == null || it.id == selected }, category, q)
    }.flatMapLatest { selection ->
        val nameById = selection.playlists.associate { it.id to it.name }
        channelRepository.channelsPager(
            type = type,
            playlistIds = selection.playlists.map { it.id },
            categoryId = selection.category,
            query = selection.query
        ).map { paging ->
            paging.map { channel ->
                channel.copy(playlistName = nameById[channel.playlistId] ?: channel.playlistName)
            }
        }
    }.cachedIn(viewModelScope)

    private data class Selection(
        val playlists: List<Playlist>,
        val category: String?,
        val query: String
    )

    fun setPlaylist(id: Long?) {
        selectedPlaylistId.value = id
        selectedCategory.value = null
    }

    fun setCategory(id: String?) {
        selectedCategory.value = id
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            channelRepository.setFavorite(channel.id, !channel.isFavorite)
        }
    }

    /** Abre la búsqueda global. */
    fun globalSearchRoute(): String = com.iptvplayerpro.ui.nav.Routes.SEARCH
}
