package io.github.rsgarrido.sazanami.data

enum class FavoriteBatchOperation {
    ADD_MISSING,
    REMOVE_SELECTED
}

data class FavoriteBatchPlan(
    val operation: FavoriteBatchOperation,
    val songs: List<Song>
)

fun planFavoriteBatch(
    songs: List<Song>,
    favoriteMembershipKeys: Set<String>
): FavoriteBatchPlan {
    val distinctSongs = songs.distinctBy(Song::membershipKey)
    val allFavorite = distinctSongs.isNotEmpty() &&
        distinctSongs.all { it.membershipKey() in favoriteMembershipKeys }
    return if (allFavorite) {
        FavoriteBatchPlan(FavoriteBatchOperation.REMOVE_SELECTED, distinctSongs)
    } else {
        FavoriteBatchPlan(
            FavoriteBatchOperation.ADD_MISSING,
            distinctSongs.filterNot { it.membershipKey() in favoriteMembershipKeys }
        )
    }
}
