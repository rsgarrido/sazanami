// Pure policy/finite-request model tests. Authored only; NOT compiled or executed by Codex.
#include "../../main/cpp/usb_feedback_policy.h"
#include <cassert>
#include <deque>
#include <vector>
using namespace sazanami::usbiso;
const size_t kKa15ReadySlots=outUrbCount(125,3)+feedbackUrbCount(1000);

static void geometryAndBounds() {
    static_assert(kOutPool==8 && kOutPackets==8); // Legacy OUT and packet geometry remain unchanged.
    assert(kKa15ReadySlots==42 && kReadyReapLimit==182);
    assert(feedbackUrbCount(1000)==21);
    assert(feedbackUrbCount(1000)*1000==21000);
    assert(feedbackHoldoverUs(1000)==20000);
    assert(feedbackUrbCount(125)==161 && feedbackUrbCount(8000)==5);
    assert(!feedbackUrbCount(0) && !feedbackUrbCount(124) && !feedbackUrbCount(8001));
    for (int interval=1;interval<=7;++interval) {
        const auto tick=intervalUs(interval), coverage=feedbackUrbCount(tick)*tick;
        assert(coverage>feedbackHoldoverUs(tick));
        assert(coverage<=feedbackHoldoverUs(tick)+tick);
        assert(feedbackUrbCount(tick)<=kMaximumFeedbackUrbs);
    }
    assert(allocationBytes(64,12,1)==76); // One descriptor per feedback request.
}

// Models finite one-packet requests, not a device/kernel implementation. A completed
// request stays outstanding to userspace until reap, but collects no more samples.
struct FiniteFeedbackQueue {
    std::vector<Identity> ids;
    std::deque<size_t> armed, completed;
    uint64_t serial=0;
    explicit FiniteFeedbackQueue(size_t count):ids(count) {
        for(size_t i=0;i<count;++i) rearm(i);
    }
    void rearm(size_t i) { assert(ids[i].submit(1,2,++serial));armed.push_back(i); }
    void service(size_t intervals) {
        while(intervals-- && !armed.empty()) { completed.push_back(armed.front());armed.pop_front(); }
    }
    void reapAndRearm() {
        while(!completed.empty()) { const auto i=completed.front();completed.pop_front();assert(ids[i].complete(1,2));rearm(i); }
    }
    void drain() {
        while(!armed.empty()) { const auto i=armed.front();armed.pop_front();ids[i].discardRequested=true;completed.push_back(i); }
        while(!completed.empty()) { const auto i=completed.front();completed.pop_front();assert(ids[i].complete(1,2)); }
        for(const auto& id:ids) assert(!id.outstanding);
    }
};
static void delayedReapCoverageAndDrain() {
    FiniteFeedbackQueue old(2);old.service(19);
    assert(old.completed.size()==2 && old.armed.empty());
    old.service(10);assert(old.completed.size()==2); // no automatic recurring transaction
    FiniteFeedbackQueue current(feedbackUrbCount(1000));current.service(19);
    assert(current.completed.size()==19 && current.armed.size()==2);
    for(const auto& id:current.ids) assert(id.outstanding); // cannot infer kernel-armed from this flag
    current.reapAndRearm();assert(current.armed.size()==21 && current.completed.empty());
    current.service(1);current.reapAndRearm();assert(current.armed.size()==21);
    current.drain();old.drain();
}
static void observedClockAndBoundedFailures() {
    FeedbackObservationClock clock;clock.started=1000;
    assert(clock.expired(100999,20000)==FeedbackTimeoutKind::None);
    assert(clock.expired(101000,20000)==FeedbackTimeoutKind::Bootstrap);
    clock.accept(2000,19278);
    assert(clock.lastSubmitted==2000 && clock.lastObserved==19278);
    assert(clock.expired(39278,20000)==FeedbackTimeoutKind::None);
    assert(clock.expired(39279,20000)==FeedbackTimeoutKind::LastValidWatchdog);
    // Deeply queued request residence can exceed holdover with a recent valid observation.
    clock.accept(100000,121000);
    assert(FeedbackObservationClock::requestAge(100000,121000)==21000);
    assert(clock.expired(121001,20000)==FeedbackTimeoutKind::None);
    clock.accept(110000,140000);
    assert(clock.expired(160000,20000)==FeedbackTimeoutKind::None);
    assert(clock.expired(160001,20000)==FeedbackTimeoutKind::LastValidWatchdog);
}

