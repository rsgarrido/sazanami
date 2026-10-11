#pragma once
#include "usb_iso_core.h"
#include "usb_out_queue_policy.h"
#include <algorithm>

namespace sazanami::usbiso {
// Only supported HS feedback intervals (125..8000 us). One extra opportunity
// covers service-phase uncertainty; all URBs still contain one four-byte packet.
constexpr size_t kMaximumFeedbackUrbs = 161;
inline uint64_t feedbackHoldoverUs(uint64_t interval) noexcept {
    return std::max<uint64_t>(20000, interval * 4);
}
inline size_t feedbackUrbCount(uint64_t interval) noexcept {
    if (interval < 125 || interval > 8000) return 0;
    const auto count=(feedbackHoldoverUs(interval)+interval-1)/interval+1;
    return count>=kFeedbackPool && count<=kMaximumFeedbackUrbs ? static_cast<size_t>(count) : 0;
}
// Keep ordinal 3 reserved for the previous at-reap timeout evidence schema.
enum class FeedbackTimeoutKind : int { None, Bootstrap, LastValidWatchdog, ReapedObservationGap };
struct FeedbackObservationClock {
    uint64_t started=0, lastObserved=0, lastSubmitted=0;
    uint64_t observationAge(uint64_t now) const noexcept { return now-(lastObserved ? lastObserved : started); }
    FeedbackTimeoutKind expired(uint64_t now,uint64_t holdover) const noexcept {
        if (!lastObserved) return now-started>=kBootstrapUs ? FeedbackTimeoutKind::Bootstrap : FeedbackTimeoutKind::None;
        if (now-lastObserved<=holdover) return FeedbackTimeoutKind::None;
        return FeedbackTimeoutKind::LastValidWatchdog;
    }
    void accept(uint64_t submitted,uint64_t observed) noexcept {
        lastSubmitted=submitted; lastObserved=observed;
    }
    // Request residence time is not a kernel completion or DAC sample timestamp.
    static uint64_t requestAge(uint64_t submitted,uint64_t observed) noexcept { return observed-submitted; }
};

// No rearming inside this pass: each known outstanding slot can complete once.
// KA15 FLAC has 21 OUT + 21 feedback slots: at most 42 reaps, no rearm.
constexpr size_t kReadyReapLimit = std::max(kOutPool,kMaximumDirectOutUrbs) + kMaximumFeedbackUrbs;
struct FeedbackReapPass {
    bool attempted=false, refreshed=false, watchdogEvaluated=false;
    size_t reaped=0, feedbackCompletions=0;
    uint64_t ageBefore=0, ageAfter=0, evaluatedAt=0;
    FeedbackTimeoutKind timeout=FeedbackTimeoutKind::None;
};
// reap must be nonblocking and process both OUT and feedback through normal
// accounting. active prevents timeout selection after a different fatal cause.
template<class Reap,class Now,class Active>
FeedbackReapPass reapBeforeFeedbackWatchdog(const FeedbackObservationClock& clock,
        uint64_t holdover,uint64_t before,size_t slotCount,Reap reap,Now now,Active active) {
    FeedbackReapPass pass;
    pass.ageBefore=clock.observationAge(before);
    const auto previousObserved=clock.lastObserved;
    const auto limit=std::min(slotCount,kReadyReapLimit);
    pass.attempted=limit>0 && active();
    while (pass.reaped<limit && active() && reap()) ++pass.reaped;
    pass.evaluatedAt=now();
    pass.ageAfter=clock.observationAge(pass.evaluatedAt);
    pass.refreshed=clock.lastObserved!=previousObserved;
    pass.watchdogEvaluated=active();
    if (pass.watchdogEvaluated) pass.timeout=clock.expired(pass.evaluatedAt,holdover);
    return pass;
}
} // namespace sazanami::usbiso
