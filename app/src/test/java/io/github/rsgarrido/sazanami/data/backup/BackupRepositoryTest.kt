package io.github.rsgarrido.sazanami.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupRepositoryTest {
    @Test
    fun restoreSummary_countsSongsAcrossAllPlaylists() {
        val summary = backupWithPlaylistSongCounts(2, 0, 3).toBackupRestoreSummary()

        assertEquals(3, summary.playlistCount)
        assertEquals(5, summary.playlistSongCount)
    }

    @Test
    fun exportResult_countsSongsAcrossAllPlaylists() {
        val result = backupWithPlaylistSongCounts(1, 4).toBackupExportResult()

        assertEquals(2, result.playlistCount)
        assertEquals(5, result.playlistSongCount)
    }

    private fun backupWithPlaylistSongCounts(vararg songCounts: Int): AppBackup {
        return AppBackup(
            createdAt = 123L,
            canonicalListeningHistory = BackupListeningHistoryV2(),
            playlists = songCounts.mapIndexed { playlistIndex, songCount ->
                BackupPlaylist(
                    name = "Playlist $playlistIndex",
                    createdAt = playlistIndex.toLong(),
                    updatedAt = playlistIndex.toLong(),
                    songs = List(songCount) { songIndex ->
                        BackupPlaylistSong(
                            songKey = "song-$playlistIndex-$songIndex",
                            position = songIndex,
                            title = "Song $songIndex",
                            artist = "Artist",
                            album = "Album",
                            duration = 1L,
                            addedAt = songIndex.toLong()
                        )
                    }
                )
            }
        )
    }
}
