package io.github.rsgarrido.sazanami.player.usb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal enum class UsbConfigurationPhase { QUERYING, FINISHED }
internal enum class UsbTargetConfigurationStatus { NOT_ATTEMPTED, BLOCKED, FAILED, EXCEPTION, TARGET_CONFIGURATION_VERIFIED, NON_AUTHORITATIVE }
internal enum class UsbConfigurationRestorationStatus { NOT_NEEDED, RESTORED, PARTIAL, FAILED, INDETERMINATE }
internal enum class UsbConfigurationStep { TARGET, BASELINE, AC_CLAIM, PREFLIGHT, AS_CLAIM, IDLE,
    CLOCK_SET, CLOCK_READBACK, CLOCK_VALIDITY, ALTERNATE_SET, ALTERNATE_READBACK, CONFIGURED_CLOCK, CONFIGURED_VALIDITY }
internal data class UsbConfigurationValidityObservation(val elapsedMs: Long, val result: UsbClockQueryResult)

internal data class UsbAudioConfigurationResult(
    val generation: Long,
    val resolution: UsbAudioConfigurationResolution,
    val phase: UsbConfigurationPhase = UsbConfigurationPhase.FINISHED,
    val targetStatus: UsbTargetConfigurationStatus = UsbTargetConfigurationStatus.NOT_ATTEMPTED,
    val failedStep: UsbConfigurationStep? = null,
    val detail: String? = null,
    val before: UsbFsOwnershipDiagnostics? = null,
    val acClaim: UsbConfigurationAndroidOwnership = UsbConfigurationAndroidOwnership(),
    val afterAcClaim: UsbFsOwnershipDiagnostics? = null,
    val preflight: UsbAudioClockDiagnostics? = null,
    val originalClockRateHz: Long? = null,
    val asClaim: UsbConfigurationAndroidOwnership = UsbConfigurationAndroidOwnership(),
    val whileOwned: UsbFsOwnershipDiagnostics? = null,
    val originalAlternate: UsbConfigurationControlEvidence? = null,
    val idleSelectionAttempted: Boolean = false,
    val idleAlternateSet: UsbStreamingAlternateEvidence? = null,
    val idleAlternateReadback: UsbConfigurationControlEvidence? = null,
    val clockWriteAttempted: Boolean = false,
    val targetClockSet: UsbConfigurationControlEvidence? = null,
    val targetClockReadback: UsbClockQueryResult? = null,
    val targetValidity: List<UsbConfigurationValidityObservation> = emptyList(),
    val alternateSelectionAttempted: Boolean = false,
    val targetAlternateSet: UsbStreamingAlternateEvidence? = null,
    val targetAlternateReadback: UsbConfigurationControlEvidence? = null,
    val configuredClockReadback: UsbClockQueryResult? = null,
    val configuredClockValidity: UsbClockQueryResult? = null,
    val idleAlternateRestore: UsbStreamingAlternateEvidence? = null,
    val idleRestorationReadback: UsbConfigurationControlEvidence? = null,
    val originalClockRestore: UsbConfigurationControlEvidence? = null,
    val originalClockReadback: UsbClockQueryResult? = null,
    val originalClockValidity: List<UsbConfigurationValidityObservation> = emptyList(),
    val restorationStatus: UsbConfigurationRestorationStatus = UsbConfigurationRestorationStatus.NOT_NEEDED,
    val restorationNotes: List<String> = emptyList(),
    val asRelease: UsbConfigurationAndroidOwnership = UsbConfigurationAndroidOwnership(),
    val acRelease: UsbConfigurationAndroidOwnership = UsbConfigurationAndroidOwnership(),
    val closeStatus: UsbAudioCleanupStatus = UsbAudioCleanupStatus.NOT_ATTEMPTED,
    val enumerationRecovery: UsbAudioForcedExperimentResult? = null,
    val afterAndroidAudio: UsbAndroidAudioDiagnostics? = null,
    val accessFailure: UsbClockQueryStatus? = null
) {
    val idleMutation get() = UsbAlternateMutationResult(generation, idleAlternateSet, idleAlternateReadback)
    val targetMutation get() = UsbAlternateMutationResult(generation, targetAlternateSet, targetAlternateReadback)
    val restorationMutation get() = UsbAlternateMutationResult(generation, idleAlternateRestore, idleRestorationReadback)
    val kernelRecovery: UsbKernelBindingRecovery get() = if (accessFailure != null) UsbKernelBindingRecovery.INDETERMINATE
        else enumerationRecovery?.kernelRecovery ?: UsbKernelBindingRecovery.INDETERMINATE
    val frameworkRecovery: UsbFrameworkEnumerationRecovery get() = if (accessFailure != null) UsbFrameworkEnumerationRecovery.INDETERMINATE
        else enumerationRecovery?.frameworkRecovery ?: UsbFrameworkEnumerationRecovery.INDETERMINATE
    val functionalAudioRecovery: UsbFunctionalAudioRecovery get() = UsbFunctionalAudioRecovery.NOT_VERIFIED
    fun invalidate(failure: UsbClockQueryStatus, reason: String) = copy(accessFailure = failure,
        targetStatus = UsbTargetConfigurationStatus.NON_AUTHORITATIVE, detail = reason,
        enumerationRecovery = enumerationRecovery?.invalidate(failure, reason))
}

