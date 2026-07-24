package com.example.mozic.feature.home

import com.example.mozic.core.domain.model.Song

sealed interface ArtistDetailUiState {
    data object Loading : ArtistDetailUiState

    data class Content(val songs: List<Song>) : ArtistDetailUiState

    data object Error : ArtistDetailUiState
}

sealed interface ArtistDetailEvent {
    data class SongClick(val song: Song, val queue: List<Song>) : ArtistDetailEvent

    data class PlayAll(val queue: List<Song>, val shuffle: Boolean) : ArtistDetailEvent
}
