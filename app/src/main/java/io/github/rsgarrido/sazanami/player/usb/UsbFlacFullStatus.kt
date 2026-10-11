package io.github.rsgarrido.sazanami.player.usb

internal object UsbFlacFullStatus {
    fun sourceFailure(source: UsbFlacSourceResult?): UsbSilentStatus {
        val faults=source?.count("sourceIntegrityMask") ?: 0L
        return when {
            faults and 2048L != 0L -> UsbSilentStatus.CANCELLED
            faults and 131072L != 0L -> UsbSilentStatus.FLAC_MD5_UNAVAILABLE
            faults and 262144L != 0L -> UsbSilentStatus.FLAC_MD5_MISMATCH
            faults and 524288L != 0L -> UsbSilentStatus.STREAMINFO_TOTAL_MISMATCH
            faults and 1048576L != 0L -> UsbSilentStatus.TERMINAL_TAIL_ACCOUNTING_FAILED
            faults and 64L != 0L -> UsbSilentStatus.QUEUE_UNDERRUN
            faults != 0L || source?.exceptionType != null -> UsbSilentStatus.FLAC_SOURCE_FAILED
            else -> UsbSilentStatus.QUEUE_PRIME_FAILED
        }
    }
    fun classify(configuration: UsbAudioConfigurationResult,usb: UsbIsoTransportResult?,source: UsbFlacSourceResult?,memoryReleased: Boolean): UsbSilentStatus {
        if (source==null) return UsbSilentStatus.QUEUE_PRIME_FAILED
        if (source.count("sourceIntegrityMask") != 0L || source.exceptionType != null) return sourceFailure(source)
        if (usb?.stopReason=="STOP_REQUESTED") return UsbSilentStatus.CANCELLED
        if (!source.primed) return UsbSilentStatus.QUEUE_PRIME_FAILED
        if (configuration.targetStatus!=UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED) return UsbSilentStatus.USB_CONFIGURATION_FAILED
        if (!configuration.selectedRateMatches(source.count("sampleRate"))) return UsbSilentStatus.USB_CONFIGURATION_FAILED
        if (usb?.stopReason=="OUT_POOL_STALL") return UsbSilentStatus.OUT_POOL_STALL
        if (usb?.stopReason=="SOURCE_VERIFICATION_STALLED") return UsbSilentStatus.SOURCE_VERIFICATION_STALLED
        if (usb?.integrityReasons?.contains("TERMINAL_TAIL_ACCOUNTING_FAILED")==true) return UsbSilentStatus.TERMINAL_TAIL_ACCOUNTING_FAILED
        if (usb==null || !usb.verified) return UsbSilentStatus.USB_TRANSPORT_FAILED
        if (usb.count("schedulerNominalRateHz")!=source.count("sampleRate")) return UsbSilentStatus.USB_TRANSPORT_FAILED
        if (!source.fullComplete || !memoryReleased) return UsbSilentStatus.RESOURCE_CLEANUP_FAILED
        // Both snapshots must describe the same entire source; independent internal consistency is insufficient.
        if (source.decoder?.count("sampleRate")!=source.count("sampleRate") ||
            source.count("totalSamples")!=usb.count("streamInfoTotalSamples") ||
            source.count("framesConsumedFromQueue")!=usb.count("framesSubmitted") ||
            source.decoder?.count("canonicalPcmBytesHashed")!=usb.count("outBytesSubmitted") ||
            source.decoder?.counters?.get("pcmFnv1a")!=usb.counters["wholeDecodedPcmHash"] ||
            source.counters["allEnqueuedPrefetchPcmHash"]!=usb.counters["allEnqueuedPcmHash"] ||
            source.counters["consumedSourcePcmHash"]!=usb.counters["sourcePcmHash"])
            return UsbSilentStatus.TERMINAL_TAIL_ACCOUNTING_FAILED
        if (configuration.restorationStatus!=UsbConfigurationRestorationStatus.RESTORED || configuration.asRelease.returned!=true ||
            configuration.acRelease.returned!=true || configuration.closeStatus!=UsbAudioCleanupStatus.SUCCEEDED) return UsbSilentStatus.USB_CONFIGURATION_FAILED
        return UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED
    }
}
