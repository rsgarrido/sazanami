package io.github.rsgarrido.sazanami.lyrics

import io.github.rsgarrido.sazanami.data.MetadataTagIO
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagTextField
import org.jaudiotagger.tag.aiff.AiffTag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.jaudiotagger.tag.wav.WavTag
import kotlinx.coroutines.CancellationException

data class EmbeddedLyricsCandidate(val source: LyricsSource.Embedded, val text: String)
data class EmbeddedLyricsReadResult(
    val candidates: List<EmbeddedLyricsCandidate> = emptyList(),
    val issues: List<LyricsSourceIssue> = emptyList(),
    val selectionComplete: Boolean = true
)

fun interface EmbeddedLyricsReader {
    suspend fun read(song: SongLyricsIdentity): EmbeddedLyricsReadResult
}

/** Reads existing tags only; never constructs AudioMetadata or requests/decodes artwork. */
class FileEmbeddedLyricsReader {
    fun read(
        file: File,
        trackKey: String,
        extension: String = file.extension,
        diagnostics: EmbeddedLyricsDiagnostics = EmbeddedLyricsDiagnostics.NONE,
        checkCancelled: () -> Unit = {}
    ): EmbeddedLyricsReadResult {
        val format = extension.lowercase(Locale.ROOT)
        if (format !in SUPPORTED_EXTENSIONS) return failure(trackKey, LyricsSourceIssue.Kind.UNSUPPORTED_FORMAT)
        var stage = EmbeddedLyricsReadStage.FILE_OPEN
        fun failed(kind: LyricsSourceIssue.Kind, cause: Throwable): EmbeddedLyricsReadResult {
            diagnostics.onFailure(stage, cause)
            return failure(trackKey, kind)
        }
        return try {
            checkCancelled()
            LyricsMetadataPreflight.check(file, format, checkCancelled) {
                stage = EmbeddedLyricsReadStage.METADATA_PREFLIGHT
            }
            stage = EmbeddedLyricsReadStage.CONTAINER_PARSING
            val tag = MetadataTagIO.readTag(file, format)
            checkCancelled()
            stage = EmbeddedLyricsReadStage.LYRICS_EXTRACTION
            extract(tag, trackKey, checkCancelled)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cause: LyricsInputLimitException) {
            failed(LyricsSourceIssue.Kind.INPUT_LIMIT, cause)
        } catch (cause: SecurityException) {
            failed(LyricsSourceIssue.Kind.PERMISSION_DENIED, cause)
        } catch (cause: java.nio.file.AccessDeniedException) {
            failed(LyricsSourceIssue.Kind.PERMISSION_DENIED, cause)
        } catch (cause: org.jaudiotagger.audio.exceptions.NoReadPermissionsException) {
            failed(LyricsSourceIssue.Kind.PERMISSION_DENIED, cause)
        } catch (cause: FileNotFoundException) {
            failed(LyricsSourceIssue.Kind.READ_FAILURE, cause)
        } catch (cause: java.io.IOException) {
            failed(LyricsSourceIssue.Kind.READ_FAILURE, cause)
        } catch (cause: Exception) {
            failed(LyricsSourceIssue.Kind.METADATA_FAILURE, cause)
        } catch (cause: LinkageError) {
            failed(LyricsSourceIssue.Kind.METADATA_FAILURE, cause)
        }
    }

