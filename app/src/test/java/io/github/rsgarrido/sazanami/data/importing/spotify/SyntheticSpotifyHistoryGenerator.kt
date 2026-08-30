package io.github.rsgarrido.sazanami.data.importing.spotify

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.util.Locale

internal object SyntheticSpotifyHistoryGenerator {
    data class Configuration(
        val recordCount: Int,
        val startIndex: Int = 0,
        val startAt: Instant = Instant.parse("2015-01-01T00:00:00Z"),
        val podcastEvery: Int = 0,
        val invalidEvery: Int = 0
    ) {
        init {
            require(recordCount >= 0)
            require(startIndex >= 0)
            require(podcastEvery >= 0)
            require(invalidEvery >= 0)
        }
    }

    /** Writes directly and leaves [output] open for its caller. */
    fun write(output: OutputStream, configuration: Configuration) {
        val writer = OutputStreamWriter(output, Charsets.UTF_8)
        writer.write("[\n")
        repeat(configuration.recordCount) { offset ->
            if (offset > 0) writer.write(",\n")
            val index = Math.addExact(configuration.startIndex, offset)
            val timestamp = configuration.startAt.plusSeconds(index.toLong())
            val invalid = configuration.invalidEvery > 0 && index % configuration.invalidEvery == 0
            val podcast = !invalid && configuration.podcastEvery > 0 &&
                index % configuration.podcastEvery == 0
            when {
                invalid -> writer.write(
                    "{\"ms_played\":$index," +
                        "\"master_metadata_track_name\":\"Invalid $index\"," +
                        "\"master_metadata_album_artist_name\":\"Generator Lab\"}"
                )
                podcast -> writer.write(
                    "{\"ts\":\"$timestamp\",\"ms_played\":$index," +
                        "\"episode_name\":\"Synthetic Episode $index\"," +
                        "\"episode_show_name\":\"Generated Airwaves\"," +
                        "\"spotify_episode_uri\":\"spotify:episode:${id(index)}\"}"
                )
                else -> writer.write(
                    "{\"ts\":\"$timestamp\",\"ms_played\":$index," +
                        "\"master_metadata_track_name\":\"Synthetic Track $index\"," +
                        "\"master_metadata_album_artist_name\":\"Generator Ensemble\"," +
                        "\"master_metadata_album_album_name\":\"Deterministic Volume ${index / 100}\"," +
                        "\"spotify_track_uri\":\"spotify:track:${id(index)}\"," +
                        "\"reason_start\":\"generated\",\"reason_end\":\"generated\"," +
                        "\"skipped\":${index % 3 == 0}}"
                )
            }
        }
        writer.write("\n]\n")
        writer.flush()
    }

    private fun id(index: Int): String = String.format(Locale.ROOT, "%022d", index)
}
