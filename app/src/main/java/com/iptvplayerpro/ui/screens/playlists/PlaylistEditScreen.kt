package com.iptvplayerpro.ui.screens.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvplayerpro.di.containerViewModel
import com.iptvplayerpro.domain.model.PlaylistType
import com.iptvplayerpro.ui.components.AppTopBar

/**
 * Agregar/editar lista: Archivo M3U local · URL M3U/M3U8 · Xtream Codes.
 * Detecta automáticamente el tipo de fuente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val viewModel = containerViewModel(PlaylistEditViewModel::class, key = "edit_$playlistId") { container, _ ->
        PlaylistEditViewModel(container)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(playlistId) { viewModel.load(playlistId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val isEdit = state.isEdit
    var tab by rememberSaveable(isEdit) {
        mutableIntStateOf(if (isEdit && state.existing?.type == PlaylistType.XTREAM) 2 else 0)
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadFile(it) }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (isEdit) "Editar lista" else "Agregar lista",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (!isEdit) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Archivo") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("URL") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Xtream") })
                }
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = { value -> viewModel.update { it.copy(name = value) } },
                label = { Text("Nombre de la lista") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            when {
                isEdit || tab == 0 -> FileSection(state, viewModel, onPickFile = {
                    filePicker.launch(arrayOf("*/*"))
                })
                tab == 1 -> UrlSection(state, viewModel)
                else -> XtreamSection(state, viewModel)
            }

            state.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    "⚠ $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    when {
                        isEdit -> viewModel.saveEdit()
                        tab == 0 -> viewModel.saveM3uFromFile()
                        tab == 1 -> viewModel.saveM3uFromUrl()
                        else -> viewModel.saveXtream()
                    }
                },
                enabled = !state.saving && when {
                    isEdit -> state.name.isNotBlank()
                    tab == 0 -> state.canSaveM3uFile
                    tab == 1 -> state.canSaveM3uUrl
                    else -> state.canSaveXtream
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isEdit) "Guardar cambios" else "Guardar y cargar canales")
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Solo agrega fuentes IPTV que estés autorizado a usar. " +
                    "Las contraseñas se guardan cifradas con Android Keystore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (state.saved) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Lista guardada") },
            text = { Text("Los canales se cargaron correctamente.") },
            confirmButton = {
                TextButton(onClick = onSaved) { Text("Aceptar") }
            }
        )
    }
}

@Composable
private fun FileSection(
    state: EditUiState,
    viewModel: PlaylistEditViewModel,
    onPickFile: () -> Unit
) {
    if (state.isEdit) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.url,
            onValueChange = { value -> viewModel.update { it.copy(url = value) } },
            label = { Text("URL de la lista") },
            enabled = state.existing?.sourceUrl != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.epgUrl,
            onValueChange = { value -> viewModel.update { it.copy(epgUrl = value) } },
            label = { Text("URL XMLTV (EPG, opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.FolderOpen, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(state.fileName?.let { "Archivo: $it" } ?: "Seleccionar archivo M3U/M3U8")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Se detecta automáticamente si el archivo es M3U o M3U8. " +
            "El archivo se procesa en segundo plano.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.epgUrl,
        onValueChange = { value -> viewModel.update { it.copy(epgUrl = value) } },
        label = { Text("URL XMLTV (EPG, opcional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun UrlSection(state: EditUiState, viewModel: PlaylistEditViewModel) {
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.url,
        onValueChange = { value -> viewModel.update { it.copy(url = value) } },
        label = { Text("URL de la lista (http:// o https://)") },
        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.epgUrl,
        onValueChange = { value -> viewModel.update { it.copy(epgUrl = value) } },
        label = { Text("URL XMLTV (EPG, opcional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Ejemplo: https://ejemplo.com/get.php?username=…&password=…&type=m3u_plus\n" +
            "Compatible con .m3u, .m3u8 y get.php. Caché ETag: no se re-descarga " +
            "si la lista no cambió.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun XtreamSection(state: EditUiState, viewModel: PlaylistEditViewModel) {
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.server,
        onValueChange = { value -> viewModel.update { it.copy(server = value) } },
        label = { Text("URL del servidor (http://servidor:puerto)") },
        leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.user,
        onValueChange = { value -> viewModel.update { it.copy(user = value) } },
        label = { Text("Usuario") },
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.password,
        onValueChange = { value -> viewModel.update { it.copy(password = value) } },
        label = { Text(if (state.isEdit) "Contraseña (vacía = conservar)" else "Contraseña") },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Detección automática: si los tres campos de Xtream están completos, la app " +
            "usa la API Xtream Codes (player_api.php). Se muestra la fecha de expiración " +
            "que reporte tu proveedor. La contraseña se guarda cifrada y nunca se registra " +
            "en logs.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (state.isEdit) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Al guardar se vuelve a verificar la cuenta con el servidor.",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
