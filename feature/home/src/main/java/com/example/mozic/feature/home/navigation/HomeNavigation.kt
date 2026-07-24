package com.example.mozic.feature.home.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.mozic.core.domain.model.Playlist
import com.example.mozic.feature.home.ArtistDetailScreen
import com.example.mozic.feature.home.HomeScreen
import com.example.mozic.feature.home.HomeSectionListScreen
import com.example.mozic.feature.home.TopArtistsScreen

/**
 * Same plain-fade duration/rationale as `PlaylistsNavigation`'s
 * `PLAYLISTS_NAV_TRANSITION_MS`. Applied unconditionally to every transition
 * leaving/entering `HomeRoute` (both the Home->Library edge and ordinary
 * top-level tab switches) since Home can't reference `:feature:library`'s
 * route type to scope it more narrowly (features never depend on features)
 * — harmless for tab switches, which never hide chrome so have no
 * resize-driven reanchor risk to begin with.
 */
private const val HOME_NAV_TRANSITION_MS = 220

fun NavGraphBuilder.homeScreen(
    navController: NavHostController,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLiked: () -> Unit,
    onNavigateToRecentlyPlayed: () -> Unit,
    onShareClick: (String) -> Unit,
    onNavigateToPlaylistDetail: (Playlist) -> Unit,
) {
    composable<HomeRoute>(
        exitTransition = { fadeOut(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
    ) {
        HomeScreen(
            onNavigateToPlaylists = onNavigateToPlaylists,
            onNavigateToLiked = onNavigateToLiked,
            onNavigateToRecentlyPlayed = onNavigateToRecentlyPlayed,
            onNavigateToTopArtists = { navController.navigate(TopArtistsRoute) },
            onNavigateToSection = { section -> navController.navigate(HomeSectionListRoute(section.name)) },
            onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
        )
    }
    composable<TopArtistsRoute>(
        enterTransition = { fadeIn(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
        exitTransition = { fadeOut(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
        popExitTransition = { fadeOut(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
    ) {
        TopArtistsScreen(
            onBackClick = { navController.popBackStack() },
            onArtistClick = { artistName -> navController.navigate(ArtistDetailRoute(artistName)) },
        )
    }
    composable<ArtistDetailRoute>(
        enterTransition = { fadeIn(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
        exitTransition = { fadeOut(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
        popExitTransition = { fadeOut(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
    ) {
        ArtistDetailScreen(onBackClick = { navController.popBackStack() }, onShareClick = onShareClick)
    }
    composable<HomeSectionListRoute>(
        enterTransition = { fadeIn(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
        exitTransition = { fadeOut(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
        popExitTransition = { fadeOut(animationSpec = tween(HOME_NAV_TRANSITION_MS)) },
    ) {
        HomeSectionListScreen(onBackClick = { navController.popBackStack() }, onShareClick = onShareClick)
    }
}
