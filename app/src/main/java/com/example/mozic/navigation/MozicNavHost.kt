package com.example.mozic.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import com.example.mozic.feature.chat.navigation.chatScreens
import com.example.mozic.feature.chat.navigation.navigateToChatThread
import com.example.mozic.feature.chat.navigation.navigateToShareSong
import com.example.mozic.feature.downloads.navigation.downloadsScreen
import com.example.mozic.feature.home.navigation.HomeRoute
import com.example.mozic.feature.home.navigation.homeScreen
import com.example.mozic.feature.library.navigation.LibraryListKind
import com.example.mozic.feature.library.navigation.libraryScreen
import com.example.mozic.feature.library.navigation.navigateToLibraryList
import com.example.mozic.feature.player.navigation.navigateToNowPlaying
import com.example.mozic.feature.player.navigation.nowPlayingScreen
import com.example.mozic.feature.playlists.navigation.PlaylistDetailRoute
import com.example.mozic.feature.playlists.navigation.playlistsScreen
import com.example.mozic.feature.profile.navigation.profileScreen
import com.example.mozic.feature.search.navigation.searchScreen
import com.example.mozic.feature.settings.navigation.SettingsRoute
import com.example.mozic.feature.settings.navigation.settingsScreen
import com.example.mozic.feature.social.navigation.socialScreens

@Composable
fun MozicNavHost(
    navController: NavHostController,
    onLoggedOut: () -> Unit,
    onCreatePlaylistLoginRequired: () -> Unit,
    onSongAddedToPlaylist: (playlistTitle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        homeScreen(
            navController,
            onNavigateToPlaylists = {
                navController.navigateToTopLevelDestination(TopLevelDestination.PLAYLISTS)
            },
            onNavigateToLiked = { navController.navigateToLibraryList(LibraryListKind.LIKED) },
            onNavigateToRecentlyPlayed = {
                navController.navigateToLibraryList(LibraryListKind.RECENTLY_PLAYED)
            },
            onShareClick = navController::navigateToShareSong,
        )
        searchScreen(onShareClick = navController::navigateToShareSong)
        downloadsScreen(onShareClick = navController::navigateToShareSong)
        playlistsScreen(
            navController,
            onShareClick = navController::navigateToShareSong,
            onLoginRequiredForCreate = onCreatePlaylistLoginRequired,
        )
        libraryScreen(navController, onShareClick = navController::navigateToShareSong)
        nowPlayingScreen(
            navController,
            onShareClick = navController::navigateToShareSong,
            onSongAddedToPlaylist = onSongAddedToPlaylist,
        )
        profileScreen(onNavigateToSettings = navController::navigateToSettings)
        settingsScreen(
            onBackClick = { navController.popBackStack() },
            onLoggedOut = {
                navController.navigateToTopLevelDestination(TopLevelDestination.HOME)
                onLoggedOut()
            },
        )
        chatScreens(navController, onNavigateToNowPlaying = navController::navigateToNowPlaying)
        socialScreens(
            navController = navController,
            onPlaylistClick = { playlist ->
                navController.navigate(
                    PlaylistDetailRoute(
                        playlistId = playlist.id,
                        title = playlist.title,
                        coverImageUrl = playlist.coverImageUrl,
                        songCount = playlist.songCount,
                        coverImageUrls = playlist.coverImageUrls,
                    ),
                )
            },
            onNavigateToChatThread = navController::navigateToChatThread,
        )
    }
}

/**
 * If [destination] is already on the back stack — either because it's the
 * tab currently showing, or because a screen was pushed on top of it (e.g.
 * Liked Songs above Home) — collapse straight back to it via a real
 * `popBackStack`, so re-tapping a tab always lands on that tab's root screen
 * instead of leaving a drill-in screen on top. Only falls through to a full
 * cross-tab switch (`saveState`/`restoreState` so switching tabs doesn't
 * reset each tab's scroll/search state) when the target tab isn't on the
 * stack yet.
 */
fun NavHostController.navigateToTopLevelDestination(destination: TopLevelDestination) {
    val poppedToExistingTab = popBackStack(route = destination.route, inclusive = false)
    if (poppedToExistingTab) return

    val topLevelNavOptions = NavOptions.Builder()
        .setPopUpTo(graph.findStartDestination().id, inclusive = false, saveState = true)
        .setLaunchSingleTop(true)
        .setRestoreState(true)
        .build()
    navigate(destination.route, topLevelNavOptions)
}

fun NavHostController.navigateToSettings() {
    navigate(SettingsRoute)
}
