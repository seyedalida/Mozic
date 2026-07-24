package com.example.mozic.feature.chat.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mozic.core.designsystem.R
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date

private const val MS_PER_MINUTE = 60_000L
private const val MS_PER_HOUR = MS_PER_MINUTE * 60
private const val MS_PER_DAY = MS_PER_HOUR * 24
private const val RECENT_DAY_CUTOFF = 7

/**
 * Short "2m"/"3h"/"5d" label for a conversation row's last-message time,
 * falling back to a locale date beyond a week.
 */

@Composable
fun formatConversationTime(epochMs: Long): String {
    val elapsed = System.currentTimeMillis() - epochMs
    return when {
        elapsed < MS_PER_MINUTE -> stringResource(R.string.chat_time_now)
        elapsed < MS_PER_HOUR -> stringResource(R.string.chat_time_minutes_ago, elapsed / MS_PER_MINUTE)
        elapsed < MS_PER_DAY -> stringResource(R.string.chat_time_hours_ago, elapsed / MS_PER_HOUR)
        elapsed < MS_PER_DAY * RECENT_DAY_CUTOFF -> stringResource(R.string.chat_time_days_ago, elapsed / MS_PER_DAY)
        else -> shortDate(epochMs)
    }
}

/** "Today" / "Yesterday" / a locale date, for a chat thread's day separators. */
@Composable
fun formatDaySeparator(epochMs: Long): String {
    val day = epochDay(epochMs)
    val today = epochDay(System.currentTimeMillis())
    return when (today - day) {
        0L -> stringResource(R.string.chat_day_today)
        1L -> stringResource(R.string.chat_day_yesterday)
        else -> shortDate(epochMs)
    }
}

fun isSameDay(aEpochMs: Long, bEpochMs: Long): Boolean = epochDay(aEpochMs) == epochDay(bEpochMs)

/** Locale short time (e.g. "3:39 PM") for a single message bubble's own timestamp. */
fun formatMessageTime(epochMs: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMs))

private fun epochDay(epochMs: Long): Long =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

private fun shortDate(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(epochMs))
