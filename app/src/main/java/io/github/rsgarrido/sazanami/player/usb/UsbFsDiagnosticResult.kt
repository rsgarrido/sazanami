package io.github.rsgarrido.sazanami.player.usb

import androidx.annotation.Keep

internal enum class UsbFsInterfaceRole { HID, AUDIOCONTROL, AUDIOSTREAMING, MIDI, OTHER }
internal data class UsbFsInterfaceEvidence(val number: Int, val roles: List<UsbFsInterfaceRole>, val alternates: List<Int>)

/** GETDRIVER covers every Android-exposed interface number once, across its alternates.
 * The optional claim target comes only from the existing topology and AC eligibility checks.
 */
internal class UsbFsProbePlan private constructor(
    val interfaces: List<UsbFsInterfaceEvidence>,
    val resolution: UsbAudioClockResolution,
    val audioControl: UsbInterfaceKey?,
    val warnings: List<String>
) {
    companion object {
        fun create(model: UsbAudioDescriptorModel, androidInterfaces: List<UsbAndroidInterfaceSnapshot>): UsbFsProbePlan {
            val resolution = UsbAudioTopology.resolveClockTarget(model)
            val androidFacts = androidInterfaces.mapIndexed { position, iface ->
                UsbAudioClaimInterface(position, iface.number, iface.alternate, iface.interfaceClass, iface.subclass, iface.protocol)
            }
            val target = resolution.target
            val eligible = target?.let { UsbAudioControlClaimRequest.resolve(it, androidFacts) }
            val warnings = resolution.issues.toMutableList()
            if (target != null && eligible == null) warnings += "Resolved AC has no unique matching Android UAC2 AudioControl alternate zero; raw claim skipped."
            val interfaces = androidInterfaces.map { it.number }.distinct().sorted().map { number ->
                require(number in 0..255)
                val descriptors = model.interfaces.filter { it.key.number == number }
                if (descriptors.isEmpty()) warnings += "Interface $number has no parsed descriptor role; reported as OTHER."
                UsbFsInterfaceEvidence(number, descriptors.map { iface -> when {
                    iface.interfaceClass == 3 -> UsbFsInterfaceRole.HID
                    iface.isAudioControl -> UsbFsInterfaceRole.AUDIOCONTROL
                    iface.isAudioStreaming -> UsbFsInterfaceRole.AUDIOSTREAMING
                    iface.interfaceClass == 1 && iface.subclass == 3 -> UsbFsInterfaceRole.MIDI
                    else -> UsbFsInterfaceRole.OTHER
                } }.distinct().ifEmpty { listOf(UsbFsInterfaceRole.OTHER) }, descriptors.map { it.key.alternate }.distinct().sorted())
            }
            return UsbFsProbePlan(interfaces, resolution, target?.audioControl?.takeIf { eligible != null }, warnings.distinct())
        }
    }
}

internal enum class UsbFsProbeStatus {
    QUERYING, RECORDED, NATIVE_UNAVAILABLE, INVALID_FD, MALFORMED_NATIVE_RESULT,
    DEVICE_DISCONNECTED, STALE_GENERATION, PERMISSION_LOST, TRANSPORT_ERROR, EXCEPTION
}
internal enum class UsbFsIoctlOperation { GETDRIVER, GET_SPEED, CLAIMINTERFACE, RELEASEINTERFACE, SETINTERFACE }
internal enum class UsbFsSpeed { UNKNOWN, LOW, FULL, HIGH, WIRELESS, SUPER, SUPER_PLUS }
internal data class UsbFsIoctlResult(val operation: UsbFsIoctlOperation, val returnValue: Int,
    val errno: Int, val errnoName: String?) {
    val succeeded: Boolean get() = returnValue >= 0
}
internal data class UsbFsDriverBinding(val iface: UsbFsInterfaceEvidence, val result: UsbFsIoctlResult, val driver: String?)
internal data class UsbFsSpeedEvidence(val result: UsbFsIoctlResult, val mapped: UsbFsSpeed) {
    val rawSpeed: Int? get() = result.returnValue.takeIf { result.succeeded }
}
internal data class UsbFsOwnershipDiagnostics(
    val generation: Long,
    val plan: UsbFsProbePlan,
    val status: UsbFsProbeStatus,
    val driverBindings: List<UsbFsDriverBinding> = emptyList(),
    val speed: UsbFsSpeedEvidence? = null,
    val claim: UsbFsIoctlResult? = null,
    val release: UsbFsIoctlResult? = null,
    val androidAudio: UsbAndroidAudioDiagnostics? = null,
    val detail: String? = null
) {
    val forcedDetachAttempted: Boolean get() = false
    fun invalidate(failure: UsbClockQueryStatus, detail: String): UsbFsOwnershipDiagnostics = copy(
        status = UsbFsEvidence.accessStatus(failure), driverBindings = emptyList(), speed = null,
        claim = null, release = null, androidAudio = null, detail = detail)
}

