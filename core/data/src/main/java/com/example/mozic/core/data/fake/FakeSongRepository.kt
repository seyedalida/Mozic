package com.example.mozic.core.data.fake

import androidx.paging.PagingData
import com.example.mozic.core.common.result.Result
import com.example.mozic.core.domain.model.HomeContent
import com.example.mozic.core.domain.model.HomeRow
import com.example.mozic.core.domain.model.HomeSection
import com.example.mozic.core.domain.model.PlaylistCategory
import com.example.mozic.core.domain.model.Song
import com.example.mozic.core.domain.model.TopArtist
import com.example.mozic.core.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Simulated network latency so `HomeUiState.Loading`'s shimmer is actually visible. */
private const val FAKE_HOME_LOAD_DELAY_MS = 700L

@Singleton
class FakeSongRepository @Inject constructor() : SongRepository {
    override fun homeContent(): Flow<HomeContent> = flow {
        delay(FAKE_HOME_LOAD_DELAY_MS)
        emit(
            HomeContent(
                carousel = SampleData.songs.take(5),
                rows = listOf(
                    HomeRow.Songs("Most popular", HomeSection.MOST_POPULAR, SampleData.songs.take(8)),
                    HomeRow.Songs("Newest", HomeSection.NEWEST, SampleData.songs.takeLast(8)),
                    HomeRow.Songs("Discover", null, SampleData.songs.shuffled().take(8)),
                    HomeRow.Playlists(
                        "Global playlists",
                        PlaylistCategory.WORLD,
                        playlistsOf(PlaylistCategory.WORLD),
                    ),
                    HomeRow.Playlists(
                        "Local playlists",
                        PlaylistCategory.LOCAL,
                        playlistsOf(PlaylistCategory.LOCAL),
                    ),
                ),
            ),
        )
    }

    override fun pagedSection(section: HomeSection): Flow<PagingData<Song>> {
        val list = when (section) {
            HomeSection.MOST_POPULAR -> SampleData.songs
            HomeSection.NEWEST -> SampleData.songs.reversed()
        }
        return flowOf(PagingData.from(list))
    }

    override suspend fun song(id: String): Result<Song> =
        SampleData.songs.find { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(NoSuchElementException("No song with id=$id"))

    override fun topArtists(): Flow<List<TopArtist>> = flowOf(
        SampleData.songs.groupBy { it.artistName }
            .map { (name, songs) ->
                TopArtist(name = name, imageUrl = songs.first().coverImageUrl, songCount = songs.size)
            },
    )

    override fun songsByArtist(artistName: String): Flow<List<Song>> =
        flowOf(SampleData.songs.filter { it.artistName == artistName })

    override suspend fun recordPlaybackForPopularity(songId: String) = Unit

    private fun playlistsOf(category: PlaylistCategory) =
        SampleData.playlists.filter { it.category == category }
}
