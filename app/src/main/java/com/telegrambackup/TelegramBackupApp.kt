package com.telegrambackup

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Environment
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltAndroidApp
class TelegramBackupApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Set up global crash handler BEFORE anything else
        setupCrashHandler()

        createNotificationChannels()
        restoreConfigFromExternalBackup()
    }

    /**
     * Global crash handler - shows crash screen with error details.
     */
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Write to file as backup
                val crashDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "TelegramBackup"
                )
                crashDir.mkdirs()
                val crashFile = File(crashDir, "crash_${System.currentTimeMillis()}.txt")
                val writer = PrintWriter(crashFile)
                writer.println("Crash on ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                writer.println("Thread: ${thread.name}")
                writer.println("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                writer.println()
                throwable.printStackTrace(writer)
                writer.close()

                // Launch crash screen
                CrashActivity.launch(this@TelegramBackupApp, thread.name, throwable)
            } catch (e: Exception) {
                // If crash screen fails, fall back to default handler
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Restore config from external storage backup (survives uninstall/reinstall).
     */
    private fun restoreConfigFromExternalBackup() {
        try {
            val backupDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "TelegramBackup"
            )
            val backupFile = File(backupDir, "config_backup.json")
            if (!backupFile.exists()) return

            val json = backupFile.readText()
            val token = extractJsonValue(json, "bot_token")
            val chatId = extractJsonValue(json, "chat_id")

            if (token.isNotEmpty() && chatId.isNotEmpty()) {
                // Save to app-internal SharedPreferences
                val prefs = getSharedPreferences("telegram_backup_prefs", MODE_PRIVATE)
                val currentToken = prefs.getString("bot_token", "") ?: ""
                if (currentToken.isEmpty()) {
                    prefs.edit()
                        .putString("bot_token", token)
                        .putString("chat_id", chatId)
                        .apply()
                    Log.i("TelegramBackupApp", "Config restored from external backup")
                }
            }
        } catch (e: Exception) {
            Log.e("TelegramBackupApp", "Failed to restore config from external backup", e)
        }
    }

    private fun extractJsonValue(json: String, key: String): String {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*?)\""
        val regex = Regex(pattern)
        val match = regex.find(json)
        return match?.groupValues?.get(1) ?: ""
    }

    private fun createNotificationChannels() {
        val uploadChannel = NotificationChannel(
            CHANNEL_UPLOAD,
            "File Upload",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows upload progress"
        }

        val audioChannel = NotificationChannel(
            CHANNEL_AUDIO,
            "Audio Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Audio playback controls"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(uploadChannel)
        manager.createNotificationChannel(audioChannel)
    }

    companion object {
        const val CHANNEL_UPLOAD = "upload_channel"
        const val CHANNEL_AUDIO = "audio_channel"
    }
}
