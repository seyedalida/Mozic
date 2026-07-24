package com.example.mozic.feature.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mozic.core.designsystem.R
import com.example.mozic.core.designsystem.theme.dimens
import com.example.mozic.core.domain.model.chat.Message
import com.example.mozic.core.domain.model.chat.MessagePayload
import com.example.mozic.core.domain.model.chat.MessageStatus
import com.example.mozic.core.ui.component.CoverImage

private val BubbleMaxWidth = 280.dp
private val StatusIconSize = 12.dp

/**
 * Aligned start/end by [isOwn] via `Arrangement`, which mirrors correctly
 * under RTL for free — never force LTR here.
 */
@Composable
fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    onSongShareClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
    ) {
        val bubbleColor = if (isOwn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        val contentColor = if (isOwn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

        Column(
            modifier = Modifier
                .widthIn(max = BubbleMaxWidth)
                .clip(MaterialTheme.shapes.medium)
                .background(bubbleColor)
                .padding(MaterialTheme.dimens.spaceSm),
            // End, not the default Start: the timestamp/status row below is
            // never as wide as the bubble's content, so without this it would
            // hug the *left* edge instead of trailing under the text. Using
            // Alignment.End (not a hardcoded right) is what keeps this correct
            // under RTL for free, same as the outer Row's Arrangement above —
            // neither child here calls fillMaxWidth(), which is what let the
            // old status-only row stretch the whole bubble to BubbleMaxWidth
            // regardless of how short the message actually was.
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.spaceXxs),
        ) {
            when (val payload = message.payload) {
                is MessagePayload.Text -> Text(
                    text = payload.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )

                is MessagePayload.SongShare -> SongShareCard(
                    payload = payload,
                    contentColor = contentColor,
                    onClick = { onSongShareClick(payload.songId) },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.spaceXxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatMessageTime(message.sentAtEpochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = CONTENT_SECONDARY_ALPHA),
                )
                if (isOwn) {
                    Icon(
                        imageVector = message.status.icon(),
                        contentDescription = stringResource(message.status.contentDescriptionRes()),
                        tint = contentColor,
                        modifier = Modifier.size(StatusIconSize),
                    )
                }
            }
        }
    }
}

@Composable
private fun SongShareCard(
    payload: MessagePayload.SongShare,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(MaterialTheme.dimens.spaceXxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.spaceXs),
    ) {
        CoverImage(
            model = payload.coverImageUrl,
            contentDescription = payload.title,
            modifier = Modifier
                .size(MaterialTheme.dimens.listRowImageSize)
                .clip(MaterialTheme.shapes.small),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = payload.title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = payload.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = CONTENT_SECONDARY_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.action_play),
            tint = contentColor,
        )
    }
}

private const val CONTENT_SECONDARY_ALPHA = 0.7f

private fun MessageStatus.icon(): ImageVector = when (this) {
    MessageStatus.SENDING -> Icons.Filled.AccessTime
    MessageStatus.SENT -> Icons.Filled.Done
    MessageStatus.READ -> Icons.Filled.DoneAll
}

private fun MessageStatus.contentDescriptionRes(): Int = when (this) {
    MessageStatus.SENDING -> R.string.chat_status_sending_cd
    MessageStatus.SENT -> R.string.chat_status_sent_cd
    MessageStatus.READ -> R.string.chat_status_read_cd
}
