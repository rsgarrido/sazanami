package io.github.rsgarrido.sazanami.data

import androidx.annotation.StringRes
import io.github.rsgarrido.sazanami.R

/** Optional landscape presentation; never replaces the user's normal PlayerTheme. */
enum class TabletLandscapeTheme(val id: String, @StringRes val labelRes: Int) {
    NORMAL_PLAYER("normal_player", R.string.tablet_landscape_theme_normal_player),
    POCKET_ADVANCE("pocket_advance", R.string.tablet_landscape_theme_pocket_advance);

    companion object {
        fun fromId(id: String?): TabletLandscapeTheme =
            entries.firstOrNull { it.id == id } ?: NORMAL_PLAYER
    }
}
