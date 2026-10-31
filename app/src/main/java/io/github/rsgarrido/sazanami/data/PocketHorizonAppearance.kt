package io.github.rsgarrido.sazanami.data

import androidx.annotation.StringRes
import io.github.rsgarrido.sazanami.R

data class PocketHorizonAppearance(
    val consoleFinish: PocketHorizonConsoleFinish = PocketHorizonConsoleFinish.GRAPHITE,
    val lcdPalette: PocketHorizonLcdPalette = PocketHorizonLcdPalette.BLUE_VIOLET,
    val waveTreatment: PocketHorizonWaveTreatment = PocketHorizonWaveTreatment.STANDARD
) {
    companion object { val Default = PocketHorizonAppearance() }
}

enum class PocketHorizonConsoleFinish(val id: String, @StringRes val labelRes: Int) {
    GRAPHITE("graphite", R.string.horizon_finish_graphite),
    SILVER("silver", R.string.horizon_finish_silver),
    MIDNIGHT_BLUE("midnight_blue", R.string.horizon_finish_midnight_blue),
    DEEP_BURGUNDY("deep_burgundy", R.string.horizon_finish_deep_burgundy);
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: GRAPHITE }
}

enum class PocketHorizonLcdPalette(val id: String, @StringRes val labelRes: Int) {
    BLUE_VIOLET("blue_violet", R.string.horizon_lcd_blue_violet),
    EMERALD_TEAL("emerald_teal", R.string.horizon_lcd_emerald_teal),
    WARM_AMBER("warm_amber", R.string.horizon_lcd_warm_amber);
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: BLUE_VIOLET }
}

enum class PocketHorizonWaveTreatment(val id: String, @StringRes val labelRes: Int) {
    STANDARD("standard", R.string.horizon_wave_standard),
    SUBTLE("subtle", R.string.horizon_wave_subtle),
    OFF("off", R.string.horizon_wave_off);
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: STANDARD }
}
