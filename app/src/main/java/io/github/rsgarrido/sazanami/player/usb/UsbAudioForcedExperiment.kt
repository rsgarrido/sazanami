package io.github.rsgarrido.sazanami.player.usb

import kotlinx.coroutines.CancellationException

/** Separate from Session 3's permanently non-forced request; no caller-supplied force flag. */
internal class UsbAudioForcedClaimRequest private constructor(val audioControl: UsbAudioClaimInterface) {
    val force: Boolean get() = true
    companion object {
        fun resolve(plan: UsbFsProbePlan, interfaces: List<UsbAudioClaimInterface>): UsbAudioForcedClaimRequest? {
            val target = plan.resolution.target ?: return null
            if (plan.audioControl != target.audioControl) return null
            if (plan.interfaces.singleOrNull { it.number == target.audioControl.number }?.roles !=
                listOf(UsbFsInterfaceRole.AUDIOCONTROL) ||
                interfaces.filter { it.number == target.audioControl.number }.singleOrNull()?.isUac2AudioControl != true) return null
            return UsbAudioControlClaimRequest.resolve(target, interfaces)?.let { UsbAudioForcedClaimRequest(it.audioControl) }
        }
    }
}

internal enum class UsbForcedPhase { QUERYING, FINISHED }
internal enum class UsbRecoveryStatus {
    ENUMERATION_RECOVERED, KERNEL_RECOVERED_FRAMEWORK_PENDING, FRAMEWORK_PRESENT_KERNEL_UNCONFIRMED,
    NOT_RECOVERED, DEVICE_DISCONNECTED, STALE_GENERATION, RELEASE_FAILED, INDETERMINATE
}
internal enum class UsbKernelBindingRecovery { RESTORED, NOT_RESTORED, INDETERMINATE }
internal enum class UsbFrameworkEnumerationRecovery { CANDIDATE_PRESENT, CANDIDATE_MISSING, AMBIGUOUS, INDETERMINATE }
/** Configuration diagnostics never exercise a playback stream; no successful functional state exists. */
internal enum class UsbFunctionalAudioRecovery { NOT_VERIFIED }
internal data class UsbForcedRecoveryObservation(val elapsedMs: Long, val snapshot: UsbFsOwnershipDiagnostics)
internal data class UsbAudioForcedExperimentResult(
    val generation: Long,
    val plan: UsbFsProbePlan,
    val phase: UsbForcedPhase = UsbForcedPhase.FINISHED,
    val claimRequest: UsbAudioForcedClaimRequest? = null,
    val before: UsbFsOwnershipDiagnostics? = null,
    val claimReturned: Boolean? = null,
    val claimException: String? = null,
    val whileOwned: UsbFsOwnershipDiagnostics? = null,
    val clocks: UsbAudioClockDiagnostics? = null,
    val releaseStatus: UsbAudioCleanupStatus = UsbAudioCleanupStatus.NOT_ATTEMPTED,
    val releaseReturned: Boolean? = null,
    val releaseException: String? = null,
    val recoveryObservations: List<UsbForcedRecoveryObservation> = emptyList(),
    val recoveryStatus: UsbRecoveryStatus = UsbRecoveryStatus.INDETERMINATE,
    val recoveryWindowExpired: Boolean = false,
    val afterAndroidAudio: UsbAndroidAudioDiagnostics? = null,
    val closeStatus: UsbAudioCleanupStatus = UsbAudioCleanupStatus.NOT_ATTEMPTED,
    val accessFailure: UsbClockQueryStatus? = null,
    val detail: String? = null
) {
    val after: UsbFsOwnershipDiagnostics? get() = recoveryObservations.lastOrNull()?.snapshot
    val kernelRecovery: UsbKernelBindingRecovery get() = UsbAudioForcedExperiment.kernelRecovery(this)
    val frameworkRecovery: UsbFrameworkEnumerationRecovery get() = UsbAudioForcedExperiment.frameworkRecovery(this)
    val functionalAudioRecovery: UsbFunctionalAudioRecovery get() = UsbFunctionalAudioRecovery.NOT_VERIFIED
    fun invalidate(failure: UsbClockQueryStatus, detail: String) = copy(before = null, whileOwned = null,
        clocks = null, recoveryObservations = emptyList(), afterAndroidAudio = null, accessFailure = failure,
        recoveryStatus = UsbAudioForcedExperiment.recoveryFailure(failure), detail = detail)
}

