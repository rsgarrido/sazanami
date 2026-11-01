package io.github.rsgarrido.sazanami.lyrics

import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.songIdentity
import io.github.rsgarrido.sazanami.data.toSongReference

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
        volumeId = volumeName,
        audioFilePath = filePath,
        trackKey = lyricsIdentity(songIdentity().localKey.orEmpty(),
            songIdentity().sourceKey.orEmpty(), uri.toString(), filePath),
        reference = toSongReference()
    )
}
