#include "usb_iso_core.h"
#include <jni.h>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>
#include <poll.h>
#include <atomic>
#include <chrono>
#include <cstdlib>
#include <cstring>
#include <cerrno>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <vector>
#include <stdexcept>
#include <algorithm>

namespace {
using namespace sazanami::usbiso;
// Versioned immutable result schema; mirrored by UsbIsoNativeStats.fields, not a pointer packet.
enum Field : size_t { Api, Attachment, Stream, Started, StateCode, Reasons, StopCode, DurationUs,
    SourceFrames, ScheduledFrames, SubmittedFrames, CompletedFrames, OutSubmitted, OutCompleted,
    OutPacketsSubmitted, OutPacketsCompleted, OutBytesSubmitted, KernelOutActualBytes, OutPacketErrors, OutUrbErrors,
    FeedbackSubmitted, FeedbackCompleted, FeedbackPacketsCompleted, FeedbackValid, FeedbackInvalid, FeedbackTimeouts,
    Discarded, CancellationCompletions, CompletionsAfterStop, UnknownCompletions, DuplicateCompletions,
    OutAtStop, FeedbackAtStop, OutstandingFinal, WorkerStopped, BootstrapPackets, FeedbackScheduledPackets,
    LastFeedbackAge, MinFeedback, MaxFeedback, SumFeedback, FeedbackSamples, InvalidStreak, Remainder,
    Underruns, CancelledFrames, NotSubmittedFrames, PcmByteOr, SubmitErrno, ReapErrno, DiscardErrno,
    FirstUrbStatus, FirstPacketStatus, FirstPacketLength, FirstPacketActual, OutCancelled, FeedbackCancelled,
    FeedbackInterval, DataInterval, EndpointCapacity, FeedbackHoldover, ShutdownIncomplete, FirstUrbErrorCount,
    FeedbackUrbErrors, LifecycleMask, FieldCount };
constexpr jint kSnapshotVersion = 1;
using Clock = std::chrono::steady_clock;
uint64_t monotonicUs() { return static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::microseconds>(Clock::now().time_since_epoch()).count()); }

struct Slot {
    // calloc alignment is suitable for usbdevfs_urb. UAPI has a flexible array with zero included entries.
    struct Free { void operator()(usbdevfs_urb* pointer) const noexcept { std::free(pointer); } };
    std::unique_ptr<usbdevfs_urb, Free> urb;
    std::vector<uint8_t> buffer;
    std::array<uint32_t, kOutPackets> frames{};
    Identity identity;
    bool feedback = false;
    unsigned packets = 0;
    uint64_t submittedAt = 0;
    void allocate(bool isFeedback, unsigned count, unsigned capacityBytes) {
        if (count < 1 || count > kOutPackets || capacityBytes < 1 || capacityBytes > 3072) throw std::invalid_argument("URB bounds");
        const size_t bytes = allocationBytes(sizeof(usbdevfs_urb), sizeof(usbdevfs_iso_packet_desc), count);
        if (!bytes) throw std::invalid_argument("URB size overflow");
        urb.reset(static_cast<usbdevfs_urb*>(std::calloc(1, bytes)));
        if (!urb) throw std::bad_alloc();
        buffer.resize(static_cast<size_t>(count) * capacityBytes, 0);
        feedback = isFeedback; packets = count;
    }
};

struct Session {
    int fd; // Borrowed; Java closes, only after this execution context returns.
    const uint64_t attachment, stream;
    const int dataEndpoint, feedbackEndpoint, dataInterval, feedbackInterval;
    const uint32_t payload;
    std::atomic<int> requestedStop{0};
    std::atomic<bool> executing{false};
    std::array<Slot, kOutPool + kFeedbackPool> slots;
    std::array<jlong, FieldCount> stats{};
    std::array<jlong, kFeedbackExamples * 6> feedbackExamples{};
    std::array<jlong, kPacketExamples * 7> packetExamples{};
    size_t feedbackExampleCount = 0, packetExampleCount = 0;
    State state = State::Idle;
    Integrity integrity;
    Scheduler scheduler;
    ZeroSource source;
    uint64_t started = 0, lastValid = 0, lastOutCompletion = 0, serial = 0;
    uint32_t acceptedFeedback = 0;
    unsigned invalidStreak = 0;
    bool stopping = false, ran = false;
    Stop stopReason = Stop::Duration;

