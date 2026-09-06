package io.github.rsgarrido.sazanami.data.preferences

enum class AppFont(val storageValue: String) {
    SAZANAMI("sazanami"),
    DEVICE("device");

    companion object {
        fun fromStorageValue(value: String?): AppFont =
            entries.firstOrNull { it.storageValue == value } ?: SAZANAMI
    }
}
