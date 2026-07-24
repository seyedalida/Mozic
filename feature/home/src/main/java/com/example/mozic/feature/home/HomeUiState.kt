package com.example.mozic.feature.home

import com.example.mozic.core.domain.model.HomeRow
import com.example.mozic.core.domain.model.HomeSection
import com.example.mozic.core.domain.model.Playlist
import com.example.mozic.core.domain.model.Song

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        val carousel: List<Song>,
        val rows: List<HomeRow>,
    ) : HomeUiState

    data object Error : HomeUiState
}

/** The four Home quick-action shortcuts. */
enum class QuickAction { LIKED, RECENTLY_PLAYED, MY_PLAYLISTS, TOP_ARTISTS }

sealed interface HomeEvent {
    data class SongClick(val song: Song, val queue: List<Song>) : HomeEvent

    data class PlaylistClick(val playlist: Playlist) : HomeEvent

    data class QuickActionClick(val action: QuickAction) : HomeEvent

    data class SeeAllClick(val section: HomeSection) : HomeEvent

    data object Retry : HomeEvent
}

sealed interface HomeEffect {
    data object NavigateToPlaylists : HomeEffect

    data object NavigateToLiked : HomeEffect

    data object NavigateToRecentlyPlayed : HomeEffect

    data object NavigateToTopArtists : HomeEffect

    data class NavigateToSection(val section: HomeSection) : HomeEffect

    /** Destinations that don't exist yet (playlist detail from Home). */
    data object ShowComingSoon : HomeEffect
}
