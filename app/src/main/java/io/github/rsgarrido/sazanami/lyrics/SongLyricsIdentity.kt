package io.github.rsgarrido.sazanami.lyrics

import io.github.rsgarrido.sazanami.data.Song

fun Song.toLyricsIdentity(): SongLyricsIdentity {
    val fileName = displayName.ifBlank {
        filePath.substringAfterLast('/').substringAfterLast('\\')
    }
    return SongLyricsIdentity(
        audioFileName = fileName,
        title = title,
        artist = artist,
        albumArtist = albumArtist,
        relativeDirectory = relativePath,
        fallbackDirectory = folderPath.ifBlank {
            filePath.substringBeforeLast('/', "").substringBeforeLast('\\', "")
        },
        volumeId = volumeName
    )
}
