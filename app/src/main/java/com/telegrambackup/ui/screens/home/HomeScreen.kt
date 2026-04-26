package com.telegrambackup.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.data.local.entity.UploadStatus
import com.telegrambackup.ui.components.FileListItem
import com.telegrambackup.ui.components.StatCard
import com.telegrambackup.util.FileUtils

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
    var showSetupDialog by remember { mutableStateOf(false) }
    var showTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var permissionGranted by remember { mutableStateOf(false) }

    // Check initial permission state
    LaunchedEffect(Unit) {
        try {
            permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            Log.e("HomeScreen", "Permission check failed", e)
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionGranted = results.values.any { it }
    }

    // Show setup dialog when not configured - use a stable trigger
    LaunchedEffect(uiState.isConfigured) {
        try {
            if (!uiState.isConfigured) {
                showSetupDialog = true
            }
        } catch (e: Exception) {
            Log.e("HomeScreen", "Config check failed", e)
        }
    }

    // Test result dialog
    showTestResult?.let { (success, message) ->
        AlertDialog(
            onDismissRequest = { showTestResult = null },
            title = { Text(if (success) "✅ Success" else "❌ Error") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { showTestResult = null }) { Text("OK") }
            }
        )
    }

    // Setup Dialog
    if (showSetupDialog) {
        SetupDialog(
            onDismiss = {
                if (uiState.isConfigured) {
                    showSetupDialog = false
                }
            },
            onConfirm = { token, chatId ->
                viewModel.setTelegramConfig(token, chatId)
                showSetupDialog = false
            },
            onTest = { token, chatId ->
                viewModel.setTelegramConfig(token, chatId)
                viewModel.testConnection { success, msg ->
                    showTestResult = Pair(success, msg)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Text(
                "Telegram Backup",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Your personal cloud",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Not configured warning - always visible when not configured
        if (!uiState.isConfigured) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Telegram not configured",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Set up your Bot Token and Chat ID to start backing up",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                        }
                        Button(
                            onClick = { showSetupDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Setup")
                        }
                    }
                }
            }
        }

        // Permission warning
        if (uiState.isConfigured && !permissionGranted) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Storage permission required",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Grant access to scan and backup your files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                        }
                        Button(
                            onClick = {
                                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(
                                        Manifest.permission.READ_MEDIA_IMAGES,
                                        Manifest.permission.READ_MEDIA_VIDEO,
                                        Manifest.permission.READ_MEDIA_AUDIO
                                    )
                                } else {
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                                permissionLauncher.launch(perms)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }
        }

        // Stats Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Total",
                    value = "${uiState.totalFiles}",
                    icon = Icons.Outlined.Folder,
                    color = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Uploaded",
                    value = "${uiState.uploadedFiles}",
                    icon = Icons.Outlined.CloudDone,
                    color = MaterialTheme.colorScheme.secondary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Pending",
                    value = "${uiState.pendingFiles}",
                    icon = Icons.Outlined.CloudUpload,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // Storage info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Storage, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Cloud Storage Used", style = MaterialTheme.typography.labelMedium)
                        Text(
                            FileUtils.formatFileSize(uiState.totalSize),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (!uiState.isConfigured) {
                            showSetupDialog = true
                            return@Button
                        }
                        if (!permissionGranted) {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(
                                    Manifest.permission.READ_MEDIA_IMAGES,
                                    Manifest.permission.READ_MEDIA_VIDEO,
                                    Manifest.permission.READ_MEDIA_AUDIO
                                )
                            } else {
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            permissionLauncher.launch(perms)
                        } else {
                            viewModel.scanFiles()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isScanning
                ) {
                    if (uiState.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (uiState.isScanning) "Scanning..." else "Scan")
                }

                Button(
                    onClick = { viewModel.uploadAll() },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isUploading && uiState.pendingFiles > 0 && uiState.isConfigured
                ) {
                    if (uiState.isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(Icons.Outlined.CloudUpload, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (uiState.isUploading) "Uploading..." else "Upload All")
                }
            }
        }

        // Toggles
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("WiFi Only", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Upload only on WiFi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.wifiOnly,
                            onCheckedChange = { viewModel.setWifiOnly(it) }
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Auto Backup", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Automatically detect and upload new files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.autoBackup,
                            onCheckedChange = { viewModel.setAutoBackup(it) }
                        )
                    }
                }
            }
        }

        // Setup button
        item {
            OutlinedButton(
                onClick = { showSetupDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Settings, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Configure Bot")
            }
        }

        // Recent Files
        if (uiState.recentFiles.isNotEmpty()) {
            item {
                Text(
                    "Recent Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(uiState.recentFiles, key = { it.id }) { file ->
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

        // Error
        uiState.error?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
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
    var tokenError by remember { mutableStateOf<String?>(null) }
    var chatIdError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun validateToken(t: String): String? {
        if (t.isBlank()) return "Bot Token is required"
        if (!t.matches(Regex("^\\d+:[A-Za-z0-9_-]{20,}$"))) {
            return "Invalid format. Example: 123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11"
        }
        return null
    }

    fun validateChatId(c: String): String? {
        if (c.isBlank()) return "Chat ID is required"
        if (!c.matches(Regex("^-?\\d+$"))) {
            return "Chat ID must be a number (e.g. 123456789 or -1001234567890)"
        }
        return null
    }

    AlertDialog(
        onDismissRequest = {
            // Only allow dismiss if not saving
            if (!isSaving) onDismiss()
        },
        title = { Text("Telegram Bot Setup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter your Telegram Bot Token and Chat ID.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it.trim()
                        tokenError = null
                    },
                    label = { Text("Bot Token") },
                    placeholder = { Text("123456:ABC-DEF...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = tokenError != null,
                    supportingText = tokenError?.let { { Text(it) } },
                    enabled = !isSaving
                )
                OutlinedTextField(
                    value = chatId,
                    onValueChange = {
                        chatId = it.trim()
                        chatIdError = null
                    },
                    label = { Text("Chat ID") },
                    placeholder = { Text("-1001234567890") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = chatIdError != null,
                    supportingText = chatIdError?.let { { Text(it) } },
                    enabled = !isSaving
                )
                Text(
                    "Get your bot token from @BotFather on Telegram.\nChat ID: send a message to @userinfobot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val tErr = validateToken(token)
                        val cErr = validateChatId(chatId)
                        if (tErr != null || cErr != null) {
                            tokenError = tErr
                            chatIdError = cErr
                            return@TextButton
                        }
                        isSaving = true
                        onTest(token, chatId)
                        isSaving = false
                    },
                    enabled = token.isNotBlank() && chatId.isNotBlank() && !isSaving
                ) {
                    Text("Test")
                }
                Button(
                    onClick = {
                        val tErr = validateToken(token)
                        val cErr = validateChatId(chatId)
                        if (tErr != null || cErr != null) {
                            tokenError = tErr
                            chatIdError = cErr
                            return@Button
                        }
                        isSaving = true
                        onConfirm(token, chatId)
                        isSaving = false
                    },
                    enabled = token.isNotBlank() && chatId.isNotBlank() && !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (!isSaving) onDismiss() },
                enabled = !isSaving
            ) { Text("Cancel") }
        }
    )
}
