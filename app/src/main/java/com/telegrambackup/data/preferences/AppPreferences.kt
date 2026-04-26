package com.telegrambackup.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds get() = context.dataStore

    // Telegram config
    val botToken: Flow<String> = ds.data.map { it[BOT_TOKEN] ?: "" }
    val chatId: Flow<String> = ds.data.map { it[CHAT_ID] ?: "" }

    suspend fun setTelegramConfig(token: String, chatId: String) {
        ds.edit {
            it[BOT_TOKEN] = token
            it[CHAT_ID] = chatId
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
