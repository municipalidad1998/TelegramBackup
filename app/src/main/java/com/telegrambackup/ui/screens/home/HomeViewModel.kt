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
    val error: String? = null
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
                val count = repository.scanAndRegisterNewFiles()
                _uiState.value = _uiState.value.copy(isScanning = false)
                if (count == 0) {
                    _uiState.value = _uiState.value.copy(
                        error = "No new files found. Make sure storage permissions are granted."
                    )
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied", e)
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "Storage permission denied. Please grant permission in Settings."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Scan error", e)
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "Scan failed: ${e.message ?: "Unknown error"}"
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
                    error = "Upload failed: ${e.message ?: "Unknown error"}"
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
            } catch (e: Exception) {
                Log.e(TAG, "Config error", e)
            }
        }
    }

    fun testConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val result = repository.testConnection()
                result.fold(
                    onSuccess = { onResult(true, "Connection successful!") },
                    onFailure = { onResult(false, it.message ?: "Connection failed") }
                )
            } catch (e: Exception) {
                onResult(false, e.message ?: "Connection failed")
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
