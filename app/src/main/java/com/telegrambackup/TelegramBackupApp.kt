package com.telegrambackup

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
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
        createNotificationChannels()
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
