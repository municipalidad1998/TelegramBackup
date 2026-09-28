package com.iptvplayerpro.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.iptvplayerpro.core.util.AndroidExt
import com.iptvplayerpro.core.util.TimeFmt
import com.iptvplayerpro.di.AppContainer
import com.iptvplayerpro.di.containerViewModel
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.ui.components.AppTopBar
import com.iptvplayerpro.ui.components.ChannelCard
import com.iptvplayerpro.ui.components.ChannelRow
import com.iptvplayerpro.ui.components.EmptyState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private class LibraryViewModel(container: AppContainer) : ViewModel() {
    private val channelRepository = container.channelRepository

    val favorites = channelRepository.favoritesPager(null)

    val recents = channelRepository.recents(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = channelRepository.history(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            channelRepository.setFavorite(channel.id, !channel.isFavorite)
        }
    }
}

/** Favoritos, Recientes e Historial. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    initialTab: Int,
    onBack: () -> Unit,
    onPlayChannel: (Long) -> Unit
) {
    val viewModel = containerViewModel(LibraryViewModel::class) { container, _ -> LibraryViewModel(container) }
    val favorites = viewModel.favorites.collectAsLazyPagingItems()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val isTv = AndroidExt.isTvDevice(LocalContext.current)

    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 2)) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = when (tab) {
                    0 -> "Favoritos"
                    1 -> "Recientes"
                    else -> "Historial"
                },
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Favoritos") },
                    icon = { Icon(Icons.Filled.Star, contentDescription = null) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Recientes") },
                    icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) }
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text("Historial") },
                    icon = { Icon(Icons.Rounded.History, contentDescription = null) }
                )
            }

            when (tab) {
                0 -> {
                    if (favorites.itemCount == 0) {
                        EmptyState(
                            title = "Sin favoritos",
                            message = "Marca canales con la estrella para verlos aquí."
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(if (isTv) 190.dp else 150.dp),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                count = favorites.itemCount,
                                key = favorites.itemKey { it.id }
                            ) { index ->
                                val channel = favorites[index] ?: return@items
                                ChannelCard(
                                    channel = channel,
                                    isTv = isTv,
                                    onClick = { onPlayChannel(channel.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(channel) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (recents.isEmpty()) {
                        EmptyState(
                            title = "Sin recientes",
                            message = "Los últimos canales que reproduzcas aparecerán aquí."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(recents, key = { it.id }) { channel ->
                                ChannelRow(
                                    channel = channel,
                                    selected = false,
                                    onClick = { onPlayChannel(channel.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(channel) }
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (history.isEmpty()) {
                        EmptyState(
                            title = "Historial vacío",
                            message = "Tu historial de reproducción aparecerá aquí."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(history, key = { "${it.channel.id}-${it.watchedAt}" }) { entry ->
                                Column {
                                    ChannelRow(
                                        channel = entry.channel,
                                        selected = false,
                                        onClick = { onPlayChannel(entry.channel.id) }
                                    )
                                    Text(
                                        "Visto ${TimeFmt.ago(entry.watchedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 14.dp, top = 2.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
