package io.github.rsgarrido.sazanami.player

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal class FragmentedMp4SeekIndex(
    positions: LongArray,
    timesUs: LongArray,
    val durationUs: Long
) {
    private val positions = positions.copyOf()
    private val timesUs = timesUs.copyOf()

    init {
        require(this.positions.isNotEmpty())
        require(this.positions.size == this.timesUs.size)
    }

    fun positions(): LongArray = positions.copyOf()

    fun timesUs(): LongArray = timesUs.copyOf()
}

internal fun interface Mp4RandomAccessReader {
    fun read(position: Long, byteCount: Int): ByteArray?
}

internal class FileChannelMp4Reader(
    private val channel: FileChannel,
    private val baseOffset: Long
) : Mp4RandomAccessReader {
    override fun read(position: Long, byteCount: Int): ByteArray? {
        if (position < 0L || byteCount < 0 || baseOffset > Long.MAX_VALUE - position) return null
        val bytes = ByteArray(byteCount)
        var bytesRead = 0
        while (bytesRead < byteCount) {
            if (position > Long.MAX_VALUE - bytesRead) return null
            val relativePosition = position + bytesRead
            if (baseOffset > Long.MAX_VALUE - relativePosition) return null
            val buffer = ByteBuffer.wrap(bytes, bytesRead, byteCount - bytesRead)
            val read = channel.read(buffer, baseOffset + relativePosition)
            if (read <= 0) return null
            bytesRead += read
        }
        return bytes
    }
}

/** Builds a seek index only when every required fragmented-MP4 invariant is proven. */
internal object FragmentedMp4SeekIndexParser {
    private const val MAX_BOXES = 100_000
    private const val MAX_SAMPLES_PER_TRUN = 1_000_000L
    private val SUPPORTED_AUDIO_SAMPLE_ENTRIES = setOf(
        "mp4a", "ac-3", "ec-3", "ac-4", "mlpa", "dtsc", "dtse", "dtsh", "dtsl", "dtsx",
        "mha1", "mhm1", "alac", "alaw", "ulaw", "Opus", "fLaC", "iamf", "ipcm", "fpcm"
    )
    private val AMBIGUOUS_FRAGMENT_BOXES = setOf("senc", "saiz", "saio", "sbgp", "sgpd", "uuid")

    fun parse(
        reader: Mp4RandomAccessReader,
        fileLength: Long,
        expectedDurationUs: Long?
    ): FragmentedMp4SeekIndex? {
        if (fileLength < 8L) return null
        val topLevel = scanChildren(reader, 0L, fileLength) ?: return null
        if (topLevel.size > MAX_BOXES) return null
        if (topLevel.count { it.type == "ftyp" } != 1) return null
        if (topLevel.count { it.type == "moov" } != 1) return null
        if (topLevel.none { it.type == "moof" }) return null
        if (topLevel.any { it.type == "sidx" || it.type == "mfra" }) return null

        val moov = topLevel.single { it.type == "moov" }
        val moofs = topLevel.filter { it.type == "moof" }
        val mdats = topLevel.filter { it.type == "mdat" }
        if (mdats.size != moofs.size || moofs.any { it.offset <= moov.offset }) return null
        val track = parseSingleAudioTrack(reader, moov) ?: return null
        val defaults = parseTrexDefaults(reader, moov, track.trackId) ?: return null
        val topLevelByOffset = topLevel.associateBy { it.offset }

        val fragments = ArrayList<Fragment>(moofs.size)
        for (moof in moofs) {
            val fragment = parseFragment(
                reader = reader,
                moof = moof,
                adjacentMdat = topLevelByOffset[moof.endOffset]?.takeIf { it.type == "mdat" }
                    ?: return null,
                track = track,
                defaults = defaults
            ) ?: return null
            fragments += fragment
        }
        if (fragments.isEmpty()) return null
        if (fragments.first().startUnits != 0L) return null
        if (!fragments.zipWithNext().all { (first, second) ->
                second.sequenceNumber > first.sequenceNumber &&
                    second.startUnits > first.startUnits &&
                    checkedAdd(first.startUnits, first.durationUnits) == second.startUnits
            }
        ) return null

        val endUnits = checkedAdd(fragments.last().startUnits, fragments.last().durationUnits)
            ?: return null
        if (track.durationUnits != null && track.durationUnits != endUnits) return null
        val indexedDurationUs = scaleToUs(endUnits, track.timescale) ?: return null
        val validatedExpectedDurationUs = expectedDurationUs?.takeIf { it > 0L }
        if (validatedExpectedDurationUs != null && validatedExpectedDurationUs != indexedDurationUs) {
            return null
        }
        if (track.durationUnits == null && validatedExpectedDurationUs == null) return null

        val timesUs = LongArray(fragments.size)
        val positions = LongArray(fragments.size)
        fragments.forEachIndexed { index, fragment ->
            timesUs[index] = scaleToUs(fragment.startUnits, track.timescale) ?: return null
            positions[index] = fragment.moofOffset
        }
        if (timesUs.first() != 0L) return null
        for (index in 1 until timesUs.size) {
            if (timesUs[index] <= timesUs[index - 1]) return null
        }
        return FragmentedMp4SeekIndex(positions, timesUs, indexedDurationUs)
    }

