package com.iptvplayerpro

import android.app.Application
import androidx.work.Configuration
import com.iptvplayerpro.di.AppContainer
import com.iptvplayerpro.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Aplicación principal: crea el grafo de dependencias y programa las tareas
 * en segundo plano (WorkManager con inicialización on-demand).
 */
class IptvApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Refrescos automáticos: listas (si la fuente lo permite) y EPG.
        appScope.launch {
            container.settings.settings.collect { settings ->
                if (settings.autoRefresh) {
                    WorkScheduler.schedulePlaylistRefresh(this@IptvApp, settings.autoRefreshHours)
                } else {
                    WorkScheduler.cancelPlaylistRefresh(this@IptvApp)
                }
                if (settings.epgRefreshHours > 0) {
                    WorkScheduler.scheduleEpgRefresh(this@IptvApp, settings.epgRefreshHours)
                } else {
                    WorkScheduler.cancelEpgRefresh(this@IptvApp)
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    companion object {
        fun containerOf(app: Application): AppContainer = (app as IptvApp).container
    }
}
