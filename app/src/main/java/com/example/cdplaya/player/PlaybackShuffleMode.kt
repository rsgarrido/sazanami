package com.example.cdplaya.player

enum class PlaybackShuffleMode {
    OFF,
    SONGS,
    ALBUMS,
    ALBUMS_AND_SONGS;

    val isEnabled: Boolean
        get() = this != OFF

    val usesDynamicSongShuffle: Boolean
        get() = this == SONGS
}