internal data class UsbAndroidAudioOutput(
    val product: String?, val type: Int, val id: Int, val safeAddress: String?,
    val sampleRates: List<Int>, val encodings: List<Int>, val channelCounts: List<Int>,
    val channelMasks: List<Int>, val channelIndexMasks: List<Int>
)
internal data class UsbAndroidAudioDiagnostics(val outputs: List<UsbAndroidAudioOutput> = emptyList(),
    val detail: String? = null, val querySucceeded: Boolean = true)

/** Stable, bounded JNI packet, API version 5. Numbers: version, interface count, then
 * [interface, return, errno] per driver; speed [return, errno]; claim and release
 * [attempted 0/1, return, errno]. Names have count drivers / count+3 errno labels.
 * Names are supplementary; numeric ioctl results and errno remain separate evidence.
 */
@Keep
internal data class UsbFsNativeResult(val numbers: IntArray, val driverNames: Array<String>,
    val errnoNames: Array<String>, val speedName: String)

/** Pure interpretation and packet validation. Never infers rates, speed, or kernel ownership from descriptors. */
internal object UsbFsEvidence {
    const val NATIVE_API_VERSION = 8
    const val MAX_INTERFACES = 256

    // Values from linux/usb/ch9.h enum usb_device_speed. Native code also uses the actual NDK enum.
    fun speed(raw: Int): UsbFsSpeed = when (raw) {
        1 -> UsbFsSpeed.LOW; 2 -> UsbFsSpeed.FULL; 3 -> UsbFsSpeed.HIGH
        4 -> UsbFsSpeed.WIRELESS; 5 -> UsbFsSpeed.SUPER; 6 -> UsbFsSpeed.SUPER_PLUS
        else -> UsbFsSpeed.UNKNOWN
    }

    private val errnoLabels = setOf("EPERM", "ENOENT", "EINTR", "EIO", "EBADF", "EAGAIN", "ENOMEM", "EACCES",
        "EBUSY", "ENODEV", "EINVAL", "ENOTTY", "ENOSYS", "ENODATA", "EOPNOTSUPP", "ETIMEDOUT", "EPIPE", "EPROTO")
    fun errnoName(number: Int, nativeLabel: String): String? =
        if (number == 0) null else nativeLabel.takeIf { it in errnoLabels } ?: "UNKNOWN"

