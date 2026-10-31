package io.github.rsgarrido.sazanami.ui.player

import androidx.annotation.StringRes
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.data.artistIdentity
import io.github.rsgarrido.sazanami.data.normalizeArtistName
import io.github.rsgarrido.sazanami.ui.library.buildLibraryAlbumGroups
import io.github.rsgarrido.sazanami.ui.library.buildLibraryArtistGroups
import io.github.rsgarrido.sazanami.ui.library.findLibraryAlbumGroupForSong

enum class NowPlayingAction { FAVORITE, GO_TO_ARTIST, GO_TO_ALBUM, ADD_TO_PLAYLIST, RATE_SONG, TRACK_INFORMATION, LYRICS, SLEEP_TIMER }

internal val NowPlayingAction.isQuickAction: Boolean
    get() = this == NowPlayingAction.FAVORITE || this == NowPlayingAction.RATE_SONG ||
            this == NowPlayingAction.SLEEP_TIMER || this == NowPlayingAction.TRACK_INFORMATION

internal enum class NowPlayingFavoriteFeedback { ADDED_TO_FAVORITES, REMOVED_FROM_FAVORITES }

internal fun supportsNowPlayingMore(presentation: PlayerPresentationIdentity): Boolean = when (presentation) {
    is PlayerPresentationIdentity.Existing -> supportsNowPlayingMore(presentation.theme)
    PlayerPresentationIdentity.PocketAdvance, PlayerPresentationIdentity.PocketHorizon -> true
}

internal fun supportsNowPlayingMore(theme: PlayerTheme): Boolean = when (theme) {
    PlayerTheme.DEFAULT, PlayerTheme.RETRO_RACK, PlayerTheme.POCKET_FLIP,
    PlayerTheme.POCKET_CASSETTE, PlayerTheme.POCKET_DISC -> true
    PlayerTheme.CLASSIC_WHEEL -> false
}

internal data class NowPlayingActionItem(
    val action: NowPlayingAction,
    @StringRes val labelRes: Int,
    val isActive: Boolean = false
)

internal fun isCurrentNowPlayingTarget(target: Song?, currentSong: Song?): Boolean =
    target != null && currentSong != null && target.membershipKey() == currentSong.membershipKey()

internal fun resolveNowPlayingAlbumKey(song: Song, librarySongs: List<Song>): String? =
    findLibraryAlbumGroupForSong(song, buildLibraryAlbumGroups(librarySongs))?.key

/** Navigation policy only; preserve the library's single-string artist grouping semantics. */
internal fun resolveNowPlayingArtistName(song: Song, librarySongs: List<Song>): String? {
    val normalized = normalizeArtistName(song.artist)
    if (normalized in nonNavigableArtistNames) {
        return null
    }
    val identity = artistIdentity(song.artist)
    return buildLibraryArtistGroups(librarySongs)
        .firstOrNull { it.identity == identity && !it.identity.isUnknown }?.name
}

private val nonNavigableArtistNames =
    setOf("", "unknown", "unknown artist", "<unknown>", "artista desconocido")

/** Revalidate both entry points against the latest track and library before navigating. */
internal fun openCurrentNowPlayingArtist(
    target: Song,
    currentSong: Song?,
    librarySongs: List<Song>,
    onOpenArtist: (String) -> Unit
): Boolean {
    if (!isCurrentNowPlayingTarget(target, currentSong)) return false
    val artistName = resolveNowPlayingArtistName(requireNotNull(currentSong), librarySongs) ?: return false
    onOpenArtist(artistName)
    return true
}

internal fun nowPlayingActions(
    target: Song,
    favoriteMembershipKeys: Set<String>,
    librarySongs: List<Song>,
    isRated: Boolean = false,
    isSleepTimerActive: Boolean = false
): List<NowPlayingActionItem> = buildList {
    if (resolveNowPlayingArtistName(target, librarySongs) != null) {
        add(NowPlayingActionItem(NowPlayingAction.GO_TO_ARTIST, R.string.library_search_go_artist))
    }
    if (resolveNowPlayingAlbumKey(target, librarySongs) != null) {
        add(NowPlayingActionItem(NowPlayingAction.GO_TO_ALBUM, R.string.player_go_to_album))
    }
    add(NowPlayingActionItem(NowPlayingAction.ADD_TO_PLAYLIST, R.string.library_song_add_to_playlist))
    // Missing or unconfigured local lyrics are handled by the existing Lyrics screen.
    add(NowPlayingActionItem(NowPlayingAction.LYRICS, R.string.player_lyrics))
    val isFavorite = target.membershipKey() in favoriteMembershipKeys
    add(NowPlayingActionItem(
        action = NowPlayingAction.FAVORITE,
        labelRes = if (isFavorite) R.string.player_remove_favorite else R.string.player_add_favorite,
        isActive = isFavorite
    ))
    add(NowPlayingActionItem(NowPlayingAction.RATE_SONG, R.string.rate_song, isActive = isRated))
    add(NowPlayingActionItem(NowPlayingAction.SLEEP_TIMER, R.string.sleep_timer_title, isActive = isSleepTimerActive))
    add(NowPlayingActionItem(NowPlayingAction.TRACK_INFORMATION, R.string.player_track_information))
}

/**
 * Dismiss first, then validate the captured target against the latest playback/library state.
 * [isFavorite] captures membership at selection time, before the optimistic toggle changes it.
 */
internal fun performNowPlayingAction(
    action: NowPlayingAction,
    target: Song,
    currentSong: Song?,
    librarySongs: List<Song>,
    onDismiss: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenAlbum: (Song) -> Unit,
    onOpenLyrics: () -> Unit,
    isFavorite: Boolean = false,
    onFavoriteFeedback: (NowPlayingFavoriteFeedback) -> Unit = {},
    onOpenArtist: (Song) -> Unit = {},
    onTrackInfoClick: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    onRateSong: (Song) -> Unit = {},
    onOpenSleepTimer: () -> Unit = {}
): Boolean {
    onDismiss()
    if (!isCurrentNowPlayingTarget(target, currentSong)) return false
    when (action) {
        NowPlayingAction.FAVORITE -> {
            val feedback = if (isFavorite) {
                NowPlayingFavoriteFeedback.REMOVED_FROM_FAVORITES
            } else {
                NowPlayingFavoriteFeedback.ADDED_TO_FAVORITES
            }
            onToggleFavorite(target)
            onFavoriteFeedback(feedback)
        }
        NowPlayingAction.GO_TO_ARTIST -> {
            if (resolveNowPlayingArtistName(requireNotNull(currentSong), librarySongs) == null) return false
            onOpenArtist(target)
        }
        NowPlayingAction.GO_TO_ALBUM -> {
            if (resolveNowPlayingAlbumKey(target, librarySongs) == null) return false
            onOpenAlbum(target)
        }
        NowPlayingAction.TRACK_INFORMATION -> onTrackInfoClick(target)
        NowPlayingAction.ADD_TO_PLAYLIST -> onAddToPlaylist(target)
        NowPlayingAction.RATE_SONG -> onRateSong(target)
        NowPlayingAction.LYRICS -> onOpenLyrics()
        NowPlayingAction.SLEEP_TIMER -> onOpenSleepTimer()
    }
    return true
}
