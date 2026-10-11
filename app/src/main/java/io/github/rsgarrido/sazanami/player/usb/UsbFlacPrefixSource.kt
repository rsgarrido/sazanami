package io.github.rsgarrido.sazanami.player.usb

import androidx.annotation.Keep
import io.github.rsgarrido.sazanami.BuildConfig
import io.github.rsgarrido.sazanami.player.bitperfect.flac.FlacNativeEvidence
import io.github.rsgarrido.sazanami.player.bitperfect.flac.FlacNativeSnapshot
import io.github.rsgarrido.sazanami.player.bitperfect.flac.FlacVerificationResult
import io.github.rsgarrido.sazanami.player.bitperfect.flac.FlacVerificationStatus
import kotlinx.coroutines.*

@Keep internal data class UsbFlacSourceSnapshot(val numbers: LongArray, val examples: LongArray, val decoder: Any?)
internal data class UsbFlacSourceResult(val counters: Map<String,Long> = emptyMap(),
    val decoder: FlacVerificationResult? = null, val examples: List<UsbPcmFrameEvidence> = emptyList(), val exceptionType: String? = null) {
    fun count(name: String)=counters[name] ?: 0L
    val pcmFormat get()=UsbDirectPcmFormat.resolve(count("sampleRate"),count("channels"),count("bitsPerSample"))
    val bytesPerFrame get()=pcmFormat?.bytesPerFrame?.toLong() ?: 0L
    val reasons get() = UsbFlacSourceEvidence.reasons.filterIndexed { i,_ -> count("sourceIntegrityMask") and (1L shl i) != 0L }
    val primed get() = exceptionType == null && count("sourceIntegrityMask")==0L && count("metadataAccepted")==1L &&
        count("initialPrimedFrames") in (if (count("fullTrackMode")==1L) minOf(8192L,count("totalSamples")) else 8192L)..16384L &&
        (count("fullTrackMode")==0L || count("totalSamples")>0L) &&
        (count("initialPrimedFrames")>=8192L || count("trueEofReached")==1L) && UsbDirectPcmFormat.supports(count("sampleRate"),count("channels"),count("bitsPerSample"))
    val clean get() = counters.keys.containsAll(UsbFlacSourceEvidence.fields) && primed && count("producerStopped")==1L && count("producerJoined")==1L &&
        count("decoderReleased")==1L && count("nativeFlacFdClosed")==1L && count("queueReleased")==1L &&
        count("queueUnderruns")==0L && count("sourceFrameGapCount")==0L && count("sourceFrameDuplicateCount")==0L &&
        count("sourceFrameOrderErrors")==0L && count("preparedButUnsubmittedFrames")==0L &&
        count("framesConsumedFromQueue")>0L && bytesPerFrame>0 && count("framesDecoded")<=Long.MAX_VALUE/bytesPerFrame && count("framesDecoded")>=count("framesEnqueued") &&
        count("framesEnqueued")>=count("framesConsumedFromQueue") &&
        count("queueFramesAtStop")==count("framesEnqueued")-count("framesConsumedFromQueue") &&
        count("prefetchedFramesDiscardedAtStop")==count("queueFramesAtStop") &&
        count("firstSourceFrameEnqueued")==0L && count("lastSourceFrameEnqueued")==count("framesEnqueued")-1L &&
        count("expectedNextEnqueueFrame")==count("framesEnqueued") && count("expectedNextConsumeFrame")==count("framesConsumedFromQueue") &&
        count("initialPrimedFrames")<=16384L && count("queueFramesAtStop") in 0L..16384L &&
        count("minimumQueueDepthDuringStreaming") in 0L..count("maximumQueueDepthDuringStreaming") &&
        count("maximumQueueDepthDuringStreaming")<=16384L && count("firstSourceFrameConsumed")==0L && count("lastSourceFrameConsumed")==count("framesConsumedFromQueue")-1L &&
        (decoder?.let { it.pcmFormat==pcmFormat && it.nativeStatus==FlacVerificationStatus.CANCELLED && it.decoderVersion=="1.5.0" && it.errors.isEmpty() && it.exceptionType==null &&
            it.count("canonicalPcmBytesHashed")==count("framesDecoded")*bytesPerFrame &&
            it.count("nativeDuplicateClosed")==1L && it.count("decoderDeleted")==1L && it.count("resourceErrors")==0L &&
            it.count("finishCalled")==1L && it.count("nativeFlacRecognized")==1L && it.count("initStatus")==0L &&
            it.count("metadataProcessed")==1L && it.count("streamInfoReceived")==1L && it.count("md5Verification")==0L &&
            it.count("frameOrderErrors")==0L && it.count("md5CheckingEnabled")==0L && it.count("decoderErrors")==0L &&
            it.count("outOfRangeSamples")==0L && it.count("formatContradictions")==0L } == true)
    val fullComplete get() = counters.keys.containsAll(UsbFlacSourceEvidence.fields) && primed && count("fullTrackMode")==1L && count("trueEofReached")==1L &&
        count("producerFinishedNaturally")==1L && count("producerStopped")==1L && count("producerJoined")==1L &&
        count("decoderReleased")==1L && count("nativeFlacFdClosed")==1L && count("queueReleased")==1L &&
        count("md5VerificationPass")==1L && count("decodedTotalMatchesStreamInfo")==1L &&
        bytesPerFrame>0 && count("totalSamples") in 1L..(Long.MAX_VALUE/bytesPerFrame) &&
        listOf("framesDecoded","framesEnqueued","framesConsumedFromQueue","expectedNextEnqueueFrame","expectedNextConsumeFrame")
            .all { counters[it]==count("totalSamples") } &&
        listOf("queueFramesAtStop","prefetchedFramesDiscardedAtStop","preparedButUnsubmittedFrames","queueUnderruns",
            "consumerEmptyEvents","sourceFrameGapCount","sourceFrameDuplicateCount","sourceFrameOrderErrors").all { counters[it]==0L } &&
        count("firstSourceFrameEnqueued")==0L && count("firstSourceFrameConsumed")==0L &&
        count("lastSourceFrameEnqueued")==count("totalSamples")-1 && count("lastSourceFrameConsumed")==count("totalSamples")-1 &&
        count("minimumQueueDepthDuringStreaming") in 0L..count("maximumQueueDepthDuringStreaming") &&
        count("maximumQueueDepthDuringStreaming")<=16384L &&
        count("allEnqueuedPrefetchPcmHash")==count("consumedSourcePcmHash") &&
        decoder?.let { it.pcmFormat==pcmFormat && it.count("totalSamples")==count("totalSamples") && it.nativeStatus==FlacVerificationStatus.EXACT_FLAC_DECODE_VERIFIED && it.copy(originalFdClosed=true).strongEvidence &&
            it.count("pcmFnv1a")==count("consumedSourcePcmHash") }==true
}
internal object UsbFlacSourceEvidence {
    val fields=listOf("version","generation","streamGeneration","sourceIntegrityMask","initialPrimedFrames","metadataAccepted",
        "sampleRate","channels","bitsPerSample","totalSamples","framesDecoded","framesEnqueued","framesConsumedFromQueue",
        "preparedButUnsubmittedFrames","queueFramesAtStop","prefetchedFramesDiscardedAtStop","minimumQueueDepthDuringStreaming",
        "maximumQueueDepthDuringStreaming","producerFullWaitCount","consumerEmptyEvents","queueUnderruns",
        "firstSourceFrameEnqueued","lastSourceFrameEnqueued","firstSourceFrameConsumed","lastSourceFrameConsumed",
        "expectedNextEnqueueFrame","expectedNextConsumeFrame","sourceFrameGapCount","sourceFrameDuplicateCount","sourceFrameOrderErrors",
        "consumedSourcePcmHash","allEnqueuedPrefetchPcmHash","producerStopped","producerJoined","decoderReleased","nativeFlacFdClosed","queueReleased","fullTrackMode","trueEofReached","producerFinishedNaturally","md5VerificationPass","decodedTotalMatchesStreamInfo")
    val reasons=listOf("FLAC_DECODER_ERROR","FLAC_DECODER_INIT_FAILED","UNSUPPORTED_FLAC_FORMAT","PCM_SAMPLE_OUT_OF_RANGE",
        "SOURCE_EOF_BEFORE_DURATION","QUEUE_PRIME_FAILED","QUEUE_UNDERRUN","SOURCE_FRAME_GAP","SOURCE_FRAME_DUPLICATE",
        "SOURCE_FRAME_ORDER_ERROR","SOURCE_SUBMITTED_HASH_MISMATCH","CANCELLED","RESOURCE_CLEANUP_FAILED","FLAC_INPUT_OPEN_FAILED","NOT_NATIVE_FLAC","FLAC_METADATA_INVALID","SOURCE_FORMAT_CONTRADICTION","FLAC_MD5_UNAVAILABLE","FLAC_MD5_MISMATCH","STREAMINFO_TOTAL_MISMATCH_OR_UNAVAILABLE","TERMINAL_TAIL_ACCOUNTING_FAILED")
    fun decode(value: UsbFlacSourceSnapshot,generation: Long,stream: Long): UsbFlacSourceResult {
        require(value.numbers.size==fields.size && value.numbers[0]==3L && value.numbers[1]==generation && value.numbers[2]==stream &&
            value.examples.size%9==0 && value.examples.size<=5*9)
        val counters=fields.zip(value.numbers.toList()).toMap()
        val signed=setOf("consumedSourcePcmHash","allEnqueuedPrefetchPcmHash")
        val indices=setOf("firstSourceFrameEnqueued","lastSourceFrameEnqueued","firstSourceFrameConsumed","lastSourceFrameConsumed")
        require(counters.all { (name,number) -> name in signed || if (name in indices) number>=-1L else number>=0L })
        require(countBooleanFields.all { counters.getValue(it) in 0L..1L } &&
            counters.getValue("sourceIntegrityMask") in 0L..2097151L && counters.getValue("initialPrimedFrames")<=16384L)
        val format=UsbDirectPcmFormat.resolve(counters.getValue("sampleRate"),counters.getValue("channels"),counters.getValue("bitsPerSample"))
        val examples=value.examples.toList().chunked(9).map {
            require(it[0]<counters.getValue("framesConsumedFromQueue"))
            decodeDirectPcmExample(it,requireNotNull(format))
        }
        require(value.decoder == null || value.decoder is FlacNativeSnapshot)
        val decoder=(value.decoder as? FlacNativeSnapshot)?.let(FlacNativeEvidence::decode)
        return UsbFlacSourceResult(counters,decoder,examples)
    }
    private val countBooleanFields=listOf("metadataAccepted","producerStopped","producerJoined","decoderReleased","nativeFlacFdClosed","queueReleased","fullTrackMode","trueEofReached","producerFinishedNaturally","md5VerificationPass","decodedTotalMatchesStreamInfo")
}
@Keep internal object UsbFlacPrefixNative {
    private val available by lazy { runCatching { System.loadLibrary("sazanami_usb_diagnostics"); true }.getOrDefault(false) }
    fun create(fd: Int,generation: Long,stream: Long,fullTrack: Boolean=false): Long { check(BuildConfig.DEBUG && available);return nativeCreate(fd,generation,stream,fullTrack) }
    fun prime(handle: Long,generation: Long,stream: Long)=UsbFlacSourceEvidence.decode(nativePrime(handle,generation,stream),generation,stream)
    fun healthy(handle: Long,generation: Long,stream: Long)=nativeHealthy(handle,generation,stream)
    fun stop(handle: Long,generation: Long,stream: Long)=nativeStop(handle,generation,stream)
    fun finish(handle: Long,generation: Long,stream: Long)=UsbFlacSourceEvidence.decode(nativeFinish(handle,generation,stream),generation,stream)
    private external fun nativeCreate(fd: Int,generation: Long,stream: Long,fullTrack: Boolean): Long
    private external fun nativePrime(handle: Long,generation: Long,stream: Long): UsbFlacSourceSnapshot
    private external fun nativeHealthy(handle: Long,generation: Long,stream: Long): Boolean
    private external fun nativeStop(handle: Long,generation: Long,stream: Long)
    private external fun nativeFinish(handle: Long,generation: Long,stream: Long): UsbFlacSourceSnapshot
}
internal interface UsbFlacSourceLifecycle {
    val handle: Long
    val result: UsbFlacSourceResult
    fun requestStop()
    fun healthy(): Boolean
    suspend fun prepare(access: () -> UsbClockQueryStatus?): Boolean
    suspend fun finish()
}
/** One narrow debug source owner. Every native operation uses both attachment and stream identities. */
internal class UsbFlacPrefixSource(private val fd: Int,private val generation: Long,private val stream: Long, private val fullTrack: Boolean=false) : UsbFlacSourceLifecycle {
    @Volatile override var handle=0L; private set
    override var result=UsbFlacSourceResult(); private set
    @Volatile private var stopRequested=false
    override fun requestStop() { stopRequested=true;val h=handle;if(h>0) runCatching { UsbFlacPrefixNative.stop(h,generation,stream) } }
    override fun healthy() = handle>0 && result.primed && runCatching { UsbFlacPrefixNative.healthy(handle,generation,stream) }.getOrDefault(false)
    override suspend fun prepare(access: ()->UsbClockQueryStatus?): Boolean {
        val parent=currentCoroutineContext()
        withContext(NonCancellable) {
            try {
                handle=UsbFlacPrefixNative.create(fd,generation,stream,fullTrack)
                if (handle<0) {
                    result=UsbFlacSourceResult(mapOf("sourceIntegrityMask" to 8192L,"createErrno" to -handle), exceptionType="NativeDuplicateFailed")
                    handle=0;return@withContext
                }
                check(handle>0)
                if (stopRequested) requestStop()
                coroutineScope {
                    val worker=async(Dispatchers.IO) { UsbFlacPrefixNative.prime(handle,generation,stream) }
                    try {
                        while(!worker.isCompleted) { if(!parent.isActive || access()!=null) requestStop();delay(5) }
                        result=worker.await()
                    } finally { if(!worker.isCompleted)requestStop();runCatching { worker.await() }.getOrNull()?.let { result=it } }
                }
            } catch(error: Exception) { result=result.copy(exceptionType=error.javaClass.simpleName) }
            catch(error: LinkageError) { result=result.copy(exceptionType=error.javaClass.simpleName) }
        }
        return parent.isActive && access()==null && result.primed
    }
    override suspend fun finish()=withContext(NonCancellable+Dispatchers.IO) {
        if(handle>0) {
            try { result=UsbFlacPrefixNative.finish(handle,generation,stream) }
            catch(error: Exception) { result=result.copy(exceptionType=error.javaClass.simpleName) }
            catch(error: LinkageError) { result=result.copy(exceptionType=error.javaClass.simpleName) }
            finally { handle=0 }
        }
    }
}
