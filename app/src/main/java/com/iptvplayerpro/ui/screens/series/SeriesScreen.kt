package com.iptvplayerpro.ui.screens.series

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.iptvplayerpro.core.util.AndroidExt
import com.iptvplayerpro.di.containerViewModel
import com.iptvplayerpro.ui.components.AppTopBar
import com.iptvplayerpro.ui.components.ChannelCard
import com.iptvplayerpro.ui.components.ChannelCardSkeleton
import com.iptvplayerpro.ui.components.EmptyState

/** Catálogo de series. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit
) {
    val viewModel = containerViewModel(SeriesViewModel::class) { container, _ -> SeriesViewModel(container) }
    val series = viewModel.series.collectAsLazyPagingItems()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isTv = AndroidExt.isTvDevice(LocalContext.current)

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Series",
                onBack = onBack,
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.setQuery(it) },
                placeholder = { Text("Buscar serie…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

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

            when {
                series.loadState.refresh is LoadState.Loading && series.itemCount == 0 -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (isTv) 190.dp else 150.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(12) { ChannelCardSkeleton() }
                    }
                }
                series.itemCount == 0 -> {
                    EmptyState(
                        title = "Sin series",
                        message = "Las series Xtream aparecen aquí. Las listas M3U con series " +
                            "se clasifican automáticamente."
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
                            count = series.itemCount,
                            key = series.itemKey { it.id }
                        ) { index ->
                            val item = series[index] ?: return@items
                            ChannelCard(
                                channel = item,
                                isTv = isTv,
                                compact = true,
                                onClick = { onOpenSeries(it.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
