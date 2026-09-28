package com.iptvplayerpro.ui.screens.channels

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.iptvplayerpro.core.util.AndroidExt
import com.iptvplayerpro.di.containerViewModel
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.domain.model.PlaylistStatus
import com.iptvplayerpro.ui.components.AppTopBar
import com.iptvplayerpro.ui.components.ChannelCard
import com.iptvplayerpro.ui.components.ChannelCardSkeleton
import com.iptvplayerpro.ui.components.EmptyState

/**
 * Pantalla de canales: TV en vivo o Películas, con categorías, selector de
 * lista, búsqueda dentro de la sección y skeleton loading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    type: ChannelType,
    onBack: () -> Unit,
    onPlayChannel: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSeries: (Long) -> Unit
) {
    val viewModel = containerViewModel(key = "channels_$type") { container, _ ->
        ChannelsViewModel(container, type)
    }
    val channels = viewModel.channels.collectAsLazyPagingItems()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isTv = AndroidExt.isTvDevice(LocalContext.current)

    val title = when (type) {
        ChannelType.LIVE -> "TV en vivo"
        ChannelType.MOVIE -> "Películas"
        ChannelType.SERIES -> "Series"
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = title,
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Búsqueda global")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Selector de lista
            if (playlists.size > 1) {
                PlaylistSelector(
                    playlists = playlists,
                    selectedId = selectedPlaylist,
                    onSelect = { viewModel.setPlaylist(it) }
                )
            }

            // Búsqueda dentro de la sección
            if (!isTv) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.setQuery(it) },
                    placeholder = { Text("Buscar en $title…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Categorías
            if (categories.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { viewModel.setCategory(null) },
                        label = { Text("Todas") }
                    )
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category.categoryId,
                            onClick = {
                                viewModel.setCategory(
                                    if (selectedCategory == category.categoryId) null else category.categoryId
                                )
                            },
                            label = { Text("${category.categoryName} (${category.count})") }
                        )
                    }
                }
            }

            // Aviso de lista vencida
            playlists
                .filter { selectedPlaylist == null || it.id == selectedPlaylist }
                .filter { it.status == PlaylistStatus.EXPIRED }
                .firstOrNull()
                ?.let { expired ->
                    Text(
                        "⚠ La lista «${expired.name}» está vencida. Los canales guardados " +
                            "siguen disponibles; actualiza credenciales en Mis listas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

            when {
                channels.loadState.refresh is LoadState.Loading && channels.itemCount == 0 -> {
                    SkeletonGrid(isTv)
                }
                channels.itemCount == 0 -> {
                    EmptyState(
                        title = "Sin canales",
                        message = "Agrega una lista en «Mis listas» para comenzar."
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (isTv) 190.dp else 150.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            count = channels.itemCount,
                            key = channels.itemKey { it.id }
                        ) { index ->
                            val channel = channels[index] ?: return@items
                            ChannelCard(
                                channel = channel,
                                isTv = isTv,
                                onClick = { ch ->
                                    if (ch.type == ChannelType.SERIES) onOpenSeries(ch.id)
                                    else onPlayChannel(ch.id)
                                },
                                onToggleFavorite = { ch -> viewModel.toggleFavorite(ch) }
                            )
                        }
                        if (channels.loadState.append is LoadState.Loading) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LinearProgressIndicator(Modifier.width(160.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonGrid(isTv: Boolean) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (isTv) 190.dp else 150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(12) { ChannelCardSkeleton() }
    }
}

@Composable
private fun PlaylistSelector(
    playlists: List<com.iptvplayerpro.domain.model.Playlist>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = playlists.firstOrNull { it.id == selectedId }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = !expanded },
            label = { Text(selected?.name ?: "Todas las listas") }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Todas las listas") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            playlists.forEach { playlist ->
                DropdownMenuItem(
                    text = { Text(playlist.name) },
                    onClick = {
                        onSelect(playlist.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
