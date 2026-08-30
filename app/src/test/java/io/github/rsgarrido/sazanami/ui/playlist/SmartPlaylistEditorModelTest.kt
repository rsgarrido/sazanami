package io.github.rsgarrido.sazanami.ui.playlist

import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.PlaylistMembershipBehavior
import io.github.rsgarrido.sazanami.data.PlaylistType
import io.github.rsgarrido.sazanami.data.SmartPlaylistMatchMode
import io.github.rsgarrido.sazanami.data.SmartPlaylistOperator
import io.github.rsgarrido.sazanami.data.SmartPlaylistRuleField
import io.github.rsgarrido.sazanami.data.SmartPlaylistSortDirection
import io.github.rsgarrido.sazanami.data.SmartPlaylistSortField
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
                    operator = SmartPlaylistOperator.LONGER_THAN,
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
    fun fieldMatrixUsesNaturalTypeSpecificConditions() {
        val duration = smartRuleFieldOptions.single { it.storage == SmartPlaylistRuleField.DURATION }
        val history = smartRuleFieldOptions.single { it.storage == LISTENING_HISTORY_EDITOR_FIELD }
        val year = smartRuleFieldOptions.single { it.storage == SmartPlaylistRuleField.YEAR }

        assertEquals(
            listOf(
                SmartPlaylistOperator.SHORTER_THAN,
                SmartPlaylistOperator.LONGER_THAN,
                SmartPlaylistOperator.BETWEEN,
                SmartPlaylistOperator.ABOUT
            ),
            duration.operators.map { it.storage }
        )
        assertEquals(
            listOf(
                SmartPlaylistOperator.NEVER,
                SmartPlaylistOperator.WITHIN_LAST_DAYS,
                SmartPlaylistOperator.MORE_THAN_DAYS_AGO
            ),
            history.operators.map { it.storage }
        )
        assertEquals(
            listOf(
                SmartPlaylistOperator.EQUALS,
                SmartPlaylistOperator.NOT_EQUALS,
                SmartPlaylistOperator.BEFORE,
                SmartPlaylistOperator.AFTER,
                SmartPlaylistOperator.BETWEEN
            ),
            year.operators.map { it.storage }
        )
    }

    @Test
    fun metadataFieldsExposeOnlyCompatibleOperators() {
        val genre = smartRuleFieldOptions.single { it.storage == SmartPlaylistRuleField.GENRE }
        val bpm = smartRuleFieldOptions.single { it.storage == SmartPlaylistRuleField.BPM }

        assertEquals(
            listOf(SmartPlaylistOperator.IS, SmartPlaylistOperator.IS_NOT, SmartPlaylistOperator.CONTAINS),
            genre.operators.map { it.storage }
        )
        assertEquals(
            listOf(
                SmartPlaylistOperator.EQUALS,
                SmartPlaylistOperator.NOT_EQUALS,
                SmartPlaylistOperator.GREATER_THAN,
                SmartPlaylistOperator.LESS_THAN,
                SmartPlaylistOperator.BETWEEN
            ),
            bpm.operators.map { it.storage }
        )
    }

    @Test
    fun changingFieldClearsIncompatibleOperatorAndValues() {
        val changed = changeSmartRuleField(
            SmartPlaylistEditorRule(
                id = 1,
                field = SmartPlaylistRuleField.GENRE,
                operator = SmartPlaylistOperator.CONTAINS,
                value = "Rock",
                secondValue = "Punk"
            ),
            SmartPlaylistRuleField.BPM
        )

        assertEquals(SmartPlaylistRuleField.BPM, changed.field)
        assertEquals(SmartPlaylistOperator.EQUALS, changed.operator)
        assertEquals("", changed.value)
        assertEquals("", changed.secondValue)
    }

    @Test
    fun yearAndBpmValidationRejectsMissingMalformedAndReversedRanges() {
        fun valid(field: String, value: String, second: String = "", operator: String = SmartPlaylistOperator.EQUALS) =
            SmartPlaylistEditorModel(
                name = "Numbers",
                rules = listOf(SmartPlaylistEditorRule(1, field, operator, value, second))
            ).validation(emptyList()).isValid

        assertFalse(valid(SmartPlaylistRuleField.YEAR, "99"))
        assertFalse(valid(SmartPlaylistRuleField.BPM, "fast"))
        assertFalse(valid(SmartPlaylistRuleField.BPM, "170", "120", SmartPlaylistOperator.BETWEEN))
        assertTrue(valid(SmartPlaylistRuleField.YEAR, "2004"))
        assertTrue(valid(SmartPlaylistRuleField.BPM, "120", "170", SmartPlaylistOperator.BETWEEN))
    }

    @Test
    fun newMetadataRulesPreserveMatchAndIndependentResultOrderingRoundTrip() {
        val model = SmartPlaylistEditorModel(
            name = "Metadata",
            matchMode = SmartPlaylistMatchMode.ANY,
            rules = listOf(
                SmartPlaylistEditorRule(1, SmartPlaylistRuleField.COMPOSER, SmartPlaylistOperator.CONTAINS, "Eno"),
                SmartPlaylistEditorRule(2, SmartPlaylistRuleField.BPM, SmartPlaylistOperator.BETWEEN, "110", "130")
            ),
            sortField = SmartPlaylistSortField.YEAR,
            sortDirection = SmartPlaylistSortDirection.DESCENDING
        )
        val reopened = SmartPlaylistEditorModel.fromDraft(model.name, model.toDraft())

        assertEquals(SmartPlaylistMatchMode.ANY, reopened.matchMode)
        assertEquals(SmartPlaylistSortField.YEAR, reopened.sortField)
        assertEquals(SmartPlaylistSortDirection.DESCENDING, reopened.sortDirection)
        assertEquals(model.rules.map { it.field to it.operator }, reopened.rules.map { it.field to it.operator })
    }

    @Test
    fun durationMinutesAndListeningHistoryMapToExistingStorageRules() {
        val about = SmartPlaylistEditorModel(
            name = "About four",
            rules = listOf(SmartPlaylistEditorRule(
                id = 1,
                field = SmartPlaylistRuleField.DURATION,
                operator = SmartPlaylistOperator.ABOUT,
                value = "4"
            ))
        ).toDraft().rules.single()
        val never = SmartPlaylistEditorModel(
            name = "Never",
            rules = listOf(SmartPlaylistEditorRule(
                id = 1,
                field = LISTENING_HISTORY_EDITOR_FIELD,
                operator = SmartPlaylistOperator.NEVER
            ))
        ).toDraft().rules.single()

        assertEquals(listOf("240000"), about.values)
        assertEquals(SmartPlaylistRuleField.NEVER_PLAYED, never.field)
        assertEquals(SmartPlaylistOperator.IS, never.operator)
    }

    @Test
    fun legacyGenericDurationConditionReopensAsNaturalDurationUx() {
        val model = SmartPlaylistEditorModel.fromDraft(
            "Legacy duration",
            io.github.rsgarrido.sazanami.data.SmartPlaylistDraft(rules = listOf(
                io.github.rsgarrido.sazanami.data.SmartPlaylistRule(
                    SmartPlaylistRuleField.DURATION,
                    SmartPlaylistOperator.EQUALS,
                    listOf("240000")
                )
            ))
        )

        assertEquals(SmartPlaylistOperator.ABOUT, model.rules.single().operator)
        assertEquals("4", model.rules.single().value)
        assertTrue(model.validation(emptyList()).isValid)
    }

    @Test
    fun matchModeChoiceOnlyAppearsWhenConditionsCanDiffer() {
        assertFalse(SmartPlaylistEditorModel().showsMatchModeChoice)
        assertTrue(SmartPlaylistEditorModel(
            rules = listOf(SmartPlaylistEditorRule(1), SmartPlaylistEditorRule(2))
        ).showsMatchModeChoice)
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

    @Test
    fun smartPresentationDoesNotExposeSuggestedOrLiveEngineTerms() {
        val userSmart = playlist(PlaylistType.SMART, PlaylistMembershipBehavior.USER_SMART_LIVE)
        val generatedLive = playlist(
            PlaylistType.SMART,
            PlaylistMembershipBehavior.GENERATED_SMART_LIVE
        )

        assertEquals("Smart", playlistCollectionKindText(userSmart))
        assertEquals("Smart Playlist • Updates automatically", playlistKindText(userSmart))
        assertEquals("Smart Playlist • Updates automatically", playlistKindText(generatedLive))
        assertFalse(playlistKindText(generatedLive).contains("Suggested"))
        assertFalse(playlistKindText(generatedLive).contains("Live"))
    }

    private fun playlist(type: PlaylistType, behavior: PlaylistMembershipBehavior) = Playlist(
        playlistId = 1,
        name = "Test",
        songCount = 0,
        type = type,
        membershipBehavior = behavior
    )
}
