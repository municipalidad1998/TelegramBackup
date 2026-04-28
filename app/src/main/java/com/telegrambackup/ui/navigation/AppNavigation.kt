package com.telegrambackup.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.telegrambackup.data.local.entity.FileType

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    data object Home      : Screen("home",      "Inicio",  Icons.Outlined.Home,       Icons.Filled.Home)
    data object Photos    : Screen("photos",    "Fotos",   Icons.Outlined.Image,       Icons.Filled.Image)
    data object Videos    : Screen("videos",    "Videos",  Icons.Outlined.PlayCircle,  Icons.Filled.PlayCircle)
    data object Documents : Screen("documents", "Docs",    Icons.Outlined.Description, Icons.Filled.Description)
    data object Audio     : Screen("audio",     "Música",  Icons.Outlined.MusicNote,   Icons.Filled.MusicNote)
    data object Gallery   : Screen("gallery",   "Galería", Icons.Outlined.Collections, Icons.Filled.Collections)
    data object Settings  : Screen("settings",  "Ajustes", Icons.Outlined.Settings,    Icons.Filled.Settings)

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

val bottomNavItems = listOf(Screen.Home, Screen.Photos, Screen.Videos, Screen.Documents, Screen.Audio)

private val NavBg       = Color(0xFF0F1219)
private val NavSelected = Color(0xFF4D9FFF)
private val NavUnsel    = Color(0xFF4A5568)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { it.route == currentDestination?.route }

    Scaffold(
        containerColor = Color(0xFF0B0D14),
        bottomBar = {
            if (showBottomBar) {
                PremiumBottomNav(
                    items = bottomNavItems,
                    currentDestination = currentDestination,
                    onItemSelected = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues),
            enterTransition = { fadeIn() },
            exitTransition  = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition  = { fadeOut() }
        ) {
            composable(Screen.Home.route) {
                com.telegrambackup.ui.screens.home.HomeScreen(
                    onNavigateToGallery = { navController.navigate(Screen.Photos.route) },
                    onNavigateToVideo   = { navController.navigate(Screen.VideoPlayer.createRoute(it)) },
                    onNavigateToImage   = { navController.navigate(Screen.ImageViewer.createRoute(it)) }
                )
            }
            composable(Screen.Photos.route) {
                com.telegrambackup.ui.screens.gallery.GalleryScreen(
                    fixedFilter = FileType.IMAGE,
                    onNavigateToVideo = { navController.navigate(Screen.VideoPlayer.createRoute(it)) },
                    onNavigateToImage = { navController.navigate(Screen.ImageViewer.createRoute(it)) }
                )
            }
            composable(Screen.Videos.route) {
                com.telegrambackup.ui.screens.gallery.GalleryScreen(
                    fixedFilter = FileType.VIDEO,
                    onNavigateToVideo = { navController.navigate(Screen.VideoPlayer.createRoute(it)) },
                    onNavigateToImage = { navController.navigate(Screen.ImageViewer.createRoute(it)) }
                )
            }
            composable(Screen.Documents.route) {
                com.telegrambackup.ui.screens.gallery.GalleryScreen(
                    fixedFilter = FileType.DOCUMENT,
                    onNavigateToVideo = { navController.navigate(Screen.VideoPlayer.createRoute(it)) },
                    onNavigateToImage = { navController.navigate(Screen.ImageViewer.createRoute(it)) }
                )
            }
            composable(Screen.Gallery.route) {
                com.telegrambackup.ui.screens.gallery.GalleryScreen(
                    onNavigateToVideo = { navController.navigate(Screen.VideoPlayer.createRoute(it)) },
                    onNavigateToImage = { navController.navigate(Screen.ImageViewer.createRoute(it)) }
                )
            }
            composable(Screen.Audio.route) {
                com.telegrambackup.ui.screens.audio.AudioScreen(
                    onNavigateToPlaylist = { navController.navigate(Screen.PlaylistDetail.createRoute(it)) }
                )
            }
            composable(Screen.Settings.route) {
                com.telegrambackup.ui.screens.settings.SettingsScreen()
            }
            composable(
                route = Screen.VideoPlayer.route,
                arguments = listOf(navArgument("fileId") { type = NavType.LongType })
            ) {
                val fileId = it.arguments?.getLong("fileId") ?: return@composable
                com.telegrambackup.ui.screens.video.VideoPlayerScreen(
                    fileId = fileId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.ImageViewer.route,
                arguments = listOf(navArgument("fileId") { type = NavType.LongType })
            ) {
                val fileId = it.arguments?.getLong("fileId") ?: return@composable
                com.telegrambackup.ui.screens.gallery.ImageViewerScreen(
                    fileId = fileId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) {
                val playlistId = it.arguments?.getLong("playlistId") ?: return@composable
                com.telegrambackup.ui.screens.audio.PlaylistDetailScreen(
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun PremiumBottomNav(
    items: List<Screen>,
    currentDestination: NavDestination?,
    onItemSelected: (Screen) -> Unit
) {
    Surface(color = NavBg, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                PremiumNavItem(screen = screen, selected = selected, onClick = { onItemSelected(screen) })
            }
        }
    }
}

@Composable
private fun PremiumNavItem(screen: Screen, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NavSelected.copy(alpha = 0.15f))
                )
            }
            Icon(
                imageVector = if (selected) screen.selectedIcon else screen.icon,
                contentDescription = screen.title,
                tint = if (selected) NavSelected else NavUnsel,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = screen.title,
            color = if (selected) NavSelected else NavUnsel,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
