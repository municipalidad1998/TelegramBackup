package com.telegrambackup.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Outlined.Home, Icons.Filled.Home)
    data object Gallery : Screen("gallery", "Gallery", Icons.Outlined.Collections, Icons.Filled.Collections)
    data object Audio : Screen("audio", "Audio", Icons.Outlined.MusicNote, Icons.Filled.MusicNote)
    data object Settings : Screen("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)

    data object VideoPlayer : Screen("video_player/{fileId}", "Video", Icons.Outlined.PlayCircle, Icons.Filled.PlayCircle) {
        fun createRoute(fileId: Long) = "video_player/$fileId"
    }

    data object ImageViewer : Screen("image_viewer/{fileId}", "Image", Icons.Outlined.Image, Icons.Filled.Image) {
        fun createRoute(fileId: Long) = "image_viewer/$fileId"
    }

    data object PlaylistDetail : Screen("playlist/{playlistId}", "Playlist", Icons.Outlined.QueueMusic, Icons.Filled.QueueMusic) {
        fun createRoute(playlistId: Long) = "playlist/$playlistId"
    }
}

val bottomNavItems = listOf(Screen.Home, Screen.Gallery, Screen.Audio, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { it.route == currentDestination?.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (selected) screen.selectedIcon else screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                com.telegrambackup.ui.screens.home.HomeScreen(
                    onNavigateToGallery = { navController.navigate(Screen.Gallery.route) },
                    onNavigateToVideo = { fileId -> navController.navigate(Screen.VideoPlayer.createRoute(fileId)) },
                    onNavigateToImage = { fileId -> navController.navigate(Screen.ImageViewer.createRoute(fileId)) }
                )
            }

            composable(Screen.Gallery.route) {
                com.telegrambackup.ui.screens.gallery.GalleryScreen(
                    onNavigateToVideo = { fileId -> navController.navigate(Screen.VideoPlayer.createRoute(fileId)) },
                    onNavigateToImage = { fileId -> navController.navigate(Screen.ImageViewer.createRoute(fileId)) }
                )
            }

            composable(Screen.Audio.route) {
                com.telegrambackup.ui.screens.audio.AudioScreen(
                    onNavigateToPlaylist = { id -> navController.navigate(Screen.PlaylistDetail.createRoute(id)) }
                )
            }

            composable(Screen.Settings.route) {
                com.telegrambackup.ui.screens.settings.SettingsScreen()
            }

            composable(
                route = Screen.VideoPlayer.route,
                arguments = listOf(navArgument("fileId") { type = NavType.LongType })
            ) { backStackEntry ->
                val fileId = backStackEntry.arguments?.getLong("fileId") ?: return@composable
                com.telegrambackup.ui.screens.video.VideoPlayerScreen(
                    fileId = fileId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.ImageViewer.route,
                arguments = listOf(navArgument("fileId") { type = NavType.LongType })
            ) { backStackEntry ->
                val fileId = backStackEntry.arguments?.getLong("fileId") ?: return@composable
                com.telegrambackup.ui.screens.gallery.ImageViewerScreen(
                    fileId = fileId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
                com.telegrambackup.ui.screens.audio.PlaylistDetailScreen(
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
