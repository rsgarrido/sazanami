package io.github.rsgarrido.sazanami.player

/** Only six exact, read-only diagnostic hooks are removed for historical algorithm pins.
 * The original digest constants remain; any surrounding policy/accounting change still fails them.
 * UsbFeedbackDiagnosticsTest and the standalone native suite separately protect the appended evidence. */
internal fun feedbackDiagnosticSourceEquivalent(text: String): String {
    var result=text.replace("\r\n","\n")
    for(line in listOf(
        "        if(prefix && !slot.feedback && frameCount && !diagnosticOutResumed) diagnosticOutResumed=slot.submittedAt;",
        "            observeFeedback(slot,urbStatus,monotonicUs());",
        "        observeFeedback(slot,urbStatus,observed);",
        "            if(!stopping && !stopCause.chosen) feedbackDiagnostics.rejected(invalidStreak);",
        "        feedbackDiagnostics.recovered();",
        "            if(slot.feedback) {const auto observed=monotonicUs();observeFeedback(slot,status,observed);freezeFeedback(FeedbackFatalReason::UrbCompletion,observed,true);}")) result=result.replace(line+"\n","")
    return result
}

/** Keep historical source pins for uninterrupted ordering/accounting. Normalize ONLY audited width/control hooks.
 * PCM24 packing/queue/byte behavior is separately exercised by the new semantic native/JVM tests.
 * Any change outside these explicit substitutions still fails the historical source digest. */
internal fun pcm16WidthSourceEquivalent(text: String): String = session17NoSeekSourceEquivalent(feedbackDiagnosticSourceEquivalent(text))
    // Conditional control branches are inactive for uninterrupted intent; the added publication only reports state.
    // PauseMachine/queue/transition tests protect these hooks. Other active algorithm changes still fail old hashes.
    .replace("        if(pauseAdmission()) return false;\n", "")
    .replace("        if(pauseAdmission()) {\n" +
        "            scheduler=schedulerBefore;stats[ScheduledFrames]=scheduledBefore;stats[BootstrapPackets]=bootstrapBefore;stats[FeedbackScheduledPackets]=feedbackBefore;\n" +
        "            if(prefix && !slot.feedback) prefix->queue.abandonReservation();\n" +
        "            return false;\n        }\n", "")
    .replace("            if(fullTrack) { playback.submitted();publishPlayback(); }\n", "")
    .replace("        if(fullTrack && playback.quiet()) {\n" +
        "            const auto& packet=slot.urb->iso_frame_desc[0];++stats[FeedbackPacketsCompleted];\n" +
        "            if(urbStatus || packet.status || packet.actual_length!=4) { ++stats[FeedbackUrbErrors];fail(UrbError,Stop::Feedback); }\n" +
        "            else {\n" +
        "                const auto decoded=decodeFeedback(slot.buffer.data(),packet.actual_length,0,sampleRateHz);\n" +
        "                if(decoded.validity==FeedbackValidity::Reserved) { ++stats[FeedbackInvalid];fail(MalformedFeedback,Stop::Feedback); }\n" +
        "                else if(decoded.validity!=FeedbackValidity::Valid) ++stats[FeedbackInvalid];\n" +
        "            }\n            return;\n        }\n", "")
    .replace("        if(fullTrack && playback.resumeReadiness) playback.resumeReadiness=2;\n", "")
    .replace("packetFits(frames, payload, frameBytes())", "packetFits(frames, payload)")
    .replace("frameBytes()", "kBytesPerFrame")
    .replace("packetFits(uint32_t frames, uint32_t payload, uint32_t bytesPerFrame = kBytesPerFrame)",
        "packetFits(uint32_t frames, uint32_t payload)")
    .replace("(bytesPerFrame==4 || bytesPerFrame==6) && static_cast<uint64_t>(frames) * bytesPerFrame <= payload",
        "static_cast<uint64_t>(frames) * kBytesPerFrame <= payload")
    .replace("            // FLAC examples retain width in the queue; snapshot reads them directly.",
        "            for (size_t i=0;i<source.exampleCount;++i) source.examples[i]=prefix->queue.examples[i];")
    .replace("!pcm.kBytesPerFrame || pcm.frames > kCounterMax / pcm.kBytesPerFrame || pcm.bytes != pcm.frames * pcm.kBytesPerFrame",
        "pcm.frames > kCounterMax / 4 || pcm.bytes != pcm.frames * 4")

