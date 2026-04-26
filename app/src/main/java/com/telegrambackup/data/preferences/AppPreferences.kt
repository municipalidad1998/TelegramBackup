package com.telegrambackup.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds get() = context.dataStore

    // Backup SharedPreferences for critical config
    private val backupPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("telegram_backup_prefs", Context.MODE_PRIVATE)
    }

    // Telegram config
    val botToken: Flow<String> = ds.data.map { it[BOT_TOKEN] ?: "" }
    val chatId: Flow<String> = ds.data.map { it[CHAT_ID] ?: "" }

    suspend fun setTelegramConfig(token: String, chatId: String) {
        try {
            // Save to DataStore
            ds.edit {
                it[BOT_TOKEN] = token
                it[CHAT_ID] = chatId
            }
        } catch (e: Exception) {
            Log.e("AppPreferences", "DataStore save failed, using SharedPreferences only", e)
        }

        // Always save to SharedPreferences (more reliable) - use commit() for synchronous write
        try {
            val committed = backupPrefs.edit()
                .putString("bot_token", token)
                .putString("chat_id", chatId)
                .commit()  // Synchronous - ensures write completes before returning
            if (!committed) {
                Log.e("AppPreferences", "SharedPreferences commit returned false")
            }
        } catch (e: Exception) {
            Log.e("AppPreferences", "SharedPreferences save failed", e)
        }

        // Also save to external storage (survives uninstall)
        try {
            saveToExternalStorage(token, chatId)
        } catch (e: Exception) {
            Log.e("AppPreferences", "External storage backup failed", e)
        }
    }

    /**
     * Save config to Documents/TelegramBackup/config_backup.json
     * This survives app uninstall on most devices.
     */
    private fun saveToExternalStorage(token: String, chatId: String) {
        try {
            val backupDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "TelegramBackup"
            )
            backupDir.mkdirs()
            val backupFile = File(backupDir, "config_backup.json")
            val json = """{"bot_token":"$token","chat_id":"$chatId"}"""
            backupFile.writeText(json)
            Log.i("AppPreferences", "Config backed up to ${backupFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("AppPreferences", "External backup failed", e)
        }
    }

    /**
     * Restore config from SharedPreferences backup if DataStore is empty.
     * Falls back to external storage if SharedPreferences is also empty.
     */
    suspend fun restoreFromBackupIfNeeded() {
        try {
            val currentToken = try { ds.data.first()[BOT_TOKEN] } catch (e: Exception) { null }
            val currentChatId = try { ds.data.first()[CHAT_ID] } catch (e: Exception) { null }

            if (currentToken.isNullOrEmpty() || currentChatId.isNullOrEmpty()) {
                // Try SharedPreferences first
                var backupToken = backupPrefs.getString("bot_token", "") ?: ""
                var backupChatId = backupPrefs.getString("chat_id", "") ?: ""

                // If SharedPreferences is also empty, try external storage
                if (backupToken.isEmpty() || backupChatId.isEmpty()) {
                    val external = readFromExternalStorage()
                    if (external != null) {
                        backupToken = external.first
                        backupChatId = external.second
                        // Also restore to SharedPreferences for next time
                        backupPrefs.edit()
                            .putString("bot_token", backupToken)
                            .putString("chat_id", backupChatId)
                            .commit()
                    }
                }

                if (backupToken.isNotEmpty() && backupChatId.isNotEmpty()) {
                    ds.edit {
                        it[BOT_TOKEN] = backupToken
                        it[CHAT_ID] = backupChatId
                    }
                    Log.i("AppPreferences", "Config restored from backup (token=${backupToken.take(5)}...)")
                }
            }
        } catch (e: Exception) {
            Log.e("AppPreferences", "Restore from backup failed", e)
        }
    }

    /**
     * Read config from external storage backup file.
     */
    private fun readFromExternalStorage(): Pair<String, String>? {
        return try {
            val backupDir = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
                "TelegramBackup"
            )
            val backupFile = java.io.File(backupDir, "config_backup.json")
            if (!backupFile.exists()) return null

            val json = backupFile.readText()
            val tokenMatch = Regex(""""bot_token"\s*:\s*"([^"]*?)"""").find(json)
            val chatIdMatch = Regex(""""chat_id"\s*:\s*"([^"]*?)"""").find(json)
            val token = tokenMatch?.groupValues?.get(1) ?: ""
            val chatId = chatIdMatch?.groupValues?.get(1) ?: ""

            if (token.isNotEmpty() && chatId.isNotEmpty()) Pair(token, chatId) else null
        } catch (e: Exception) {
            Log.e("AppPreferences", "External storage read failed", e)
            null
        }
    }

    // WiFi only
    val wifiOnly: Flow<Boolean> = ds.data.map { it[WIFI_ONLY] ?: true }
    suspend fun setWifiOnly(value: Boolean) {
        ds.edit { it[WIFI_ONLY] = value }
    }

    // Auto backup enabled
    val autoBackupEnabled: Flow<Boolean> = ds.data.map { it[AUTO_BACKUP] ?: true }
    suspend fun setAutoBackupEnabled(value: Boolean) {
        ds.edit { it[AUTO_BACKUP] = value }
    }

    // Theme
    val darkMode: Flow<Boolean> = ds.data.map { it[DARK_MODE] ?: true }
    suspend fun setDarkMode(value: Boolean) {
        ds.edit { it[DARK_MODE] = value }
    }

    // Sync state
    val lastSyncTime: Flow<Long> = ds.data.map { it[LAST_SYNC] ?: 0L }
    suspend fun setLastSyncTime(time: Long) {
        ds.edit { it[LAST_SYNC] = time }
    }

    val isConfigured: Flow<Boolean> = ds.data.map {
        !it[BOT_TOKEN].isNullOrEmpty() && !it[CHAT_ID].isNullOrEmpty()
    }

    companion object {
        private val BOT_TOKEN = stringPreferencesKey("bot_token")
        private val CHAT_ID = stringPreferencesKey("chat_id")
        private val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        private val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private val LAST_SYNC = longPreferencesKey("last_sync")
    }
}