    internal fun extract(tag: Tag?, trackKey: String, checkCancelled: () -> Unit = {}): EmbeddedLyricsReadResult {
        val actual = when (tag) {
            is WavTag -> if (tag.isExistingId3Tag()) tag.getID3Tag() else null
            is AiffTag -> if (tag.isExistingId3Tag()) tag.getID3Tag() else null
            is FlacTag -> tag.vorbisCommentTag
            else -> tag
        }
        val candidates = mutableListOf<EmbeddedLyricsCandidate>()
        val representation = when (actual) {
            is AbstractID3v2Tag -> EmbeddedLyricsRepresentation.ID3_USLT
            is VorbisCommentTag -> EmbeddedLyricsRepresentation.VORBIS_COMMENT
            else -> EmbeddedLyricsRepresentation.MP4_ATOM
        }
        val issues = mutableListOf<LyricsSourceIssue>()
        val ordinals = mutableMapOf<Triple<String, String?, String?>, Int>()
        var examined = 0
        var characters = 0L
        var selectionComplete = true
        fun add(field: String, text: String, language: String? = null, description: String? = null, textEncoding: Int? = null) {
            checkCancelled()
            if (++examined > LyricsLimits.CANDIDATES) selectionComplete = false
            if (examined > LyricsLimits.CANDIDATES || text.length > LyricsLimits.SOURCE_CHARACTERS ||
                characters + text.length > LyricsLimits.SOURCE_CHARACTERS || field.length > 256 ||
                (language?.length ?: 0) > 32 || (description?.length ?: 0) > 4096) {
                if (issues.none { it.kind == LyricsSourceIssue.Kind.INPUT_LIMIT }) {
                    issues += LyricsSourceIssue(trackKey, LyricsSourceIssue.Kind.INPUT_LIMIT)
                }
                return
            }
            characters += text.length
            if (text.isBlank()) return
            val key = Triple(field, language, description)
            val ordinal = ordinals[key] ?: 0
            ordinals[key] = ordinal + 1
            val source = LyricsSource.Embedded(trackKey, field, language, description, ordinal, representation, textEncoding)
            candidates += EmbeddedLyricsCandidate(source, text)
        }
        when (actual) {
            is AbstractID3v2Tag -> {
                actual.getFields(FieldKey.LYRICS).take(LyricsLimits.CANDIDATES + 1).forEach { field ->
                    checkCancelled()
                    val body = (field as? AbstractID3v2Frame)?.body as? FrameBodyUSLT
                    if (body != null) add(field.id, body.lyric.orEmpty(), body.language, body.description, body.textEncoding.toInt())
                    else issues += LyricsSourceIssue(trackKey, LyricsSourceIssue.Kind.METADATA_FAILURE)
                }
                // Binary SYLT/SLT deliberately remains separate from USLT/LRC text.
                if (actual.getFields("SYLT").isNotEmpty() || actual.getFields("SLT").isNotEmpty()) {
                    issues += LyricsSourceIssue(trackKey, LyricsSourceIssue.Kind.UNSUPPORTED_TIMING)
                }
            }
            is org.jaudiotagger.tag.id3.ID3v1Tag -> Unit
            is VorbisCommentTag -> listOf("LYRICS", "UNSYNCEDLYRICS").forEach { key ->
                actual.getFields(key).take(LyricsLimits.CANDIDATES + 1).forEach { field ->
                    (field as? TagTextField)?.let { add(key, it.content.orEmpty()) }
                        ?: issues.add(LyricsSourceIssue(trackKey, LyricsSourceIssue.Kind.INVALID_LYRICS))
                }
            }
            null -> Unit
            else -> actual.getFields(FieldKey.LYRICS).take(LyricsLimits.CANDIDATES + 1).forEach { field ->
                (field as? TagTextField)?.let { add(field.id, it.content.orEmpty()) }
                    ?: issues.add(LyricsSourceIssue(trackKey, LyricsSourceIssue.Kind.INVALID_LYRICS))
            }
        }
        if (candidates.isEmpty() && issues.isEmpty()) issues += LyricsSourceIssue(trackKey, LyricsSourceIssue.Kind.NOT_FOUND)
        return EmbeddedLyricsReadResult(candidates.toList(), issues.toList(), selectionComplete)
    }

    companion object {
        val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "ogg", "oga", "m4a", "mp4", "m4b", "m4p", "wav", "aif", "aiff", "aifc")
        internal fun failure(trackKey: String, kind: LyricsSourceIssue.Kind) =
            EmbeddedLyricsReadResult(issues = listOf(LyricsSourceIssue(trackKey, kind)))
    }
}
