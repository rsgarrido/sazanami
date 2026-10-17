// Production policy/telemetry helpers with deterministic clocks; no USB or JNI.
#include "../../main/cpp/usb_live_diagnostics.h"
#include "../../main/cpp/usb_iso_stop.h"
#include <cassert>
using namespace sazanami::usbiso;

static void explicitDetachAndFirstCause() {
    for(bool feedback:{false,true}) {
        (void)feedback; // Both endpoint kinds use this predicate before generic handling.
        assert(completionIndicatesDetach(-ENODEV,0,false));
        assert(completionIndicatesDetach(0,-ENODEV,false));
        assert(completionIndicatesDetach(-EIO,-ENODEV,false));
        assert(!completionIndicatesDetach(-EIO,-EIO,false));
        assert(!completionIndicatesDetach(0,-EXDEV,false));
        for(int cancelledStatus:{-ENOENT,-ECONNRESET}) {
            assert(!completionIndicatesDetach(cancelledStatus,0,true));
            assert(!completionIndicatesDetach(cancelledStatus,-ENODEV,true));
        }
        StopCause detached;
        if(completionIndicatesDetach(0,-ENODEV,false)) detached.choose(Stop::Detached);
        detached.choose(feedback?Stop::Feedback:Stop::Worker);
        assert(detached.value==Stop::Detached);
        StopCause earlier;earlier.choose(Stop::Feedback);
        if(completionIndicatesDetach(-ENODEV,0,false)) earlier.choose(Stop::Detached);
        assert(earlier.value==Stop::Feedback);
        LiveDiagnostics d;
        d.explicitDetach(0,-ENODEV,9,1000,feedback);
        d.explicitDetach(-EIO,-ENODEV,10,2000,!feedback);
        assert(d.detach[0]==0 && d.detach[1]==-ENODEV && d.detach[2]==9 && d.detach[3]==1000 && d.detach[4]==feedback);
    }
}
static void refreshThenRealTimeoutAndProvenance() {
    LiveDiagnostics d;d.timings.boundary(100799,2,8);
    FeedbackObservationClock clock;clock.started=1000;clock.accept(90000,100000);
    uint64_t time=100799;unsigned calls=0;
    auto pass=reapBeforeFeedbackWatchdog(clock,20000,time,42,[&] {
        if(++calls==1) {
            d.timings.probe(100800,100900,0,0,2,8);
            time=101000;clock.accept(90000,time);
            FeedbackObservation row{};row[DSequence]=1;row[DRaw]=361264;
            d.correlate(row,9,90000,time,2,8);d.accepted(101030,9);return true;
        }
        d.timings.probe(101040,124300,-1,EAGAIN,2,8);time=124350;return false;
    },[&] {return time;},[] {return true;});
    pass.feedbackCompletions=1;
    assert(calls==2 && pass.ageBefore==799 && pass.refreshed && pass.ageAfter==23350);
    assert(pass.enteredAt==100799 && pass.evaluatedAt==124350 && pass.timeout==FeedbackTimeoutKind::LastValidWatchdog);
    d.watchdogPass(pass,100799,2,8,clock.lastObserved);
    d.timeoutEvidence(&pass,pass.evaluatedAt,pass.timeout);
    assert(d.haveFatalWatchdog && d.fatalWatchdog[5]==101000 && d.fatalWatchdog[6]==101030);
    assert(d.fatalWatchdog[12]-d.fatalWatchdog[9]==50); // Delay AFTER final syscall.
    assert(d.timings.phases[TReapSyscall][4]-d.timings.phases[TReapSyscall][3]==23260);
    assert(d.correlations.latest()[1]==9 && d.correlations.latest()[4]==101030);
    const auto evidence=timeoutReapEvidence(pass.timeout,pass.evaluatedAt,&pass);
    assert(evidence[0]==1 && evidence[1]==1 && evidence[2]==1 && evidence[3]==1 && evidence[4]==799 && evidence[5]==23350);
    // A direct post-seek bootstrap expiry cannot borrow this previous evaluation.
    for(const auto& absent:{timeoutReapEvidence(FeedbackTimeoutKind::Bootstrap,200000,nullptr),
                          timeoutReapEvidence(FeedbackTimeoutKind::Bootstrap,200000,&pass),
                          timeoutReapEvidence(pass.timeout,pass.evaluatedAt+1,&pass)})
        for(auto word:absent) assert(word==-1);
    auto notExpired=pass;notExpired.timeout=FeedbackTimeoutKind::None;
    assert(timeoutReapEvidence(FeedbackTimeoutKind::Bootstrap,notExpired.evaluatedAt,&notExpired)[0]==-1);
    d.timeoutEvidence(nullptr,200000,FeedbackTimeoutKind::Bootstrap);assert(!d.haveFatalWatchdog);
    LiveWords absent{};d.reliability(absent);assert(absent[kLiveReliabilityOffset+RFatalWatchdogPresent]==0);
    for(size_t i=kLiveFatalWatchdogOffset;i<kLiveWords;++i) assert(absent[i]==0);
    // Bootstrap policy and a valid active bootstrap evaluation are unchanged.
    FeedbackObservationClock fresh;fresh.started=100000;
    auto bootstrap=reapBeforeFeedbackWatchdog(fresh,20000,200000,42,[] {return false;},[] {return 200000ULL;},[] {return true;});
    assert(bootstrap.timeout==FeedbackTimeoutKind::Bootstrap);
    assert(timeoutReapEvidence(bootstrap.timeout,200000,&bootstrap)[0]==1);
    assert(fresh.expired(199999,20000)==FeedbackTimeoutKind::None);
}
static uint64_t fakeTime=1;
static uint64_t fakeNow() {return fakeTime;}
static void boundedTimingAndFrozenCorrelation() {
    LiveDiagnostics d;d.timings.boundary(10,0,0);fakeTime=20;
    {TimingScope scope(d.timings,TControlLock,fakeNow,0,0);fakeTime=10020;}
    assert(d.timings.events.count==1 && d.timings.phases[TControlLock][4]-d.timings.phases[TControlLock][3]==10000);
    for(unsigned i=0;i<40;++i) d.timings.span(TPoll,20000+i*10000,26000+i*10000,-EINTR,0,0);
    assert(d.timings.events.count==16 && d.timings.events.latest()[0]==41);
    d.timings.span(TPoll,500000,500100,0,0,0); // Short spans update maxima/count, not history.
    assert(d.timings.events.latest()[0]==41 && d.timings.phases[TPoll][0]==41);
    const auto frozen=d.timings.events.latest();d.timings.frozen=true;
    d.timings.span(TPoll,600000,630000,0,0,0);assert(d.timings.events.latest()==frozen);
    FeedbackObservation row{};row[DSequence]=1;row[DRaw]=379616;row[DRejection]=4;
    d.correlate(row,1,1,2,0,0);assert(!d.correlations.count);
    d.suspend();assert(!d.timings.iterationAt);d.timings.boundary(900000,1,7);
    assert(d.timings.phases[TIteration][0]==0); // Intentional quiet duration excluded.
    LiveDiagnostics rolling;
    for(int64_t i=1;i<=40;++i) {row[DSequence]=i;rolling.correlate(row,i,100+i,1000+i,0,0);}
    assert(rolling.correlations.count==16 && rolling.correlations.latest()[0]==40);
    LiveWords words{};rolling.reliability(words);assert(words[kLiveReliabilityOffset+RCorrelationCount]==16);
    assert(words[kLiveCorrelationOffset]==25 && words[kLiveCorrelationOffset+4]==-1);
}
static void immutableTerminalAndCollectionInterval() {
    PublishedLiveDiagnostics publisher;LiveWords live{};live[LVersion]=2;
    live[LCapturedAt]=101000;live[kLiveReliabilityOffset+RCollectionStarted]=70000;
    live[kLiveReliabilityOffset+RCollectionDuration]=31000;
    assert(publisher.tryPublish(live));assert(publisher.read()[kLiveReliabilityOffset+RCollectionDuration]==31000);
    auto terminal=live;terminal[LTerminal]=1;publisher.publishTerminal(terminal);
    auto later=terminal;later[LCapturedAt]=999999;publisher.publishTerminal(later);
    // Terminal write is immutable, and cannot be displaced by a late live publication.
    assert(!publisher.tryPublish(later));assert(publisher.read()[LCapturedAt]==101000 && publisher.read()[LTerminal]==1);
}
int main() {explicitDetachAndFirstCause();refreshThenRealTimeoutAndProvenance();boundedTimingAndFrozenCorrelation();immutableTerminalAndCollectionInterval();}
