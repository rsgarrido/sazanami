package com.example.cdplaya.data

import org.jaudiotagger.tag.FieldKey

enum class BatchMetadataField(
    val label: String,
    val isMultiValue: Boolean,
    internal val fieldKey: FieldKey,
    internal val requiredCapability: EditableMetadataField
) {
    ALBUM("Album", false, FieldKey.ALBUM, EditableMetadataField.ALBUM),
    ALBUM_ARTIST(
        "Album Artist",
        true,
        FieldKey.ALBUM_ARTIST,
        EditableMetadataField.ALBUM_ARTIST
    ),
    DATE("Date / year", false, FieldKey.YEAR, EditableMetadataField.DATE),
    GENRE("Genre", true, FieldKey.GENRE, EditableMetadataField.GENRE),
    COMPOSER("Composer", true, FieldKey.COMPOSER, EditableMetadataField.COMPOSER),
    COMMENT("Comment", false, FieldKey.COMMENT, EditableMetadataField.COMMENT),
    PUBLISHER(
        "Publisher / Label",
        false,
        FieldKey.RECORD_LABEL,
        EditableMetadataField.PUBLISHER
    ),
    COPYRIGHT("Copyright", false, FieldKey.COPYRIGHT, EditableMetadataField.COPYRIGHT),
    BPM("BPM", false, FieldKey.BPM, EditableMetadataField.BPM),
    DISC_NUMBER("Disc number", false, FieldKey.DISC_NO, EditableMetadataField.DISC_NUMBER),
    DISC_TOTAL("Disc total", false, FieldKey.DISC_TOTAL, EditableMetadataField.DISC_TOTAL)
}

sealed interface BatchMetadataValue {
    data class Text(val value: String) : BatchMetadataValue
    data class MultiValue(val values: List<String>) : BatchMetadataValue
}

sealed interface BatchInitialValue<out T> {
    data class Common<T>(val value: T) : BatchInitialValue<T>
    data object Mixed : BatchInitialValue<Nothing>
}

sealed interface BatchEditIntent<out T> {
    data object Untouched : BatchEditIntent<Nothing>
    data class Set<T>(val value: T) : BatchEditIntent<T>
    data object Clear : BatchEditIntent<Nothing>
}

data class BatchFieldState<T>(
    val initial: BatchInitialValue<T>,
    val intent: BatchEditIntent<T> = BatchEditIntent.Untouched
)

data class BatchMetadataTargetId(
    val referenceKey: String,
    val mediaStoreId: Long,
    val filePath: String,
    val volumeName: String = "",
    val displayName: String = "",
    val title: String = "",
    val artist: String = "",
    val contentUri: String = "",
    val relativePath: String = "",
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val dateModifiedEpochSeconds: Long = 0L
)

data class BatchArtworkReference(
    val identity: String,
    val previewUri: String
)

sealed interface BatchArtworkValue {
    data object None : BatchArtworkValue
    data class Present(val artwork: BatchArtworkReference) : BatchArtworkValue
}

data class BatchMetadataTarget(
    val id: BatchMetadataTargetId,
    val values: Map<BatchMetadataField, BatchMetadataValue>,
    val capabilities: MetadataFormatCapabilities,
    val artwork: BatchArtworkValue
)

data class BatchMetadataFieldChange(
    val initial: BatchInitialValue<BatchMetadataValue>,
    val intent: BatchEditIntent<BatchMetadataValue>
)

data class BatchArtworkChange(
    val initial: BatchInitialValue<BatchArtworkValue>,
    val intent: BatchEditIntent<BatchArtworkValue>
)

data class BatchMetadataPlan(
    val selectedTargets: List<BatchMetadataTargetId>,
    val fieldChanges: Map<BatchMetadataField, BatchMetadataFieldChange>,
    val artworkChange: BatchArtworkChange?
) {
    val selectedTrackCount: Int
        get() = selectedTargets.size

    val changeCount: Int
        get() = fieldChanges.size + if (artworkChange == null) 0 else 1
}

