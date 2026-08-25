package com.example.cdplaya.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlaylistTemplatesTest {
    @Test
    fun heavyRotationUsesThirtyDayRecentPlaysAndStableFiveDaySnapshot() {
        val template = SmartPlaylistTemplate.HEAVY_ROTATION

        assertEquals(GeneratedPlaylistMembershipMode.SNAPSHOT, template.membershipMode)
        assertEquals(GeneratedPlaylistRefreshPolicy.ON_OPEN_IF_STALE, template.refreshPolicy)
        assertEquals(SMART_PLAYLIST_GENERATED_REFRESH_INTERVAL_MILLIS, template.refreshIntervalMillis)
        assertEquals(SmartPlaylistRuleField.RECENT_PLAY_COUNT, template.draft.rules.single().field)
        assertEquals("30", template.draft.rules.single().parameters["days"])
        assertEquals(SmartPlaylistSortField.RECENT_PLAY_COUNT, template.draft.sortField)
        assertEquals(SmartPlaylistSortDirection.DESCENDING, template.draft.sortDirection)
        assertEquals(50, template.draft.resultLimit)
    }

    @Test
    fun forgottenFavoritesIsDeterministicAndDoesNotRequireRating() {
        val draft = SmartPlaylistTemplate.FORGOTTEN_FAVORITES.draft

        assertEquals(SmartPlaylistMatchMode.ALL, draft.matchMode)
        assertEquals(
            listOf(SmartPlaylistRuleField.TOTAL_PLAY_COUNT, SmartPlaylistRuleField.LAST_PLAYED),
            draft.rules.map(SmartPlaylistRule::field)
        )
        assertFalse(draft.rules.any { it.field == SmartPlaylistRuleField.RATING })
        assertEquals(SmartPlaylistSortField.FORGOTTEN_FAVORITES_RANK, draft.sortField)
    }

    @Test
    fun topRatedAndNeverPlayedAreLiveDefinitions() {
        val topRated = SmartPlaylistTemplate.TOP_RATED
        val neverPlayed = SmartPlaylistTemplate.NEVER_PLAYED

        assertEquals(GeneratedPlaylistMembershipMode.LIVE_DERIVED, topRated.membershipMode)
        assertEquals(SmartPlaylistRuleField.RATING, topRated.draft.rules.single().field)
        assertEquals(listOf("4"), topRated.draft.rules.single().values)
        assertEquals(GeneratedPlaylistMembershipMode.LIVE_DERIVED, neverPlayed.membershipMode)
        assertEquals(SmartPlaylistRuleField.NEVER_PLAYED, neverPlayed.draft.rules.single().field)
        assertNull(topRated.refreshIntervalMillis)
        assertNull(neverPlayed.refreshIntervalMillis)
    }

    @Test
    fun snapshotRefreshEligibilityPreservesMembershipUntilIntervalExpires() {
        val now = 1_000_000_000L
        val state = GeneratedPlaylistState(
            playlistId = 1L,
            templateKey = SmartPlaylistTemplate.HEAVY_ROTATION.key,
            membershipMode = GeneratedPlaylistMembershipMode.SNAPSHOT,
            refreshPolicy = GeneratedPlaylistRefreshPolicy.ON_OPEN_IF_STALE,
            refreshIntervalMillis = SMART_PLAYLIST_GENERATED_REFRESH_INTERVAL_MILLIS,
            lastRefreshedAt = now,
            snapshotVersion = 1
        )

        assertFalse(generatedSnapshotRefreshEligible(state, now + 4L * 24L * 60L * 60L * 1_000L))
        assertTrue(generatedSnapshotRefreshEligible(state, now + SMART_PLAYLIST_GENERATED_REFRESH_INTERVAL_MILLIS))
        assertTrue(generatedSnapshotRefreshEligible(state.copy(lastRefreshedAt = null), now))
    }
}
