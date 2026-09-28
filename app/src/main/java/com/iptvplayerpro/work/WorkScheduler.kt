package com.iptvplayerpro.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Programa/cancela las tareas en segundo plano (WorkManager). */
object WorkScheduler {

    private const val PLAYLIST_WORK = "playlist_refresh"
    private const val EPG_WORK = "epg_refresh"

    fun schedulePlaylistRefresh(context: Context, intervalHours: Int) {
        val wm = WorkManager.getInstance(context)
        if (intervalHours <= 0) {
            wm.cancelUniqueWork(PLAYLIST_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        wm.enqueueUniquePeriodicWork(PLAYLIST_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleEpgRefresh(context: Context, intervalHours: Int) {
        val wm = WorkManager.getInstance(context)
        if (intervalHours <= 0) {
            wm.cancelUniqueWork(EPG_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        wm.enqueueUniquePeriodicWork(EPG_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelPlaylistRefresh(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PLAYLIST_WORK)
    }

    fun cancelEpgRefresh(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(EPG_WORK)
    }
}