/** Uses the existing AC adapter/read transport and enumeration observation boundary. */
internal interface UsbAudioConfigurationTransport : UsbAudioForcedTransport {
    fun connectionValid(): Boolean
    fun claimStreaming(request: UsbAudioConfigurationPlan.UsbAudioStreamingClaimRequest): Boolean
    fun releaseStreaming(request: UsbAudioConfigurationPlan.UsbAudioStreamingClaimRequest): Boolean
    fun control(request: UsbConfigurationControlRequest): UsbConfigurationControlEvidence
    fun setAlternate(request: UsbStreamingAlternateRequest): UsbStreamingAlternateEvidence
}

internal object UsbAudioConfigurationExperiment {
    const val VALIDITY_WINDOW_MS = 1_000L
    const val VALIDITY_INTERVAL_MS = 50L
    const val MAX_VALIDITY_RETRIES = 20
    private val preflightOrder = listOf(UsbClockQuery.FREQUENCY_RANGE, UsbClockQuery.CURRENT_FREQUENCY, UsbClockQuery.CLOCK_VALIDITY)
    private class Stop(val step: UsbConfigurationStep, message: String) : Exception(message)

    suspend fun run(resolution: UsbAudioConfigurationResolution, generation: Long, transport: UsbAudioConfigurationTransport,
        accessFailure: () -> UsbClockQueryStatus? = { null }): UsbAudioConfigurationResult =
        runTransaction(resolution, generation, transport, accessFailure, null)

    suspend fun runWithEndpointWork(resolution: UsbAudioConfigurationResolution, generation: Long,
        transport: UsbAudioConfigurationTransport, endpointWork: UsbConfiguredEndpointWork,
        accessFailure: () -> UsbClockQueryStatus? = { null }): UsbAudioConfigurationResult =
        runTransaction(resolution, generation, transport, accessFailure, endpointWork)

