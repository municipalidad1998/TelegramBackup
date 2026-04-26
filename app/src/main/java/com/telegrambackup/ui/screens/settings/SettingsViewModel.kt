package com.telegrambackup.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telegrambackup.data.local.database.AppDatabase
import com.telegrambackup.data.preferences.AppPreferences
import com.telegrambackup.data.repository.BackupRepository
import com.telegrambackup.worker.AutoScanWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val botToken: String = "",
    val chatId: String = "",
    val wifiOnly: Boolean = true,
    val autoBackup: Boolean = true,
    val darkMode: Boolean = true,
    val totalFiles: Int = 0,
    val uploadedFiles: Int = 0,
    val totalSize: Long = 0L,
    val testResult: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val preferences: AppPreferences,
    private val repository: BackupRepository,
    private val database: AppDatabase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.botToken,
                preferences.chatId,
                preferences.wifiOnly,
                preferences.autoBackupEnabled,
                preferences.darkMode
            ) { token, chatId, wifi, auto, dark ->
                _uiState.value.copy(
                    botToken = token,
                    chatId = chatId,
                    wifiOnly = wifi,
                    autoBackup = auto,
                    darkMode = dark
                )
            }.collect { _uiState.value = it }
        }

        viewModelScope.launch {
            repository.getTotalCount().collect { count ->
                _uiState.value = _uiState.value.copy(totalFiles = count)
            }
        }
        viewModelScope.launch {
            repository.getUploadedCount().collect { count ->
                _uiState.value = _uiState.value.copy(uploadedFiles = count)
            }
        }
        viewModelScope.launch {
            repository.getTotalUploadedSize().collect { size ->
                _uiState.value = _uiState.value.copy(totalSize = size ?: 0L)
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

    fun setDarkMode(value: Boolean) {
        viewModelScope.launch { preferences.setDarkMode(value) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(testResult = "Testing...")
            val result = repository.testConnection()
            _uiState.value = _uiState.value.copy(
                testResult = result.fold(
                    onSuccess = { "✅ Connection successful!" },
                    onFailure = { "❌ ${it.message}" }
                )
            )
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            database.clearAllTables()
        }
    }
}
