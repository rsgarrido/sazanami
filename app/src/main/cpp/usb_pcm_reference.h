#pragma once
#include "usb_iso_core.h"
#include <initializer_list>

namespace sazanami::usbiso {
enum class PcmMode : int { Zero = 0, Reference = 1 };
// Offline rounded 1024*sin(2*pi*n/100). No runtime oscillator or gain.
inline constexpr std::array<int16_t, 100> kReferenceTone{
    0, 64, 128, 192, 255, 316, 377, 436, 493, 549,
    602, 653, 701, 746, 789, 828, 865, 897, 927, 952,
    974, 992, 1006, 1016, 1022, 1024, 1022, 1016, 1006, 992,
    974, 952, 927, 897, 865, 828, 789, 746, 701, 653,
    602, 549, 493, 436, 377, 316, 255, 192, 128, 64,
    0, -64, -128, -192, -255, -316, -377, -436, -493, -549,
    -602, -653, -701, -746, -789, -828, -865, -897, -927, -952,
    -974, -992, -1006, -1016, -1022, -1024, -1022, -1016, -1006, -992,
    -974, -952, -927, -897, -865, -828, -789, -746, -701, -653,
    -602, -549, -493, -436, -377, -316, -255, -192, -128, -64
};
constexpr uint64_t kFnvOffset = 14695981039346656037ULL, kFnvPrime = 1099511628211ULL;
constexpr uint64_t kReferenceHash = 0xbbd1384f8c503ee1ULL;
constexpr uint64_t kLeftStart = 4410, kLeftEnd = 13210, kRightStart = 17620, kRightEnd = 26420;
inline uint64_t hashBytes(uint64_t hash, const uint8_t* bytes, size_t count) noexcept {
    for (size_t i = 0; i < count; ++i) hash = (hash ^ bytes[i]) * kFnvPrime;
    return hash; // Unsigned overflow is defined modulo 2^64.
}
inline void pack16(uint8_t* bytes, int16_t sample) noexcept {
    const auto value = static_cast<uint16_t>(sample);
    bytes[0] = static_cast<uint8_t>(value); bytes[1] = static_cast<uint8_t>(value >> 8);
}
inline int16_t unpack16(const uint8_t* bytes) noexcept {
    const int32_t value = bytes[0] | (static_cast<uint32_t>(bytes[1]) << 8);
    return static_cast<int16_t>(value >= 32768 ? value - 65536 : value);
}
struct Stereo { int16_t left = 0, right = 0; };
inline Stereo referenceFrame(uint64_t index) noexcept {
    if (index >= kLeftStart && index < kLeftEnd) return {kReferenceTone[(index - kLeftStart) % kReferenceTone.size()], 0};
    if (index >= kRightStart && index < kRightEnd) return {0, kReferenceTone[(index - kRightStart) % kReferenceTone.size()]};
    return {};
}
inline void packFrame(uint8_t* bytes, Stereo frame) noexcept {
    pack16(bytes, frame.left); pack16(bytes + 2, frame.right);
}
inline uint64_t tableHash() noexcept {
    uint64_t hash = kFnvOffset; uint8_t bytes[2];
    for (auto sample : kReferenceTone) { pack16(bytes, sample); hash = hashBytes(hash, bytes, 2); }
    return hash;
}
inline unsigned activePerCycle() noexcept {
    unsigned count = 0; for (auto sample : kReferenceTone) if (sample != 0) ++count;
    return count;
}
inline uint64_t referenceProblem(uint64_t index, Stereo actual) noexcept {
    const auto expected = referenceFrame(index);
    uint64_t reasons = 0;
    const bool left = index >= kLeftStart && index < kLeftEnd, right = index >= kRightStart && index < kRightEnd;
    if ((left && actual.right != 0) || (right && actual.left != 0)) reasons |= InactiveChannelNonzero;
    if (!left && !right && (actual.left != 0 || actual.right != 0)) reasons |= NonzeroOutsideWindow;
    if (actual.left > 1024 || actual.left < -1024 || actual.right > 1024 || actual.right < -1024) reasons |= ReferencePeakViolation;
    if (actual.left != expected.left || actual.right != expected.right) reasons |= PcmPackingMismatch;
    return reasons;
}
struct ReferencePreflight {
    uint64_t reasons = 0, actualHash = tableHash();
    int maximum = -32768, minimum = 32767, peak = 0;
    bool packing = true, channels = true, deterministic = true;
    ReferencePreflight() noexcept {
        if (kReferenceTone.size() != 100 || kRate / kReferenceTone.size() != 441 ||
            kLeftEnd - kLeftStart != 8800 || kRightEnd - kRightStart != 8800 ||
            kLeftEnd + 4410 != kRightStart || kLeftStart != 4410 || kReferenceTone[0] != 0) reasons |= ReferencePreflightFailed;
        for (int value : kReferenceTone) {
            if (value > maximum) maximum = value;
            if (value < minimum) minimum = value;
            const int absolute = value < 0 ? -value : value;
            if (absolute > peak) peak = absolute;
        }
        if (maximum != 1024 || minimum != -1024 || peak != 1024) reasons |= ReferencePeakViolation;
        if (actualHash != kReferenceHash) reasons |= ReferenceTableHashMismatch;
        const int16_t values[]{0, 1, -1, 1024, -1024, 32767, -32768};
        const uint8_t expected[][2]{{0,0},{1,0},{255,255},{0,4},{0,252},{255,127},{0,128}};
        for (size_t i = 0; i < 7; ++i) {
            uint8_t bytes[2]; pack16(bytes, values[i]);
            if (bytes[0] != expected[i][0] || bytes[1] != expected[i][1] || unpack16(bytes) != values[i]) packing = false;
        }
        uint64_t first = kFnvOffset, second = kFnvOffset;
        for (uint64_t index = 0; index < kRate + 100; ++index) {
            const auto frame = referenceFrame(index); uint8_t bytes[4], repeat[4];
            packFrame(bytes, frame); packFrame(repeat, referenceFrame(index));
            // Independent absolute-window oracle, including every silence frame and trailing zeros.
            const int16_t expectedLeft = index >= 4410 && index <= 13209 ? kReferenceTone[(index - 4410) % 100] : 0;
            const int16_t expectedRight = index >= 17620 && index <= 26419 ? kReferenceTone[(index - 17620) % 100] : 0;
            if (unpack16(bytes) != expectedLeft || unpack16(bytes + 2) != expectedRight) channels = false;
            first = hashBytes(first, bytes, 4); second = hashBytes(second, repeat, 4);
        }
        deterministic = first == second;
        if (!packing) reasons |= PcmPackingMismatch;
        if (!channels || !deterministic) reasons |= ReferencePreflightFailed;
    }
    bool pass() const noexcept { return reasons == 0; }
};
// Internal diagnostics only: two closed modes, no arbitrary PCM producer.
struct PcmTestSource {
    PcmMode mode;
    ZeroSource zero;
    uint64_t generated = 0, sourceHash = kFnvOffset, submittedHash = kFnvOffset, reasons = 0;
    uint64_t silence = 0, leftFrames = 0, rightFrames = 0, leftActive = 0, rightActive = 0;
    uint64_t unexpected = 0, inactive = 0, outside = 0;
    int maximum = 0, minimum = 0, peak = 0;
    static constexpr std::array<uint64_t, 13> exampleIndices{0,4410,4411,4435,4485,13209,13210,17620,17621,17645,17695,26419,26420};
    std::array<std::array<int64_t, 7>, exampleIndices.size()> examples{};
    size_t exampleCount = 0;
    explicit PcmTestSource(PcmMode selected) : mode(selected) {}
    // index is the committed submitted cursor plus this prepared URB's offset.
    // Failed work remains generated-but-unsubmitted; it is never retried as new frames.
    void fill(uint8_t* bytes, uint64_t index, uint32_t frames) noexcept {
        if (mode == PcmMode::Zero) { zero.fill(bytes, frames); generated = zero.generated; return; }
        for (uint32_t i = 0; i < frames; ++i) {
            const auto frame = referenceFrame(index + i);
            packFrame(bytes + i * 4, frame);
            if (exampleCount < exampleIndices.size() && index + i == exampleIndices[exampleCount]) {
                const auto* packed = bytes + i * 4;
                examples[exampleCount++] = {static_cast<int64_t>(index + i), frame.left, frame.right, packed[0], packed[1], packed[2], packed[3]};
            }
            if (index + i >= kLeftStart && index + i < kLeftEnd) { ++leftFrames; if (frame.left) ++leftActive; }
            else if (index + i >= kRightStart && index + i < kRightEnd) { ++rightFrames; if (frame.right) ++rightActive; }
            else ++silence;
            for (int value : {static_cast<int>(frame.left), static_cast<int>(frame.right)}) {
                if (value > maximum) maximum = value;
                if (value < minimum) minimum = value;
                const int absolute = value < 0 ? -value : value;
                if (absolute > peak) peak = absolute;
            }
        }
        sourceHash = hashBytes(sourceHash, bytes, static_cast<size_t>(frames) * 4);
        generated += frames;
    }
    bool inspect(const uint8_t* bytes, size_t count, uint64_t index, uint64_t frames) noexcept {
        if (mode == PcmMode::Zero) return zero.inspect(bytes, count);
        if (count % 4 || count / 4 != frames) reasons |= PcmPackingMismatch;
        for (size_t i = 0; i < count / 4; ++i) {
            const Stereo actual{unpack16(bytes + i * 4), unpack16(bytes + i * 4 + 2)};
            const auto expected = referenceFrame(index + i);
            const auto problem = referenceProblem(index + i, actual);
            reasons |= problem;
            if (problem & InactiveChannelNonzero) ++inactive;
            if (problem & NonzeroOutsideWindow) ++outside;
            if (actual.left != 0 && actual.left != expected.left) ++unexpected;
            if (actual.right != 0 && actual.right != expected.right) ++unexpected;
        }
        if (hashBytes(submittedHash, bytes, count) != sourceHash) reasons |= SourceHashMismatch;
        return reasons == 0;
    }
    void submitted(const uint8_t* bytes, size_t count) noexcept {
        if (mode == PcmMode::Reference) submittedHash = hashBytes(submittedHash, bytes, count);
    }
};
} // namespace sazanami::usbiso
