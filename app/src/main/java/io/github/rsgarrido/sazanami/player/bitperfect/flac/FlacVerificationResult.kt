package io.github.rsgarrido.sazanami.player.bitperfect.flac

import androidx.annotation.Keep
import io.github.rsgarrido.sazanami.player.usb.UsbDirectPcmFormat
import io.github.rsgarrido.sazanami.player.usb.decodeDirectPcmExample

internal enum class FlacVerificationStatus {
    NOT_ATTEMPTED, EXACT_FLAC_DECODE_VERIFIED, UNSUPPORTED_FLAC_FORMAT,
    FLAC_DECODE_COMPLETED_MD5_UNAVAILABLE, TOTAL_SAMPLE_COUNT_UNAVAILABLE, FLAC_MD5_MISMATCH,
    FLAC_DECODER_ERROR, TRUNCATED_OR_UNEXPECTED_EOF, PCM_SAMPLE_OUT_OF_RANGE, FORMAT_CONTRADICTION,
    INPUT_OPEN_FAILED, CANCELLED, NOT_NATIVE_FLAC, COUNTER_OVERFLOW, RESOURCE_CLEANUP_FAILED,
    SEEKED_SOURCE_EOF_NOT_WHOLE_FILE_VERIFIED, NATIVE_UNAVAILABLE, NATIVE_EVIDENCE_INVALID
}
internal enum class FlacVerificationPhase { IDLE, DOCUMENT_OPENED, DECODER_CREATED, METADATA_READING,
    FORMAT_ACCEPTED, DECODING, END_OF_STREAM, VERIFYING, FINISHED }
internal data class FlacPcmExample(val frame: Long, val left: Int, val right: Int, val bytes: List<Int>)
@Keep internal data class FlacNativeSnapshot(val numbers: LongArray, val md5: ByteArray,
    val examples: LongArray, val errors: LongArray, val decoderVersion: String)
