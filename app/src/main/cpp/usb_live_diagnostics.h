#pragma once
#include "usb_feedback_diagnostics.h"
#include "usb_feedback_policy.h"
#include <atomic>
#include <mutex>

namespace sazanami::usbiso {
// Independent v2 wire schema. The v1 prefix/row offsets remain fixed.
// All times are steady_clock microseconds; -1 is unavailable.
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
enum ReliabilityField : size_t {
    RCollectionStarted, RCollectionDuration, RIteration, RTimingEventCount, RCorrelationCount,
    RWatchdogPresent, RFatalWatchdogPresent, RUsbSpeed, RAcInterface, RAsInterface, RAlternate, RIdleAlternate,
    RDataEndpoint, RDataAttributes, RDataMaxPacket, RDataBInterval,
    RFeedbackEndpoint, RFeedbackAttributes, RFeedbackMaxPacket, RFeedbackBInterval,
    RDetachUrbStatus, RDetachPacketStatus, RDetachRequest, RDetachObserved, RDetachFeedback, RFieldCount
};
enum TimingPhase : size_t { TIteration, TControlStep, TControlLock, TReapPass, TReapSyscall,
    TFeedbackProcessing, TRefill, TSubmitSyscall, TPoll, TSnapshot, TPublication, TProgressPublication, TPhaseCount };
constexpr size_t kTimingCapacity=16,kTimingWords=9,kPhaseWords=6,kCorrelationCapacity=16,kCorrelationWords=9;
constexpr uint64_t kTimingEventThresholdUs=5000; // Observation filter, never a playback deadline.
// Watchdog: iteration, epoch, seek, loop timestamp, actual pass entry, last valid observation,
// acceptance publication, accepted request serial, final ioctl entry/return/result/errno,
// evaluation, loop-top age, evaluation age, reaped, feedback completions, refreshed, timeout.
constexpr size_t kWatchdogWords=19;
constexpr size_t kLiveReliabilityOffset=kLiveStartupOffset+kLiveStartupCapacity*kLiveStartupWords;
constexpr size_t kLivePhaseOffset=kLiveReliabilityOffset+RFieldCount;
constexpr size_t kLiveTimingOffset=kLivePhaseOffset+TPhaseCount*kPhaseWords;
constexpr size_t kLiveCorrelationOffset=kLiveTimingOffset+kTimingCapacity*kTimingWords;
constexpr size_t kLiveWatchdogOffset=kLiveCorrelationOffset+kCorrelationCapacity*kCorrelationWords;
constexpr size_t kLiveFatalWatchdogOffset=kLiveWatchdogOffset+kWatchdogWords;
constexpr size_t kLiveWords=kLiveFatalWatchdogOffset+kWatchdogWords;
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

// Worker-only, fixed storage. Durations are wall time, including any preemption.
// A lock-acquisition span cannot by itself separate contention from descheduling.
struct WorkerTimings {
    // Per phase: count, last begin/end, maximum begin/end, result at maximum.
    std::array<std::array<int64_t,kPhaseWords>,TPhaseCount> phases{};
    DiagnosticHistory<kTimingCapacity,kTimingWords> events;
    std::array<int64_t,4> lastProbe{}; // begin, end, syscall return, errno
    uint64_t iteration=0,iterationAt=0;
    int64_t sequence=0;
    bool frozen=false;
    void span(TimingPhase phase,uint64_t begin,uint64_t end,int64_t result,uint64_t epoch,uint64_t seek,uint64_t request=0) noexcept {
        if(end<begin || end>static_cast<uint64_t>(INT64_MAX)) return;
        auto& p=phases[phase];if(p[0]<INT64_MAX) ++p[0];p[1]=begin;p[2]=end;
        if(p[0]==1 || end-begin>static_cast<uint64_t>(p[4]-p[3])) {p[3]=begin;p[4]=end;p[5]=result;}
        if(frozen || !iteration || end-begin<kTimingEventThresholdUs || sequence==INT64_MAX) return;
        events.add({++sequence,static_cast<int64_t>(iteration),static_cast<int64_t>(phase),static_cast<int64_t>(begin),
            static_cast<int64_t>(end),result,static_cast<int64_t>(epoch),static_cast<int64_t>(seek),static_cast<int64_t>(request)});
    }
    void boundary(uint64_t at,uint64_t epoch,uint64_t seek) noexcept {
        if(iteration<static_cast<uint64_t>(INT64_MAX)) ++iteration;
        if(iterationAt) span(TIteration,iterationAt,at,0,epoch,seek);
        iterationAt=at;
    }
    void probe(uint64_t begin,uint64_t end,int returned,int saved,uint64_t epoch,uint64_t seek) noexcept {
        lastProbe={{static_cast<int64_t>(begin),static_cast<int64_t>(end),returned,saved}};
        span(TReapSyscall,begin,end,returned<0?-saved:returned,epoch,seek);
    }
};
struct TimingScope {
    WorkerTimings& timings;TimingPhase phase;uint64_t (*now)();
    const uint64_t begin,epoch,seek,request;
    TimingScope(WorkerTimings& t,TimingPhase p,uint64_t (*clock)(),uint64_t e,uint64_t s,uint64_t r=0)
        :timings(t),phase(p),now(clock),begin(clock()),epoch(e),seek(s),request(r) {}
    ~TimingScope() {timings.span(phase,begin,now(),0,epoch,seek,request);}
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
    WorkerTimings timings;
    // Observation sequence, submitted request serial, submission/observation/acceptance
    // times, raw LE32 (-1 if unavailable), rejection, source epoch, active seek.
    DiagnosticHistory<kCorrelationCapacity,kCorrelationWords> correlations;
    std::array<int64_t,kWatchdogWords> watchdog{},fatalWatchdog{};
    bool haveWatchdog=false,haveFatalWatchdog=false;
    uint64_t lastAcceptedAt=0,lastAcceptedRequest=0;
    std::array<int64_t,5> detach{}; // First explicit removal completion; independent of first stop cause.
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
    void suspend() noexcept { lastWake=submissionGapBase=timings.iterationAt=0; }
    void correlate(const FeedbackObservation& row,uint64_t request,uint64_t submitted,uint64_t observed,uint64_t epoch,uint64_t seek) noexcept {
        if(timings.frozen) return;
        correlations.add({row[DSequence],static_cast<int64_t>(request),static_cast<int64_t>(submitted),
            static_cast<int64_t>(observed),-1,row[DRaw],row[DRejection],static_cast<int64_t>(epoch),static_cast<int64_t>(seek)});
    }
    void accepted(uint64_t at,uint64_t request) noexcept {
        lastAcceptedAt=at;lastAcceptedRequest=request;
        if(!timings.frozen && correlations.count) correlations.latest()[4]=static_cast<int64_t>(at);
    }
    void watchdogPass(const FeedbackReapPass& pass,uint64_t loop,uint64_t epoch,uint64_t seek,uint64_t observed) noexcept {
        if(timings.frozen) return;
        const auto& p=timings.lastProbe;
        watchdog={{static_cast<int64_t>(timings.iteration),static_cast<int64_t>(epoch),static_cast<int64_t>(seek),
            static_cast<int64_t>(loop),static_cast<int64_t>(pass.enteredAt),static_cast<int64_t>(observed),
            static_cast<int64_t>(lastAcceptedAt),static_cast<int64_t>(lastAcceptedRequest),p[0],p[1],p[2],p[3],
            static_cast<int64_t>(pass.evaluatedAt),static_cast<int64_t>(pass.ageBefore),static_cast<int64_t>(pass.ageAfter),
            static_cast<int64_t>(pass.reaped),static_cast<int64_t>(pass.feedbackCompletions),pass.refreshed,
            pass.watchdogEvaluated?static_cast<int64_t>(pass.timeout):-1}};
        haveWatchdog=true;
    }
    void timeoutEvidence(const FeedbackReapPass* pass,uint64_t observed,FeedbackTimeoutKind kind) noexcept {
        // The current row must identify the same evaluation, never a prior seek/pass.
        haveFatalWatchdog=timeoutReapEvidence(kind,observed,pass)[0]>=0 && haveWatchdog && watchdog[12]==static_cast<int64_t>(observed);
        if(haveFatalWatchdog) fatalWatchdog=watchdog;
    }
    void explicitDetach(int urb,int packet,uint64_t request,uint64_t observed,bool feedback) noexcept {
        if(!detach[2]) detach={{urb,packet,static_cast<int64_t>(request),static_cast<int64_t>(observed),feedback}};
    }
    void reliability(LiveWords& v) const noexcept {
        v[kLiveReliabilityOffset+RIteration]=timings.iteration;
        v[kLiveReliabilityOffset+RTimingEventCount]=timings.events.count;
        v[kLiveReliabilityOffset+RCorrelationCount]=correlations.count;
        v[kLiveReliabilityOffset+RWatchdogPresent]=haveWatchdog;
        v[kLiveReliabilityOffset+RFatalWatchdogPresent]=haveFatalWatchdog;
        std::copy(detach.begin(),detach.end(),v.begin()+kLiveReliabilityOffset+RDetachUrbStatus);
        for(size_t i=0;i<TPhaseCount;++i) std::copy(timings.phases[i].begin(),timings.phases[i].end(),v.begin()+kLivePhaseOffset+i*kPhaseWords);
        timings.events.copyTo(v.data()+kLiveTimingOffset);correlations.copyTo(v.data()+kLiveCorrelationOffset);
        if(haveWatchdog) std::copy(watchdog.begin(),watchdog.end(),v.begin()+kLiveWatchdogOffset);
        if(haveFatalWatchdog) std::copy(fatalWatchdog.begin(),fatalWatchdog.end(),v.begin()+kLiveFatalWatchdogOffset);
    }
};

// Two bounded slots, one writer. Serialized readers hold at most one slot.
// Active publication NEVER waits: try the other slot, then the current one. A reader copies a
// complete value under its slot lock, then releases it before JNI allocation/formatting.
// PCM atomics are sampled over the reported collection interval, not one producer-wide transaction.
class PublishedLiveDiagnostics {
    struct Slot { mutable std::mutex mutex;LiveWords words{}; };
    std::array<Slot,2> slots;
    mutable std::mutex readers;
    std::atomic<unsigned> current{0};
    LiveWords terminal{};
    std::atomic<bool> terminalReady{false}; // One immutable terminal write, no diagnostic lock acquisition.
public:
    bool tryPublish(const LiveWords& value) {
        if(terminalReady.load(std::memory_order_relaxed)) return false;
        const unsigned other=1-current.load(std::memory_order_relaxed);
        for(unsigned index:{other,1-other}) {
            std::unique_lock<std::mutex> lock(slots[index].mutex,std::try_to_lock);
            if(!lock.owns_lock()) continue;
            slots[index].words=value;current.store(index,std::memory_order_release);return true;
        }
        return false; // try_lock may also fail spuriously; retain the previous complete publication.
    }
    void publishTerminal(const LiveWords& value) {
        if(terminalReady.load(std::memory_order_relaxed)) return;
        terminal=value;terminalReady.store(true,std::memory_order_release);
    }
    LiveWords read() const {
        if(terminalReady.load(std::memory_order_acquire)) return terminal;
        std::lock_guard<std::mutex> oneReader(readers);
        const auto index=current.load(std::memory_order_acquire);
        std::lock_guard<std::mutex> lock(slots[index].mutex);return slots[index].words;
    }
};
static_assert(std::atomic<unsigned>::is_always_lock_free);
static_assert(std::atomic<bool>::is_always_lock_free);
static_assert(LFieldCount==71 && SActive+1==kLiveStartupWords);
static_assert(RFieldCount==25 && TPhaseCount==12 && kLiveWords==1094);
static_assert(sizeof(LiveWords)<9000);
} // namespace sazanami::usbiso
