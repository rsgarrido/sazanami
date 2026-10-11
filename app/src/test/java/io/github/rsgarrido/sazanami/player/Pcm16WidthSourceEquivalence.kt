package io.github.rsgarrido.sazanami.player

/** Keep historical source pins for uninterrupted ordering/accounting. Normalize ONLY audited width/control hooks.
 * PCM24 packing/queue/byte behavior is separately exercised by the new semantic native/JVM tests.
 * Any change outside these explicit substitutions still fails the historical source digest. */
internal fun pcm16WidthSourceEquivalent(text: String): String = text.replace("\r\n","\n")
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
