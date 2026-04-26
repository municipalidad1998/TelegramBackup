package com.telegrambackup.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.telegrambackup.data.local.dao.BackupFileDao
import com.telegrambackup.data.local.entity.UploadStatus
import com.telegrambackup.data.preferences.AppPreferences
import com.telegrambackup.util.NetworkUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupFileDao: BackupFileDao,
    private val preferences: AppPreferences,
    private val networkUtils: NetworkUtils
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val wifiOnly = preferences.wifiOnly.first()
            if (wifiOnly && !networkUtils.isWifiConnected()) {
                return@withContext Result.success()
            }

            val pendingFiles = backupFileDao.getFilesByStatus(UploadStatus.PENDING).first()
            for (file in pendingFiles) {
                if (wifiOnly && !networkUtils.isWifiConnected()) break
                FileUploadWorker.enqueue(applicationContext, file.id)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "auto_scan"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoScanWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
