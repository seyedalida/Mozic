package com.example.mozic.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `public.songs` (backend/supabase/schema.sql). */
@Serializable
data class SongDto(
    val id: String,
    val title: String,
    @SerialName("artist_name") val artistName: String,
    @SerialName("cover_image_url") val coverImageUrl: String,
    @SerialName("audio_url") val audioUrl: String,
    @SerialName("duration_ms") val durationMs: Long? = null,
)

/** POST body for `rpc/increment_song_popularity` — param name must match the SQL function's. */
@Serializable
data class IncrementPopularityRequestDto(@SerialName("song_id") val songId: String)
