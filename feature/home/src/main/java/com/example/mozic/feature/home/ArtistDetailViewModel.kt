package com.example.mozic.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.mozic.core.domain.model.Song
import com.example.mozic.core.domain.player.PlayerController
import com.example.mozic.core.domain.repository.SongRepository
import com.example.mozic.feature.home.navigation.ArtistDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    songRepository: SongRepository,
    private val playerController: PlayerController,
) : ViewModel() {

    val artistName: String = savedStateHandle.toRoute<ArtistDetailRoute>().artistName

    val uiState: StateFlow<ArtistDetailUiState> = songRepository.songsByArtist(artistName)
        .map { songs -> ArtistDetailUiState.Content(songs) as ArtistDetailUiState }
        .catch { emit(ArtistDetailUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistDetailUiState.Loading)

    fun onEvent(event: ArtistDetailEvent) {
        when (event) {
            is ArtistDetailEvent.SongClick -> playerController.playQueue(
                songIds = event.queue.map(Song::id),
                startIndex = event.queue.indexOf(event.song).coerceAtLeast(0),
            )

            is ArtistDetailEvent.PlayAll -> playerController.playQueue(
                songIds = event.queue.map(Song::id),
                shuffle = event.shuffle,
            )
        }
    }
}
