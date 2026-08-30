package io.github.rsgarrido.sazanami.data

import io.github.rsgarrido.sazanami.data.local.FavoriteSongEntity
import io.github.rsgarrido.sazanami.data.local.PlaylistSongEntity
import io.github.rsgarrido.sazanami.data.local.SongPlayStatsEntity

internal data class PersistedSongReferenceRows(
    val favorites: List<FavoriteSongEntity>,
    val playlistRows: List<PlaylistSongEntity>,
    val historyRows: List<SongPlayStatsEntity>
)

internal data class FavoriteReferenceBackfill(
    val oldReferenceKeys: List<String>,
    val rows: List<FavoriteSongEntity>,
    val result: SongReferenceReconciliation
)

internal data class PlaylistReferenceBackfill(
    val rows: List<PlaylistSongEntity>,
    val result: SongReferenceReconciliation
)

internal data class HistoryReferenceBackfill(
    val oldReferenceKeys: List<String>,
    val rows: List<SongPlayStatsEntity>,
    val result: SongReferenceReconciliation
)

internal data class SongReferenceReconciliationPlan(
    val favorites: FavoriteReferenceBackfill,
    val playlists: PlaylistReferenceBackfill,
    val history: HistoryReferenceBackfill
) {
    val inspectedRowCount: Int = favorites.result.inspectedCount +
        playlists.result.inspectedCount + history.result.inspectedCount
    val writeCount: Int = favorites.result.backfilledCount +
        playlists.result.backfilledCount + history.result.backfilledCount
}

internal object SongReferenceReconciliationPlanner {
    fun plan(
        index: SongReferenceIndex,
        rows: PersistedSongReferenceRows
    ): SongReferenceReconciliationPlan = SongReferenceReconciliationPlan(
        favorites = planFavorites(index, rows.favorites),
        playlists = planPlaylists(index, rows.playlistRows),
        history = planHistory(index, rows.historyRows)
    )

    fun planFavorites(
        index: SongReferenceIndex,
        rows: List<FavoriteSongEntity>
    ): FavoriteReferenceBackfill {
        val memberships = linkedSetOf<String>()
        val resolvedGroups = linkedMapOf<
            String,
            MutableList<Pair<FavoriteSongEntity, FavoriteSongEntity>>
        >()
        val updates = linkedMapOf<String, FavoriteSongEntity>()
        val deletes = linkedSetOf<String>()
        var unresolved = 0
        var ambiguous = 0
        var changed = 0

        rows.forEach { favorite ->
            when (val resolution = index.resolve(favorite.toSongReference())) {
                is SongReferenceResolution.Resolved -> {
                    val desired = favorite.withSongReference(resolution.song)
                    memberships += desired.referenceKey
                    resolvedGroups.getOrPut(desired.referenceKey) { mutableListOf() }
                        .add(favorite to desired)
                }

                is SongReferenceResolution.Ambiguous -> ambiguous += 1
                SongReferenceResolution.NotFound -> unresolved += 1
            }
        }

        resolvedGroups.forEach { (targetKey, group) ->
            val winner = group.firstOrNull { (original) -> original.referenceKey == targetKey }
                ?: group.first()
            group.forEach { (original, desired) ->
                if (original != desired || original !== winner.first) changed += 1
                if (original !== winner.first || original.referenceKey != targetKey) {
                    deletes += original.referenceKey
                }
            }
            if (winner.first != winner.second) updates[targetKey] = winner.second
        }

        return FavoriteReferenceBackfill(
            oldReferenceKeys = deletes.toList(),
            rows = updates.values.toList(),
            result = SongReferenceReconciliation(
                resolvedMembershipKeys = memberships,
                unresolvedCount = unresolved,
                ambiguousCount = ambiguous,
                backfilledCount = changed,
                inspectedCount = rows.size
            )
        )
    }

    fun planPlaylists(
        index: SongReferenceIndex,
        rows: List<PlaylistSongEntity>
    ): PlaylistReferenceBackfill {
        val updates = ArrayList<PlaylistSongEntity>()
        var unresolved = 0
        var ambiguous = 0

        rows.forEach { playlistSong ->
            when (val resolution = index.resolve(playlistSong.toSongReference())) {
                is SongReferenceResolution.Resolved -> {
                    playlistSong.withSongReference(resolution.song)
                        .takeIf { it != playlistSong }
                        ?.let(updates::add)
                }

                is SongReferenceResolution.Ambiguous -> ambiguous += 1
                SongReferenceResolution.NotFound -> unresolved += 1
            }
        }

        return PlaylistReferenceBackfill(
            rows = updates.distinctBy { it.playlistSongId },
            result = SongReferenceReconciliation(
                unresolvedCount = unresolved,
                ambiguousCount = ambiguous,
                backfilledCount = updates.size,
                inspectedCount = rows.size
            )
        )
    }

    fun planHistory(
        index: SongReferenceIndex,
        rows: List<SongPlayStatsEntity>
    ): HistoryReferenceBackfill {
        val resolvedGroups = linkedMapOf<
            String,
            MutableList<Pair<SongPlayStatsEntity, SongPlayStatsEntity>>
        >()
        var unresolved = 0
        var ambiguous = 0

        rows.forEach { stats ->
            when (val resolution = index.resolve(stats.toSongReference())) {
                is SongReferenceResolution.Resolved -> {
                    val desired = stats.withSongReference(resolution.song)
                    resolvedGroups.getOrPut(desired.referenceKey) { mutableListOf() }
                        .add(stats to desired)
                }

                is SongReferenceResolution.Ambiguous -> ambiguous += 1
                SongReferenceResolution.NotFound -> unresolved += 1
            }
        }

        val originalByKey = rows.associateBy { it.referenceKey }
        val deletes = linkedSetOf<String>()
        val updates = ArrayList<SongPlayStatsEntity>()
        var changed = 0

        resolvedGroups.forEach { (targetKey, group) ->
            val first = group.first().second
            val merged = first.copy(
                playCount = group.sumOf { it.first.playCount },
                firstPlayedAt = group.minOf { it.first.firstPlayedAt },
                lastPlayedAt = group.maxOf { it.first.lastPlayedAt }
            )
            val originalTarget = originalByKey[targetKey]
            val oldKeys = group.mapTo(linkedSetOf()) { it.first.referenceKey }
            val groupChanged = group.size > 1 || originalTarget != merged || oldKeys.any {
                it != targetKey
            }
            if (groupChanged) {
                changed += group.count { (original, desired) -> original != desired }
                    .coerceAtLeast(1)
                deletes += oldKeys.filter { it != targetKey }
                updates += merged
            }
        }

        return HistoryReferenceBackfill(
            oldReferenceKeys = deletes.toList(),
            rows = updates.distinctBy { it.referenceKey },
            result = SongReferenceReconciliation(
                unresolvedCount = unresolved,
                ambiguousCount = ambiguous,
                backfilledCount = changed,
                inspectedCount = rows.size
            )
        )
    }
}
