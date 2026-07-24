package com.example.mozic.feature.search

import com.example.mozic.core.domain.model.Artist
import com.example.mozic.core.domain.model.Playlist
import com.example.mozic.core.domain.model.SearchFilter

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val history: List<String> = emptyList(),
)

sealed interface SearchEvent {
    data class QueryChanged(val query: String) : SearchEvent

    data class FilterChanged(val filter: SearchFilter) : SearchEvent

    data class HistoryItemClick(val query: String) : SearchEvent

    data class HistoryItemRemove(val query: String) : SearchEvent

    data object Submit : SearchEvent
}

sealed interface SearchEffect {
    data class NavigateToArtist(val artist: Artist) : SearchEffect

    data class NavigateToPlaylistDetail(val playlist: Playlist) : SearchEffect
}
