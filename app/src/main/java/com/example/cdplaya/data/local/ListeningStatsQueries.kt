package com.example.cdplaya.data.local

import com.example.cdplaya.data.AnalyticsBucketBoundary
import com.example.cdplaya.data.ListeningAnalyticsBucketBuilder
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

data class ListeningStatsQuerySpec(
    val startInclusive: Long?,
    val endExclusive: Long?,
    val sourceStorageValues: List<String>,
    val includeLegacyBaseline: Boolean
) {
    init {
        require((startInclusive == null) == (endExclusive == null))
        require(startInclusive == null || startInclusive < requireNotNull(endExclusive))
        require(sourceStorageValues.isNotEmpty())
    }
}

object ListeningStatsQueries {
    fun detailedEventBounds(spec: ListeningStatsQuerySpec): SupportSQLiteQuery {
        val filtered = filteredEvents(spec)
        return SimpleSQLiteQuery(
            """
                SELECT
                    MIN(attributionAt) AS earliestStartedAt,
                    MAX(attributionAt) AS latestStartedAt
                FROM listening_events
                WHERE ${filtered.whereClause}
            """.trimIndent(),
            filtered.args.toTypedArray()
        )
    }

    fun trend(
        boundaries: List<AnalyticsBucketBoundary>,
        sourceStorageValues: List<String>
    ): SupportSQLiteQuery {
        require(boundaries.size <= ListeningAnalyticsBucketBuilder.MAX_BUCKET_COUNT)
        require(sourceStorageValues.isNotEmpty())
        if (boundaries.isEmpty()) {
            return SimpleSQLiteQuery(
                """
                    SELECT
                        0 AS bucketIndex,
                        0 AS startInclusive,
                        1 AS endExclusive,
                        0 AS listenedMs,
                        0 AS qualifiedPlayCount,
                        0 AS totalAttemptCount,
                        0 AS naturalCompletionCount
                    WHERE 0
                """.trimIndent()
            )
        }
        boundaries.forEachIndexed { index, boundary ->
            require(boundary.index == index)
            if (index > 0) require(boundaries[index - 1].endExclusive == boundary.startInclusive)
        }
        val values = boundaries.joinToString(",") { boundary ->
            "(${boundary.index}, ?, ?)"
        }
        val sourcePlaceholders = sourceStorageValues.joinToString(",") { "?" }
        val args = buildList<Any> {
            boundaries.forEach { boundary ->
                add(boundary.startInclusive)
                add(boundary.endExclusive)
            }
            addAll(sourceStorageValues)
        }
        require(args.size <= MAX_TREND_QUERY_BINDINGS)
        val sql = """
            WITH buckets(bucketIndex, startInclusive, endExclusive) AS (
                VALUES $values
            )
            SELECT
                b.bucketIndex,
                b.startInclusive,
                b.endExclusive,
                COALESCE(SUM(e.listenedMs), 0) AS listenedMs,
                COALESCE(SUM(CASE WHEN e.qualifiedAsPlay = 1 THEN 1 ELSE 0 END), 0) AS qualifiedPlayCount,
                COUNT(e.id) AS totalAttemptCount,
                COALESCE(SUM(CASE WHEN e.completionClassification IN ('native_natural', 'source_documented_natural') THEN 1 ELSE 0 END), 0) AS naturalCompletionCount
            FROM buckets b
            LEFT JOIN listening_events e
              ON e.attributionAt >= b.startInclusive
             AND e.attributionAt < b.endExclusive
             AND e.source IN ($sourcePlaceholders)
             AND e.publicationState != 'import_pending'
            GROUP BY b.bucketIndex, b.startInclusive, b.endExclusive
            ORDER BY b.bucketIndex ASC
        """.trimIndent()
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    fun overview(spec: ListeningStatsQuerySpec): SupportSQLiteQuery {
        val filtered = filteredEvents(spec)
        val includeLegacy = if (spec.includeLegacyBaseline) 1 else 0
        val sql = """
            WITH detailed AS (
                SELECT
                    COUNT(*) AS detailedEventCount,
                    COALESCE(SUM(listenedMs), 0) AS detailedListeningMs,
                    COALESCE(SUM(CASE WHEN qualifiedAsPlay = 1 THEN 1 ELSE 0 END), 0) AS detailedQualifiedPlayCount,
                    COALESCE(SUM(CASE WHEN completionClassification IN ('native_natural', 'source_documented_natural') THEN 1 ELSE 0 END), 0) AS naturalCompletionCount,
                    COALESCE(SUM(CASE WHEN qualifiedAsPlay = 0 THEN 1 ELSE 0 END), 0) AS nonQualifiedAttemptCount,
                    MIN(attributionAt) AS firstDetailedEventAt,
                    MAX(attributionAt) AS latestDetailedEventAt,
                    MIN(CASE WHEN qualifiedAsPlay = 1 THEN attributionAt END) AS firstQualifiedAt,
                    MAX(CASE WHEN qualifiedAsPlay = 1 THEN attributionAt END) AS latestQualifiedAt
                FROM listening_events
                WHERE ${filtered.whereClause}
            ), baseline AS (
                SELECT
                    COALESCE(SUM(historicalPlayCount), 0) AS legacyPlayCount,
                    MIN(firstKnownPlayedAt) AS firstLegacyAt,
                    MAX(lastKnownPlayedAt) AS latestLegacyAt,
                    COUNT(*) AS legacyIdentityCount
                FROM legacy_listening_baselines
            )
            SELECT
                CASE WHEN $includeLegacy = 1 THEN baseline.legacyPlayCount ELSE 0 END AS legacyPlayCount,
                detailed.detailedQualifiedPlayCount,
                detailed.detailedListeningMs,
                detailed.naturalCompletionCount,
                detailed.nonQualifiedAttemptCount,
                detailed.detailedEventCount,
                detailed.firstDetailedEventAt,
                detailed.latestDetailedEventAt,
                ${combinedTime("CASE WHEN $includeLegacy = 1 THEN baseline.firstLegacyAt END", "detailed.firstQualifiedAt", earliest = true)} AS firstKnownPlayAt,
                ${combinedTime("CASE WHEN $includeLegacy = 1 THEN baseline.latestLegacyAt END", "detailed.latestQualifiedAt", earliest = false)} AS latestKnownPlayAt,
                CASE WHEN $includeLegacy = 1 THEN baseline.legacyIdentityCount ELSE 0 END AS legacyIdentityCount
            FROM detailed CROSS JOIN baseline
        """.trimIndent()
        return SimpleSQLiteQuery(sql, filtered.args.toTypedArray())
    }

    fun tracks(
        spec: ListeningStatsQuerySpec,
        orderByListeningTime: Boolean,
        qualifiedOnly: Boolean,
        limit: Int
    ): SupportSQLiteQuery {
        require(limit > 0)
        val cte = trackStatsCte(spec, includePreferredBinding = true)
        val total = "(track_stats.legacyPlayCount + track_stats.detailedQualifiedPlayCount)"
        val order = if (orderByListeningTime) {
            "track_stats.detailedListeningMs DESC, $total DESC, COALESCE(track_stats.latestKnownPlayAt, -9223372036854775808) DESC, track_stats.trackIdentityId ASC"
        } else {
            "$total DESC, COALESCE(track_stats.latestKnownPlayAt, -9223372036854775808) DESC, track_stats.trackIdentityId ASC"
        }
        val qualifiedPredicate = if (qualifiedOnly) "WHERE $total > 0" else ""
        val sql = """
            ${cte.sql}
            SELECT * FROM track_stats
            $qualifiedPredicate
            ORDER BY $order
            LIMIT $limit
        """.trimIndent()
        return SimpleSQLiteQuery(sql, cte.args.toTypedArray())
    }

    fun recentlyPlayed(spec: ListeningStatsQuerySpec, limit: Int): SupportSQLiteQuery {
        require(limit > 0)
        val cte = trackStatsCte(spec, includePreferredBinding = true)
        val total = "(track_stats.legacyPlayCount + track_stats.detailedQualifiedPlayCount)"
        val sql = """
            ${cte.sql}
            SELECT * FROM track_stats
            WHERE $total > 0 AND track_stats.latestKnownPlayAt IS NOT NULL
            ORDER BY track_stats.latestKnownPlayAt DESC, track_stats.trackIdentityId ASC
            LIMIT $limit
        """.trimIndent()
        return SimpleSQLiteQuery(sql, cte.args.toTypedArray())
    }

    /**
     * Provider-neutral metrics keyed only by currently playable canonical local identities.
     * Driving from playable identities preserves zero rows for never/not-recently-played rules.
     * Reconciliation sources cannot surface separately because they have no local binding.
     */
    fun canonicalMetrics(
        spec: ListeningStatsQuerySpec,
        trackIdentityId: Long? = null
    ): SupportSQLiteQuery {
        require(trackIdentityId == null || trackIdentityId > 0L)
        val cte = trackStatsCte(spec, includePreferredBinding = false)
        val identityPredicate = trackIdentityId?.let { "AND playable.trackIdentityId = ?" }.orEmpty()
        val args = buildList<Any> {
            addAll(cte.args)
            trackIdentityId?.let(::add)
        }
        val sql = """
            ${cte.sql}
            SELECT
                playable.trackIdentityId,
                COALESCE(track_stats.legacyPlayCount, 0) AS legacyPlayCount,
                COALESCE(track_stats.detailedQualifiedPlayCount, 0) AS detailedQualifiedPlayCount,
                COALESCE(track_stats.detailedEventCount, 0) AS detailedEventCount,
                COALESCE(track_stats.detailedListeningMs, 0) AS detailedListeningMs,
                COALESCE(track_stats.naturalCompletionCount, 0) AS naturalCompletionCount,
                COALESCE(track_stats.nonQualifiedAttemptCount, 0) AS nonQualifiedAttemptCount,
                track_stats.firstKnownPlayAt,
                track_stats.latestKnownPlayAt,
                track_stats.latestDetailedEventAt,
                ratings.rating AS effectiveRating
            FROM (
                SELECT DISTINCT trackIdentityId
                FROM local_track_bindings
                WHERE missingSince IS NULL
            ) playable
            LEFT JOIN track_stats ON track_stats.trackIdentityId = playable.trackIdentityId
            LEFT JOIN song_ratings ratings ON ratings.trackIdentityId = playable.trackIdentityId
            WHERE 1 = 1
            $identityPredicate
            ORDER BY playable.trackIdentityId ASC
        """.trimIndent()
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    fun albums(spec: ListeningStatsQuerySpec, limit: Int): SupportSQLiteQuery {
        require(limit > 0)
        val cte = trackStatsCte(spec, includePreferredBinding = false)
        val albumArtistKey = "COALESCE(NULLIF(LOWER(TRIM(track_stats.albumArtistSnapshot)), ''), NULLIF(track_stats.normalizedArtist, ''), '<unknown-artist>')"
        val albumKey = "COALESCE(NULLIF(track_stats.normalizedAlbum, ''), '<unknown-album>')"
        val groupingKey = "($albumArtistKey || '|' || $albumKey)"
        val total = "SUM(track_stats.legacyPlayCount) + SUM(track_stats.detailedQualifiedPlayCount)"
        val sql = """
            ${cte.sql}
            SELECT
                $groupingKey AS groupingKey,
                COALESCE(MIN(NULLIF(TRIM(track_stats.albumSnapshot), '')), 'Unknown Album') AS displayAlbum,
                COALESCE(MIN(NULLIF(TRIM(track_stats.albumArtistSnapshot), '')), MIN(NULLIF(TRIM(track_stats.artistSnapshot), '')), 'Unknown Artist') AS displayAlbumArtist,
                SUM(track_stats.legacyPlayCount) AS legacyPlayCount,
                SUM(track_stats.detailedQualifiedPlayCount) AS detailedQualifiedPlayCount,
                SUM(track_stats.detailedListeningMs) AS detailedListeningMs,
                SUM(track_stats.naturalCompletionCount) AS naturalCompletionCount,
                COUNT(*) AS trackCount,
                MAX(track_stats.latestKnownPlayAt) AS latestKnownPlayAt
            FROM track_stats
            GROUP BY $groupingKey
            HAVING $total > 0
            ORDER BY $total DESC, COALESCE(MAX(track_stats.latestKnownPlayAt), -9223372036854775808) DESC, groupingKey ASC
            LIMIT $limit
        """.trimIndent()
        return SimpleSQLiteQuery(sql, cte.args.toTypedArray())
    }

    fun artists(spec: ListeningStatsQuerySpec, limit: Int): SupportSQLiteQuery {
        require(limit > 0)
        val cte = trackStatsCte(spec, includePreferredBinding = false)
        val artistKey = "COALESCE(NULLIF(track_stats.normalizedArtist, ''), '<unknown-artist>')"
        val albumKey = "COALESCE(NULLIF(track_stats.normalizedAlbum, ''), '<unknown-album>')"
        val total = "SUM(track_stats.legacyPlayCount) + SUM(track_stats.detailedQualifiedPlayCount)"
        val sql = """
            ${cte.sql}
            SELECT
                $artistKey AS groupingKey,
                COALESCE(MIN(NULLIF(TRIM(track_stats.artistSnapshot), '')), 'Unknown Artist') AS displayArtist,
                SUM(track_stats.legacyPlayCount) AS legacyPlayCount,
                SUM(track_stats.detailedQualifiedPlayCount) AS detailedQualifiedPlayCount,
                SUM(track_stats.detailedListeningMs) AS detailedListeningMs,
                SUM(track_stats.naturalCompletionCount) AS naturalCompletionCount,
                COUNT(*) AS distinctTrackCount,
                COUNT(DISTINCT $albumKey) AS distinctAlbumCount,
                MAX(track_stats.latestKnownPlayAt) AS latestKnownPlayAt
            FROM track_stats
            GROUP BY $artistKey
            HAVING $total > 0
            ORDER BY $total DESC, COALESCE(MAX(track_stats.latestKnownPlayAt), -9223372036854775808) DESC, groupingKey ASC
            LIMIT $limit
        """.trimIndent()
        return SimpleSQLiteQuery(sql, cte.args.toTypedArray())
    }

    fun recentEvents(spec: ListeningStatsQuerySpec, limit: Int): SupportSQLiteQuery {
        require(limit > 0)
        val filtered = filteredEvents(spec, alias = "e")
        val sql = """
            SELECT
                e.eventUuid,
                e.trackIdentityId,
                i.titleSnapshot,
                i.artistSnapshot,
                i.albumSnapshot,
                e.source,
                e.startedAt,
                e.endedAt,
                e.attributionAt,
                e.listenedMs,
                e.qualifiedAsPlay,
                e.qualificationReason,
                e.endReason,
                e.playbackSessionId
            FROM listening_events e
            JOIN listening_track_identities i ON i.id = e.trackIdentityId
            WHERE ${filtered.whereClause}
            ORDER BY e.attributionAt DESC, e.id DESC
            LIMIT $limit
        """.trimIndent()
        return SimpleSQLiteQuery(sql, filtered.args.toTypedArray())
    }

    private fun trackStatsCte(
        spec: ListeningStatsQuerySpec,
        includePreferredBinding: Boolean
    ): SqlAndArgs {
        val filtered = filteredEvents(spec, alias = "e")
        val legacyCte = if (spec.includeLegacyBaseline) {
            """,
            resolved_baseline AS (
                SELECT
                    COALESCE(r.targetIdentityId, b.trackIdentityId) AS resolvedIdentityId,
                    COALESCE(SUM(b.historicalPlayCount), 0) AS historicalPlayCount,
                    MIN(b.firstKnownPlayedAt) AS firstKnownPlayedAt,
                    MAX(b.lastKnownPlayedAt) AS lastKnownPlayedAt
                FROM legacy_listening_baselines b
                LEFT JOIN listening_identity_reconciliations r
                  ON r.sourceIdentityId = b.trackIdentityId
                GROUP BY COALESCE(r.targetIdentityId, b.trackIdentityId)
            )"""
        } else ""
        val legacyCount = if (spec.includeLegacyBaseline) "COALESCE(b.historicalPlayCount, 0)" else "0"
        val legacyFirst = if (spec.includeLegacyBaseline) "b.firstKnownPlayedAt" else "NULL"
        val legacyLatest = if (spec.includeLegacyBaseline) "b.lastKnownPlayedAt" else "NULL"
        val legacyJoin = if (spec.includeLegacyBaseline) {
            "LEFT JOIN resolved_baseline b ON b.resolvedIdentityId = i.id"
        } else ""
        val legacyPresence = if (spec.includeLegacyBaseline) "b.resolvedIdentityId IS NOT NULL" else "0 = 1"
        val bindingCte = if (includePreferredBinding) {
            """,
            preferred_binding AS (
                SELECT candidate.*
                FROM local_track_bindings candidate
                WHERE NOT EXISTS (
                    SELECT 1 FROM local_track_bindings preferred
                    WHERE preferred.trackIdentityId = candidate.trackIdentityId
                      AND (
                        (candidate.missingSince IS NOT NULL AND preferred.missingSince IS NULL)
                        OR (
                            (candidate.missingSince IS NULL) = (preferred.missingSince IS NULL)
                            AND (
                                preferred.lastSeenAt > candidate.lastSeenAt
                                OR (preferred.lastSeenAt = candidate.lastSeenAt AND preferred.id < candidate.id)
                            )
                        )
                      )
                )
            )"""
        } else ""
        val bindingColumns = if (includePreferredBinding) {
            """
                pb.id AS localTrackBindingId,
                pb.referenceKey,
                pb.mediaStoreId,
                pb.volumeName,
                pb.contentUri,
                pb.relativePath,
                pb.displayName,
                pb.fileSizeBytes,
                pb.dateModifiedEpochSeconds,
                pb.durationMsSnapshot AS bindingDurationMsSnapshot,
                pb.legacyStableKey,
                pb.portableKey,
                pb.portableKeyVersion,
                pb.missingSince,
            """.trimIndent()
        } else ""
        val bindingJoin = if (includePreferredBinding) {
            "LEFT JOIN preferred_binding pb ON pb.trackIdentityId = i.id"
        } else ""
        val sql = """
            WITH resolved_detailed AS (
                SELECT
                    COALESCE(r.targetIdentityId, e.trackIdentityId) AS resolvedIdentityId,
                    COUNT(*) AS detailedEventCount,
                    COALESCE(SUM(e.listenedMs), 0) AS detailedListeningMs,
                    COALESCE(SUM(CASE WHEN e.qualifiedAsPlay = 1 THEN 1 ELSE 0 END), 0) AS detailedQualifiedPlayCount,
                    COALESCE(SUM(CASE WHEN e.completionClassification IN ('native_natural', 'source_documented_natural') THEN 1 ELSE 0 END), 0) AS naturalCompletionCount,
                    COALESCE(SUM(CASE WHEN e.qualifiedAsPlay = 0 THEN 1 ELSE 0 END), 0) AS nonQualifiedAttemptCount,
                    MIN(CASE WHEN e.qualifiedAsPlay = 1 THEN e.attributionAt END) AS firstQualifiedAt,
                    MAX(CASE WHEN e.qualifiedAsPlay = 1 THEN e.attributionAt END) AS latestQualifiedAt,
                    MAX(e.attributionAt) AS latestDetailedEventAt
                FROM listening_events e
                LEFT JOIN listening_identity_reconciliations r
                  ON r.sourceIdentityId = e.trackIdentityId
                WHERE ${filtered.whereClause}
                GROUP BY COALESCE(r.targetIdentityId, e.trackIdentityId)
            )$legacyCte$bindingCte,
            track_stats AS (
                SELECT
                    i.id AS trackIdentityId,
                    i.titleSnapshot,
                    i.artistSnapshot,
                    i.albumSnapshot,
                    i.albumArtistSnapshot,
                    i.durationMsSnapshot,
                    i.normalizedArtist,
                    i.normalizedAlbum,
                    $bindingColumns
                    ratings.rating AS effectiveRating,
                    $legacyCount AS legacyPlayCount,
                    COALESCE(d.detailedQualifiedPlayCount, 0) AS detailedQualifiedPlayCount,
                    COALESCE(d.detailedListeningMs, 0) AS detailedListeningMs,
                    COALESCE(d.detailedEventCount, 0) AS detailedEventCount,
                    COALESCE(d.naturalCompletionCount, 0) AS naturalCompletionCount,
                    COALESCE(d.nonQualifiedAttemptCount, 0) AS nonQualifiedAttemptCount,
                    ${combinedTime(legacyFirst, "d.firstQualifiedAt", earliest = true)} AS firstKnownPlayAt,
                    ${combinedTime(legacyLatest, "d.latestQualifiedAt", earliest = false)} AS latestKnownPlayAt,
                    d.latestDetailedEventAt
                FROM listening_track_identities i
                LEFT JOIN resolved_detailed d ON d.resolvedIdentityId = i.id
                $legacyJoin
                LEFT JOIN song_ratings ratings ON ratings.trackIdentityId = i.id
                $bindingJoin
                WHERE d.resolvedIdentityId IS NOT NULL
                   OR $legacyPresence
            )
        """.trimIndent()
        return SqlAndArgs(sql, filtered.args)
    }

    private fun filteredEvents(spec: ListeningStatsQuerySpec, alias: String? = null): SqlAndArgs {
        val prefix = alias?.let { "$it." }.orEmpty()
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()
        val placeholders = spec.sourceStorageValues.joinToString(",") { "?" }
        clauses += "${prefix}source IN ($placeholders)"
        clauses += "${prefix}publicationState != 'import_pending'"
        args.addAll(spec.sourceStorageValues)
        spec.startInclusive?.let {
            clauses += "${prefix}attributionAt >= ?"
            args += it
        }
        spec.endExclusive?.let {
            clauses += "${prefix}attributionAt < ?"
            args += it
        }
        return SqlAndArgs(clauses.joinToString(" AND "), args)
    }

    private fun combinedTime(legacy: String, detailed: String, earliest: Boolean): String {
        val comparison = if (earliest) "$legacy <= $detailed" else "$legacy >= $detailed"
        return "CASE WHEN $legacy IS NULL THEN $detailed WHEN $detailed IS NULL THEN $legacy WHEN $comparison THEN $legacy ELSE $detailed END"
    }

    private data class SqlAndArgs(val sql: String, val args: List<Any>) {
        val whereClause: String get() = sql
    }

    private const val MAX_TREND_QUERY_BINDINGS = 900
}