    private fun parseSingleAudioTrack(reader: Mp4RandomAccessReader, moov: Box): AudioTrack? {
        val moovChildren = scanChildren(reader, moov.payloadOffset, moov.endOffset) ?: return null
        val traks = moovChildren.filter { it.type == "trak" }
        if (traks.size != 1) return null
        val trak = traks.single()
        val trakChildren = scanChildren(reader, trak.payloadOffset, trak.endOffset) ?: return null
        if (trakChildren.any { it.type == "edts" }) return null
        val trackId = trakChildren.singleOrNull { it.type == "tkhd" }
            ?.let { parseTkhdTrackId(reader, it) }
            ?: return null
        val mdia = trakChildren.singleOrNull { it.type == "mdia" } ?: return null
        val mdiaChildren = scanChildren(reader, mdia.payloadOffset, mdia.endOffset) ?: return null
        val handler = mdiaChildren.singleOrNull { it.type == "hdlr" }
            ?.let { readPayload(reader, it, 8L, 4) }
            ?.let { fourCc(it, 0) }
        if (handler != "soun") return null
        val mdhd = mdiaChildren.singleOrNull { it.type == "mdhd" }
            ?.let { parseMdhd(reader, it) }
            ?: return null
        if (mdhd.timescale <= 0L) return null

        val minf = mdiaChildren.singleOrNull { it.type == "minf" } ?: return null
        val minfChildren = scanChildren(reader, minf.payloadOffset, minf.endOffset) ?: return null
        val stbl = minfChildren.singleOrNull { it.type == "stbl" } ?: return null
        val stblChildren = scanChildren(reader, stbl.payloadOffset, stbl.endOffset) ?: return null
        val stsd = stblChildren.singleOrNull { it.type == "stsd" } ?: return null
        val sampleEntries = parseStsdEntries(reader, stsd) ?: return null
        if (sampleEntries.size != 1 || sampleEntries.single() !in SUPPORTED_AUDIO_SAMPLE_ENTRIES) {
            return null
        }
        return AudioTrack(trackId, mdhd.timescale, mdhd.durationUnits)
    }

    private fun parseStsdEntries(reader: Mp4RandomAccessReader, stsd: Box): List<String>? {
        val header = readPayload(reader, stsd, 0L, 8) ?: return null
        val entryCount = unsignedInt(header, 4)
        if (entryCount <= 0L || entryCount > MAX_BOXES) return null
        val entries = ArrayList<String>(entryCount.toInt())
        var offset = stsd.payloadOffset + 8L
        repeat(entryCount.toInt()) {
            val entry = readBox(reader, offset, stsd.endOffset) ?: return null
            entries += entry.type
            offset = entry.endOffset
        }
        if (offset != stsd.endOffset) return null
        return entries
    }

