package io.github.rsgarrido.sazanami.player.replaygain

import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.TagEditorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ReplayGainRepository(
    private val tagEditorRepository: TagEditorRepository = TagEditorRepository()
) {
    private val replayGainCache = mutableMapOf<ReplayGainCacheKey, ReplayGainInfo>()

    suspend fun getReplayGainInfo(song: Song): ReplayGainInfo {
        val cacheKey = song.toReplayGainCacheKey()

        synchronized(replayGainCache) {
            replayGainCache[cacheKey]?.let { cachedInfo ->
                return cachedInfo
            }
        }

        val loadedInfo = withContext(Dispatchers.IO) {
            tagEditorRepository.readReplayGainTags(song)
        }

        synchronized(replayGainCache) {
            replayGainCache[cacheKey] = loadedInfo
        }

        return loadedInfo
    }

    fun clearCache() {
        synchronized(replayGainCache) {
            replayGainCache.clear()
        }
    }

    fun clearCacheForSong(song: Song) {
        val filePath = song.filePath

        synchronized(replayGainCache) {
            val matchingKeys = replayGainCache.keys.filter { cacheKey ->
                cacheKey.songId == song.id ||
                        cacheKey.filePath == filePath
            }

            matchingKeys.forEach { cacheKey ->
                replayGainCache.remove(cacheKey)
            }
        }
    }

    private fun Song.toReplayGainCacheKey(): ReplayGainCacheKey {
        val file = File(filePath)

        return ReplayGainCacheKey(
            songId = id,
            filePath = filePath,
            lastModified = if (file.exists()) {
                file.lastModified()
            } else {
                0L
            }
        )
    }

    private data class ReplayGainCacheKey(
        val songId: Long,
        val filePath: String,
        val lastModified: Long
    )
}