package com.example.cdplaya.data.importing.spotify

import com.example.cdplaya.data.importing.ImportFileFormat
import kotlinx.serialization.json.JsonObject

internal object SpotifyFormatDetector {
    private val extendedKeys = setOf(
        "ts",
        "ms_played",
        "master_metadata_track_name",
        "master_metadata_album_artist_name",
        "master_metadata_album_album_name",
        "spotify_track_uri",
        "spotify_episode_uri",
        "audiobook_uri",
        "audiobook_chapter_uri",
        "reason_start",
        "reason_end",
        "skipped"
    )
    private val distinctiveExtendedKeys = extendedKeys - setOf("ts", "skipped")
    private val basicKeys = setOf("endTime", "artistName", "trackName", "msPlayed")

    fun detect(element: JsonObject): ImportFileFormat? {
        val keys = element.keys
        val basicEvidence = keys.count { it in basicKeys }
        val extendedEvidence = keys.count { it in extendedKeys }
        return when {
            basicEvidence >= 3 && keys.none { it in distinctiveExtendedKeys } ->
                ImportFileFormat.SPOTIFY_BASIC_ACCOUNT_HISTORY_UNSUPPORTED
            "ts" in keys && "ms_played" in keys ->
                ImportFileFormat.SPOTIFY_EXTENDED_STREAMING_HISTORY
            keys.any { it in distinctiveExtendedKeys } && extendedEvidence >= 2 ->
                ImportFileFormat.SPOTIFY_EXTENDED_STREAMING_HISTORY
            else -> null
        }
    }
}
