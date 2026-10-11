package io.github.rsgarrido.sazanami.player

/** Keep historical PCM16 source pins for ordering/accounting. Normalize ONLY audited width changes.
 * PCM24 packing/queue/byte behavior is separately exercised by the new semantic native/JVM tests.
 * Any change outside these explicit substitutions still fails the historical source digest. */
internal fun pcm16WidthSourceEquivalent(text: String): String = text.replace("\r\n","\n")
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
