package com.example.cdplaya.ui.playlist

import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.PlaylistMembershipBehavior
import com.example.cdplaya.data.PlaylistType
import com.example.cdplaya.data.SmartPlaylistMatchMode
import com.example.cdplaya.data.SmartPlaylistOperator
import com.example.cdplaya.data.SmartPlaylistRuleField
import com.example.cdplaya.data.SmartPlaylistSortDirection
import com.example.cdplaya.data.SmartPlaylistSortField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlaylistEditorModelTest {
    @Test
    fun validEditorModelMapsToPersistedDefinition() {
        val model = SmartPlaylistEditorModel(
            name = "Warning songs",
            matchMode = SmartPlaylistMatchMode.ANY,
            rules = listOf(
                SmartPlaylistEditorRule(
                    id = 1,
                    field = SmartPlaylistRuleField.ARTIST,
                    operator = SmartPlaylistOperator.CONTAINS,
                    value = "The Warning"
                ),
                SmartPlaylistEditorRule(
                    id = 2,
                    field = SmartPlaylistRuleField.DURATION,
                    operator = SmartPlaylistOperator.AT_LEAST,
                    value = "3.5"
                )
            ),
            sortField = SmartPlaylistSortField.ARTIST,
            sortDirection = SmartPlaylistSortDirection.DESCENDING,
            resultLimit = "25"
        )

        assertTrue(model.validation(emptyList()).isValid)
        val draft = model.toDraft()
        assertEquals(SmartPlaylistMatchMode.ANY, draft.matchMode)
        assertEquals("The Warning", draft.rules[0].values.single())
        assertEquals("210000", draft.rules[1].values.single())
        assertEquals(25, draft.resultLimit)
    }

    @Test
    fun incompleteAndUnsupportedRulesRemainInvalid() {
        val incomplete = SmartPlaylistEditorModel(
            name = "Incomplete",
            rules = listOf(SmartPlaylistEditorRule(1, value = ""))
        )
        val unsupported = SmartPlaylistEditorModel(
            name = "Future",
            rules = listOf(
                SmartPlaylistEditorRule(1, field = "future_bpm", operator = "approximately", value = "120")
            )
        )

        assertFalse(incomplete.validation(emptyList()).isValid)
        assertFalse(unsupported.validation(emptyList()).isValid)
    }

    @Test
    fun manualOnlyActionsAreGatedFromEverySmartMembershipType() {
        assertTrue(allowsManualPlaylistActions(playlist(PlaylistType.MANUAL, PlaylistMembershipBehavior.MANUAL)))
        assertFalse(allowsManualPlaylistActions(playlist(PlaylistType.SMART, PlaylistMembershipBehavior.USER_SMART_LIVE)))
        assertFalse(allowsManualPlaylistActions(playlist(PlaylistType.SMART, PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT)))
    }

    private fun playlist(type: PlaylistType, behavior: PlaylistMembershipBehavior) = Playlist(
        playlistId = 1,
        name = "Test",
        songCount = 0,
        type = type,
        membershipBehavior = behavior
    )
}
