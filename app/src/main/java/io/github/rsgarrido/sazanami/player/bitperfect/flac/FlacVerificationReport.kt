package io.github.rsgarrido.sazanami.player.bitperfect.flac

import io.github.rsgarrido.sazanami.player.usb.UsbDirectPcmFormat

internal object FlacVerificationReport {
    fun format(result: FlacVerificationResult?, running: Boolean = false): String = buildString {
        appendLine()
        appendLine("Exact FLAC Decode Verification — Session 9")
        appendLine("------------------------------------------")
        appendLine("Scope: selected-document decode verification only; Audio output: NONE; USB ownership: NONE; USB traffic: NONE")
        appendLine("Bit-perfect USB/song playback: NOT IMPLEMENTED BY SESSION 9; Media3 integration: NONE; DAC consumption: NOT ASSERTED")
        appendLine("Decoder: official upstream libFLAC 1.5.0; integer output: YES; float conversion: NO")
        appendLine("Provenance: https://github.com/xiph/flac/releases/tag/1.5.0; BSD-3-Clause/Xiph license, packaged in third_party_notices/libFLAC-COPYING.Xiph")
        appendLine("Input access: Storage Access Framework, read-only ParcelFileDescriptor; native dup owns only its duplicate; Kotlin owns/closes original")
        appendLine("Sequential decode; no seek/flush/reset. Non-seekable providers supported; file length is unavailable unless reported by a regular seekable fd.")
        appendLine("Cancellation: cooperative read/block checks; a blocking provider read may delay cleanup. No detached native worker; stale screen results are suppressed.")
        appendLine("Supported subset only: native FLAC / stereo / 16-bit at 44100 or 48000 Hz, or packed 24-bit at 48000 or 96000 Hz; no conversion")
        if (running) { appendLine("Lifecycle: verification running; final evidence pending"); return@buildString }
        if (result == null) { appendLine("Result: NOT_ATTEMPTED; debug action: Verify FLAC exact PCM (stereo 16/44.1, 16/48 or 24/48)"); return@buildString }
        appendLine("Result: ${result.status}")
        appendLine("Runtime libFLAC version: ${result.decoderVersion ?: "unavailable"}")
        if (result.counters.isEmpty()) {
            appendLine("Native decode evidence unavailable; no verification inferred.")
            result.exceptionType?.let { appendLine("Boundary exception type: $it; private message omitted") }
            appendLine("Kotlin original fd closed: ${result.originalFdClosed ?: "not opened"}")
            return@buildString
        }
        val c = result.counters
        fun value(name: String) = c[name]?.toString() ?: "unavailable"
        appendLine("Seekable: ${when (result.count("seekable")) { 1L -> "YES"; 0L -> "NO"; else -> "UNAVAILABLE" }}; file size: ${c["fileSize"]?.takeIf { it >= 0 } ?: "UNAVAILABLE"}")
        appendLine("Lifecycle reached: ${FlacVerificationPhase.entries.filter { result.count("lifecycleMask") and (1L shl it.ordinal) != 0L }.joinToString()}")
        appendLine("Decoder created: ${value("decoderCreated")}; init_stream status: ${value("initStatus")} (0 OK, -1 not attempted); metadata processed: ${value("metadataProcessed")}")
        appendLine("STREAMINFO received: ${value("streamInfoReceived")}; sample rate: ${value("sampleRate")}; channels: ${value("channels")}; bits/sample: ${value("bitsPerSample")}")
        appendLine("STREAMINFO total samples/channel: ${value("totalSamples")} ${if (result.count("totalSamples") == 0L) "(UNKNOWN)" else ""}")
        appendLine("Block-size range: ${value("minBlockSize")}..${value("maxBlockSize")}; encoded frame-size range: ${value("minFrameSize")}..${value("maxFrameSize")} (0 means unspecified)")
        appendLine("Format accepted: ${result.count("streamInfoReceived") == 1L && UsbDirectPcmFormat.supports(result.count("sampleRate"), result.count("channels"), result.count("bitsPerSample"))}")
        appendLine("FLAC MD5: checking enabled=${value("md5CheckingEnabled")}; reference available=${value("md5ReferenceAvailable")}; verification=${result.md5Verification}")
        appendLine("STREAMINFO MD5 reference: ${result.streamInfoMd5 ?: "unavailable"}")
        appendLine("API evidence: set_md5_checking(true) + get_md5_checking before init_stream; captured decoder state=${value("decoderState")} before finish resets it; finish called=${value("finishCalled")}; finish returned=${value("finishReturned")}")
        appendLine("libFLAC 1.5.0 finish(false) signals MD5 mismatch when a nonzero reference is checked. finish(true) alone is insufficient: zero MD5 disables checking internally; getter describes requested setting, not internal active checking.")
        appendLine("Callbacks: ${value("decoderCallbacks")}; complete decoded blocks: ${value("decodedBlocks")}; decoded stereo frames: ${value("decodedStereoFrames")}; samples/channel: ${value("decodedSamplesPerChannel")}")
        appendLine("Process succeeded: ${value("processSucceeded")}; clean end-of-stream: ${value("cleanEndOfStream")}; actual input EOF: ${value("physicalInputEof")}")
        appendLine("Decoder errors: ${value("decoderErrors")}; bounded error codes: ${result.errors.joinToString().ifEmpty { "none" }} (0 LOST_SYNC, 1 BAD_HEADER, 2 FRAME_CRC_MISMATCH, 3 UNPARSEABLE_STREAM, 4 BAD_METADATA, 5 OUT_OF_BOUNDS, 6 MISSING_FRAME)")
        appendLine("Canonical PCM: signed stereo PCM${result.count("bitsPerSample")}LE packed; L bytes then R bytes, little-endian; bytes/frame=${result.bytesPerFrame}; bytes hashed: ${value("canonicalPcmBytesHashed")}; FNV-1a 64: ${result.canonicalHash ?: "unavailable"}")
        appendLine("FNV-1a: offset cbf29ce484222325; prime 100000001b3; modulo 2^64; only ordered serialized PCM bytes, no metadata/compressed bytes/padding")
        appendLine("FLAC MD5 is libFLAC's original-audio reference check. Sazanami FNV is the identity of serialized PCM; it is not the STREAMINFO MD5 or a DAC/USB hash.")
        if (result.count("decodedStereoFrames") > 0L)
            appendLine("Range L: ${value("minLeftSample")}..${value("maxLeftSample")}; R: ${value("minRightSample")}..${value("maxRightSample")}; overall: ${value("minObservedSample")}..${value("maxObservedSample")}")
        else appendLine("Sample range: UNAVAILABLE (no PCM frames accepted)")
        appendLine("Out-of-range samples: ${value("outOfRangeSamples")}; format contradictions: ${value("formatContradictions")}; frame-order errors: ${value("frameOrderErrors")}")
        appendLine("Count verification: STREAMINFO=${value("totalSamples")}; decoded=${value("decodedStereoFrames")}; known total matches=${result.count("totalSamples") > 0L && result.count("totalSamples") == result.count("decodedStereoFrames")}")
        appendLine("Native duplicate closed: ${value("nativeDuplicateClosed")}; decoder deleted: ${value("decoderDeleted")}; Kotlin original closed: ${result.originalFdClosed}; resource errors: ${value("resourceErrors")}")
        appendLine("Read errno: ${value("readErrno")}; dup errno: ${value("dupErrno")}; no input URI/path/name/tags/artwork included")
        appendLine("Sparse PCM examples (maximum seven records, first frames/minimum/maximum/final, some may share an index):")
        result.examples.forEach { appendLine("  frame=${it.frame}; L=${it.left}; R=${it.right}; packed=${it.bytes.joinToString(" ") { byte -> byte.toString(16).padStart(2, '0') }}") }
        if (result.count("md5ReferenceAvailable") != 1L) appendLine("MD5 reference unavailable/all-zero: strongest exact verification cannot be awarded; choose another FLAC for first validation.")
        if (result.count("totalSamples") == 0L) appendLine("Total sample count unavailable: strongest exact verification cannot be awarded.")
        appendLine("Strong complete evidence: ${result.strongEvidence}; this result does not establish playback or DAC-internal sample reconstruction.")
    }
}
