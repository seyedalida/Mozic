package com.example.mozic.core.domain.model

/**
 * An artist derived from [Song.artistName] — there's no separate `artists`
 * table (see `backend/README.md`), so this is computed client-side from the
 * song catalog, not fetched as its own resource. [imageUrl] borrows the
 * artist's most popular song's cover; [songCount] and ranking both come from
 * aggregating that artist's songs. Deliberately distinct from [Artist] (the
 * social-search/share-card model, which carries a real synthetic id and a
 * [Artist.followerCount] that means something different there) rather than
 * repurposing it.
 */
data class TopArtist(
    val name: String,
    val imageUrl: String?,
    val songCount: Int,
)