/** Snapshot is GETDRIVER/GET_SPEED + secondary framework evidence, NEVER a raw claim probe.
 * Runner owns release/close. Implementations expose only the validated AC request and clock reads.
 */
internal interface UsbAudioForcedTransport : UsbClockNativeReadTransport {
    fun interfaces(): List<UsbAudioClaimInterface>
    fun snapshot(): UsbFsOwnershipDiagnostics
    fun frameworkSnapshot(): UsbAndroidAudioDiagnostics
    fun claim(request: UsbAudioForcedClaimRequest): Boolean
    fun release(request: UsbAudioForcedClaimRequest): Boolean
    fun close()
    fun monotonicMs(): Long
    suspend fun pause(milliseconds: Long)
}

internal object UsbAudioForcedExperiment {
    // Known manual test evidence, never a device-detection result or generic driver policy.
    const val MANUAL_HARDWARE_CAVEAT = "On the tested FIIO KA15/Samsung environment, repeated forced AudioControl ownership left subsequent playback routed to the phone speaker despite restored kernel/framework enumeration. Physical disconnect/reconnect restored DAC playback. This is an observed result in that environment, not a universal rule for USB DACs."
    const val MANUAL_DEVICE_STATE_NOTE = "Device-side state appeared to reinitialize during manual testing; the cause was not established."
    const val RECOVERY_WINDOW_MS = 2_000L
    const val RECOVERY_INTERVAL_MS = 250L
    const val MAX_RECOVERY_POLLS = 8
    private val readOrder = listOf(UsbClockQuery.FREQUENCY_RANGE, UsbClockQuery.CURRENT_FREQUENCY, UsbClockQuery.CLOCK_VALIDITY)

