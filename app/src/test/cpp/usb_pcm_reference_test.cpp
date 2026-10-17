// Standalone pure-helper coverage. Created for human execution; not run by Codex.
#include "../../main/cpp/usb_pcm_reference.h"
#include <cassert>
#include <cmath>
#include <type_traits>
#include <vector>
#include <algorithm>
using namespace sazanami::usbiso;
static_assert(std::is_const_v<std::remove_reference_t<decltype(kReferenceTone)>>);
static_assert(kReferenceTone.size() == 100 && kRate == 441 * 100);
static_assert(kOutPool == 8 && kOutPackets == 8 && kFeedbackPool == 2);

static void tableAndPreflight() {
    // Independent mathematical oracle belongs only to host tests, never the native transport.
    uint64_t independentlyHashed = 14695981039346656037ULL;
    for (size_t i = 0; i < 100; ++i) {
        const int expected = static_cast<int>(std::lround(1024.0 * std::sin(2.0 * 3.14159265358979323846 * i / 100.0)));
        assert(kReferenceTone[i] == expected && expected >= -32768 && expected <= 32767);
        for (unsigned byte : {static_cast<unsigned>(expected) & 255U, (static_cast<unsigned>(expected) >> 8) & 255U})
            independentlyHashed = (independentlyHashed ^ byte) * 1099511628211ULL;
    }
    assert(independentlyHashed == 0xbbd1384f8c503ee1ULL);
    assert(tableHash() == independentlyHashed);
    const ReferencePreflight p;
    assert(p.pass() && p.maximum == 1024 && p.minimum == -1024 && p.peak == 1024);
    assert(p.packing && p.channels && p.deterministic);
    assert(activePerCycle() == 98); // Independent oracle: two zero crossings.
}
static void packing() {
    const int16_t samples[]{0,1,-1,1024,-1024,32767,-32768};
    const uint8_t expected[][2]{{0,0},{1,0},{255,255},{0,4},{0,252},{255,127},{0,128}};
    for (size_t i = 0; i < 7; ++i) {
        uint8_t bytes[2]; pack16(bytes, samples[i]);
        assert(bytes[0] == expected[i][0] && bytes[1] == expected[i][1]);
        assert(unpack16(bytes) == samples[i]);
    }
    uint8_t stereo[4]; packFrame(stereo, {1024,-1024});
    assert(stereo[0] == 0 && stereo[1] == 4 && stereo[2] == 0 && stereo[3] == 252);
}
static void exactWindows() {
    size_t left = 0, right = 0, leftActive = 0, rightActive = 0;
    for (uint64_t i = 0; i < 50000; ++i) {
        const auto frame = referenceFrame(i);
        if (i >= 4410 && i <= 13209) {
            ++left; if (frame.left) ++leftActive;
            assert(frame.left == kReferenceTone[(i-4410)%100] && frame.right == 0);
        } else if (i >= 17620 && i <= 26419) {
            ++right; if (frame.right) ++rightActive;
            assert(frame.left == 0 && frame.right == kReferenceTone[(i-17620)%100]);
        } else assert(frame.left == 0 && frame.right == 0);
    }
    assert(left == 8800 && right == 8800 && left/100 == 88 && right/100 == 88);
    assert(leftActive == 88*98 && rightActive == 88*98);
    assert(referenceFrame(4410).left == 0 && referenceFrame(4411).left == 64);
    assert(referenceFrame(13209).left == -64 && referenceFrame(13210).left == 0);
    assert(referenceFrame(17620).right == 0 && referenceFrame(17621).right == 64);
    assert(referenceFrame(26419).right == -64 && referenceFrame(26420).right == 0);
}
static void orderedPacketization() {
    PcmTestSource whole(PcmMode::Reference), divided(PcmMode::Reference);
    std::vector<uint8_t> all(44100*4), chunks;
    whole.fill(all.data(), 0, 44100);
    assert(whole.inspect(all.data(), all.size(), 0, 44100));
    whole.submitted(all.data(), all.size());
    const uint32_t sizes[]{5,6,22,21,23,1,7,8};
    uint64_t cursor = 0;
    for (unsigned packet = 0; cursor < 44100; ++packet) {
        const uint32_t frames = std::min<uint32_t>(sizes[packet % 8], 44100 - cursor);
        std::array<uint8_t, 100> bytes; bytes.fill(0xa5);
        divided.fill(bytes.data(), cursor, frames);
        assert(divided.inspect(bytes.data(), frames*4, cursor, frames));
        divided.submitted(bytes.data(), frames*4);
        assert(bytes[frames*4] == 0xa5); // Unused bytes stay untouched and unhashed.
        chunks.insert(chunks.end(), bytes.begin(), bytes.begin()+frames*4);
        cursor += frames;
    }
    assert(all == chunks && whole.sourceHash == divided.sourceHash);
    assert(whole.sourceHash == whole.submittedHash && divided.sourceHash == divided.submittedHash);
    assert(whole.sourceHash == 0xe397ae906a35c025ULL);
    assert(divided.generated == 44100 && divided.leftFrames == 8800 && divided.rightFrames == 8800);
    assert(divided.silence == 44100 - 17600 && divided.leftActive == 88*activePerCycle());
    assert(divided.rightActive == 88*activePerCycle() && divided.peak == 1024);
    assert(divided.maximum == 1024 && divided.minimum == -1024);
    assert(divided.examples[2][0] == 4411 && divided.examples[2][1] == 64 && divided.examples[2][2] == 0);
    assert(divided.exampleCount == 13);
}
static void corruptedPayload() {
    std::array<uint8_t, 8> bytes{};
    PcmTestSource mutation(PcmMode::Reference); mutation.fill(bytes.data(), 4411, 2);
    bytes[0] ^= 1;
    assert(!mutation.inspect(bytes.data(), 8, 4411, 2));
    assert(mutation.reasons & SourceHashMismatch);
    assert(mutation.submittedHash == kFnvOffset && mutation.generated == 2);
    // Restoring bytes cannot restore sticky validity.
    packFrame(bytes.data(), referenceFrame(4411));
    assert(!mutation.inspect(bytes.data(), 8, 4411, 2));
    PcmTestSource reordered(PcmMode::Reference); reordered.fill(bytes.data(), 4411, 2);
    for (size_t i = 0; i < 4; ++i) std::swap(bytes[i], bytes[4+i]);
    assert(!reordered.inspect(bytes.data(), 8, 4411, 2) && (reordered.reasons & SourceHashMismatch));
    PcmTestSource inactive(PcmMode::Reference); inactive.fill(bytes.data(), 4411, 1); pack16(bytes.data()+2, 64);
    assert(!inactive.inspect(bytes.data(), 4, 4411, 1) && inactive.inactive == 1 && inactive.unexpected == 1);
    PcmTestSource outside(PcmMode::Reference); outside.fill(bytes.data(), 0, 1); pack16(bytes.data(), 1);
    assert(!outside.inspect(bytes.data(), 4, 0, 1) && outside.outside == 1);
    PcmTestSource peak(PcmMode::Reference); peak.fill(bytes.data(), 4411, 1); pack16(bytes.data(), 1025);
    assert(!peak.inspect(bytes.data(), 4, 4411, 1) && (peak.reasons & ReferencePeakViolation));
    PcmTestSource length(PcmMode::Reference); length.fill(bytes.data(), 0, 1);
    assert(!length.inspect(bytes.data(), 3, 0, 1) && (length.reasons & PcmPackingMismatch));
}
static void silenceAndFailure() {
    PcmTestSource zero(PcmMode::Zero); std::array<uint8_t, 32> bytes; bytes.fill(255);
    zero.fill(bytes.data(), 0, 6); assert(zero.inspect(bytes.data(), 24, 0, 6));
    assert(zero.generated == 6 && bytes[24] == 255);
    bytes[0] = 1; assert(!zero.inspect(bytes.data(), 24, 0, 6));
    zero.fill(bytes.data(), 6, 6); assert(!zero.inspect(bytes.data(), 24, 6, 6));
    PcmTestSource partial(PcmMode::Reference); partial.fill(bytes.data(), 4411, 6);
    assert(partial.inspect(bytes.data(), 24, 4411, 6));
    assert(partial.generated == 6 && partial.sourceHash != partial.submittedHash);
    // Failed ioctl does not call submitted(): submitted cursor/hash remain unchanged.
    assert(partial.submittedHash == kFnvOffset);
}
static void feedbackOnlyChangesPacketization() {
    Scheduler scheduler; PcmTestSource source(PcmMode::Reference); uint64_t cursor = 0;
    std::vector<uint8_t> packets;
    for (unsigned packet = 0; packet < 8000; ++packet) {
        uint32_t frames = 0;
        assert(scheduler.next(1, packet < 8 ? 0 : (packet % 2 ? 360000 : 362000), frames));
        assert(packetFits(frames, 776)); std::array<uint8_t, 24> bytes{};
        source.fill(bytes.data(), cursor, frames); assert(source.inspect(bytes.data(), frames*4, cursor, frames));
        source.submitted(bytes.data(), frames*4);
        packets.insert(packets.end(), bytes.begin(), bytes.begin()+frames*4); cursor += frames;
    }
    PcmTestSource canonical(PcmMode::Reference); std::vector<uint8_t> expected(cursor*4);
    canonical.fill(expected.data(), 0, static_cast<uint32_t>(cursor));
    assert(packets == expected && cursor == scheduler.frames && cursor == source.generated);
    assert(source.sourceHash == canonical.sourceHash && source.sourceHash == source.submittedHash);
}
int main() { tableAndPreflight(); packing(); exactWindows(); orderedPacketization(); corruptedPayload(); silenceAndFailure(); feedbackOnlyChangesPacketization(); }
