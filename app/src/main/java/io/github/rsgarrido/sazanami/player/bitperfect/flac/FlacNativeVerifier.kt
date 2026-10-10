package io.github.rsgarrido.sazanami.player.bitperfect.flac

import androidx.annotation.Keep
import io.github.rsgarrido.sazanami.BuildConfig

@Keep internal class FlacVerificationCancellation {
    @JvmField @Volatile var cancelled: Boolean = false
}
/** Selected-document verification only; returns evidence, never application PCM buffers. */
@Keep internal object FlacNativeVerifier {
    private val available by lazy {
        runCatching { System.loadLibrary("sazanami_flac_verification"); true }.getOrDefault(false)
    }
    fun verifySelectedDocument(borrowedFd: Int, cancellation: FlacVerificationCancellation): FlacVerificationResult {
        check(BuildConfig.DEBUG)
        if (!available) return FlacVerificationResult(FlacVerificationStatus.NATIVE_UNAVAILABLE)
        return try { FlacNativeEvidence.decode(nativeVerify(borrowedFd, cancellation)) }
        catch (error: Exception) { FlacVerificationResult(FlacVerificationStatus.NATIVE_EVIDENCE_INVALID, exceptionType = error.javaClass.simpleName) }
        catch (error: LinkageError) { FlacVerificationResult(FlacVerificationStatus.NATIVE_UNAVAILABLE, exceptionType = error.javaClass.simpleName) }
    }
    private external fun nativeVerify(borrowedFd: Int, cancellation: FlacVerificationCancellation): FlacNativeSnapshot
}
