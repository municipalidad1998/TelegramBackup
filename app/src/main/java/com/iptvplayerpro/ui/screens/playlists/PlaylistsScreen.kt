package com.iptvplayerpro.ui.screens.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvplayerpro.core.util.TimeFmt
import com.iptvplayerpro.di.containerViewModel
import com.iptvplayerpro.domain.model.Playlist
import com.iptvplayerpro.domain.model.PlaylistType
import com.iptvplayerpro.domain.model.TestConnectionResult
import com.iptvplayerpro.ui.components.AppTopBar
import com.iptvplayerpro.ui.components.EmptyState
import com.iptvplayerpro.ui.components.LabeledValue
import com.iptvplayerpro.ui.components.StatusChip

/**
 * «Mis listas»: nombre, tipo, canales, última actualización, estado,
 * expiración, editar, actualizar, eliminar, probar conexión y predeterminada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onBack: () -> Unit,
    onAddList: () -> Unit,
    onEditList: (Long) -> Unit
) {
    val viewModel = containerViewModel(PlaylistsViewModel::class) { container, _ -> PlaylistsViewModel(container) }
    val items by viewModel.items.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Mis listas",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onAddList) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar lista")
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                title = "Aún no hay listas",
                message = "Agrega una lista M3U/M3U8 (archivo o URL) o una cuenta Xtream " +
                    "con el botón + de arriba."
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.playlist.id }) { item ->
                PlaylistCard(
                    item = item,
                    onEdit = { onEditList(item.playlist.id) },
                    onRefresh = { viewModel.refresh(item.playlist.id) },
                    onDelete = { deleteTarget = item.playlist },
                    onTest = { viewModel.testConnection(item.playlist.id) },
                    onSetDefault = { viewModel.setDefault(item.playlist.id) },
                    onAutoRefresh = { viewModel.setAutoRefresh(item.playlist, it) }
                )
            }
        }
    }

    deleteTarget?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("¿Eliminar «${playlist.name}»?") },
            text = {
                Text(
                    "Se borrarán sus ${playlist.channelCount} canales guardados. " +
                        "Favoritos e historial de esa lista también se eliminarán. " +
                        "Esta acción no afecta tu cuenta con el proveedor."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(playlist.id)
                        deleteTarget = null
                    }
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PlaylistCard(
    item: PlaylistItem,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onSetDefault: () -> Unit,
    onAutoRefresh: (Boolean) -> Unit
) {
    val playlist = item.playlist
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (playlist.isDefault) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (playlist.isDefault) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "Predeterminada",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TypeChip(playlist.type)
                        StatusChip(playlist.status)
                    }
                }
                if (item.isRefreshing || item.isTesting) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(Modifier.height(10.dp))
            LabeledValue("Canales", playlist.channelCount.toString())
            LabeledValue(
                "Última actualización",
                playlist.lastUpdated?.let { TimeFmt.dateTime(it) } ?: "Nunca"
            )
            playlist.expiresAt?.let {
                LabeledValue("Expira", TimeFmt.dateTime(it))
            }
            playlist.lastError?.let {
                Text(
                    "⚠ $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            item.testResult?.let { result ->
                Spacer(Modifier.height(6.dp))
                TestResultBanner(result)
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Auto-actualizar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = playlist.autoRefresh,
                    onCheckedChange = onAutoRefresh
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                }
                IconButton(onClick = onTest) {
                    Icon(Icons.Default.WifiTethering, contentDescription = "Probar conexión")
                }
                IconButton(onClick = onSetDefault) {
                    Icon(
                        if (playlist.isDefault) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Establecer predeterminada",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeChip(type: PlaylistType) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            when (type) {
                PlaylistType.M3U -> "M3U/M3U8"
                PlaylistType.XTREAM -> "Xtream"
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun TestResultBanner(result: TestConnectionResult) {
    Surface(
        color = if (result.ok) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        contentColor = if (result.ok) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        shape = MaterialTheme.shapes.small
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(result.message, style = MaterialTheme.typography.bodySmall)
            result.expiresAt?.let {
                Text(
                    "Expira: ${TimeFmt.dateTime(it)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            result.details?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
