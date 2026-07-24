package com.example.mozic.core.data.repository

import androidx.paging.PagingData
import com.example.mozic.core.common.result.Result
import com.example.mozic.core.data.local.dao.DownloadDao
import com.example.mozic.core.data.local.entity.DownloadEntity
import com.example.mozic.core.data.worker.STATE_DOWNLOADED
import com.example.mozic.core.domain.model.HomeContent
import com.example.mozic.core.domain.model.HomeSection
import com.example.mozic.core.domain.model.Song
import com.example.mozic.core.domain.model.TopArtist
import com.example.mozic.core.domain.repository.SongRepository
import com.example.mozic.core.network.repository.NetworkSongRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Wraps [NetworkSongRepository] so a downloaded song's metadata resolves from
 * [DownloadDao]'s local snapshot (see [DownloadEntity]) instead of the network —
 * this is the single seam every `song(id)` caller goes through
 * (`Media3PlayerController.play`/`playQueue`/process-death restore,
 * `DownloadRepositoryImpl.allDownloads`), so a downloaded song plays and lists
 * correctly with no connectivity at all, not just a fallback after a network
 * failure. Local is checked *first*, not as a fallback after a failed/slow
 * network call, so offline playback of a download never waits on a timeout.
 * `homeContent`/`pagedSection` are catalog browsing, not tied to a specific
 * song — those still require network, same as before.
 */
@Singleton
class OfflineAwareSongRepository @Inject constructor(
    private val network: NetworkSongRepository,
    private val downloadDao: DownloadDao,
) : SongRepository {

    override fun homeContent(): Flow<HomeContent> = network.homeContent()

    override fun pagedSection(section: HomeSection): Flow<PagingData<Song>> = network.pagedSection(section)

    override suspend fun song(id: String): Result<Song> {
        val downloaded = downloadDao.get(id)?.takeIf { it.state == STATE_DOWNLOADED }?.toSong()
        if (downloaded != null) return Result.Success(downloaded)
        return network.song(id)
    }

    // Catalog browsing, same as homeContent/pagedSection — no offline-download seam to apply.
    override fun topArtists(): Flow<List<TopArtist>> = network.topArtists()

    override fun songsByArtist(artistName: String): Flow<List<Song>> = network.songsByArtist(artistName)

    // Best-effort — a downloaded song played fully offline just won't bump its
    // popularity until the caller's own fire-and-forget call fails silently;
    // no local queue/retry, same as the rest of this "catalog" half of the interface.
    override suspend fun recordPlaybackForPopularity(songId: String) = network.recordPlaybackForPopularity(songId)
}

private fun DownloadEntity.toSong(): Song = Song(
    id = songId,
    title = title,
    artistName = artistName,
    coverImageUrl = coverImageUrl,
    audioUrl = audioUrl,
    durationMs = durationMs,
)
