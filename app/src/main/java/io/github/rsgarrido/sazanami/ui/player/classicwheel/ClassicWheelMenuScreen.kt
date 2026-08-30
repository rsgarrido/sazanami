package io.github.rsgarrido.sazanami.ui.player.classicwheel

sealed class ClassicWheelMenuScreen {
    data object NowPlaying : ClassicWheelMenuScreen()
    data object MainMenu : ClassicWheelMenuScreen()
    data object Songs : ClassicWheelMenuScreen()
    data object Artists : ClassicWheelMenuScreen()
    data class ArtistSongs(val artistName: String) : ClassicWheelMenuScreen()
    data object Albums : ClassicWheelMenuScreen()
    data class AlbumSongs(
        val albumKey: String,
        val albumTitle: String
    ) : ClassicWheelMenuScreen()
}

/** Only Now Playing owns the artwork, metadata, and play/pause elements shared with mini player. */
internal fun ClassicWheelMenuScreen.ownsNowPlayingMorphContent(): Boolean =
    this == ClassicWheelMenuScreen.NowPlaying

/** Lyrics opening belongs to the Now Playing display, never scrollable internal menu pages. */
internal fun ClassicWheelMenuScreen.allowsLyricsSwipe(): Boolean =
    this == ClassicWheelMenuScreen.NowPlaying
