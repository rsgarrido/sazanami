package io.github.rsgarrido.sazanami.ui.tageditor

import android.R
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.data.EditableSongTags
import io.github.rsgarrido.sazanami.data.EditableMetadataField
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.isValidMetadataBpm

@Composable
fun TagEditorScreen(
    song: Song,
    initialTags: EditableSongTags,
    selectedArtworkUri: Uri?,
    isSaving: Boolean,
    unsupportedMessage: String?,
    isCurrentSong: Boolean,
    onBackClick: () -> Unit,
    onChangeArtworkClick: () -> Unit,
    onSaveClick: (EditableSongTags) -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTags by remember(song.id, initialTags) {
        mutableStateOf(initialTags)
    }

    var isAdvancedMetadataExpanded by remember(song.id) {
        mutableStateOf(false)
    }

    val hasUnsavedTagChanges = currentTags != initialTags

    LaunchedEffect(hasUnsavedTagChanges) {
        onUnsavedChangesChanged(hasUnsavedTagChanges)
    }

    val artworkPreviewUri = selectedArtworkUri ?: song.albumArtUri

    val titleError = currentTags.title.trim().isBlank()
    val artistError = currentTags.artist.trim().isBlank()
    val albumError = currentTags.album.trim().isBlank()
    val bpmError = hasInvalidChangedBpm(
        originalBpm = initialTags.bpm,
        editedBpm = currentTags.bpm
    )

    val hasValidationError = titleError || artistError || albumError || bpmError
    val canEditFields = !isSaving && unsupportedMessage == null
    val canSave =
        canEditFields &&
                !hasValidationError &&
                (hasUnsavedTagChanges || selectedArtworkUri != null)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                enabled = !isSaving
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                text = "Edit Tags",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = artworkPreviewUri,
                contentDescription = "Artwork for ${song.title}",
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.ic_media_play),
                placeholder = painterResource(R.drawable.ic_media_play)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title.ifBlank { "Unknown Title" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = song.artist.ifBlank { "Unknown Artist" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onChangeArtworkClick,
                    enabled = canEditFields
                ) {
                    Text(
                        text = if (selectedArtworkUri == null) {
                            "Change Artwork"
                        } else {
                            "Choose Different Artwork"
                        }
                    )
                }
            }
        }

        if (selectedArtworkUri != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "New artwork selected. Tap Save to write it into the audio file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = song.filePath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (unsupportedMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = unsupportedMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (isCurrentSong) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "This song is currently playing. If playback behaves strangely after saving, pause the song before editing next time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = currentTags.title,
            onValueChange = { value ->
                currentTags = currentTags.copy(title = value)
            },
            label = {
                Text(text = "Title")
            },
            singleLine = true,
            enabled = canEditFields,
            isError = titleError,
            supportingText = {
                if (titleError) {
                    Text(text = "Title cannot be empty.")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = currentTags.artist,
            onValueChange = { value ->
                currentTags = currentTags.copy(artist = value)
            },
            label = {
                Text(text = "Artist")
            },
            singleLine = true,
            enabled = canEditFields,
            isError = artistError,
            supportingText = {
                if (artistError) {
                    Text(text = "Artist cannot be empty.")
                } else {
                    Text(text = MULTI_VALUE_HELP)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = currentTags.album,
            onValueChange = { value ->
                currentTags = currentTags.copy(album = value)
            },
            label = {
                Text(text = "Album")
            },
            singleLine = true,
            enabled = canEditFields,
            isError = albumError,
            supportingText = {
                if (albumError) {
                    Text(text = "Album cannot be empty.")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = currentTags.trackNumber,
            onValueChange = { value ->
                currentTags = currentTags.copy(trackNumber = value)
            },
            label = {
                Text(text = "Track number")
            },
            singleLine = true,
            enabled = canEditFields,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = currentTags.year,
            onValueChange = { value ->
                currentTags = currentTags.copy(year = value)
            },
            label = {
                Text(text = "Date / year")
            },
            singleLine = true,
            enabled = canEditFields,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = {
                isAdvancedMetadataExpanded = !isAdvancedMetadataExpanded
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Advanced metadata",
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isAdvancedMetadataExpanded) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = if (isAdvancedMetadataExpanded) {
                    "Collapse advanced metadata"
                } else {
                    "Expand advanced metadata"
                }
            )
        }

        if (isAdvancedMetadataExpanded) {
            Spacer(modifier = Modifier.height(16.dp))

            AdvancedMetadataFields(
                tags = currentTags,
                canEditFields = canEditFields,
                bpmError = bpmError,
                onTagsChanged = { updated -> currentTags = updated }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row {
            OutlinedButton(
                onClick = onBackClick,
                enabled = !isSaving,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Cancel")
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = {
                    onSaveClick(currentTags)
                },
                enabled = canSave,
                modifier = Modifier.weight(1f)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(text = "Saving")
                } else {
                    Text(text = "Save")
                }
            }
        }
    }
}

@Composable
private fun AdvancedMetadataFields(
    tags: EditableSongTags,
    canEditFields: Boolean,
    bpmError: Boolean,
    onTagsChanged: (EditableSongTags) -> Unit
) {
    AdvancedMetadataTextField(
        value = tags.albumArtist,
        onValueChange = { onTagsChanged(tags.copy(albumArtist = it)) },
        label = "Album artist",
        field = EditableMetadataField.ALBUM_ARTIST,
        tags = tags,
        canEditFields = canEditFields,
        supportingMessage = MULTI_VALUE_HELP
    )

    Spacer(modifier = Modifier.height(12.dp))

    Row(modifier = Modifier.fillMaxWidth()) {
        AdvancedMetadataTextField(
            value = tags.trackTotal,
            onValueChange = { onTagsChanged(tags.copy(trackTotal = it)) },
            label = "Track total",
            field = EditableMetadataField.TRACK_TOTAL,
            tags = tags,
            canEditFields = canEditFields,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        AdvancedMetadataTextField(
            value = tags.discNumber,
            onValueChange = { onTagsChanged(tags.copy(discNumber = it)) },
            label = "Disc number",
            field = EditableMetadataField.DISC_NUMBER,
            tags = tags,
            canEditFields = canEditFields,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    AdvancedMetadataTextField(
        value = tags.discTotal,
        onValueChange = { onTagsChanged(tags.copy(discTotal = it)) },
        label = "Disc total",
        field = EditableMetadataField.DISC_TOTAL,
        tags = tags,
        canEditFields = canEditFields,
        keyboardType = KeyboardType.Number
    )

    Spacer(modifier = Modifier.height(12.dp))

    AdvancedMetadataTextField(
        value = tags.genre,
        onValueChange = { onTagsChanged(tags.copy(genre = it)) },
        label = "Genre",
        field = EditableMetadataField.GENRE,
        tags = tags,
        canEditFields = canEditFields,
        supportingMessage = MULTI_VALUE_HELP
    )

    Spacer(modifier = Modifier.height(12.dp))

    AdvancedMetadataTextField(
        value = tags.composer,
        onValueChange = { onTagsChanged(tags.copy(composer = it)) },
        label = "Composer",
        field = EditableMetadataField.COMPOSER,
        tags = tags,
        canEditFields = canEditFields,
        supportingMessage = MULTI_VALUE_HELP
    )

    Spacer(modifier = Modifier.height(12.dp))

    AdvancedMetadataTextField(
        value = tags.comment,
        onValueChange = { onTagsChanged(tags.copy(comment = it)) },
        label = "Comment",
        field = EditableMetadataField.COMMENT,
        tags = tags,
        canEditFields = canEditFields,
        singleLine = false,
        minLines = 3
    )

    Spacer(modifier = Modifier.height(12.dp))

    AdvancedMetadataTextField(
        value = tags.publisher,
        onValueChange = { onTagsChanged(tags.copy(publisher = it)) },
        label = "Publisher / Label",
        field = EditableMetadataField.PUBLISHER,
        tags = tags,
        canEditFields = canEditFields
    )

    Spacer(modifier = Modifier.height(12.dp))

    AdvancedMetadataTextField(
        value = tags.copyright,
        onValueChange = { onTagsChanged(tags.copy(copyright = it)) },
        label = "Copyright",
        field = EditableMetadataField.COPYRIGHT,
        tags = tags,
        canEditFields = canEditFields
    )

    Spacer(modifier = Modifier.height(12.dp))

    AdvancedMetadataTextField(
        value = tags.bpm,
        onValueChange = { onTagsChanged(tags.copy(bpm = it)) },
        label = "BPM",
        field = EditableMetadataField.BPM,
        tags = tags,
        canEditFields = canEditFields,
        keyboardType = KeyboardType.Number,
        isError = bpmError,
        supportingMessage = if (bpmError) BPM_ERROR_MESSAGE else null
    )
}

@Composable
private fun AdvancedMetadataTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    field: EditableMetadataField,
    tags: EditableSongTags,
    canEditFields: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingMessage: String? = null
) {
    val isSupported = tags.capabilities.supports(field)
    val message = if (isSupported) supportingMessage else UNSUPPORTED_FIELD_MESSAGE

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        enabled = canEditFields && isSupported,
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = message?.let { text ->
            { Text(text = text) }
        },
        modifier = modifier
    )
}

internal fun isValidEditableBpm(value: String): Boolean {
    return value.isValidMetadataBpm()
}

internal fun hasInvalidChangedBpm(originalBpm: String, editedBpm: String): Boolean {
    return originalBpm.trim() != editedBpm.trim() && !isValidEditableBpm(editedBpm)
}

private const val MULTI_VALUE_HELP = "Separate multiple values with semicolons."
private const val BPM_ERROR_MESSAGE = "Enter a whole number from 1 to 999, or leave blank."
private const val UNSUPPORTED_FIELD_MESSAGE = "This field is not supported for this file format."
