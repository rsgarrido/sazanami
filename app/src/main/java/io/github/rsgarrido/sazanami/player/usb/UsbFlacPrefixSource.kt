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
    val reasons get() = UsbFlacSourceEvidence.reasons.filterIndexed { i,_ -> count("sourceIntegrityMask") and (1L shl i) != 0L }
    val primed get() = exceptionType == null && count("sourceIntegrityMask")==0L && count("metadataAccepted")==1L &&
        count("initialPrimedFrames") in 8192L..16384L && count("sampleRate")==44100L && count("channels")==2L && count("bitsPerSample")==16L
    val clean get() = counters.keys.containsAll(UsbFlacSourceEvidence.fields) && primed && count("producerStopped")==1L && count("producerJoined")==1L &&
        count("decoderReleased")==1L && count("nativeFlacFdClosed")==1L && count("queueReleased")==1L &&
        count("queueUnderruns")==0L && count("sourceFrameGapCount")==0L && count("sourceFrameDuplicateCount")==0L &&
        count("sourceFrameOrderErrors")==0L && count("preparedButUnsubmittedFrames")==0L &&
        count("framesConsumedFromQueue")>0L && count("framesDecoded")<=Long.MAX_VALUE/4L && count("framesDecoded")>=count("framesEnqueued") &&
        count("framesEnqueued")>=count("framesConsumedFromQueue") &&
        count("queueFramesAtStop")==count("framesEnqueued")-count("framesConsumedFromQueue") &&
        count("prefetchedFramesDiscardedAtStop")==count("queueFramesAtStop") &&
        count("firstSourceFrameEnqueued")==0L && count("lastSourceFrameEnqueued")==count("framesEnqueued")-1L &&
        count("expectedNextEnqueueFrame")==count("framesEnqueued") && count("expectedNextConsumeFrame")==count("framesConsumedFromQueue") &&
        count("initialPrimedFrames")<=16384L && count("queueFramesAtStop") in 0L..16384L &&
        count("minimumQueueDepthDuringStreaming") in 0L..count("maximumQueueDepthDuringStreaming") &&
        count("maximumQueueDepthDuringStreaming")<=16384L && count("firstSourceFrameConsumed")==0L && count("lastSourceFrameConsumed")==count("framesConsumedFromQueue")-1L &&
        (decoder?.let { it.nativeStatus==FlacVerificationStatus.CANCELLED && it.decoderVersion=="1.5.0" && it.errors.isEmpty() && it.exceptionType==null &&
            it.count("canonicalPcmBytesHashed")==count("framesDecoded")*4L &&
            it.count("nativeDuplicateClosed")==1L && it.count("decoderDeleted")==1L && it.count("resourceErrors")==0L &&
            it.count("finishCalled")==1L && it.count("nativeFlacRecognized")==1L && it.count("initStatus")==0L &&
            it.count("metadataProcessed")==1L && it.count("streamInfoReceived")==1L && it.count("md5Verification")==0L &&
            it.count("frameOrderErrors")==0L && it.count("md5CheckingEnabled")==0L && it.count("decoderErrors")==0L &&
            it.count("outOfRangeSamples")==0L && it.count("formatContradictions")==0L } == true)
}
internal object UsbFlacSourceEvidence {
    val fields=listOf("version","generation","streamGeneration","sourceIntegrityMask","initialPrimedFrames","metadataAccepted",
        "sampleRate","channels","bitsPerSample","totalSamples","framesDecoded","framesEnqueued","framesConsumedFromQueue",
        "preparedButUnsubmittedFrames","queueFramesAtStop","prefetchedFramesDiscardedAtStop","minimumQueueDepthDuringStreaming",
        "maximumQueueDepthDuringStreaming","producerFullWaitCount","consumerEmptyEvents","queueUnderruns",
        "firstSourceFrameEnqueued","lastSourceFrameEnqueued","firstSourceFrameConsumed","lastSourceFrameConsumed",
        "expectedNextEnqueueFrame","expectedNextConsumeFrame","sourceFrameGapCount","sourceFrameDuplicateCount","sourceFrameOrderErrors",
        "consumedSourcePcmHash","allEnqueuedPrefetchPcmHash","producerStopped","producerJoined","decoderReleased","nativeFlacFdClosed","queueReleased")
    val reasons=listOf("FLAC_DECODER_ERROR","FLAC_DECODER_INIT_FAILED","UNSUPPORTED_FLAC_FORMAT","PCM_SAMPLE_OUT_OF_RANGE",
        "SOURCE_EOF_BEFORE_DURATION","QUEUE_PRIME_FAILED","QUEUE_UNDERRUN","SOURCE_FRAME_GAP","SOURCE_FRAME_DUPLICATE",
        "SOURCE_FRAME_ORDER_ERROR","SOURCE_SUBMITTED_HASH_MISMATCH","CANCELLED","RESOURCE_CLEANUP_FAILED","FLAC_INPUT_OPEN_FAILED","NOT_NATIVE_FLAC","FLAC_METADATA_INVALID","SOURCE_FORMAT_CONTRADICTION")
    fun decode(value: UsbFlacSourceSnapshot,generation: Long,stream: Long): UsbFlacSourceResult {
        require(value.numbers.size==fields.size && value.numbers[0]==1L && value.numbers[1]==generation && value.numbers[2]==stream &&
            value.examples.size%7==0 && value.examples.size<=5*7)
        val counters=fields.zip(value.numbers.toList()).toMap()
        val signed=setOf("consumedSourcePcmHash","allEnqueuedPrefetchPcmHash")
        val indices=setOf("firstSourceFrameEnqueued","lastSourceFrameEnqueued","firstSourceFrameConsumed","lastSourceFrameConsumed")
        require(counters.all { (name,number) -> name in signed || if (name in indices) number>=-1L else number>=0L })
        require(countBooleanFields.all { counters.getValue(it) in 0L..1L } &&
            counters.getValue("sourceIntegrityMask") in 0L..131071L && counters.getValue("initialPrimedFrames")<=16384L)
        val examples=value.examples.toList().chunked(7).map {
            require(it[0]>=0 && it[0]<counters.getValue("framesConsumedFromQueue") &&
                it[1] in -32768L..32767L && it[2] in -32768L..32767L && it.drop(3).all { b->b in 0L..255L })
            require(it[3]==(it[1] and 255L) && it[4]==((it[1] ushr 8) and 255L) &&
                it[5]==(it[2] and 255L) && it[6]==((it[2] ushr 8) and 255L))
            UsbPcmFrameEvidence(it[0],it[1].toInt(),it[2].toInt(),it.drop(3).map { b->b.toInt() })
        }
        require(value.decoder == null || value.decoder is FlacNativeSnapshot)
        val decoder=(value.decoder as? FlacNativeSnapshot)?.let(FlacNativeEvidence::decode)
        return UsbFlacSourceResult(counters,decoder,examples)
    }
    private val countBooleanFields=listOf("metadataAccepted","producerStopped","producerJoined","decoderReleased","nativeFlacFdClosed","queueReleased")
}
@Keep internal object UsbFlacPrefixNative {
    private val available by lazy { runCatching { System.loadLibrary("sazanami_usb_diagnostics"); true }.getOrDefault(false) }
    fun create(fd: Int,generation: Long,stream: Long): Long { check(BuildConfig.DEBUG && available);return nativeCreate(fd,generation,stream) }
    fun prime(handle: Long,generation: Long,stream: Long)=UsbFlacSourceEvidence.decode(nativePrime(handle,generation,stream),generation,stream)
    fun healthy(handle: Long,generation: Long,stream: Long)=nativeHealthy(handle,generation,stream)
    fun stop(handle: Long,generation: Long,stream: Long)=nativeStop(handle,generation,stream)
    fun finish(handle: Long,generation: Long,stream: Long)=UsbFlacSourceEvidence.decode(nativeFinish(handle,generation,stream),generation,stream)
    private external fun nativeCreate(fd: Int,generation: Long,stream: Long): Long
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
internal class UsbFlacPrefixSource(private val fd: Int,private val generation: Long,private val stream: Long) : UsbFlacSourceLifecycle {
    @Volatile override var handle=0L; private set
    override var result=UsbFlacSourceResult(); private set
    override fun requestStop() { val h=handle;if(h>0) runCatching { UsbFlacPrefixNative.stop(h,generation,stream) } }
    override fun healthy() = handle>0 && result.primed && runCatching { UsbFlacPrefixNative.healthy(handle,generation,stream) }.getOrDefault(false)
    override suspend fun prepare(access: ()->UsbClockQueryStatus?): Boolean {
        val parent=currentCoroutineContext()
        withContext(NonCancellable) {
            try {
                handle=UsbFlacPrefixNative.create(fd,generation,stream)
                if (handle<0) {
                    result=UsbFlacSourceResult(mapOf("sourceIntegrityMask" to 8192L,"createErrno" to -handle), exceptionType="NativeDuplicateFailed")
                    handle=0;return@withContext
                }
                check(handle>0)
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
