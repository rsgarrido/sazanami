package io.github.rsgarrido.sazanami.player.usb

/** Prefix-source and USB evidence are both required; restoration is part of Session 10 success. */
internal object UsbFlacExperimentStatus {
    fun classify(configuration: UsbAudioConfigurationResult, usb: UsbIsoTransportResult?,
        source: UsbFlacSourceResult?, memoryReleased: Boolean): UsbSilentStatus {
        val faults = source?.count("sourceIntegrityMask") ?: 0L
        if (faults and 2048L != 0L || usb?.stopReason == "STOP_REQUESTED") return UsbSilentStatus.CANCELLED
        if (faults and 16L != 0L) return UsbSilentStatus.SOURCE_EOF_BEFORE_DURATION
        if (faults and 64L != 0L) return UsbSilentStatus.QUEUE_UNDERRUN
        if (source?.exceptionType != null || faults != 0L) return UsbSilentStatus.FLAC_SOURCE_FAILED
        if (source == null || !source.primed) return UsbSilentStatus.QUEUE_PRIME_FAILED
        if (configuration.targetStatus != UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED)
            return UsbSilentStatus.USB_CONFIGURATION_FAILED
        if (!configuration.selectedFormatMatches(source.count("sampleRate"),source.count("channels"),source.count("bitsPerSample"))) return UsbSilentStatus.USB_CONFIGURATION_FAILED
        if (usb == null || !usb.verified) return UsbSilentStatus.USB_TRANSPORT_FAILED
        if (usb.count("schedulerNominalRateHz")!=source.count("sampleRate") || usb.pcmFormat!=source.pcmFormat || usb.count("formatAlternate")!=configuration.resolution.plan?.streaming?.alternate?.toLong()) return UsbSilentStatus.USB_TRANSPORT_FAILED
        if (!source.clean || !memoryReleased) return UsbSilentStatus.RESOURCE_CLEANUP_FAILED
        if (source.count("framesConsumedFromQueue") != usb.count("framesSubmitted") ||
            source.count("consumedSourcePcmHash") != usb.count("submittedPcmHash")) return UsbSilentStatus.FLAC_SOURCE_FAILED
        if (configuration.restorationStatus != UsbConfigurationRestorationStatus.RESTORED ||
            configuration.asRelease.returned != true || configuration.acRelease.returned != true ||
            configuration.closeStatus != UsbAudioCleanupStatus.SUCCEEDED) return UsbSilentStatus.USB_CONFIGURATION_FAILED
        return UsbSilentStatus.BOUNDED_FLAC_USB_TRANSPORT_VERIFIED
    }
}
