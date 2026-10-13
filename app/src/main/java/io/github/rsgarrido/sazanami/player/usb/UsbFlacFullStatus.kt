package io.github.rsgarrido.sazanami.player.usb

internal object UsbFlacFullStatus {
    fun sourceFailure(source: UsbFlacSourceResult?): UsbSilentStatus {
        val faults=source?.count("sourceIntegrityMask") ?: 0L
        return when {
            faults and 2097152L != 0L || (source?.count("sourceSeekFailures") ?: 0L)>0L -> UsbSilentStatus.FLAC_SOURCE_FAILED
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
        if(usb!=null && (usb.count("seekFailures")>0L || source.count("sourceSeekFailures")>0L)) return UsbSilentStatus.USB_TRANSPORT_FAILED
        // A concurrent Stop may cancel the producer, but cannot hide a real pause/resume/USB fault.
        if (usb!=null && (usb.count("pauseDrainFailures")>0L || usb.count("resumeFailure")>0L ||
            (usb.stopReason=="STOP_REQUESTED" && usb.count("playbackTransportState")==UsbFullTransportPhase.FAILED.ordinal.toLong())))
            return UsbSilentStatus.USB_TRANSPORT_FAILED
        if (source.count("sourceIntegrityMask") != 0L || source.exceptionType != null) return sourceFailure(source)
        if (source.decoder?.nativeStatus==io.github.rsgarrido.sazanami.player.bitperfect.flac.FlacVerificationStatus.FLAC_MD5_MISMATCH ||
            source.decoder?.count("md5Verification")==2L) return UsbSilentStatus.FLAC_MD5_MISMATCH
        if (usb?.stopReason=="STOP_REQUESTED") return UsbSilentStatus.CANCELLED
        if (!source.primed) return UsbSilentStatus.QUEUE_PRIME_FAILED
        if (configuration.targetStatus!=UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED) return UsbSilentStatus.USB_CONFIGURATION_FAILED
        if (!configuration.selectedFormatMatches(source.count("sampleRate"),source.count("channels"),source.count("bitsPerSample"))) return UsbSilentStatus.USB_CONFIGURATION_FAILED
        if (usb?.stopReason=="OUT_POOL_STALL") return UsbSilentStatus.OUT_POOL_STALL
        if (usb?.stopReason=="SOURCE_VERIFICATION_STALLED") return UsbSilentStatus.SOURCE_VERIFICATION_STALLED
        if (usb?.integrityReasons?.contains("TERMINAL_TAIL_ACCOUNTING_FAILED")==true) return UsbSilentStatus.TERMINAL_TAIL_ACCOUNTING_FAILED
        if (usb==null || !(if (source.referenceUnavailableComplete) usb.fullPlaybackComplete && usb.count("md5VerificationPass")==0L else usb.verified))
            return UsbSilentStatus.USB_TRANSPORT_FAILED
        if (usb.count("schedulerNominalRateHz")!=source.count("sampleRate") || usb.pcmFormat!=source.pcmFormat || usb.count("formatAlternate")!=configuration.resolution.plan?.streaming?.alternate?.toLong()) return UsbSilentStatus.USB_TRANSPORT_FAILED
        if(usb.count("seekConfirmed")>0L) {
            if(!source.seekComplete || !memoryReleased) return UsbSilentStatus.RESOURCE_CLEANUP_FAILED
            var invalidated=0L
            for(field in listOf("intentionallyInvalidatedQueuedFrames","intentionallyInvalidatedCanonicalFrames","intentionallyInvalidatedRawFrames")) {
                val value=source.count(field)
                if(value<0L || invalidated>Long.MAX_VALUE-value) return UsbSilentStatus.TERMINAL_TAIL_ACCOUNTING_FAILED
                invalidated+=value
            }
            if(source.count("sourceEpoch")!=usb.count("seekSourceEpoch") || source.count("sourceSegmentStartFrame")!=usb.count("confirmedSeekTargetFrame") ||
                source.count("generation")!=usb.generation || source.count("streamGeneration")!=usb.streamGeneration ||
                invalidated!=usb.count("seekInvalidatedFrames") || source.count("sourceSeekAttempts")!=usb.count("sourceSeekAttempts") ||
                source.count("sourceFirstSeekFrame")!=usb.count("seekFirstSourceFrame") || source.count("framesConsumedFromQueue")!=usb.count("framesSubmitted") ||
                source.decoder?.counters?.get("pcmFnv1a")!=usb.counters["wholeDecodedPcmHash"] ||
                source.counters["allEnqueuedPrefetchPcmHash"]!=usb.counters["allEnqueuedPcmHash"] ||
                source.counters["consumedSourcePcmHash"]!=usb.counters["sourcePcmHash"] || source.count("totalSamples")!=usb.count("streamInfoTotalSamples"))
                return UsbSilentStatus.TERMINAL_TAIL_ACCOUNTING_FAILED
            if(configuration.restorationStatus!=UsbConfigurationRestorationStatus.RESTORED || configuration.asRelease.returned!=true ||
                configuration.acRelease.returned!=true || configuration.closeStatus!=UsbAudioCleanupStatus.SUCCEEDED) return UsbSilentStatus.USB_CONFIGURATION_FAILED
            return UsbSilentStatus.SEEKED_SEGMENTS_USB_TRANSPORT_VERIFIED
        }
        if (!source.fullPlaybackComplete || !memoryReleased) return UsbSilentStatus.RESOURCE_CLEANUP_FAILED
        // Both snapshots must describe the same entire source; independent internal consistency is insufficient.
        if ((source.referenceUnavailableComplete && (source.count("generation")!=usb.generation || source.count("streamGeneration")!=usb.streamGeneration)) ||
            source.decoder?.count("sampleRate")!=source.count("sampleRate") ||
            source.count("totalSamples")!=usb.count("streamInfoTotalSamples") ||
            source.count("framesConsumedFromQueue")!=usb.count("framesSubmitted") ||
            source.decoder?.count("canonicalPcmBytesHashed")!=usb.count("outBytesSubmitted") ||
            source.decoder?.counters?.get("pcmFnv1a")!=usb.counters["wholeDecodedPcmHash"] ||
            source.counters["allEnqueuedPrefetchPcmHash"]!=usb.counters["allEnqueuedPcmHash"] ||
            source.counters["consumedSourcePcmHash"]!=usb.counters["sourcePcmHash"])
            return UsbSilentStatus.TERMINAL_TAIL_ACCOUNTING_FAILED
        if (configuration.restorationStatus!=UsbConfigurationRestorationStatus.RESTORED || configuration.asRelease.returned!=true ||
            configuration.acRelease.returned!=true || configuration.closeStatus!=UsbAudioCleanupStatus.SUCCEEDED) return UsbSilentStatus.USB_CONFIGURATION_FAILED
        return if (source.fullComplete) UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED
            else UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_COMPLETE_MD5_UNAVAILABLE
    }
}
