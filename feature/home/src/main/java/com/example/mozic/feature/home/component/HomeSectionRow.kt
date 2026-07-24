package com.example.mozic.feature.home.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.mozic.core.designsystem.R
import com.example.mozic.core.designsystem.theme.dimens

/**
 * Title + horizontally scrolling row, the shared shape for every Home
 * section (and its skeleton). [onSeeAllClick] adds a trailing "See all" —
 * omitted for rows with no full-list screen behind them (e.g. Discover's
 * random pick, which has nothing stable to page through).
 */
@Composable
fun HomeSectionRow(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAllClick: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.screenHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (onSeeAllClick != null) {
                Text(
                    text = stringResource(R.string.action_see_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onSeeAllClick),
                )
            }
        }
        LazyRow(
            modifier = Modifier.padding(top = MaterialTheme.dimens.spaceXs),
            contentPadding = PaddingValues(horizontal = MaterialTheme.dimens.screenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.spaceMd),
            content = content,
        )
    }
}
