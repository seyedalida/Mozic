package com.example.mozic.core.domain.repository

import androidx.paging.PagingData
import com.example.mozic.core.common.result.Result
import com.example.mozic.core.domain.model.HomeContent
import com.example.mozic.core.domain.model.HomeSection
import com.example.mozic.core.domain.model.Song
import com.example.mozic.core.domain.model.TopArtist
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    /** Carousel + preview rows in one fetch. */
    fun homeContent(): Flow<HomeContent>

    /** Full, pageable list for a Home section. */
    fun pagedSection(section: HomeSection): Flow<PagingData<Song>>

    suspend fun song(id: String): Result<Song>

    /** Artists ranked by aggregate popularity across their songs, most popular first. */
    fun topArtists(): Flow<List<TopArtist>>

    /** All of one artist's songs, most popular first. */
    fun songsByArtist(artistName: String): Flow<List<Song>>

    /** Fire-and-forget: bumps the song's `popularity` by 1 on a real playback transition. */
    suspend fun recordPlaybackForPopularity(songId: String)
}