internal data class FlacVerificationResult(
    val nativeStatus: FlacVerificationStatus = FlacVerificationStatus.NOT_ATTEMPTED,
    val counters: Map<String, Long> = emptyMap(), val streamInfoMd5: String? = null,
    val examples: List<FlacPcmExample> = emptyList(), val errors: List<Int> = emptyList(),
    val decoderVersion: String? = null, val originalFdClosed: Boolean? = null,
    val exceptionType: String? = null
) {
    fun count(field: String) = counters[field] ?: 0L
    val canonicalHash get() = counters["pcmFnv1a"]?.toULong()?.toString(16)?.padStart(16, '0')
    val pcmFormat get()=UsbDirectPcmFormat.resolve(count("sampleRate"),count("channels"),count("bitsPerSample"))
    val bytesPerFrame get()=pcmFormat?.bytesPerFrame?.toLong() ?: 0L
    private val sampleLimit get()=pcmFormat?.let { 1L shl (it.bitsPerSample-1) } ?: 0L
    private val completeDecodeEvidence get() = decoderVersion == "1.5.0" && exceptionType == null &&
        count("nativeFlacRecognized") == 1L && count("decoderCreated") == 1L && count("initStatus") == 0L &&
        count("streamInfoReceived") == 1L && UsbDirectPcmFormat.supports(count("sampleRate"), count("channels"), count("bitsPerSample")) && count("metadataProcessed") == 1L &&
        count("processSucceeded") == 1L && count("cleanEndOfStream") == 1L && count("physicalInputEof") == 1L &&
        count("decoderState") == 4L && (count("lifecycleMask") and 511L) == 511L &&
        count("finishCalled") == 1L && count("finishReturned") == 1L &&
        count("decoderErrors") == 0L && errors.isEmpty() && count("formatContradictions") == 0L &&
        count("frameOrderErrors") == 0L && count("outOfRangeSamples") == 0L &&
        count("totalSamples") > 0L && count("decodedStereoFrames") == count("totalSamples") &&
        bytesPerFrame>0 && count("decodedStereoFrames") <= Long.MAX_VALUE / bytesPerFrame &&
        count("decodedSamplesPerChannel") == count("decodedStereoFrames") &&
        count("canonicalPcmBytesHashed") == count("decodedStereoFrames") * bytesPerFrame && canonicalHash != null &&
        count("decoderCallbacks") > 0L && count("decodedBlocks") == count("decoderCallbacks") &&
        listOf("minLeftSample", "maxLeftSample", "minRightSample", "maxRightSample", "minObservedSample", "maxObservedSample")
            .all { count(it) in -sampleLimit until sampleLimit } &&
        count("nativeDuplicateClosed") == 1L && count("decoderDeleted") == 1L &&
        count("resourceErrors") == 0L && originalFdClosed == true
    val strongEvidence get() = completeDecodeEvidence && count("md5CheckingEnabled") == 1L && count("md5ReferenceAvailable") == 1L &&
        streamInfoMd5 != null && streamInfoMd5 != "0".repeat(32) && count("md5Verification") == 1L
    /** Clean whole-file decoding, without an independent source-reference claim. */
    val referenceUnavailableComplete get() = counters.keys.containsAll(FlacNativeEvidence.fields) && completeDecodeEvidence &&
        count("version")==2L && count("result")==FlacVerificationStatus.FLAC_DECODE_COMPLETED_MD5_UNAVAILABLE.ordinal.toLong() &&
        nativeStatus==FlacVerificationStatus.FLAC_DECODE_COMPLETED_MD5_UNAVAILABLE && count("md5CheckingEnabled")==1L &&
        count("md5ReferenceAvailable")==0L && streamInfoMd5=="0".repeat(32) && count("md5Verification")==0L &&
        count("minBlockSize") in 1L..65535L && count("maxBlockSize") in count("minBlockSize")..65535L &&
        (count("minFrameSize")==0L || count("maxFrameSize")==0L || count("minFrameSize")<=count("maxFrameSize")) &&
        count("readErrno")==0L && count("dupErrno")==0L
    val status get() = when {
        originalFdClosed == false -> FlacVerificationStatus.RESOURCE_CLEANUP_FAILED
        nativeStatus == FlacVerificationStatus.EXACT_FLAC_DECODE_VERIFIED && !strongEvidence -> FlacVerificationStatus.NATIVE_EVIDENCE_INVALID
        else -> nativeStatus
    }
    val md5Verification get() = when (count("md5Verification")) { 1L -> "PASS"; 2L -> "FAIL"; else -> "UNAVAILABLE" }
}
internal object FlacNativeEvidence {
    val fields = listOf("version", "result", "lifecycleMask", "nativeFlacRecognized", "decoderCreated", "initStatus",
        "md5CheckingEnabled", "streamInfoReceived", "sampleRate", "channels", "bitsPerSample", "totalSamples",
        "minBlockSize", "maxBlockSize", "minFrameSize", "maxFrameSize", "md5ReferenceAvailable", "metadataProcessed",
        "processSucceeded", "cleanEndOfStream", "physicalInputEof", "finishCalled", "finishReturned", "md5Verification",
        "decoderCallbacks", "decodedBlocks", "decodedStereoFrames", "decodedSamplesPerChannel", "canonicalPcmBytesHashed",
        "pcmFnv1a", "minLeftSample", "maxLeftSample", "minRightSample", "maxRightSample", "minObservedSample",
        "maxObservedSample", "outOfRangeSamples", "formatContradictions", "decoderErrors", "readErrno", "seekable",
        "fileSize", "nativeDuplicateClosed", "decoderDeleted", "resourceErrors", "frameOrderErrors", "decoderState", "dupErrno")
    fun decode(value: FlacNativeSnapshot): FlacVerificationResult {
        require(value.numbers.size == fields.size && value.numbers[0] == 2L && value.numbers[1] in 1L..15L &&
            value.md5.size == 16 && value.examples.size % 9 == 0 && value.examples.size <= 7 * 9 && value.errors.size <= 8)
        require(value.decoderVersion == "1.5.0")
        val numbers = fields.zip(value.numbers.toList()).toMap()
        if(value.numbers[1]==15L) require(numbers.getValue("md5Verification")==0L && numbers.getValue("cleanEndOfStream")==1L &&
            numbers.getValue("nativeDuplicateClosed")==1L && numbers.getValue("decoderDeleted")==1L)
        val allowedSigned = setOf("pcmFnv1a", "initStatus", "decoderState", "seekable", "fileSize",
            "minLeftSample", "maxLeftSample", "minRightSample", "maxRightSample", "minObservedSample", "maxObservedSample")
        require(numbers.all { (name, number) -> name in allowedSigned || number >= 0L })
        require(numbers.getValue("seekable") in -1L..1L && numbers.getValue("md5Verification") in 0L..2L &&
            numbers.getValue("fileSize") >= -1L)
        val format=UsbDirectPcmFormat.resolve(numbers.getValue("sampleRate"),numbers.getValue("channels"),numbers.getValue("bitsPerSample"))
        val pcm = value.examples.toList().chunked(9).map { row ->
            require(row[0]<numbers.getValue("decodedStereoFrames"))
            val example=decodeDirectPcmExample(row,requireNotNull(format))
            FlacPcmExample(example.index,example.left,example.right,example.bytes)
        }
        require(value.errors.all { it in 0L..6L })
        return FlacVerificationResult(FlacVerificationStatus.entries[value.numbers[1].toInt()], numbers,
            value.md5.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') },
            pcm, value.errors.map { it.toInt() }, value.decoderVersion)
    }
}