    private fun parseTrexDefaults(
        reader: Mp4RandomAccessReader,
        moov: Box,
        trackId: Long
    ): TrexDefaults? {
        val moovChildren = scanChildren(reader, moov.payloadOffset, moov.endOffset) ?: return null
        val mvex = moovChildren.singleOrNull { it.type == "mvex" } ?: return null
        val trexBoxes = scanChildren(reader, mvex.payloadOffset, mvex.endOffset)
            ?.filter { it.type == "trex" }
            ?: return null
        if (trexBoxes.size != 1) return null
        val defaults = parseTrex(reader, trexBoxes.single()) ?: return null
        return defaults.takeIf {
            it.trackId == trackId && it.sampleDescriptionIndex == 1L
        }
    }

    private fun parseFragment(
        reader: Mp4RandomAccessReader,
        moof: Box,
        adjacentMdat: Box,
        track: AudioTrack,
        defaults: TrexDefaults
    ): Fragment? {
        val moofChildren = scanChildren(reader, moof.payloadOffset, moof.endOffset) ?: return null
        val mfhd = moofChildren.singleOrNull { it.type == "mfhd" } ?: return null
        val sequenceNumber = readUnsignedInt(reader, mfhd, 4L) ?: return null
        val traf = moofChildren.singleOrNull { it.type == "traf" } ?: return null
        val trafChildren = scanChildren(reader, traf.payloadOffset, traf.endOffset) ?: return null
        if (trafChildren.any { it.type in AMBIGUOUS_FRAGMENT_BOXES }) return null
        val tfhd = trafChildren.singleOrNull { it.type == "tfhd" }
            ?.let { parseTfhd(reader, it) }
            ?: return null
        if (tfhd.trackId != track.trackId || tfhd.durationIsEmpty) return null
        val descriptionIndex = tfhd.sampleDescriptionIndex ?: defaults.sampleDescriptionIndex
        if (descriptionIndex != 1L) return null
        val tfdt = trafChildren.singleOrNull { it.type == "tfdt" }
            ?.let { parseTfdt(reader, it) }
            ?: return null
        val trunBoxes = trafChildren.filter { it.type == "trun" }
        if (trunBoxes.isEmpty()) return null
        val defaultDuration = (tfhd.sampleDuration ?: defaults.sampleDuration).takeIf { it > 0L }
        val defaultSize = (tfhd.sampleSize ?: defaults.sampleSize).takeIf { it > 0L }
        val defaultFlags = tfhd.sampleFlags ?: defaults.sampleFlags
        val truns = trunBoxes.map { box ->
            parseTrun(reader, box, defaultDuration, defaultSize, defaultFlags) ?: return null
        }
        if (truns.any { it.compositionOffsetsPresent || !it.firstSampleSync }) return null
        val durationUnits = checkedSum(truns.map { it.durationUnits })?.takeIf { it > 0L }
            ?: return null

        val dataPositions = truns.map { trun ->
            when {
                trun.dataOffset != null -> checkedAddSigned(
                    tfhd.baseDataOffset ?: moof.offset,
                    trun.dataOffset
                )
                tfhd.baseDataOffset != null -> tfhd.baseDataOffset
                truns.size == 1 -> adjacentMdat.payloadOffset
                else -> null
            } ?: return null
        }
        val ranges = truns.indices.map { index ->
            val end = checkedAdd(dataPositions[index], truns[index].totalSampleBytes) ?: return null
            dataPositions[index] to end
        }
        if (ranges.any { (start, end) -> !rangeIsWithinMdat(adjacentMdat, start, end) }) return null
        if (!ranges.zipWithNext().all { (first, second) -> first.second <= second.first }) return null
        return Fragment(moof.offset, sequenceNumber, tfdt, durationUnits)
    }

