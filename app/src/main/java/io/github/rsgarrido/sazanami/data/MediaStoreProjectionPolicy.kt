package io.github.rsgarrido.sazanami.data

internal object MediaStoreProjectionPolicy {
    private const val ANDROID_10_API = 29

    const val ID = "_id"
    const val TITLE = "title"
    const val ARTIST = "artist"
    const val ALBUM = "album"
    const val TRACK = "track"
    const val DURATION = "duration"
    const val DATA = "_data"
    const val DISPLAY_NAME = "_display_name"
    const val SIZE = "_size"
    const val DATE_ADDED = "date_added"
    const val DATE_MODIFIED = "date_modified"
    const val YEAR = "year"
    const val VOLUME_NAME = "volume_name"
    const val RELATIVE_PATH = "relative_path"

    fun audioProjection(sdkInt: Int): List<String> = buildList {
        add(ID)
        add(TITLE)
        add(ARTIST)
        add(ALBUM)
        add(TRACK)
        add(DURATION)
        add(DATA)
        add(DISPLAY_NAME)
        add(SIZE)
        add(DATE_ADDED)
        add(DATE_MODIFIED)
        add(YEAR)
        if (sdkInt >= ANDROID_10_API) {
            add(VOLUME_NAME)
            add(RELATIVE_PATH)
        }
    }

    fun imageProjection(sdkInt: Int): List<String> = buildList {
        add(ID)
        add(DATA)
        add(DISPLAY_NAME)
        if (sdkInt >= ANDROID_10_API) {
            add(VOLUME_NAME)
            add(RELATIVE_PATH)
        }
    }
}