// Uses the production ordering helper, clock, parser and identity accounting.
// Available completions are simulated; this does not assert kernel timing.
enum class ReadyKind { Out, Valid, Invalid, UrbError, PacketError, ReapError, Malformed };
struct ReadyModel {
    FeedbackObservationClock clock;
    std::deque<ReadyKind> ready;
    std::vector<Identity> pending=std::vector<Identity>(kKa15ReadySlots);
    uint64_t now=80103555650ULL; // Exactly 20.455 ms since the last observation.
    size_t index=0, feedback=0, outFrames=0, calls=0;
    bool fatal=false;
    ReadyModel() {
        clock.started=80000000000ULL;
        clock.accept(80103514220ULL,80103535195ULL);
        for (size_t i=0;i<pending.size();++i) assert(pending[i].submit(1,2,i+1));
    }
    bool reap() {
        ++calls;
        if (ready.empty()) return false;
        const auto kind=ready.front();ready.pop_front();
        if (kind==ReadyKind::ReapError) { fatal=true;return false; }
        assert(pending[index++].complete(1,2));
        if (kind==ReadyKind::Out) { outFrames+=44;return true; }
        ++feedback;
        if (kind==ReadyKind::UrbError) { fatal=true;return true; }
        const std::array<uint8_t,4> valid{0x30,0x83,0x05,0},invalid{0,0,0,0};
        const auto decoded=decodeFeedback(kind==ReadyKind::Invalid ? invalid.data() : valid.data(),
            kind==ReadyKind::Malformed ? 3 : 4,kind==ReadyKind::PacketError ? -18 : 0);
        if (decoded.validity==FeedbackValidity::CompletionError || decoded.validity==FeedbackValidity::Length) fatal=true;
        if (!fatal && decoded.validity==FeedbackValidity::Valid) clock.accept(80103514220ULL,now);
        return true;
    }
    FeedbackReapPass pass() {
        return reapBeforeFeedbackWatchdog(clock,20000,now,pending.size(),
            [this] { return reap(); },[this] { return now; },[this] { return !fatal; });
    }
};
static void readyWorkPrecedesWatchdog() {
    ReadyModel recovery;recovery.ready={ReadyKind::Out,ReadyKind::Valid,ReadyKind::Out,ReadyKind::Valid};
    assert(recovery.pending[1].outstanding);
    const auto recovered=recovery.pass();
    assert(recovered.attempted && recovered.reaped==4 && recovered.refreshed);
    assert(recovered.ageBefore==20455 && recovered.ageAfter==0);
    assert(recovered.watchdogEvaluated && recovered.timeout==FeedbackTimeoutKind::None);
    assert(recovery.clock.lastObserved==80103555650ULL && recovery.clock.lastSubmitted==80103514220ULL);
    assert(recovery.feedback==2 && recovery.outFrames==88); // Mixed batch counts OUT normally.
    assert(!recovery.pending[0].outstanding && !recovery.pending[3].outstanding);
    assert(FeedbackObservationClock::requestAge(recovery.clock.lastSubmitted,recovery.now)==41430);
    ReadyModel absent;const auto empty=absent.pass();
    assert(empty.attempted && empty.reaped==0 && !empty.refreshed);
    assert(empty.ageAfter==20455 && empty.timeout==FeedbackTimeoutKind::LastValidWatchdog);
    ReadyModel onlyOut;onlyOut.ready={ReadyKind::Out};
    assert(onlyOut.pass().timeout==FeedbackTimeoutKind::LastValidWatchdog && onlyOut.outFrames==44);
    ReadyModel invalid;invalid.ready={ReadyKind::Invalid};const auto rejected=invalid.pass();
    assert(!rejected.refreshed && invalid.clock.lastObserved==80103535195ULL);
    assert(rejected.timeout==FeedbackTimeoutKind::LastValidWatchdog);
    ReadyModel slowPass;slowPass.ready={ReadyKind::Valid};
    const auto expiredAgain=reapBeforeFeedbackWatchdog(slowPass.clock,20000,slowPass.now,kKa15ReadySlots,
        [&] { return slowPass.reap(); },[&] { return slowPass.now+20001; },[&] { return !slowPass.fatal; });
    assert(expiredAgain.refreshed && expiredAgain.ageAfter==20001);
    assert(expiredAgain.timeout==FeedbackTimeoutKind::LastValidWatchdog); // New time sample still enforces holdover.
    for (const auto kind : {ReadyKind::UrbError,ReadyKind::PacketError,ReadyKind::ReapError,ReadyKind::Malformed}) {
        ReadyModel error;error.ready={kind,ReadyKind::Valid};const auto failed=error.pass();
        assert(error.fatal && !failed.refreshed && !failed.watchdogEvaluated);
        assert(failed.timeout==FeedbackTimeoutKind::None); // Existing real error takes precedence.
        assert(error.ready.size()==1); // Stop bounded work immediately after the error.
    }
}
static void boundedPassAndBootstrap() {
    FeedbackObservationClock clock;clock.started=1000;
    auto passAt=[&](uint64_t time) { return reapBeforeFeedbackWatchdog(clock,20000,time,kKa15ReadySlots,
        [] { return false; },[time] { return time; },[] { return true; }); };
    assert(passAt(100999).timeout==FeedbackTimeoutKind::None);
    assert(passAt(101000).timeout==FeedbackTimeoutKind::Bootstrap);
    bool available=true;
    const auto firstValid=reapBeforeFeedbackWatchdog(clock,20000,101000,kKa15ReadySlots,
        [&] { if (!available) return false;available=false;clock.accept(1000,101000);return true; },
        [] { return 101000ULL; },[] { return true; });
    assert(firstValid.refreshed && firstValid.timeout==FeedbackTimeoutKind::None);
    clock=FeedbackObservationClock{};clock.started=1000;
    size_t calls=0;
    const auto bounded=reapBeforeFeedbackWatchdog(clock,20000,101000,kKa15ReadySlots,
        [&] { ++calls;return true; },[] { return 101000ULL; },[] { return true; });
    assert(calls==kKa15ReadySlots && bounded.reaped==kKa15ReadySlots && bounded.timeout==FeedbackTimeoutKind::Bootstrap);
    calls=0;
    const auto capped=reapBeforeFeedbackWatchdog(clock,20000,101000,9999,
        [&] { ++calls;return true; },[] { return 101000ULL; },[] { return true; });
    assert(calls==182 && capped.reaped==kReadyReapLimit);
    const auto stopped=reapBeforeFeedbackWatchdog(clock,20000,101000,kKa15ReadySlots,
        [&] { ++calls;return true; },[] { return 101000ULL; },[] { return false; });
    assert(!stopped.attempted && !stopped.watchdogEvaluated && stopped.reaped==0 && calls==182);
}
static void validationAndSchedulerEquivalence() {
    const std::array<uint8_t,4> sample{0x33,0x83,0x05,0};
    const auto valid=decodeFeedback(sample.data(),sample.size(),0);
    assert(valid.validity==FeedbackValidity::Valid);
    assert(decodeFeedback(sample.data(),3,0).validity==FeedbackValidity::Length);
    assert(decodeFeedback(sample.data(),4,-18).validity==FeedbackValidity::CompletionError);
    const std::array<size_t,3> lengths{4,3,4};
    const std::array<FeedbackValidity,3> expected{FeedbackValidity::Valid,FeedbackValidity::Length,FeedbackValidity::Valid};
    for(size_t i=0;i<lengths.size();++i) assert(decodeFeedback(sample.data(),lengths[i],0).validity==expected[i]);
    const std::array<uint8_t,4> reserved{255,255,255,255};
    assert(decodeFeedback(reserved.data(),4,0).validity==FeedbackValidity::Reserved);
    Scheduler before,after;uint32_t a=0,b=0;
    FeedbackObservationClock clock;clock.started=1;
    for(unsigned i=0;i<8000;++i) {
        clock.accept(1,1000+i*1000ULL);
        assert(before.next(1,valid.raw,a) && after.next(1,valid.raw,b));
        assert(a==b && before.remainder==after.remainder && before.frames==after.frames);
    }
}
int main() {
    geometryAndBounds();delayedReapCoverageAndDrain();observedClockAndBoundedFailures();
    readyWorkPrecedesWatchdog();boundedPassAndBootstrap();validationAndSchedulerEquivalence();
}
