package com.example.mozic.feature.search.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.mozic.core.domain.model.Artist
import com.example.mozic.core.domain.model.Playlist
import com.example.mozic.feature.search.SearchScreen

fun NavGraphBuilder.searchScreen(
    onShareClick: (String) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToPlaylistDetail: (Playlist) -> Unit,
) {
    composable<SearchRoute> {
        SearchScreen(
            onShareClick = onShareClick,
            onNavigateToArtist = onNavigateToArtist,
            onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
        )
    }
}
