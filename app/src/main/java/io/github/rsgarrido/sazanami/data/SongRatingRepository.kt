package io.github.rsgarrido.sazanami.data

import androidx.room.withTransaction
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.SongRatingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SongRating(
    val trackIdentityId: Long,
    val value: Int,
    val ratedAt: Long,
    val updatedAt: Long
)

data class SongRatingSnapshot(
    val byTrackIdentityId: Map<Long, SongRating> = emptyMap(),
    val byReferenceKey: Map<String, SongRating> = emptyMap()
)

interface SongRatingDataSource {
    fun observeRatingSnapshot(): Flow<SongRatingSnapshot>
    suspend fun getRatingForSong(song: Song): SongRating?
    suspend fun setRating(song: Song, rating: Int): SongRating
    suspend fun clearRating(song: Song): Boolean
}

/** Identity-owned ratings. Favorites and legacy aggregate play statistics are deliberately absent. */
class SongRatingRepository(
    private val database: AppDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val nativeTrackResolver: ListeningNativeTrackResolver =
        ListeningNativeTrackResolver(database, nowMillis)
) : SongRatingDataSource {
    override fun observeRatingSnapshot(): Flow<SongRatingSnapshot> =
        database.songRatingDao().observeAllWithBindings().map { rows ->
            val ratingsByIdentity = rows
                .distinctBy { row -> row.trackIdentityId }
                .associate { row ->
                    row.trackIdentityId to SongRating(
                        trackIdentityId = row.trackIdentityId,
                        value = row.rating,
                        ratedAt = row.ratedAt,
                        updatedAt = row.updatedAt
                    )
                }
            val ratingsByReference = rows
                .mapNotNull { row -> row.referenceKey?.let { key -> key to row.trackIdentityId } }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .mapNotNull { (referenceKey, identityIds) ->
                    identityIds.distinct().singleOrNull()?.let { identityId ->
                        ratingsByIdentity[identityId]?.let { rating -> referenceKey to rating }
                    }
                }
                .toMap()
            SongRatingSnapshot(
                byTrackIdentityId = ratingsByIdentity,
                byReferenceKey = ratingsByReference
            )
        }

    override suspend fun getRatingForSong(song: Song): SongRating? {
        val binding = database.localTrackBindingDao().getByReferenceKey(song.membershipKey())
            ?: return null
        return database.songRatingDao().getByTrackIdentityId(binding.trackIdentityId)?.toDomain()
    }

    override suspend fun setRating(song: Song, rating: Int): SongRating {
        validateRating(rating)
        return database.withTransaction {
            val resolved = nativeTrackResolver.resolveOrCreate(
                referenceKey = song.membershipKey(),
                reference = song.toSongReference()
            )
            setRatingWithinTransaction(resolved.trackIdentityId, rating)
        }
    }

    override suspend fun clearRating(song: Song): Boolean = database.withTransaction {
        val binding = database.localTrackBindingDao().getByReferenceKey(song.membershipKey())
            ?: return@withTransaction false
        database.songRatingDao().deleteByTrackIdentityId(binding.trackIdentityId) > 0
    }

    private suspend fun setRatingWithinTransaction(trackIdentityId: Long, rating: Int): SongRating {
        val current = database.songRatingDao().getByTrackIdentityId(trackIdentityId)
        if (current?.rating == rating) return current.toDomain()
        val now = nowMillis()
        val entity = SongRatingEntity(
            trackIdentityId = trackIdentityId,
            rating = rating,
            ratedAt = current?.ratedAt ?: now,
            updatedAt = now
        )
        database.songRatingDao().upsert(entity)
        return entity.toDomain()
    }

    private fun validateRating(rating: Int) {
        require(rating in 1..5) { "Song rating must be between 1 and 5" }
    }
}

private fun SongRatingEntity.toDomain() = SongRating(
    trackIdentityId = trackIdentityId,
    value = rating,
    ratedAt = ratedAt,
    updatedAt = updatedAt
)
