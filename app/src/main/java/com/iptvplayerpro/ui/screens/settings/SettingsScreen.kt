package com.iptvplayerpro.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvplayerpro.BuildConfig
import com.iptvplayerpro.di.containerViewModel
import com.iptvplayerpro.domain.model.ThemeMode
import com.iptvplayerpro.ui.components.AppTopBar

/** Configuración: tema, buffer del reproductor, refrescos, PiP y actualizaciones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenVpn: () -> Unit
) {
    val viewModel = containerViewModel { container, _ -> SettingsViewModel(container) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val checking by viewModel.checkingUpdate.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val updateError by viewModel.updateError.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar(title = "Configuración", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Apariencia
            SectionTitle("Apariencia")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Modo", Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = settings.themeMode == ThemeMode.SYSTEM,
                                onClick = { viewModel.setTheme(ThemeMode.SYSTEM) },
                                label = { Text("Auto") }
                            )
                            FilterChip(
                                selected = settings.themeMode == ThemeMode.LIGHT,
                                onClick = { viewModel.setTheme(ThemeMode.LIGHT) },
                                label = { Text("Claro") }
                            )
                            FilterChip(
                                selected = settings.themeMode == ThemeMode.DARK,
                                onClick = { viewModel.setTheme(ThemeMode.DARK) },
                                label = { Text("Oscuro") }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Colores dinámicos (Android 12+)", Modifier.weight(1f))
                        Switch(
                            checked = settings.dynamicColors,
                            onCheckedChange = { viewModel.setDynamicColors(it) }
                        )
                    }
                }
            }

            // Reproductor
            SectionTitle("Reproductor")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Buffer: ${settings.minBufferMs / 1000}s – ${settings.maxBufferMs / 1000}s",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Un buffer mayor da más estabilidad con conexiones inestables " +
                            "(Wi-Fi o datos móviles); menor permite un inicio más rápido.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SliderSetting(
                        label = "Buffer mínimo",
                        valueSeconds = settings.minBufferMs / 1000f,
                        range = 5f..60f,
                        onValueChangeSeconds = { sec ->
                            viewModel.setBuffer(
                                (sec * 1000).toInt(),
                                settings.maxBufferMs,
                                settings.bufferForPlaybackMs,
                                settings.rebufferMs
                            )
                        }
                    )
                    SliderSetting(
                        label = "Buffer máximo",
                        valueSeconds = settings.maxBufferMs / 1000f,
                        range = 30f..300f,
                        onValueChangeSeconds = { sec ->
                            viewModel.setBuffer(
                                settings.minBufferMs,
                                (sec * 1000).toInt(),
                                settings.bufferForPlaybackMs,
                                settings.rebufferMs
                            )
                        }
                    )
                    SliderSetting(
                        label = "Reintentos de reconexión",
                        valueSeconds = settings.retryCount.toFloat(),
                        range = 0f..6f,
                        steps = 5,
                        format = { "${it.toInt()}" },
                        onValueChangeSeconds = { viewModel.setRetryCount(it.toInt()) }
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PiP automático al salir", Modifier.weight(1f))
                        Switch(
                            checked = settings.autoPip,
                            onCheckedChange = { viewModel.setAutoPip(it) }
                        )
                    }
                }
            }

            // Actualización de listas
            SectionTitle("Listas y EPG")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Actualizar listas automáticamente", fontWeight = FontWeight.Medium)
                            Text(
                                "Solo cuando la fuente lo permita. M3U usa ETag para no " +
                                    "descargar si no hubo cambios.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoRefresh,
                            onCheckedChange = { viewModel.setAutoRefresh(it) }
                        )
                    }
                    SliderSetting(
                        label = "Intervalo de listas (horas)",
                        valueSeconds = settings.autoRefreshHours.toFloat(),
                        range = 1f..72f,
                        format = { "${it.toInt()} h" },
                        onValueChangeSeconds = { viewModel.setAutoRefreshHours(it.toInt()) }
                    )
                    SliderSetting(
                        label = "Intervalo EPG (horas)",
                        valueSeconds = settings.epgRefreshHours.toFloat(),
                        range = 1f..48f,
                        format = { "${it.toInt()} h" },
                        onValueChangeSeconds = { viewModel.setEpgRefreshHours(it.toInt()) }
                    )
                }
            }

            // Actualización de la app
            SectionTitle("Actualización de la aplicación")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Versión actual: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    Spacer(Modifier.height(8.dp))
                    var updateUrl by remember(settings.updateUrl) {
                        mutableStateOf(settings.updateUrl)
                    }
                    OutlinedTextField(
                        value = updateUrl,
                        onValueChange = { updateUrl = it },
                        label = { Text("URL de actualizaciones (JSON o GitHub Releases)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            viewModel.setUpdateUrl(updateUrl)
                            viewModel.checkUpdate()
                        }) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, Modifier.width(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Buscar actualizaciones")
                        }
                        if (checking) CircularProgressIndicator(Modifier.width(22.dp), strokeWidth = 2.dp)
                    }
                    updateError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    updateInfo?.let { info ->
                        Spacer(Modifier.height(8.dp))
                        Text("Última versión: ${info.newVersionName}")
                        if (info.changelog.isNotBlank()) {
                            Text(info.changelog, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        if (info.isUpdateAvailable) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.downloadUpdate(info) }) {
                                    Icon(Icons.Default.Download, contentDescription = null, Modifier.width(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Descargar")
                                }
                                Button(onClick = { viewModel.installDownloaded(info) }) {
                                    Text("Instalar")
                                }
                            }
                            Text(
                                "La instalación usa el instalador oficial de Android " +
                                    "(con tu confirmación). Nunca se instala en silencio.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("Tienes la última versión.", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }

            // Acerca de
            SectionTitle("Acerca de")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("IPTV Player Pro", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Reproductor IPTV para fuentes que estés autorizado a usar. " +
                            "La VPN integrada (WireGuard) es para privacidad y para conectarte " +
                            "desde regiones que tu proveedor permita; no evita geobloqueos, DRM " +
                            "ni límites de suscripción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SliderSetting(
    label: String,
    valueSeconds: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    format: (Float) -> String = { "${it.toInt()} s" },
    onValueChangeSeconds: (Float) -> Unit
) {
    Column {
        Text("$label: ${format(valueSeconds)}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = valueSeconds.coerceIn(range.start, range.endInclusive),
            onValueChange = { onValueChangeSeconds(it) },
            valueRange = range,
            steps = steps
        )
    }
}
