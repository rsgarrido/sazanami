package io.github.rsgarrido.sazanami.ui

import io.github.rsgarrido.sazanami.player.*
import io.github.rsgarrido.sazanami.player.usb.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Layout/ownership source boundaries and immutable report retention. No Android or USB execution. */
class IntegratedDirectDiagnosticAccessTest {
    private fun source(path: String)=listOf(Paths.get("src/main/java/io/github/rsgarrido/sazanami",path),
        Paths.get("app/src/main/java/io/github/rsgarrido/sazanami",path)).first { Files.isRegularFile(it) }.toFile().readText()

    @Test fun statusIsInsertedBesideAnUnconditionalNavigationHostForAllPhases() {
        val root=source("ui/MusicScreen.kt").substringAfter("val directStatusVisible =")
            .substringBefore("internal fun dismissExpandedPlayerPresentation")
        val presentation=source("ui/MusicPlayerPresentation.kt")
        val banner=source("ui/settings/IntegratedDirectPlaybackSection.kt").substringAfter("internal fun IntegratedDirectPlaybackBanner(")
            .substringBefore("private suspend fun Context.copyIntegratedDirectReport")
        assertTrue(root.contains("Column(modifier = modifier.fillMaxSize().appShellBackground())"))
        assertTrue(root.contains("Modifier.weight(1f).fillMaxWidth()"))
        assertEquals(1,Regex("PlayerMorphHost\\(").findAll(root).count())
        assertTrue(root.indexOf("IntegratedDirectPlaybackBanner(")<root.indexOf("PlayerMorphHost("))
        assertTrue(Regex("}\\s*BoxWithConstraints\\(").containsMatchIn(root))
        assertTrue(presentation.contains("bodyPresentation(bottomContentPadding)"))
        assertTrue(presentation.contains("bottomNavigationContent()"))
        assertFalse(presentation.contains("IntegratedDirectPlaybackBanner"))
        for (phase in IntegratedDirectPhase.entries) {
            assertFalse("Navigation must not branch on $phase",root.contains("IntegratedDirectPhase.$phase"))
            assertFalse("Banner must remain available for $phase",banner.contains("IntegratedDirectPhase.$phase"))
        }
        assertTrue(banner.contains("if(!state.enabled) return"))
        assertTrue(root.contains("BuildConfig.DEBUG"));assertTrue(banner.contains("if(!BuildConfig.DEBUG) return"))
    }

    @Test fun statusHasBoundedTextAndOnlyVisibleControlsConsumeInput() {
        val banner=source("ui/settings/IntegratedDirectPlaybackSection.kt").substringAfter("internal fun IntegratedDirectPlaybackBanner(")
            .substringBefore("private suspend fun Context.copyIntegratedDirectReport")
        assertTrue(banner.contains("heightIn(max=144.dp)"))
        assertTrue(banner.contains("maxLines=1,overflow=TextOverflow.Ellipsis"))
        assertTrue(banner.contains("Modifier.weight(1f)")) // action labels share width, not remaining zero width
        for (forbidden in listOf("fillMaxSize","fillMaxHeight","Surface(","Dialog(","pointerInput","zIndex","BackHandler","clickable("))
            assertFalse(forbidden,banner.contains(forbidden))
        assertTrue(banner.contains("state.error ?: state.notice ?: state.engine?.status?.name"))
        assertFalse(banner.contains("IntegratedDirectPlaybackBridge::start")) // no accidental retry from the strip
        assertFalse(banner.contains("IntegratedDirectPlaybackBridge.select"))
    }

    @Test fun settingsBackAndRouteChangesDoNotOwnPlayback() {
        for (file in listOf("ui/MusicScreen.kt","ui/MusicScreenBody.kt","ui/MusicBodyPresentation.kt")) {
            val text=source(file)
            assertFalse(text.contains("IntegratedDirectPlaybackBridge.stop"))
            assertFalse(text.contains("IntegratedDirectPlaybackBridge.select"))
        }
        val body=source("ui/MusicScreenBody.kt")
        assertTrue(body.contains("isSettingsScreenVisible ->"));assertTrue(body.contains("isDiagnosticsScreenVisible ->"))
        assertTrue(body.contains("DiagnosticsScreen("))
        assertTrue(source("ui/MusicScreen.kt").contains("BackHandler("))
        val service=source("player/PlaybackService.kt")
        assertTrue(service.contains("ExperimentalUsbPlaybackPlayer(normalSessionPlayer,serviceScope"))
        assertTrue(service.contains("physicalPlayers.releaseForDirect()"))
        val route=source("player/ExperimentalUsbPlaybackPlayer.kt")
        assertTrue(route.contains("selectedDirect && (!normalReleaseSafe || !selectionHandedOff"))
        assertTrue(route.indexOf("direct.stopAndJoin()")<route.indexOf("recreateNormal()"))
        assertTrue(source("player/DirectUsbMediaPlayer.kt").contains("usb.runIntegratedFull(song.uri,commands)"))
    }

