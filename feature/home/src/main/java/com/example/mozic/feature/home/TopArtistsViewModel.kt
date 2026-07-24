package com.example.mozic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mozic.core.domain.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TopArtistsViewModel @Inject constructor(
    songRepository: SongRepository,
) : ViewModel() {

    val uiState: StateFlow<TopArtistsUiState> = songRepository.topArtists()
        .map { artists -> TopArtistsUiState.Content(artists) as TopArtistsUiState }
        .catch { emit(TopArtistsUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TopArtistsUiState.Loading)
}