    suspend fun run(plan: UsbFsProbePlan, generation: Long, transport: UsbAudioForcedTransport,
        accessFailure: () -> UsbClockQueryStatus? = { null }): UsbAudioForcedExperimentResult {
        var result = UsbAudioForcedExperimentResult(generation, plan)
        var kernelFailure: UsbClockQueryStatus? = null
        fun access(): UsbClockQueryStatus? = accessFailure() ?: kernelFailure
        fun observe(): UsbFsOwnershipDiagnostics {
            val snapshot = transport.snapshot()
            require(snapshot.generation == generation && snapshot.plan === plan && snapshot.claim == null && snapshot.release == null)
            if (snapshot.status == UsbFsProbeStatus.DEVICE_DISCONNECTED) kernelFailure = UsbClockQueryStatus.DEVICE_DISCONNECTED
            return snapshot
        }
        try {
            val request = UsbAudioForcedClaimRequest.resolve(plan, transport.interfaces())
            if (request == null) {
                result = result.copy(detail = "No unique descriptor-resolved Android UAC2 AudioControl target; no force claim issued.")
            } else {
                result = result.copy(claimRequest = request)
                access()?.let { result = result.invalidate(it, "Access check failed before baseline.") }
                if (result.accessFailure == null) {
                    result = result.copy(before = observe())
                    access()?.let { result = result.invalidate(it, "Access changed during baseline; no force claim issued.") }
                    if (result.accessFailure == null && result.before?.status == UsbFsProbeStatus.RECORDED &&
                        result.before?.speed?.result?.succeeded == true) {
                        // No suspension between returned true, ownership recording and finally.
                        try {
                            val claimAccessFailure = access()
                            if (claimAccessFailure == null) result = result.copy(claimReturned = transport.claim(request))
                            else result = result.invalidate(claimAccessFailure, "Access changed before force claim.")
                            if (result.claimReturned == true) {
                                access()?.let { result = result.invalidate(it, "Access changed after force claim.") }
                                if (result.accessFailure == null) result = result.copy(whileOwned = observe())
                                access()?.let { result = result.invalidate(it, "Access changed while owned.") }
                                if (result.accessFailure == null && result.whileOwned?.status == UsbFsProbeStatus.RECORDED) {
                                    val reads = object : UsbClockNativeReadTransport {
                                        override fun lastControlEvidence() = transport.lastControlEvidence()
                                        override fun read(request: UsbClockReadRequest, buffer: ByteArray): Int {
                                            val count = try { transport.read(request, buffer) }
                                            catch (denied: SecurityException) {
                                                kernelFailure = UsbClockQueryStatus.PERMISSION_LOST
                                                throw denied
                                            }
                                            val evidence = transport.lastControlEvidence()
                                            if (count < 0 && evidence?.failureStatus() in setOf(
                                                    UsbClockQueryStatus.DEVICE_DISCONNECTED, UsbClockQueryStatus.PERMISSION_LOST))
                                                kernelFailure = evidence?.failureStatus()
                                            return count
                                        }
                                    }
                                    result = result.copy(clocks = UsbAudioClockControl.queryInOrder(plan.resolution, generation,
                                        reads, readOrder, ::access).copy(claimMode = UsbClockClaimMode.AUDIOCONTROL_FORCED_NATIVE))
                                }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            if (error is SecurityException) kernelFailure = UsbClockQueryStatus.PERMISSION_LOST
                            result = if (result.claimReturned == null) result.copy(claimException = error.javaClass.simpleName)
                            else result.copy(detail = "Owned observation/read stage threw ${error.javaClass.simpleName}; private exception message omitted.")
                        } finally {
                            if (result.claimReturned == true) {
                                // Deliberately ignores cancellation/access guards: release the exact
                                // claimed handle once even on detach, stale generation or read failure.
                                try {
                                    val released = transport.release(request)
                                    result = result.copy(releaseReturned = released, releaseStatus =
                                        if (released) UsbAudioCleanupStatus.SUCCEEDED else UsbAudioCleanupStatus.FAILED)
                                } catch (error: Exception) {
                                    result = result.copy(releaseStatus = UsbAudioCleanupStatus.EXCEPTION,
                                        releaseException = error.javaClass.simpleName)
                                }
                            }
                        }
                        access()?.let { result = result.invalidate(it, "Access changed; release/close facts retained, device values omitted.") }
                        if (result.claimReturned == true && result.accessFailure == null) {
                            result = observeEnumerationRecovery(result, transport, ::access)
                        } else if (result.claimReturned == false) result = result.copy(detail =
                            "Android force claim returned false; no native clock reads or release issued. Cause unavailable.")
                    } else if (result.accessFailure == null) result = result.copy(detail =
                        "Fresh native baseline/speed unavailable; no disruptive claim attempted.")
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            result = result.copy(detail = "Experiment observation failed (${error.javaClass.simpleName}); no capability inferred.")
            access()?.let { result = result.invalidate(it, "Access lost during observation.") }
        } finally {
            try { transport.close(); result = result.copy(closeStatus = UsbAudioCleanupStatus.SUCCEEDED) }
            catch (_: Exception) { result = result.copy(closeStatus = UsbAudioCleanupStatus.EXCEPTION) }
            // Mandatory final secondary snapshot, also when claim returned false or threw.
            // No USB ioctl after close. Cancelled/stale results are still never published.
            if (result.accessFailure == null && (result.claimReturned != null || result.claimException != null)) {
                val audio = try { transport.frameworkSnapshot() }
                catch (_: Exception) { UsbAndroidAudioDiagnostics(detail = "Final framework snapshot unavailable.", querySucceeded = false) }
                result = result.copy(afterAndroidAudio = audio)
            }
        }
        return result.copy(recoveryStatus = classify(result))
    }

    /** Shared Session 5/6 bounded observation. Only enumeration, never routing repair or playback. */
    suspend fun observeEnumerationRecovery(baseline: UsbAudioForcedExperimentResult, transport: UsbAudioForcedTransport,
        accessFailure: () -> UsbClockQueryStatus?): UsbAudioForcedExperimentResult {
        var result = baseline
        val started = transport.monotonicMs()
        fun record() {
            val snapshot = transport.snapshot()
            require(snapshot.generation == baseline.generation && snapshot.plan === baseline.plan && snapshot.claim == null && snapshot.release == null)
            result = if (snapshot.status == UsbFsProbeStatus.DEVICE_DISCONNECTED) result.invalidate(
                UsbClockQueryStatus.DEVICE_DISCONNECTED, "Device disconnected during enumeration recovery.")
            else result.copy(recoveryObservations = result.recoveryObservations +
                UsbForcedRecoveryObservation((transport.monotonicMs() - started).coerceAtLeast(0), snapshot))
            result = result.copy(recoveryStatus = classify(result))
        }
        accessFailure()?.let { return result.invalidate(it, "Access changed before enumeration recovery.") }
        record()
        var polls = 0
        while (result.accessFailure == null && result.recoveryStatus != UsbRecoveryStatus.ENUMERATION_RECOVERED &&
            result.releaseReturned == true && polls < MAX_RECOVERY_POLLS && accessFailure() == null) {
            val remaining = RECOVERY_WINDOW_MS - (transport.monotonicMs() - started)
            if (remaining <= 0) break
            transport.pause(minOf(RECOVERY_INTERVAL_MS, remaining))
            accessFailure()?.let { result = result.invalidate(it, "Access changed during enumeration recovery.") }
            if (result.accessFailure != null || transport.monotonicMs() - started > RECOVERY_WINDOW_MS) break
            record()
            polls++
        }
        accessFailure()?.let { result = result.invalidate(it, "Access changed after enumeration recovery.") }
        return if (result.accessFailure != null) result else result.copy(recoveryStatus = classify(result),
            recoveryWindowExpired = result.releaseReturned == true && result.recoveryStatus != UsbRecoveryStatus.ENUMERATION_RECOVERED &&
                (polls == MAX_RECOVERY_POLLS || transport.monotonicMs() - started >= RECOVERY_WINDOW_MS))
    }

    fun recoveryFailure(failure: UsbClockQueryStatus): UsbRecoveryStatus = when (failure) {
        UsbClockQueryStatus.DEVICE_DISCONNECTED -> UsbRecoveryStatus.DEVICE_DISCONNECTED
        UsbClockQueryStatus.STALE_GENERATION -> UsbRecoveryStatus.STALE_GENERATION
        else -> UsbRecoveryStatus.INDETERMINATE
    }

    /** Requires AC plus every baseline-bound interface (including AS/HID) to return
     * to its observed driver. Framework candidate continuity is secondary, never
     * a USB-device association, usable audio route, DAC playback or clock-rate proof.
     * HID continuity is independent evidence and cannot prove USB audio works.
     */
    fun kernelRestored(result: UsbAudioForcedExperimentResult): Boolean {
        if (result.accessFailure != null) return false
        val before = result.before ?: return false
        val after = result.after ?: return false
        if (before.status != UsbFsProbeStatus.RECORDED || after.status != UsbFsProbeStatus.RECORDED) return false
        val ac = before.driverBindings.singleOrNull { it.iface.number == result.plan.audioControl?.number }
        if (ac == null || !ac.result.succeeded || ac.driver == null || before.driverBindings.size != result.plan.interfaces.size) return false
        return before.driverBindings.all { baseline ->
            if (baseline.result.succeeded && baseline.driver != null) after.driverBindings.singleOrNull {
                it.iface.number == baseline.iface.number
            }?.let { it.result.succeeded && it.driver == baseline.driver } == true
            else baseline.result.errnoName == "ENODATA" && baseline.iface.roles.none {
                it in setOf(UsbFsInterfaceRole.AUDIOCONTROL, UsbFsInterfaceRole.AUDIOSTREAMING, UsbFsInterfaceRole.HID)
            }
        }
    }

    fun kernelRecovery(result: UsbAudioForcedExperimentResult): UsbKernelBindingRecovery = when {
        result.accessFailure != null -> UsbKernelBindingRecovery.INDETERMINATE
        kernelRestored(result) -> UsbKernelBindingRecovery.RESTORED
        knownDriverMissing(result) -> UsbKernelBindingRecovery.NOT_RESTORED
        else -> UsbKernelBindingRecovery.INDETERMINATE
    }

    /** Candidate enumeration only. Same ID/address/capabilities never establish an audio route. */
    fun frameworkRecovery(result: UsbAudioForcedExperimentResult): UsbFrameworkEnumerationRecovery {
        if (result.accessFailure != null) return UsbFrameworkEnumerationRecovery.INDETERMINATE
        val baseline = result.before?.androidAudio ?: return UsbFrameworkEnumerationRecovery.INDETERMINATE
        val observed = result.afterAndroidAudio ?: result.after?.androidAudio
            ?: return UsbFrameworkEnumerationRecovery.INDETERMINATE
        if (!baseline.querySucceeded || !observed.querySucceeded) return UsbFrameworkEnumerationRecovery.INDETERMINATE
        if (observed.outputs.isEmpty()) return UsbFrameworkEnumerationRecovery.CANDIDATE_MISSING
        val before = baseline.outputs.singleOrNull() ?: return UsbFrameworkEnumerationRecovery.AMBIGUOUS
        val after = observed.outputs.singleOrNull() ?: return UsbFrameworkEnumerationRecovery.AMBIGUOUS
        val address = UsbFsEvidence.safeAudioAddress(before.safeAddress) ?: return UsbFrameworkEnumerationRecovery.AMBIGUOUS
        return if (after.type == before.type && UsbFsEvidence.safeAudioAddress(after.safeAddress) == address)
            UsbFrameworkEnumerationRecovery.CANDIDATE_PRESENT else UsbFrameworkEnumerationRecovery.AMBIGUOUS
    }

    fun frameworkCandidateReturned(result: UsbAudioForcedExperimentResult): Boolean =
        frameworkRecovery(result) == UsbFrameworkEnumerationRecovery.CANDIDATE_PRESENT

    fun classify(result: UsbAudioForcedExperimentResult): UsbRecoveryStatus {
        result.accessFailure?.let { return recoveryFailure(it) }
        if (result.claimReturned != true) return UsbRecoveryStatus.INDETERMINATE
        if (result.releaseStatus in setOf(UsbAudioCleanupStatus.FAILED, UsbAudioCleanupStatus.EXCEPTION)) return UsbRecoveryStatus.RELEASE_FAILED
        if (result.closeStatus == UsbAudioCleanupStatus.EXCEPTION) return UsbRecoveryStatus.INDETERMINATE
        if (result.releaseReturned != true || result.after == null) return UsbRecoveryStatus.INDETERMINATE
        if (result.after?.status == UsbFsProbeStatus.DEVICE_DISCONNECTED) return UsbRecoveryStatus.DEVICE_DISCONNECTED
        val kernel = result.kernelRecovery == UsbKernelBindingRecovery.RESTORED
        val framework = result.frameworkRecovery == UsbFrameworkEnumerationRecovery.CANDIDATE_PRESENT
        return when {
            kernel && framework -> UsbRecoveryStatus.ENUMERATION_RECOVERED
            kernel -> UsbRecoveryStatus.KERNEL_RECOVERED_FRAMEWORK_PENDING
            framework -> UsbRecoveryStatus.FRAMEWORK_PRESENT_KERNEL_UNCONFIRMED
            result.kernelRecovery == UsbKernelBindingRecovery.NOT_RESTORED -> UsbRecoveryStatus.NOT_RECOVERED
            else -> UsbRecoveryStatus.INDETERMINATE
        }
    }

    private fun knownDriverMissing(result: UsbAudioForcedExperimentResult): Boolean {
        val before = result.before ?: return false
        val after = result.after ?: return false
        if (before.status != UsbFsProbeStatus.RECORDED || after.status != UsbFsProbeStatus.RECORDED) return false
        return before.driverBindings.any { baseline ->
            baseline.result.succeeded && baseline.driver != null && after.driverBindings.singleOrNull {
                it.iface.number == baseline.iface.number
            }?.let { current -> current.result.errnoName == "ENODATA" ||
                (current.result.succeeded && current.driver != baseline.driver) } == true
        }
    }
}
