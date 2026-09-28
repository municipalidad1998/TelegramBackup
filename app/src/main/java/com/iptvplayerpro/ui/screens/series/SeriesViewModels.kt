package com.iptvplayerpro.ui.screens.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.iptvplayerpro.di.AppContainer
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.domain.model.Playlist
import com.iptvplayerpro.domain.model.SeriesDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Grid de series con selector de lista y búsqueda. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SeriesViewModel(container: AppContainer) : ViewModel() {

    private val channelRepository = container.channelRepository
    private val playlistRepository = container.playlistRepository

    val selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedCategory = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")

    val playlists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<com.iptvplayerpro.domain.model.CategoryCount>> = combine(
        playlists, selectedPlaylistId
    ) { lists, selected -> lists.filter { selected == null || it.id == selected }.map { it.id } }
        .flatMapLatest { ids -> channelRepository.categories(ChannelType.SERIES, ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val series: Flow<PagingData<Channel>> = combine(
        playlists, selectedPlaylistId, selectedCategory, query.debounce(250)
    ) { lists, selected, category, q ->
        Triple(lists.filter { selected == null || it.id == selected }, category, q)
    }.flatMapLatest { (lists, category, q) ->
        val nameById = lists.associate { it.id to it.name }
        channelRepository.channelsPager(ChannelType.SERIES, lists.map { it.id }, category, q)
            .map { paging -> paging.map { it.copy(playlistName = nameById[it.playlistId] ?: "") } }
    }.cachedIn(viewModelScope)

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
}

data class SeriesDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val detail: SeriesDetail? = null,
    val channel: Channel? = null
)

/** Detalle de una serie (Xtream): temporadas y episodios. */
class SeriesDetailViewModel(container: AppContainer) : ViewModel() {

    private val playlistRepository = container.playlistRepository
    private val channelRepository = container.channelRepository

    private val _state = MutableStateFlow(SeriesDetailUiState())
    val state: StateFlow<SeriesDetailUiState> = _state.asStateFlow()

    fun load(channelId: Long) {
        if (_state.value.detail != null || _state.value.channel?.id == channelId) return
        viewModelScope.launch {
            _state.value = SeriesDetailUiState(loading = true)
            val channel = channelRepository.getChannel(channelId)
            if (channel == null) {
                _state.value = SeriesDetailUiState(loading = false, error = "La serie ya no existe.")
                return@launch
            }
            _state.value = SeriesDetailUiState(loading = true, channel = channel)
            playlistRepository.fetchSeriesDetail(channel)
                .onSuccess { detail ->
                    _state.value = SeriesDetailUiState(loading = false, detail = detail, channel = channel)
                }
                .onFailure { e ->
                    _state.value = SeriesDetailUiState(
                        loading = false,
                        channel = channel,
                        error = e.message ?: "No se pudo cargar la serie."
                    )
                }
        }
    }

    fun buildEpisodeUrl(
        episodeId: String,
        extension: String?,
        onReady: (String) -> Unit
    ) {
        val channel = _state.value.channel ?: return
        viewModelScope.launch {
            val url = playlistRepository.buildEpisodeUrl(channel, episodeId, extension)
            if (url != null) onReady(url)
        }
    }
}
