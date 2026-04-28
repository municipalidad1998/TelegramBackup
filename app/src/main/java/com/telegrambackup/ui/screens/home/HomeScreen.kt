package com.telegrambackup.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.data.local.entity.UploadStatus
import com.telegrambackup.ui.components.FileListItem
import com.telegrambackup.util.FileUtils

// ── Brand colours ─────────────────────────────────────────────────────────────
private val Bg          = Color(0xFF0B0D14)
private val SurfCard    = Color(0xFF131720)
private val SurfCard2   = Color(0xFF1A1F2E)
private val Accent      = Color(0xFF4D9FFF)
private val AccentGreen = Color(0xFF1DB954)
private val AccentAmber = Color(0xFFFFB74D)
private val AccentRed   = Color(0xFFFF453A)
private val TextPrimary = Color(0xFFE8EDF5)
private val TextSecond  = Color(0xFF8A97B0)
private val Divider     = Color(0xFF1E2738)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToGallery: () -> Unit,
    onNavigateToVideo: (Long) -> Unit,
    onNavigateToImage: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSetupDialog  by remember { mutableStateOf(false) }
    var showTestResult   by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showMarkAllDialog by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(false) }
    var autoStarted      by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            else
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { Log.e("HomeScreen", "Permission check failed", e) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> permissionGranted = results.values.any { it } }

    LaunchedEffect(uiState.isConfigured, uiState.restoreAttempted) {
        if (uiState.restoreAttempted && !uiState.isConfigured) showSetupDialog = true
    }

    LaunchedEffect(permissionGranted, uiState.isConfigured, uiState.restoreAttempted) {
        if (!autoStarted && permissionGranted && uiState.isConfigured && uiState.restoreAttempted) {
            autoStarted = true
            viewModel.startAutoBackup()
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    showTestResult?.let { (success, message) ->
        AlertDialog(
            onDismissRequest = { showTestResult = null },
            containerColor = SurfCard2,
            title = { Text(if (success) "Conexión exitosa" else "Error", color = TextPrimary) },
            text  = { Text(message, color = TextSecond) },
            confirmButton = { TextButton(onClick = { showTestResult = null }) { Text("OK", color = Accent) } }
        )
    }

    if (showMarkAllDialog) {
        AlertDialog(
            onDismissRequest = { showMarkAllDialog = false },
            containerColor = SurfCard2,
            title = { Text("¿Ya subiste estos archivos?", color = TextPrimary) },
            text = {
                Text(
                    "Esto marcará los ${uiState.pendingFiles} archivos como ya subidos sin volver a subirlos.",
                    color = TextSecond
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMarkAllDialog = false
                        viewModel.markAllAsUploaded { count ->
                            showTestResult = Pair(true, "$count archivos marcados como subidos")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("Sí, ya los subí") }
            },
            dismissButton = {
                TextButton(onClick = { showMarkAllDialog = false }) { Text("Cancelar", color = TextSecond) }
            }
        )
    }

    if (showSetupDialog) {
        SetupDialog(
            onDismiss = { if (uiState.isConfigured) showSetupDialog = false },
            onConfirm = { token, chatId ->
                viewModel.setTelegramConfig(token, chatId)
                showSetupDialog = false
            },
            onTest = { token, chatId ->
                viewModel.testConnection(token, chatId) { success, msg ->
                    showTestResult = Pair(success, msg)
                }
            }
        )
    }

    // ── Main content ──────────────────────────────────────────────────────────
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {

        // ── Hero header ───────────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0D1B3E), Color(0xFF0B0D14))
                        )
                    )
            ) {
                // Decorative circle
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .offset(x = 180.dp, y = (-40).dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Accent.copy(alpha = 0.18f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(50)
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .statusBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CloudDone,
                                null,
                                tint = Accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "TelegramBackup",
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                "Tu nube personal segura",
                                color = TextSecond,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    // Upload progress bar
                    if (uiState.totalFiles > 0) {
                        val progress = if (uiState.totalFiles > 0)
                            uiState.uploadedFiles.toFloat() / uiState.totalFiles.toFloat()
                        else 0f

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${uiState.uploadedFiles} subidos",
                                    color = AccentGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${(progress * 100).toInt()}%",
                                    color = TextSecond,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = AccentGreen,
                                trackColor = Color.White.copy(alpha = 0.08f)
                            )
                        }
                    }
                }

                // Config button top-right
                IconButton(
                    onClick = { showSetupDialog = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp)
                ) {
                    Icon(Icons.Outlined.Settings, "Configurar", tint = TextSecond)
                }
            }
        }

        // ── Alerts ────────────────────────────────────────────────────────────
        if (!uiState.isConfigured) {
            item {
                AlertBanner(
                    icon = Icons.Filled.Warning,
                    text = "Configura tu Bot Token y Chat ID para empezar",
                    actionLabel = "Configurar",
                    color = AccentAmber,
                    onAction = { showSetupDialog = true }
                )
            }
        }
        if (uiState.isConfigured && !permissionGranted) {
            item {
                AlertBanner(
                    icon = Icons.Filled.FolderOff,
                    text = "Se necesita permiso de almacenamiento para escanear archivos",
                    actionLabel = "Conceder",
                    color = AccentRed,
                    onAction = {
                        permissionLauncher.launch(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
                            else
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        )
                    }
                )
            }
        }

        // ── Stat cards ────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PremiumStatCard(
                    modifier    = Modifier.weight(1f),
                    value       = "${uiState.totalFiles}",
                    label       = "Total",
                    icon        = Icons.Outlined.Folder,
                    accentColor = Accent
                )
                PremiumStatCard(
                    modifier    = Modifier.weight(1f),
                    value       = "${uiState.uploadedFiles}",
                    label       = "Subidos",
                    icon        = Icons.Outlined.CloudDone,
                    accentColor = AccentGreen
                )
                PremiumStatCard(
                    modifier    = Modifier.weight(1f),
                    value       = "${uiState.pendingFiles}",
                    label       = "Pendientes",
                    icon        = Icons.Outlined.CloudUpload,
                    accentColor = AccentAmber
                )
            }
        }

        // ── Storage card ──────────────────────────────────────────────────────
        if (uiState.totalSize > 0) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfCard)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Storage, null, tint = Accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Almacenamiento en nube", color = TextSecond, fontSize = 11.sp)
                        Text(
                            FileUtils.formatFileSize(uiState.totalSize),
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── Recovery banner ───────────────────────────────────────────────────
        if (uiState.uploadedFiles == 0 && uiState.totalFiles > 0 && uiState.isConfigured) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0D2A4A), Color(0xFF0B1A2E))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudSync, null, tint = Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("¿Ya subiste archivos antes?", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Detecta qué archivos ya están en Telegram para no volverlos a subir",
                        color = TextSecond,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.syncFromTelegram { msg ->
                                    showTestResult = Pair(msg.startsWith("✅"), msg)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.Sync, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sincronizar", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { showMarkAllDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecond),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Divider)
                        ) {
                            Text("Ya los subí", fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── Upload status card ────────────────────────────────────────────────
        item {
            val isPaused = uiState.isPaused
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfCard)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background((if (isPaused) AccentAmber else AccentGreen).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPaused) Icons.Filled.PauseCircle else Icons.Outlined.CloudUpload,
                        null,
                        tint = if (isPaused) AccentAmber else AccentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            isPaused -> "Copia pausada"
                            uiState.isScanning -> "Detectando archivos..."
                            uiState.pendingFiles > 0 -> "Subiendo ${uiState.pendingFiles} archivos..."
                            else -> "Todo al día"
                        },
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isPaused) "Toca Reanudar para continuar"
                        else "La copia corre automáticamente en segundo plano",
                        color = TextSecond,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { if (isPaused) viewModel.resumeUpload() else viewModel.pauseUpload() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) Accent else AccentRed.copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(if (isPaused) "Reanudar" else "Pausar", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Settings toggles ──────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfCard)
            ) {
                PremiumToggleRow(
                    icon = Icons.Outlined.Wifi,
                    title = "Solo WiFi",
                    subtitle = "No usar datos móviles",
                    checked = uiState.wifiOnly,
                    onToggle = { viewModel.setWifiOnly(it) }
                )
                HorizontalDivider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                PremiumToggleRow(
                    icon = Icons.Outlined.Sync,
                    title = "Copia automática",
                    subtitle = "Detectar y subir archivos nuevos",
                    checked = uiState.autoBackup,
                    onToggle = { viewModel.setAutoBackup(it) }
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Recent files ──────────────────────────────────────────────────────
        if (uiState.recentFiles.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Archivos recientes",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            items(uiState.recentFiles, key = { it.id }) { file ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                    FileListItem(
                        file = file,
                        onClick = {
                            when (file.fileType) {
                                FileType.VIDEO -> onNavigateToVideo(file.id)
                                FileType.IMAGE -> onNavigateToImage(file.id)
                                else -> {}
                            }
                        },
                        onUpload = { viewModel.uploadSingle(file.id) }
                    )
                }
            }
        }

        // ── Error ─────────────────────────────────────────────────────────────
        uiState.error?.let { error ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentRed.copy(alpha = 0.12f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(error, color = AccentRed, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Components ─────────────────────────────────────────────────────────────────

@Composable
private fun PremiumStatCard(
    modifier: Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    accentColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfCard)
            .padding(14.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(value, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)
        Text(label, color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AlertBanner(
    icon: ImageVector,
    text: String,
    actionLabel: String,
    color: Color,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = color, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onAction,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(actionLabel, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PremiumToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecond, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Accent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color(0xFF2A3347)
            )
        )
    }
}

@Composable
fun SetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onTest: (String, String) -> Unit
) {
    var token by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    var tokenError  by remember { mutableStateOf<String?>(null) }
    var chatIdError by remember { mutableStateOf<String?>(null) }
    var isSaving    by remember { mutableStateOf(false) }

    fun validateToken(t: String): String? {
        if (t.isBlank()) return "El Token del Bot es obligatorio"
        if (!t.matches(Regex("^\\d+:[A-Za-z0-9_-]{20,}$")))
            return "Formato inválido. Ej: 123456:ABC-DEFghi..."
        return null
    }
    fun validateChatId(c: String): String? {
        if (c.isBlank()) return "El Chat ID es obligatorio"
        if (!c.matches(Regex("^-?\\d+$"))) return "El Chat ID debe ser un número"
        return null
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = Color(0xFF1A1F2E),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudDone, null, tint = Color(0xFF4D9FFF), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Configuración de Telegram", color = Color(0xFFE8EDF5), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ingresa tu Bot Token y Chat ID.", color = Color(0xFF8A97B0), fontSize = 13.sp)
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim(); tokenError = null },
                    label = { Text("Token del Bot") },
                    placeholder = { Text("123456:ABC-DEF...", color = Color(0xFF4A5568)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = tokenError != null,
                    supportingText = tokenError?.let { e -> { Text(e, color = Color(0xFFFF453A)) } },
                    enabled = !isSaving,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4D9FFF),
                        unfocusedBorderColor = Color(0xFF2A3347),
                        focusedLabelColor = Color(0xFF4D9FFF),
                        unfocusedLabelColor = Color(0xFF8A97B0),
                        focusedTextColor = Color(0xFFE8EDF5),
                        unfocusedTextColor = Color(0xFFE8EDF5)
                    )
                )
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it.trim(); chatIdError = null },
                    label = { Text("Chat ID") },
                    placeholder = { Text("-1001234567890", color = Color(0xFF4A5568)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = chatIdError != null,
                    supportingText = chatIdError?.let { e -> { Text(e, color = Color(0xFFFF453A)) } },
                    enabled = !isSaving,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4D9FFF),
                        unfocusedBorderColor = Color(0xFF2A3347),
                        focusedLabelColor = Color(0xFF4D9FFF),
                        unfocusedLabelColor = Color(0xFF8A97B0),
                        focusedTextColor = Color(0xFFE8EDF5),
                        unfocusedTextColor = Color(0xFFE8EDF5)
                    )
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val te = validateToken(token); val ce = validateChatId(chatId)
                        tokenError = te; chatIdError = ce
                        if (te == null && ce == null) onTest(token, chatId)
                    },
                    enabled = !isSaving
                ) { Text("Probar", color = Color(0xFF4D9FFF)) }
                Button(
                    onClick = {
                        val te = validateToken(token); val ce = validateChatId(chatId)
                        tokenError = te; chatIdError = ce
                        if (te == null && ce == null) { isSaving = true; onConfirm(token, chatId) }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4D9FFF))
                ) { Text("Guardar") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isSaving) onDismiss() }) {
                Text("Cancelar", color = Color(0xFF8A97B0))
            }
        }
    )
}
