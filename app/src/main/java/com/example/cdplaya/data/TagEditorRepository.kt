package com.example.cdplaya.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.wav.WavOptions
import org.jaudiotagger.audio.wav.WavSaveOptions
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.wav.WavTag
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.max
import com.example.cdplaya.player.replaygain.ReplayGainInfo
import com.example.cdplaya.player.replaygain.parseReplayGainDb
import com.example.cdplaya.player.replaygain.parseReplayGainPeak
import org.jaudiotagger.tag.TagField
import org.jaudiotagger.tag.TagTextField

class TagEditorRepository {

    private val metadataReader = EmbeddedMetadataReader()

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
        TagOptionSingleton.getInstance().apply {
            setWavOptions(WavOptions.READ_ID3_UNLESS_ONLY_INFO)
            setWavSaveOptions(WavSaveOptions.SAVE_BOTH)
        }
    }

    fun readTags(song: Song): EditableSongTags {
        val file = File(song.filePath)

        if (!file.exists()) {
            return AudioMetadata().toEditableSongTags(song)
        }

        val result = metadataReader.readOrNull(file)
        return result?.metadata?.toEditableSongTags(
            song = song,
            capabilities = result.format.editorCapabilities()
        ) ?: AudioMetadata().toEditableSongTags(
            song = song,
            capabilities = file.extension.toAudioMetadataFormat().editorCapabilities()
        )
    }

    fun readReplayGainTags(song: Song): ReplayGainInfo {
        val file = File(song.filePath)

        if (!file.exists()) {
            return emptyReplayGainInfo()
        }

        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return emptyReplayGainInfo()

            ReplayGainInfo(
                trackGainDb = parseReplayGainDb(
                    readReplayGainTagValue(
                        tag = tag,
                        possibleFieldNames = replayGainTrackGainFieldNames
                    )
                ),
                trackPeak = parseReplayGainPeak(
                    readReplayGainTagValue(
                        tag = tag,
                        possibleFieldNames = replayGainTrackPeakFieldNames
                    )
                ),
                albumGainDb = parseReplayGainDb(
                    readReplayGainTagValue(
                        tag = tag,
                        possibleFieldNames = replayGainAlbumGainFieldNames
                    )
                ),
                albumPeak = parseReplayGainPeak(
                    readReplayGainTagValue(
                        tag = tag,
                        possibleFieldNames = replayGainAlbumPeakFieldNames
                    )
                )
            )
        } catch (exception: Exception) {
            emptyReplayGainInfo()
        } catch (error: LinkageError) {
            emptyReplayGainInfo()
        }
    }

    fun writeTags(
        song: Song,
        editedTags: EditableSongTags
    ): TagEditorResult {
        val file = File(song.filePath)

        if (!file.exists()) {
            return TagEditorResult(
                wasSuccessful = false,
                message = "The audio file could not be found."
            )
        }

        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            val originalTags = metadataForTag(tag).toEditableSongTags(song)
            val edits = editedTags.changedFieldsFrom(originalTags)

            metadataEditValidationMessage(edits)?.let { message ->
                return TagEditorResult(wasSuccessful = false, message = message)
            }

            if (edits.isEmpty()) {
                return TagEditorResult(
                    wasSuccessful = true,
                    message = "No metadata changes to save."
                )
            }

            applyMetadataTextEdits(
                tag = tag,
                edits = edits
            )

            AudioFileIO.write(audioFile)

            if (!writtenTextEditsMatch(file, edits)) {
                return TagEditorResult(
                    wasSuccessful = false,
                    message = "Tags were written but could not be verified by reading the file back."
                )
            }

            TagEditorResult(
                wasSuccessful = true,
                message = "Tags saved successfully."
            )
        } catch (exception: Exception) {
            TagEditorResult(
                wasSuccessful = false,
                message = exception.message ?: "Could not save tags."
            )
        } catch (error: LinkageError) {
            TagEditorResult(
                wasSuccessful = false,
                message = error.message ?: "Could not save tags on this Android device."
            )
        }
    }

    fun writeTagsAndArtwork(
        context: Context,
        song: Song,
        editedTags: EditableSongTags,
        artworkUri: Uri?
    ): TagEditorResult {
        val audioFileOnDisk = File(song.filePath)

        if (!audioFileOnDisk.exists()) {
            return TagEditorResult(
                wasSuccessful = false,
                message = "The audio file could not be found."
            )
        }

        var temporaryArtworkFile: File? = null
        var selectedArtworkHash: String? = null

        return try {
            val audioFile = AudioFileIO.read(audioFileOnDisk)
            val tag = audioFile.tagOrCreateAndSetDefault
            val originalTags = metadataForTag(tag).toEditableSongTags(song)
            val edits = editedTags.changedFieldsFrom(originalTags)

            metadataEditValidationMessage(edits)?.let { message ->
                return TagEditorResult(wasSuccessful = false, message = message)
            }

            if (edits.isEmpty() && artworkUri == null) {
                return TagEditorResult(
                    wasSuccessful = true,
                    message = "No metadata changes to save."
                )
            }

            applyMetadataTextEdits(
                tag = tag,
                edits = edits
            )

            if (artworkUri != null) {
                val optimizedArtwork = createOptimizedArtworkImageData(
                    context = context,
                    artworkUri = artworkUri
                ) ?: return TagEditorResult(
                    wasSuccessful = false,
                    message = "The selected artwork could not be read."
                )
                selectedArtworkHash = optimizedArtwork.bytes.sha256()

                if (tag is FlacTag) {
                    setFlacArtwork(
                        flacTag = tag,
                        artworkImageData = optimizedArtwork
                    )
                } else {
                    temporaryArtworkFile = createTemporaryArtworkFile(
                        context = context,
                        artworkImageData = optimizedArtwork
                    )

                    if (temporaryArtworkFile == null || !temporaryArtworkFile.exists()) {
                        return TagEditorResult(
                            wasSuccessful = false,
                            message = "The selected artwork could not be prepared."
                        )
                    }

                    val artwork = ArtworkFactory.createArtworkFromFile(temporaryArtworkFile)
                    val artworkTag = if (tag is WavTag) tag.getID3Tag() else tag

                    deleteExistingArtwork(artworkTag)

                    artworkTag.setField(artwork)
                }
            }

            AudioFileIO.write(audioFile)

            if (!writtenTextEditsMatch(audioFileOnDisk, edits)) {
                return TagEditorResult(
                    wasSuccessful = false,
                    message = "Tags were written but could not be verified by reading the file back."
                )
            }

            val expectedArtworkHash = selectedArtworkHash
            if (expectedArtworkHash != null) {
                val artworkWasSaved = artworkMatches(
                    audioFileOnDisk = audioFileOnDisk,
                    expectedArtworkHash = expectedArtworkHash
                )

                if (!artworkWasSaved) {
                    return TagEditorResult(
                        wasSuccessful = false,
                        message = "Artwork could not be verified after saving."
                    )
                }
            }

            TagEditorResult(
                wasSuccessful = true,
                message = if (artworkUri == null) {
                    "Tags saved successfully."
                } else {
                    "Tags and artwork saved successfully."
                }
            )
        } catch (exception: Exception) {
            TagEditorResult(
                wasSuccessful = false,
                message = exception.message ?: "Could not save tags and artwork."
            )
        } catch (error: LinkageError) {
            TagEditorResult(
                wasSuccessful = false,
                message = error.message ?: "Could not save artwork on this Android device."
            )
        } finally {
            temporaryArtworkFile?.delete()
        }
    }

    fun getUnsupportedEditingMessage(song: Song): String? {
        val file = File(song.filePath)

        if (!file.exists()) {
            return "The audio file could not be found."
        }

        val extension = file.extension.lowercase()

        if (extension.isBlank()) {
            return "This file does not have a recognizable audio extension."
        }

        if (extension !in metadataWritableExtensions) {
            return "Tag editing is not enabled for .$extension files yet."
        }

        return null
    }

    private fun metadataForTag(tag: Tag): AudioMetadata = if (tag is WavTag) {
        readWavMetadata(tag).metadata
    } else {
        tag.toAudioMetadata()
    }

    private fun metadataEditValidationMessage(
        edits: Map<FieldKey, MetadataTextEdit>
    ): String? {
        val bpmEdit = edits[FieldKey.BPM] ?: return null
        if (bpmEdit.isClear) return null
        val bpm = bpmEdit.values.singleOrNull()
        return if (bpm != null && bpm.isValidMetadataBpm()) {
            null
        } else {
            "BPM must be a whole number from 1 to 999."
        }
    }

    internal fun prepareBatchArtwork(
        context: Context,
        artworkUri: Uri
    ): PreparedBatchArtwork? = createOptimizedArtworkImageData(context, artworkUri)?.let { data ->
        PreparedBatchArtwork(
            bytes = data.bytes.copyOf(),
            mimeType = data.mimeType,
            width = data.width,
            height = data.height,
            hash = data.bytes.sha256()
        )
    }

    internal fun writeExplicitMetadataPatch(
        context: Context,
        song: Song,
        edits: Map<FieldKey, MetadataTextEdit>,
        artworkEdit: BatchArtworkExecutionEdit
    ): ExplicitMetadataPatchResult {
        val file = File(song.filePath)
        if (!file.isFile) {
            return ExplicitMetadataPatchResult(
                false,
                "The audio file could not be found.",
                ExplicitPatchFailureKind.WRITE
            )
        }
        var temporaryArtworkFile: File? = null
        return try {
            metadataEditValidationMessage(edits)?.let { message ->
                return ExplicitMetadataPatchResult(
                    false,
                    message,
                    ExplicitPatchFailureKind.WRITE
                )
            }
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            applyMetadataTextEdits(tag, edits)
            val artworkTag = if (tag is WavTag) tag.getID3Tag() else tag
            when (artworkEdit) {
                BatchArtworkExecutionEdit.Untouched -> Unit
                BatchArtworkExecutionEdit.Clear -> deleteExistingArtwork(artworkTag)
                is BatchArtworkExecutionEdit.Replace -> {
                    val prepared = artworkEdit.artwork
                    val data = ArtworkImageData(
                        prepared.bytes,
                        prepared.mimeType,
                        prepared.width,
                        prepared.height
                    )
                    if (tag is FlacTag) {
                        setFlacArtwork(tag, data)
                    } else {
                        val temporaryFile = createTemporaryArtworkFile(context, data)
                        temporaryArtworkFile = temporaryFile
                        val artwork = ArtworkFactory.createArtworkFromFile(temporaryFile)
                        deleteExistingArtwork(artworkTag)
                        artworkTag.setField(artwork)
                    }
                }
            }
            AudioFileIO.write(audioFile)
            if (!writtenTextEditsMatch(file, edits)) {
                return ExplicitMetadataPatchResult(
                    false,
                    "Metadata was written but could not be verified.",
                    ExplicitPatchFailureKind.VERIFICATION
                )
            }
            val artworkVerified = when (artworkEdit) {
                BatchArtworkExecutionEdit.Untouched -> true
                BatchArtworkExecutionEdit.Clear -> artworkIsAbsent(file)
                is BatchArtworkExecutionEdit.Replace ->
                    artworkMatches(file, artworkEdit.artwork.hash)
            }
            if (!artworkVerified) {
                ExplicitMetadataPatchResult(
                    false,
                    "Artwork was written but could not be verified.",
                    ExplicitPatchFailureKind.VERIFICATION
                )
            } else {
                ExplicitMetadataPatchResult(true, "Metadata saved and verified.")
            }
        } catch (exception: Exception) {
            ExplicitMetadataPatchResult(
                false,
                exception.message ?: "Could not save metadata.",
                ExplicitPatchFailureKind.WRITE
            )
        } catch (error: LinkageError) {
            ExplicitMetadataPatchResult(
                false,
                error.message ?: "Could not save metadata on this device.",
                ExplicitPatchFailureKind.WRITE
            )
        } finally {
            temporaryArtworkFile?.delete()
        }
    }

    private fun writtenTextEditsMatch(
        file: File,
        edits: Map<FieldKey, MetadataTextEdit>
    ): Boolean {
        if (edits.isEmpty()) return true

        return try {
            val writtenTag = AudioFileIO.read(file).tag ?: return false
            edits.all { (fieldKey, edit) ->
                if (writtenTag is WavTag) {
                    tagValuesMatch(writtenTag.getID3Tag(), fieldKey, edit.values) &&
                        (fieldKey !in wavInfoFieldKeys ||
                            tagValuesMatch(writtenTag.getInfoTag(), fieldKey, edit.values))
                } else {
                    tagValuesMatch(writtenTag, fieldKey, edit.values)
                }
            }
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    private fun tagValuesMatch(
        tag: Tag,
        fieldKey: FieldKey,
        expectedValues: List<String>
    ): Boolean {
        val rawValues = try {
            tag.getAll(fieldKey).map { value -> value.trim().trimEnd('\u0000').trim() }
        } catch (_: Exception) {
            emptyList()
        }
        if (expectedValues.isEmpty()) {
            return runCatching { !tag.hasField(fieldKey) }.getOrDefault(rawValues.isEmpty())
        }
        if (expectedValues.all(String::isEmpty)) {
            return runCatching { tag.hasField(fieldKey) }.getOrDefault(false) &&
                rawValues == expectedValues
        }
        return rawValues.filter(String::isNotEmpty) == expectedValues
    }

    private fun readReplayGainTagValue(
        tag: Tag,
        possibleFieldNames: List<String>
    ): String? {
        val directValue = readReplayGainDirectTagValue(
            tag = tag,
            possibleFieldNames = possibleFieldNames
        )

        if (directValue != null) {
            return directValue
        }

        return readReplayGainRawTagValue(
            tag = tag,
            possibleFieldNames = possibleFieldNames
        )
    }

    private fun readReplayGainDirectTagValue(
        tag: Tag,
        possibleFieldNames: List<String>
    ): String? {
        possibleFieldNames.forEach { fieldName ->
            val directValue = try {
                tag.getFirst(fieldName)
            } catch (exception: Exception) {
                ""
            }

            if (directValue.isNotBlank()) {
                return directValue
            }
        }

        return null
    }

    private fun readReplayGainRawTagValue(
        tag: Tag,
        possibleFieldNames: List<String>
    ): String? {
        val normalizedFieldNames = possibleFieldNames.map { fieldName ->
            fieldName.uppercase()
        }

        val fields = tag.getFields()

        while (fields.hasNext()) {
            val field = fields.next()
            val fieldId = field.id.orEmpty()
            val fieldContent = field.readReplayGainFieldContent()

            val combinedText = "$fieldId $fieldContent".uppercase()

            val matchingFieldName = normalizedFieldNames.firstOrNull { fieldName ->
                combinedText.contains(fieldName)
            }

            if (matchingFieldName != null && fieldContent.isNotBlank()) {
                return fieldContent
            }
        }

        return null
    }

    private fun TagField.readReplayGainFieldContent(): String {
        return if (this is TagTextField) {
            content.orEmpty()
        } else {
            toString()
        }
    }

    private fun emptyReplayGainInfo(): ReplayGainInfo {
        return ReplayGainInfo(
            trackGainDb = null,
            trackPeak = null,
            albumGainDb = null,
            albumPeak = null
        )
    }

    private fun setFlacArtwork(
        flacTag: FlacTag,
        artworkImageData: ArtworkImageData
    ) {
        deleteExistingArtwork(flacTag)

        val artworkField = flacTag.createArtworkField(
            artworkImageData.bytes,
            FRONT_COVER_PICTURE_TYPE,
            artworkImageData.mimeType,
            "Cover",
            artworkImageData.width,
            artworkImageData.height,
            DEFAULT_COLOUR_DEPTH,
            DEFAULT_INDEXED_COLOUR_COUNT
        )

        flacTag.addField(artworkField)
    }

    private fun deleteExistingArtwork(tag: Tag) {
        try {
            tag.deleteArtworkField()
        } catch (exception: Exception) {
        }
    }

    private fun createOptimizedArtworkImageData(
        context: Context,
        artworkUri: Uri
    ): ArtworkImageData? {
        val originalBytes = context.contentResolver.openInputStream(artworkUri)
            ?.use { inputStream ->
                inputStream.readBytes()
            }
            ?: return null

        if (originalBytes.isEmpty()) {
            return null
        }

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeByteArray(
            originalBytes,
            0,
            originalBytes.size,
            boundsOptions
        )

        val originalWidth = boundsOptions.outWidth
        val originalHeight = boundsOptions.outHeight

        if (originalWidth <= 0 || originalHeight <= 0) {
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                width = originalWidth,
                height = originalHeight,
                maxSize = MAX_ARTWORK_SIZE_PX
            )
        }

        val decodedBitmap = BitmapFactory.decodeByteArray(
            originalBytes,
            0,
            originalBytes.size,
            decodeOptions
        ) ?: return null

        val scaledBitmap = scaleBitmapIfNeeded(
            bitmap = decodedBitmap,
            maxSize = MAX_ARTWORK_SIZE_PX
        )

        if (scaledBitmap !== decodedBitmap) {
            decodedBitmap.recycle()
        }

        val outputStream = ByteArrayOutputStream()

        scaledBitmap.compress(
            Bitmap.CompressFormat.JPEG,
            ARTWORK_JPEG_QUALITY,
            outputStream
        )

        val optimizedBytes = outputStream.toByteArray()

        val width = scaledBitmap.width
        val height = scaledBitmap.height

        scaledBitmap.recycle()

        if (optimizedBytes.isEmpty()) {
            return null
        }

        return ArtworkImageData(
            bytes = optimizedBytes,
            mimeType = OPTIMIZED_ARTWORK_MIME_TYPE,
            width = width,
            height = height
        )
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxSize: Int
    ): Int {
        var sampleSize = 1

        var sampledWidth = width
        var sampledHeight = height

        while (sampledWidth / 2 >= maxSize || sampledHeight / 2 >= maxSize) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }

        return sampleSize
    }

    private fun scaleBitmapIfNeeded(
        bitmap: Bitmap,
        maxSize: Int
    ): Bitmap {
        val largestSide = max(bitmap.width, bitmap.height)

        if (largestSide <= maxSize) {
            return bitmap
        }

        val scale = maxSize.toFloat() / largestSide.toFloat()
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(
            bitmap,
            newWidth,
            newHeight,
            true
        )
    }

    private fun createTemporaryArtworkFile(
        context: Context,
        artworkImageData: ArtworkImageData
    ): File {
        val temporaryArtworkFile = File.createTempFile(
            "selected_artwork_",
            ".jpg",
            context.cacheDir
        )

        temporaryArtworkFile.writeBytes(artworkImageData.bytes)

        return temporaryArtworkFile
    }

    private fun artworkIsAbsent(audioFileOnDisk: File): Boolean {
        return try {
            val tag = AudioFileIO.read(audioFileOnDisk).tag ?: return true
            val artworkTag = if (tag is WavTag) tag.getID3Tag() else tag
            artworkTag.artworkList.isEmpty()
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    private fun artworkMatches(
        audioFileOnDisk: File,
        expectedArtworkHash: String
    ): Boolean {
        return try {
            val updatedAudioFile = AudioFileIO.read(audioFileOnDisk)
            val updatedTag = updatedAudioFile.tag ?: return false

            updatedTag.artworkList.any { artwork ->
                val artworkBytes = artwork.binaryData

                artworkBytes != null &&
                        artworkBytes.isNotEmpty() &&
                        artworkBytes.sha256() == expectedArtworkHash
            }
        } catch (exception: Exception) {
            false
        } catch (error: LinkageError) {
            false
        }
    }

    private fun ByteArray.sha256(): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(this)

        return bytes.joinToString("") { byte ->
            "%02x".format(byte)
        }
    }

    private data class ArtworkImageData(
        val bytes: ByteArray,
        val mimeType: String,
        val width: Int,
        val height: Int
    )

    companion object {
        private const val FRONT_COVER_PICTURE_TYPE = 3
        private const val DEFAULT_COLOUR_DEPTH = 24
        private const val DEFAULT_INDEXED_COLOUR_COUNT = 0

        private const val MAX_ARTWORK_SIZE_PX = 1000
        private const val ARTWORK_JPEG_QUALITY = 90
        private const val OPTIMIZED_ARTWORK_MIME_TYPE = "image/jpeg"

        private val replayGainTrackGainFieldNames = listOf(
            "REPLAYGAIN_TRACK_GAIN",
            "replaygain_track_gain"
        )

        private val replayGainTrackPeakFieldNames = listOf(
            "REPLAYGAIN_TRACK_PEAK",
            "replaygain_track_peak"
        )

        private val replayGainAlbumGainFieldNames = listOf(
            "REPLAYGAIN_ALBUM_GAIN",
            "replaygain_album_gain"
        )

        private val replayGainAlbumPeakFieldNames = listOf(
            "REPLAYGAIN_ALBUM_PEAK",
            "replaygain_album_peak"
        )
    }
}