/** Exact audited hooks only. At epoch zero, sourceHead=head=consumedTotal=submitted.
 * Added guards fail on finite-counter exhaustion; they cannot relax old success predicates.
 * Seek tests separately protect target proof, epoch rejection, segment hashes and these guards. */
internal fun session17NoSeekSourceEquivalent(text: String): String = text.replace("\r\n","\n")
    .replace("        std::unique_lock<std::mutex> eofBoundary(controlMutex);\n        if(command.seekSerial>activeSeekSerial || requestedStop.load()) return false;\n", "")
    .replace("commandsClosed=true;stopCause.choose(Stop::NaturalEof); return true;", "stopCause.choose(Stop::NaturalEof); return true;")
    .replace("        if(stats[SeekConfirmed]) return seekNaturalComplete();\n", "")
    .replace("slot.firstSourceFrame=fullTrack?prefix->queue.sourceHead():static_cast<uint64_t>(stats[SubmittedFrames]);", "slot.firstSourceFrame=static_cast<uint64_t>(stats[SubmittedFrames]);")
    .replace("        slot.sourceEpoch=fullTrack?prefix->queue.sourceEpoch.load():0;\n", "")
    .replace("position=prefix->queue.sourceHead();", "position=static_cast<uint64_t>(stats[SubmittedFrames]);")
    .replace("prefix->queue.consumedTotal.load()", "prefix->queue.head.load()")
    .replace("                if(fullTrack) segments.submittedHash=segments.epoch?fv::hashBytes(segments.submittedHash,slot.buffer.data(),length):submittedPrefixHash;\n", "")
    .replace("                if(slot.terminal && stats[SeekConfirmed]) {stats[SeekTailFrames]+=frameCount;stats[SeekTailPackets]+=slot.packets;++stats[SeekTailUrbs];}\n", "")
    .replace("                if(fullTrack && slot.terminal && stats[SeekConfirmed]) {++stats[SeekTailPacketCompletions];stats[SeekTailFrameCompletions]+=slot.frames[i];}\n", "")
    .replace("                stats[SeekOriginalNext]=prefix->decoder.originalNext;stats[SeekAttempts]=prefix->decoder.seekAttempts;\n", "")
    .replace("        if(fullTrack && !slot.feedback && slot.sourceEpoch!=segments.epoch) {fail(GenerationMismatch,Stop::Accounting);return true;}\n", "")
    .replace("                if(fullTrack && !fv::canAdd(stats[ScheduledFrames],frames)) {fail(TerminalAccounting,Stop::Accounting);return false;}\n", "")
    .replace("        if(fullTrack && !slot.feedback && (!fv::canAdd(stats[SubmittedFrames],frameCount) || !fv::canAdd(stats[OutBytesSubmitted],length))) {\n" +
        "            fail(TerminalAccounting,Stop::Accounting);return false;\n        }\n", "")
    .replace("            if(fullTrack && (!fv::canAdd(stats[OutPacketsCompleted],1) || !fv::canAdd(stats[KernelOutActualBytes],packet.actual_length))) {\n" +
        "                fail(TerminalAccounting,Stop::Accounting);continue;\n            }\n", "")
    .replace("                if(fullTrack && !fv::canAdd(stats[CompletedFrames],slot.frames[i])) {fail(TerminalAccounting,Stop::Accounting);continue;}\n", "")
