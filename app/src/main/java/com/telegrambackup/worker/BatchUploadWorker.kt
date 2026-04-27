package com.telegrambackup.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.telegrambackup.MainActivity
import com.telegrambackup.TelegramBackupApp
import com.telegrambackup.data.local.dao.BackupFileDao
import com.telegrambackup.data.local.entity.FileType
import com.telegrambackup.data.local.entity.UploadStatus
import com.telegrambackup.data.preferences.AppPreferences
import com.telegrambackup.network.TelegramApiService
import com.telegrambackup.util.FileUtils
import com.telegrambackup.util.NetworkUtils
import com.telegrambackup.util.UploadHistoryStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class BatchUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupFileDao: BackupFileDao,
    private val telegramApi: TelegramApiService,
    private val preferences: AppPreferences,
    private val networkUtils: NetworkUtils,
    private val historyStore: UploadHistoryStore
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "batch_upload"
        private const val NOTIF_ID = 2001

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<BatchUploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.KEEP, request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        backupFileDao.resetStuckUploading()

        val token = preferences.botToken.first()
        val chatId = preferences.chatId.first()
        if (token.isEmpty() || chatId.isEmpty()) return@withContext Result.failure()

        val pendingFiles = backupFileDao.getPendingFiles().first()
        val total = pendingFiles.size
        var done = 0

        for (file in pendingFiles) {
            if (preferences.uploadPaused.first()) {
                dismissNotification()
                return@withContext Result.retry()
            }
            if (preferences.wifiOnly.first() && !networkUtils.isWifiConnected()) {
                dismissNotification()
                return@withContext Result.retry()
            }

            val localFile = File(file.filePath)
            if (!localFile.exists()) {
                backupFileDao.markError(file.id, "Archivo no encontrado")
                continue
            }

            try {
                backupFileDao.updateStatus(file.id, UploadStatus.UPLOADING, 0)
                val caption = "📁 ${file.fileName}\n💾 ${FileUtils.formatFileSize(file.fileSize)}"

                val result = when (file.fileType) {
                    FileType.IMAGE -> telegramApi.sendPhoto(token, chatId, localFile, caption) { p ->
                        updateNotification(file.fileName, p, done, total)
                    }
                    FileType.VIDEO -> telegramApi.sendVideo(token, chatId, localFile, caption, onProgress = { p ->
                        updateNotification(file.fileName, p, done, total)
                    })
                    FileType.AUDIO -> telegramApi.sendAudio(token, chatId, localFile, caption) { p ->
                        updateNotification(file.fileName, p, done, total)
                    }
                    FileType.DOCUMENT -> telegramApi.sendDocument(token, chatId, localFile, caption) { p ->
                        updateNotification(file.fileName, p, done, total)
                    }
                }

                result.fold(
                    onSuccess = { msg ->
                        val tgFileId = when (file.fileType) {
                            FileType.IMAGE -> msg.photo?.lastOrNull()?.file_id
                            FileType.VIDEO -> msg.video?.file_id
                            FileType.AUDIO -> msg.audio?.file_id
                            FileType.DOCUMENT -> msg.document?.file_id
                        } ?: ""
                        backupFileDao.markUploaded(file.id, tgFileId, msg.message_id, System.currentTimeMillis(), chatId)
                        // Save to external history so it survives DB reset
                        historyStore.record(file.fileHash ?: "", file.filePath, tgFileId, chatId, msg.message_id)
                        done++
                    },
                    onFailure = { e ->
                        backupFileDao.markError(file.id, e.message ?: "Error")
                        Log.w("BatchUpload", "Failed ${file.fileName}: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                backupFileDao.markError(file.id, e.message ?: "Error desconocido")
                Log.e("BatchUpload", "Exception on ${file.fileName}", e)
            }
        }

        dismissNotification()
        Result.success()
    }

    private fun updateNotification(fileName: String, progress: Int, done: Int, total: Int) {
        val tapIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, TelegramBackupApp.CHANNEL_UPLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Subiendo a Telegram ($done/$total)")
            .setContentText("$fileName ($progress%)")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, notification)
    }

    private fun dismissNotification() {
        applicationContext.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }
}