data class BatchMetadataEditorState(
    val targets: List<BatchMetadataTarget>,
    val capabilities: MetadataFormatCapabilities,
    val fields: Map<BatchMetadataField, BatchFieldState<BatchMetadataValue>>,
    val artwork: BatchFieldState<BatchArtworkValue>
) {
    val selectedTrackCount: Int
        get() = targets.size

    fun supports(field: BatchMetadataField): Boolean =
        capabilities.supports(field.requiredCapability)

    fun set(field: BatchMetadataField, userInput: String): BatchMetadataEditorState {
        if (!supports(field)) return this
        val value = field.valueFromUserInput(userInput)
        val current = fields.getValue(field)
        val intent = if (current.initial.commonValueOrNull()?.semanticallyEquals(value) == true) {
            BatchEditIntent.Untouched
        } else {
            BatchEditIntent.Set(value)
        }
        return copy(fields = fields + (field to current.copy(intent = intent)))
    }

    fun clear(field: BatchMetadataField): BatchMetadataEditorState {
        if (!supports(field)) return this
        val current = fields.getValue(field)
        return copy(fields = fields + (field to current.copy(intent = BatchEditIntent.Clear)))
    }

    fun reset(field: BatchMetadataField): BatchMetadataEditorState {
        val current = fields.getValue(field)
        return copy(fields = fields + (field to current.copy(intent = BatchEditIntent.Untouched)))
    }

    fun replaceArtwork(reference: BatchArtworkReference): BatchMetadataEditorState {
        if (!capabilities.supports(EditableMetadataField.ARTWORK)) return this
        val replacement = BatchArtworkValue.Present(reference)
        val intent = if (
            artwork.initial.commonValueOrNull()?.semanticallyEquals(replacement) == true
        ) {
            BatchEditIntent.Untouched
        } else {
            BatchEditIntent.Set(replacement)
        }
        return copy(artwork = artwork.copy(intent = intent))
    }

    fun clearArtwork(): BatchMetadataEditorState {
        if (!capabilities.supports(EditableMetadataField.ARTWORK)) return this
        return copy(artwork = artwork.copy(intent = BatchEditIntent.Clear))
    }

    fun resetArtwork(): BatchMetadataEditorState =
        copy(artwork = artwork.copy(intent = BatchEditIntent.Untouched))

    fun plan(): BatchMetadataPlan = BatchMetadataPlan(
        selectedTargets = targets.map(BatchMetadataTarget::id),
        fieldChanges = fields.mapNotNull { (field, state) ->
            state.intent.takeUnless { it == BatchEditIntent.Untouched }?.let { intent ->
                field to BatchMetadataFieldChange(state.initial, intent)
            }
        }.toMap(),
        artworkChange = artwork.intent.takeUnless { it == BatchEditIntent.Untouched }?.let { intent ->
            BatchArtworkChange(artwork.initial, intent)
        }
    )

    companion object {
        fun derive(targets: List<BatchMetadataTarget>): BatchMetadataEditorState {
            require(targets.isNotEmpty()) { "A batch editor requires at least one target" }
            return BatchMetadataEditorState(
                targets = targets,
                capabilities = MetadataFormatCapabilities.intersection(
                    targets.map(BatchMetadataTarget::capabilities)
                ),
                fields = BatchMetadataField.entries.associateWith { field ->
                    deriveFieldState(targets.map { target -> target.values.getValue(field) })
                },
                artwork = deriveArtworkState(targets.map(BatchMetadataTarget::artwork))
            )
        }
    }
}

