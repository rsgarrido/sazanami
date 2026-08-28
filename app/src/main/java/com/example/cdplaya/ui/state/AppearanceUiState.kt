package com.example.cdplaya.ui.state

import com.example.cdplaya.data.PlayerTheme
import com.example.cdplaya.player.replaygain.ReplayGainMode
import com.example.cdplaya.ui.library.LibraryGridColumns
import com.example.cdplaya.ui.library.LibraryViewMode
import com.example.cdplaya.ui.library.LibraryViewCategory
import com.example.cdplaya.ui.library.LibraryTab
import com.example.cdplaya.ui.library.viewCategory
import com.example.cdplaya.ui.player.modern.ModernArtworkTransitionStyle
import com.example.cdplaya.ui.player.modern.ModernPlayerAppearance
import com.example.cdplaya.ui.player.modern.ModernSeekbarStyle
import com.example.cdplaya.ui.player.theme.PlayerThemeTokens
import com.example.cdplaya.ui.player.theme.defaultTokens

data class PlayerAppearanceUiState(
    val selectedTheme: PlayerTheme = PlayerTheme.DEFAULT,
    val themeTokens: PlayerThemeTokens = PlayerTheme.DEFAULT.defaultTokens(),
    val modernArtworkTransitionStyle: ModernArtworkTransitionStyle =
        ModernArtworkTransitionStyle.SLIDE,
    val modernPlayerAppearance: ModernPlayerAppearance = ModernPlayerAppearance.Default,
    val replayGainMode: ReplayGainMode = ReplayGainMode.OFF,
    val isLoaded: Boolean = false
) {
    val modernSeekbarStyle: ModernSeekbarStyle
        get() = modernPlayerAppearance.seekbar.style
}

data class LibraryCategoryAppearance(
    val viewMode: LibraryViewMode = LibraryViewMode.LIST,
    val gridColumnCount: Int = LibraryGridColumns.DEFAULT
)

data class LibraryAppearanceUiState(
    val songs: LibraryCategoryAppearance = LibraryCategoryAppearance(),
    val albums: LibraryCategoryAppearance = LibraryCategoryAppearance(),
    val artists: LibraryCategoryAppearance = LibraryCategoryAppearance(),
    val playlists: LibraryCategoryAppearance = LibraryCategoryAppearance(),
    val isLoaded: Boolean = false
)

fun LibraryAppearanceUiState.category(category: LibraryViewCategory): LibraryCategoryAppearance =
    when (category) {
        LibraryViewCategory.SONGS -> songs
        LibraryViewCategory.ALBUMS -> albums
        LibraryViewCategory.ARTISTS -> artists
        LibraryViewCategory.PLAYLISTS -> playlists
    }

fun LibraryAppearanceUiState.modeFor(tab: LibraryTab): LibraryViewMode =
    tab.viewCategory()?.let(::category)?.viewMode ?: LibraryViewMode.LIST

fun LibraryAppearanceUiState.gridColumnCountFor(tab: LibraryTab): Int =
    tab.viewCategory()?.let(::category)?.gridColumnCount ?: LibraryGridColumns.DEFAULT
