package com.telegrambackup.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.telegrambackup.TelegramBackupApp
import com.telegrambackup.data.local.dao.BackupFileDao
import com.telegrambackup.data.local.entity.UploadStatus
import com.telegrambackup.data.repository.BackupRepository
import com.telegrambackup.worker.FileUploadWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class UploadService : Service() {

    @Inject lateinit var repository: BackupRepository
    @Inject lateinit var backupFileDao: BackupFileDao

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var uploadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action") ?: "upload_all"

        when (action) {
            "upload_all" -> startUploadAll()
            "upload_single" -> {
                val fileId = intent?.getLongExtra("file_id", -1) ?: -1L
                if (fileId != -1L) startUploadSingle(fileId)
            }
            "stop" -> stopUpload()
        }

        return START_STICKY
    }

    private fun startUploadAll() {
        if (uploadJob?.isActive == true) return

        startForeground(NOTIFICATION_ID, createNotification("Preparing upload...", 0))

        uploadJob = scope.launch {
            try {
                val pendingFiles = backupFileDao.getFilesByStatus(UploadStatus.PENDING).first()
                val total = pendingFiles.size
                var completed = 0

                for (file in pendingFiles) {
                    if (!isActive) break

                    updateNotification(
                        "Uploading ${file.fileName}",
                        ((completed * 100) / total)
                    )

                    FileUploadWorker.enqueue(this@UploadService, file.id)
                    completed++
                }

                updateNotification("Upload complete", 100)
                delay(2000)
                stopSelf()
            } catch (e: CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                Log.e("UploadService", "Upload error", e)
                updateNotification("Upload error: ${e.message}", -1)
            }
        }
    }

    private fun startUploadSingle(fileId: Long) {
        startForeground(NOTIFICATION_ID, createNotification("Uploading file...", 0))
        FileUploadWorker.enqueue(this, fileId)
        scope.launch {
            delay(1000)
            stopSelf()
        }
    }

    private fun stopUpload() {
        uploadJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(text: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, TelegramBackupApp.CHANNEL_UPLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Telegram Backup")
            .setContentText(text)
            .apply {
                if (progress >= 0) setProgress(100, progress, progress == 0)
            }
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String, progress: Int) {
        val notification = createNotification(text, progress)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
    }
}
