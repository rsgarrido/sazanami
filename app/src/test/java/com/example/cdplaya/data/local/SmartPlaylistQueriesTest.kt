package com.example.cdplaya.data.local

import com.example.cdplaya.data.SmartPlaylistDraft
import com.example.cdplaya.data.SmartPlaylistMatchMode
import com.example.cdplaya.data.SmartPlaylistOperator
import com.example.cdplaya.data.SmartPlaylistRule
import com.example.cdplaya.data.SmartPlaylistRuleField
import com.example.cdplaya.data.SmartPlaylistSortDirection
import com.example.cdplaya.data.SmartPlaylistSortField
import org.junit.Assert.assertTrue
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
                sortField = SmartPlaylistSortField.PLAY_COUNT,
                sortDirection = SmartPlaylistSortDirection.DESCENDING,
                resultLimit = 25
            ),
            10_000_000_000L
        )

        assertTrue(query.sql.contains("recent_0 AS"))
        assertTrue(query.sql.contains("GROUP BY COALESCE(r.targetIdentityId, e.trackIdentityId)"))
        assertTrue(query.sql.contains("library_rows.volumeName ASC"))
        assertTrue(query.sql.contains("library_rows.mediaStoreId ASC"))
        assertTrue(query.sql.contains("LIMIT ?"))
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
}
