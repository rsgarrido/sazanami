package io.github.rsgarrido.sazanami.data.importing.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SpotifyExtendedStreamingRecordDto(
    val ts: String? = null,
    @SerialName("ms_played") val msPlayed: Long? = null,
    @SerialName("master_metadata_track_name") val trackName: String? = null,
    @SerialName("master_metadata_album_artist_name") val albumArtistName: String? = null,
    @SerialName("master_metadata_album_album_name") val albumName: String? = null,
    @SerialName("spotify_track_uri") val spotifyTrackUri: String? = null,
    @SerialName("episode_name") val episodeName: String? = null,
    @SerialName("episode_show_name") val episodeShowName: String? = null,
    @SerialName("spotify_episode_uri") val spotifyEpisodeUri: String? = null,
    @SerialName("audiobook_title") val audiobookTitle: String? = null,
    @SerialName("audiobook_uri") val audiobookUri: String? = null,
    @SerialName("audiobook_chapter_uri") val audiobookChapterUri: String? = null,
    @SerialName("audiobook_chapter_title") val audiobookChapterTitle: String? = null,
    @SerialName("video_title") val videoTitle: String? = null,
    @SerialName("spotify_video_uri") val spotifyVideoUri: String? = null,
    @SerialName("reason_start") val reasonStart: String? = null,
    @SerialName("reason_end") val reasonEnd: String? = null,
    val skipped: Boolean? = null
)
