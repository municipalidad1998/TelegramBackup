package com.iptvplayerpro.ui.screens.vpn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvplayerpro.core.util.AndroidExt
import com.iptvplayerpro.core.util.TimeFmt
import com.iptvplayerpro.di.containerViewModel
import com.iptvplayerpro.domain.model.VpnConnectionState
import com.iptvplayerpro.domain.model.VpnProfile
import com.iptvplayerpro.ui.components.AppTopBar
import com.iptvplayerpro.vpn.FreeVpnCatalog
import com.iptvplayerpro.vpn.WgConfigInspector

/**
 * Sección VPN (WireGuard · Android VpnService):
 * estado, IP pública, servidores por región, importación de configuraciones
 * y catálogo de proveedores con planes gratuitos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnScreen(onBack: () -> Unit) {
    val viewModel = containerViewModel { container, _ -> VpnViewModel(container) }
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingConnect by remember { mutableStateOf<VpnProfile?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingConnect?.let { viewModel.connect(it) }
        }
        pendingConnect = null
    }

    fun connect(profile: VpnProfile) {
        val intent = viewModel.prepareIntent()
        if (intent != null) {
            pendingConnect = profile
            permissionLauncher.launch(intent)
        } else {
            viewModel.connect(profile)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "VPN",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.refreshIp() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar IP")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Estado
            StatusCard(
                state = connectionState,
                profile = activeProfile,
                ipInfo = ui.ipInfo,
                ipLoading = ui.ipLoading,
                lastError = lastError,
                onDisconnect = { viewModel.disconnect() }
            )

            // Aviso legal
            InfoCard(
                icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                text = FreeVpnCatalog.DISCLAIMER
            )
            InfoCard(
                icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                text = FreeVpnCatalog.DNS_NOTE
            )

            // Servidores
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Servidores (${profiles.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar")
                }
            }

            if (profiles.isEmpty()) {
                Text(
                    "Importa tus configuraciones WireGuard (.conf) para conectar. " +
                        "Puedes obtenerlas gratis de los proveedores de abajo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val byCountry = profiles.groupBy { it.countryCode ?: "XX" }
            byCountry.forEach { (code, list) ->
                Text(
                    "${AndroidExt.flagEmoji(code)} ${WgConfigInspector.countryName(code)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                list.forEach { profile ->
                    ProfileRow(
                        profile = profile,
                        isActive = activeProfile?.id == profile.id,
                        isConnecting = connectionState == VpnConnectionState.CONNECTING &&
                            activeProfile?.id == profile.id,
                        onConnect = { connect(profile) },
                        onDelete = { viewModel.deleteProfile(profile.id) }
                    )
                }
            }

            // Catálogo de VPN gratuitas
            Text("VPN gratuitas legítimas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Estos proveedores ofrecen planes gratuitos con configuraciones " +
                    "WireGuard descargables. La app no incluye credenciales ajenas ni " +
                    "enruta tu tráfico por servidores propios: importas TU configuración.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FreeVpnCatalog.providers.forEach { provider ->
                FreeProviderCard(
                    provider = provider,
                    onOpenSite = { AndroidExt.openUrl(context, provider.website) }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, text, provider ->
                viewModel.addProfile(name, text, provider) { ok, message ->
                    viewModel.showMessage(message)
                }
                showAddDialog = false
            },
            onAddUrl = { url ->
                viewModel.addProfileFromUrl(url) { ok, message ->
                    viewModel.showMessage(message)
                }
                showAddDialog = false
            }
        )
    }

    ui.message?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.showMessage(null) },
            title = { Text("VPN") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.showMessage(null) }) { Text("Aceptar") }
            }
        )
    }
}

// ------------------------------------------------------------------ tarjetas

@Composable
private fun StatusCard(
    state: VpnConnectionState,
    profile: VpnProfile?,
    ipInfo: com.iptvplayerpro.domain.model.PublicIpInfo?,
    ipLoading: Boolean,
    lastError: String?,
    onDisconnect: () -> Unit
) {
    val (label, color) = when (state) {
        VpnConnectionState.CONNECTED -> "Conectado" to MaterialTheme.colorScheme.tertiary
        VpnConnectionState.CONNECTING -> "Conectando…" to MaterialTheme.colorScheme.secondary
        VpnConnectionState.DISCONNECTING -> "Desconectando…" to MaterialTheme.colorScheme.secondary
        VpnConnectionState.ERROR -> "Error" to MaterialTheme.colorScheme.error
        VpnConnectionState.DISCONNECTED -> "Desconectado" to MaterialTheme.colorScheme.outline
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    profile?.let {
                        Text(
                            "${AndroidExt.flagEmoji(it.countryCode)} ${it.name}" +
                                (it.country?.let { c -> " · $c" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        it.serverHost?.let { host ->
                            Text(
                                host,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (state == VpnConnectionState.CONNECTED ||
                    state == VpnConnectionState.CONNECTING
                ) {
                    OutlinedButton(onClick = onDisconnect) { Text("Desconectar") }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Public,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (ipLoading) "Consultando IP pública…"
                    else ipInfo?.let { info ->
                        "IP: ${info.ip}" + (info.country?.let { c -> " · $c" } ?: "")
                    } ?: "IP pública no disponible ahora",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            lastError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: VpnProfile,
    isActive: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${AndroidExt.flagEmoji(profile.countryCode)} ${profile.name}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            profile.lastConnectedAt?.let {
                Text(
                    TimeFmt.ago(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            if (isConnecting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (!isActive) {
                TextButton(onClick = onConnect) { Text("Conectar") }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FreeProviderCard(
    provider: com.iptvplayerpro.vpn.FreeVpnProvider,
    onOpenSite: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(provider.name, style = MaterialTheme.typography.titleMedium)
            Text(
                provider.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Gratis: ${provider.freeAllowance}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Regiones: ${provider.regions}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            provider.configSteps.forEachIndexed { index, step ->
                Text(
                    "${index + 1}. $step",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenSite) {
                Icon(Icons.Default.Language, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Abrir web del proveedor")
            }
        }
    }
}

@Composable
private fun InfoCard(icon: @Composable () -> Unit, text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            icon()
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ------------------------------------------------------------------ diálogo

@Composable
private fun AddProfileDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String?) -> Unit,
    onAddUrl: (String) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var configText by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val text = runCatching {
                    context.contentResolver.openInputStream(it)
                        ?.bufferedReader()?.use { reader -> reader.readText() }
                }.getOrNull()
                if (text != null) {
                    configText = text
                    if (name.isBlank()) {
                        name = it.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".conf") ?: "Servidor VPN"
                    }
                } else {
                    error = "No se pudo leer el archivo."
                }
            } catch (e: Exception) {
                error = "No se pudo leer el archivo."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar servidor VPN") },
        text = {
            Column {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Pegar") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Archivo") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("URL") })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (p. ej. «Tokio – Proton»)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                when (tab) {
                    0 -> OutlinedTextField(
                        value = configText,
                        onValueChange = { configText = it },
                        label = { Text("Configuración WireGuard (.conf)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                    1 -> {
                        OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Seleccionar archivo .conf")
                        }
                        if (configText.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Archivo cargado (${configText.length} caracteres).",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    else -> OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("https://…/servidor.conf") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = null
                    if (name.isBlank()) {
                        error = "Ponle un nombre."
                        return@TextButton
                    }
                    when (tab) {
                        0, 1 -> {
                            if (!WgConfigInspector.isValid(WgConfigInspector.normalize(configText))) {
                                error = "La configuración no parece WireGuard válido."
                            } else {
                                onAdd(name, configText, null)
                            }
                        }
                        else -> {
                            if (url.isBlank()) {
                                error = "Escribe la URL."
                            } else {
                                onAddUrl(url)
                            }
                        }
                    }
                }
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