    @Test fun failureEvidenceSurvivesStopModeDisableNormalRestoreAndBridgeDetach() {
        val evidence=IntegratedDirectAttemptEvidence()
        val result=UsbSilentTransportExperimentResult(1,2,UsbSilentTransportResolution(),status=UsbSilentStatus.FAILED)
        val failed=IntegratedDirectPlaybackState(enabled=true,phase=IntegratedDirectPhase.ERROR,generation=7,
            libraryId=88,completedFrames=628337,totalFrames=12353300,error="DIRECT_TRANSPORT_FAILED",engine=result)
        evidence.record(failed)
        evidence.record(failed.copy(phase=IntegratedDirectPhase.STOPPED))
        evidence.record(failed.copy(enabled=false,normalReleased=false))
        evidence.record(IntegratedDirectPlaybackState())
        evidence.record(IntegratedDirectPlaybackState(enabled=true,phase=IntegratedDirectPhase.ERROR,error="NORMAL_BACKEND_RELEASE_UNVERIFIED"))
        evidence.record(IntegratedDirectPlaybackState(enabled=true,generation=8,libraryId=99)) // selection alone is not a new run
        assertSame(failed,evidence.latest.value);assertSame(result,evidence.latest.value?.engine)
        assertTrue(formatIntegratedDirectPlayback(evidence.latest.value!!).contains("DIRECT_TRANSPORT_FAILED"))
        val bridge=source("player/IntegratedDirectPlaybackBridge.kt")
        assertTrue(bridge.contains("attempts.record(value)"));assertTrue(bridge.contains("latestAttempt.value ?: state.value"))
        val detach=bridge.substringAfter("fun detach(").substringBefore("fun publish(")
        assertFalse(detach.contains("attempts."));assertFalse(detach.contains("latestAttempt"))
    }

    @Test fun newAttemptIntentionallyReplacesOldEvidenceAndTracksCompletedFrames() {
        val evidence=IntegratedDirectAttemptEvidence()
        evidence.record(IntegratedDirectPlaybackState(enabled=true,phase=IntegratedDirectPhase.ERROR,generation=7,error="DIRECT_TRANSPORT_FAILED"))
        val preparing=IntegratedDirectPlaybackState(enabled=true,phase=IntegratedDirectPhase.PREPARING,generation=8,libraryId=88)
        evidence.record(preparing);assertSame(preparing,evidence.latest.value)
        val playing=preparing.copy(phase=IntegratedDirectPhase.PLAYING,completedFrames=44100,totalFrames=88200,sampleRateHz=44100)
        evidence.record(playing);assertEquals(1000L,evidence.latest.value?.positionMs)
        val ended=playing.copy(phase=IntegratedDirectPhase.ENDED,completedFrames=88200)
        evidence.record(ended);evidence.record(ended.copy(phase=IntegratedDirectPhase.STOPPED))
        assertSame(ended,evidence.latest.value)
    }

    @Test fun copyUsesTheExistingReportFormatterAndDoesNotChangePlayback() {
        val section=source("ui/settings/IntegratedDirectPlaybackSection.kt")
        val copy=section.substringAfter("private suspend fun Context.copyIntegratedDirectReport()")
        assertTrue(copy.contains("UsbTransportTelemetryBridge.capture()"))
        assertTrue(copy.indexOf("UsbTransportTelemetryBridge.capture()")<copy.indexOf("setPrimaryClip"))
        // The copy path delegates retained evidence plus an explicit locale, rather than pinning a one-argument signature.
        assertTrue("Copy must delegate retained evidence to the shared formatter",Regex(
            """formatIntegratedDirectPlayback\s*\(\s*IntegratedDirectPlaybackBridge\.reportState\s*,""").containsMatchIn(copy))
        assertTrue("The non-composable helper must read the current resource locale at copy time",copy.contains("resources.configuration.locales[0]"))
        assertTrue(copy.contains("setPrimaryClip"))
        assertTrue(copy.contains("ClipData.newPlainText"))
        assertFalse(section.contains("fun formatIntegratedDirectPlayback"))
        val screen=source("ui/settings/DiagnosticsScreen.kt")
        assertTrue(screen.contains("context.copyToClipboard("))
        val copyAction=screen.substringAfter("context.copyToClipboard(").substringBefore("},")
        assertTrue(copyAction.contains("IntegratedDirectPlaybackBridge.reportState"))
        assertTrue(copyAction.contains("reportLocale"));assertTrue(screen.contains("LocalConfiguration.current"))
        assertFalse(copyAction.contains("resources.configuration"))
        assertTrue(Regex("""if\s*\(\s*BuildConfig\.DEBUG\s*\)""").containsMatchIn(copyAction))
        for (command in listOf(".stop(",".start(",".select(",".prepare(",".release(",".runIntegratedFull(",
            ".pause(",".resume(",".seek(",".seekTo(",".play(",".requestStop(",".requestPlayback(")) {
            assertFalse(command,copy.contains(command));assertFalse(command,copyAction.contains(command))
        }
        assertTrue(source("player/IntegratedDirectPlaybackReport.kt").contains("internal fun formatIntegratedDirectPlayback("))
        // Exercise the production formatter's locale argument and immutable evidence, beyond source-expression matching.
        val transport=UsbIsoTransportResult(7,2)
        val result=UsbSilentTransportExperimentResult(7,2,UsbSilentTransportResolution(),status=UsbSilentStatus.FAILED,transport=transport)
        val state=IntegratedDirectPlaybackState(enabled=true,phase=IntegratedDirectPhase.ERROR,generation=7,
            libraryId=88,completedFrames=44100,totalFrames=88200,sampleRateHz=44100,error="DIRECT_TRANSPORT_FAILED",engine=result)
        val before=state.copy()
        for((locale,title) in listOf(Locale.ENGLISH to "First fatal feedback decision",Locale("es") to "Primera decisión fatal de feedback")) {
            val report=formatIntegratedDirectPlayback(state,locale)
            assertTrue(report.contains(title));assertTrue(report.contains("DIRECT_TRANSPORT_FAILED"))
            assertTrue(report.contains("DAC-internal bit-perfect output: NOT INDEPENDENTLY VERIFIED"))
            assertEquals(before,state);assertSame(result,state.engine);assertSame(transport,state.engine?.transport)
        }
    }
}