    fun decode(packet: UsbFsNativeResult, plan: UsbFsProbePlan, generation: Long,
        claimProbeExpected: Boolean = plan.audioControl != null): UsbFsOwnershipDiagnostics {
        fun malformed(detail: String) = UsbFsOwnershipDiagnostics(generation, plan, UsbFsProbeStatus.MALFORMED_NATIVE_RESULT, detail = detail)
        val data = packet.numbers
        val count = plan.interfaces.size
        if (count !in 0..MAX_INTERFACES || data.size != 10 + count * 3 || data[0] != NATIVE_API_VERSION || data[1] != count ||
            packet.driverNames.size != count || packet.errnoNames.size != count + 3)
            return malformed("Native packet version/length/count mismatch; no values inferred.")
        fun ioctlResult(operation: UsbFsIoctlOperation, offset: Int, labelIndex: Int) =
            UsbFsIoctlResult(operation, data[offset], data[offset + 1], errnoName(data[offset + 1], packet.errnoNames[labelIndex]))
        val bindings = plan.interfaces.mapIndexed { index, iface ->
            val offset = 2 + index * 3
            if (data[offset] != iface.number) return malformed("Native interface order differs from the descriptor-derived plan.")
            val result = ioctlResult(UsbFsIoctlOperation.GETDRIVER, offset + 1, index)
            UsbFsDriverBinding(iface, result, packet.driverNames[index].takeIf { result.succeeded && it.isNotEmpty() })
        }
        val speedOffset = 2 + count * 3
        val speedResult = ioctlResult(UsbFsIoctlOperation.GET_SPEED, speedOffset, count)
        val mapped = if (speedResult.succeeded) speed(speedResult.returnValue) else UsbFsSpeed.UNKNOWN
        if (packet.speedName != mapped.name) return malformed("Native speed label disagrees with its raw UAPI value.")
        val claimOffset = speedOffset + 2
        val releaseOffset = claimOffset + 3
        if (data[claimOffset] !in 0..1 || data[releaseOffset] !in 0..1 ||
            (data[claimOffset] == 1) != claimProbeExpected) return malformed("Invalid native claim/release attempt metadata.")
        val claim = if (data[claimOffset] == 1) ioctlResult(UsbFsIoctlOperation.CLAIMINTERFACE, claimOffset + 1, count + 1) else null
        val release = if (data[releaseOffset] == 1) ioctlResult(UsbFsIoctlOperation.RELEASEINTERFACE, releaseOffset + 1, count + 2) else null
        val ioctls = bindings.map { it.result } + speedResult + listOfNotNull(claim, release)
        val invalidErrno = ioctls.any { (it.succeeded && it.errno != 0) || (!it.succeeded && it.errno <= 0) }
        val invalidRelease = (release != null) != (claim?.succeeded == true)
        val disconnected = ioctls.any { !it.succeeded && it.errnoName == "ENODEV" }
        return UsbFsOwnershipDiagnostics(generation, plan,
            when {
                disconnected -> UsbFsProbeStatus.DEVICE_DISCONNECTED
                invalidErrno || invalidRelease -> UsbFsProbeStatus.MALFORMED_NATIVE_RESULT
                else -> UsbFsProbeStatus.RECORDED
            },
            bindings, UsbFsSpeedEvidence(speedResult, mapped), claim, release,
            detail = when {
                disconnected -> "usbfs returned ENODEV; device evidence is non-authoritative even if Android enumeration has not caught up."
                invalidRelease -> "Claim/release metadata is inconsistent; successful raw claims require immediate release evidence, and failed/unissued claims must not release."
                invalidErrno -> "Native errno metadata is inconsistent; numeric evidence retained."
                else -> "Probe finished. RECORDED describes collected evidence, not a successful interface claim."
            })
    }

    fun driverExplanation(binding: UsbFsDriverBinding): String = when {
        binding.result.succeeded && binding.driver != null -> "Kernel returned this bound driver name at query time."
        binding.result.succeeded -> "GETDRIVER succeeded but returned no driver name."
        binding.result.errnoName == "ENODATA" -> "Kernel returned no driver data; the interface may be unbound or unavailable."
        else -> "GETDRIVER failed; no bound-driver conclusion is inferred."
    }

    fun claimExplanation(diagnostics: UsbFsOwnershipDiagnostics): String {
        val claim = diagnostics.claim ?: return "Raw claim was not attempted; inspect target-resolution warnings."
        if (claim.succeeded) return when {
            diagnostics.release == null -> "Raw claim reported success but immediate release evidence is missing; the result is incomplete."
            !diagnostics.release.succeeded -> "Raw non-forced AC claim succeeded and immediate release failed; its return/errno are retained. Kotlin still attempts connection close."
            else -> "Raw non-forced AC claim succeeded and immediate release succeeded. No clock or endpoint I/O was performed while claimed."
        }
        if (claim.errnoName != "EBUSY") return "Raw claim failed with ${claim.errnoName ?: "unknown errno"}; preserve this error without assuming a kernel-driver conflict."
        val driver = diagnostics.driverBindings.singleOrNull { it.iface.number == diagnostics.plan.audioControl?.number }
            ?.takeIf { it.result.succeeded }?.driver
        return "Interface claim failed with EBUSY; the interface is currently busy/owned." +
            if (driver != null) " GETDRIVER separately reported a bound driver on the same AC interface; the driver name is listed above. These observations do not establish exclusive causation." else
                " No same-interface bound driver was established; the owner is not inferred."
    }

    fun accessStatus(failure: UsbClockQueryStatus): UsbFsProbeStatus = when (failure) {
        UsbClockQueryStatus.DEVICE_DISCONNECTED -> UsbFsProbeStatus.DEVICE_DISCONNECTED
        UsbClockQueryStatus.STALE_GENERATION -> UsbFsProbeStatus.STALE_GENERATION
        UsbClockQueryStatus.PERMISSION_LOST -> UsbFsProbeStatus.PERMISSION_LOST
        else -> UsbFsProbeStatus.TRANSPORT_ERROR
    }

    /** USB ALSA card/device addresses only; never retains arbitrary paths, serials or identifiers. */
    fun safeAudioAddress(address: String?): String? = address?.takeIf { Regex("card=[0-9]{1,3};device=[0-9]{1,3};?").matches(it) }
}