internal val metadataWritableExtensions = setOf(
    "mp3",
    "flac",
    "m4a",
    "mp4",
    "ogg",
    "wav",
    "aif",
    "aiff"
)

internal fun applyMetadataTextEdits(
    tag: Tag,
    edits: Map<FieldKey, MetadataTextEdit>
) {
    if (tag is WavTag) {
        // An explicit WAV edit intentionally updates both common representations when the field
        // exists in INFO. Only requested fields are synchronized; unrelated conflicts survive.
        edits.forEach { (fieldKey, value) ->
            applyMetadataTextEdit(tag.getID3Tag(), fieldKey, value)
            if (fieldKey in wavInfoFieldKeys) {
                applyMetadataTextEdit(tag.getInfoTag(), fieldKey, value)
            }
        }
    } else {
        edits.forEach { (fieldKey, value) ->
            applyMetadataTextEdit(tag, fieldKey, value)
        }
    }

}

private fun applyMetadataTextEdit(tag: Tag, fieldKey: FieldKey, edit: MetadataTextEdit) {
    if (edit.isClear) {
        tag.deleteField(fieldKey)
        return
    }

    if (fieldKey !in multiValueFieldKeys) {
        tag.deleteField(fieldKey)
        tag.setField(fieldKey, edit.values.single())
        return
    }

    tag.deleteField(fieldKey)
    tag.setField(fieldKey, edit.values.first())
    edit.values.drop(1).forEach { value ->
        tag.addField(fieldKey, value)
    }
}

private val multiValueFieldKeys = setOf(
    FieldKey.ARTIST,
    FieldKey.ALBUM_ARTIST,
    FieldKey.GENRE,
    FieldKey.COMPOSER
)

/** FieldKey coverage implemented by jaudiotagger's WavInfoTag in 3.0.1. */
internal val wavInfoFieldKeys = setOf(
    FieldKey.ALBUM,
    FieldKey.ARTIST,
    FieldKey.ALBUM_ARTIST,
    FieldKey.TITLE,
    FieldKey.TRACK,
    FieldKey.GENRE,
    FieldKey.COMMENT,
    FieldKey.YEAR,
    FieldKey.RECORD_LABEL,
    FieldKey.ISRC,
    FieldKey.COMPOSER,
    FieldKey.LYRICIST,
    FieldKey.ENCODER,
    FieldKey.CONDUCTOR,
    FieldKey.RATING,
    FieldKey.COPYRIGHT
)
