package com.example.mozic.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mozic.core.designsystem.R as DesignSystemR
import com.example.mozic.core.domain.model.TopArtist
import com.example.mozic.core.ui.component.EmptyState
import com.example.mozic.core.ui.component.MediaListRow
import com.example.mozic.core.ui.component.MediaListRowSkeleton

private const val SKELETON_ROW_COUNT = 8

/** Ranked list of the catalog's top artists (derived from songs — see `SongRepository.topArtists`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopArtistsScreen(
    onBackClick: () -> Unit,
    onArtistClick: (artistName: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TopArtistsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(DesignSystemR.string.home_quick_action_top_artists)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(DesignSystemR.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            TopArtistsUiState.Loading -> LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                items(SKELETON_ROW_COUNT) { MediaListRowSkeleton(imageShape = CircleShape) }
            }

            TopArtistsUiState.Error -> EmptyState(
                icon = Icons.Filled.WifiOff,
                title = stringResource(DesignSystemR.string.state_error),
                subtitle = stringResource(DesignSystemR.string.state_error_subtitle),
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
            )

            is TopArtistsUiState.Content -> if (state.artists.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Star,
                    title = stringResource(DesignSystemR.string.state_empty),
                    subtitle = stringResource(DesignSystemR.string.home_top_artists_empty),
                    modifier = Modifier.padding(innerPadding).fillMaxSize(),
                )
            } else {
                LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    items(state.artists, key = TopArtist::name) { artist ->
                        MediaListRow(
                            imageUrl = artist.imageUrl,
                            title = artist.name,
                            subtitle = stringResource(DesignSystemR.string.home_playlist_song_count, artist.songCount),
                            onClick = { onArtistClick(artist.name) },
                            imageShape = CircleShape,
                            isAvatar = true,
                        )
                    }
                }
            }
        }
    }
}
