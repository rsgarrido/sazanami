package io.github.rsgarrido.sazanami.player.bitperfect.flac

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.junit.Test

/** Synthetic JNI evidence/source boundaries only. No claimed FLAC file or hardware validation. */
class FlacVerificationTest {
    private fun source(relative: String) = listOf(Paths.get("src/main", relative), Paths.get("app/src/main", relative))
        .first { Files.isRegularFile(it) }.toFile().readText(Charsets.UTF_8)
    private fun snapshot(vararg changes: Pair<String, Long>): FlacNativeSnapshot {
        val facts = mutableMapOf("version" to 1L, "result" to 1L, "lifecycleMask" to 511L,
            "nativeFlacRecognized" to 1L, "decoderCreated" to 1L, "initStatus" to 0L,
            "md5CheckingEnabled" to 1L, "streamInfoReceived" to 1L, "sampleRate" to 44100L,
            "channels" to 2L, "bitsPerSample" to 16L, "totalSamples" to 6L,
            "minBlockSize" to 16L, "maxBlockSize" to 4096L, "md5ReferenceAvailable" to 1L,
            "metadataProcessed" to 1L, "processSucceeded" to 1L, "cleanEndOfStream" to 1L,
            "physicalInputEof" to 1L, "finishCalled" to 1L, "finishReturned" to 1L, "md5Verification" to 1L,
            "decoderCallbacks" to 1L, "decodedBlocks" to 1L, "decodedStereoFrames" to 6L,
            "decodedSamplesPerChannel" to 6L, "canonicalPcmBytesHashed" to 24L,
            "pcmFnv1a" to 0x255d40cc915e3c4dL, "nativeDuplicateClosed" to 1L, "decoderDeleted" to 1L,
            "seekable" to 1L, "fileSize" to 100L, "decoderState" to 4L)
        facts.putAll(changes)
        return FlacNativeSnapshot(FlacNativeEvidence.fields.map { facts[it] ?: 0L }.toLongArray(),
            ByteArray(16) { (it+1).toByte() }, longArrayOf(0,0,0,0,0,0,0), longArrayOf(), "1.5.0")
    }
    private fun result(vararg changes: Pair<String, Long>) = FlacNativeEvidence.decode(snapshot(*changes)).copy(originalFdClosed = true)
    private fun rejects(snapshot: FlacNativeSnapshot) {
        try { FlacNativeEvidence.decode(snapshot); fail("Invalid native schema accepted") } catch (_: IllegalArgumentException) {}
    }
    @Test fun strongestStatusRequiresAllDecodeAndCleanupEvidence() {
        assertEquals(FlacVerificationStatus.EXACT_FLAC_DECODE_VERIFIED, result().status)
        assertTrue(result().strongEvidence)
        for (name in listOf("nativeFlacRecognized","decoderCreated","streamInfoReceived","metadataProcessed",
            "md5CheckingEnabled","md5ReferenceAvailable","md5Verification","processSucceeded","cleanEndOfStream",
            "physicalInputEof","finishCalled","finishReturned","decoderCallbacks","decodedBlocks","totalSamples",
            "nativeDuplicateClosed","decoderDeleted","lifecycleMask"))
            assertEquals("Missing $name", FlacVerificationStatus.NATIVE_EVIDENCE_INVALID, result(name to 0).status)
        assertEquals(FlacVerificationStatus.RESOURCE_CLEANUP_FAILED, result().copy(originalFdClosed = false).status)
        assertFalse(result().copy(originalFdClosed = null).strongEvidence)
    }
    @Test fun formatGateNeverAcceptsDownconversion() {
        for ((name, value) in listOf("sampleRate" to 48000L, "sampleRate" to 96000L, "sampleRate" to 192000L,
            "channels" to 1L, "channels" to 6L, "bitsPerSample" to 24L, "bitsPerSample" to 32L))
            assertFalse(result(name to value).strongEvidence)
        val unsupported = result("result" to 2, "sampleRate" to 96000, "bitsPerSample" to 24)
        assertEquals(FlacVerificationStatus.UNSUPPORTED_FLAC_FORMAT, unsupported.status)
        assertTrue(FlacVerificationReport.format(unsupported).contains("sample rate: 96000"))
    }
    @Test fun unavailableMd5AndTotalRemainWeakerResults() {
        val unavailable = FlacNativeEvidence.decode(snapshot("result" to 3, "md5ReferenceAvailable" to 0,
            "md5Verification" to 0).copy(md5 = ByteArray(16))).copy(originalFdClosed = true)
        assertEquals(FlacVerificationStatus.FLAC_DECODE_COMPLETED_MD5_UNAVAILABLE, unavailable.status)
        assertFalse(unavailable.strongEvidence)
        assertEquals(FlacVerificationStatus.TOTAL_SAMPLE_COUNT_UNAVAILABLE, result("result" to 4, "totalSamples" to 0).status)
        val zeroDigest = FlacNativeEvidence.decode(snapshot().copy(md5 = ByteArray(16))).copy(originalFdClosed = true)
        assertFalse(zeroDigest.strongEvidence)
    }
    @Test fun mismatchAndCorruptionNeverBecomeSuccess() {
        assertEquals(FlacVerificationStatus.FLAC_MD5_MISMATCH, result("result" to 5, "finishReturned" to 0, "md5Verification" to 2).status)
        for (name in listOf("decoderErrors","outOfRangeSamples","formatContradictions","frameOrderErrors","resourceErrors"))
            assertFalse(result(name to 1).strongEvidence)
        assertFalse(result("decoderState" to 7).strongEvidence)
        assertEquals(FlacVerificationStatus.TRUNCATED_OR_UNEXPECTED_EOF, result("result" to 7, "totalSamples" to 10).status)
    }
    @Test fun checkedByteAndFrameCountsAreRequired() {
        assertFalse(result("canonicalPcmBytesHashed" to 23).strongEvidence)
        assertFalse(result("decodedStereoFrames" to 5).strongEvidence)
        assertFalse(result("decodedSamplesPerChannel" to 5).strongEvidence)
        assertFalse(result("decodedStereoFrames" to Long.MAX_VALUE, "totalSamples" to Long.MAX_VALUE).strongEvidence)
    }
    @Test fun malformedSchemaAndExcessExamplesAreRejected() {
        rejects(snapshot().copy(numbers = LongArray(2)))
        rejects(snapshot("version" to 2)); rejects(snapshot("result" to 99))
        rejects(snapshot().copy(md5 = ByteArray(15))); rejects(snapshot().copy(examples = LongArray(8*7)))
        rejects(snapshot().copy(examples = longArrayOf(6,0,0,0,0,0,0)))
        rejects(snapshot().copy(examples = longArrayOf(0,32768,0,0,0,0,0)))
        rejects(snapshot().copy(examples = longArrayOf(0,1024,-1024,0,4,0,4)))
        rejects(snapshot().copy(errors = LongArray(9))); rejects(snapshot().copy(decoderVersion = "unknown"))
    }
    @Test fun resultCopiesNativeArraysAndPreservesSignedPackedExamples() {
        val input = snapshot().copy(examples = longArrayOf(0,-32768,32767,0,128,255,127))
        val decoded = FlacNativeEvidence.decode(input)
        input.numbers.fill(99); input.md5.fill(0); input.examples.fill(0)
        assertEquals(6L, decoded.count("decodedStereoFrames"))
        assertEquals(-32768, decoded.examples.single().left)
        assertEquals(listOf(0,128,255,127), decoded.examples.single().bytes)
        assertEquals("0102030405060708090a0b0c0d0e0f10", decoded.streamInfoMd5)
    }
    @Test fun unsignedFnvHexAndUnknownProviderSizeAreReported() {
        val value = result("seekable" to 0, "fileSize" to -1, "pcmFnv1a" to -1)
        assertEquals("ffffffffffffffff", value.canonicalHash)
        val report = FlacVerificationReport.format(value)
        assertTrue(report.contains("Seekable: NO; file size: UNAVAILABLE"))
        assertFalse(report.contains("file size: 0"))
    }
    @Test fun reportSeparatesMd5FromPcmAndNeverClaimsPlayback() {
        val report = FlacVerificationReport.format(result())
        listOf("Exact FLAC Decode Verification — Session 9","Audio output: NONE","USB ownership: NONE",
            "USB traffic: NONE","Media3 integration: NONE","DAC consumption: NOT ASSERTED",
            "FNV-1a 64: 255d40cc915e3c4d","STREAMINFO MD5 reference: 0102030405060708090a0b0c0d0e0f10",
            "finish(true) alone is insufficient","NOT IMPLEMENTED BY SESSION 9").forEach { assertTrue("Missing $it", report.contains(it)) }
        assertFalse(report.contains("content://")); assertFalse(report.contains("Bit-perfect ACTIVE"))
        assertTrue(report.length < 12000)
    }
    @Test fun noNativeResultNeverInventsEvidence() {
        val report = FlacVerificationReport.format(FlacVerificationResult(FlacVerificationStatus.INPUT_OPEN_FAILED))
        assertTrue(report.contains("Native decode evidence unavailable"))
        assertFalse(report.contains("FNV-1a 64:"))
        assertFalse(FlacVerificationResult().strongEvidence)
    }
    @Test fun errorCodesAndPartialCountsRemainVisible() {
        val partial = FlacNativeEvidence.decode(snapshot("result" to 6, "decoderErrors" to 1,
            "cleanEndOfStream" to 0).copy(errors = longArrayOf(2))).copy(originalFdClosed = true)
        val report = FlacVerificationReport.format(partial)
        assertTrue(report.contains("bounded error codes: 2"))
        assertTrue(report.contains("decoded stereo frames: 6"))
        assertEquals(FlacVerificationStatus.FLAC_DECODER_ERROR, partial.status)
    }
    @Test fun actionUsesSafReadOnlyAndDebugGuards() {
        val ui = source("java/io/github/rsgarrido/sazanami/ui/settings/FlacVerificationDiagnosticsSection.kt")
        val controller = source("java/io/github/rsgarrido/sazanami/player/bitperfect/flac/FlacVerificationController.kt")
        assertTrue(ui.contains("if (!BuildConfig.DEBUG) return")); assertTrue(ui.contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(controller.contains("if (!BuildConfig.DEBUG")); assertTrue(controller.contains("openFileDescriptor(uri, \"r\")"))
        assertFalse(controller.contains("getDisplayName")); assertFalse(controller.contains("takePersistableUriPermission"))
    }
    @Test fun newSourceHasNoPlaybackUsbFilePathOrFloatApi() {
        val native = source("cpp/flac/flac_verifier.cpp") + source("cpp/flac/flac_decode_core.h") + source("cpp/flac/flac_pcm_core.h")
        val kotlin = source("java/io/github/rsgarrido/sazanami/player/bitperfect/flac/FlacNativeVerifier.kt") +
            source("java/io/github/rsgarrido/sazanami/player/bitperfect/flac/FlacVerificationController.kt")
        for (forbidden in listOf("AudioTrack","ExoPlayer","MediaCodec","AudioSink","UsbFsDiagnostics",
            "USBDEVFS","sendPcmToUsb","decodeSongToPcm","readBytes(","float ","double ","ReplayGain"))
            assertFalse("Unexpected $forbidden", (native+kotlin).contains(forbidden))
        assertFalse(kotlin.contains("File(uri")); assertFalse(native.contains("init_ogg_stream("))
    }
    @Test fun libFlacMd5ApiAndErrorStopsAreExplicit() {
        val native = source("cpp/flac/flac_decode_core.h")
        assertTrue(native.contains("bool whole = true"))
        assertTrue(source("cpp/flac/flac_verifier.cpp").contains("Verification verification(std::move(pending), [=]"))
        assertFalse(source("cpp/flac/flac_verifier.cpp").contains(", false)"))
        assertTrue(native.contains("set_md5_checking(decoder.get(), wholeFile)"))
        assertTrue(native.indexOf("set_md5_checking(decoder.get(), wholeFile)") < native.indexOf("init_stream(decoder.get()"))
        assertTrue(native.indexOf("facts[DecoderState] = FLAC__stream_decoder_get_state") < native.indexOf("facts[FinishOk] = FLAC__stream_decoder_finish"))
        assertTrue(native.contains("v.failure != Outcome::Pending"))
        assertFalse(native.contains("stream_decoder_seek_absolute(")); assertFalse(native.contains("stream_decoder_flush("))
    }
    @Test fun fdAndCoroutineLifetimeAreStructuredWithoutStalePublication() {
        val native = source("cpp/flac/flac_verifier.cpp")
        val kotlin = source("java/io/github/rsgarrido/sazanami/player/bitperfect/flac/FlacVerificationController.kt")
        assertTrue(native.contains("::dup(borrowedFd)")); assertFalse(native.contains("::close(borrowedFd)"))
        assertTrue(native.indexOf("verification.finalize()") < native.indexOf("return snapshotFlac(env, verification)"))
        assertTrue(kotlin.contains("withContext(NonCancellable)")); assertTrue(kotlin.contains("worker.await()"))
        assertTrue(kotlin.contains("generation == request && parent.isActive && !flag.cancelled"))
        assertTrue(kotlin.contains("descriptor.close()")); assertFalse(native.contains(".detach("))
    }
    @Test fun buildIsPinnedLocalDecoderOnlyAndUsbTargetsUnchanged() {
        val cmake = source("cpp/third_party/flac/CMakeLists.txt")
        assertTrue(cmake.contains("f2c1c76592a82ffff8413ba3c4a1299b6c7ab06c734dee03fd88630485c2b920"))
        assertTrue(cmake.contains("FLAC__INTEGER_ONLY_LIBRARY FLAC__NO_ASM"))
        assertFalse(cmake.contains("stream_encoder.c")); assertFalse(cmake.contains("ogg_decoder_aspect.c"))
        assertFalse(cmake.contains("FetchContent")); assertFalse(cmake.contains("https://"))
    }
}