    private fun parseTrun(
        reader: Mp4RandomAccessReader,
        box: Box,
        defaultDuration: Long?,
        defaultSize: Long?,
        defaultFlags: Long
    ): Trun? {
        val header = readPayload(reader, box, 0L, 8) ?: return null
        val flags = fullBoxFlags(header)
        val sampleCount = unsignedInt(header, 4)
        if (sampleCount <= 0L || sampleCount > MAX_SAMPLES_PER_TRUN) return null
        var cursor = 8L
        val dataOffset = if (flags and 0x000001 != 0) {
            val bytes = readPayload(reader, box, cursor, 4) ?: return null
            cursor += 4L
            signedInt(bytes, 0)
        } else {
            null
        }
        val firstSampleFlags = if (flags and 0x000004 != 0) {
            val value = readUnsignedInt(reader, box, cursor) ?: return null
            cursor += 4L
            value
        } else {
            null
        }
        val durationsPresent = flags and 0x000100 != 0
        val sizesPresent = flags and 0x000200 != 0
        val sampleFlagsPresent = flags and 0x000400 != 0
        val compositionOffsetsPresent = flags and 0x000800 != 0
        val entrySize = (if (durationsPresent) 4 else 0) +
            (if (sizesPresent) 4 else 0) +
            (if (sampleFlagsPresent) 4 else 0) +
            (if (compositionOffsetsPresent) 4 else 0)
        val entriesBytes = checkedMultiply(sampleCount, entrySize.toLong()) ?: return null
        val payloadSize = box.size - box.headerSize
        if (cursor > payloadSize || entriesBytes > payloadSize - cursor) return null

        var durationUnits = if (durationsPresent) 0L else {
            defaultDuration?.let { checkedMultiply(sampleCount, it) } ?: return null
        }
        var totalBytes = if (sizesPresent) 0L else {
            defaultSize?.let { checkedMultiply(sampleCount, it) } ?: return null
        }
        var firstPerSampleFlags: Long? = null
        repeat(sampleCount.toInt()) { sampleIndex ->
            if (durationsPresent) {
                durationUnits = checkedAdd(
                    durationUnits,
                    readUnsignedInt(reader, box, cursor) ?: return null
                ) ?: return null
                cursor += 4L
            }
            if (sizesPresent) {
                totalBytes = checkedAdd(
                    totalBytes,
                    readUnsignedInt(reader, box, cursor) ?: return null
                ) ?: return null
                cursor += 4L
            }
            if (sampleFlagsPresent) {
                val value = readUnsignedInt(reader, box, cursor) ?: return null
                if (sampleIndex == 0) firstPerSampleFlags = value
                cursor += 4L
            }
            if (compositionOffsetsPresent) cursor += 4L
        }
        val effectiveFirstFlags = firstPerSampleFlags ?: firstSampleFlags ?: defaultFlags
        val firstSampleSync = effectiveFirstFlags and 0x00010000L == 0L
        return Trun(
            dataOffset = dataOffset,
            durationUnits = durationUnits,
            totalSampleBytes = totalBytes,
            compositionOffsetsPresent = compositionOffsetsPresent,
            firstSampleSync = firstSampleSync
        )
    }

    private fun parseTkhdTrackId(reader: Mp4RandomAccessReader, box: Box): Long? {
        val version = readPayload(reader, box, 0L, 1)?.first()?.toInt()?.and(0xff) ?: return null
        val offset = when (version) {
            0 -> 12L
            1 -> 20L
            else -> return null
        }
        return readUnsignedInt(reader, box, offset)?.takeIf { it > 0L }
    }

    private fun parseMdhd(reader: Mp4RandomAccessReader, box: Box): Mdhd? {
        val version = readPayload(reader, box, 0L, 1)?.first()?.toInt()?.and(0xff) ?: return null
        return when (version) {
            0 -> {
                val timescale = readUnsignedInt(reader, box, 12L) ?: return null
                val duration = readUnsignedInt(reader, box, 16L)
                    ?.takeIf { it > 0L && it != 0xffffffffL }
                Mdhd(timescale, duration)
            }
            1 -> {
                val timescale = readUnsignedInt(reader, box, 20L) ?: return null
                val duration = readPayload(reader, box, 24L, 8)
                    ?.let(::unsignedLong)
                    ?.takeIf { it > 0L }
                Mdhd(timescale, duration)
            }
            else -> null
        }
    }

