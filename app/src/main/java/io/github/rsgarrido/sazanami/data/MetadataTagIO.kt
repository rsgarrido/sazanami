package io.github.rsgarrido.sazanami.data

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp4.Mp4TagReader
import org.jaudiotagger.audio.mp4.Mp4TagWriter
import org.jaudiotagger.tag.Tag
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Reads and writes tags while avoiding MP4 audio-header parsing for metadata-only operations.
 */
internal object MetadataTagIO {
    private val logger = Logger.getLogger(MetadataTagIO::class.java.name)

    fun readTag(file: File): Tag? = if (file.isMp4FamilyMetadataFile()) {
        readMp4Tag(file)
    } else {
        AudioFileIO.read(file).tag
    }

    fun openForWrite(file: File): MetadataTagWriteTarget {
        if (file.isMp4FamilyMetadataFile()) {
            val tag = readMp4Tag(file)
            return MetadataTagWriteTarget(tag) {
                try {
                    Mp4TagWriter(file.toString()).write(tag, file.toPath())
                } catch (exception: Exception) {
                    logger.log(
                        Level.WARNING,
                        "Could not write MP4 metadata to ${file.absolutePath}",
                        exception
                    )
                    throw MetadataTagIOException(
                        "Could not save this file's metadata.",
                        exception
                    )
                }
            }
        }

        val audioFile = AudioFileIO.read(file)
        return MetadataTagWriteTarget(audioFile.tagOrCreateAndSetDefault) {
            AudioFileIO.write(audioFile)
        }
    }

    private fun readMp4Tag(file: File): Tag = try {
        Mp4TagReader().read(file.toPath())
    } catch (exception: Exception) {
        logger.log(
            Level.WARNING,
            "Could not read MP4 metadata from ${file.absolutePath}",
            exception
        )
        throw MetadataTagIOException(
            "Could not read this file's metadata.",
            exception
        )
    }
}

internal class MetadataTagWriteTarget(
    val tag: Tag,
    private val writeAction: () -> Unit
) {
    fun write() = writeAction()
}

private class MetadataTagIOException(
    message: String,
    cause: Exception
) : Exception(message, cause)

private fun File.isMp4FamilyMetadataFile(): Boolean =
    extension.toAudioMetadataFormat() == AudioMetadataFormat.MP4
