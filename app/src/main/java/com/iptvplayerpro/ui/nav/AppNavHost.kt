package com.iptvplayerpro.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iptvplayerpro.domain.model.ChannelType
import com.iptvplayerpro.ui.screens.channels.ChannelsScreen
import com.iptvplayerpro.ui.screens.epg.EpgGuideScreen
import com.iptvplayerpro.ui.screens.home.HomeScreen
import com.iptvplayerpro.ui.screens.library.LibraryScreen
import com.iptvplayerpro.ui.screens.player.PlayerScreen
import com.iptvplayerpro.ui.screens.playlists.PlaylistEditScreen
import com.iptvplayerpro.ui.screens.playlists.PlaylistsScreen
import com.iptvplayerpro.ui.screens.search.SearchScreen
import com.iptvplayerpro.ui.screens.series.SeriesDetailScreen
import com.iptvplayerpro.ui.screens.series.SeriesScreen
import com.iptvplayerpro.ui.screens.settings.SettingsScreen
import com.iptvplayerpro.ui.screens.vpn.VpnScreen

/** Grafo de navegación de la aplicación. */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(180)) }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } }
            )
        }

        composable(
            route = Routes.CHANNELS,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { entry ->
            val type = entry.arguments?.getString("type")
                ?.let { runCatching { ChannelType.valueOf(it) }.getOrNull() }
                ?: ChannelType.LIVE
            ChannelsScreen(
                type = type,
                onBack = { navController.popBackStack() },
                onPlayChannel = { channelId ->
                    navController.navigate(Routes.player(channelId)) { launchSingleTop = true }
                },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSeries = { channelId ->
                    navController.navigate(Routes.seriesDetail(channelId)) { launchSingleTop = true }
                }
            )
        }

        composable(Routes.SERIES) {
            SeriesScreen(
                onBack = { navController.popBackStack() },
                onOpenSeries = { channelId ->
                    navController.navigate(Routes.seriesDetail(channelId)) { launchSingleTop = true }
                }
            )
        }

        composable(
            route = Routes.SERIES_DETAIL,
            arguments = listOf(navArgument("channelId") { type = NavType.LongType })
        ) { entry ->
            val channelId = entry.arguments?.getLong("channelId") ?: -1L
            SeriesDetailScreen(
                channelId = channelId,
                onBack = { navController.popBackStack() },
                onPlayEpisode = { url, name ->
                    navController.navigate(Routes.playerExternal(url, name)) { launchSingleTop = true }
                }
            )
        }

        composable(
            route = Routes.LIBRARY,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { entry ->
            val tab = entry.arguments?.getInt("tab") ?: 0
            LibraryScreen(
                initialTab = tab,
                onBack = { navController.popBackStack() },
                onPlayChannel = { channelId ->
                    navController.navigate(Routes.player(channelId)) { launchSingleTop = true }
                }
            )
        }

        composable(Routes.EPG) {
            EpgGuideScreen(
                onBack = { navController.popBackStack() },
                onPlayChannel = { channelId ->
                    navController.navigate(Routes.player(channelId)) { launchSingleTop = true }
                }
            )
        }

        composable(Routes.PLAYLISTS) {
            PlaylistsScreen(
                onBack = { navController.popBackStack() },
                onAddList = { navController.navigate(Routes.playlistEdit()) },
                onEditList = { id -> navController.navigate(Routes.playlistEdit(id)) }
            )
        }

        composable(
            route = Routes.PLAYLIST_EDIT,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            PlaylistEditScreen(
                playlistId = id,
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("channelId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("url") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("name") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val channelId = entry.arguments?.getLong("channelId") ?: -1L
            val url = entry.arguments?.getString("url").orEmpty()
            val name = entry.arguments?.getString("name").orEmpty()
            PlayerScreen(
                channelId = channelId,
                externalUrl = url,
                externalName = name,
                onBack = { navController.popBackStack() },
                onOpenVpn = { navController.navigate(Routes.VPN) { launchSingleTop = true } }
            )
        }

        composable(Routes.VPN) {
            VpnScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenVpn = { navController.navigate(Routes.VPN) { launchSingleTop = true } }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onPlayChannel = { channelId ->
                    navController.navigate(Routes.player(channelId)) { launchSingleTop = true }
                },
                onOpenSeries = { channelId ->
                    navController.navigate(Routes.seriesDetail(channelId)) { launchSingleTop = true }
                }
            )
        }
    }
}
