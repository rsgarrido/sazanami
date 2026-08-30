package io.github.rsgarrido.sazanami.data

enum class PlayerTheme(
    val id: String,
    val displayName: String
) {
    DEFAULT(
        id = "default",
        displayName = "Sazanami Default"
    ),

    CLASSIC_WHEEL(
        id = "classic_wheel",
        displayName = "Classic Wheel"
    ),

    RETRO_RACK(
        id = "retro_rack",
        displayName = "Retro Rack"
    ),

    POCKET_FLIP(
        id = "pocket_flip",
        displayName = "Pocket Flip"
    ),

    POCKET_CASSETTE(
        id = "pocket_cassette",
        displayName = "Pocket Cassette"
    );

    companion object {
        fun fromId(id: String?): PlayerTheme {
            return values().firstOrNull { playerTheme ->
                playerTheme.id == id
            } ?: DEFAULT
        }
    }
}
