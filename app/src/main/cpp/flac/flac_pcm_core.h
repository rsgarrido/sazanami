#pragma once
#include <array>
#include <cstdint>
#include <cstddef>
#include <limits>
#include <cstring>
#include <initializer_list>

namespace sazanami::flacverify {
constexpr uint64_t kOffset = 14695981039346656037ULL, kPrime = 1099511628211ULL;
constexpr uint64_t kCounterMax = static_cast<uint64_t>(INT64_MAX);
enum class Outcome : int { Pending, Exact, Unsupported, Md5Unavailable, TotalUnavailable, Md5Mismatch,
    DecoderError, Truncated, SampleRange, FormatContradiction, InputOpenFailed, Cancelled,
    NotNativeFlac, CounterOverflow, CleanupFailed };
enum class Phase : int { Idle, DocumentOpened, DecoderCreated, MetadataReading, FormatAccepted,
    Decoding, EndOfStream, Verifying, Finished };
enum Field : size_t { Version, Result, Lifecycle, Recognized, Created, InitStatus, Md5Enabled, StreamInfo,
    Rate, Channels, Bits, Total, MinBlock, MaxBlock, MinFrame, MaxFrame, Md5Available, MetadataOk,
    ProcessOk, CleanEos, PhysicalEof, FinishCalled, FinishOk, Md5Verification, Callbacks, Blocks,
    Frames, SamplesPerChannel, Bytes, Hash, MinL, MaxL, MinR, MaxR, MinObserved, MaxObserved,
    RangeErrors, Contradictions, DecoderErrors, ReadErrno, Seekable, FileSize, FdClosed, DecoderDeleted,
    ResourceError, FrameOrderErrors, DecoderState, DupErrno, FieldCount };
inline void pack16(uint8_t* bytes, int32_t value) noexcept {
    const uint16_t bits = static_cast<uint16_t>(value);
    bytes[0] = static_cast<uint8_t>(bits); bytes[1] = static_cast<uint8_t>(bits >> 8);
}
inline uint64_t hashBytes(uint64_t hash, const uint8_t* bytes, size_t count) noexcept {
    for (size_t i = 0; i < count; ++i) hash = (hash ^ bytes[i]) * kPrime;
    return hash;
}
inline int64_t hashBits(uint64_t hash) noexcept { int64_t result; std::memcpy(&result, &hash, 8); return result; }
inline bool supported(uint32_t rate, uint32_t channels, uint32_t bits) noexcept { return rate == 44100 && channels == 2 && bits == 16; }
inline bool inRange(int32_t sample) noexcept { return sample >= -32768 && sample <= 32767; }
inline bool canAdd(uint64_t current, uint64_t amount) noexcept { return current <= kCounterMax && amount <= kCounterMax - current; }
struct Pcm {
    uint64_t frames = 0, bytes = 0, hash = kOffset, blocks = 0, callbacks = 0, rangeErrors = 0;
    int32_t minL = 0, maxL = 0, minR = 0, maxR = 0, minimum = 0, maximum = 0;
    std::array<std::array<int64_t, 7>, 7> examples{};
    std::array<bool, 7> exampleUsed{};
    Outcome failure = Outcome::Pending;
    void example(size_t slot, int32_t left, int32_t right, const uint8_t* packed) noexcept {
        examples[slot] = {static_cast<int64_t>(frames), left, right, packed[0], packed[1], packed[2], packed[3]};
        exampleUsed[slot] = true;
    }
    bool append(int32_t left, int32_t right) noexcept {
        if (failure != Outcome::Pending) return false;
        if (!inRange(left) || !inRange(right)) {
            rangeErrors += !inRange(left) + !inRange(right); failure = Outcome::SampleRange; return false;
        }
        if (!canAdd(frames, 1) || !canAdd(bytes, 4)) { failure = Outcome::CounterOverflow; return false; }
        uint8_t packed[4]; pack16(packed, left); pack16(packed + 2, right);
        const int32_t low = left < right ? left : right, high = left > right ? left : right;
        if (frames == 0 || low < minimum) { minimum = low; example(4, left, right, packed); }
        if (frames == 0 || high > maximum) { maximum = high; example(5, left, right, packed); }
        if (frames < 4) example(static_cast<size_t>(frames), left, right, packed);
        example(6, left, right, packed);
        if (frames == 0 || left < minL) minL = left;
        if (frames == 0 || left > maxL) maxL = left;
        if (frames == 0 || right < minR) minR = right;
        if (frames == 0 || right > maxR) maxR = right;
        hash = hashBytes(hash, packed, 4); ++frames; bytes += 4;
        return true;
    }
};
inline Outcome classify(bool recognized, bool initialized, bool metadata, bool accepted, bool process,
    bool eos, bool md5Enabled, bool md5Present, bool finish, uint64_t total, const Pcm& pcm,
    uint64_t errors, uint64_t contradictions, bool resourcesReleased) noexcept {
    if (!resourcesReleased) return Outcome::CleanupFailed;
    if (!recognized) return Outcome::NotNativeFlac;
    if (!initialized || !metadata) return Outcome::DecoderError;
    if (!accepted) return Outcome::Unsupported;
    if (pcm.failure != Outcome::Pending) return pcm.failure;
    if (contradictions) return Outcome::FormatContradiction;
    if (errors) return Outcome::DecoderError;
    if (!process || !eos || pcm.frames == 0 || (total && total != pcm.frames)) return Outcome::Truncated;
    if (pcm.frames > kCounterMax / 4 || pcm.bytes != pcm.frames * 4) return Outcome::CounterOverflow;
    if (!md5Enabled) return Outcome::DecoderError;
    if (md5Present && !finish) return Outcome::Md5Mismatch;
    if (!md5Present) return Outcome::Md5Unavailable;
    if (!total) return Outcome::TotalUnavailable;
    return Outcome::Exact;
}
} // namespace sazanami::flacverify
