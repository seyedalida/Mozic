package com.example.mozic.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.mozic.core.designsystem.R as DesignSystemR
import com.example.mozic.feature.downloads.navigation.DownloadsRoute
import com.example.mozic.feature.home.navigation.HomeRoute
import com.example.mozic.feature.playlists.navigation.PlaylistsRoute
import com.example.mozic.feature.profile.navigation.ProfileRoute
import com.example.mozic.feature.search.navigation.SearchRoute
import kotlin.reflect.KClass

/**
 * Top-level, save/restore-state destinations (`saveState`/`restoreState` on switch — see
 * [navigateToTopLevelDestination]). [PROFILE] is deliberately **not** rendered as a bottom-nav
 * tab (see `MozicApp`'s `MozicBottomBar` call) — it's reached via the top bar's avatar instead —
 * but stays a real entry here since [navigateToTopLevelDestination]'s save/restore-state
 * back-stack handling is exactly what that avatar tap should get too.
 *
 * [icon] is the same outline glyph for both selected and unselected states —
 * [MozicBottomBar][com.example.mozic.ui.MozicBottomBar] is icon-only (no
 * label, no bold/filled swap on selection) for a minimal look, with tint
 * color the only signal for which tab is active.
 */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val icon: ImageVector,
    val labelRes: Int,
) {
    HOME(
        route = HomeRoute,
        routeClass = HomeRoute::class,
        icon = Icons.Outlined.Home,
        labelRes = DesignSystemR.string.nav_home,
    ),
    SEARCH(
        route = SearchRoute,
        routeClass = SearchRoute::class,
        icon = Icons.Outlined.Search,
        labelRes = DesignSystemR.string.nav_search,
    ),
    DOWNLOADS(
        route = DownloadsRoute,
        routeClass = DownloadsRoute::class,
        icon = Icons.Outlined.DownloadForOffline,
        labelRes = DesignSystemR.string.nav_downloads,
    ),
    PLAYLISTS(
        route = PlaylistsRoute,
        routeClass = PlaylistsRoute::class,
        icon = Icons.AutoMirrored.Outlined.QueueMusic,
        labelRes = DesignSystemR.string.nav_playlists,
    ),
    PROFILE(
        route = ProfileRoute,
        routeClass = ProfileRoute::class,
        icon = Icons.Outlined.Person,
        labelRes = DesignSystemR.string.nav_profile,
    ),
}
