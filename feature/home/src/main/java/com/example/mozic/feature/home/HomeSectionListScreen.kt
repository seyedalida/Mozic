package com.example.mozic.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.mozic.core.designsystem.R as DesignSystemR
import com.example.mozic.core.designsystem.theme.dimens
import com.example.mozic.core.ui.component.MediaListRow
import com.example.mozic.core.ui.component.MediaListRowSkeleton
import com.example.mozic.core.ui.component.ShareIconButton

private const val SKELETON_ROW_COUNT = 8

/**
 * The full, pageable list behind a Home song row's "See all" — generic over
 * [HomeSection] since `SongRepository.pagedSection` already is; reused for
 * both Most Popular and Newest instead of one screen per section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSectionListScreen(
    onBackClick: () -> Unit,
    onShareClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeSectionListViewModel = hiltViewModel(),
) {
    val pagingItems = viewModel.songs.collectAsLazyPagingItems()
    val isInitialLoad = pagingItems.loadState.refresh is LoadState.Loading
    val queueIds = remember(pagingItems.itemCount) {
        pagingItems.itemSnapshotList.items.map { it.id }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(viewModel.section.titleRes())) },
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
        LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isInitialLoad) {
                items(SKELETON_ROW_COUNT) { MediaListRowSkeleton() }
            } else {
                items(pagingItems.itemCount) { index ->
                    pagingItems[index]?.let { song ->
                        MediaListRow(
                            imageUrl = song.coverImageUrl,
                            title = song.title,
                            subtitle = song.artistName,
                            onClick = { viewModel.onSongClick(song, queueIds) },
                            trailing = { ShareIconButton(onClick = { onShareClick(song.id) }) },
                        )
                    }
                }
                if (pagingItems.loadState.append is LoadState.Loading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaterialTheme.dimens.spaceMd),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
