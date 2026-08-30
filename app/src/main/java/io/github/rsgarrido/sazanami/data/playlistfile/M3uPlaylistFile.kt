package io.github.rsgarrido.sazanami.data.playlistfile

import io.github.rsgarrido.sazanami.data.Song
import kotlin.math.max

data class M3uEntry(
    val location: String,
    val durationSeconds: Int? = null,
    val displayTitle: String? = null
)

object M3uPlaylistFile {
    fun parseM3uLines(lines: List<String>): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
        var pendingExtInf: M3uExtInf? = null

        lines.forEach { rawLine ->
            val line = rawLine.trim()

            when {
                line.isBlank() -> {
                }

                line.equals("#EXTM3U", ignoreCase = true) -> {
                }

                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pendingExtInf = parseExtInf(line)
                }

                line.startsWith("#") -> {
                }

                else -> {
                    entries.add(
                        M3uEntry(
                            location = line,
                            durationSeconds = pendingExtInf?.durationSeconds,
                            displayTitle = pendingExtInf?.displayTitle
                        )
                    )

                    pendingExtInf = null
                }
            }
        }

        return entries
    }

    fun buildM3uText(songs: List<Song>): String {
        return buildString {
            appendLine("#EXTM3U")

            songs.forEach { song ->
                val durationSeconds = max(0, (song.duration / 1000L).toInt())
                val displayTitle = buildExtInfDisplayTitle(song)

                appendLine("#EXTINF:$durationSeconds,$displayTitle")
                appendLine(song.filePath.ifBlank { song.uri.toString() })
            }
        }
    }

    private fun parseExtInf(line: String): M3uExtInf {
        val value = line.substringAfter("#EXTINF:", missingDelimiterValue = "")
        val durationText = value.substringBefore(",", missingDelimiterValue = value).trim()
        val displayTitle = value.substringAfter(",", missingDelimiterValue = "").trim()

        return M3uExtInf(
            durationSeconds = durationText.toIntOrNull(),
            displayTitle = displayTitle.takeIf { title ->
                title.isNotBlank()
            }
        )
    }

    private fun buildExtInfDisplayTitle(song: Song): String {
        val artist = song.artist.trim()
        val title = song.title.trim()

        return when {
            artist.isNotBlank() && title.isNotBlank() -> "$artist - $title"
            title.isNotBlank() -> title
            artist.isNotBlank() -> artist
            else -> song.filePath.substringAfterLast("/")
        }
    }
}

private data class M3uExtInf(
    val durationSeconds: Int?,
    val displayTitle: String?
)