    private suspend fun runTransaction(resolution: UsbAudioConfigurationResolution, generation: Long,
        transport: UsbAudioConfigurationTransport, accessFailure: () -> UsbClockQueryStatus?,
        endpointWork: UsbConfiguredEndpointWork?): UsbAudioConfigurationResult {
        val plan = resolution.plan
        var result = UsbAudioConfigurationResult(generation, resolution)
        var permit: UsbConfigurationMutationPermit? = null
        var terminalFailure: UsbClockQueryStatus? = null
        var step = UsbConfigurationStep.TARGET
        val coroutine = currentCoroutineContext()
        var restoring = false
        val started = transport.monotonicMs()
        var evidenceOrder = 0

        fun connectionValid(): Boolean = try { transport.connectionValid() }
        catch (error: Exception) {
            terminalFailure = if (error is SecurityException) UsbClockQueryStatus.PERMISSION_LOST else UsbClockQueryStatus.TRANSPORT_ERROR
            false
        }

        fun terminal(status: UsbClockQueryStatus?, errno: String? = null) {
            if (status in setOf(UsbClockQueryStatus.DEVICE_DISCONNECTED, UsbClockQueryStatus.STALE_GENERATION,
                    UsbClockQueryStatus.PERMISSION_LOST) || errno == "EBADF")
                terminalFailure = status ?: UsbClockQueryStatus.TRANSPORT_ERROR
        }
        fun live(): UsbClockQueryStatus? {
            val failure = terminalFailure ?: accessFailure() ?: if (!connectionValid()) terminalFailure ?: UsbClockQueryStatus.TRANSPORT_ERROR else null
            if (failure != null) {
                terminalFailure = failure
                result = result.invalidate(failure, "USB attachment/permission/fd authority changed; no further forward configuration.")
            }
            return failure
        }
        suspend fun guard() {
            currentCoroutineContext().ensureActive()
            if (live() != null) throw Stop(step, "USB authority is no longer current.")
            endpointWork?.forwardIssue()?.let { throw Stop(step, it) }
        }
        fun requireStage(condition: Boolean, message: String) { if (!condition) throw Stop(step, message) }
        fun readAccess(): UsbClockQueryStatus? { if (!restoring) coroutine.ensureActive(); return live() }
        val reads = object : UsbClockNativeReadTransport {
            override fun lastControlEvidence() = transport.lastControlEvidence()
            override fun read(request: UsbClockReadRequest, buffer: ByteArray): Int {
                val count = try { transport.read(request, buffer) }
                catch (denied: SecurityException) { terminalFailure = UsbClockQueryStatus.PERMISSION_LOST; throw denied }
                val evidence = transport.lastControlEvidence()
                terminal(if (count < 0) evidence?.failureStatus() else null, evidence?.errnoName)
                return count
            }
        }
        fun clock(kind: UsbClockQuery) = UsbAudioClockControl.queryOne(requireNotNull(plan).clock, kind, reads, ::readAccess)
        fun snapshot(): UsbFsOwnershipDiagnostics {
            val observation = transport.snapshot()
            require(observation.generation == generation && observation.plan === plan?.probe && observation.claim == null && observation.release == null)
            if (observation.status == UsbFsProbeStatus.DEVICE_DISCONNECTED) terminalFailure = UsbClockQueryStatus.DEVICE_DISCONNECTED
            return observation
        }
        fun control(request: UsbConfigurationControlRequest): UsbConfigurationControlEvidence {
            val order = ++evidenceOrder
            val observation = try { transport.control(request) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { UsbConfigurationControlEvidence(request, status =
                when (error) {
                    is SecurityException -> UsbClockQueryStatus.PERMISSION_LOST
                    is IllegalArgumentException -> UsbClockQueryStatus.MALFORMED_RESPONSE
                    else -> UsbClockQueryStatus.TRANSPORT_ERROR
                },
                exceptionType = error.javaClass.simpleName) }
            terminal(observation.status, observation.native?.errnoName)
            return observation.copy(generation = generation, order = order, elapsedMs = (transport.monotonicMs() - started).coerceAtLeast(0))
        }
        fun alternate(request: UsbStreamingAlternateRequest): UsbStreamingAlternateEvidence {
            val order = ++evidenceOrder
            val observation = try { transport.setAlternate(request) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { UsbStreamingAlternateEvidence(request, status =
                if (error is SecurityException) UsbClockQueryStatus.PERMISSION_LOST else UsbClockQueryStatus.TRANSPORT_ERROR,
                exceptionType = error.javaClass.simpleName) }
            terminal(observation.status, observation.result?.errnoName)
            return observation.copy(generation = generation, order = order, elapsedMs = (transport.monotonicMs() - started).coerceAtLeast(0))
        }
        suspend fun validity(): List<UsbConfigurationValidityObservation> {
            val started = transport.monotonicMs()
            val observations = mutableListOf<UsbConfigurationValidityObservation>()
            var retries = 0
            while (live() == null) {
                val response = clock(UsbClockQuery.CLOCK_VALIDITY)
                observations += UsbConfigurationValidityObservation((transport.monotonicMs() - started).coerceAtLeast(0), response)
                if (response.status != UsbClockQueryStatus.SUCCESS || response.clockValid == true || retries >= MAX_VALIDITY_RETRIES) break
                val remaining = VALIDITY_WINDOW_MS - (transport.monotonicMs() - started)
                if (remaining <= 0) break
                transport.pause(minOf(VALIDITY_INTERVAL_MS, remaining))
                if (transport.monotonicMs() - started > VALIDITY_WINDOW_MS) break
                retries++
            }
            return observations.toList()
        }
        fun ownership(action: () -> Boolean): UsbConfigurationAndroidOwnership = try {
            UsbConfigurationAndroidOwnership(attempted = true, returned = action())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (error is SecurityException) terminalFailure = UsbClockQueryStatus.PERMISSION_LOST
            UsbConfigurationAndroidOwnership(attempted = true, exceptionType = error.javaClass.simpleName)
        }
        fun release(action: () -> Boolean): UsbConfigurationAndroidOwnership = try {
            UsbConfigurationAndroidOwnership(attempted = true, returned = action())
        } catch (error: Exception) { UsbConfigurationAndroidOwnership(attempted = true, exceptionType = error.javaClass.simpleName) }

        try {
            requireStage(plan != null, "No unambiguous PCM16 configuration target; no USB action issued.")
            val target = requireNotNull(plan)
            guard()
            requireStage(target.acClaim.audioControl in transport.interfaces() && target.asClaim.audioStreaming in transport.interfaces(),
                "Live Android audio handles do not match the validated descriptor plan.")
            step = UsbConfigurationStep.BASELINE
            result = result.copy(before = snapshot())
            guard()
            requireStage(result.before?.status == UsbFsProbeStatus.RECORDED && result.before?.speed?.result?.succeeded == true,
                "Native baseline/API unavailable; no forced ownership attempted.")
            endpointWork?.baselineIssue(result)?.let { throw Stop(step, it) }
            step = UsbConfigurationStep.AC_CLAIM
            guard()
            result = result.copy(acClaim = ownership { transport.claim(target.acClaim) })
            requireStage(result.acClaim.returned == true, "AudioControl force claim failed; AS untouched.")
            guard()
            result = result.copy(afterAcClaim = snapshot())
            guard()
            step = UsbConfigurationStep.PREFLIGHT
            val preflight = UsbAudioClockControl.queryInOrder(target.probe.resolution, generation, reads, preflightOrder, ::readAccess)
                .copy(claimMode = UsbClockClaimMode.USB_CONFIGURATION_NATIVE)
            result = result.copy(preflight = preflight)
            guard()
            permit = UsbConfigurationMutationPermit.fromPreflight(target, generation, preflight)
            requireStage(permit != null, "Fresh RANGE/CUR/validity must be well formed, 44100 represented, and original rate safely restorable; no AS claim/write.")
            result = result.copy(originalClockRateHz = requireNotNull(permit).originalRateHz)
            step = UsbConfigurationStep.AS_CLAIM
            guard()
            result = result.copy(asClaim = ownership { transport.claimStreaming(target.asClaim) })
            requireStage(result.asClaim.returned == true, "AudioStreaming force claim failed; no clock/alternate mutation.")
            guard()
            result = result.copy(whileOwned = snapshot())
            guard()
            step = UsbConfigurationStep.IDLE
            result = result.copy(originalAlternate = control(UsbConfigurationControlRequest.getInterface(target)))
            guard()
            // Initial query is advisory: the explicit host selection below establishes idle.
            result = result.copy(idleSelectionAttempted = true) // Even a failure may have mutated AS.
            result = result.copy(idleAlternateSet = alternate(UsbStreamingAlternateRequest.idle(target)))
            guard()
            requireStage(result.idleAlternateSet?.status == UsbClockQueryStatus.SUCCESS, "Explicit idle SETINTERFACE failed; no clock mutation.")
            result = result.copy(idleAlternateReadback = control(UsbConfigurationControlRequest.getInterface(target)))
            guard()
            requireStage(result.idleMutation.established, "Descriptor-declared GET_INTERFACE contradicts the requested idle alternate; no clock mutation.")
            step = UsbConfigurationStep.CLOCK_SET
            guard()
            result = result.copy(clockWriteAttempted = true) // Includes failures/exception: a mutation might have reached the DAC.
            result = result.copy(targetClockSet = control(UsbConfigurationControlRequest.targetClock(requireNotNull(permit))))
            guard()
            requireStage(result.targetClockSet?.status == UsbClockQueryStatus.SUCCESS, "Target SET_CUR did not transfer exactly four bytes.")
            step = UsbConfigurationStep.CLOCK_READBACK
            result = result.copy(targetClockReadback = clock(UsbClockQuery.CURRENT_FREQUENCY))
            guard()
            requireStage(result.targetClockReadback?.status == UsbClockQueryStatus.SUCCESS && result.targetClockReadback?.currentFrequencyHz == target.sampleRateHz,
                "Target GET_CUR did not verify 44100 Hz.")
            step = UsbConfigurationStep.CLOCK_VALIDITY
            result = result.copy(targetValidity = validity())
            guard()
            requireStage(result.targetValidity.lastOrNull()?.result?.clockValid == true, "Clock did not become valid within the bounded window.")
            step = UsbConfigurationStep.ALTERNATE_SET
            guard()
            result = result.copy(alternateSelectionAttempted = true)
            result = result.copy(targetAlternateSet = alternate(UsbStreamingAlternateRequest.target(target)))
            guard()
            requireStage(result.targetAlternateSet?.status == UsbClockQueryStatus.SUCCESS, "Target SETINTERFACE failed.")
            step = UsbConfigurationStep.ALTERNATE_READBACK
            result = result.copy(targetAlternateReadback = control(UsbConfigurationControlRequest.getInterface(target)))
            guard()
            requireStage(result.targetMutation.established,
                "Descriptor-declared GET_INTERFACE contradicts the requested target alternate.")
            step = UsbConfigurationStep.CONFIGURED_CLOCK
            result = result.copy(configuredClockReadback = clock(UsbClockQuery.CURRENT_FREQUENCY))
            guard()
            requireStage(result.configuredClockReadback?.status == UsbClockQueryStatus.SUCCESS && result.configuredClockReadback?.currentFrequencyHz == target.sampleRateHz,
                "Clock frequency changed while the target alternate was selected.")
            step = UsbConfigurationStep.CONFIGURED_VALIDITY
            result = result.copy(configuredClockValidity = clock(UsbClockQuery.CLOCK_VALIDITY))
            guard()
            requireStage(result.configuredClockValidity?.status == UsbClockQueryStatus.SUCCESS && result.configuredClockValidity?.clockValid == true,
                "Configured Clock Validity is not true.")
            result = result.copy(targetStatus = UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED,
                detail = "PCM16 stereo / 44100 configuration verified by successful SETINTERFACE and clock readbacks; advisory alternate queries are reported separately." +
                    if (endpointWork == null) " No endpoint I/O; restoration begins immediately." else " Session 7 endpoint work precedes restoration.")
            if (endpointWork != null) {
                guard()
                endpointWork.runConfigured(result)
                endpointWork.authorityFailure?.let { terminalFailure = it }
                guard()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (stopped: Stop) {
            result = result.copy(failedStep = stopped.step)
            if (result.accessFailure == null) result = result.copy(targetStatus =
                if (result.clockWriteAttempted || result.alternateSelectionAttempted || result.idleSelectionAttempted) UsbTargetConfigurationStatus.FAILED else UsbTargetConfigurationStatus.BLOCKED,
                failedStep = stopped.step, detail = stopped.message)
        } catch (error: Exception) {
            result = result.copy(targetStatus = UsbTargetConfigurationStatus.EXCEPTION, failedStep = step,
                detail = "Configuration step threw ${error.javaClass.simpleName}; private exception message omitted.")
            if (error is SecurityException) terminalFailure = UsbClockQueryStatus.PERMISSION_LOST
            live()
        } finally {
            // Restore only this same still-authoritative connection. Coroutine cancellation
            // alone must not skip restoration/release; detach/stale/permission loss block writes.
            withContext(NonCancellable) {
                restoring = true
                try {
                    // Source teardown precedes restoration; releases/close remain in the outer finally even if it throws.
                    try { endpointWork?.beforeRestoration() }
                    catch (error: Exception) { result = result.copy(restorationNotes = result.restorationNotes +
                        "Source teardown failed (${error.javaClass.simpleName}); cleanup evidence remains unavailable.") }
                    if (plan != null && result.asClaim.returned == true && (result.idleSelectionAttempted || result.clockWriteAttempted || result.alternateSelectionAttempted)) {
                        if (endpointWork?.usbCleanupSafe == false) {
                            result = result.copy(restorationNotes = result.restorationNotes +
                                "Endpoint URBs are not fully accounted; alt/clock restoration and explicit releases skipped. Owning connection must close before native buffers can be freed.")
                        } else if (live() == null) {
                            // Always establish idle again, including uncertain initial/target mutations.
                            result = result.copy(idleAlternateRestore = alternate(UsbStreamingAlternateRequest.idle(plan)))
                            if (live() == null) result = result.copy(idleRestorationReadback = control(UsbConfigurationControlRequest.getInterface(plan)))
                            val idleVerified = result.restorationMutation.established
                            if (idleVerified && live() == null && permit != null && result.clockWriteAttempted) {
                                val restorationPermit = requireNotNull(permit)
                                if (restorationPermit.originalRateHz != plan.sampleRateHz) result = result.copy(
                                    originalClockRestore = control(UsbConfigurationControlRequest.restoreClock(restorationPermit)))
                                else result = result.copy(restorationNotes = result.restorationNotes + "Original clock was already 44100 Hz; redundant restore SET_CUR skipped.")
                                if (live() == null) result = result.copy(originalClockReadback = clock(UsbClockQuery.CURRENT_FREQUENCY))
                                if (live() == null) result = result.copy(originalClockValidity = validity())
                            } else if (result.clockWriteAttempted) result = result.copy(restorationNotes = result.restorationNotes + "Idle SETINTERFACE failed or declared alternate evidence contradicts idle; original clock write skipped to avoid changing a potentially active stream.")
                        } else result = result.copy(restorationNotes = result.restorationNotes + "Connection authority lost; configuration restoration writes skipped. Release/close still attempted where meaningful.")
                    }
                } catch (error: Exception) {
                    result = result.copy(restorationNotes = result.restorationNotes + "Restoration interrupted by ${error.javaClass.simpleName}; release/close still required.")
                } finally {
                    // Reverse acquisition order. Never resolves new Android handles in cleanup.
                    if (plan != null && result.asClaim.returned == true && endpointWork?.usbCleanupSafe != false) result = result.copy(asRelease =
                        if (connectionValid()) release { transport.releaseStreaming(plan.asClaim) }
                        else UsbConfigurationAndroidOwnership(note = "Invalid fd; AS release not meaningful. Connection close still attempted."))
                    if (plan != null && result.acClaim.returned == true && endpointWork?.usbCleanupSafe != false) result = result.copy(acRelease =
                        if (connectionValid()) release { transport.release(plan.acClaim) }
                        else UsbConfigurationAndroidOwnership(note = "Invalid fd; AC release not meaningful. Connection close still attempted."))
                    try {
                        if (plan != null && result.acClaim.returned == true && live() == null && endpointWork?.usbCleanupSafe != false) {
                            val releasesSucceeded = result.acRelease.returned == true && (result.asClaim.returned != true || result.asRelease.returned == true)
                            val baseline = UsbAudioForcedExperimentResult(generation, plan.probe, before = result.before,
                                claimReturned = true, releaseReturned = releasesSucceeded,
                                releaseStatus = if (releasesSucceeded) UsbAudioCleanupStatus.SUCCEEDED else UsbAudioCleanupStatus.FAILED)
                            val recovery = UsbAudioForcedExperiment.observeEnumerationRecovery(baseline, transport, ::live)
                            result = result.copy(enumerationRecovery = recovery)
                            recovery.accessFailure?.let { terminalFailure = it; live() }
                        }
                    } catch (error: Exception) {
                        result = result.copy(restorationNotes = result.restorationNotes + "Enumeration observation unavailable (${error.javaClass.simpleName}); no functional recovery inferred.")
                    } finally {
                        try { transport.close(); result = result.copy(closeStatus = UsbAudioCleanupStatus.SUCCEEDED) }
                        catch (_: Exception) { result = result.copy(closeStatus = UsbAudioCleanupStatus.EXCEPTION) }
                        try { endpointWork?.connectionClosed(result.closeStatus == UsbAudioCleanupStatus.SUCCEEDED) }
                        catch (_: Exception) { result = result.copy(restorationNotes = result.restorationNotes + "Native pool release after connection close failed; no safe teardown inferred.") }
                        val audio = try { transport.frameworkSnapshot() }
                        catch (_: Exception) { UsbAndroidAudioDiagnostics(detail = "Final Android audio query unavailable.", querySucceeded = false) }
                        result = result.copy(afterAndroidAudio = audio)
                        if (plan != null && result.acClaim.returned == true && result.accessFailure == null && result.enumerationRecovery == null) {
                            val releasesSucceeded = result.acRelease.returned == true && (result.asClaim.returned != true || result.asRelease.returned == true)
                            result = result.copy(enumerationRecovery = UsbAudioForcedExperimentResult(generation, plan.probe,
                                before = result.before, claimReturned = true, releaseReturned = releasesSucceeded,
                                releaseStatus = if (releasesSucceeded) UsbAudioCleanupStatus.SUCCEEDED else UsbAudioCleanupStatus.FAILED))
                        }
                        result.enumerationRecovery?.let { recovery ->
                            val final = recovery.copy(afterAndroidAudio = audio, closeStatus = result.closeStatus)
                            result = result.copy(enumerationRecovery = final.copy(recoveryStatus = UsbAudioForcedExperiment.classify(final)))
                        }
                    }
                }
            }
        }
        return result.copy(phase = UsbConfigurationPhase.FINISHED, restorationStatus = classifyRestoration(result))
    }

    /** Restores configuration only; Android releases/close are independent evidence. */
    fun classifyRestoration(result: UsbAudioConfigurationResult): UsbConfigurationRestorationStatus {
        if (!result.clockWriteAttempted && !result.alternateSelectionAttempted && !result.idleSelectionAttempted) return UsbConfigurationRestorationStatus.NOT_NEEDED
        if (result.accessFailure != null) return UsbConfigurationRestorationStatus.INDETERMINATE
        val idle = result.restorationMutation.established
        if (!result.clockWriteAttempted) return if (idle) UsbConfigurationRestorationStatus.RESTORED else UsbConfigurationRestorationStatus.FAILED
        val original = result.originalClockRateHz
        val rate = original != null && result.originalClockReadback?.status == UsbClockQueryStatus.SUCCESS && result.originalClockReadback?.currentFrequencyHz == original
        val rateSet = original == result.resolution.plan?.sampleRateHz || result.originalClockRestore?.status == UsbClockQueryStatus.SUCCESS
        val valid = result.originalClockValidity.lastOrNull()?.result?.let { it.status == UsbClockQueryStatus.SUCCESS && it.clockValid == true } == true
        return when {
            idle && rate && rateSet && valid -> UsbConfigurationRestorationStatus.RESTORED
            idle || rate || valid -> UsbConfigurationRestorationStatus.PARTIAL
            else -> UsbConfigurationRestorationStatus.FAILED
        }
    }
}
