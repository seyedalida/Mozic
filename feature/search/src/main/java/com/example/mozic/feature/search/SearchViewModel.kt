package com.example.mozic.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.mozic.core.domain.model.SearchFilter
import com.example.mozic.core.domain.model.SearchResult
import com.example.mozic.core.domain.player.PlayerController
import com.example.mozic.core.domain.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 400L
private const val MIN_QUERY_LENGTH = 1

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val playerController: PlayerController,
) : ViewModel() {

    private val queryState = MutableStateFlow("")
    private val filterState = MutableStateFlow(SearchFilter.ALL)

    private val _effects = Channel<SearchEffect>(Channel.BUFFERED)
    val effects: Flow<SearchEffect> = _effects.receiveAsFlow()

    val uiState: StateFlow<SearchUiState> = combine(
        queryState,
        filterState,
        searchRepository.history(),
    ) { query, filter, history ->
        SearchUiState(query = query, filter = filter, history = history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    val results: Flow<PagingData<SearchResult>> = combine(queryState, filterState) { query, filter ->
        query.trim() to filter
    }
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .filter { (query, _) -> query.length >= MIN_QUERY_LENGTH }
        .flatMapLatest { (query, filter) -> searchRepository.search(query, filter) }
        .cachedIn(viewModelScope)

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> queryState.value = event.query

            is SearchEvent.FilterChanged -> filterState.value = event.filter

            is SearchEvent.HistoryItemClick -> queryState.value = event.query

            is SearchEvent.HistoryItemRemove -> viewModelScope.launch {
                searchRepository.removeFromHistory(event.query)
            }

            SearchEvent.Submit -> viewModelScope.launch {
                searchRepository.addToHistory(queryState.value)
            }
        }
    }

    fun onResultClick(result: SearchResult) {
        when (result) {
            is SearchResult.SongResult -> playerController.play(result.song.id)
            is SearchResult.ArtistResult -> _effects.trySend(SearchEffect.NavigateToArtist(result.artist))
            is SearchResult.PlaylistResult ->
                _effects.trySend(SearchEffect.NavigateToPlaylistDetail(result.playlist))
        }
    }
}
