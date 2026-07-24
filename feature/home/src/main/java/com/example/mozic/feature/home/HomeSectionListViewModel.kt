package com.example.mozic.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.mozic.core.domain.model.HomeSection
import com.example.mozic.core.domain.model.Song
import com.example.mozic.core.domain.player.PlayerController
import com.example.mozic.core.domain.repository.SongRepository
import com.example.mozic.feature.home.navigation.HomeSectionListRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@HiltViewModel
class HomeSectionListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    songRepository: SongRepository,
    private val playerController: PlayerController,
) : ViewModel() {

    val section: HomeSection = HomeSection.valueOf(savedStateHandle.toRoute<HomeSectionListRoute>().sectionName)

    val songs: Flow<PagingData<Song>> = songRepository.pagedSection(section).cachedIn(viewModelScope)

    fun onSongClick(song: Song, queueIds: List<String>) {
        playerController.playQueue(songIds = queueIds, startIndex = queueIds.indexOf(song.id).coerceAtLeast(0))
    }
}
