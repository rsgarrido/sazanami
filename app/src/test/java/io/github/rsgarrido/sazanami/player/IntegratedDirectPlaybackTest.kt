package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.player.usb.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

/** Synthetic policy/evidence and source-boundary checks. Authored, never executed by Codex. */
class IntegratedDirectPlaybackTest {
    @Test fun synthetic48000FullEofUsesSameEndedPredicateAndSelectedDuration() {
        val fixture=fullResult()
        val rate=48000L
        val result=fixture.copy(flacSource=fixture.flacSource!!.copy(counters=fixture.flacSource!!.counters+("sampleRate" to rate)),
            transport=fixture.transport!!.copy(counters=fixture.transport!!.counters+("schedulerNominalRateHz" to rate)))
        val ended=integratedTerminalState(IntegratedDirectPlaybackState(enabled=true),result)
        assertEquals(IntegratedDirectPhase.ENDED,ended.phase);assertNull(ended.error)
        assertEquals(rate,ended.sampleRateHz)
        assertEquals(10001L*1000/rate,ended.durationMs);assertEquals(ended.durationMs,ended.positionMs)
        assertEquals(IntegratedDirectPhase.ERROR,integratedTerminalState(IntegratedDirectPlaybackState(),
            result.copy(transport=fixture.transport)).phase)
        val unsupported=fullResult(UsbSilentStatus.FLAC_SOURCE_FAILED).copy(flacSource=UsbFlacSourceResult(mapOf(
            "sampleRate" to 44100L,"channels" to 2L,"bitsPerSample" to 24L,"sourceIntegrityMask" to 4L)))
        assertEquals("UNSUPPORTED_TRACK",integratedTerminalState(IntegratedDirectPlaybackState(enabled=true),unsupported).error)
    }
    private fun source(path: String)=listOf(Paths.get("src/main",path),Paths.get("app/src/main",path)).first { Files.isRegularFile(it) }.toFile().readText()
    private fun fullResult(status: UsbSilentStatus=UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED): UsbSilentTransportExperimentResult {
        val values=UsbIsoNativeStats.fields.associateWith { 0L }.toMutableMap().apply { putAll(mapOf(
            "formatChannels" to 2L,"formatBitsPerSample" to 16L,"formatBytesPerSample" to 2L,"formatBytesPerFrame" to 4L,"formatSubslotBytes" to 2L,"formatValidBits" to 16L,"formatAlternate" to 9L,"schedulerNominalRateHz" to 44100L,"playbackTransportState" to 7L,"sourceMode" to 3L,"stopReason" to 14L,"started" to 1L,"workerStopped" to 1L,"state" to 7L,
            "streamInfoTotalSamples" to 10001L,"sourceFramesGenerated" to 10001L,"framesScheduled" to 10001L,"framesSubmitted" to 10001L,
            "framesCompleted" to 10001L,"prefixSourceFramesConsumed" to 10001L,"feedbackPacketsValid" to 1L,"feedbackScheduledPackets" to 1L,
            "trueEofReached" to 1L,"producerFinishedNaturally" to 1L,"md5VerificationPass" to 1L,"prefixProducerStopped" to 1L,"prefixProducerJoined" to 1L,
            "prefixInitialPrimedFrames" to 8192L,"finalSubmittedSourceFrame" to 10000L,"finalCompletedSourceFrame" to 10000L,
            "outUrbsSubmitted" to 30L,"outUrbsCompleted" to 30L,"outPacketsSubmitted" to 235L,"outPacketsCompleted" to 235L,
            "outBytesSubmitted" to 40004L,"kernelOutActualBytes" to 40004L,"terminalTailOutUrbs" to 1L,"terminalTailPackets" to 3L,
            "terminalTailFrames" to 101L,"remainingFramesAtTerminalTransition" to 101L,"terminalTailPacketCompletions" to 3L,"terminalTailFrameCompletions" to 101L,
            "wholeDecodedPcmHash" to 123L,"allEnqueuedPcmHash" to 123L,"sourcePcmHash" to 123L,"submittedPcmHash" to 123L)) }
        return UsbSilentTransportExperimentResult(1,2,UsbSilentTransportResolution(),status=status,
            configuration=UsbAudioConfigurationResult(1,UsbAudioConfigurationResolution(),targetStatus=UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED,
                acClaim=UsbConfigurationAndroidOwnership(true,true),asClaim=UsbConfigurationAndroidOwnership(true,true),
                restorationStatus=UsbConfigurationRestorationStatus.RESTORED,asRelease=UsbConfigurationAndroidOwnership(true,true),
                acRelease=UsbConfigurationAndroidOwnership(true,true),closeStatus=UsbAudioCleanupStatus.SUCCEEDED),
            transport=UsbIsoTransportResult(1,2,values),originalFlacFdClosed=true,
            flacSource=UsbFlacSourceResult(mapOf("sampleRate" to 44100L,"totalSamples" to 10001L,"producerJoined" to 1L)))
    }
    @Test fun positionUsesIntegerCompletedFramesAndIsBoundedWithoutClock() {
        assertEquals(0L,completedFramePositionMs(0));assertEquals(1000L,completedFramePositionMs(44100))
        assertEquals(1L,completedFramePositionMs(45));assertEquals(226L,completedFramePositionMs(10001))
        assertTrue(completedFramePositionMs(Long.MAX_VALUE)>0)
        assertEquals(1000L,IntegratedDirectPlaybackState(completedFrames=88200,totalFrames=44100,sampleRateHz=44100).positionMs)
        assertEquals(1200L,IntegratedDirectPlaybackState(metadataDurationMs=1200).durationMs)
        for(n in listOf(1L,44L,45L,44100L,1000000000L)) assertTrue(completedFramePositionMs(n)>=0)
    }
    @Test fun naturalResultEndsAtFullDurationWithoutAdvancingQueue() {
        val previous=IntegratedDirectPlaybackState(enabled=true,phase=IntegratedDirectPhase.PLAYING,generation=7,libraryId=42)
        val result=fullResult();assertTrue(result.transport!!.verified);assertTrue(result.integratedCleanupSafe())
        val end=integratedTerminalState(previous,result)
        assertEquals(IntegratedDirectPhase.ENDED,end.phase);assertEquals(end.durationMs,end.positionMs)
        assertEquals(42L,end.libraryId);assertEquals(7L,end.generation);assertSame(result,end.engine)
    }
    @Test fun everyDirectFailureAndCancellationCannotRemainPlaying() {
        val previous=IntegratedDirectPlaybackState(enabled=true,phase=IntegratedDirectPhase.PLAYING)
        for(status in UsbSilentStatus.entries.filter { it!=UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED }) {
            val final=integratedTerminalState(previous,fullResult(status))
            assertFalse(final.busy)
            assertEquals(if(status==UsbSilentStatus.CANCELLED) IntegratedDirectPhase.STOPPED else IntegratedDirectPhase.ERROR,final.phase)
        }
        val bad=fullResult().let { it.copy(transport=it.transport!!.copy(counters=it.transport.counters+("framesCompleted" to 10000L))) }
        assertEquals(IntegratedDirectPhase.ERROR,integratedTerminalState(previous,bad).phase)
    }
    @Test fun restorationAndReleasesGateNormalReactivation() {
        val base=fullResult()
        for(bad in listOf(base.copy(nativeMemoryReleased=false),base.copy(connectionCloseRequired=true),base.copy(originalFlacFdClosed=false),
            base.copy(configuration=base.configuration!!.copy(restorationStatus=UsbConfigurationRestorationStatus.FAILED)),
            base.copy(configuration=base.configuration!!.copy(asRelease=UsbConfigurationAndroidOwnership(true,false))),
            base.copy(configuration=base.configuration!!.copy(acRelease=UsbConfigurationAndroidOwnership(true,false))),
            base.copy(configuration=base.configuration!!.copy(closeStatus=UsbAudioCleanupStatus.FAILED)))) {
            assertFalse(bad.integratedCleanupSafe());assertEquals(IntegratedDirectPhase.ERROR,integratedTerminalState(IntegratedDirectPlaybackState(),bad).phase)
        }
        val beforeOwnership=base.copy(status=UsbSilentStatus.FLAC_INPUT_OPEN_FAILED,transport=null,flacSource=null,originalFlacFdClosed=false,
            configuration=UsbAudioConfigurationResult(1,UsbAudioConfigurationResolution(),targetStatus=UsbTargetConfigurationStatus.BLOCKED))
        assertTrue(beforeOwnership.integratedCleanupSafe()) // failed open acquired no FD or USB ownership
    }
    @Test fun sourceFormatAndErrorTaxonomyAreExplicit() {
        val base=fullResult(UsbSilentStatus.FLAC_SOURCE_FAILED)
        assertEquals("UNSUPPORTED_TRACK",integratedDirectFailure(base.copy(flacSource=UsbFlacSourceResult(mapOf("sourceIntegrityMask" to 4L)))))
        assertEquals("UNSUPPORTED_TRACK",integratedDirectFailure(base.copy(flacSource=UsbFlacSourceResult(mapOf("sourceIntegrityMask" to 16384L)))))
        assertEquals("DIRECT_SOURCE_FAILED",integratedDirectFailure(base))
        assertEquals("DIRECT_USB_PERMISSION_FAILED",integratedDirectFailure(base.copy(configuration=base.configuration!!.copy(accessFailure=UsbClockQueryStatus.PERMISSION_LOST))))
        assertEquals("DIRECT_USB_DEVICE_UNAVAILABLE",integratedDirectFailure(base.copy(configuration=base.configuration!!.copy(accessFailure=UsbClockQueryStatus.DEVICE_DISCONNECTED))))
    }
    @Test fun recreatedServiceWaitsForOwnedCleanupLease()=runBlocking {
        val lease=DirectTeardownLease();assertNull(lease.safe)
        val waiting=async { lease.awaitSafe() };yield();assertFalse(waiting.isCompleted)
        lease.complete(false);assertFalse(waiting.await())
        val clean=DirectTeardownLease();clean.complete(true);assertTrue(clean.awaitSafe())
    }
    @Test fun sameEngineIsUsedByBothClientsAndSourceIsLibraryUri() {
        val adapter=source("java/io/github/rsgarrido/sazanami/player/DirectUsbMediaPlayer.kt")
        val controller=source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioController.kt")
        fun controllerMethod(start: String,end: String): String {
            val startIndex=controller.indexOf(start)
            assertTrue("Missing controller method: $start",startIndex>=0)
            val endIndex=controller.indexOf(end,startIndex+start.length)
            assertTrue("Missing controller method boundary: $end",endIndex>startIndex)
            return controller.substring(startIndex,endIndex).replace(Regex("\\s+"),"")
        }
        val standalone=controllerMethod("fun playFullFlac(","override suspend fun runIntegratedFull(")
        val integrated=controllerMethod("override suspend fun runIntegratedFull(","override fun stopIntegratedFull(")
        val configuration=controllerMethod("private fun runConfigurationDiagnostic(","private fun usbAccessFailure(")
        assertTrue(adapter.contains("usb.runIntegratedFull(song.uri,commands)"));assertTrue(adapter.contains("PlaybackLibraryBridge.songs"))
        assertFalse(adapter.contains("OpenDocument"));assertFalse(adapter.contains("filePath"))
        assertTrue("Standalone full playback must reach the shared engine",standalone.contains("runConfigurationDiagnostic(identity,true,UsbPcmDiagnosticMode.FLAC_FULL,uri)"))
        assertTrue("Integrated full playback must pass its URI/client to the shared engine",integrated.contains("runConfigurationDiagnostic(selected.identity,true,UsbPcmDiagnosticMode.FLAC_FULL,uri,client)"))
        assertTrue(configuration.contains("elsebase.withEndpointExperiment(mode,value)"));assertTrue(configuration.contains("integratedFlacUsbExperiment=value"))
        assertTrue(configuration.contains("UsbSilentTransportExperiment.run("));assertTrue(configuration.contains("transport.silentBackend("))
        val prepareIndex=configuration.indexOf("source.prepare")
        val openIndex=configuration.indexOf("usb.openDevice(live)")
        assertTrue("Missing FLAC source preparation in the shared engine",prepareIndex>=0)
        assertTrue("Missing USB open in the shared engine",openIndex>=0)
        assertTrue("Shared engine must prime FLAC before USB open: prepare=$prepareIndex, open=$openIndex",prepareIndex<openIndex)
        assertTrue(adapter.contains("if(evidence.generation!=generation) return@launch"))
    }
    @Test fun normalPathCannotDecodeBelowDirectAndNoFallbackExists() {
        val route=source("java/io/github/rsgarrido/sazanami/player/ExperimentalUsbPlaybackPlayer.kt")
        val service=source("java/io/github/rsgarrido/sazanami/player/PlaybackService.kt")
        val dual=source("java/io/github/rsgarrido/sazanami/player/DualPlayerPlaybackCoordinator.kt")
        assertTrue(route.contains("normal.stop()"));assertTrue(route.contains("releaseNormal()"));assertFalse(route.contains("volume = 0"))
        assertTrue(dual.contains("active.player.stop();standby.player.stop()"));assertTrue(dual.contains("player.release()"))
        assertTrue(dual.contains("crossfadeTransition.release()"));assertTrue(dual.contains("standbyPreparation.release()"))
        assertTrue(service.contains("physicalPlayers.releaseForDirect()"));assertTrue(route.indexOf("direct.stopAndJoin()")<route.indexOf("recreateNormal()"))
        val adapter=source("java/io/github/rsgarrido/sazanami/player/DirectUsbMediaPlayer.kt")
        for(name in listOf("ExoPlayer","AudioTrack","DefaultAudioSink","AudioSink","ReplayGain","volume =","MIXER_BEHAVIOR_BIT_PERFECT","resample(","seekTo("))
            assertFalse(name,adapter.contains(name))
    }
    @Test fun directCapabilitiesAndUnsupportedCommandsAreHonest() {
        val adapter=source("java/io/github/rsgarrido/sazanami/player/DirectUsbMediaPlayer.kt")
        val commands=source("java/io/github/rsgarrido/sazanami/player/IntegratedDirectPlaybackState.kt").substringAfter("internal val integratedDirectCommands")
        for(name in listOf("COMMAND_PREPARE","COMMAND_STOP","COMMAND_PLAY_PAUSE","COMMAND_GET_METADATA","COMMAND_GET_TIMELINE")) assertTrue(commands.contains(name))
        for(name in listOf("COMMAND_SEEK_TO_NEXT_MEDIA_ITEM","COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM","COMMAND_SEEK_TO_MEDIA_ITEM","COMMAND_SET_REPEAT","COMMAND_SET_SHUFFLE","COMMAND_SET_VOLUME","COMMAND_SET_SPEED")) assertFalse(commands.contains(name))
        assertTrue(adapter.contains("PositionSupplier { evidence.positionMs }"));assertTrue(adapter.contains("unsupported(\"Queue edit\")"))
        val app=source("java/io/github/rsgarrido/sazanami/player/PlaybackController.kt")
        assertTrue(app.contains("if (IntegratedDirectPlaybackBridge.directSelected) { isPlaying=false;currentPosition=duration } else handleSongCompleted()"))
        assertTrue(app.contains("isPlaying = !IntegratedDirectPlaybackBridge.directSelected"))
        for(name in listOf("Next","Previous","Shuffle")) assertTrue(app.contains("reject(\"$name\")"))
        assertTrue(app.contains("IntegratedDirectPlaybackBridge.seek(position.toLong())"));assertTrue(adapter.contains("handleSeek(mediaItemIndex"))
        assertTrue(adapter.contains("commands.remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)"))
        assertTrue(app.contains("IntegratedDirectPlaybackBridge.togglePlayPause()"));assertTrue(app.contains("IntegratedDirectPlaybackBridge.pause()"))
    }
    @Test fun progressAndShutdownStayServiceOwnedAndBounded() {
        val transport=source("java/io/github/rsgarrido/sazanami/player/usb/UsbNativeSilentTransport.kt")
        assertTrue(transport.contains("nextProgressAt=now+250L"));assertTrue(transport.contains("authority==null"))
        val native=source("cpp/usb_iso_transport.cpp")
        assertTrue(native.contains("std::atomic<uint64_t> completedProgress"))
        val progress=native.substringAfter("UsbFsDiagnostics_nativeFullProgress(").substringBefore("UsbFsDiagnostics_nativeStopSilentTransport(")
        assertTrue(progress.contains("lock(session->controlMutex)"));assertTrue(progress.contains("session->livePlayback[0]"));assertTrue(progress.contains("session->livePlayback[1]"))
        assertFalse(progress.contains("session->stats[")) // No mutable native telemetry is read off-worker.
        val service=source("java/io/github/rsgarrido/sazanami/player/PlaybackService.kt")
        assertTrue(service.contains("shutdownJob=serviceScope.launch"));assertTrue(service.indexOf("usbAudioController?.awaitIdle()")<service.indexOf("serviceScope.cancel()"))
        assertTrue(service.contains("previousDirectTeardown.awaitSafe()"))
        assertTrue(source("java/io/github/rsgarrido/sazanami/ui/settings/IntegratedDirectPlaybackSection.kt").contains("if(!BuildConfig.DEBUG) return"))
    }
    @Test fun reportHasAuthorityAndFinalEngineEvidenceWithoutPrivateSources() {
        val state=integratedTerminalState(IntegratedDirectPlaybackState(enabled=true,libraryId=42,normalReleased=true),fullResult())
        val report=formatIntegratedDirectPlayback(state)
        for(text in listOf("authoritative backend=DIRECT_USB","standby/preload inactive=true","integrated state=ENDED","FULL_FLAC_USB_TRANSPORT_VERIFIED","NATURAL_EOF_COMPLETE","hash agreement=true","restoration=RESTORED")) assertTrue(text,report.contains(text))
        assertFalse(report.contains("content://"));assertFalse(report.contains("file://"));assertTrue(report.lines().size<70)
    }
}
