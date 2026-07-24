package com.example.mozic.feature.home

import com.example.mozic.core.domain.model.TopArtist

sealed interface TopArtistsUiState {
    data object Loading : TopArtistsUiState

    data class Content(val artists: List<TopArtist>) : TopArtistsUiState

    data object Error : TopArtistsUiState
}
