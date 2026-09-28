package com.iptvplayerpro.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iptvplayerpro.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Ajustes del usuario. */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColors: Boolean = true,
    // Buffer del reproductor
    val minBufferMs: Int = 30_000,
    val maxBufferMs: Int = 120_000,
    val bufferForPlaybackMs: Int = 2_500,
    val rebufferMs: Int = 5_000,
    // Actualizaciones automáticas de listas
    val autoRefresh: Boolean = true,
    val autoRefreshHours: Int = 24,
    // EPG
    val epgRefreshHours: Int = 12,
    // Reproductor
    val autoPip: Boolean = true,
    val retryCount: Int = 3,
    // Fuente oficial de actualizaciones (JSON con la info de versión)
    val updateUrl: String = ""
)

/**
 * Persistencia de preferencias con DataStore. Los valores sensibles
 * (contraseñas, configuraciones VPN) NO van aquí: usan [com.iptvplayerpro.core.crypto.CryptoManager].
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_colors")
        val MIN_BUFFER = intPreferencesKey("min_buffer_ms")
        val MAX_BUFFER = intPreferencesKey("max_buffer_ms")
        val PLAYBACK_BUFFER = intPreferencesKey("playback_buffer_ms")
        val REBUFFER = intPreferencesKey("rebuffer_ms")
        val AUTO_REFRESH = booleanPreferencesKey("auto_refresh")
        val AUTO_REFRESH_HOURS = intPreferencesKey("auto_refresh_hours")
        val EPG_REFRESH_HOURS = intPreferencesKey("epg_refresh_hours")
        val AUTO_PIP = booleanPreferencesKey("auto_pip")
        val RETRY_COUNT = intPreferencesKey("retry_count")
        val UPDATE_URL = stringPreferencesKey("update_url")
    }

    private val defaults = Settings()

    val settings: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        Settings(
            themeMode = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
            dynamicColors = prefs[Keys.DYNAMIC] ?: defaults.dynamicColors,
            minBufferMs = prefs[Keys.MIN_BUFFER] ?: defaults.minBufferMs,
            maxBufferMs = prefs[Keys.MAX_BUFFER] ?: defaults.maxBufferMs,
            bufferForPlaybackMs = prefs[Keys.PLAYBACK_BUFFER] ?: defaults.bufferForPlaybackMs,
            rebufferMs = prefs[Keys.REBUFFER] ?: defaults.rebufferMs,
            autoRefresh = prefs[Keys.AUTO_REFRESH] ?: defaults.autoRefresh,
            autoRefreshHours = prefs[Keys.AUTO_REFRESH_HOURS] ?: defaults.autoRefreshHours,
            epgRefreshHours = prefs[Keys.EPG_REFRESH_HOURS] ?: defaults.epgRefreshHours,
            autoPip = prefs[Keys.AUTO_PIP] ?: defaults.autoPip,
            retryCount = prefs[Keys.RETRY_COUNT] ?: defaults.retryCount,
            updateUrl = prefs[Keys.UPDATE_URL] ?: defaults.updateUrl
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.settingsDataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setDynamicColors(value: Boolean) = context.settingsDataStore.edit { it[Keys.DYNAMIC] = value }
    suspend fun setBuffer(min: Int, max: Int, playback: Int, rebuffer: Int) =
        context.settingsDataStore.edit {
            it[Keys.MIN_BUFFER] = min
            it[Keys.MAX_BUFFER] = max
            it[Keys.PLAYBACK_BUFFER] = playback
            it[Keys.REBUFFER] = rebuffer
        }
    suspend fun setAutoRefresh(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.AUTO_REFRESH] = enabled }
    suspend fun setAutoRefreshHours(hours: Int) = context.settingsDataStore.edit { it[Keys.AUTO_REFRESH_HOURS] = hours }
    suspend fun setEpgRefreshHours(hours: Int) = context.settingsDataStore.edit { it[Keys.EPG_REFRESH_HOURS] = hours }
    suspend fun setAutoPip(value: Boolean) = context.settingsDataStore.edit { it[Keys.AUTO_PIP] = value }
    suspend fun setRetryCount(value: Int) = context.settingsDataStore.edit { it[Keys.RETRY_COUNT] = value }
    suspend fun setUpdateUrl(value: String) = context.settingsDataStore.edit { it[Keys.UPDATE_URL] = value.trim() }
}
