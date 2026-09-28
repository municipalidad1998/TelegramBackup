package com.iptvplayerpro.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.iptvplayerpro.IptvApp
import com.iptvplayerpro.data.repository.EpgRepositoryImpl
import com.iptvplayerpro.data.repository.PlaylistRepositoryImpl

/**
 * Actualización programada de listas (M3U y Xtream).
 * - M3U: usa ETag/If-Modified-Since para no re-descargar listas sin cambios.
 * - Xtream: re-autentica; si la cuenta venció marca la lista EXPIRED y
 *   conserva los canales ya guardados.
 */
class PlaylistRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as IptvApp).container
        val repo = container.playlistRepository as PlaylistRepositoryImpl

        // Marca listas cuya expiración temporal ya venció.
        repo.revalidateExpirations()

        var failures = 0
        container.database.playlistDao().getAll()
            .filter { it.autoRefresh }
            .forEach { playlist ->
                val result = repo.refresh(playlist.id)
                if (result.isFailure) failures++
            }
        return if (failures == 0) Result.success() else Result.retry()
    }
}

/** Actualización programada del EPG (XMLTV / Xtream). */
class EpgRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as IptvApp).container
        val repo = container.epgRepository as EpgRepositoryImpl
        return try {
            repo.refreshAll()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
