package io.github.rsgarrido.sazanami.data

import androidx.annotation.StringRes
import io.github.rsgarrido.sazanami.R

/** Durable preset choices only. Rendering colors and landscape enablement remain separate. */
data class PocketAdvanceAppearance(
    val consoleFinish: PocketAdvanceConsoleFinish = PocketAdvanceConsoleFinish.ORIGINAL_PURPLE,
    val lcdPalette: PocketAdvanceLcdPalette = PocketAdvanceLcdPalette.ORIGINAL,
    val controlsFinish: PocketAdvanceControlsFinish = PocketAdvanceControlsFinish.ORIGINAL_DARK
) {
    companion object { val Default = PocketAdvanceAppearance() }
}

enum class PocketAdvanceConsoleFinish(val id: String, @StringRes val labelRes: Int) {
    ORIGINAL_PURPLE("original_purple", R.string.pocket_advance_console_original_purple),
    GRAPHITE("graphite", R.string.pocket_advance_console_graphite),
    OCEAN_BLUE("ocean_blue", R.string.pocket_advance_console_ocean_blue),
    FOREST_GREEN("forest_green", R.string.pocket_advance_console_forest_green),
    BERRY("berry", R.string.pocket_advance_console_berry);

    companion object {
        fun fromId(id: String?): PocketAdvanceConsoleFinish = entries.firstOrNull { it.id == id } ?: ORIGINAL_PURPLE
    }
}

enum class PocketAdvanceLcdPalette(val id: String, @StringRes val labelRes: Int) {
    ORIGINAL("original", R.string.pocket_advance_lcd_original),
    CLASSIC_GREEN("classic_green", R.string.pocket_advance_lcd_classic_green),
    WARM_AMBER("warm_amber", R.string.pocket_advance_lcd_warm_amber);

    companion object {
        fun fromId(id: String?): PocketAdvanceLcdPalette = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

enum class PocketAdvanceControlsFinish(val id: String, @StringRes val labelRes: Int) {
    ORIGINAL_DARK("original_dark", R.string.pocket_advance_controls_original_dark),
    LIGHT_GRAY("light_gray", R.string.pocket_advance_controls_light_gray),
    DEEP_NAVY("deep_navy", R.string.pocket_advance_controls_deep_navy);

    companion object {
        fun fromId(id: String?): PocketAdvanceControlsFinish = entries.firstOrNull { it.id == id } ?: ORIGINAL_DARK
    }
}
