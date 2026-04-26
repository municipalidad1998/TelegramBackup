package com.telegrambackup.ui.screens.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telegrambackup.data.local.entity.BackupFile
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.data.local.entity.UploadStatus
import com.telegrambackup.data.preferences.AppPreferences
import com.telegrambackup.data.repository.BackupRepository
import com.telegrambackup.service.UploadService
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

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeStats()
        observeConfig()
    }

    private fun observeStats() {
        viewModelScope.launch {
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
        }
    }

    private fun observeConfig() {
        viewModelScope.launch {
            preferences.isConfigured.collect { configured ->
                _uiState.value = _uiState.value.copy(isConfigured = configured)
            }
        }
        viewModelScope.launch {
            preferences.wifiOnly.collect { wifi ->
                _uiState.value = _uiState.value.copy(wifiOnly = wifi)
            }
        }
        viewModelScope.launch {
            preferences.autoBackupEnabled.collect { auto ->
                _uiState.value = _uiState.value.copy(autoBackup = auto)
            }
        }
    }

    fun scanFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, error = null)
            try {
                val count = repository.scanAndRegisterNewFiles()
                _uiState.value = _uiState.value.copy(isScanning = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = e.message
                )
            }
        }
    }

    fun uploadAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null)
            try {
                val context = getApplication<Application>()
                repository.uploadAllPending()
                _uiState.value = _uiState.value.copy(isUploading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    error = e.message
                )
            }
        }
    }

    fun uploadSingle(fileId: Long) {
        FileUploadWorker.enqueue(getApplication(), fileId)
    }

    fun setTelegramConfig(token: String, chatId: String) {
        viewModelScope.launch {
            preferences.setTelegramConfig(token, chatId)
        }
    }

    fun testConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.testConnection()
            result.fold(
                onSuccess = { onResult(true, "Connection successful!") },
                onFailure = { onResult(false, it.message ?: "Connection failed") }
            )
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
