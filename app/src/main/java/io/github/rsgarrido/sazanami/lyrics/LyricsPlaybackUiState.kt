package io.github.rsgarrido.sazanami.lyrics

import io.github.rsgarrido.sazanami.data.Song

sealed interface LyricsPlaybackUiState {
    data object Hidden : LyricsPlaybackUiState

    data class Loading(
        val song: Song
    ) : LyricsPlaybackUiState

    data class Synced(
        val song: Song,
        val lyrics: LyricsDocument.Synced,
        val activeGroup: ActiveLyricGroup?,
        val autoFollowEnabled: Boolean
    ) : LyricsPlaybackUiState

    data class Unsynced(
        val song: Song,
        val lyrics: LyricsDocument.Unsynced
    ) : LyricsPlaybackUiState

    data class Unavailable(
        val song: Song,
        val reason: LyricsUnavailableReason
    ) : LyricsPlaybackUiState
}

sealed interface LyricsUnavailableReason {
    data object NoRootsConfigured : LyricsUnavailableReason
    data object NotFound : LyricsUnavailableReason
    data class Ambiguous(val candidates: List<LyricsCandidate>) : LyricsUnavailableReason
    data class PermissionLost(val rootUri: String) : LyricsUnavailableReason
    data class RootScanError(val rootUri: String) : LyricsUnavailableReason
    data class StaleFile(val documentUri: String) : LyricsUnavailableReason
    data class ReadError(val documentUri: String) : LyricsUnavailableReason
    data class InvalidLyrics(val documentUri: String) : LyricsUnavailableReason
}
