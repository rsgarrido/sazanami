package io.github.rsgarrido.sazanami.data.local

import io.github.rsgarrido.sazanami.data.SmartPlaylistDraft
import io.github.rsgarrido.sazanami.data.FolderSelection
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.SmartPlaylistMatchMode
import io.github.rsgarrido.sazanami.data.SmartPlaylistOperator
import io.github.rsgarrido.sazanami.data.SmartPlaylistRule
import io.github.rsgarrido.sazanami.data.SmartPlaylistRuleField
import io.github.rsgarrido.sazanami.data.SmartPlaylistSortDirection
import io.github.rsgarrido.sazanami.data.SmartPlaylistSortField
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SmartPlaylistQueriesTest {
    @Test
    fun allRulesCompileToAndAndAnyRulesCompileToOr() {
        val rules = listOf(
            SmartPlaylistRule(SmartPlaylistRuleField.RATING, SmartPlaylistOperator.UNRATED),
            SmartPlaylistRule(SmartPlaylistRuleField.TOTAL_PLAY_COUNT, SmartPlaylistOperator.AT_MOST, listOf("0"))
        )

        val allSql = SmartPlaylistQueries.resolve(SmartPlaylistDraft(rules = rules), 1_000L).sql
        val anySql = SmartPlaylistQueries.resolve(
            SmartPlaylistDraft(matchMode = SmartPlaylistMatchMode.ANY, rules = rules),
            1_000L
        ).sql

        assertTrue(allSql.contains("library_rows.rating IS NULL AND"))
        assertTrue(anySql.contains("library_rows.rating IS NULL OR"))
    }

    @Test
    fun recentWindowAggregatesInSqlAndOrderingHasStableIdentityTies() {
        val query = SmartPlaylistQueries.resolve(
            SmartPlaylistDraft(
                rules = listOf(SmartPlaylistRule(
                    SmartPlaylistRuleField.RECENT_PLAY_COUNT,
                    SmartPlaylistOperator.AT_LEAST,
                    listOf("1"),
                    parameters = mapOf("days" to "30")
                )),
                sortField = SmartPlaylistSortField.RECENT_PLAY_COUNT,
                sortDirection = SmartPlaylistSortDirection.DESCENDING,
                resultLimit = 25
            ),
            10_000_000_000L
        )

        assertTrue(query.sql.contains("recent_0 AS"))
        assertTrue(query.sql.contains("GROUP BY COALESCE(r.targetIdentityId, e.trackIdentityId)"))
        assertTrue(query.sql.contains("library_rows.recentPlayCount0 DESC"))
        assertTrue(query.sql.contains("library_rows.volumeName ASC"))
        assertTrue(query.sql.contains("library_rows.mediaStoreId ASC"))
        assertTrue(query.sql.contains("LIMIT ?"))
    }

    @Test
    fun forgottenFavoritesRankingUsesHistoryThenOptionalRatingWithStableTies() {
        val query = SmartPlaylistQueries.resolve(
            io.github.rsgarrido.sazanami.data.SmartPlaylistTemplate.FORGOTTEN_FAVORITES.draft,
            10_000_000_000L
        )

        assertTrue(query.sql.contains("library_rows.totalPlayCount DESC"))
        assertTrue(query.sql.contains("library_rows.rating DESC"))
        assertTrue(query.sql.contains("library_rows.lastPlayedAt ASC"))
        assertTrue(query.sql.contains("library_rows.title COLLATE NOCASE ASC"))
    }

    @Test
    fun textAndRelativeTimeOperatorsCompileToBoundPredicates() {
        val query = SmartPlaylistQueries.resolve(
            SmartPlaylistDraft(rules = listOf(
                SmartPlaylistRule(
                    SmartPlaylistRuleField.TITLE,
                    SmartPlaylistOperator.DOES_NOT_CONTAIN,
                    listOf("live_100%")
                ),
                SmartPlaylistRule(
                    SmartPlaylistRuleField.LAST_PLAYED,
                    SmartPlaylistOperator.MORE_THAN_DAYS_AGO,
                    listOf("90")
                )
            )),
            10_000_000_000L
        )

        assertTrue(query.sql.contains("NOT LIKE '%' || ? || '%' ESCAPE"))
        assertTrue(query.sql.contains("library_rows.lastPlayedAt IS NOT NULL"))
        assertTrue(query.sql.contains("library_rows.lastPlayedAt < ?"))
    }

    @Test
    fun candidateCteAppliesAuthoritativeFolderSelection() {
        val query = SmartPlaylistQueries.resolve(
            SmartPlaylistDraft(),
            1_000L,
            FolderSelection(
                mode = FolderSelectionMode.CUSTOM,
                customFolders = setOf("/music"),
                excludedFolders = setOf("/music/private")
            )
        )

        assertTrue(query.sql.contains("WHERE CASE"))
        assertTrue(query.sql.contains("songs.folderPath = ? COLLATE NOCASE"))
        assertTrue(query.sql.contains("END = 1"))
    }

    @Test
    fun aboutDurationUsesNearestMinuteHalfOpenBucket() {
        val query = SmartPlaylistQueries.resolve(
            SmartPlaylistDraft(rules = listOf(SmartPlaylistRule(
                SmartPlaylistRuleField.DURATION,
                SmartPlaylistOperator.ABOUT,
                listOf("240000")
            ))),
            1_000L
        )

        assertTrue(query.sql.contains("library_rows.duration >= ?"))
        assertTrue(query.sql.contains("library_rows.duration < ?"))
    }

    @Test
    fun metadataRulesUseNormalizedGenreTextAndNullSafeNumericPredicates() {
        val query = SmartPlaylistQueries.resolve(
            SmartPlaylistDraft(rules = listOf(
                SmartPlaylistRule(SmartPlaylistRuleField.GENRE, SmartPlaylistOperator.IS, listOf(" Rock ")),
                SmartPlaylistRule(SmartPlaylistRuleField.COMPOSER, SmartPlaylistOperator.CONTAINS, listOf("Jones")),
                SmartPlaylistRule(SmartPlaylistRuleField.PUBLISHER, SmartPlaylistOperator.IS, listOf("Blue Note")),
                SmartPlaylistRule(SmartPlaylistRuleField.YEAR, SmartPlaylistOperator.NOT_EQUALS, listOf("2004")),
                SmartPlaylistRule(SmartPlaylistRuleField.BPM, SmartPlaylistOperator.GREATER_THAN, listOf("150"))
            )),
            1_000L
        )

        assertTrue(query.sql.contains("LOWER(library_rows.normalizedGenresJson) LIKE"))
        assertTrue(query.sql.contains("LOWER(library_rows.composerText) LIKE"))
        assertTrue(query.sql.contains("library_rows.publisher = ? COLLATE NOCASE"))
        assertTrue(query.sql.contains("library_rows.year IS NOT NULL AND library_rows.year != ?"))
        assertTrue(query.sql.contains("library_rows.bpm IS NOT NULL AND library_rows.bpm > ?"))
    }

    @Test
    fun unknownGenreUsesEmptyAuthoritativeNormalizedGenreList() {
        val query = SmartPlaylistQueries.resolve(
            SmartPlaylistDraft(rules = listOf(SmartPlaylistRule(
                SmartPlaylistRuleField.GENRE,
                SmartPlaylistOperator.IS,
                listOf("Unknown Genre")
            ))),
            1_000L
        )

        assertTrue(query.sql.contains("library_rows.normalizedGenresJson = '[]'"))
    }

    @Test
    fun malformedBpmRuleFailsClosedBeforeEvaluation() {
        assertThrows(IllegalArgumentException::class.java) {
            SmartPlaylistQueries.resolve(
                SmartPlaylistDraft(rules = listOf(SmartPlaylistRule(
                    SmartPlaylistRuleField.BPM,
                    SmartPlaylistOperator.GREATER_THAN,
                    listOf("fast")
                ))),
                1_000L
            )
        }
    }
}
