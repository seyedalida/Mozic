package com.example.mozic.core.network.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.mozic.core.common.result.Result
import com.example.mozic.core.domain.model.HomeContent
import com.example.mozic.core.domain.model.HomeRow
import com.example.mozic.core.domain.model.HomeSection
import com.example.mozic.core.domain.model.PlaylistCategory
import com.example.mozic.core.domain.model.Song
import com.example.mozic.core.domain.model.TopArtist
import com.example.mozic.core.domain.repository.SongRepository
import com.example.mozic.core.network.SupabaseCatalogApi
import com.example.mozic.core.network.mapper.playlistsWithCounts
import com.example.mozic.core.network.mapper.toDomain
import com.example.mozic.core.network.paging.SongsPagingSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val HOME_ROW_SIZE = 8
private const val HOME_CAROUSEL_SIZE = 5
private const val HOME_SECTION_PAGE_SIZE = 20
private const val POPULARITY_DESC = "popularity.desc"
private const val CREATED_AT_DESC = "created_at.desc"

/**
 * Wide enough to cover the whole catalog (60 seeded songs, per
 * `backend/supabase/seed.py`) with headroom for it to grow — both
 * [NetworkSongRepository.topArtists] (needs every song to aggregate
 * correctly, not just a top-N slice) and the Discover row (wants a genuine
 * random pick, not just a shuffle of the top few) read from a pool this size.
 */
private const val CATALOG_POOL_SIZE = 200

private const val TOP_ARTISTS_LIMIT = 10

/**
 * Real, Supabase/PostgREST-backed [SongRepository] (C2). Suspend Ktor calls are
 * already non-blocking and internally sequenced onto their own dispatcher — no
 * `@IoDispatcher` to inject here, same reasoning as
 * `UserPreferencesRepositoryImpl`'s DataStore calls.
 */
@Singleton
class NetworkSongRepository @Inject constructor(
    private val api: SupabaseCatalogApi,
) : SongRepository {

    override fun homeContent(): Flow<HomeContent> = flow {
        coroutineScope {
            val popularDeferred = async { api.songs(POPULARITY_DESC, 0 until HOME_ROW_SIZE) }
            val newestDeferred = async { api.songs(CREATED_AT_DESC, 0 until HOME_ROW_SIZE) }
            val worldDeferred = async { api.playlistsWithCounts(PlaylistCategory.WORLD) }
            val localDeferred = async { api.playlistsWithCounts(PlaylistCategory.LOCAL) }
            // Its own fetch (not reused from `popular`/`newest`, both capped at HOME_ROW_SIZE) —
            // a genuine random pick needs a pool spanning the whole catalog, not just the top 8
            // of some other ordering.
            val discoverPoolDeferred = async { api.songs(POPULARITY_DESC, 0 until CATALOG_POOL_SIZE) }

            val popular = popularDeferred.await().items.map { it.toDomain() }
            val newest = newestDeferred.await().items.map { it.toDomain() }
            val discover = discoverPoolDeferred.await().items.map { it.toDomain() }.shuffled().take(HOME_ROW_SIZE)

            emit(
                HomeContent(
                    carousel = popular.take(HOME_CAROUSEL_SIZE),
                    rows = listOf(
                        HomeRow.Songs("Most popular", HomeSection.MOST_POPULAR, popular),
                        HomeRow.Songs("Newest", HomeSection.NEWEST, newest),
                        // No HomeSection — a random pick has no stable "see all" list to page through.
                        HomeRow.Songs("Discover", null, discover),
                        HomeRow.Playlists("Global playlists", PlaylistCategory.WORLD, worldDeferred.await()),
                        HomeRow.Playlists("Local playlists", PlaylistCategory.LOCAL, localDeferred.await()),
                    ),
                ),
            )
        }
    }

    override fun pagedSection(section: HomeSection): Flow<PagingData<Song>> {
        val order = when (section) {
            HomeSection.MOST_POPULAR -> POPULARITY_DESC
            HomeSection.NEWEST -> CREATED_AT_DESC
        }
        return Pager(PagingConfig(pageSize = HOME_SECTION_PAGE_SIZE)) {
            SongsPagingSource(api, order)
        }.flow
    }

    // Same reasoning as OffsetPagingSource.load: this is Song's one-shot
    // Result-wrapped boundary, so any network/serialization failure needs to
    // land as Result.Error rather than propagate.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun song(id: String): Result<Song> = try {
        api.songById(id)?.toDomain()?.let { Result.Success(it) }
            ?: Result.Error(NoSuchElementException("No song with id=$id"))
    } catch (e: Exception) {
        Result.Error(e)
    }

    /**
     * Groups the whole catalog by [Song.artistName] — there's no separate
     * artists table (`backend/README.md`), so "top artists" is entirely
     * derived here, same technique `search_catalog()`'s SQL RPC uses for its
     * own artist results (`distinct on (artist_name) ... order by popularity
     * desc`), just done in Kotlin instead of SQL since this needs every
     * artist's full song group, not one row per artist.
     *
     * Ranked by each artist's single most popular song, not a summed/average
     * score across all their songs — [SongDto] never exposes the raw
     * `popularity` column to the client at all (only used server-side for
     * `order=`), so a per-artist aggregate score genuinely can't be computed
     * here. [songs] arrives popularity-sorted from the server, and
     * `groupBy` preserves each key's first-encounter order, so the resulting
     * map is already ordered by "the rank of that artist's best song" with
     * no extra sorting needed — the first song in each group is also that
     * artist's most popular, reused below as the representative cover.
     */
    override fun topArtists(): Flow<List<TopArtist>> = flow {
        val songs = api.songs(POPULARITY_DESC, 0 until CATALOG_POOL_SIZE).items.map { it.toDomain() }
        val topArtists = songs.groupBy { it.artistName }
            .entries
            .take(TOP_ARTISTS_LIMIT)
            .map { (artistName, artistSongs) ->
                TopArtist(name = artistName, imageUrl = artistSongs.first().coverImageUrl, songCount = artistSongs.size)
            }
        emit(topArtists)
    }

    override fun songsByArtist(artistName: String): Flow<List<Song>> = flow {
        emit(api.songsByArtist(artistName).map { it.toDomain() })
    }

    override suspend fun recordPlaybackForPopularity(songId: String) {
        api.incrementSongPopularity(songId)
    }
}
