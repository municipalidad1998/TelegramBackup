package com.telegrambackup.ui.screens.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.preferences.AppPreferences
import com.telegrambackup.data.repository.BackupRepository
import com.telegrambackup.worker.AutoScanWorker
import com.telegrambackup.worker.BatchUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isConfigured: Boolean = false,
    val isScanning: Boolean = false,
    val isPaused: Boolean = false,
    val totalFiles: Int = 0,
    val uploadedFiles: Int = 0,
    val pendingFiles: Int = 0,
    val totalSize: Long = 0L,
    val recentFiles: List<BackupFile> = emptyList(),
    val wifiOnly: Boolean = true,
    val autoBackup: Boolean = true,
    val error: String? = null,
    val restoreAttempted: Boolean = false
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
            try { preferences.isConfigured.collect { _uiState.value = _uiState.value.copy(isConfigured = it) } }
            catch (e: Exception) { Log.e(TAG, "Error", e) }
        }
        viewModelScope.launch {
            try { preferences.wifiOnly.collect { _uiState.value = _uiState.value.copy(wifiOnly = it) } }
            catch (e: Exception) { Log.e(TAG, "Error", e) }
        }
        viewModelScope.launch {
            try { preferences.autoBackupEnabled.collect { _uiState.value = _uiState.value.copy(autoBackup = it) } }
            catch (e: Exception) { Log.e(TAG, "Error", e) }
        }
        viewModelScope.launch {
            try { preferences.uploadPaused.collect { _uiState.value = _uiState.value.copy(isPaused = it) } }
            catch (e: Exception) { Log.e(TAG, "Error", e) }
        }
    }

    // Called once when app opens with permissions + config ready
    fun startAutoBackup() {
        viewModelScope.launch {
            if (!repository.isConfigured()) return@launch
            try {
                _uiState.value = _uiState.value.copy(isScanning = true)
                repository.scanAndRegisterNewFiles()
                _uiState.value = _uiState.value.copy(isScanning = false)
                // Only start upload if not paused
                if (!preferences.uploadPaused.first()) {
                    BatchUploadWorker.enqueue(getApplication())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-backup error", e)
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }
    }

    fun uploadSingle(fileId: Long) {
        viewModelScope.launch {
            // Re-enqueue batch worker so it picks up this file
            if (!preferences.uploadPaused.first()) {
                BatchUploadWorker.enqueue(getApplication())
            }
        }
    }

    fun pauseUpload() {
        viewModelScope.launch {
            preferences.setUploadPaused(true)
            BatchUploadWorker.cancel(getApplication())
        }
    }

    fun resumeUpload() {
        viewModelScope.launch {
            preferences.setUploadPaused(false)
            BatchUploadWorker.enqueue(getApplication())
        }
    }

    fun setTelegramConfig(token: String, chatId: String) {
        viewModelScope.launch {
            try {
                preferences.setTelegramConfig(token, chatId)
                repository.syncUploadedFilesForChat(chatId)
                Log.i(TAG, "Config saved, synced for chat: $chatId")
            } catch (e: Exception) {
                Log.e(TAG, "Config error", e)
                _uiState.value = _uiState.value.copy(error = "Error al guardar configuración: ${e.message}")
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
                preferences.setTelegramConfig(token, chatId)
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
