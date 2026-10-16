#pragma once
#include "usb_feedback_diagnostics.h"
#include <atomic>
#include <mutex>

namespace sazanami::usbiso {
// Independent v1 wire schema. All times are steady_clock microseconds; -1 is unavailable.
// No resource pointers, strings, allocation, or transport/control locks in this block.
enum LiveField : size_t {
    LVersion, LAttachment, LStream, LEpoch, LSeekSerial, LCapturedAt, LReadAt, LTerminal,
    LTransportState, LPlaybackPhase, LRate, LChannels, LBits, LFrameBytes, LStartedAt,
    LOutSubmitted, LOutCompleted, LOutOutstanding, LOutPool, LOutFree, LOutPendingFrames,
    LOutPayloadDuration, LEstimatedCoverage, LNominalCoverage, LLastSubmitAge, LLastCompletionAge,
    LMaxSubmitGap, LMaxBatchGap, LMaxCompletionGap, LMaxWorkerGap, LMaxRefillGap, LMaxRefillLatency,
    LPoolStalls, LEmptyAfterReap, LOutUrbErrors, LOutPacketErrors, LCancelled, LSubmitErrno, LReapErrno,
    LPcmDepth, LPcmCapacity, LPcmMinDepth, LPcmEmpty, LPcmUnderruns, LProducerFullWaits, LPcmPrepared,
    LSourceFaults, LWaitingForEof, LFeedbackValid, LFeedbackInvalid, LInvalidStreak, LAcceptedRaw,
    LFeedbackOutstanding, LFeedbackLastAcceptedAge, LFeedbackHoldover, LFeedbackTimeouts, LFeedbackErrors,
    LFeedbackLastObservationAge, LRefillDemand, LIntegrity, LStopReason, LGapCount, LStartupCount, LRejectedCount, LFeedbackRecoveredGaps,
    LPcmShortageDepth, LPcmShortageRequested, LPcmShortageAge,
    LFeedbackAcceptedDecisions, LFeedbackRejectedDecisions, LFeedbackIgnoredDecisions,
    LFieldCount
};
constexpr size_t kLiveGapCapacity=16, kLiveGapWords=8, kLiveStartupCapacity=8, kLiveStartupWords=10;
constexpr size_t kLiveRejectedCapacity=8;
// Gaps: sequence, session elapsed us, kind (1=worker wake,2=OUT submission), gap us,
// application OUT outstanding, PCM depth (frames), source epoch, active seek serial.
// Startups: epoch, seek serial, monotonic origin us, target frame, metadata ready us,
// first PCM published us, prebuffer ready us, first OUT submit us, first successful OUT reap us,
// native Playing observed us. Milestones are durations from that row's origin.
enum StartupField : size_t { SEpoch, SSerial, SOrigin, STarget, SMetadata, SPcm, SReady, SSubmit, SComplete, SActive };
constexpr size_t kLiveFeedbackOffset=LFieldCount;
constexpr size_t kLiveRejectedOffset=kLiveFeedbackOffset+kFeedbackDiagnosticWords;
constexpr size_t kLiveGapOffset=kLiveRejectedOffset+kLiveRejectedCapacity*kFeedbackObservationWords;
constexpr size_t kLiveStartupOffset=kLiveGapOffset+kLiveGapCapacity*kLiveGapWords;
constexpr size_t kLiveWords=kLiveStartupOffset+kLiveStartupCapacity*kLiveStartupWords;
using LiveWords=std::array<int64_t,kLiveWords>;

template<size_t Capacity,size_t Width> struct DiagnosticHistory {
    using Row=std::array<int64_t,Width>;
    std::array<Row,Capacity> rows{};
    size_t next=0,count=0;
    void add(const Row& row) noexcept { rows[next]=row;next=(next+1)%Capacity;count=std::min(count+1,Capacity); }
    Row& latest() noexcept { return rows[(next+Capacity-1)%Capacity]; }
    void copyTo(int64_t* destination) const noexcept {
        const auto first=(next+Capacity-count)%Capacity;
        for(size_t i=0;i<count;++i) std::copy(rows[(first+i)%Capacity].begin(),rows[(first+i)%Capacity].end(),destination+i*Width);
    }
};

inline int64_t payloadDurationUs(uint64_t frames,uint32_t rate) noexcept {
    if(!rate || frames>static_cast<uint64_t>(INT64_MAX)/1000000) return -1;
    return static_cast<int64_t>(frames*1000000/rate);
}

// Reads only worker-owned identity/packet-frame bookkeeping, never kernel-mutated URB descriptors.
template<class Slots> void captureOutPool(LiveWords& words,const Slots& slots,size_t capacity,uint32_t rate,
                                        int64_t submitted,int64_t completed) noexcept {
    uint64_t outstanding=0,pendingFrames=0;
    for(const auto& slot:slots) if(!slot.feedback && slot.identity.outstanding) {
        ++outstanding;
        for(unsigned i=0;i<slot.packets;++i) pendingFrames+=slot.frames[i];
    }
    words[LOutSubmitted]=submitted;words[LOutCompleted]=completed;words[LOutOutstanding]=static_cast<int64_t>(outstanding);
    words[LOutPool]=static_cast<int64_t>(capacity);words[LOutFree]=static_cast<int64_t>(capacity)-static_cast<int64_t>(outstanding);
    words[LOutPendingFrames]=static_cast<int64_t>(pendingFrames);words[LOutPayloadDuration]=payloadDurationUs(pendingFrames,rate);
}

// Single worker writer. Only observations missing from the established counters live here.
struct LiveDiagnostics {
    DiagnosticHistory<kLiveGapCapacity,kLiveGapWords> gaps;
    DiagnosticHistory<kLiveStartupCapacity,kLiveStartupWords> startups;
    // Sparse rejected observations survive normal feedback churn in the existing recent-16 ring.
    DiagnosticHistory<kLiveRejectedCapacity,kFeedbackObservationWords> rejected;
    uint64_t lastSubmit=0,lastCompletion=0,lastWake=0,submissionGapBase=0;
    uint64_t feedbackAccepted=0,feedbackRejected=0,feedbackIgnored=0;
    int64_t gapSequence=0;
    void gap(uint64_t started,uint64_t at,int kind,uint64_t duration,uint64_t out,int64_t pcm,uint64_t epoch,uint64_t serial) noexcept {
        if(gapSequence==INT64_MAX) return;
        gaps.add({++gapSequence,diagnosticElapsed(started,at),kind,static_cast<int64_t>(duration),
            static_cast<int64_t>(out),pcm,static_cast<int64_t>(epoch),static_cast<int64_t>(serial)});
    }
    void beginSource(uint64_t epoch,uint64_t serial,uint64_t at,uint64_t target) noexcept {
        DiagnosticHistory<kLiveStartupCapacity,kLiveStartupWords>::Row row{};row.fill(-1);
        row[SEpoch]=static_cast<int64_t>(epoch);row[SSerial]=static_cast<int64_t>(serial);
        row[SOrigin]=static_cast<int64_t>(at);row[STarget]=static_cast<int64_t>(target);startups.add(row);
    }
    void milestone(StartupField field,uint64_t at) noexcept {
        if(startups.count && at && startups.latest()[field]<0)
            startups.latest()[field]=diagnosticElapsed(static_cast<uint64_t>(startups.latest()[SOrigin]),at);
    }
    // Counts recorded decisions, unlike historical FeedbackValid which also counts
    // valid terminal drain packets that never select a scheduling rate. Stops with recorder freeze.
    void feedback(const FeedbackObservation& row) noexcept {
        if(row[DAccepted]) ++feedbackAccepted;
        else if(row[DRejection]>=1 && row[DRejection]<=6) {++feedbackRejected;rejected.add(row);}
        else ++feedbackIgnored;
    }
    // Intentional pause/reposition intervals are excluded from scheduling gap history.
    void suspend() noexcept { lastWake=submissionGapBase=0; }
};

// Two bounded slots, one writer. Serialized readers hold at most one slot.
// Active publication NEVER waits: try the other slot, then the current one. A reader copies a
// complete value under its slot lock, then releases it before JNI allocation/formatting.
// PCM atomics are sampled by the worker over a bounded interval, not one producer-wide transaction.
class PublishedLiveDiagnostics {
    struct Slot { mutable std::mutex mutex;LiveWords words{}; };
    std::array<Slot,2> slots;
    mutable std::mutex readers;
    std::atomic<unsigned> current{0};
public:
    bool tryPublish(const LiveWords& value) {
        const unsigned other=1-current.load(std::memory_order_relaxed);
        for(unsigned index:{other,1-other}) {
            std::unique_lock<std::mutex> lock(slots[index].mutex,std::try_to_lock);
            if(!lock.owns_lock()) continue;
            slots[index].words=value;current.store(index,std::memory_order_release);return true;
        }
        return false; // try_lock may also fail spuriously; retain the previous complete publication.
    }
    // Only after shutdown, or fully drained PAUSED before its indefinite wait.
    // No audio is in flight in PAUSED; readers hold a slot only for the fixed copy.
    void publishQuiescent(const LiveWords& value) {
        const auto index=1-current.load(std::memory_order_relaxed);
        std::lock_guard<std::mutex> lock(slots[index].mutex);
        slots[index].words=value;current.store(index,std::memory_order_release);
    }
    LiveWords read() const {
        std::lock_guard<std::mutex> oneReader(readers);
        const auto index=current.load(std::memory_order_acquire);
        std::lock_guard<std::mutex> lock(slots[index].mutex);return slots[index].words;
    }
};
static_assert(std::atomic<unsigned>::is_always_lock_free);
static_assert(LFieldCount==71 && SActive+1==kLiveStartupWords);
static_assert(sizeof(LiveWords)<6000);
} // namespace sazanami::usbiso
