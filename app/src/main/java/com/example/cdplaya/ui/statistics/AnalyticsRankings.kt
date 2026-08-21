package com.example.cdplaya.ui.statistics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cdplaya.R
import com.example.cdplaya.data.AlbumListeningStats
import com.example.cdplaya.data.ArtistListeningStats
import com.example.cdplaya.data.ListeningRankingCategory
import com.example.cdplaya.data.TrackListeningStats
import com.example.cdplaya.ui.ratings.CompactRatingIndicator
import com.example.cdplaya.ui.ratings.LocalSongRatingUi

@Composable
internal fun TopListeningHeader(
    selectedCategory: ListeningRankingCategory,
    onCategorySelected: (ListeningRankingCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDescription = stringResource(R.string.statistics_range_selected)
    val notSelectedDescription = stringResource(R.string.statistics_range_not_selected)
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.statistics_rankings_title),
            style = MaterialTheme.typography.titleLarge
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ListeningRankingCategory.entries.forEach { category ->
                val selected = selectedCategory == category
                FilterChip(
                    selected = selected,
                    onClick = { onCategorySelected(category) },
                    label = {
                        Text(
                            stringResource(
                                when (category) {
                                    ListeningRankingCategory.TRACKS -> R.string.statistics_rankings_tracks
                                    ListeningRankingCategory.ARTISTS -> R.string.statistics_rankings_artists
                                    ListeningRankingCategory.ALBUMS -> R.string.statistics_rankings_albums
                                }
                            )
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            stateDescription = if (selected) selectedDescription else notSelectedDescription
                        }
                )
            }
        }
        Text(
            text = stringResource(R.string.statistics_rankings_basis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun TrackRankingRow(
    rank: Int,
    stats: TrackListeningStats,
    modifier: Modifier = Modifier
) {
    val rating = LocalSongRatingUi.current.state
        .ratingsByTrackIdentityId[stats.trackIdentityId]
    RankingRow(
        rank = rank,
        primary = stats.title,
        secondary = formatStatisticsTrackMetadata(stats.artist, stats.album),
        playCount = stats.playCounts.totalPlayCount,
        recordedMs = stats.confirmedDetailedListeningMs,
        rating = rating,
        modifier = modifier
    )
}

internal fun formatStatisticsTrackMetadata(artist: String, album: String): String =
    listOf(artist.trim(), album.trim())
        .filter(String::isNotBlank)
        .joinToString(" · ")

@Composable
internal fun ArtistRankingRow(
    rank: Int,
    stats: ArtistListeningStats,
    modifier: Modifier = Modifier
) {
    RankingRow(
        rank = rank,
        primary = stats.artist,
        secondary = null,
        playCount = stats.playCounts.totalPlayCount,
        recordedMs = stats.confirmedDetailedListeningMs,
        modifier = modifier
    )
}

@Composable
internal fun AlbumRankingRow(
    rank: Int,
    stats: AlbumListeningStats,
    modifier: Modifier = Modifier
) {
    RankingRow(
        rank = rank,
        primary = stats.album,
        secondary = stats.albumArtist,
        playCount = stats.playCounts.totalPlayCount,
        recordedMs = stats.confirmedDetailedListeningMs,
        modifier = modifier
    )
}

@Composable
private fun RankingRow(
    rank: Int,
    primary: String,
    secondary: String?,
    playCount: Long,
    recordedMs: Long,
    rating: Int? = null,
    modifier: Modifier = Modifier
) {
    val plays = pluralStringResource(
        R.plurals.statistics_rankings_plays,
        playCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        formatAnalyticsCount(playCount)
    )
    val recorded = stringResource(
        R.string.statistics_rankings_recorded_time,
        rankingDurationText(recordedMs)
    )
    val accessibility = buildString {
        append(rank)
        append(". ")
        append(primary)
        secondary?.takeIf { it.isNotBlank() }?.let { append(". "); append(it) }
        append(". ")
        append(plays)
        append(". ")
        append(recorded)
    }
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = accessibility },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BoxWithConstraints {
            val stackedMetrics = maxWidth < 340.dp || LocalConfiguration.current.fontScale >= 1.3f
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = rank.toString(),
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = primary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    secondary?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    rating?.let { value ->
                        CompactRatingIndicator(rating = value, iconFirst = true)
                    }
                    if (stackedMetrics) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = plays,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = recorded,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
                if (!stackedMetrics) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = plays,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = recorded,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rankingDurationText(milliseconds: Long): String {
    val parts = listeningDurationParts(milliseconds)
    return when {
        parts.days > 0L -> stringResource(R.string.statistics_duration_days_hours, parts.days, parts.hours)
        parts.hours > 0L -> stringResource(R.string.statistics_duration_hours_minutes, parts.hours, parts.minutes)
        else -> stringResource(R.string.statistics_duration_minutes, parts.minutes)
    }
}
