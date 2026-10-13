#pragma once
#include "usb_iso_core.h"
#include <algorithm>

namespace sazanami::usbiso {
// USB snapshot v13 appends this fixed block after historical indices 0..235.
// Signed JNI words; -1 means unavailable. No clocks, allocation, locks or policy decisions here.
constexpr size_t kFeedbackHistoryCapacity=16, kFeedbackObservationWords=15, kFeedbackFatalWords=32;
constexpr size_t kFeedbackDiagnosticWords=kFeedbackFatalWords+kFeedbackHistoryCapacity*kFeedbackObservationWords;
enum FeedbackObservationField : size_t { DSequence, DElapsed, DRaw, DValidity, DRejection,
    DRequested, DActual, DUrbStatus, DPacketStatus, DAccepted, DRequestAge, DPhase, DEpoch, DSeekSerial, DUrbErrors };
enum class FeedbackRejection : int { None, Length, Zero, Reserved, Implausible, UrbStatus, PacketStatus, Quiet, Terminal };
enum class FeedbackFatalReason : int { PersistentInvalid=1, Malformed, QuietCompletion, BootstrapTimeout, WatchdogTimeout, ReapedTimeout, UrbCompletion };
enum FeedbackFatalField : size_t { FExists, FReason, FSequence, FFirstRejectedSequence, FRaw, FValidity, FStreak,
    FNominalRate, FPhase, FTransportState, FEpoch, FSeekSerial, FSinceSeek, FSinceOut, FObserved, FRequestAge,
    FOut, FFeedback, FBootstrap, FRefill, FNominalCoverage, FEstimatedCoverage, FBootstrapElapsed,
    FAdmissionStopped, FPoolsDrained, FSeekConfirmed, FFreshPrime, FOutResumed, FHistoryCount,
    FFirstRejectedElapsed, FAcceptedRaw, FTimeoutKind };
using FeedbackObservation=std::array<int64_t,kFeedbackObservationWords>;
using FeedbackFatalContext=std::array<int64_t,kFeedbackFatalWords>;
inline int64_t diagnosticElapsed(uint64_t origin,uint64_t observed) noexcept {
    if(!origin || observed<origin || observed-origin>static_cast<uint64_t>(INT64_MAX)) return -1;
    return static_cast<int64_t>(observed-origin);
}
struct FeedbackDiagnostics {
    std::array<FeedbackObservation,kFeedbackHistoryCapacity> rows{};
    FeedbackFatalContext fatal{};
    size_t next=0,count=0;
    int64_t sequence=0,firstRejectedSequence=-1,firstRejectedElapsed=-1;
    bool frozen() const noexcept {return fatal[FExists]==1;}
    void observe(FeedbackObservation row) noexcept {
        if(frozen() || sequence==INT64_MAX) return;
        row[DSequence]=++sequence;rows[next]=row;next=(next+1)%kFeedbackHistoryCapacity;
        count=std::min(count+1,kFeedbackHistoryCapacity);
    }
    const FeedbackObservation* latest() const noexcept {return count?&rows[(next+kFeedbackHistoryCapacity-1)%kFeedbackHistoryCapacity]:nullptr;}
    void rejected(unsigned streak) noexcept {
        if(!frozen() && streak==1 && latest()) {firstRejectedSequence=(*latest())[DSequence];firstRejectedElapsed=(*latest())[DElapsed];}
    }
    void recovered() noexcept {if(!frozen()) {firstRejectedSequence=firstRejectedElapsed=-1;}}
    void freeze(FeedbackFatalContext context) noexcept {
        if(frozen()) return;
        context[FExists]=1;context[FHistoryCount]=static_cast<int64_t>(count);fatal=context;
    }
    std::array<int64_t,kFeedbackDiagnosticWords> serialized() const noexcept {
        std::array<int64_t,kFeedbackDiagnosticWords> result{};
        std::copy(fatal.begin(),fatal.end(),result.begin());result[FHistoryCount]=static_cast<int64_t>(count);
        const auto first=(next+kFeedbackHistoryCapacity-count)%kFeedbackHistoryCapacity;
        for(size_t i=0;i<count;++i) std::copy(rows[(first+i)%kFeedbackHistoryCapacity].begin(),
            rows[(first+i)%kFeedbackHistoryCapacity].end(),result.begin()+kFeedbackFatalWords+i*kFeedbackObservationWords);
        return result;
    }
};
static_assert(FTimeoutKind+1==kFeedbackFatalWords && DUrbErrors+1==kFeedbackObservationWords);
static_assert(kFeedbackDiagnosticWords==272 && sizeof(FeedbackObservation)==120);
} // namespace sazanami::usbiso
