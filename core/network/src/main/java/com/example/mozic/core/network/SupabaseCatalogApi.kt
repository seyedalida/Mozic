package com.example.mozic.core.network

import com.example.mozic.core.network.dto.IncrementPopularityRequestDto
import com.example.mozic.core.network.dto.PlaylistDto
import com.example.mozic.core.network.dto.PlaylistInsertDto
import com.example.mozic.core.network.dto.PlaylistSongCountRowDto
import com.example.mozic.core.network.dto.PlaylistSongCoverRowDto
import com.example.mozic.core.network.dto.PlaylistSongInsertDto
import com.example.mozic.core.network.dto.PlaylistSongRowDto
import com.example.mozic.core.network.dto.SearchCatalogRowDto
import com.example.mozic.core.network.dto.SongDto
import com.example.mozic.core.network.paging.RangePage
import com.example.mozic.core.network.paging.applyRange
import com.example.mozic.core.network.paging.toRangePage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

private const val REST_PATH = "/rest/v1/"

/** Matches the 2x2-grid cap the collage cover UI renders (`core/domain/model/Playlist.kt`). */
private const val MAX_COVER_COLLAGE_SONGS = 4

/**
 * Thin wrapper over PostgREST's raw HTTP surface (`backend/supabase/schema.sql`
 * + `backend/README.md` §Endpoints). Returns DTOs only — mapping to domain
 * models happens in `mapper/CatalogMappers.kt`, never here.
 */
@Singleton
class SupabaseCatalogApi @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun songs(order: String, range: IntRange): RangePage<SongDto> {
        val response = client.get(restUrl("songs")) {
            parameter("select", "*")
            parameter("order", order)
            applyRange(range)
        }
        return response.toRangePage()
    }

    suspend fun songById(id: String): SongDto? {
        val response = client.get(restUrl("songs")) {
            parameter("id", "eq.$id")
            parameter("select", "*")
        }
        return response.body<List<SongDto>>().firstOrNull()
    }

    /** Exact match — `artist_name` is a plain text column, not a foreign key (no separate artists table). */
    suspend fun songsByArtist(artistName: String): List<SongDto> {
        val response = client.get(restUrl("songs")) {
            parameter("artist_name", "eq.$artistName")
            parameter("select", "*")
            parameter("order", "popularity.desc")
        }
        return response.body()
    }

    /**
     * `security definer` RPC (schema.sql) — no auth needed, works the same
     * for a logged-out browsing session as a signed-in one, since anon can
     * already play every song.
     */
    suspend fun incrementSongPopularity(songId: String) {
        client.post(restUrl("rpc/increment_song_popularity")) {
            contentType(ContentType.Application.Json)
            setBody(IncrementPopularityRequestDto(songId))
        }
    }

    suspend fun playlists(category: String): List<PlaylistDto> {
        val response = client.get(restUrl("playlists")) {
            parameter("category", "eq.$category")
            parameter("select", "*")
        }
        return response.body()
    }

    /** RLS scopes this to the caller's own JWT — `playlists_insert_own` (schema.sql). */
    suspend fun createPlaylist(accessToken: String, playlist: PlaylistInsertDto) {
        client.post(restUrl("playlists")) {
            bearerAuth(accessToken)
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(playlist)
        }
    }

    suspend fun playlistSongs(playlistId: String, range: IntRange): RangePage<PlaylistSongRowDto> {
        val response = client.get(restUrl("playlist_songs")) {
            parameter("playlist_id", "eq.$playlistId")
            parameter("select", "position,songs(*)")
            parameter("order", "position")
            applyRange(range)
        }
        return response.toRangePage()
    }

    /**
     * RLS scopes this to the playlist's own owner — `playlist_songs_insert_owner`
     * (schema.sql). `resolution=merge-duplicates` makes re-adding an
     * already-present song a safe no-op (keeps its original position) instead
     * of a 409 on the composite primary key.
     */
    suspend fun addSongToPlaylist(accessToken: String, entry: PlaylistSongInsertDto) {
        client.post(restUrl("playlist_songs")) {
            bearerAuth(accessToken)
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            contentType(ContentType.Application.Json)
            setBody(entry)
        }
    }

    /** One batched query instead of N+1 per-playlist count lookups. */
    suspend fun playlistSongCounts(playlistIds: List<String>): Map<String, Int> {
        if (playlistIds.isEmpty()) return emptyMap()
        val response = client.get(restUrl("playlist_songs")) {
            parameter("playlist_id", "in.(${playlistIds.joinToString(",")})")
            parameter("select", "playlist_id")
        }
        val rows: List<PlaylistSongCountRowDto> = response.body()
        return rows.groupingBy { it.playlistId }.eachCount()
    }

    /**
     * Batched, like [playlistSongCounts] — one query for every playlist id
     * instead of N+1. Returns up to the first [MAX_COVER_COLLAGE_SONGS]
     * (by position) song cover URLs per playlist, nulls filtered out.
     */
    suspend fun playlistCoverImageUrls(playlistIds: List<String>): Map<String, List<String>> {
        if (playlistIds.isEmpty()) return emptyMap()
        val response = client.get(restUrl("playlist_songs")) {
            parameter("playlist_id", "in.(${playlistIds.joinToString(",")})")
            parameter("select", "playlist_id,position,songs(cover_image_url)")
        }
        val rows: List<PlaylistSongCoverRowDto> = response.body()
        return rows.groupBy { it.playlistId }.mapValues { (_, playlistRows) ->
            playlistRows.sortedBy { it.position }
                .mapNotNull { it.songs.coverImageUrl }
                .take(MAX_COVER_COLLAGE_SONGS)
        }
    }

    /**
     * Called as `GET .../rpc/search_catalog?q=...&result_type=...`, not POST +
     * JSON body — confirmed empirically that PostgREST only honors the `Range`
     * header (i.e. actually paginates) on GET-invoked RPCs; a POST call with
     * the same params returns the *entire* result set every time regardless
     * of the requested range.
     */
    suspend fun searchCatalog(query: String, resultType: String, range: IntRange): RangePage<SearchCatalogRowDto> {
        val response = client.get(restUrl("rpc/search_catalog")) {
            parameter("q", query)
            parameter("result_type", resultType)
            applyRange(range)
        }
        return response.toRangePage()
    }

    private fun restUrl(path: String) = "${BuildConfig.SUPABASE_URL}$REST_PATH$path"
}
