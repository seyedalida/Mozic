package com.example.mozic.core.domain.model

/**
 * One-shot payload for the Home tab: the top carousel plus a set of rows.
 * Rows carry preview items; the full, pageable song lists are fetched
 * separately via [com.example.mozic.core.domain.repository.SongRepository.pagedSection].
 */
data class HomeContent(
    val carousel: List<Song>,
    val rows: List<HomeRow>,
)

sealed interface HomeRow {
    val title: String

    data class Songs(
        override val title: String,
        /** Null for a row with no pageable backing (e.g. Discover's random pick) — no "see all". */
        val section: HomeSection?,
        val songs: List<Song>,
    ) : HomeRow

    data class Playlists(
        override val title: String,
        val category: PlaylistCategory,
        val playlists: List<Playlist>,
    ) : HomeRow
}

/** Pageable song sections on Home; "see all" opens the full paged list. */
enum class HomeSection { MOST_POPULAR, NEWEST }
