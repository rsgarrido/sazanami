package io.github.rsgarrido.sazanami.ui.state

import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.ui.library.LibraryGridColumns
import io.github.rsgarrido.sazanami.ui.library.LibraryViewMode
import io.github.rsgarrido.sazanami.ui.library.LibraryViewCategory
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import io.github.rsgarrido.sazanami.ui.library.viewCategory
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarStyle
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens

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
