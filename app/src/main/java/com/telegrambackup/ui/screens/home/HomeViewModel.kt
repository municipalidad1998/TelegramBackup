package com.telegrambackup.ui.screens.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.data.local.entity.UploadStatus
import com.telegrambackup.data.preferences.AppPreferences
import com.telegrambackup.data.repository.BackupRepository
import com.telegrambackup.worker.AutoScanWorker
import com.telegrambackup.worker.FileUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isConfigured: Boolean = false,
    val isScanning: Boolean = false,
    val isUploading: Boolean = false,
    val totalFiles: Int = 0,
    val uploadedFiles: Int = 0,
    val pendingFiles: Int = 0,
    val totalSize: Long = 0L,
    val recentFiles: List<BackupFile> = emptyList(),
    val wifiOnly: Boolean = true,
    val autoBackup: Boolean = true,
    val error: String? = null,
    val restoreAttempted: Boolean = false  // Track if backup restore has been attempted
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val repository: BackupRepository,
    private val preferences: AppPreferences
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Restore config from backup FIRST, then observe - this ensures config is available
        viewModelScope.launch {
            try {
                preferences.restoreFromBackupIfNeeded()
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring config from backup", e)
            } finally {
                _uiState.value = _uiState.value.copy(restoreAttempted = true)
            }
        }
        observeStats()
        observeConfig()
    }

    private fun observeStats() {
        viewModelScope.launch {
            try {
                combine(
                    repository.getTotalCount(),
                    repository.getUploadedCount(),
                    repository.getPendingCount(),
                    repository.getTotalUploadedSize(),
                    repository.getAllFiles()
                ) { total, uploaded, pending, size, files ->
                    _uiState.value.copy(
                        totalFiles = total,
                        uploadedFiles = uploaded,
                        pendingFiles = pending,
                        totalSize = size ?: 0L,
                        recentFiles = files.take(20)
                    )
                }.collect { _uiState.value = it }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing stats", e)
            }
        }
    }

    private fun observeConfig() {
        viewModelScope.launch {
            try {
                preferences.isConfigured.collect { configured ->
                    _uiState.value = _uiState.value.copy(isConfigured = configured)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing config", e)
            }
        }
        viewModelScope.launch {
            try {
                preferences.wifiOnly.collect { wifi ->
                    _uiState.value = _uiState.value.copy(wifiOnly = wifi)
                }
            } catch (e: Exception) { Log.e(TAG, "Error", e) }
        }
        viewModelScope.launch {
            try {
                preferences.autoBackupEnabled.collect { auto ->
                    _uiState.value = _uiState.value.copy(autoBackup = auto)
                }
            } catch (e: Exception) { Log.e(TAG, "Error", e) }
        }
    }

    fun scanFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, error = null)
            try {
                // Verify config is available before scanning
                if (!repository.isConfigured()) {
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        error = "Primero configura tu Token y Chat ID de Telegram."
                    )
                    return@launch
                }

                val count = try {
                    repository.scanAndRegisterNewFiles()
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "OOM during scan", e)
                    0
                }

                _uiState.value = _uiState.value.copy(isScanning = false)
                if (count == 0) {
                    _uiState.value = _uiState.value.copy(
                        error = "No se encontraron archivos nuevos. Asegúrate de que los permisos de almacenamiento están concedidos."
                    )
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied", e)
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "Permiso de almacenamiento denegado. Concede el permiso en Ajustes."
                )
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Out of memory during scan", e)
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "Demasiados archivos para escanear a la vez. Inténtalo de nuevo."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Scan error", e)
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "Error al escanear: ${e.message ?: "Error desconocido"}"
                )
            }
        }
    }

    fun uploadAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null)
            try {
                repository.uploadAllPending()
                _uiState.value = _uiState.value.copy(isUploading = false)
            } catch (e: Exception) {
                Log.e(TAG, "Upload error", e)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = "Error al subir: ${e.message ?: "Error desconocido"}"
                )
            }
        }
    }

    fun uploadSingle(fileId: Long) {
        FileUploadWorker.enqueue(getApplication(), fileId)
    }

    fun setTelegramConfig(token: String, chatId: String) {
        viewModelScope.launch {
            try {
                preferences.setTelegramConfig(token, chatId)
                // Re-evaluate which files are already uploaded to THIS specific chat
                repository.syncUploadedFilesForChat(chatId)
                Log.i(TAG, "Telegram config saved and uploads synced for chat: $chatId")
            } catch (e: Exception) {
                Log.e(TAG, "Config error", e)
                _uiState.value = _uiState.value.copy(
                    error = "Error al guardar configuración: ${e.message}"
                )
            }
        }
    }

    fun testConnection(token: String, chatId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                if (token.isBlank() || chatId.isBlank()) {
                    onResult(false, "Primero ingresa el Token del Bot y el Chat ID.")
                    return@launch
                }
                // Save first and wait for it
                preferences.setTelegramConfig(token, chatId)
                // Then test with the same values
                val result = repository.testConnectionWith(token, chatId)
                result.fold(
                    onSuccess = { onResult(true, "¡Conexión exitosa! El bot funciona correctamente.") },
                    onFailure = { onResult(false, "Conexión fallida: ${it.message ?: "Error desconocido"}") }
                )
            } catch (e: Exception) {
                onResult(false, "Error: ${e.message ?: "Conexión fallida"}")
            }
        }
    }

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch { preferences.setWifiOnly(value) }
    }

    fun setAutoBackup(value: Boolean) {
        viewModelScope.launch {
            preferences.setAutoBackupEnabled(value)
            val context = getApplication<Application>()
            if (value) AutoScanWorker.schedule(context)
            else AutoScanWorker.cancel(context)
        }
    }
}