    Session(int borrowedFd, uint64_t a, uint64_t s, const std::array<jint, 16>& config)
        : fd(borrowedFd), attachment(a), stream(s), dataEndpoint(config[4]), feedbackEndpoint(config[8]),
          dataInterval(config[7]), feedbackInterval(config[11]), payload(capacity(config[6])) {
        stats[Api] = kSnapshotVersion; stats[Attachment] = static_cast<jlong>(a); stats[Stream] = static_cast<jlong>(s);
        stats[DataInterval] = static_cast<jlong>(intervalUs(dataInterval));
        stats[FeedbackInterval] = static_cast<jlong>(intervalUs(feedbackInterval));
        stats[EndpointCapacity] = payload;
        stats[FeedbackHoldover] = static_cast<jlong>(std::max<uint64_t>(20000, intervalUs(feedbackInterval) * 4));
        for (size_t i = 0; i < slots.size(); ++i) slots[i].allocate(i >= kOutPool,
            i >= kOutPool ? 1 : static_cast<unsigned>(kOutPackets), i >= kOutPool ? 4 : payload);
        move(State::Configured);
    }
    void move(State next) {
        if (!transition(state, next)) integrity.compromise(SchedulerInvalid);
        stats[StateCode] = static_cast<int>(state);
        stats[LifecycleMask] |= 1LL << static_cast<int>(state);
    }
    void fail(uint64_t reason, Stop stop) { integrity.compromise(reason); if (!stopping) stopReason = stop; }
    size_t outstanding(bool feedback) const {
        size_t count = 0;
        for (const auto& slot : slots) if (slot.feedback == feedback && slot.identity.outstanding) ++count;
        return count;
    }
    void beginStop() {
        if (stopping) return;
        stopping = true; stats[OutAtStop] = static_cast<jlong>(outstanding(false)); stats[FeedbackAtStop] = static_cast<jlong>(outstanding(true));
        move(State::StopRequested); move(State::Draining);
    }
    bool submit(Slot& slot) {
        if (stopping || requestedStop.load() != 0 || !integrity.valid()) return false;
        if (monotonicUs() - started >= kDurationUs) return false;
        if (slot.identity.outstanding) { fail(SchedulerInvalid, Stop::Accounting); return false; }
        auto* urb = slot.urb.get();
        std::memset(urb, 0, sizeof(usbdevfs_urb) + slot.packets * sizeof(usbdevfs_iso_packet_desc));
        urb->type = USBDEVFS_URB_TYPE_ISO;
        urb->endpoint = static_cast<unsigned char>(slot.feedback ? feedbackEndpoint : dataEndpoint);
        urb->flags = USBDEVFS_URB_ISO_ASAP; // Kernel derives interval from the active endpoint descriptor.
        urb->number_of_packets = static_cast<int>(slot.packets); urb->buffer = slot.buffer.data();
        urb->usercontext = &slot.identity;
        uint32_t length = 0, frameCount = 0;
        if (slot.feedback) {
            std::fill(slot.buffer.begin(), slot.buffer.end(), 0);
            urb->iso_frame_desc[0].length = 4; length = 4;
        } else {
            for (unsigned i = 0; i < slot.packets; ++i) {
                uint32_t frames = 0;
                if (!scheduler.next(static_cast<unsigned>(intervalUs(dataInterval) / 125), acceptedFeedback, frames)) {
                    fail(SchedulerInvalid, Stop::Accounting); return false;
                }
                stats[ScheduledFrames] += frames;
                const uint64_t bytes = static_cast<uint64_t>(frames) * kBytesPerFrame;
                if (!packetFits(frames, payload) || length + bytes > slot.buffer.size()) { fail(CapacityExceeded, Stop::Capacity); return false; }
                source.fill(slot.buffer.data() + length, frames);
                if (!source.inspect(slot.buffer.data() + length, static_cast<size_t>(bytes))) { fail(NonzeroPcm, Stop::Worker); return false; }
                slot.frames[i] = frames; frameCount += frames;
                urb->iso_frame_desc[i].length = static_cast<unsigned>(bytes); length += static_cast<unsigned>(bytes);
                ++stats[acceptedFeedback ? FeedbackScheduledPackets : BootstrapPackets];
            }
        }
        // Exact packed lengths only: stale/unused buffer capacity is never submitted.
        urb->buffer_length = static_cast<int>(length);
        if (requestedStop.load() != 0) { fail(AuthorityChanged, Stop::Requested); return false; }
        slot.submittedAt = monotonicUs();
        const int returned = ::ioctl(fd, USBDEVFS_SUBMITURB, urb);
        const int saved = returned < 0 ? errno : 0;
        if (returned != 0) {
            stats[SubmitErrno] = saved; stats[NotSubmittedFrames] += frameCount;
            fail(saved == ENODEV ? DeviceDetached : SubmitError, saved == ENODEV ? Stop::Detached : Stop::Submit); return false;
        }
        if (!slot.identity.submit(attachment, stream, ++serial)) { fail(SchedulerInvalid, Stop::Accounting); return false; }
        ++stats[slot.feedback ? FeedbackSubmitted : OutSubmitted];
        if (!slot.feedback) { stats[SubmittedFrames] += frameCount; stats[OutBytesSubmitted] += length; stats[OutPacketsSubmitted] += slot.packets; }
        return true;
    }
    void packetExample(const Slot& slot, unsigned i, int urbStatus) {
        const auto& packet = slot.urb->iso_frame_desc[i];
        const bool error = urbStatus != 0 || packet.status != 0 || (!slot.feedback && packet.actual_length != packet.length);
        if (!error && packetExampleCount >= 8) return;
        const size_t index = packetExampleCount < kPacketExamples ? packetExampleCount++ : kPacketExamples - 1;
        const std::array<jlong, 7> row{slot.urb->endpoint, urbStatus, static_cast<int>(packet.status), packet.length,
            packet.actual_length, static_cast<jlong>(stream), static_cast<jlong>(slot.identity.submission)};
        std::copy(row.begin(), row.end(), packetExamples.begin() + index * row.size());
    }
    void feedbackPacket(Slot& slot, int urbStatus) {
        const auto& packet = slot.urb->iso_frame_desc[0];
        const auto decoded = decodeFeedback(slot.buffer.data(), packet.actual_length, static_cast<int>(packet.status));
        const size_t index = feedbackExampleCount < kFeedbackExamples ? feedbackExampleCount++ : kFeedbackExamples - 1;
        uint32_t raw = 0;
        for (unsigned i = 0; i < std::min<unsigned>(4, packet.actual_length); ++i) raw |= static_cast<uint32_t>(slot.buffer[i]) << (8 * i);
        const uint64_t observed = monotonicUs(), age = observed - slot.submittedAt;
        const bool stale = age > static_cast<uint64_t>(stats[FeedbackHoldover]);
        const std::array<jlong, 6> row{raw, packet.actual_length, static_cast<int>(packet.status),
            static_cast<jlong>(observed - started), static_cast<int>(stale ? FeedbackValidity::Stale : decoded.validity), static_cast<jlong>(age)};
        std::copy(row.begin(), row.end(), feedbackExamples.begin() + index * row.size());
        ++stats[FeedbackPacketsCompleted];
        if (stale) { ++stats[FeedbackInvalid]; ++stats[FeedbackTimeouts]; fail(FeedbackTimeout, Stop::Feedback); return; }
        if (urbStatus != 0 || packet.status != 0 || packet.actual_length > 4 || decoded.validity == FeedbackValidity::Length || decoded.validity == FeedbackValidity::Reserved) {
            ++stats[FeedbackInvalid]; fail(MalformedFeedback, Stop::Feedback); return;
        }
        if (decoded.validity != FeedbackValidity::Valid) {
            ++stats[FeedbackInvalid]; stats[InvalidStreak] = ++invalidStreak;
            if (invalidStreak >= kInvalidFeedbackLimit) fail(InvalidFeedback, Stop::Feedback);
            return;
        }
        ++stats[FeedbackValid]; ++stats[FeedbackSamples]; stats[SumFeedback] += decoded.raw;
        if (!stats[MinFeedback] || decoded.raw < static_cast<uint64_t>(stats[MinFeedback])) stats[MinFeedback] = decoded.raw;
        if (decoded.raw > static_cast<uint64_t>(stats[MaxFeedback])) stats[MaxFeedback] = decoded.raw;
        // Conservative age bound: kernel supplies no feedback timestamp, so use submission time,
        // never pretend a delayed reap makes an old sample fresh.
        invalidStreak = 0; stats[InvalidStreak] = 0; acceptedFeedback = decoded.raw; lastValid = slot.submittedAt;
        if (!stopping && state == State::Bootstrap) move(State::FeedbackLocked);
    }
    bool reapOne() {
        void* completed = nullptr;
        const int returned = ::ioctl(fd, USBDEVFS_REAPURBNDELAY, &completed);
        const int saved = returned < 0 ? errno : 0;
        if (returned < 0) {
            if (saved == EAGAIN || saved == EINTR) return false;
            stats[ReapErrno] = saved; fail(saved == ENODEV ? DeviceDetached : ReapError, saved == ENODEV ? Stop::Detached : Stop::Reap); return false;
        }
        Slot* found = nullptr;
        for (auto& slot : slots) if (slot.urb.get() == completed) { found = &slot; break; }
        if (!found) { ++stats[UnknownCompletions]; fail(UnknownCompletion, Stop::Accounting); return true; }
        auto& slot = *found;
        if (!slot.identity.outstanding) { ++stats[DuplicateCompletions]; fail(DuplicateCompletion, Stop::Accounting); return true; }
        if (!slot.identity.complete(attachment, stream)) { fail(GenerationMismatch, Stop::Accounting); return true; }
        if (stopping) ++stats[CompletionsAfterStop];
        ++stats[slot.feedback ? FeedbackCompleted : OutCompleted];
        const int status = slot.urb->status;
        const bool cancelled = slot.identity.discardRequested && (status == -ENOENT || status == -ECONNRESET);
        if (cancelled) {
            ++stats[CancellationCompletions]; ++stats[slot.feedback ? FeedbackCancelled : OutCancelled];
            if (!slot.feedback) { for (unsigned i = 0; i < slot.packets; ++i) stats[CancelledFrames] += slot.frames[i]; }
        } else if (status != 0 || slot.urb->error_count != 0) {
            if (!stats[FirstUrbStatus]) stats[FirstUrbStatus] = status;
            if (!stats[FirstUrbErrorCount]) stats[FirstUrbErrorCount] = slot.urb->error_count;
            ++stats[slot.feedback ? FeedbackUrbErrors : OutUrbErrors];
            fail(UrbError, Stop::Worker);
        }
        for (unsigned i = 0; i < slot.packets; ++i) {
            const auto& packet = slot.urb->iso_frame_desc[i];
            packetExample(slot, i, status);
            if (slot.feedback) continue;
            ++stats[OutPacketsCompleted]; stats[KernelOutActualBytes] += packet.actual_length;
            integrity.compromise(outPacketProblem(status, static_cast<int>(packet.status), packet.length, packet.actual_length, cancelled));
            if (cancelled) continue; // Cancelled frames are explicitly distinct from successful completion frames.
            if (packet.status != 0) {
                ++stats[OutPacketErrors]; fail(PacketError, Stop::Worker);
                if (!stats[FirstPacketStatus]) { stats[FirstPacketStatus] = static_cast<int>(packet.status);
                    stats[FirstPacketLength] = packet.length; stats[FirstPacketActual] = packet.actual_length; }
            } else if (packet.actual_length != packet.length) {
                ++stats[OutPacketErrors]; fail(ShortPacket, Stop::Worker); stats[FirstPacketLength] = packet.length; stats[FirstPacketActual] = packet.actual_length;
            } else if (status == 0) stats[CompletedFrames] += slot.frames[i];
        }
        if (!slot.feedback) lastOutCompletion = monotonicUs();
        if (slot.feedback && !cancelled) feedbackPacket(slot, status);
        return true;
    }
    void cancel(Slot& slot) {
        if (!slot.identity.outstanding || slot.identity.discardRequested) return;
        // Only known pointers in this generation. DISCARD kills synchronously but DOES NOT reap.
        const int returned = ::ioctl(fd, USBDEVFS_DISCARDURB, slot.urb.get());
        const int saved = returned < 0 ? errno : 0;
        if (returned == 0) { slot.identity.discardRequested = true; ++stats[Discarded]; }
        else if (saved != EINVAL) { stats[DiscardErrno] = saved; integrity.compromise(saved == ENODEV ? DeviceDetached : CancelError); }
        // EINVAL is a possible completion race; identity remains outstanding until actual reap.
    }
    void pollCompletion() {
        pollfd descriptor{fd, POLLOUT, 0};
        const int returned = ::poll(&descriptor, 1, 1);
        const int saved = returned < 0 ? errno : 0;
        if (returned < 0 && saved != EINTR) fail(ReapError, Stop::Reap);
        if (returned > 0 && (descriptor.revents & (POLLERR | POLLHUP | POLLNVAL))) fail(DeviceDetached, Stop::Detached);
    }
    void shutdown() {
        beginStop();
        for (auto& slot : slots) if (slot.feedback || !integrity.valid() || requestedStop.load()) cancel(slot);
        const uint64_t drainStarted = monotonicUs();
        bool cancelledOut = false;
        while (outstanding(false) + outstanding(true) > 0 && monotonicUs() - drainStarted < kDrainUs + kCancelUs) {
            for (unsigned i = 0; i < 64 && reapOne(); ++i) {}
            if (!cancelledOut && (monotonicUs() - drainStarted >= kDrainUs || !integrity.valid())) {
                for (auto& slot : slots) cancel(slot);
                cancelledOut = true;
                if (outstanding(false)) integrity.compromise(ShutdownUnaccounted);
            }
            if (outstanding(false) + outstanding(true)) pollCompletion();
        }
        stats[OutstandingFinal] = static_cast<jlong>(outstanding(false) + outstanding(true));
        if (stats[OutstandingFinal]) { integrity.compromise(ShutdownUnaccounted); stats[ShutdownIncomplete] = 1; }
        stats[SourceFrames] = static_cast<jlong>(source.generated); stats[PcmByteOr] = source.byteOr;
        stats[NotSubmittedFrames] = std::max(stats[NotSubmittedFrames], stats[SourceFrames] - stats[SubmittedFrames]);
        stats[Remainder] = static_cast<jlong>(scheduler.remainder);
        if (stats[SourceFrames] != stats[ScheduledFrames] || stats[ScheduledFrames] != stats[SubmittedFrames] ||
            stats[SubmittedFrames] != stats[CompletedFrames]) integrity.compromise(FrameMismatch);
        if (stats[OutSubmitted] != stats[OutCompleted] || stats[FeedbackSubmitted] != stats[FeedbackCompleted] ||
            stats[OutPacketsSubmitted] != stats[OutPacketsCompleted]) integrity.compromise(ShutdownUnaccounted);
        if (stats[OutBytesSubmitted] != stats[SubmittedFrames] * kBytesPerFrame || stats[KernelOutActualBytes] != stats[OutBytesSubmitted] ||
            stats[OutPacketsSubmitted] != stats[OutSubmitted] * static_cast<jlong>(kOutPackets)) integrity.compromise(FrameMismatch);
        stats[LastFeedbackAge] = lastValid ? static_cast<jlong>(monotonicUs() - lastValid) : -1;
        stats[DurationUs] = static_cast<jlong>(monotonicUs() - started);
        move(State::Stopped); stats[WorkerStopped] = 1;
        stats[Reasons] = static_cast<jlong>(integrity.reasons); stats[StopCode] = static_cast<int>(stopReason);
    }
    void run() {
        if (executing.exchange(true)) throw std::invalid_argument("Transport already executing");
        struct End { Session& session; ~End() { session.executing.store(false); } } end{*this};
        if (ran) throw std::invalid_argument("Transport already used");
        ran = true;
        started = monotonicUs(); lastOutCompletion = started; stats[Started] = 1;
        move(State::Starting); move(State::Bootstrap);
        try {
            // Receive feedback immediately, before priming a modest nominal OUT queue.
            for (size_t i = kOutPool; i < slots.size() && integrity.valid(); ++i) submit(slots[i]);
            for (size_t i = 0; i < kOutPool && integrity.valid(); ++i) submit(slots[i]);
            while (integrity.valid()) {
                const uint64_t now = monotonicUs();
                if (requestedStop.load()) { fail(AuthorityChanged, requestedStop.load() == 2 ? Stop::AuthorityLost : Stop::Requested); break; }
                if (now - started >= kDurationUs) break;
                if (feedbackExpired(started, lastValid, now, static_cast<uint64_t>(stats[FeedbackHoldover]))) {
                    ++stats[FeedbackTimeouts]; fail(FeedbackTimeout, Stop::Feedback); break;
                }
                for (unsigned i = 0; i < 64 && reapOne(); ++i) {}
                if (!integrity.valid()) break;
                if (monotonicUs() - started >= kDurationUs) break;
                if (outstanding(false) == 0 || monotonicUs() - lastOutCompletion > kQueueWatchdogUs) {
                    ++stats[Underruns]; fail(QueueUnderrun, Stop::Underrun); break;
                }
                for (auto& slot : slots) if (!slot.identity.outstanding && integrity.valid()) submit(slot);
                pollCompletion();
            }
        } catch (...) { fail(WorkerFailure, Stop::Worker); }
        shutdown(); // No execution context returns while continuing to submit/reap in the background.
    }
};

