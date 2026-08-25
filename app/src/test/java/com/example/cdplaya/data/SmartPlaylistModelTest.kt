package com.example.cdplaya.data

import com.example.cdplaya.data.local.SmartPlaylistDependencies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlaylistModelTest {
    @Test
    fun openStringRulesRoundTripUnknownFutureFieldsAndOperators() {
        val rules = listOf(
            SmartPlaylistRule(
                field = "future_bpm",
                operator = "approximately",
                values = listOf("128"),
                parameters = mapOf("tolerance" to "3")
            )
        )

        assertEquals(rules, SmartPlaylistRuleJson.decode(SmartPlaylistRuleJson.encode(rules)))
    }

    @Test
    fun dependencyMaskIncludesRulesAndOrdering() {
        val draft = SmartPlaylistDraft(
            rules = listOf(
                SmartPlaylistRule(SmartPlaylistRuleField.RATING, SmartPlaylistOperator.AT_LEAST, listOf("4")),
                SmartPlaylistRule(SmartPlaylistRuleField.TITLE, SmartPlaylistOperator.CONTAINS, listOf("mix"))
            ),
            sortField = SmartPlaylistSortField.PLAY_COUNT
        )

        assertEquals(SmartPlaylistDependencies.ALL, SmartPlaylistDependencies.forDefinition(draft))
    }

    @Test
    fun onlyRelativeAndWindowRulesAreTimeSensitive() {
        assertFalse(SmartPlaylistDependencies.isTimeSensitive(SmartPlaylistDraft(
            rules = listOf(SmartPlaylistRule(
                SmartPlaylistRuleField.TOTAL_PLAY_COUNT,
                SmartPlaylistOperator.AT_LEAST,
                listOf("2")
            ))
        )))
        assertTrue(SmartPlaylistDependencies.isTimeSensitive(SmartPlaylistDraft(
            rules = listOf(SmartPlaylistRule(
                SmartPlaylistRuleField.RECENT_PLAY_COUNT,
                SmartPlaylistOperator.AT_LEAST,
                listOf("2"),
                parameters = mapOf("days" to "30")
            ))
        )))
    }
}