    private fun parseTrex(reader: Mp4RandomAccessReader, box: Box): TrexDefaults? {
        val bytes = readPayload(reader, box, 4L, 20) ?: return null
        return TrexDefaults(
            trackId = unsignedInt(bytes, 0),
            sampleDescriptionIndex = unsignedInt(bytes, 4),
            sampleDuration = unsignedInt(bytes, 8),
            sampleSize = unsignedInt(bytes, 12),
            sampleFlags = unsignedInt(bytes, 16)
        )
    }

    private fun parseTfhd(reader: Mp4RandomAccessReader, box: Box): Tfhd? {
        val header = readPayload(reader, box, 0L, 8) ?: return null
        val flags = fullBoxFlags(header)
        val trackId = unsignedInt(header, 4)
        var cursor = 8L
        fun readOptional(flag: Int, byteCount: Int): Long? {
            if (flags and flag == 0) return null
            val bytes = readPayload(reader, box, cursor, byteCount) ?: return null
            cursor += byteCount
            return if (byteCount == 8) unsignedLong(bytes) else unsignedInt(bytes, 0)
        }
        val baseDataOffset = if (flags and 0x000001 != 0) {
            readOptional(0x000001, 8) ?: return null
        } else null
        val descriptionIndex = if (flags and 0x000002 != 0) {
            readOptional(0x000002, 4) ?: return null
        } else null
        val duration = if (flags and 0x000008 != 0) {
            readOptional(0x000008, 4) ?: return null
        } else null
        val size = if (flags and 0x000010 != 0) {
            readOptional(0x000010, 4) ?: return null
        } else null
        val sampleFlags = if (flags and 0x000020 != 0) {
            readOptional(0x000020, 4) ?: return null
        } else null
        return Tfhd(
            trackId = trackId,
            baseDataOffset = baseDataOffset,
            sampleDescriptionIndex = descriptionIndex,
            sampleDuration = duration,
            sampleSize = size,
            sampleFlags = sampleFlags,
            durationIsEmpty = flags and 0x010000 != 0
        )
    }

    private fun parseTfdt(reader: Mp4RandomAccessReader, box: Box): Long? {
        val version = readPayload(reader, box, 0L, 1)?.first()?.toInt()?.and(0xff) ?: return null
        return when (version) {
            0 -> readUnsignedInt(reader, box, 4L)
            1 -> readPayload(reader, box, 4L, 8)?.let(::unsignedLong)
            else -> null
        }
    }

    private fun scanChildren(
        reader: Mp4RandomAccessReader,
        start: Long,
        end: Long
    ): List<Box>? {
        if (start < 0L || end < start) return null
        val boxes = mutableListOf<Box>()
        var offset = start
        while (offset < end) {
            if (boxes.size >= MAX_BOXES) return null
            val box = readBox(reader, offset, end) ?: return null
            boxes += box
            offset = box.endOffset
        }
        return boxes.takeIf { offset == end }
    }

    private fun readBox(reader: Mp4RandomAccessReader, offset: Long, containerEnd: Long): Box? {
        val remaining = containerEnd - offset
        if (remaining < 8L) return null
        val header = reader.read(offset, 8) ?: return null
        val size32 = unsignedInt(header, 0)
        val extended = size32 == 1L
        val headerSize = if (extended) 16L else 8L
        val size = when {
            size32 == 0L -> remaining
            extended -> reader.read(offset + 8L, 8)?.let(::unsignedLong) ?: return null
            else -> size32
        }
        if (size < headerSize || size > remaining) return null
        return Box(fourCc(header, 4), offset, size, headerSize)
    }

