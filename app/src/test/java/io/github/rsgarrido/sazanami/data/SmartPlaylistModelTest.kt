package io.github.rsgarrido.sazanami.data

import io.github.rsgarrido.sazanami.data.local.SmartPlaylistDependencies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlaylistModelTest {
    @Test
    fun legacyRuleJsonStillDeserializesWithOriginalIdentifiers() {
        val decoded = SmartPlaylistRuleJson.decode(
            """[{"field":"artist","operator":"contains","values":["Miles"],"parameters":{}}]"""
        )

        assertEquals(
            SmartPlaylistRule(SmartPlaylistRuleField.ARTIST, SmartPlaylistOperator.CONTAINS, listOf("Miles")),
            decoded.single()
        )
    }

    @Test
    fun legacyAndMetadataRulesRoundTripTogether() {
        val rules = listOf(
            SmartPlaylistRule(SmartPlaylistRuleField.TITLE, SmartPlaylistOperator.CONTAINS, listOf("live")),
            SmartPlaylistRule(SmartPlaylistRuleField.GENRE, SmartPlaylistOperator.IS, listOf("Rock")),
            SmartPlaylistRule(SmartPlaylistRuleField.BPM, SmartPlaylistOperator.GREATER_THAN, listOf("150"))
        )

        assertEquals(rules, SmartPlaylistRuleJson.decode(SmartPlaylistRuleJson.encode(rules)))
    }

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