std::mutex registryMutex;
std::unordered_map<jlong, std::shared_ptr<Session>> registry;
jlong nextHandle = 1;
void error(JNIEnv* env, const char* type, const char* message) {
    jclass cls = env->FindClass(type);
    if (cls) { env->ThrowNew(cls, message); env->DeleteLocalRef(cls); }
}
std::shared_ptr<Session> lookup(jlong handle, jlong attachment, jlong stream) {
    std::lock_guard<std::mutex> lock(registryMutex);
    auto found = registry.find(handle);
    if (found == registry.end() || found->second->attachment != static_cast<uint64_t>(attachment) || found->second->stream != static_cast<uint64_t>(stream)) return nullptr;
    return found->second;
}
jobject snapshot(JNIEnv* env, const Session& session) {
    jclass cls = env->FindClass("io/github/rsgarrido/sazanami/player/usb/UsbIsoNativeSnapshot");
    if (!cls) return nullptr;
    jmethodID constructor = env->GetMethodID(cls, "<init>", "([J[J[J)V");
    if (!constructor) return nullptr;
    jlongArray numbers = env->NewLongArray(FieldCount);
    jlongArray feedback = env->NewLongArray(static_cast<jsize>(session.feedbackExampleCount * 6));
    jlongArray packets = env->NewLongArray(static_cast<jsize>(session.packetExampleCount * 7));
    if (!numbers || !feedback || !packets) return nullptr;
    env->SetLongArrayRegion(numbers, 0, FieldCount, session.stats.data());
    env->SetLongArrayRegion(feedback, 0, static_cast<jsize>(session.feedbackExampleCount * 6), session.feedbackExamples.data());
    env->SetLongArrayRegion(packets, 0, static_cast<jsize>(session.packetExampleCount * 7), session.packetExamples.data());
    if (env->ExceptionCheck()) return nullptr;
    return env->NewObject(cls, constructor, numbers, feedback, packets);
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeCreateSilentTransport(
    JNIEnv* env, jobject, jint fd, jlong attachment, jlong stream, jintArray configuration) {
    if (fd < 0 || attachment < 1 || stream < 1 || !configuration || env->GetArrayLength(configuration) != 16) {
        error(env, "java/lang/IllegalArgumentException", "Invalid silent transport identity/configuration"); return 0;
    }
    std::array<jint, 16> config{}; env->GetIntArrayRegion(configuration, 0, 16, config.data());
    if (env->ExceptionCheck()) return 0;
    if (config[0] < 0 || config[0] > 255 || config[1] < 0 || config[1] > 255 || config[0] == config[1] ||
        config[2] < 1 || config[2] > 255 || config[3] < 0 || config[3] > 255 || config[2] == config[3] ||
        config[4] < 1 || config[4] > 15 || config[5] != 5 || !capacity(config[6]) || config[7] < 1 || config[7] > 3 ||
        config[8] < 0x81 || config[8] > 0x8f || config[9] != 0x11 || config[10] != 4 || config[11] < 1 || config[11] > 7 ||
        config[12] != static_cast<int>(kRate) || config[13] != 2 || config[14] != 2 || config[15] != 16) {
        error(env, "java/lang/IllegalArgumentException", "Unsupported bounded UAC2 silence target"); return 0;
    }
    const int speed = ::ioctl(fd, USBDEVFS_GET_SPEED);
    const int saved = speed < 0 ? errno : 0;
    if (speed < 0) return -static_cast<jlong>(saved);
    if (speed != 3) { error(env, "java/lang/IllegalStateException", "Actual USB High Speed required"); return 0; }
    constexpr uint64_t scaledSecond = 100 * kDurationUs;
    const uint64_t worstFrames = (static_cast<uint64_t>(kRate) * intervalUs(config[7]) * (100 + kPlausibilityPercent) + scaledSecond - 1) / scaledSecond;
    if (((config[6] & 0x1800) != 0 && config[7] != 1) || capacity(config[6]) < worstFrames * kBytesPerFrame) {
        error(env, "java/lang/IllegalArgumentException", "Insufficient/invalid service-interval capacity"); return 0;
    }
    try {
        auto session = std::make_shared<Session>(fd, static_cast<uint64_t>(attachment), static_cast<uint64_t>(stream), config);
        std::lock_guard<std::mutex> lock(registryMutex);
        if (!registry.empty() || nextHandle == std::numeric_limits<jlong>::max()) throw std::invalid_argument("Transport registry busy/exhausted");
        const jlong handle = nextHandle++; registry.emplace(handle, std::move(session)); return handle;
    } catch (...) { error(env, "java/lang/IllegalStateException", "Silent transport allocation/registration unavailable"); return 0; }
}

extern "C" JNIEXPORT jobject JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeRunSilentTransport(JNIEnv* env, jobject, jlong handle, jlong attachment, jlong stream) {
    const auto session = lookup(handle, attachment, stream);
    if (!session) { error(env, "java/lang/IllegalArgumentException", "Stale silent transport handle"); return nullptr; }
    try { session->run(); } catch (...) { error(env, "java/lang/IllegalStateException", "Silent transport execution rejected"); return nullptr; }
    return snapshot(env, *session);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeStopSilentTransport(JNIEnv*, jobject, jlong handle, jlong attachment, jlong stream, jint reason) {
    const auto session = lookup(handle, attachment, stream);
    if (session) session->requestedStop.store(reason == 2 ? 2 : 1);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeSilentTransportIdle(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(registryMutex);
    return registry.empty() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeDestroySilentTransport(JNIEnv*, jobject, jlong handle, jlong attachment, jlong stream, jboolean connectionClosed) {
    std::lock_guard<std::mutex> lock(registryMutex);
    auto found = registry.find(handle);
    if (found == registry.end()) return JNI_FALSE;
    const auto& session = found->second;
    if (session->attachment != static_cast<uint64_t>(attachment) || session->stream != static_cast<uint64_t>(stream) || session->executing.load()) return JNI_FALSE;
    if (!connectionClosed && session->outstanding(false) + session->outstanding(true) != 0) return JNI_FALSE;
    // Close has synchronously destroyed all usbfs async work; only then may unreaped pools be freed.
    session->fd = -1; registry.erase(found); return JNI_TRUE;
}
