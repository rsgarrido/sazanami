package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import android.net.Uri
import androidx.compose.runtime.saveable.SaverScope
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.UNKNOWN_ARTIST_DISPLAY_NAME
import io.github.rsgarrido.sazanami.data.UNKNOWN_ARTIST_IDENTITY
import io.github.rsgarrido.sazanami.data.artistIdentity
import io.github.rsgarrido.sazanami.data.stableUiKey
import io.github.rsgarrido.sazanami.ui.library.LibraryArtistGroup
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.LibrarySortOption
import io.github.rsgarrido.sazanami.ui.library.LibrarySortState
import io.github.rsgarrido.sazanami.ui.library.sortedLibraryArtistGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class PocketAdvanceArtistsNavigationTest {
    @Test
    fun canonicalGroupsNormalizeNamesWithoutSplittingCollaborationsOrUsingAlbumArtist() {
        val supplied = listOf(
            song(1, artist = "  The   Warning  "),
            song(2, artist = "THE WARNING"),
            song(3, artist = "The Warning & Guest"),
            song(4, artist = "The Warning; Guest"),
            song(5, artist = "The Warning feat. Guest"),
            song(6, artist = " \t ", albumArtist = "Album Artist Only"),
            song(7, artist = UNKNOWN_ARTIST_DISPLAY_NAME),
            song(8, artist = "Track Artist", albumArtist = "Album Artist Only")
        )
        val groups = artists(supplied)
        val warning = groups.single { it.key == artistIdentity("the warning").key }
        assertEquals("THE WARNING", warning.name)
        assertEquals(listOf(1L, 2L), warning.songs.map(Song::id))
        listOf("The Warning & Guest", "The Warning; Guest", "The Warning feat. Guest").forEach { name ->
            assertEquals(1, groups.single { it.key == artistIdentity(name).key }.songs.size)
        }
        assertFalse(groups.any { it.key == artistIdentity("Guest").key })
        assertFalse(groups.any { it.key == artistIdentity("Album Artist Only").key })
        assertEquals(listOf(8L), groups.single { it.key == artistIdentity("Track Artist").key }.songs.map(Song::id))
        val unknown = groups.single { it.identity.isUnknown }
        val literalUnknown = groups.single { it.key == artistIdentity(UNKNOWN_ARTIST_DISPLAY_NAME).key }
        assertEquals(UNKNOWN_ARTIST_IDENTITY, unknown.identity)
        assertEquals(UNKNOWN_ARTIST_DISPLAY_NAME, unknown.name)
        assertEquals(listOf(6L), unknown.songs.map(Song::id))
        assertEquals(listOf(7L), literalUnknown.songs.map(Song::id))
        assertNotEquals(unknown.key, literalUnknown.key)
        assertEquals(unknown.name, literalUnknown.name)
        assertEquals("  The   Warning  ", supplied.first().artist)
    }

    @Test
    fun artistOrderingUsesTheMainLibraryNameDirectionAndSongCountChoices() {
        val supplied = listOf(song(1, "Beta"), song(2, "Alpha"), song(3, "Alpha"),
            song(4, "Gamma"), song(5, ""))
        assertEquals(listOf("Alpha", "Beta", "Gamma", UNKNOWN_ARTIST_DISPLAY_NAME),
            artists(supplied).map { it.name })
        assertEquals(listOf("Gamma", "Beta", "Alpha", UNKNOWN_ARTIST_DISPLAY_NAME),
            artists(supplied, LibrarySortOption.NAME, LibrarySortDirection.DESCENDING).map { it.name })
        val byCount = artists(supplied, LibrarySortOption.SONG_COUNT, LibrarySortDirection.DESCENDING)
        assertEquals(listOf("Alpha", "Beta", "Gamma", UNKNOWN_ARTIST_DISPLAY_NAME), byCount.map { it.name })
        assertEquals(listOf(2, 1, 1, 1), byCount.map { it.songs.size })
    }

    @Test
    fun artistTracksKeepCanonicalAlbumDiscTrackAndTitleOrder() {
        val supplied = listOf(
            song(20, album = "Zulu", track = 1, disc = 1),
            song(4, album = "Alpha", track = 2_001),
            song(3, album = "Alpha", track = 2, disc = 1, title = "Beta"),
            song(5, album = "Alpha", track = 0, disc = 1),
            song(2, album = "Alpha", track = 2, disc = 1, title = "Alpha"),
            song(1, album = "Alpha", track = 1, disc = 1)
        )
        val group = artists(supplied).single()
        assertEquals(listOf(1L, 2L, 3L, 5L, 4L, 20L), group.songs.map(Song::id))
        val navigation = artistsNavigation(listOf(group))
        navigation.openArtist(group.key, 0)
        navigation.reconcileArtists(listOf(group), isLibraryReady = true)
        val keys = group.songs.map(Song::stableUiKey)
        navigation.selectArtistSong(keys[3], 3)
        val selected = group.songs[requireNotNull(navigation.selectedBrowseIndex(keys))]
        assertSame(group.songs[3], selected)
        assertEquals(5L, selected.id)
        assertEquals(listOf(1L, 2L, 3L, 5L, 4L, 20L), group.songs.map(Song::id))
    }

    @Test
    fun artistsAndDetailBackNavigationRetainTheirPageCursorsAndViewports() {
        val groups = artists(listOf(song(1, "Alpha"), song(2, "Beta"), song(3, "Beta")))
        val navigation = artistsNavigation(groups)
        assertTrue(PocketAdvanceRootEntry.Artists.isAvailable)
        assertEquals(PocketAdvanceBrowserPage.Artists, navigation.browserPage)
        assertEquals(groups.first().key, navigation.selectedArtistKey)
        navigation.moveSelectionDown(groups.map { it.key })
        navigation.artistsListState.requestScrollToItem(1, 7)
        val selected = groups[1]
        navigation.openArtist(selected.key, 1)
        navigation.reconcileArtists(groups, isLibraryReady = true)
        navigation.selectArtistSong(selected.songs[1].stableUiKey(), 1)
        navigation.artistSongsListState.requestScrollToItem(1, 11)
        val artistList = navigation.artistsListState
        val trackList = navigation.artistSongsListState
        navigation.toggleMode()
        assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
        navigation.back()
        assertEquals(PocketAdvanceBrowserPage.ArtistSongs(selected.key), navigation.browserPage)
        assertEquals(selected.songs[1].stableUiKey(), navigation.selectedArtistSongKey)
        assertSame(artistList, navigation.artistsListState)
        assertSame(trackList, navigation.artistSongsListState)
        assertEquals(7, artistList.firstVisibleItemScrollOffset)
        assertEquals(11, trackList.firstVisibleItemScrollOffset)
        navigation.back()
        assertEquals(PocketAdvanceBrowserPage.Artists, navigation.browserPage)
        assertEquals(selected.key, navigation.selectedArtistKey)
        navigation.back()
        assertEquals(PocketAdvanceBrowserPage.Root, navigation.browserPage)
        assertEquals(PocketAdvanceRootEntry.Artists, navigation.selectedRootEntry)
        navigation.back()
        assertEquals(PocketAdvanceLcdMode.NowPlaying, navigation.lcdMode)
        navigation.openBrowser()
        navigation.confirmSelection()
        assertEquals(PocketAdvanceBrowserPage.Artists, navigation.browserPage)
        assertEquals(selected.key, navigation.selectedArtistKey)
    }

    @Test
    fun reopeningTheSameArtistRetainsDetailButAnotherArtistStartsAtItsFirstTrack() {
        val groups = artists(listOf(song(1, "Alpha"), song(2, "Alpha"), song(3, "Beta")))
        val navigation = artistsNavigation(groups)
        val alpha = groups[0]
        navigation.openArtist(alpha.key, 0)
        navigation.reconcileArtists(groups, isLibraryReady = true)
        navigation.selectArtistSong(alpha.songs[1].stableUiKey(), 1)
        navigation.artistSongsListState.requestScrollToItem(1, 17)
        navigation.back()
        navigation.openArtist(alpha.key, 0)
        assertEquals(alpha.songs[1].stableUiKey(), navigation.selectedArtistSongKey)
        assertEquals(1, navigation.artistSongsListState.firstVisibleItemIndex)
        assertEquals(17, navigation.artistSongsListState.firstVisibleItemScrollOffset)
        navigation.back()
        navigation.openArtist(groups[1].key, 1)
        assertNull(navigation.selectedArtistSongKey)
        assertEquals(0, navigation.artistSongsListState.firstVisibleItemIndex)
        assertEquals(0, navigation.artistSongsListState.firstVisibleItemScrollOffset)
        navigation.reconcileArtists(groups, isLibraryReady = true)
        assertEquals(groups[1].songs.first().stableUiKey(), navigation.selectedArtistSongKey)
    }

    @Test
    fun reorderedArtistsResolveTheirCurrentIdentityAndDeletedSelectionUsesAValidNeighbor() {
        val supplied = listOf(song(1, "Alpha"), song(2, "Beta"), song(3, "Gamma"))
        val original = artists(supplied)
        val navigation = artistsNavigation(original)
        navigation.moveSelectionDown(original.map { it.key })
        val selectedKey = navigation.selectedArtistKey
        val changed = artists(supplied + song(4, "Delta"), LibrarySortOption.NAME, LibrarySortDirection.DESCENDING)
        val keys = changed.map { it.key }
        assertEquals(2, navigation.selectedArtistIndex(keys))
        assertEquals(selectedKey, changed[requireNotNull(navigation.selectedBrowseIndex(keys))].key)
        navigation.reconcileArtists(changed, isLibraryReady = true)
        assertEquals(selectedKey, navigation.selectedArtistKey)
        val withoutSelected = changed.filterNot { it.key == selectedKey }
        navigation.reconcileArtists(withoutSelected, isLibraryReady = true)
        assertEquals(withoutSelected[2].key, navigation.selectedArtistKey)
        assertEquals(2, navigation.selectedArtistIndex(withoutSelected.map { it.key }))
    }

    @Test
    fun detailSelectionSurvivesCaseMetadataAndOrderChangesThenFallsBackAfterTrackDeletion() {
        val supplied = listOf(song(1), song(2), song(3))
        val original = artists(supplied)
        val navigation = artistsNavigation(original)
        navigation.openArtist(original.single().key, 0)
        navigation.reconcileArtists(original, isLibraryReady = true)
        val selectedKey = supplied[1].stableUiKey()
        navigation.selectArtistSong(selectedKey, 1)
        val updatedSongs = supplied.map { track ->
            track.copy(artist = "  ARTIST  ", title = "Revised " + track.id,
                trackNumber = if (track.id == 2L) 99 else track.trackNumber)
        }
        val changed = artists(updatedSongs)
        val changedKeys = changed.single().songs.map(Song::stableUiKey)
        assertEquals(2, navigation.selectedArtistSongIndex(changedKeys))
        navigation.reconcileArtists(changed, isLibraryReady = true)
        assertEquals(PocketAdvanceBrowserPage.ArtistSongs(original.single().key), navigation.browserPage)
        assertEquals(selectedKey, navigation.selectedArtistSongKey)
        val removed = artists(updatedSongs.filterNot { it.id == 2L })
        navigation.reconcileArtists(removed, isLibraryReady = true)
        assertEquals(removed.single().songs.last().stableUiKey(), navigation.selectedArtistSongKey)
        assertEquals(1, navigation.selectedArtistSongIndex(removed.single().songs.map(Song::stableUiKey)))
    }

    @Test
    fun movingTheSelectedTrackToAnotherArtistKeepsTheOriginalArtistContextCurrent() {
        val supplied = listOf(song(1, "Alpha"), song(2, "Alpha"), song(3, "Alpha"), song(4, "Beta"))
        val original = artists(supplied)
        val alpha = original.first()
        val selectedTrack = supplied[1]
        val navigation = artistsNavigation(original)
        navigation.openArtist(alpha.key, 0)
        navigation.reconcileArtists(original, isLibraryReady = true)
        navigation.selectArtistSong(selectedTrack.stableUiKey(), 1)
        val updatedSongs = supplied.map { track ->
            if (track.id == selectedTrack.id) track.copy(artist = "Beta") else track
        }
        val changed = artists(updatedSongs)
        navigation.reconcileArtists(changed, isLibraryReady = true)
        val currentAlpha = changed.single { it.key == alpha.key }
        val beta = changed.single { it.key == artistIdentity("Beta").key }
        val movedTrack = beta.songs.single { it.id == selectedTrack.id }
        assertEquals(selectedTrack.stableUiKey(), movedTrack.stableUiKey())
        assertEquals(alpha.key, navigation.selectedArtistKey)
        assertEquals(PocketAdvanceBrowserPage.ArtistSongs(alpha.key), navigation.browserPage)
        assertEquals(listOf(1L, 3L), currentAlpha.songs.map(Song::id))
        assertFalse(currentAlpha.songs.any { it.stableUiKey() == selectedTrack.stableUiKey() })
        assertEquals(listOf(2L, 4L), beta.songs.map(Song::id))
        val contextKeys = currentAlpha.songs.map(Song::stableUiKey)
        val selected = currentAlpha.songs[requireNotNull(navigation.selectedBrowseIndex(contextKeys))]
        assertEquals(3L, selected.id)
        assertEquals(selected.stableUiKey(), navigation.selectedArtistSongKey)
        assertTrue(navigation.canConfirm(contextKeys))

        val renamed = artists(updatedSongs.map { track ->
            if (track.artist == "Alpha") track.copy(artist = "New Alpha") else track
        })
        navigation.reconcileArtists(renamed, isLibraryReady = true)
        assertFalse(renamed.any { it.key == alpha.key })
        assertEquals(PocketAdvanceBrowserPage.Artists, navigation.browserPage)
        assertNull(navigation.retainedArtistKey)
        assertNull(navigation.selectedArtistSongKey)
        assertTrue(renamed.any { it.key == navigation.selectedArtistKey })
    }

    @Test
    fun sameNumericTrackIdOnDifferentVolumesKeepsTheSelectedTrackIdentity() {
        val first = song(1, track = 1)
        val second = song(1, track = 2).copy(volumeName = "removable_volume")
        val original = artists(listOf(first, second))
        val navigation = artistsNavigation(original)
        navigation.openArtist(original.single().key, 0)
        navigation.reconcileArtists(original, isLibraryReady = true)
        navigation.selectArtistSong(second.stableUiKey(), 1)
        assertNotEquals(first.stableUiKey(), second.stableUiKey())
        val changed = artists(listOf(first.copy(trackNumber = 2), second.copy(trackNumber = 1)))
        navigation.reconcileArtists(changed, isLibraryReady = true)
        assertEquals(second.stableUiKey(), navigation.selectedArtistSongKey)
        assertEquals(0, navigation.selectedArtistSongIndex(changed.single().songs.map(Song::stableUiKey)))
        assertEquals("removable_volume", changed.single().songs[0].volumeName)
    }

    @Test
    fun temporaryEmptyOrPartialLibrariesPreserveTheArtistAndTrackUntilAuthoritativeRemoval() {
        val groups = artists(listOf(song(1, "Alpha"), song(2, "Beta"), song(3, "Beta")))
        val beta = groups[1]
        val navigation = artistsNavigation(groups)
        navigation.openArtist(beta.key, 1)
        navigation.reconcileArtists(groups, isLibraryReady = true)
        navigation.selectArtistSong(beta.songs[1].stableUiKey(), 1)
        navigation.artistsListState.requestScrollToItem(1, 9)
        navigation.artistSongsListState.requestScrollToItem(1, 13)
        navigation.reconcileArtists(emptyList(), isLibraryReady = false)
        navigation.reconcileArtists(groups.take(1), isLibraryReady = false)
        assertEquals(PocketAdvanceBrowserPage.ArtistSongs(beta.key), navigation.browserPage)
        assertEquals(beta.key, navigation.selectedArtistKey)
        assertEquals(beta.songs[1].stableUiKey(), navigation.selectedArtistSongKey)
        assertNull(navigation.selectedArtistSongIndex(emptyList()))
        assertFalse(navigation.canConfirm(emptyList()))
        assertEquals(9, navigation.artistsListState.firstVisibleItemScrollOffset)
        assertEquals(13, navigation.artistSongsListState.firstVisibleItemScrollOffset)
        navigation.reconcileArtists(groups.reversed(), isLibraryReady = true)
        assertEquals(beta.key, navigation.selectedArtistKey)
        assertEquals(0, navigation.selectedArtistIndex(groups.reversed().map { it.key }))
        navigation.reconcileArtists(groups.take(1), isLibraryReady = true)
        assertEquals(PocketAdvanceBrowserPage.Artists, navigation.browserPage)
        assertNull(navigation.retainedArtistKey)
        assertNull(navigation.selectedArtistSongKey)
        assertEquals(groups.first().key, navigation.selectedArtistKey)
        assertEquals(0, navigation.artistSongsListState.firstVisibleItemIndex)
        assertEquals(0, navigation.artistSongsListState.firstVisibleItemScrollOffset)
    }

    @Test
    fun authoritativeEmptyReturnsToArtistsAndClearsBothBrowseCursors() {
        val groups = artists(listOf(song(1), song(2)))
        val navigation = artistsNavigation(groups)
        navigation.openArtist(groups.single().key, 0)
        navigation.reconcileArtists(groups, isLibraryReady = true)
        navigation.artistSongsListState.requestScrollToItem(1, 5)
        navigation.reconcileArtists(emptyList(), isLibraryReady = true)
        assertEquals(PocketAdvanceBrowserPage.Artists, navigation.browserPage)
        assertNull(navigation.selectedArtistKey)
        assertNull(navigation.selectedArtistSongKey)
        assertNull(navigation.retainedArtistKey)
        assertFalse(navigation.canConfirm(emptyList()))
        assertEquals(listOf("", 0, 0, 0, ""), navigation.artistsCursor.save())
        assertEquals(listOf("", 0, 0, 0, ""), navigation.artistSongsCursor.save())
        navigation.reconcileArtists(groups, isLibraryReady = true)
        assertEquals(groups.first().key, navigation.selectedArtistKey)
        assertEquals(PocketAdvanceBrowserPage.Artists, navigation.browserPage)
        assertNull(navigation.selectedArtistSongKey)
    }

    @Test
    fun browseHoldTicksClampWhileTapsWrapAndStalePageTicksCannotCrossDestinations() {
        val groups = artists(listOf(song(1, "Alpha"), song(2, "Beta"), song(3, "Beta")))
        val navigation = artistsNavigation(groups)
        val artistKeys = groups.map { it.key }
        val artistsPage = PocketAdvanceBrowserPage.Artists
        assertFalse(navigation.repeatSongSelection(1, artistKeys))
        assertFalse(navigation.repeatBrowseSelection(-1, artistKeys, artistsPage))
        assertTrue(navigation.repeatBrowseSelection(1, artistKeys, artistsPage))
        assertTrue(navigation.artistsCursor.takeScrollRequest())
        assertFalse(navigation.repeatBrowseSelection(1, artistKeys, artistsPage))
        assertFalse(navigation.artistsCursor.takeScrollRequest())
        navigation.moveSelectionDown(artistKeys)
        assertEquals(groups.first().key, navigation.selectedArtistKey)
        assertTrue(navigation.artistsCursor.takeScrollRequest())
        val beta = groups[1]
        navigation.openArtist(beta.key, 1)
        navigation.reconcileArtists(groups, isLibraryReady = true)
        val detailPage = PocketAdvanceBrowserPage.ArtistSongs(beta.key)
        val trackKeys = beta.songs.map(Song::stableUiKey)
        assertFalse(navigation.repeatBrowseSelection(1, artistKeys, artistsPage))
        assertFalse(navigation.repeatBrowseSelection(-1, trackKeys, detailPage))
        assertFalse(navigation.repeatBrowseSelection(0, trackKeys, detailPage))
        assertFalse(navigation.repeatBrowseSelection(1, emptyList(), detailPage))
        assertTrue(navigation.repeatBrowseSelection(1, trackKeys, detailPage))
        assertTrue(navigation.artistSongsCursor.takeScrollRequest())
        assertFalse(navigation.repeatBrowseSelection(1, trackKeys, detailPage))
        assertFalse(navigation.artistSongsCursor.takeScrollRequest())
        navigation.moveSelectionDown(trackKeys)
        assertEquals(trackKeys.first(), navigation.selectedArtistSongKey)
        navigation.showNowPlaying()
        assertFalse(navigation.repeatBrowseSelection(1, trackKeys, detailPage))
        navigation.openBrowser()
        navigation.back()
        navigation.openArtist(groups.first().key, 0)
        navigation.reconcileArtists(groups, isLibraryReady = true)
        assertFalse(navigation.repeatBrowseSelection(1, trackKeys, detailPage))
        assertEquals(groups.first().songs.first().stableUiKey(), navigation.selectedArtistSongKey)
    }

    @Test
    fun songsArtistsAndArtistTracksKeepIndependentCursorsAndViewportsThroughSaving() {
        val groups = artists(listOf(song(1, "Alpha"), song(2, "Beta"), song(3, "Beta")))
        val navigation = PocketAdvanceNavigationState(PocketAdvanceRootEntry.Songs)
        navigation.openBrowser()
        navigation.confirmSelection()
        navigation.reconcileSongs(listOf("song-a", "song-b"), isLibraryReady = true)
        navigation.selectSong("song-b", 1)
        navigation.rootListState.requestScrollToItem(2, 3)
        navigation.songsListState.requestScrollToItem(1, 5)
        navigation.back()
        navigation.activateRootEntry(PocketAdvanceRootEntry.Artists)
        navigation.reconcileArtists(groups, isLibraryReady = true)
        navigation.openArtist(groups[1].key, 1)
        navigation.reconcileArtists(groups, isLibraryReady = true)
        navigation.selectArtistSong(groups[1].songs[1].stableUiKey(), 1)
        navigation.artistsListState.requestScrollToItem(1, 7)
        navigation.artistSongsListState.requestScrollToItem(1, 11)
        val saved = save(navigation)
        assertEquals(15, saved.size) // Album Archive appends compact state after the original twelve fields.
        assertEquals(listOf("Artists", "ArtistSongs", "song-b", 1, 2, 3, 1, 5, ""), saved.take(9))
        val restored = requireNotNull(PocketAdvanceNavigationState.Saver.restore(saved))
        assertEquals(PocketAdvanceLcdMode.NowPlaying, restored.lcdMode)
        assertEquals(PocketAdvanceBrowserPage.ArtistSongs(groups[1].key), restored.browserPage)
        assertEquals("song-b", restored.selectedSongKey)
        assertEquals(groups[1].key, restored.selectedArtistKey)
        assertEquals(groups[1].songs[1].stableUiKey(), restored.selectedArtistSongKey)
        assertEquals(3, restored.rootListState.firstVisibleItemScrollOffset)
        assertEquals(5, restored.songsListState.firstVisibleItemScrollOffset)
        assertEquals(7, restored.artistsListState.firstVisibleItemScrollOffset)
        assertEquals(11, restored.artistSongsListState.firstVisibleItemScrollOffset)
        restored.openBrowser()
        restored.back()
        restored.back()
        restored.activateRootEntry(PocketAdvanceRootEntry.Songs)
        assertEquals(1, restored.selectedBrowseIndex(listOf("song-a", "song-b")))
        assertEquals("song-b", restored.selectedSongKey)
        assertEquals(5, restored.songsListState.firstVisibleItemScrollOffset)
        assertEquals(groups[1].songs[1].stableUiKey(), restored.selectedArtistSongKey)
    }

    @Test
    fun restoredArtistAndTrackViewportsFollowTheirKeysAfterTheLibraryReorders() {
        val betaSongs = listOf(song(2, "Beta"), song(3, "Beta"), song(4, "Beta"))
        val original = artists(listOf(song(1, "Alpha")) + betaSongs + song(5, "Gamma"))
        val alpha = original[0]
        val beta = original[1]
        val firstTrackKey = beta.songs[0].stableUiKey()
        val selectedTrackKey = beta.songs[1].stableUiKey()
        val payload = listOf("Artists", "ArtistSongs", "", 0, 0, 0, 0, 0, "",
            listOf(beta.key, 1, 0, 7, alpha.key), beta.key,
            listOf(selectedTrackKey, 1, 0, 13, firstTrackKey))
        val restored = requireNotNull(PocketAdvanceNavigationState.Saver.restore(payload))
        restored.reconcileArtists(emptyList(), isLibraryReady = false)
        assertEquals(0, restored.artistsListState.firstVisibleItemIndex)
        assertEquals(0, restored.artistSongsListState.firstVisibleItemIndex)
        val changedBeta = beta.copy(songs = beta.songs.reversed())
        val reordered = listOf(changedBeta, original[2], alpha)
        restored.reconcileArtists(reordered, isLibraryReady = true)
        assertEquals(beta.key, restored.selectedArtistKey)
        assertEquals(0, restored.selectedArtistIndex(reordered.map { it.key }))
        assertEquals(selectedTrackKey, restored.selectedArtistSongKey)
        assertEquals(1, restored.selectedArtistSongIndex(changedBeta.songs.map(Song::stableUiKey)))
        assertEquals(2, restored.artistsListState.firstVisibleItemIndex)
        assertEquals(7, restored.artistsListState.firstVisibleItemScrollOffset)
        assertEquals(2, restored.artistSongsListState.firstVisibleItemIndex)
        assertEquals(13, restored.artistSongsListState.firstVisibleItemScrollOffset)
        assertFalse(restored.artistsCursor.takeScrollRequest())
        assertFalse(restored.artistSongsCursor.takeScrollRequest())
    }

    @Test
    fun authoritativeEmptyAfterRestorationCannotResaveDetachedArtistViewportAnchors() {
        val group = artists(listOf(song(1))).single()
        val payload = listOf("Artists", "ArtistSongs", "", 0, 0, 0, 0, 0, "",
            listOf(group.key, 2, 2, 7, "old-artist-anchor"), group.key,
            listOf(group.songs.first().stableUiKey(), 3, 3, 11, "old-track-anchor"))
        val restored = requireNotNull(PocketAdvanceNavigationState.Saver.restore(payload))
        restored.reconcileArtists(emptyList(), isLibraryReady = true)
        val saved = save(restored)
        assertEquals("Artists", saved[1])
        assertEquals(listOf("", 0, 0, 0, ""), saved[9])
        assertEquals("", saved[10])
        assertEquals(listOf("", 0, 0, 0, ""), saved[11])
    }

    @Test
    fun legacySongsPayloadRemainsReadableAndMissingArtistDetailRestoresToItsParent() {
        val legacy = listOf("Songs", "Songs", "selected-song", 2, 1, 3, 2, 5, "visible-song")
        val restored = requireNotNull(PocketAdvanceNavigationState.Saver.restore(legacy))
        assertEquals(PocketAdvanceLcdMode.NowPlaying, restored.lcdMode)
        assertEquals(PocketAdvanceBrowserPage.Songs, restored.browserPage)
        assertEquals("selected-song", restored.selectedSongKey)
        assertEquals(2, restored.songsListState.firstVisibleItemIndex)
        assertEquals(5, restored.songsListState.firstVisibleItemScrollOffset)
        assertNull(restored.selectedArtistKey)
        assertNull(restored.selectedArtistSongKey)
        val missingArtist = requireNotNull(PocketAdvanceNavigationState.Saver.restore(
            listOf("Artists", "ArtistSongs", "", 0, 0, 0, 0, 0, "")))
        assertEquals(PocketAdvanceLcdMode.NowPlaying, missingArtist.lcdMode)
        assertEquals(PocketAdvanceBrowserPage.Artists, missingArtist.browserPage)
        assertNull(missingArtist.retainedArtistKey)
    }

    private fun artists(
        songs: List<Song>,
        option: LibrarySortOption = LibrarySortOption.NAME,
        direction: LibrarySortDirection = LibrarySortDirection.ASCENDING
    ): List<LibraryArtistGroup> = sortedLibraryArtistGroups(songs, LibrarySortState(option, direction))

    private fun artistsNavigation(groups: List<LibraryArtistGroup>) =
        PocketAdvanceNavigationState(PocketAdvanceRootEntry.Artists).apply {
            openBrowser()
            confirmSelection()
            reconcileArtists(groups, isLibraryReady = true)
        }

    private fun save(navigation: PocketAdvanceNavigationState): List<*> {
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        return requireNotNull(with(PocketAdvanceNavigationState.Saver) { scope.save(navigation) }) as List<*>
    }

    private fun song(
        id: Long,
        artist: String = "Artist",
        albumArtist: String = "Unrelated Album Artist",
        album: String = "Album",
        track: Int = id.toInt(),
        disc: Int? = null,
        title: String = "Track $id"
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        trackNumber = track,
        duration = 180_000L,
        uri = mock(Uri::class.java),
        filePath = "/music/$album/$id.flac",
        folderPath = "/music/$album",
        albumArtUri = null,
        albumArtist = albumArtist,
        volumeName = "external_primary",
        discNumber = disc
    )
}
