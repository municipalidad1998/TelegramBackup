package com.iptvplayerpro.di

import android.app.Application
import androidx.room.Room
import com.iptvplayerpro.core.crypto.CryptoManager
import com.iptvplayerpro.core.prefs.SettingsStore
import com.iptvplayerpro.data.local.IptvDatabase
import com.iptvplayerpro.data.remote.Http
import com.iptvplayerpro.data.remote.xtream.XtreamService
import com.iptvplayerpro.data.repository.ChannelRepositoryImpl
import com.iptvplayerpro.data.repository.EpgRepositoryImpl
import com.iptvplayerpro.data.repository.IpInfoRepositoryImpl
import com.iptvplayerpro.data.repository.PlaylistRepositoryImpl
import com.iptvplayerpro.data.repository.UpdateRepositoryImpl
import com.iptvplayerpro.data.repository.VpnRepositoryImpl
import com.iptvplayerpro.domain.repository.ChannelRepository
import com.iptvplayerpro.domain.repository.EpgRepository
import com.iptvplayerpro.domain.repository.IpInfoRepository
import com.iptvplayerpro.domain.repository.PlaylistRepository
import com.iptvplayerpro.domain.repository.UpdateRepository
import com.iptvplayerpro.domain.repository.VpnRepository
import com.iptvplayerpro.player.PlayerController
import com.iptvplayerpro.vpn.VpnController

/**
 * Contenedor de dependencias (inyección manual, sin frameworks).
 * Un único grafo de objetos por proceso, creado en [com.iptvplayerpro.IptvApp].
 */
class AppContainer(app: Application) {

    /** Contexto de aplicación (para resolver URIs de archivos, etc.). */
    val appContext: android.content.Context = app

    val database: IptvDatabase = Room.databaseBuilder(app, IptvDatabase::class.java, IptvDatabase.NAME)
        .fallbackToDestructiveMigration()
        .build()

    val crypto: CryptoManager = CryptoManager()

    val settings: SettingsStore = SettingsStore(app)

    val httpClient = Http.client()

    val xtreamService: XtreamService = XtreamService.create(httpClient)

    val playlistRepository: PlaylistRepository = PlaylistRepositoryImpl(
        database, httpClient, xtreamService, crypto
    )

    val channelRepository: ChannelRepository = ChannelRepositoryImpl(
        database, database.channelDao(), database.playlistDao()
    )

    val epgRepository: EpgRepository = EpgRepositoryImpl(database, httpClient, crypto)

    val vpnRepository: VpnRepository = VpnRepositoryImpl(database.vpnDao(), crypto, httpClient)

    val updateRepository: UpdateRepositoryImpl = UpdateRepositoryImpl(app, httpClient)

    val ipInfoRepository: IpInfoRepository = IpInfoRepositoryImpl(httpClient)

    val vpnController: VpnController = VpnController(app, vpnRepository)

    val playerController: PlayerController = PlayerController(
        app, channelRepository, playlistRepository, epgRepository, settings, httpClient
    )
}
