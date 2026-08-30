package io.github.rsgarrido.sazanami.data.backup

object SongRatingBackupValidator {
    fun validate(
        ratings: BackupSongRatings,
        history: BackupListeningHistoryV2
    ): BackupSongRatings {
        require(ratings.formatVersion == BackupSongRatings.CURRENT_FORMAT_VERSION) {
            "Unsupported song-rating backup format version ${ratings.formatVersion}."
        }
        val identityIds = history.identities.mapTo(HashSet()) { it.backupIdentityId }
        require(
            ratings.entries.map { it.trackIdentityBackupId }.distinct().size ==
                ratings.entries.size
        ) { "Song ratings must reference unique identities." }
        ratings.entries.forEach { entry ->
            require(entry.trackIdentityBackupId in identityIds) {
                "Song rating references a missing listening-track identity."
            }
            require(entry.rating in 1..5) { "Song rating must be between 1 and 5." }
            require(entry.ratedAt >= 0L) { "Song rating timestamp cannot be negative." }
            require(entry.updatedAt >= entry.ratedAt) {
                "Song rating update cannot precede its creation."
            }
        }
        return ratings
    }
}
