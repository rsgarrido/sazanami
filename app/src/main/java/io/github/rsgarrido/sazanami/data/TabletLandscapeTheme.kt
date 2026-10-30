package io.github.rsgarrido.sazanami.data

import androidx.annotation.StringRes
import io.github.rsgarrido.sazanami.R

/** Persisted landscape choice; registration alone does not make a presentation usable or selectable. */
enum class TabletLandscapeTheme(val id: String, @StringRes val labelRes: Int) {
    NORMAL_PLAYER("normal_player", R.string.tablet_landscape_theme_normal_player),
    POCKET_ADVANCE("pocket_advance", R.string.tablet_landscape_theme_pocket_advance),
    POCKET_HORIZON("pocket_horizon", R.string.tablet_landscape_theme_pocket_horizon);

    companion object {
        /** Only usable themes belong in Settings. Reserved IDs must still survive preference/backup reads. */
        val selectableThemes: List<TabletLandscapeTheme> = listOf(NORMAL_PLAYER, POCKET_ADVANCE)

        fun fromId(id: String?): TabletLandscapeTheme =
            entries.firstOrNull { it.id == id } ?: NORMAL_PLAYER
    }
}
