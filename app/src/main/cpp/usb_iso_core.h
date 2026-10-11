#pragma once
#include "direct_pcm_format.h"
#include <array>
#include <cstddef>
#include <cstdint>
#include <limits>

namespace sazanami::usbiso {
using std::size_t;
constexpr uint32_t kRate = 44100, kBytesPerFrame = 4;
// kFeedbackPool is the minimum; usb_feedback_policy.h derives the runtime feedback pool.
constexpr size_t kOutPool = 8, kOutPackets = 8, kFeedbackPool = 2;
constexpr uint64_t kDurationUs = 1000000, kBootstrapUs = 100000;
constexpr uint64_t kDrainUs = 100000, kCancelUs = 250000, kQueueWatchdogUs = 50000;
constexpr unsigned kInvalidFeedbackLimit = 3, kPlausibilityPercent = 5;
constexpr size_t kFeedbackExamples = 12, kPacketExamples = 24;
enum class State : int { Idle, Configured, Starting, Bootstrap, FeedbackLocked, StopRequested, Draining, Stopped };
enum class Stop : int { Duration, Requested, AuthorityLost, Detached, Submit, Reap, Feedback, Underrun, Capacity, Worker, Accounting, Source, SourceEof, OutPoolStall, NaturalEof, SourceVerificationStall };
enum Reason : uint64_t { SubmitError = 1ULL << 0, ReapError = 1ULL << 1, UrbError = 1ULL << 2,
    PacketError = 1ULL << 3, ShortPacket = 1ULL << 4, MalformedFeedback = 1ULL << 5,
    InvalidFeedback = 1ULL << 6, FeedbackTimeout = 1ULL << 7, QueueUnderrun = 1ULL << 8,
    CapacityExceeded = 1ULL << 9, UnknownCompletion = 1ULL << 10, DuplicateCompletion = 1ULL << 11,
    GenerationMismatch = 1ULL << 12, DeviceDetached = 1ULL << 13, AuthorityChanged = 1ULL << 14,
    WorkerFailure = 1ULL << 15, ShutdownUnaccounted = 1ULL << 16, FrameMismatch = 1ULL << 17,
    NonzeroPcm = 1ULL << 18, SchedulerInvalid = 1ULL << 19, CancelError = 1ULL << 20,
    ReferencePreflightFailed = 1ULL << 21, ReferenceTableHashMismatch = 1ULL << 22,
    PcmPackingMismatch = 1ULL << 23, SourceHashMismatch = 1ULL << 24,
    InactiveChannelNonzero = 1ULL << 25, NonzeroOutsideWindow = 1ULL << 26, ReferencePeakViolation = 1ULL << 27,
    FlacSourceFault = 1ULL << 28, OutPoolStall = 1ULL << 29, TerminalAccounting = 1ULL << 30 };

struct Integrity {
    uint64_t reasons = 0;
    void compromise(uint64_t reason) noexcept { reasons |= reason; } // Never clears for this generation.
    bool valid() const noexcept { return reasons == 0; }
};
inline bool transition(State& state, State next) noexcept {
    const bool allowed = (state == State::Idle && next == State::Configured) ||
        (state == State::Configured && next == State::Starting) ||
        (state == State::Starting && next == State::Bootstrap) ||
        (state == State::Bootstrap && next == State::FeedbackLocked) ||
        ((state == State::Starting || state == State::Bootstrap || state == State::FeedbackLocked) && next == State::StopRequested) ||
        (state == State::StopRequested && next == State::Draining) || (state == State::Draining && next == State::Stopped);
    if (allowed) state = next;
    return allowed;
}
inline uint64_t intervalUs(int interval) noexcept { return interval >= 1 && interval <= 16 ? 125ULL << (interval - 1) : 0; }
inline uint32_t capacity(int raw) noexcept {
    const unsigned bytes = static_cast<unsigned>(raw) & 0x7ff, extra = (static_cast<unsigned>(raw) >> 11) & 3;
    return raw > 0 && raw <= 0xffff && !(raw & 0xe000) && bytes > 0 && bytes <= 1024 && extra < 3 ? bytes * (extra + 1) : 0;
}
inline size_t allocationBytes(size_t base, size_t descriptor, unsigned count) noexcept {
    if (count == 0 || count > kOutPackets || descriptor > SIZE_MAX / count) return 0;
    const size_t extra = descriptor * count;
    return extra <= SIZE_MAX - base ? base + extra : 0;
}
inline bool packetFits(uint32_t frames, uint32_t payload, uint32_t bytesPerFrame = kBytesPerFrame) noexcept {
    return (bytesPerFrame==4 || bytesPerFrame==6) && static_cast<uint64_t>(frames) * bytesPerFrame <= payload;
}
inline uint64_t outPacketProblem(int urbStatus, int packetStatus, uint32_t requested, uint32_t actual, bool cancelled) noexcept {
    if (cancelled) return 0;
    uint64_t reasons = urbStatus != 0 ? static_cast<uint64_t>(UrbError) : 0ULL;
    if (packetStatus != 0) reasons |= PacketError;
    else if (actual != requested) reasons |= ShortPacket;
    return reasons;
}
enum class FeedbackValidity : int { Valid, Length, Zero, Reserved, Implausible, CompletionError, Stale };
inline bool feedbackExpired(uint64_t started, uint64_t lastValid, uint64_t now, uint64_t holdover) noexcept {
    return lastValid ? now - lastValid > holdover : now - started >= kBootstrapUs;
}
struct Feedback { uint32_t raw = 0; FeedbackValidity validity = FeedbackValidity::Length; };
// USB 2.0 §5.12.4.2: HS unsigned 12.13 aligned on the wire as LE 16.16, per microframe.
// UAC2 §3.16.2.2 references this format. No empirical format shifting or device quirks.
inline Feedback decodeFeedback(const uint8_t* bytes, size_t length, int packetStatus, uint32_t nominalRate = kRate) noexcept {
    Feedback result;
    if (packetStatus != 0) { result.validity = FeedbackValidity::CompletionError; return result; }
    if (length != 4) return result;
    for (unsigned i = 0; i < 4; ++i) result.raw |= static_cast<uint32_t>(bytes[i]) << (8 * i);
    if (!result.raw) result.validity = FeedbackValidity::Zero;
    else if (result.raw & 0xf0000000U) result.validity = FeedbackValidity::Reserved;
    else {
        const uint64_t scaled = static_cast<uint64_t>(result.raw) * 8000 * 100;
        const uint64_t nominal = static_cast<uint64_t>(nominalRate) * 65536;
        result.validity = scaled >= nominal * (100 - kPlausibilityPercent) && scaled <= nominal * (100 + kPlausibilityPercent)
            ? FeedbackValidity::Valid : FeedbackValidity::Implausible;
    }
    return result;
}
struct Scheduler {
    static constexpr uint64_t denominator = 8000ULL * 65536;
    uint32_t nominalRate; // Copied with the rational phase on source transaction rollback.
    explicit Scheduler(uint32_t rate = kRate) noexcept : nominalRate(rate) {}
    uint64_t remainder = 0, frames = 0;
    bool next(unsigned microframes, uint32_t feedback, uint32_t& count) noexcept {
        if (!sazanami::directpcm::supportsRate(nominalRate) || microframes < 1 || microframes > 4) return false;
        const uint64_t increment = (feedback ? static_cast<uint64_t>(feedback) * 8000 : static_cast<uint64_t>(nominalRate) * 65536) * microframes;
        if (increment > std::numeric_limits<uint64_t>::max() - remainder) return false;
        remainder += increment;
        const uint64_t scheduled = remainder / denominator;
        remainder %= denominator;
        if (scheduled > std::numeric_limits<uint32_t>::max() || frames > std::numeric_limits<uint64_t>::max() - scheduled) return false;
        count = static_cast<uint32_t>(scheduled);
        frames += count;
        return true;
    }
};
struct ZeroSource {
    uint64_t generated = 0;
    uint8_t byteOr = 0;
    void fill(uint8_t* bytes, uint32_t frames) noexcept {
        const size_t count = static_cast<size_t>(frames) * kBytesPerFrame;
        for (size_t i = 0; i < count; ++i) bytes[i] = 0;
        generated += frames;
    }
    bool inspect(const uint8_t* bytes, size_t count) noexcept {
        for (size_t i = 0; i < count; ++i) byteOr |= bytes[i];
        return byteOr == 0;
    }
};
struct Identity {
    uint64_t attachment = 0, stream = 0, submission = 0;
    bool outstanding = false, discardRequested = false;
    bool submit(uint64_t a, uint64_t s, uint64_t serial) noexcept {
        if (outstanding || serial == 0) return false;
        attachment = a; stream = s; submission = serial; outstanding = true; discardRequested = false;
        return true;
    }
    bool complete(uint64_t a, uint64_t s) noexcept {
        if (!outstanding || attachment != a || stream != s) return false;
        outstanding = false;
        return true;
    }
};
} // namespace sazanami::usbiso
