package com.example.mozic.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mozic.core.designsystem.R as DesignSystemR
import com.example.mozic.core.designsystem.theme.dimens
import com.example.mozic.core.designsystem.theme.mozicColors
import com.example.mozic.core.domain.model.Song
import com.example.mozic.core.ui.component.Avatar
import com.example.mozic.core.ui.component.EmptyState
import com.example.mozic.core.ui.component.MediaListRow
import com.example.mozic.core.ui.component.MediaListRowSkeleton
import com.example.mozic.core.ui.component.ShareIconButton

private const val SKELETON_ROW_COUNT = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    onBackClick: () -> Unit,
    onShareClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
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
        val songs = (uiState as? ArtistDetailUiState.Content)?.songs.orEmpty()

        LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            item {
                ArtistDetailHeader(
                    artistName = viewModel.artistName,
                    coverImageUrl = songs.firstOrNull()?.coverImageUrl,
                    songCount = songs.size,
                    playAllEnabled = songs.isNotEmpty(),
                    onPlayAll = { shuffle -> viewModel.onEvent(ArtistDetailEvent.PlayAll(songs, shuffle)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.dimens.screenHorizontalPadding),
                )
            }

            when (uiState) {
                ArtistDetailUiState.Loading -> items(SKELETON_ROW_COUNT) {
                    MediaListRowSkeleton(imageShape = CircleShape)
                }

                ArtistDetailUiState.Error, is ArtistDetailUiState.Content -> if (songs.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.MusicNote,
                            title = stringResource(DesignSystemR.string.state_empty),
                            subtitle = stringResource(DesignSystemR.string.playlist_detail_empty_subtitle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MaterialTheme.dimens.spaceXl),
                        )
                    }
                } else {
                    items(songs, key = Song::id) { song ->
                        MediaListRow(
                            imageUrl = song.coverImageUrl,
                            title = song.title,
                            subtitle = song.artistName,
                            onClick = { viewModel.onEvent(ArtistDetailEvent.SongClick(song, songs)) },
                            trailing = { ShareIconButton(onClick = { onShareClick(song.id) }) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistDetailHeader(
    artistName: String,
    coverImageUrl: String?,
    songCount: Int,
    playAllEnabled: Boolean,
    onPlayAll: (shuffle: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.spaceMd),
    ) {
        Avatar(
            model = coverImageUrl,
            contentDescription = artistName,
            modifier = Modifier
                .size(MaterialTheme.dimens.heroCoverSize)
                .clip(CircleShape),
        )

        Text(
            text = artistName,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(DesignSystemR.string.home_playlist_song_count, songCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.spaceSm)) {
            Button(
                onClick = { onPlayAll(false) },
                enabled = playAllEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = MaterialTheme.mozicColors.textTertiary,
                ),
                modifier = Modifier.background(
                    brush = MaterialTheme.mozicColors.accentGradient,
                    shape = ButtonDefaults.shape,
                ),
            ) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(MaterialTheme.dimens.spaceXs))
                Text(
                    text = stringResource(DesignSystemR.string.playlists_play_all),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            OutlinedButton(onClick = { onPlayAll(true) }, enabled = playAllEnabled) {
                Icon(imageVector = Icons.Outlined.Shuffle, contentDescription = null)
                Spacer(Modifier.width(MaterialTheme.dimens.spaceXs))
                Text(stringResource(DesignSystemR.string.playlists_shuffle))
            }
        }
    }
}
