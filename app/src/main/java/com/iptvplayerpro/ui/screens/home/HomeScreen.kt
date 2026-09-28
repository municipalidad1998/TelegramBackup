package com.iptvplayerpro.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvplayerpro.core.util.AndroidExt
import com.iptvplayerpro.core.util.TimeFmt
import com.iptvplayerpro.di.containerViewModel
import com.iptvplayerpro.domain.model.Channel
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.domain.model.VpnConnectionState
import com.iptvplayerpro.ui.components.ChannelRow
import com.iptvplayerpro.ui.components.StatusChip
import com.iptvplayerpro.ui.nav.Routes
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.PaddingValues

/** Pantalla principal: menú estilo apps IPTV profesionales. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val viewModel = containerViewModel(HomeViewModel::class) { container, _ -> HomeViewModel(container) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val isTv = AndroidExt.isTvDevice(LocalContext.current)

    val menu = listOf(
        MenuItem("TV en vivo", Icons.Filled.LiveTv, Routes.channels(ChannelType.LIVE)),
        MenuItem("Películas", Icons.Filled.Movie, Routes.channels(ChannelType.MOVIE)),
        MenuItem("Series", Icons.Filled.Tv, Routes.SERIES),
        MenuItem("Favoritos", Icons.Filled.Star, Routes.library(0)),
        MenuItem("EPG", Icons.Filled.CalendarMonth, Routes.EPG),
        MenuItem("Listas", Icons.Filled.ListAlt, Routes.PLAYLISTS),
        MenuItem("VPN", Icons.Filled.VpnKey, Routes.VPN),
        MenuItem("Configuración", Icons.Filled.Settings, Routes.SETTINGS)
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BrandHeader(totalChannels = state.totalChannels, onSearch = { onNavigate(Routes.SEARCH) })

            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) 170.dp else 140.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(menu, key = { it.title }) { item ->
                    MenuCard(item.title, item.icon, isTv) { onNavigate(item.route) }
                }
                // Recientes dentro del grid: fila horizontal con ancho completo.
                if (state.recents.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Continuar viendo",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.recents.size, key = { state.recents[it].id }) { index ->
                                    val channel = state.recents[index]
                                    RecentChip(channel = channel) {
                                        onNavigate(Routes.player(channel.id))
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        // Aviso de actualización disponible.
        if (updateInfo != null) {
            UpdateBanner(
                info = updateInfo!!,
                onDismiss = { viewModel.dismissUpdate() },
                onDownload = { viewModel.downloadUpdate(updateInfo!!) },
                onInstall = { viewModel.installIfDownloaded(updateInfo!!) }
            )
        }
    }
}

private data class MenuItem(val title: String, val icon: ImageVector, val route: String)

@Composable
private fun BrandHeader(totalChannels: Int, onSearch: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "IPTV PLAYER PRO",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Búsqueda global",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        Text(
            "Reproduce contenido IPTV de fuentes autorizadas",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (totalChannels > 0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$totalChannels canales disponibles",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MenuCard(title: String, icon: ImageVector, isTv: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTv) 4.dp else 1.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (isTv) 46.dp else 38.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentChip(channel: Channel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(190.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (channel.logoUrl.isNullOrBlank()) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        channel.name.take(2).uppercase(),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                channel.categoryName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateBanner(
    info: com.iptvplayerpro.domain.model.UpdateInfo,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Actualización disponible") },
        text = {
            Column {
                Text("Versión actual: ${com.iptvplayerpro.BuildConfig.VERSION_NAME}")
                Text("Nueva versión: ${info.newVersionName}")
                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Cambios:", fontWeight = FontWeight.Bold)
                    Text(info.changelog, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "La instalación se realiza con el instalador oficial de Android; " +
                        "nunca se instala de forma silenciosa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDownload() }) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Descargar")
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = { if (onInstall()) onDismiss() }) { Text("Instalar") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Más tarde") }
            }
        }
    )
}
