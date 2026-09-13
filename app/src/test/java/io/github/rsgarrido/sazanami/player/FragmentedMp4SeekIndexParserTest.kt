package io.github.rsgarrido.sazanami.player

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FragmentedMp4SeekIndexParserTest {
    @Test
    fun validContiguousFragments_createSeekableIndexIncludingShortFinalFragment() {
        val file = fixture(
            fragments = listOf(
                FragmentSpec(start = 0, duration = 4_000),
                FragmentSpec(start = 4_000, duration = 4_000),
                FragmentSpec(start = 8_000, duration = 1_500)
            )
        )

        val index = parse(file, expectedDurationUs = 9_500_000)

        requireNotNull(index)
        assertArrayEquals(longArrayOf(0, 4_000_000, 8_000_000), index.timesUs())
        assertArrayEquals(file.moofOffsets, index.positions())
        assertEquals(9_500_000, index.durationUs)
    }

    @Test
    fun gap_rejectsIndex() {
        val file = fixture(
            fragments = listOf(
                FragmentSpec(start = 0, duration = 4_000),
                FragmentSpec(start = 4_001, duration = 4_000)
            )
        )

        assertNull(parse(file, expectedDurationUs = 8_001_000))
    }

    @Test
    fun overlap_rejectsIndex() {
        val file = fixture(
            fragments = listOf(
                FragmentSpec(start = 0, duration = 4_000),
                FragmentSpec(start = 3_999, duration = 4_000)
            )
        )

        assertNull(parse(file, expectedDurationUs = 7_999_000))
    }

    @Test
    fun missingTfdt_rejectsIndex() {
        val file = fixture(
            fragments = listOf(
                FragmentSpec(start = 0, duration = 4_000),
                FragmentSpec(start = 4_000, duration = 4_000, includeTfdt = false)
            )
        )

        assertNull(parse(file, expectedDurationUs = 8_000_000))
    }

    @Test
    fun unresolvedSampleDuration_rejectsIndex() {
        val file = fixture(
            fragments = listOf(FragmentSpec(start = 0, duration = 4_000, includeDuration = false)),
            trexDefaultDuration = 0
        )

        assertNull(parse(file, expectedDurationUs = 4_000_000))
    }

    @Test
    fun unresolvedSampleSize_rejectsIndex() {
        val file = fixture(
            fragments = listOf(FragmentSpec(start = 0, duration = 4_000, includeSize = false)),
            trexDefaultSize = 0
        )

        assertNull(parse(file, expectedDurationUs = 4_000_000))
    }

    @Test
    fun sampleRangeOutsideMdat_rejectsIndex() {
        val file = fixture(
            fragments = listOf(FragmentSpec(start = 0, duration = 4_000, declaredSize = 17))
        )

        assertNull(parse(file, expectedDurationUs = 4_000_000))
    }

    @Test
    fun editList_rejectsIndex() {
        val file = fixture(
            fragments = listOf(FragmentSpec(start = 0, duration = 4_000)),
            includeEditList = true
        )

        assertNull(parse(file, expectedDurationUs = 4_000_000))
    }

    @Test
    fun auxiliaryFragmentData_rejectsIndex() {
        val file = fixture(
            fragments = listOf(FragmentSpec(start = 0, duration = 4_000, includeAuxiliary = true))
        )

        assertNull(parse(file, expectedDurationUs = 4_000_000))
    }

    @Test
    fun encryptedSampleEntry_rejectsIndex() {
        val file = fixture(
            fragments = listOf(FragmentSpec(start = 0, duration = 4_000)),
            sampleEntryType = "enca"
        )

        assertNull(parse(file, expectedDurationUs = 4_000_000))
    }

    @Test
    fun incompleteDurationCoverage_rejectsIndex() {
        val file = fixture(fragments = listOf(FragmentSpec(start = 0, duration = 4_000)))

        assertNull(parse(file, expectedDurationUs = 5_000_000))
    }

    @Test
    fun existingIndexAndNonFragmentedMp4_doNotUseFallback() {
        val indexed = fixture(
            fragments = listOf(FragmentSpec(start = 0, duration = 4_000)),
            includeSidx = true
        )
        val nonFragmented = bytes(
            box("ftyp", ascii("M4A "), u32(0), ascii("isom")),
            movieBox(duration = 4_000, includeEditList = false, trexDuration = 0, trexSize = 0)
        )

        assertNull(parse(indexed, expectedDurationUs = 4_000_000))
        assertNull(
            FragmentedMp4SeekIndexParser.parse(
                reader = byteReader(nonFragmented),
                fileLength = nonFragmented.size.toLong(),
                expectedDurationUs = 4_000_000
            )
        )
    }

    @Test
    fun cacheIdentity_doesNotReuseIndexAfterSizeOrModificationChanges() {
        val cache = FragmentedMp4SeekIndexCache(maxEntries = 4)
        val index = FragmentedMp4SeekIndex(longArrayOf(100), longArrayOf(0), 4_000_000)
        var builds = 0
        fun load(identity: FragmentedMp4SourceIdentity): FragmentedMp4SeekIndex? =
            cache.getOrBuild(identity) {
                builds++
                index
            }

        load(FragmentedMp4SourceIdentity("content://audio/1", 1_000, 10))
        load(FragmentedMp4SourceIdentity("content://audio/1", 1_000, 10))
        load(FragmentedMp4SourceIdentity("content://audio/1", 1_001, 10))
        load(FragmentedMp4SourceIdentity("content://audio/1", 1_001, 11))

        assertEquals(3, builds)
    }

    private fun parse(file: Fixture, expectedDurationUs: Long): FragmentedMp4SeekIndex? =
        FragmentedMp4SeekIndexParser.parse(
            reader = byteReader(file.bytes),
            fileLength = file.bytes.size.toLong(),
            expectedDurationUs = expectedDurationUs
        )

    private fun byteReader(bytes: ByteArray) = Mp4RandomAccessReader { position, byteCount ->
        if (position < 0L || position > Int.MAX_VALUE || byteCount < 0) {
            null
        } else {
            val start = position.toInt()
            val end = start.toLong() + byteCount
            if (end > bytes.size) null else bytes.copyOfRange(start, end.toInt())
        }
    }

    private fun fixture(
        fragments: List<FragmentSpec>,
        includeEditList: Boolean = false,
        includeSidx: Boolean = false,
        trexDefaultDuration: Int = 0,
        trexDefaultSize: Int = 0,
        sampleEntryType: String = "ec-3"
    ): Fixture {
        val totalDuration = fragments.maxOf { it.start + it.duration }
        val prefixParts = mutableListOf(
            box("ftyp", ascii("M4A "), u32(0), ascii("isom")),
            movieBox(
                totalDuration,
                includeEditList,
                trexDefaultDuration,
                trexDefaultSize,
                sampleEntryType
            )
        )
        if (includeSidx) prefixParts += box("sidx", ByteArray(4))
        val output = ByteArrayOutputStream()
        prefixParts.forEach { part -> output.write(part) }
        val offsets = LongArray(fragments.size)
        fragments.forEachIndexed { index, spec ->
            offsets[index] = output.size().toLong()
            val placeholder = fragmentBox(spec, sequence = index + 1, baseDataOffset = 0)
            val mdatPayloadOffset = output.size().toLong() + placeholder.size + 8L
            val moof = fragmentBox(spec, sequence = index + 1, baseDataOffset = mdatPayloadOffset)
            output.write(moof)
            output.write(box("mdat", ByteArray(16)))
        }
        return Fixture(output.toByteArray(), offsets)
    }

    private fun movieBox(
        duration: Int,
        includeEditList: Boolean,
        trexDuration: Int,
        trexSize: Int,
        sampleEntryType: String = "ec-3"
    ): ByteArray {
        val tkhd = ByteArray(16).also { writeU32(it, 12, 1) }
        val mdhd = ByteArray(20).also {
            writeU32(it, 12, TIMESCALE)
            writeU32(it, 16, duration)
        }
        val hdlr = ByteArray(12).also { ascii("soun").copyInto(it, 8) }
        val stsd = box("stsd", ByteArray(4), u32(1), box(sampleEntryType))
        val mdia = box(
            "mdia",
            box("mdhd", mdhd),
            box("hdlr", hdlr),
            box("minf", box("stbl", stsd))
        )
        val trakParts = mutableListOf(box("tkhd", tkhd), mdia)
        if (includeEditList) trakParts += box("edts", box("elst", ByteArray(8)))
        val trex = ByteArray(24).also {
            writeU32(it, 4, 1)
            writeU32(it, 8, 1)
            writeU32(it, 12, trexDuration)
            writeU32(it, 16, trexSize)
        }
        return box("moov", box("trak", *trakParts.toTypedArray()), box("mvex", box("trex", trex)))
    }

    private fun fragmentBox(spec: FragmentSpec, sequence: Int, baseDataOffset: Long): ByteArray {
        val mfhd = box("mfhd", ByteArray(4), u32(sequence))
        val tfhd = box("tfhd", fullBox(flags = 0x000001), u32(1), u64(baseDataOffset))
        val trafParts = mutableListOf(tfhd)
        if (spec.includeTfdt) {
            trafParts += box("tfdt", fullBox(version = 1), u64(spec.start.toLong()))
        }
        var trunFlags = 0x000400
        if (spec.includeDuration) trunFlags = trunFlags or 0x000100
        if (spec.includeSize) trunFlags = trunFlags or 0x000200
        val sampleValues = mutableListOf<ByteArray>()
        if (spec.includeDuration) sampleValues += u32(spec.duration)
        if (spec.includeSize) sampleValues += u32(spec.declaredSize)
        sampleValues += u32(0)
        trafParts += box(
            "trun",
            fullBox(flags = trunFlags),
            u32(1),
            *sampleValues.toTypedArray()
        )
        if (spec.includeAuxiliary) trafParts += box("senc", ByteArray(4))
        return box("moof", mfhd, box("traf", *trafParts.toTypedArray()))
    }

    private fun fullBox(version: Int = 0, flags: Int = 0): ByteArray = byteArrayOf(
        version.toByte(),
        (flags ushr 16).toByte(),
        (flags ushr 8).toByte(),
        flags.toByte()
    )

    private fun u32(value: Int): ByteArray = ByteArray(4).also { writeU32(it, 0, value) }

    private fun u64(value: Long): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { it.writeLong(value) }
        bytes.toByteArray()
    }

    private fun writeU32(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun ascii(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII)

    private fun box(type: String, vararg payloads: ByteArray): ByteArray {
        require(type.length == 4)
        val size = 8 + payloads.sumOf { payload -> payload.size }
        return bytes(u32(size), ascii(type), *payloads)
    }

    private fun bytes(vararg parts: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        parts.forEach { part -> output.write(part) }
        return output.toByteArray()
    }

    private data class FragmentSpec(
        val start: Int,
        val duration: Int,
        val includeTfdt: Boolean = true,
        val includeDuration: Boolean = true,
        val includeSize: Boolean = true,
        val declaredSize: Int = 16,
        val includeAuxiliary: Boolean = false
    )

    private data class Fixture(val bytes: ByteArray, val moofOffsets: LongArray)

    private companion object {
        const val TIMESCALE = 1_000
    }
}
