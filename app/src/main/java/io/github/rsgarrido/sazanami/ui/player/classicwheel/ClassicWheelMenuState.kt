package io.github.rsgarrido.sazanami.ui.player.classicwheel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ClassicWheelMenuState {
    var currentScreen by mutableStateOf<ClassicWheelMenuScreen>(
        ClassicWheelMenuScreen.NowPlaying
    )
        private set

    var selectedIndex by mutableIntStateOf(0)
        private set

    private val backStack = mutableStateListOf<ClassicWheelBackStackEntry>()

    fun openMainMenu() {
        if (currentScreen != ClassicWheelMenuScreen.MainMenu) {
            pushCurrentScreenToBackStack()
        }

        currentScreen = ClassicWheelMenuScreen.MainMenu
        selectedIndex = 0
    }

    fun openSongs() {
        pushCurrentScreenToBackStack()
        currentScreen = ClassicWheelMenuScreen.Songs
        selectedIndex = 0
    }

    fun openNowPlaying() {
        backStack.clear()
        currentScreen = ClassicWheelMenuScreen.NowPlaying
        selectedIndex = 0
    }

    fun moveSelectionUp(itemCount: Int) {
        if (itemCount <= 0) {
            selectedIndex = 0
            return
        }

        selectedIndex = selectedIndex.coerceIn(0, itemCount - 1)

        selectedIndex = if (selectedIndex <= 0) {
            itemCount - 1
        } else {
            selectedIndex - 1
        }
    }

    fun moveSelectionDown(itemCount: Int) {
        if (itemCount <= 0) {
            selectedIndex = 0
            return
        }

        selectedIndex = selectedIndex.coerceIn(0, itemCount - 1)

        selectedIndex = if (selectedIndex >= itemCount - 1) {
            0
        } else {
            selectedIndex + 1
        }
    }

    fun goBack() {
        val previousEntry = backStack.removeLastOrNull()

        if (previousEntry == null) {
            currentScreen = ClassicWheelMenuScreen.NowPlaying
            selectedIndex = 0
            return
        }

        currentScreen = previousEntry.screen
        selectedIndex = previousEntry.selectedIndex
    }

    fun openArtists() {
        pushCurrentScreenToBackStack()
        currentScreen = ClassicWheelMenuScreen.Artists
        selectedIndex = 0
    }

    fun openArtistSongs(artistName: String) {
        pushCurrentScreenToBackStack()
        currentScreen = ClassicWheelMenuScreen.ArtistSongs(artistName)
        selectedIndex = 0
    }

    fun openAlbums() {
        pushCurrentScreenToBackStack()
        currentScreen = ClassicWheelMenuScreen.Albums
        selectedIndex = 0
    }

    fun openAlbumSongs(
        albumKey: String,
        albumTitle: String
    ) {
        pushCurrentScreenToBackStack()
        currentScreen = ClassicWheelMenuScreen.AlbumSongs(
            albumKey = albumKey,
            albumTitle = albumTitle
        )
        selectedIndex = 0
    }

    private fun pushCurrentScreenToBackStack() {
        backStack.add(
            ClassicWheelBackStackEntry(
                screen = currentScreen,
                selectedIndex = selectedIndex
            )
        )
    }

    private data class ClassicWheelBackStackEntry(
        val screen: ClassicWheelMenuScreen,
        val selectedIndex: Int
    )
}