package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal enum class PocketHorizonCategory { NowPlaying, Songs, Albums, Artists }

internal enum class PocketHorizonList { Songs, Albums, Artists, AlbumTracks, ArtistTracks }

/** The first visible canonical item, with index only as a fallback and offset in physical pixels. */
@Immutable
internal data class PocketHorizonScrollAnchor(
    val key: String? = null,
    val index: Int = 0,
    val offset: Int = 0
)

/**
 * Local browsing state only: no library objects, playback intent, preferences, or global lifecycle.
 * Switching categories and showing Now Playing retain detail locations. Future collapse/expansion
 * integration should retain this owner; leaving a presentation may discard it only by explicit policy.
 */
@Stable
internal class PocketHorizonNavigationState(
    initialCategory: PocketHorizonCategory = PocketHorizonCategory.NowPlaying
) {
    var category by mutableStateOf(initialCategory)
        private set
    var albumKey by mutableStateOf<String?>(null)
        private set
    var artistKey by mutableStateOf<String?>(null)
        private set
    var selectedSongKey by mutableStateOf<String?>(null)
        private set

    private val anchors = mutableStateMapOf<PocketHorizonList, PocketHorizonScrollAnchor>()
    // Owners keep same-group detail viewports useful after Back, without keeping unbounded history.
    private var albumTracksOwnerKey: String? = null
    private var artistTracksOwnerKey: String? = null

    val canGoBack: Boolean get() = category != PocketHorizonCategory.NowPlaying

    fun selectCategory(category: PocketHorizonCategory) {
        this.category = category
    }

    fun showNowPlaying() = selectCategory(PocketHorizonCategory.NowPlaying)

    fun openAlbum(key: String) {
        // Empty folder paths can be real canonical album keys; null alone means no detail context.
        if (albumTracksOwnerKey != key) anchors.remove(PocketHorizonList.AlbumTracks)
        albumTracksOwnerKey = key
        albumKey = key
        category = PocketHorizonCategory.Albums
    }

    fun openArtist(key: String) {
        if (artistTracksOwnerKey != key) anchors.remove(PocketHorizonList.ArtistTracks)
        artistTracksOwnerKey = key
        artistKey = key
        category = PocketHorizonCategory.Artists
    }

    fun selectSong(key: String?) {
        selectedSongKey = key
    }

    /** Detail -> category root -> Now Playing. Never consumes Back at the Now Playing root. */
    fun back(): Boolean = when {
        category == PocketHorizonCategory.Albums && albumKey != null -> {
            albumKey = null
            true
        }
        category == PocketHorizonCategory.Artists && artistKey != null -> {
            artistKey = null
            true
        }
        canGoBack -> {
            showNowPlaying()
            true
        }
        else -> false
    }

    fun anchor(list: PocketHorizonList): PocketHorizonScrollAnchor =
        anchors[list] ?: PocketHorizonScrollAnchor()

    /** Called with the actual visible row, not the transient empty/loading viewport. */
    fun updateAnchor(list: PocketHorizonList, key: String?, index: Int, offset: Int) {
        anchors[list] = PocketHorizonScrollAnchor(key, index.coerceAtLeast(0), offset.coerceAtLeast(0))
    }

    /**
     * Returns a safe position in the supplied list. A missing key in provisional data must not erase
     * the retained restoration target. Automatic viewport observers must likewise avoid recording a
     * provisional fallback as a new anchor until the key returns or the user deliberately scrolls.
     */
    fun resolveAnchor(
        list: PocketHorizonList,
        keys: List<String>,
        authoritative: Boolean
    ): PocketHorizonScrollAnchor {
        val saved = anchor(list)
        if (keys.isEmpty()) {
            if (authoritative) anchors.remove(list)
            return PocketHorizonScrollAnchor()
        }
        val relocated = saved.key?.let(keys::indexOf) ?: -1
        if (relocated >= 0) {
            return saved.copy(index = relocated).also { if (it != saved) anchors[list] = it }
        }
        val index = saved.index.coerceIn(0, keys.lastIndex)
        val resolved = PocketHorizonScrollAnchor(
            key = keys[index],
            index = index,
            offset = if (saved.key == null && index == saved.index) saved.offset else 0
        )
        if (authoritative || saved.key == null) anchors[list] = resolved
        return resolved
    }

    /** Only a completed, error-free library snapshot may prove that a retained context disappeared. */
    fun reconcileLibrary(
        albumKeys: Set<String>,
        artistKeys: Set<String>,
        authoritative: Boolean,
        songKeys: Set<String>? = null
    ) {
        if (!authoritative) return
        if (albumKey?.let { it !in albumKeys } == true) albumKey = null
        if (artistKey?.let { it !in artistKeys } == true) artistKey = null
        if (albumTracksOwnerKey?.let { it !in albumKeys } == true) {
            albumTracksOwnerKey = null
            anchors.remove(PocketHorizonList.AlbumTracks)
        }
        if (artistTracksOwnerKey?.let { it !in artistKeys } == true) {
            artistTracksOwnerKey = null
            anchors.remove(PocketHorizonList.ArtistTracks)
        }
        if (songKeys != null && selectedSongKey?.let { it !in songKeys } == true) selectedSongKey = null
    }

    companion object {
        private const val SavedVersion = 1

        /** Flat primitive values only. List owners distinguish a real empty album key from null. */
        val Saver: Saver<PocketHorizonNavigationState, Any> = Saver(
            save = { state ->
                buildList<Any?> {
                    add(SavedVersion)
                    add(state.category.name)
                    add(state.albumKey)
                    add(state.artistKey)
                    add(state.selectedSongKey)
                    add(state.albumTracksOwnerKey)
                    add(state.artistTracksOwnerKey)
                    PocketHorizonList.entries.forEach { list ->
                        val anchor = state.anchor(list)
                        add(anchor.key)
                        add(anchor.index)
                        add(anchor.offset)
                    }
                }
            },
            restore = { saved ->
                val values = saved as? List<*>
                if (values == null || values.getOrNull(0) != SavedVersion) {
                    PocketHorizonNavigationState()
                } else {
                    PocketHorizonNavigationState(
                        PocketHorizonCategory.entries.firstOrNull { it.name == values.getOrNull(1) }
                            ?: PocketHorizonCategory.NowPlaying
                    ).apply {
                        albumKey = values.getOrNull(2) as? String
                        artistKey = values.getOrNull(3) as? String
                        selectedSongKey = values.getOrNull(4) as? String
                        albumTracksOwnerKey = values.getOrNull(5) as? String ?: albumKey
                        artistTracksOwnerKey = values.getOrNull(6) as? String ?: artistKey
                        PocketHorizonList.entries.forEachIndexed { index, list ->
                            val start = 7 + index * 3
                            updateAnchor(list, values.getOrNull(start) as? String,
                                (values.getOrNull(start + 1) as? Int) ?: 0,
                                (values.getOrNull(start + 2) as? Int) ?: 0)
                        }
                    }
                }
            }
        )
    }
}

@Composable
internal fun rememberPocketHorizonNavigationState(): PocketHorizonNavigationState =
    rememberSaveable(saver = PocketHorizonNavigationState.Saver) { PocketHorizonNavigationState() }
