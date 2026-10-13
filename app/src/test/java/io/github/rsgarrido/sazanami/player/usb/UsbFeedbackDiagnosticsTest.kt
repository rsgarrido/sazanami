package io.github.rsgarrido.sazanami.player.usb

import io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackState
import io.github.rsgarrido.sazanami.player.formatIntegratedDirectPlayback
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Immutable numeric fixtures and production decoder/formatter. No worker, USB or hardware execution. */
class UsbFeedbackDiagnosticsTest {
    private fun source(path: String)=listOf(Paths.get("src/main",path),Paths.get("app/src/main",path)).first {Files.isRegularFile(it)}.toFile().readText()
    private fun row(seq: Long,raw: Long,at: Long=seq*1000,reason: Long=if(raw==0L) 2L else if(raw>379330L) 4L else 0L)=
        listOf(seq,at,raw,when(reason) {0L->0L;2L->2L;else->4L},reason,4L,4L,0L,0L,if(reason==0L) 1L else 0L,1000L,1L,6L,9L,0L)
    private fun words(rows: List<List<Long>> = emptyList(),fatal: Boolean=false): LongArray {
        val w=LongArray(UsbFeedbackDiagnostics.WORDS);w[28]=rows.size.toLong()
        rows.flatten().forEachIndexed {i,n->w[32+i]=n}
        if(fatal) {
            val last=rows.last();val first=rows[rows.size-3]
            val f=listOf(1L,1L,last[0],first[0],last[2],last[3],3L,44100L,1L,4L,6L,9L,last[1]-2000,last[1]-3000,
                last[1],1000L,21L,20L,0L,1L,21000L,12150L,last[1]-2900,1000L,1500L,2000L,2900L,3000L,
                rows.size.toLong(),first[1],379288L,0L)
            f.forEachIndexed {i,n->w[i]=n}
        }
        return w
    }
    private fun causal()=words(listOf(row(10,361264),row(11,379288),row(12,379616),row(13,380000),row(14,381000)),true)
    private fun packet(tail: LongArray=LongArray(272)): UsbIsoNativeSnapshot {
        val c=mapOf("version" to UsbIsoNativeStats.VERSION.toLong(),"generation" to 1L,"streamGeneration" to 2L,
            "state" to 7L,"stopReason" to (if(tail.getOrNull(1)==7L) 9L else 6L),
            "integrityMask" to (if(tail.getOrNull(0)==1L) when(tail[1]) {1L->1L shl 6;2L->1L shl 5;3L,7L->1L shl 2;else->1L shl 7} else 0L),
            "schedulerNominalRateHz" to 44100L,"formatChannels" to 2L,"formatBitsPerSample" to 16L)
        return UsbIsoNativeSnapshot(UsbIsoNativeStats.historicalFields.map {c[it] ?: 0L}.toLongArray()+tail,longArrayOf(),longArrayOf())
    }
    private fun rejected(w: LongArray) {try {UsbIsoNativeStats.decode(packet(w),1,2);fail("Malformed diagnostics accepted")} catch(_: IllegalArgumentException) {}}
    @Test fun completeFrozenRecordRetainsThirdNotShutdownEleventhAndSurvivesTerminalCopy() {
        val w=causal();val snapshot=packet(w);snapshot.numbers[42]=11L
        val result=UsbIsoNativeStats.decode(snapshot,1,2)
        val d=result.feedbackDiagnostics!!
        assertTrue(d.exists);assertEquals(3L,d.fatal[6]);assertEquals(11L,result.count("invalidStreak"))
        assertEquals(listOf(379616L,380000L,381000L),d.history.takeLast(3).map {it[2]})
        assertEquals(12L,d.fatal[3]);assertEquals(14L,d.fatal[2]);assertEquals(6L,d.fatal[10]);assertEquals(9L,d.fatal[11])
        assertEquals(12000L,d.fatal[12]);assertEquals(11000L,d.fatal[13]);assertEquals(1000L,d.fatal[15])
        snapshot.numbers.fill(0L) // The decoded terminal evidence owns copies, not the mutable JNI arrays.
        assertEquals(381000L,result.copy().feedbackDiagnostics!!.fatal[4]);assertEquals(5,d.history.size)
        val engine=UsbSilentTransportExperimentResult(1,2,UsbSilentTransportResolution(),transport=result)
        val report=formatIntegratedDirectPlayback(IntegratedDirectPlaybackState(engine=engine),Locale.ROOT)
        assertTrue(report.contains("streak at decision=3"));assertTrue(report.contains("invalidStreak=11"))
        assertTrue(report.contains("PERSISTENT_INVALID_FEEDBACK"));assertFalse(result.verified)
        val inconsistent=packet(w).also {it.numbers[5]=0L}
        try {UsbIsoNativeStats.decode(inconsistent,1,2);fail("Fatal record without failed integrity accepted")} catch(_: IllegalArgumentException) {}
    }
    @Test fun validOnlyRecoveredIntermittentGradualAndAbruptHistoriesKeepOrderingAndAcceptance() {
        for(raws in listOf(listOf(361264L,361280L,361264L),listOf(361264L,379616L,361264L),
            listOf(379616L,361264L,379616L,361264L),listOf(361264L,365000L,370000L,375000L,379288L))) {
            val rows=raws.mapIndexed {i,raw->row(i+1L,raw)}
            val d=UsbIsoNativeStats.decode(packet(words(rows)),1,2).feedbackDiagnostics!!
            assertFalse(d.exists);assertEquals(raws,d.history.map {it[2]})
            assertEquals(raws.map {if(it>379330) 0L else 1L},d.history.map {it[9]})
        }
        val wrapped=(25L..40L).map {row(it,361264)}
        val d=UsbIsoNativeStats.decode(packet(words(wrapped)),1,2).feedbackDiagnostics!!
        assertEquals(16,d.history.size);assertEquals(25L,d.history.first()[0]);assertEquals(40L,d.history.last()[0])
        val abrupt=listOf(row(1,361264),row(2,420000),row(3,420000),row(4,420000))
        assertEquals(420000L,UsbIsoNativeStats.decode(packet(words(abrupt,true)),1,2).feedbackDiagnostics!!.fatal[4])
    }
    @Test fun absentRecordIsConciseAndBilingualWithoutAnInventedTimeline() {
        val d=UsbIsoNativeStats.decode(packet(),1,2).feedbackDiagnostics!!
        assertFalse(d.exists);assertTrue(d.history.isEmpty())
        val en=formatFeedbackDiagnostics(d,Locale.ROOT);val es=formatFeedbackDiagnostics(d,Locale("es"))
        assertTrue(en.contains("NONE"));assertTrue(es.contains("NINGUNA"));assertEquals(1,en.trim().lines().size)
        assertFalse(en.contains("seq |"));assertFalse(es.contains("seq |"))
        assertTrue(formatFeedbackDiagnostics(null,Locale.ROOT).contains("UNAVAILABLE"))
        val fatal=UsbIsoNativeStats.decode(packet(causal()),1,2).feedbackDiagnostics!!
        for(locale in listOf(Locale.ROOT,Locale("es"))) {
            val report=formatFeedbackDiagnostics(fatal,locale)
            assertTrue(report.contains("IMPLAUSIBLE"));assertTrue(report.contains("accepted"));assertTrue(report.contains("379288"))
            assertTrue(report.contains("46299.80469"));assertTrue(report.contains("46339.84375"));assertTrue(report.contains("windowHz=41895.0..46305.0"))
            assertTrue(report.lines().size<=27);assertFalse(report.contains("content://"));assertFalse(report.contains("/storage/"))
        }
        val engine=UsbSilentTransportExperimentResult(1,2,UsbSilentTransportResolution(),transport=UsbIsoNativeStats.decode(packet(causal()),1,2))
        assertTrue(formatIntegratedDirectPlayback(IntegratedDirectPlaybackState(engine=engine),Locale("es")).contains("Primera decisión fatal"))
        assertTrue(source("java/io/github/rsgarrido/sazanami/ui/settings/IntegratedDirectPlaybackSection.kt").contains("resources.configuration.locales[0]"))
        val screen=source("java/io/github/rsgarrido/sazanami/ui/settings/DiagnosticsScreen.kt")
        assertTrue(screen.contains("val configuration = LocalConfiguration.current"))
        assertTrue(screen.contains("val reportLocale = configuration.locales[0]"))
        assertTrue(screen.contains("IntegratedDirectPlaybackBridge.reportState,reportLocale)"))
        assertFalse(screen.contains("context.resources.configuration.locales[0]"))
    }
    @Test fun strictLengthsVersionsCountsCodesArithmeticAndUnusedRowsAreValidated() {
        val p=packet(causal())
        for(bad in listOf(p.copy(numbers=p.numbers.copyOf(236)),p.copy(numbers=p.numbers.copyOf(507)),
            p.copy(numbers=p.numbers.copyOf(509)),p.copy(numbers=p.numbers.copyOf().also {it[0]=12}))) {
            try {UsbIsoNativeStats.decode(bad,1,2);fail("Old/truncated/oversized snapshot accepted")} catch(_: IllegalArgumentException) {}
        }
        for((i,value) in listOf(0 to 2L,1 to 8L,6 to 11L,7 to 48000L,8 to 12L,12 to Long.MAX_VALUE,14 to -2L,
            15 to Long.MAX_VALUE,16 to -1L,17 to 162L,18 to 2L,21 to 21001L,25 to 14001L,28 to -1L,28 to 17L,
            32 to 0L,35 to 7L,36 to 9L,39 to Long.MAX_VALUE,41 to 2L,42 to -2L)) rejected(causal().also {it[i]=value})
        rejected(causal().also {it[32+15]=10L}) // Duplicate/out-of-order sequence.
        rejected(causal().also {it[32+15+1]=0L}) // Reversed host time.
        rejected(causal().also {it[32+5*15]=1L}) // Nonzero unused row, not silently ignored.
        rejected(words().also {it[4]=379616}) // Absent record cannot carry a fabricated fatal raw value.
    }
    @Test fun noSeekBootstrapTimeoutQuietAndMalformedSamplesRepresentUnavailableFacts() {
        val timeout=LongArray(272);timeout[0]=1;timeout[1]=4;timeout[6]=0;timeout[7]=44100;timeout[8]=0;timeout[9]=3
        for(i in listOf(2,3,4,5,10,11,12,13,15,23,24,25,27,29)) timeout[i]=-1
        timeout[14]=100000;timeout[16]=21;timeout[17]=21;timeout[18]=1;timeout[19]=1;timeout[20]=21000
        timeout[22]=100000;timeout[26]=0;timeout[31]=1
        // Non-source diagnostic sessions use phase/epoch/serial=-1; full source initial epoch is zero.
        timeout[10]=0;timeout[11]=0
        val d=UsbIsoNativeStats.decode(packet(timeout),1,2).feedbackDiagnostics!!
        assertEquals(-1L,d.fatal[12]);assertEquals(-1L,d.fatal[13]);assertTrue(d.history.isEmpty());assertEquals(1L,d.fatal[18])
        for(case in listOf(listOf(1L,1L,-1L,0L,0L,0L),
            listOf(5L,6L,361264L,4L,-18L,0L),listOf(5L,5L,361264L,4L,0L,1L),listOf(3L,3L,0xf0000001L,4L,0L,0L),
            listOf(0L,7L,361264L,4L,0L,0L),listOf(0L,8L,361264L,4L,0L,0L),listOf(2L,2L,0L,4L,0L,0L))) {
            val validity=case[0]
            val reason=case[1]
            val raw=case[2]
            val actualLength=case[3]
            val packetStatus=case[4]
            val urbErrorCount=case[5]
            val r=row(1,361264).toMutableList();r[2]=raw;r[3]=validity;r[4]=reason;r[6]=actualLength;r[9]=0;r[8]=packetStatus;r[14]=urbErrorCount
            if(reason==7L) r[11]=2
            assertEquals(reason,UsbIsoNativeStats.decode(packet(words(listOf(r))),1,2).feedbackDiagnostics!!.history.single()[4])
        }
    }
    @Test fun nativeContractKeepsHistoricalIndicesAndProgressAndFreezesBeforeFailureMutation() {
        assertEquals(13,UsbIsoNativeStats.VERSION);assertEquals(508,UsbIsoNativeStats.fields.size)
        assertEquals(236,UsbIsoNativeStats.historicalFields.size);assertEquals(32,UsbFeedbackDiagnostics.fatalFields.size)
        assertEquals(15,UsbFeedbackDiagnostics.rowFields.size);assertEquals(272,UsbFeedbackDiagnostics.fields.size)
        assertEquals("seekStartedPaused",UsbIsoNativeStats.fields[235]);assertEquals("feedbackFatal_exists",UsbIsoNativeStats.fields[236])
        assertEquals("feedbackHistory15_urbErrorCount",UsbIsoNativeStats.fields.last())
        assertEquals(58,4+UsbIsoNativeStats.controlFields.size+UsbIsoNativeStats.seekFields.size)
        assertEquals(16,UsbFsEvidence.NATIVE_API_VERSION);assertEquals(53,UsbFlacSourceEvidence.fields.size)
        val native=source("cpp/usb_iso_transport.cpp");val h=source("cpp/usb_feedback_diagnostics.h")
        assertTrue(native.contains("kSnapshotVersion = 13"));assertTrue(native.contains("FieldCount==236 && kSnapshotFieldCount==508"))
        assertTrue(native.contains("env->SetLongArrayRegion(numbers,FieldCount,kFeedbackDiagnosticWords"))
        val failure=native.substringAfter("void fail(").substringBefore("void failSource()")
        assertTrue(failure.indexOf("freezeFeedback(")<failure.indexOf("playbackFault=true"))
        assertTrue(native.contains("if(feedbackDiagnostics.frozen() || stopping || stopCause.chosen) return"))
        assertTrue(native.indexOf("auto result = snapshot(env, *session)")<native.indexOf("session->prefix.reset()"))
        val progress=native.substringAfter("UsbFsDiagnostics_nativeFullProgress(").substringBefore("extern \"C\"")
        assertFalse(progress.contains("feedbackDiagnostics"))
        for(forbidden in listOf("vector<","malloc(","new ","sleep","mutex","ioctl","printf","logcat")) assertFalse(forbidden,h.contains(forbidden))
        assertTrue(h.contains("if(frozen()) return"));assertTrue(h.contains("kFeedbackHistoryCapacity=16"))
        fun nativeFields(name: String)=h.substringAfter("enum $name : size_t {").substringBefore("};").split(',').map {it.trim()}
        assertEquals(listOf("DSequence","DElapsed","DRaw","DValidity","DRejection","DRequested","DActual","DUrbStatus",
            "DPacketStatus","DAccepted","DRequestAge","DPhase","DEpoch","DSeekSerial","DUrbErrors"),nativeFields("FeedbackObservationField"))
        assertEquals(listOf("FExists","FReason","FSequence","FFirstRejectedSequence","FRaw","FValidity","FStreak","FNominalRate",
            "FPhase","FTransportState","FEpoch","FSeekSerial","FSinceSeek","FSinceOut","FObserved","FRequestAge","FOut","FFeedback",
            "FBootstrap","FRefill","FNominalCoverage","FEstimatedCoverage","FBootstrapElapsed","FAdmissionStopped","FPoolsDrained",
            "FSeekConfirmed","FFreshPrime","FOutResumed","FHistoryCount","FFirstRejectedElapsed","FAcceptedRaw","FTimeoutKind"),nativeFields("FeedbackFatalField"))
        assertEquals(listOf("sequence","elapsedUs","raw","validity","rejection","requestedLength","actualLength","urbStatus",
            "packetStatus","accepted","requestAgeUs","phase","sourceEpoch","confirmedSeekSerial","urbErrorCount"),UsbFeedbackDiagnostics.rowFields)
        assertEquals(listOf("exists","reason","sequence","firstRejectedSequence","raw","validity","streak","nominalRateHz","phase",
            "transportState","sourceEpoch","confirmedSeekSerial","sinceSeekUs","sinceOutUs","observedElapsedUs","requestAgeUs",
            "outOutstanding","feedbackOutstanding","bootstrapActive","refillDemand","nominalCoverageUs","estimatedCoverageUs","bootstrapElapsedUs",
            "admissionStoppedElapsedUs","poolsDrainedElapsedUs","seekConfirmedElapsedUs","freshPrimeElapsedUs","outResumedElapsedUs",
            "historyCount","firstRejectedElapsedUs","heldAcceptedRaw","timeoutKind"),UsbFeedbackDiagnostics.fatalFields)
        assertTrue(source("cpp/usb_iso_core.h").contains("kInvalidFeedbackLimit = 3, kPlausibilityPercent = 5"))
    }
}
