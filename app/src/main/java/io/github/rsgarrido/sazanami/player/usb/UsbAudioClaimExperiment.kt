package io.github.rsgarrido.sazanami.player.usb

import kotlinx.coroutines.CancellationException

/** Android enumeration facts, with an opaque list position rather than an Android handle. */
internal data class UsbAudioClaimInterface(
    val position: Int,
    val number: Int,
    val alternate: Int,
    val interfaceClass: Int,
    val subclass: Int,
    val protocol: Int
) {
    val isUac2AudioControl: Boolean
        get() = interfaceClass == 1 && subclass == 1 && protocol == 0x20 && alternate == 0
}

/** The only constructible claim is a unique, resolved UAC2 AudioControl alternate zero.
 * Force is not a constructor/caller option.
 */
internal class UsbAudioControlClaimRequest private constructor(val audioControl: UsbAudioClaimInterface) {
    val force: Boolean get() = false

    companion object {
        fun resolve(target: UsbAudioClockTarget, interfaces: List<UsbAudioClaimInterface>): UsbAudioControlClaimRequest? {
            if (target.audioControl.alternate != 0 || target.audioControl.configurationOffset == null ||
                target.playbackPaths.any { it.number == target.audioControl.number }) return null
            val candidates = interfaces.filter {
                it.number == target.audioControl.number && it.alternate == target.audioControl.alternate && it.isUac2AudioControl
            }
            return candidates.singleOrNull()?.let { UsbAudioControlClaimRequest(it) }
        }
    }
}

internal enum class UsbAudioClaimStatus {
    NOT_ATTEMPTED, CLAIM_SUCCEEDED, CLAIM_FAILED, DEVICE_DISCONNECTED, STALE_GENERATION,
    PERMISSION_LOST, TARGET_AMBIGUOUS, INTERFACE_NOT_FOUND, TRANSPORT_ERROR, EXCEPTION
}
internal enum class UsbAudioCleanupStatus { NOT_ATTEMPTED, SUCCEEDED, FAILED, EXCEPTION }
internal enum class UsbAudioClaimComparison {
    NOT_ATTEMPTED, BASELINE_SUCCEEDED, CLAIM_UNAVAILABLE, ACCESS_CHANGED,
    CLAIM_IMPROVED_READS, CLAIM_DID_NOT_RESOLVE, INDETERMINATE
}

internal data class UsbAudioClaimExperimentResult(
    val generation: Long,
    val resolution: UsbAudioClockResolution,
    val claimStatus: UsbAudioClaimStatus = UsbAudioClaimStatus.NOT_ATTEMPTED,
    val claimRequest: UsbAudioControlClaimRequest? = null,
    val unclaimed: UsbAudioClockDiagnostics? = null,
    val claimed: UsbAudioClockDiagnostics? = null,
    val releaseStatus: UsbAudioCleanupStatus = UsbAudioCleanupStatus.NOT_ATTEMPTED,
    val closeStatus: UsbAudioCleanupStatus = UsbAudioCleanupStatus.NOT_ATTEMPTED,
    val detail: String? = null,
    val claimReturned: Boolean? = null,
    val accessFailure: UsbClockQueryStatus? = null
) {
    val force: Boolean get() = false

    private fun current(stage: UsbAudioClockDiagnostics?): UsbAudioClockDiagnostics? = stage?.takeIf {
        it.generation == generation && it.resolution.target == resolution.target
    }

    /** Failed access invalidates all previously decoded values from this operation. */
    fun preferredStage(query: UsbClockQuery): UsbAudioClockDiagnostics? {
        if (accessFailure != null || claimStatus in setOf(UsbAudioClaimStatus.DEVICE_DISCONNECTED, UsbAudioClaimStatus.STALE_GENERATION,
                UsbAudioClaimStatus.PERMISSION_LOST)) return null
        val after = current(claimed)?.takeIf { stage -> stage.results.any { it.query == query && it.status == UsbClockQueryStatus.SUCCESS } }
        val before = current(unclaimed)?.takeIf { stage -> stage.results.any { it.query == query && it.status == UsbClockQueryStatus.SUCCESS } }
        return after ?: before
    }

    fun comparison(): UsbAudioClaimComparison {
        if (claimStatus == UsbAudioClaimStatus.NOT_ATTEMPTED) return UsbAudioClaimComparison.NOT_ATTEMPTED
        if (accessFailure != null || claimStatus in setOf(UsbAudioClaimStatus.DEVICE_DISCONNECTED, UsbAudioClaimStatus.STALE_GENERATION,
                UsbAudioClaimStatus.PERMISSION_LOST)) return UsbAudioClaimComparison.ACCESS_CHANGED
        val before = current(unclaimed)?.results ?: return UsbAudioClaimComparison.INDETERMINATE
        if (before.size == UsbClockQuery.entries.size && before.all { it.status == UsbClockQueryStatus.SUCCESS })
            return UsbAudioClaimComparison.BASELINE_SUCCEEDED
        if (claimStatus == UsbAudioClaimStatus.CLAIM_FAILED) return UsbAudioClaimComparison.CLAIM_UNAVAILABLE
        if (claimStatus != UsbAudioClaimStatus.CLAIM_SUCCEEDED) return UsbAudioClaimComparison.INDETERMINATE
        val after = current(claimed)?.results ?: return UsbAudioClaimComparison.INDETERMINATE
        val rejected = before.filter { it.status == UsbClockQueryStatus.REJECTED_OR_TRANSPORT_ERROR }
        if (rejected.any { old -> after.any { it.query == old.query && it.status == UsbClockQueryStatus.SUCCESS } })
            return UsbAudioClaimComparison.CLAIM_IMPROVED_READS
        if (rejected.isNotEmpty() && rejected.all { old -> after.any {
                it.query == old.query && it.status == UsbClockQueryStatus.REJECTED_OR_TRANSPORT_ERROR
            } }) return UsbAudioClaimComparison.CLAIM_DID_NOT_RESOLVE
        return UsbAudioClaimComparison.INDETERMINATE
    }
}

