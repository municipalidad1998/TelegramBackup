package com.iptvplayerpro.ui.screens.series

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.iptvplayerpro.di.containerViewModel
import com.iptvplayerpro.ui.components.EmptyState

/** Detalle de serie: póster, información, temporadas y episodios. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    channelId: Long,
    onBack: () -> Unit,
    onPlayEpisode: (url: String, name: String) -> Unit
) {
    val viewModel = containerViewModel(SeriesDetailViewModel::class, key = "series_$channelId") { container, _ ->
        SeriesDetailViewModel(container)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(channelId) { viewModel.load(channelId) }

    val detail = state.detail
    var selectedSeason by remember(detail) {
        mutableStateOf(detail?.episodes?.minOfOrNull { it.season } ?: 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.detail?.seriesName ?: state.channel?.name ?: "Serie",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cargando serie…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.error != null -> {
                EmptyState(title = "No se pudo cargar la serie", message = state.error ?: "")
            }
            detail != null -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Cabecera: póster + info
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        AsyncImage(
                            model = detail.poster,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(120.dp)
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            detail.genre?.let {
                                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            detail.rating?.takeIf { it.isNotBlank() }?.let {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.width(16.dp)
                                    )
                                    Text(" $it", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            detail.releaseDate?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            detail.cast?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Reparto: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    detail.plot?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // Selector de temporada
                    val seasons = detail.episodes.map { it.season }.distinct().sorted()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        seasons.forEach { season ->
                            FilterChip(
                                selected = season == selectedSeason,
                                onClick = { selectedSeason = season },
                                label = { Text("T$season") }
                            )
                        }
                    }

                    // Episodios
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            detail.episodes.filter { it.season == selectedSeason },
                            key = { "${it.season}-${it.episode}-${it.id}" }
                        ) { episode ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "E${episode.episode} · ${episode.title}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        episode.plot?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            viewModel.buildEpisodeUrl(episode.id, episode.extension) { url ->
                                                onPlayEpisode(
                                                    url,
                                                    "T${episode.season}E${episode.episode} · ${episode.title}"
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.LiveTv, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ver")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
