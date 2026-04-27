package com.telegrambackup.worker

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.telegrambackup.R
import com.telegrambackup.TelegramBackupApp
import com.telegrambackup.data.local.dao.BackupFileDao
import com.telegrambackup.data.local.entity.UploadStatus
import com.telegrambackup.data.preferences.AppPreferences
import com.telegrambackup.network.TelegramApiService
import com.telegrambackup.util.FileUtils
import com.telegrambackup.util.NetworkUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class FileUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupFileDao: BackupFileDao,
    private val telegramApi: TelegramApiService,
    private val preferences: AppPreferences,
    private val networkUtils: NetworkUtils
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val fileId = inputData.getLong("file_id", -1)
        if (fileId == -1L) return@withContext Result.failure()

        val wifiOnly = preferences.wifiOnly.first()
        if (wifiOnly && !networkUtils.isWifiConnected()) {
            return@withContext Result.retry()
        }

        val file = backupFileDao.getFileById(fileId)
            ?: return@withContext Result.failure()

        if (file.uploadStatus == UploadStatus.UPLOADED) {
            return@withContext Result.success()
        }

        val token = preferences.botToken.first()
        val chatId = preferences.chatId.first()

        if (token.isEmpty() || chatId.isEmpty()) {
            return@withContext Result.failure()
        }

        try {
            backupFileDao.updateStatus(fileId, UploadStatus.UPLOADING, 0)

            val localFile = File(file.filePath)
            if (!localFile.exists()) {
                backupFileDao.markError(fileId, "File not found")
                return@withContext Result.failure()
            }

            val caption = "📁 ${file.fileName}\n💾 ${FileUtils.formatFileSize(file.fileSize)}"

            val result = when (file.fileType) {
                com.telegrambackup.data.local.entity.FileType.IMAGE ->
                    telegramApi.sendPhoto(token, chatId, localFile, caption) { progress ->
                        setProgressAsync(workDataOf("progress" to progress))
                        updateNotification(file.fileName, progress)
                    }
                com.telegrambackup.data.local.entity.FileType.VIDEO ->
                    telegramApi.sendVideo(token, chatId, localFile, caption, onProgress = { progress ->
                        setProgressAsync(workDataOf("progress" to progress))
                        updateNotification(file.fileName, progress)
                    })
                com.telegrambackup.data.local.entity.FileType.AUDIO ->
                    telegramApi.sendAudio(token, chatId, localFile, caption) { progress ->
                        setProgressAsync(workDataOf("progress" to progress))
                        updateNotification(file.fileName, progress)
                    }
                com.telegrambackup.data.local.entity.FileType.DOCUMENT ->
                    telegramApi.sendDocument(token, chatId, localFile, caption) { progress ->
                        setProgressAsync(workDataOf("progress" to progress))
                        updateNotification(file.fileName, progress)
                    }
            }

            result.fold(
                onSuccess = { msg ->
                    val tgFileId = when (file.fileType) {
                        com.telegrambackup.data.local.entity.FileType.IMAGE -> msg.photo?.lastOrNull()?.file_id
                        com.telegrambackup.data.local.entity.FileType.VIDEO -> msg.video?.file_id
                        com.telegrambackup.data.local.entity.FileType.AUDIO -> msg.audio?.file_id
                        com.telegrambackup.data.local.entity.FileType.DOCUMENT -> msg.document?.file_id
                    } ?: ""

                    backupFileDao.markUploaded(fileId, tgFileId, msg.message_id, System.currentTimeMillis(), chatId)
                    Result.success()
                },
                onFailure = { e ->
                    backupFileDao.markError(fileId, e.message ?: "Upload failed")
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            )
        } catch (e: Exception) {
            backupFileDao.markError(fileId, e.message ?: "Unknown error")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun updateNotification(fileName: String, progress: Int) {
        val notification = NotificationCompat.Builder(applicationContext, TelegramBackupApp.CHANNEL_UPLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Uploading")
            .setContentText("$fileName ($progress%)")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(UPLOAD_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val UPLOAD_NOTIFICATION_ID = 1001

        fun enqueue(context: Context, fileId: Long): OneTimeWorkRequest {
            val data = workDataOf("file_id" to fileId)
            val request = OneTimeWorkRequestBuilder<FileUploadWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "upload_$fileId",
                    ExistingWorkPolicy.KEEP,
                    request
                )
            return request
        }
    }
}