/** One temporary connection, owned until release followed by close; no general USB API. */
internal interface UsbAudioClaimTransport : UsbClockReadTransport {
    fun interfaces(): List<UsbAudioClaimInterface>
    fun claim(request: UsbAudioControlClaimRequest): Boolean
    fun release(request: UsbAudioControlClaimRequest): Boolean
    fun close()
}

/** Synchronous worker operation. Both stages use the existing encoder/decoder on one connection.
 * Cancellation propagates only after finally has attempted release and close, without suspending.
 */
internal object UsbAudioClaimExperiment {
    fun run(resolution: UsbAudioClockResolution, generation: Long, transport: UsbAudioClaimTransport,
        accessFailure: () -> UsbClockQueryStatus? = { null }): UsbAudioClaimExperimentResult {
        var status = UsbAudioClaimStatus.NOT_ATTEMPTED
        var request: UsbAudioControlClaimRequest? = null
        var baseline: UsbAudioClockDiagnostics? = null
        var claimed: UsbAudioClockDiagnostics? = null
        var ownsAudioControl = false
        var claimReturned: Boolean? = null
        var failureStatus: UsbClockQueryStatus? = null
        var release = UsbAudioCleanupStatus.NOT_ATTEMPTED
        var close = UsbAudioCleanupStatus.NOT_ATTEMPTED
        var detail: String? = null
        fun checkAccess(): Boolean {
            val failure = accessFailure() ?: return true
            failureStatus = failure
            status = claimFailure(failure)
            detail = "Device access/generation changed; no further control requests are issued."
            return false
        }
        try {
            val target = resolution.target
            if (target == null) {
                status = UsbAudioClaimStatus.TARGET_AMBIGUOUS
                detail = "No unique topology-resolved AudioControl/Clock Source; no reads or claim issued."
            } else if (checkAccess()) {
                // Always repeat Stage A on this fresh connection/generation for a direct comparison.
                baseline = UsbAudioClockControl.query(resolution, generation, transport, accessFailure)
                if (checkAccess()) {
                    request = UsbAudioControlClaimRequest.resolve(target, transport.interfaces())
                    val selected = request
                    if (selected == null) {
                        status = UsbAudioClaimStatus.INTERFACE_NOT_FOUND
                        detail = "No unique Android UAC2 AudioControl alternate zero matches the resolved target."
                    } else if (checkAccess()) {
                        ownsAudioControl = transport.claim(selected)
                        claimReturned = ownsAudioControl
                        if (!ownsAudioControl) {
                            status = UsbAudioClaimStatus.CLAIM_FAILED
                            detail = "Android returned false for non-forced AudioControl claim; cause unknown. No claimed reads or forced retry."
                            checkAccess()
                        } else {
                            status = UsbAudioClaimStatus.CLAIM_SUCCEEDED
                            if (checkAccess()) {
                                claimed = UsbAudioClockControl.query(resolution, generation, transport, accessFailure)
                                    .copy(claimMode = UsbClockClaimMode.AUDIOCONTROL_NON_FORCED)
                                checkAccess()
                            }
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SecurityException) {
            status = UsbAudioClaimStatus.PERMISSION_LOST
            detail = "Android denied USB access during the claim experiment."
        } catch (_: Exception) {
            status = UsbAudioClaimStatus.EXCEPTION
            detail = "Claim experiment threw an exception; no cause or private exception text inferred."
        } finally {
            // No more reads after this point. Attempt release only when claim returned true.
            try {
                if (ownsAudioControl) {
                    release = try {
                        if (transport.release(requireNotNull(request))) UsbAudioCleanupStatus.SUCCEEDED else UsbAudioCleanupStatus.FAILED
                    } catch (_: Exception) { UsbAudioCleanupStatus.EXCEPTION }
                }
            } finally {
                close = try { transport.close(); UsbAudioCleanupStatus.SUCCEEDED }
                    catch (_: Exception) { UsbAudioCleanupStatus.EXCEPTION }
            }
        }
        return UsbAudioClaimExperimentResult(generation, resolution, status, request, baseline, claimed, release, close, detail,
            claimReturned, failureStatus)
    }

    fun diagnostics(result: UsbAudioClaimExperimentResult): UsbAudioClockDiagnostics {
        val results = UsbClockQuery.entries.map { query ->
            val preferred = result.preferredStage(query)
            preferred?.results?.firstOrNull { it.query == query } ?: if (result.accessFailure != null)
                UsbClockQueryResult(query, result.accessFailure, detail = "Access changed; prior values are not authoritative.")
            else when (result.claimStatus) {
                UsbAudioClaimStatus.DEVICE_DISCONNECTED, UsbAudioClaimStatus.STALE_GENERATION, UsbAudioClaimStatus.PERMISSION_LOST ->
                    UsbClockQueryResult(query, queryFailure(result.claimStatus), detail = "Access changed; prior values are not authoritative.")
                else -> (result.claimed ?: result.unclaimed)?.takeIf {
                    it.generation == result.generation && it.resolution.target == result.resolution.target
                }?.results?.firstOrNull { it.query == query } ?: UsbClockQueryResult(query,
                    queryFailure(result.claimStatus), detail = result.detail)
            }
        }
        val successful = results.count { it.status == UsbClockQueryStatus.SUCCESS }
        val phase = when (successful) {
            results.size -> UsbClockDiagnosticPhase.COMPLETE
            0 -> UsbClockDiagnosticPhase.UNAVAILABLE
            else -> UsbClockDiagnosticPhase.PARTIAL
        }
        return UsbAudioClockDiagnostics(result.generation, result.resolution, phase,
            if (result.claimReturned == true || result.claimed != null) UsbClockClaimMode.AUDIOCONTROL_NON_FORCED
            else result.unclaimed?.claimMode ?: UsbClockClaimMode.NOT_ATTEMPTED, results, result)
    }

    /** Publication revalidation must retain cleanup facts while omitting obsolete stage values. */
    fun invalidate(result: UsbAudioClaimExperimentResult, failure: UsbClockQueryStatus, detail: String): UsbAudioClockDiagnostics =
        diagnostics(result.copy(claimStatus = claimFailure(failure), unclaimed = null, claimed = null,
            accessFailure = failure, detail = detail))

    fun claimFailure(status: UsbClockQueryStatus): UsbAudioClaimStatus = when (status) {
        UsbClockQueryStatus.DEVICE_DISCONNECTED -> UsbAudioClaimStatus.DEVICE_DISCONNECTED
        UsbClockQueryStatus.STALE_GENERATION -> UsbAudioClaimStatus.STALE_GENERATION
        UsbClockQueryStatus.PERMISSION_LOST -> UsbAudioClaimStatus.PERMISSION_LOST
        UsbClockQueryStatus.AMBIGUOUS_TARGET -> UsbAudioClaimStatus.TARGET_AMBIGUOUS
        UsbClockQueryStatus.TRANSPORT_ERROR -> UsbAudioClaimStatus.TRANSPORT_ERROR
        else -> UsbAudioClaimStatus.EXCEPTION
    }

    private fun queryFailure(status: UsbAudioClaimStatus): UsbClockQueryStatus = when (status) {
        UsbAudioClaimStatus.DEVICE_DISCONNECTED -> UsbClockQueryStatus.DEVICE_DISCONNECTED
        UsbAudioClaimStatus.STALE_GENERATION -> UsbClockQueryStatus.STALE_GENERATION
        UsbAudioClaimStatus.PERMISSION_LOST -> UsbClockQueryStatus.PERMISSION_LOST
        UsbAudioClaimStatus.TARGET_AMBIGUOUS -> UsbClockQueryStatus.AMBIGUOUS_TARGET
        else -> UsbClockQueryStatus.TRANSPORT_ERROR
    }
}
