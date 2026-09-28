package com.iptvplayerpro.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iptvplayerpro.di.AppContainer
import com.iptvplayerpro.di.containerViewModel
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.ui.components.AppTopBar
import com.iptvplayerpro.ui.components.ChannelRow
import com.iptvplayerpro.ui.components.EmptyState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

private val TYPE_LABELS = mapOf(
    ChannelType.LIVE to "TV en vivo",
    ChannelType.MOVIE to "Películas",
    ChannelType.SERIES to "Series"
)

@OptIn(FlowPreview::class)
private class SearchViewModel(container: AppContainer) : ViewModel() {
    private val channelRepository = container.channelRepository

    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<Channel>>(emptyList())
    val searching = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            query.debounce(300).collect { q ->
                if (q.isBlank()) {
                    results.value = emptyList()
                } else {
                    searching.value = true
                    results.value = runCatching { channelRepository.search(q) }.getOrDefault(emptyList())
                    searching.value = false
                }
            }
        }
    }
}

/** Búsqueda global en todas las listas y tipos de contenido. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPlayChannel: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit
) {
    val viewModel = containerViewModel(SearchViewModel::class) { container, _ -> SearchViewModel(container) }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()

    val grouped = results.groupBy { it.type }

    Scaffold(
        topBar = { AppTopBar(title = "Búsqueda global", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                placeholder = { Text("Buscar canales, películas y series…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            if (!searching && query.isNotBlank() && results.isEmpty()) {
                EmptyState(
                    title = "Sin resultados",
                    message = "No se encontró contenido para «$query»."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    grouped.forEach { (type, channels) ->
                        item(key = "header_${type.name}") {
                            Text(
                                TYPE_LABELS[type] ?: type.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                            )
                        }
                        items(channels, key = { "${type.name}-${it.id}" }) { channel ->
                            ChannelRow(
                                channel = channel,
                                selected = false,
                                onClick = {
                                    if (channel.type == ChannelType.SERIES) onOpenSeries(channel.id)
                                    else onPlayChannel(channel.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
