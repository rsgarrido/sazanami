package com.example.cdplaya.player

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PlayerStateStorageTest {
    @Test
    fun fullStateRoundTripPreservesQueueHistoryAndContextOrder() {
        val harness = SharedPreferencesHarness()
        val storage = PlayerStateStorage(harness.preferences)

        storage.saveState(7L, 12_345, PlaybackShuffleMode.ALBUMS, RepeatMode.ALL,
            listOf(1L, 2L), listOf(5L, 4L), listOf(8L, 8L, 9L), listOf(7L, 3L, 2L))

        assertEquals(7L, storage.getCurrentSongId())
        assertEquals(12_345, storage.getCurrentPosition())
        assertTrue(storage.isShuffleEnabled())
        assertEquals(PlaybackShuffleMode.ALBUMS, storage.getShuffleMode())
        assertEquals(RepeatMode.ALL, storage.getRepeatMode())
        assertEquals(listOf(1L, 2L), storage.getPreviousSongIds())
        assertEquals(listOf(5L, 4L), storage.getNextSongIds())
        assertEquals(listOf(8L, 8L, 9L), storage.getQueueSongIds())
        assertEquals(listOf(7L, 3L, 2L), storage.getPlaybackContextSongIds())
    }

    @Test
    fun emptyStorageReturnsSafeDefaults() {
        val storage = PlayerStateStorage(SharedPreferencesHarness().preferences)

        assertNull(storage.getCurrentSongId())
        assertEquals(0, storage.getCurrentPosition())
        assertFalse(storage.isShuffleEnabled())
        assertEquals(PlaybackShuffleMode.OFF, storage.getShuffleMode())
        assertEquals(RepeatMode.OFF, storage.getRepeatMode())
        assertEquals(emptyList<Long>(), storage.getQueueSongIds())
    }

    @Test
    fun malformedIdsInvalidRepeatAndWrongTypesFallBackSafely() {
        val harness = SharedPreferencesHarness().apply {
            values["current_song_id"] = "wrong"
            values["current_position"] = "wrong"
            values["shuffle_enabled"] = 1
            values["shuffle_mode"] = "INVALID"
            values["repeat_mode"] = "INVALID"
            values["queue"] = "1,nope,-2,3,,4x"
            values["previous_history"] = 99
        }
        val storage = PlayerStateStorage(harness.preferences)

        assertNull(storage.getCurrentSongId())
        assertEquals(0, storage.getCurrentPosition())
        assertFalse(storage.isShuffleEnabled())
        assertEquals(PlaybackShuffleMode.OFF, storage.getShuffleMode())
        assertEquals(RepeatMode.OFF, storage.getRepeatMode())
        assertEquals(listOf(1L, 3L), storage.getQueueSongIds())
        assertEquals(emptyList<Long>(), storage.getPreviousSongIds())
    }

    @Test
    fun legacyShuffleBooleanMigratesToSongShuffleMode() {
        val harness = SharedPreferencesHarness().apply {
            values["shuffle_enabled"] = true
        }
        val storage = PlayerStateStorage(harness.preferences)

        assertEquals(PlaybackShuffleMode.SONGS, storage.getShuffleMode())
        assertTrue(storage.isShuffleEnabled())
    }

    private class SharedPreferencesHarness {
        val values = mutableMapOf<String, Any>()
        val preferences: SharedPreferences = mock(SharedPreferences::class.java)
        private val editor: SharedPreferences.Editor = mock(SharedPreferences.Editor::class.java)

        init {
            doAnswer { call -> typedValue(call.getArgument(0), call.getArgument<Long>(1)) }
                .`when`(preferences).getLong(anyString(), anyLong())
            doAnswer { call -> typedValue(call.getArgument(0), call.getArgument<Int>(1)) }
                .`when`(preferences).getInt(anyString(), anyInt())
            doAnswer { call -> typedValue(call.getArgument(0), call.getArgument<Boolean>(1)) }
                .`when`(preferences).getBoolean(anyString(), anyBoolean())
            doAnswer { call ->
                val key = call.getArgument<String>(0)
                val default = call.getArgument<String?>(1)
                when (val value = values[key]) {
                    null -> default
                    is String -> value
                    else -> throw ClassCastException()
                }
            }.`when`(preferences).getString(anyString(), nullable(String::class.java))
            `when`(preferences.edit()).thenReturn(editor)
            doAnswer { call -> values[call.getArgument(0)] = call.getArgument<Long>(1); editor }
                .`when`(editor).putLong(anyString(), anyLong())
            doAnswer { call -> values[call.getArgument(0)] = call.getArgument<Int>(1); editor }
                .`when`(editor).putInt(anyString(), anyInt())
            doAnswer { call -> values[call.getArgument(0)] = call.getArgument<Boolean>(1); editor }
                .`when`(editor).putBoolean(anyString(), anyBoolean())
            doAnswer { call -> values[call.getArgument(0)] = call.getArgument<String>(1); editor }
                .`when`(editor).putString(anyString(), nullable(String::class.java))
        }

        private inline fun <reified T : Any> typedValue(key: String, default: T): T {
            return when (val value = values[key]) {
                null -> default
                is T -> value
                else -> throw ClassCastException()
            }
        }
    }
}