internal fun Song.toBatchMetadataTarget(tags: EditableSongTags): BatchMetadataTarget =
    BatchMetadataTarget(
        id = BatchMetadataTargetId(
            referenceKey = membershipKey(),
            mediaStoreId = id,
            filePath = filePath,
            volumeName = volumeName,
            displayName = displayName,
            title = title,
            artist = artist,
            contentUri = uri.toString(),
            relativePath = relativePath,
            durationMs = duration,
            fileSizeBytes = fileSizeBytes,
            dateModifiedEpochSeconds = dateModifiedEpochSeconds
        ),
        values = mapOf(
            BatchMetadataField.ALBUM to BatchMetadataValue.Text(tags.album.trim()),
            BatchMetadataField.ALBUM_ARTIST to tags.albumArtist.toBatchMultiValue(),
            BatchMetadataField.DATE to BatchMetadataValue.Text(tags.year.trim()),
            BatchMetadataField.GENRE to tags.genre.toBatchMultiValue(),
            BatchMetadataField.COMPOSER to tags.composer.toBatchMultiValue(),
            BatchMetadataField.COMMENT to BatchMetadataValue.Text(tags.comment.trim()),
            BatchMetadataField.PUBLISHER to BatchMetadataValue.Text(tags.publisher.trim()),
            BatchMetadataField.COPYRIGHT to BatchMetadataValue.Text(tags.copyright.trim()),
            BatchMetadataField.BPM to BatchMetadataValue.Text(tags.bpm.trim()),
            BatchMetadataField.DISC_NUMBER to BatchMetadataValue.Text(tags.discNumber.trim()),
            BatchMetadataField.DISC_TOTAL to BatchMetadataValue.Text(tags.discTotal.trim())
        ),
        capabilities = tags.capabilities,
        artwork = albumArtUri?.let { artworkUri ->
            val previewUri = artworkUri.toString()
            val identity = EmbeddedArtworkContract.parse(artworkUri)?.artworkHash ?: previewUri
            BatchArtworkValue.Present(
                BatchArtworkReference(identity = identity, previewUri = previewUri)
            )
        } ?: BatchArtworkValue.None
    )

internal fun BatchMetadataValue.displayText(): String = when (this) {
    is BatchMetadataValue.Text -> value
    is BatchMetadataValue.MultiValue -> values.toEditableMultiValue()
}

private fun BatchMetadataField.valueFromUserInput(input: String): BatchMetadataValue =
    if (isMultiValue) {
        BatchMetadataValue.MultiValue(input.parseEditableMultiValue())
    } else {
        BatchMetadataValue.Text(input)
    }

private fun String.toBatchMultiValue(): BatchMetadataValue.MultiValue =
    BatchMetadataValue.MultiValue(parseEditableMultiValue())

private fun BatchMetadataValue.semanticallyEquals(other: BatchMetadataValue): Boolean =
    when {
        this is BatchMetadataValue.Text && other is BatchMetadataValue.Text ->
            value.trim() == other.value.trim()
        this is BatchMetadataValue.MultiValue && other is BatchMetadataValue.MultiValue ->
            values == other.values
        else -> false
    }

private fun <T> BatchInitialValue<T>.commonValueOrNull(): T? = when (this) {
    is BatchInitialValue.Common -> value
    BatchInitialValue.Mixed -> null
}

private fun <T> deriveFieldState(values: List<T>): BatchFieldState<T> {
    val first = values.first()
    return BatchFieldState(
        initial = if (values.all { value -> value == first }) {
            BatchInitialValue.Common(first)
        } else {
            BatchInitialValue.Mixed
        }
    )
}

private fun deriveArtworkState(
    values: List<BatchArtworkValue>
): BatchFieldState<BatchArtworkValue> {
    val first = values.first()
    return BatchFieldState(
        initial = if (values.all { value -> first.semanticallyEquals(value) }) {
            BatchInitialValue.Common(first)
        } else {
            BatchInitialValue.Mixed
        }
    )
}

private fun BatchArtworkValue.semanticallyEquals(other: BatchArtworkValue): Boolean = when {
    this == BatchArtworkValue.None && other == BatchArtworkValue.None -> true
    this is BatchArtworkValue.Present && other is BatchArtworkValue.Present ->
        artwork.identity == other.artwork.identity
    else -> false
}
