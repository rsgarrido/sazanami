package io.github.rsgarrido.sazanami.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.AlbumListeningStats
import io.github.rsgarrido.sazanami.data.ArtistListeningStats
import io.github.rsgarrido.sazanami.data.ListeningRankingCategory
import io.github.rsgarrido.sazanami.data.TrackListeningStats
import io.github.rsgarrido.sazanami.data.artistIdentity
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import io.github.rsgarrido.sazanami.ui.library.ArtistPicture
import io.github.rsgarrido.sazanami.ui.ratings.CompactRatingIndicator
import io.github.rsgarrido.sazanami.ui.ratings.LocalSongRatingUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopListeningHeader(
    selectedCategory: ListeningRankingCategory,
    onCategorySelected: (ListeningRankingCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val showBasis = maxWidth >= 360.dp && LocalConfiguration.current.fontScale < 1.3f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.statistics_rankings_title),
                    style = MaterialTheme.typography.titleLarge
                )
                if (showBasis) {
                    Text(
                        text = stringResource(R.string.statistics_rankings_basis_compact),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            ListeningRankingCategory.entries.forEachIndexed { index, category ->
                SegmentedButton(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ListeningRankingCategory.entries.size
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    icon = {},
                    label = {
                        Text(
                            text = stringResource(
                                when (category) {
                                    ListeningRankingCategory.TRACKS -> R.string.statistics_rankings_tracks
                                    ListeningRankingCategory.ARTISTS -> R.string.statistics_rankings_artists
                                    ListeningRankingCategory.ALBUMS -> R.string.statistics_rankings_albums
                                }
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
internal fun TrackRankingRow(
    rank: Int,
    stats: TrackListeningStats,
    artworkModel: Any? = null,
    showDivider: Boolean = true,
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
        artwork = {
            RankingArtwork(
                model = artworkModel,
                modifier = Modifier.size(52.dp)
            )
        },
        showDivider = showDivider,
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
    fallbackArtworkModel: Any? = null,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    RankingRow(
        rank = rank,
        primary = stats.artist,
        secondary = null,
        playCount = stats.playCounts.totalPlayCount,
        recordedMs = stats.confirmedDetailedListeningMs,
        artwork = {
            ArtistPicture(
                identity = artistIdentity(stats.artist),
                fallbackModel = fallbackArtworkModel,
                contentDescription = stats.artist,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clearAndSetSemantics { }
            )
        },
        showDivider = showDivider,
        modifier = modifier
    )
}

@Composable
internal fun AlbumRankingRow(
    rank: Int,
    stats: AlbumListeningStats,
    artworkModel: Any? = null,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    RankingRow(
        rank = rank,
        primary = stats.album,
        secondary = stats.albumArtist,
        playCount = stats.playCounts.totalPlayCount,
        recordedMs = stats.confirmedDetailedListeningMs,
        artwork = {
            RankingArtwork(
                model = artworkModel,
                modifier = Modifier.size(52.dp)
            )
        },
        showDivider = showDivider,
        modifier = modifier
    )
}

@Composable
private fun RankingArtwork(
    model: Any?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppShellIcons.AlbumStack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.42f)
        )
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun RankingRow(
    rank: Int,
    primary: String,
    secondary: String?,
    playCount: Long,
    recordedMs: Long,
    artwork: @Composable () -> Unit,
    rating: Int? = null,
    showDivider: Boolean,
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
    val rowColor = when (rank) {
        1 -> MaterialTheme.colorScheme.surfaceContainerHigh
        2 -> MaterialTheme.colorScheme.surfaceContainer
        3 -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = if (rank <= 3 && showDivider) 6.dp else 0.dp)
            .fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = accessibility },
            shape = if (rank <= 3) RoundedCornerShape(14.dp) else RoundedCornerShape(0.dp),
            color = rowColor
        ) {
            BoxWithConstraints {
                val stackedMetrics = maxWidth < 350.dp || LocalConfiguration.current.fontScale >= 1.3f
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RankBadge(rank)
                    artwork()
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = plays,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = recorded,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        if (showDivider && rank > 3) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 100.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val (container, content) = when (rank) {
        1 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        2 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        3 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.size(30.dp),
        shape = CircleShape,
        color = container
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Medium,
                color = content
            )
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
