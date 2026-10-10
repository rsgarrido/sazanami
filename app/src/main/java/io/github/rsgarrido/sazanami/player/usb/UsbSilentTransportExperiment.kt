package io.github.rsgarrido.sazanami.player.usb

import kotlinx.coroutines.CancellationException

/** Optional work in the SAME configuration transaction. Returning never starts a second ownership lifecycle. */
internal interface UsbConfiguredEndpointWork {
    fun baselineIssue(configuration: UsbAudioConfigurationResult): String?
    suspend fun runConfigured(configuration: UsbAudioConfigurationResult)
    val usbCleanupSafe: Boolean
    val authorityFailure: UsbClockQueryStatus? get() = null
    fun connectionClosed(succeeded: Boolean)
    suspend fun beforeRestoration() {}
    fun forwardIssue(): String? = null
}
internal interface UsbSilentTransportBackend {
    suspend fun run(): UsbIsoTransportResult
    val usbCleanupSafe: Boolean
    val memoryReleased: Boolean
    fun connectionClosed(succeeded: Boolean)
}

internal object UsbSilentTransportExperiment {
    suspend fun run(resolution: UsbSilentTransportResolution, generation: Long, stream: Long,
        connection: UsbAudioConfigurationTransport, factory: (UsbSilentTransportPlan, UsbIsoTiming) -> UsbSilentTransportBackend,
        onPhase: suspend (UsbSilentPhase) -> Unit = {},
        mode: UsbPcmDiagnosticMode = UsbPcmDiagnosticMode.ZERO,
        preflight: () -> UsbReferencePreflight = UsbFsDiagnostics::referencePreflight,
        prefixSource: UsbFlacSourceLifecycle? = null,
        accessFailure: () -> UsbClockQueryStatus? = { null }): UsbSilentTransportExperimentResult {
        val plan = resolution.plan
        var timing: UsbIsoTiming? = null
        var native: UsbIsoTransportResult? = null
        var backend: UsbSilentTransportBackend? = null
        var closeRequired = false
        var endpointStarted = false
        val reference = if (mode == UsbPcmDiagnosticMode.REFERENCE) preflight() else null
        val work = object : UsbConfiguredEndpointWork {
            override fun baselineIssue(configuration: UsbAudioConfigurationResult): String? {
                if (mode.isFlac && prefixSource?.healthy() != true)
                    return "FLAC queue was not primed; no AC/AS ownership attempted."
                if (mode == UsbPcmDiagnosticMode.REFERENCE && reference?.passed != true)
                    return "REFERENCE_PREFLIGHT_FAILED; no AC/AS ownership attempted."
                timing = plan?.timing(configuration.before?.speed?.mapped)
                return if (timing == null) "Actual High Speed and supported endpoint timing are required; no ownership attempted." else null
            }
            override suspend fun runConfigured(configuration: UsbAudioConfigurationResult) {
                require(configuration.targetStatus == UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED)
                endpointStarted = true
                try {
                    onPhase(UsbSilentPhase.STARTING_TRANSPORT)
                    backend = factory(requireNotNull(plan), requireNotNull(timing))
                    native = requireNotNull(backend).run()
                    if (native?.generation != generation || native?.streamGeneration != stream)
                        native = UsbIsoTransportResult(generation, stream, exceptionType = "StaleStreamResult", authorityFailure = UsbClockQueryStatus.STALE_GENERATION)
                    else if (native?.exceptionType == null && native?.count("sourceMode") != mode.ordinal.toLong())
                        native = native?.copy(exceptionType = "SourceModeMismatch")
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { native = UsbIsoTransportResult(generation, stream, exceptionType = error.javaClass.simpleName) }
                finally {
                    closeRequired = backend?.usbCleanupSafe == false
                    onPhase(UsbSilentPhase.TRANSPORT_STOPPED)
                    onPhase(UsbSilentPhase.RESTORING_USB)
                }
            }
            override fun forwardIssue() = if (mode.isFlac && !endpointStarted && prefixSource?.healthy() != true)
                "FLAC source lost primed/healthy state before endpoint start; forward configuration stopped." else null
            override val usbCleanupSafe get() = backend?.usbCleanupSafe ?: true
            override suspend fun beforeRestoration() { prefixSource?.finish() }
            override val authorityFailure get() = native?.authorityFailure
            override fun connectionClosed(succeeded: Boolean) { backend?.connectionClosed(succeeded) }
        }
        val configurationResolution = plan?.configuration ?: UsbAudioConfigurationResolution(issues = resolution.issues)
        val configuration = UsbAudioConfigurationExperiment.runWithEndpointWork(configurationResolution, generation, connection, work, accessFailure)
        val status = when {
            configuration.accessFailure != null || native?.authorityFailure != null -> UsbSilentStatus.NON_AUTHORITATIVE
            mode == UsbPcmDiagnosticMode.FLAC_FULL -> UsbFlacFullStatus.classify(configuration,native,prefixSource?.result,
                !closeRequired && backend?.memoryReleased != false)
            mode == UsbPcmDiagnosticMode.FLAC_PREFIX -> UsbFlacExperimentStatus.classify(configuration,native,prefixSource?.result,
                !closeRequired && backend?.memoryReleased != false)
            native?.verified == true && !closeRequired && backend?.memoryReleased != false && configuration.targetStatus == UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED ->
                if (mode == UsbPcmDiagnosticMode.REFERENCE && reference?.passed == true) UsbSilentStatus.DETERMINISTIC_PCM_TRANSPORT_VERIFIED else UsbSilentStatus.SILENT_TRANSPORT_VERIFIED
            native == null && configuration.targetStatus == UsbTargetConfigurationStatus.BLOCKED -> UsbSilentStatus.BLOCKED
            else -> UsbSilentStatus.FAILED
        }
        val closedAndReleased = configuration.closeStatus == UsbAudioCleanupStatus.SUCCEEDED && backend?.memoryReleased != false
        val pendingAtWorkerStop = native?.counters?.get("outstandingUrbsFinal")
        return UsbSilentTransportExperimentResult(generation, stream, resolution, status = status, configuration = configuration,
            timing = timing, transport = native, detail = if (native == null) configuration.detail else "Transport, restoration and Android recovery are separate evidence.",
            nativeMemoryReleased = backend?.memoryReleased ?: true, connectionCloseRequired = closeRequired || !work.usbCleanupSafe,
            connectionCloseDisposedUrbs = if (closedAndReleased && closeRequired) pendingAtWorkerStop else null,
            outstandingAfterConnectionClose = if (closedAndReleased) 0 else pendingAtWorkerStop, mode = mode, preflight = reference,
            flacSource = prefixSource?.result)
    }
}
