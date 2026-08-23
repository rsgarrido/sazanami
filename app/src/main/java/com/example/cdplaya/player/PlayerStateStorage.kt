package com.example.cdplaya.player

import android.content.Context
import android.content.SharedPreferences

class PlayerStateStorage internal constructor(
    private val preferences: SharedPreferences
) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    )

    fun getCurrentSongId(): Long? {
        val id = runCatching {
            preferences.getLong(KEY_CURRENT_SONG_ID, NO_SONG_ID)
        }.getOrDefault(NO_SONG_ID)
        return id.takeIf { it >= 0L }
    }

    fun saveState(
        currentSongId: Long?,
        currentPosition: Int,
        shuffleMode: PlaybackShuffleMode,
        repeatMode: RepeatMode,
        previousSongIds: List<Long>,
        nextSongIds: List<Long>,
        queueSongIds: List<Long>,
        playbackContextSongIds: List<Long>
    ) {
        preferences.edit()
            .putLong(KEY_CURRENT_SONG_ID, currentSongId ?: NO_SONG_ID)
            .putInt(KEY_CURRENT_POSITION, currentPosition.coerceAtLeast(0))
            .putBoolean(KEY_SHUFFLE_ENABLED, shuffleMode.isEnabled)
            .putString(KEY_SHUFFLE_MODE, shuffleMode.name)
            .putString(KEY_REPEAT_MODE, repeatMode.name)
            .putString(KEY_PREVIOUS_HISTORY, previousSongIds.joinToString(","))
            .putString(KEY_NEXT_HISTORY, nextSongIds.joinToString(","))
            .putString(KEY_QUEUE, queueSongIds.joinToString(","))
            .putString(KEY_PLAYBACK_CONTEXT, playbackContextSongIds.joinToString(","))
            .apply()
    }

    fun saveServicePlaybackState(
        currentSongId: Long,
        currentPosition: Int,
        repeatMode: RepeatMode
    ) {
        preferences.edit()
            .putLong(KEY_CURRENT_SONG_ID, currentSongId)
            .putInt(KEY_CURRENT_POSITION, currentPosition.coerceAtLeast(0))
            .putString(KEY_REPEAT_MODE, repeatMode.name)
            .apply()
    }

    fun getQueueSongIds(): List<Long> {
        return getSongIds(KEY_QUEUE)
    }

    fun getCurrentPosition(): Int {
        return runCatching {
            preferences.getInt(KEY_CURRENT_POSITION, 0)
        }.getOrDefault(0).coerceAtLeast(0)
    }

    fun getShuffleMode(): PlaybackShuffleMode {
        val savedMode = runCatching {
            preferences.getString(KEY_SHUFFLE_MODE, null)
        }.getOrNull()

        if (!savedMode.isNullOrBlank()) {
            return runCatching {
                PlaybackShuffleMode.valueOf(savedMode)
            }.getOrDefault(PlaybackShuffleMode.OFF)
        }

        // Backward compatibility for player state written before shuffle modes existed.
        return if (isLegacyShuffleEnabled()) {
            PlaybackShuffleMode.SONGS
        } else {
            PlaybackShuffleMode.OFF
        }
    }

    fun isShuffleEnabled(): Boolean = getShuffleMode().isEnabled

    private fun isLegacyShuffleEnabled(): Boolean {
        return runCatching {
            preferences.getBoolean(KEY_SHUFFLE_ENABLED, false)
        }.getOrDefault(false)
    }

    fun getRepeatMode(): RepeatMode {
        val savedMode = runCatching {
            preferences.getString(KEY_REPEAT_MODE, RepeatMode.OFF.name)
        }.getOrNull()

        return try {
            RepeatMode.valueOf(savedMode ?: RepeatMode.OFF.name)
        } catch (exception: IllegalArgumentException) {
            RepeatMode.OFF
        }
    }

    fun getPreviousSongIds(): List<Long> {
        return getSongIds(KEY_PREVIOUS_HISTORY)
    }

    fun getNextSongIds(): List<Long> {
        return getSongIds(KEY_NEXT_HISTORY)
    }

    private fun getSongIds(key: String): List<Long> {
        val savedIds = runCatching {
            preferences.getString(key, "")
        }.getOrNull().orEmpty()

        if (savedIds.isBlank()) {
            return emptyList()
        }

        return savedIds
            .split(",")
            .mapNotNull { id ->
                id.toLongOrNull()?.takeIf { parsedId -> parsedId >= 0L }
            }
    }

    fun getPlaybackContextSongIds(): List<Long> {
        return getSongIds(KEY_PLAYBACK_CONTEXT)
    }

    companion object {
        private const val PREFERENCES_NAME = "player_state"
        private const val KEY_CURRENT_SONG_ID = "current_song_id"
        private const val KEY_CURRENT_POSITION = "current_position"
        private const val KEY_SHUFFLE_ENABLED = "shuffle_enabled"
        private const val KEY_SHUFFLE_MODE = "shuffle_mode"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val KEY_PREVIOUS_HISTORY = "previous_history"
        private const val KEY_NEXT_HISTORY = "next_history"
        private const val KEY_QUEUE = "queue"
        private const val NO_SONG_ID = -1L
        private const val KEY_PLAYBACK_CONTEXT = "playback_context"
    }
}