    private fun readPayload(
        reader: Mp4RandomAccessReader,
        box: Box,
        offset: Long,
        byteCount: Int
    ): ByteArray? {
        val payloadSize = box.size - box.headerSize
        if (offset < 0L || byteCount < 0 || offset > payloadSize ||
            byteCount.toLong() > payloadSize - offset
        ) return null
        return reader.read(box.payloadOffset + offset, byteCount)
    }

    private fun readUnsignedInt(reader: Mp4RandomAccessReader, box: Box, offset: Long): Long? =
        readPayload(reader, box, offset, 4)?.let { unsignedInt(it, 0) }

    private fun fullBoxFlags(bytes: ByteArray): Int =
        ((bytes[1].toInt() and 0xff) shl 16) or
            ((bytes[2].toInt() and 0xff) shl 8) or
            (bytes[3].toInt() and 0xff)

    private fun unsignedInt(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xffL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
            (bytes[offset + 3].toLong() and 0xffL)

    private fun signedInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun unsignedLong(bytes: ByteArray): Long? {
        if ((bytes.first().toInt() and 0x80) != 0) return null
        var result = 0L
        bytes.forEach { byte -> result = (result shl 8) or (byte.toLong() and 0xffL) }
        return result
    }

    private fun fourCc(bytes: ByteArray, offset: Int): String = buildString(4) {
        repeat(4) { index -> append((bytes[offset + index].toInt() and 0xff).toChar()) }
    }

    private fun checkedAdd(first: Long, second: Long): Long? =
        if (first < 0L || second < 0L || first > Long.MAX_VALUE - second) null else first + second

    private fun checkedAddSigned(first: Long, second: Int): Long? = when {
        first < 0L -> null
        second > 0 && first > Long.MAX_VALUE - second.toLong() -> null
        second < 0 && first < -second.toLong() -> null
        else -> first + second.toLong()
    }

    private fun checkedMultiply(first: Long, second: Long): Long? {
        if (first < 0L || second < 0L) return null
        if (first == 0L || second == 0L) return 0L
        return if (first > Long.MAX_VALUE / second) null else first * second
    }

    private fun checkedSum(values: List<Long>): Long? {
        var result = 0L
        values.forEach { value -> result = checkedAdd(result, value) ?: return null }
        return result
    }

    private fun scaleToUs(units: Long, timescale: Long): Long? {
        if (units < 0L || timescale <= 0L) return null
        val wholeUs = checkedMultiply(units / timescale, 1_000_000L) ?: return null
        val remainderUs = checkedMultiply(units % timescale, 1_000_000L)?.div(timescale) ?: return null
        return checkedAdd(wholeUs, remainderUs)
    }

    private fun rangeIsWithinMdat(mdat: Box, start: Long, end: Long): Boolean =
        start >= mdat.payloadOffset && end <= mdat.endOffset

    private data class Box(val type: String, val offset: Long, val size: Long, val headerSize: Long) {
        val payloadOffset: Long get() = offset + headerSize
        val endOffset: Long get() = offset + size
    }

    private data class Mdhd(val timescale: Long, val durationUnits: Long?)
    private data class AudioTrack(val trackId: Long, val timescale: Long, val durationUnits: Long?)
    private data class TrexDefaults(
        val trackId: Long,
        val sampleDescriptionIndex: Long,
        val sampleDuration: Long,
        val sampleSize: Long,
        val sampleFlags: Long
    )
    private data class Tfhd(
        val trackId: Long,
        val baseDataOffset: Long?,
        val sampleDescriptionIndex: Long?,
        val sampleDuration: Long?,
        val sampleSize: Long?,
        val sampleFlags: Long?,
        val durationIsEmpty: Boolean
    )
    private data class Trun(
        val dataOffset: Int?,
        val durationUnits: Long,
        val totalSampleBytes: Long,
        val compositionOffsetsPresent: Boolean,
        val firstSampleSync: Boolean
    )
    private data class Fragment(
        val moofOffset: Long,
        val sequenceNumber: Long,
        val startUnits: Long,
        val durationUnits: Long
    )
}
