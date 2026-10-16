// Standalone C++17 CPU suite. No USB, libFLAC linkage or Gradle integration.
#include "../../main/cpp/usb_feedback_diagnostics.h"
#include "../../main/cpp/usb_live_diagnostics.h"
#include "../../main/cpp/usb_iso_stop.h"
#include <cassert>
#include <memory>
#include <thread>
#include <vector>
using namespace sazanami::usbiso;
static FeedbackObservation row(uint32_t raw,uint64_t at,int phase=1,int64_t epoch=0,int64_t serial=0) {
    std::array<uint8_t,4> bytes{};for(unsigned i=0;i<4;++i) bytes[i]=raw>>(8*i);
    const auto decoded=decodeFeedback(bytes.data(),4,0,44100);
    const auto rejected=decoded.validity==FeedbackValidity::Valid?0:decoded.validity==FeedbackValidity::Zero?2:4;
    return {0,static_cast<int64_t>(at),raw,static_cast<int>(decoded.validity),rejected,4,4,0,0,rejected==0,1000,phase,epoch,serial,0};
}
static FeedbackFatalContext context(const FeedbackDiagnostics& d,unsigned streak,uint64_t observed) {
    const auto& r=*d.latest();FeedbackFatalContext f{};
    f[FReason]=static_cast<int>(FeedbackFatalReason::PersistentInvalid);f[FSequence]=r[DSequence];
    f[FFirstRejectedSequence]=d.firstRejectedSequence;f[FFirstRejectedElapsed]=d.firstRejectedElapsed;
    f[FRaw]=r[DRaw];f[FValidity]=r[DValidity];f[FStreak]=streak;f[FNominalRate]=44100;
    f[FPhase]=r[DPhase];f[FTransportState]=4;f[FEpoch]=r[DEpoch];f[FSeekSerial]=r[DSeekSerial];
    f[FObserved]=observed;f[FSinceSeek]=diagnosticElapsed(2000,observed);f[FSinceOut]=diagnosticElapsed(3000,observed);
    f[FRequestAge]=r[DRequestAge];f[FOut]=21;f[FFeedback]=20;f[FBootstrap]=0;f[FRefill]=1;
    f[FNominalCoverage]=21000;f[FEstimatedCoverage]=12150;f[FSeekConfirmed]=2000;f[FOutResumed]=3000;
    return f;
}
static void rollingAndZeroEvidence() {
    FeedbackDiagnostics d;auto empty=d.serialized();for(auto word:empty) assert(word==0);
    for(unsigned i=0;i<40;++i) d.observe(row(361264,1000+i));
    auto data=d.serialized();assert(data[FExists]==0 && data[FHistoryCount]==16 && d.count==16);
    for(unsigned i=0;i<16;++i) {const auto offset=kFeedbackFatalWords+i*kFeedbackObservationWords;
        assert(data[offset+DSequence]==25+i && data[offset+DElapsed]==1024+i && data[offset+DAccepted]==1);}
    assert(sizeof(d)<2300); // Fixed footprint, independent of forty or forty million observations.
}
static void recoverThenFreezeAtThirdNotEleventh() {
    FeedbackDiagnostics d;unsigned streak=0;StopCause stop;
    auto sample=[&](uint32_t raw,uint64_t at) {
        auto observation=row(raw,at,1,6,9);d.observe(observation);
        if(observation[DAccepted]) {if(!stop.chosen) {streak=0;d.recovered();}}
        else {++streak;d.rejected(streak);if(streak>=kInvalidFeedbackLimit && !stop.chosen) {d.freeze(context(d,streak,at));stop.choose(Stop::Feedback);}}
    };
    sample(379616,4000);assert(!d.frozen());sample(361264,4001);assert(streak==0 && d.firstRejectedSequence==-1);
    for(unsigned i=0;i<20;++i) {sample(379616,5000+i*2);sample(361264,5001+i*2);assert(!d.frozen());}
    // Gradual accepted movement toward the limit, then the actual causal streak.
    for(auto raw:{361264U,365000U,370000U,375000U,379288U}) sample(raw,6000+d.sequence);
    sample(379616,7000);sample(380000,7001);assert(!d.frozen());sample(381000,7002);
    const auto frozen=d.serialized();assert(d.frozen() && stop.value==Stop::Feedback && frozen[FStreak]==3);
    assert(frozen[FRaw]==381000 && frozen[FFirstRejectedSequence]==frozen[FSequence]-2);
    assert(frozen[FPhase]==1 && frozen[FEpoch]==6 && frozen[FSeekSerial]==9 && frozen[FSinceSeek]==5002 && frozen[FSinceOut]==4002);
    assert(frozen[FRequestAge]==1000 && frozen[FOut]==21 && frozen[FFeedback]==20 && frozen[FBootstrap]==0);
    for(unsigned i=0;i<8;++i) sample(379616,8000+i);
    assert(streak==11 && d.serialized()==frozen && d.fatal[FStreak]==3);
    sample(361264,9000);d.recovered();d.freeze({});stop.choose(Stop::Requested);
    assert(d.serialized()==frozen && stop.value==Stop::Feedback); // Post-fault valid data and Stop cannot overwrite it.
    const auto firstRow=kFeedbackFatalWords+(16-3)*kFeedbackObservationWords;
    assert(frozen[firstRow+DRaw]==379616 && frozen[firstRow+DAccepted]==0);
}
static void suddenMalformedTimeoutAndUnavailableTimes() {
    FeedbackDiagnostics sudden;sudden.observe(row(361264,1000));
    for(unsigned i=1;i<=3;++i) {sudden.observe(row(420000,1000+i));sudden.rejected(i);}
    sudden.freeze(context(sudden,3,1003));assert(sudden.fatal[FRaw]==420000);
    FeedbackDiagnostics malformed;auto missing=row(0,2000);missing[DRaw]=-1;missing[DActual]=0;
    missing[DValidity]=static_cast<int>(FeedbackValidity::Length);missing[DRejection]=1;missing[DAccepted]=0;
    malformed.observe(missing);auto f=context(malformed,0,2000);f[FReason]=static_cast<int>(FeedbackFatalReason::Malformed);
    f[FFirstRejectedSequence]=f[FFirstRejectedElapsed]=-1;malformed.freeze(f);assert(malformed.fatal[FRaw]==-1);
    FeedbackDiagnostics timeout;FeedbackFatalContext noSample{};noSample[FReason]=4;
    noSample[FSequence]=noSample[FRaw]=noSample[FValidity]=noSample[FRequestAge]=-1;noSample[FBootstrap]=1;
    timeout.freeze(noSample);assert(timeout.fatal[FBootstrap]==1 && timeout.count==0);
    FeedbackDiagnostics stopped;StopCause earlierStop;earlierStop.choose(Stop::Requested);
    stopped.observe(row(379616,3000));stopped.rejected(1);
    // Session gate uses the already chosen cause: a later feedback problem must not invent a first fatal decision.
    if(!earlierStop.chosen) stopped.freeze(f);
    assert(!stopped.frozen() && earlierStop.value==Stop::Requested);
    FeedbackDiagnostics badStatus;auto failed=row(361264,4000);
    failed[DValidity]=static_cast<int>(FeedbackValidity::CompletionError);failed[DRejection]=5;
    failed[DUrbStatus]=-5;failed[DAccepted]=0;badStatus.observe(failed);
    assert((*badStatus.latest())[DUrbStatus]==-5 && (*badStatus.latest())[DRaw]==361264 && !(*badStatus.latest())[DAccepted]);
    assert(diagnosticElapsed(0,100)==-1 && diagnosticElapsed(100,99)==-1 && diagnosticElapsed(100,100)==0);
    assert(diagnosticElapsed(1,UINT64_MAX)==-1 && diagnosticElapsed(1,static_cast<uint64_t>(INT64_MAX)+1)==INT64_MAX);
    // A returned terminal copy survives the recording object's lifetime/resource release.
    std::array<int64_t,kFeedbackDiagnosticWords> retained{};
    {FeedbackDiagnostics owner;owner.observe(row(0,3000));owner.freeze(f);retained=owner.serialized();}
    assert(retained[FExists]==1 && retained[FHistoryCount]==1);
}
static void liveOutputPoolAndUnits() {
    struct TestSlot { Identity identity;bool feedback=false;unsigned packets=0;std::array<uint32_t,kOutPackets> frames{}; };
    std::array<TestSlot,4> slots{};slots[3].feedback=true;
    LiveWords w{};int64_t submitted=0,completed=0;
    captureOutPool(w,slots,3,44100,submitted,completed);
    assert(w[LOutOutstanding]==0 && w[LOutFree]==3 && w[LOutPendingFrames]==0);
    for(unsigned i=0;i<2;++i) {
        slots[i].packets=8;slots[i].frames.fill(6);assert(slots[i].identity.submit(2,9,i+1));++submitted;
    }
    // Short final request: only its actual two packets contribute, not stale frame-array capacity.
    slots[2].packets=2;slots[2].frames.fill(1000);slots[2].frames[0]=5;slots[2].frames[1]=4;
    assert(slots[2].identity.submit(2,9,3));++submitted;
    assert(slots[3].identity.submit(2,9,4));slots[3].frames.fill(999);slots[3].packets=1;
    captureOutPool(w,slots,3,48000,submitted,completed);
    assert(w[LOutSubmitted]==3 && w[LOutCompleted]==0 && w[LOutOutstanding]==3 && w[LOutFree]==0);
    assert(w[LOutPendingFrames]==105 && w[LOutPayloadDuration]==2187); // floor(105 * 1e6 / 48000)
    assert(!slots[0].identity.complete(3,9)); // Stale attachment cannot advance pool accounting.
    assert(slots[0].identity.complete(2,9));++completed;
    captureOutPool(w,slots,3,96000,submitted,completed);
    assert(w[LOutSubmitted]==3 && w[LOutCompleted]==1 && w[LOutOutstanding]==2 && w[LOutFree]==1);
    assert(w[LOutPendingFrames]==57 && w[LOutPayloadDuration]==593);
    assert(payloadDurationUs(44100,44100)==1000000 && payloadDurationUs(1,44100)==22);
    assert(payloadDurationUs(1,0)==-1 && payloadDurationUs(UINT64_MAX,44100)==-1);
}
static void liveGapsStartupAndSparseRejections() {
    LiveDiagnostics d;d.beginSource(0,0,1000,0);d.milestone(SMetadata,1100);d.milestone(SPcm,1200);
    d.milestone(SReady,1300);d.milestone(SSubmit,1500);d.milestone(SComplete,2500);d.milestone(SActive,1501);
    d.milestone(SSubmit,9000);assert(d.startups.latest()[SSubmit]==500); // First evidence only.
    for(unsigned i=0;i<40;++i) d.gap(1000,3000+i,1,22000,21,i%2?0:8192,0,0);
    std::array<int64_t,kLiveGapCapacity*kLiveGapWords> gaps{};d.gaps.copyTo(gaps.data());
    assert(d.gaps.count==16 && gaps[0]==25 && gaps[8*15]==40 && gaps[5]==8192 && gaps[13]==0);
    // A long scheduling interval with PCM remains distinct from no decoded PCM; neither asserts audible dropout.
    d.lastSubmit=d.submissionGapBase=4000;d.lastWake=5000;d.suspend();
    assert(!d.lastWake && !d.submissionGapBase && d.lastSubmit==4000);
    for(unsigned epoch=1;epoch<=12;++epoch) {d.beginSource(epoch,epoch+5,10000+epoch,epoch*100);d.milestone(SPcm,11000+epoch);}
    std::array<int64_t,kLiveStartupCapacity*kLiveStartupWords> starts{};d.startups.copyTo(starts.data());
    assert(d.startups.count==8 && starts[SEpoch]==5 && starts[7*10+SEpoch]==12 && starts[SPcm]==1000);
    assert(starts[SMetadata]==-1 && starts[SSubmit]==-1); // Seek/restart does not inherit old startup milestones.
    FeedbackDiagnostics feedback;
    for(unsigned i=1;i<=12;++i) {
        feedback.observe(row(379616,20000+i*2));d.feedback(*feedback.latest());
        for(unsigned n=0;n<20;++n) {feedback.observe(row(361264,20000+i*2));d.feedback(*feedback.latest());}
    }
    assert(d.rejected.count==8 && d.rejected.latest()[DRaw]==379616 && d.rejected.latest()[DAccepted]==0);
    assert(feedback.latest() && (*feedback.latest())[DRaw]==361264);
    assert(d.feedbackAccepted==240 && d.feedbackRejected==12 && d.feedbackIgnored==0);
    auto quiet=row(361264,30000,3);quiet[DRejection]=7;quiet[DAccepted]=0;d.feedback(quiet);
    assert(d.feedbackAccepted==240 && d.feedbackIgnored==1 && d.feedbackRejected==12);
    LiveDiagnostics recreated;assert(!recreated.gaps.count && !recreated.startups.count && !recreated.rejected.count);
}
static void concurrentLivePublicationAndRetainedLifetime() {
    auto owner=std::make_shared<PublishedLiveDiagnostics>();LiveWords first{};first.fill(1);first[LTerminal]=0;
    assert(owner->tryPublish(first));std::atomic<bool> start{false};std::vector<std::thread> readers;
    // Exercise concurrent retrieval, the exact production publisher, and resource-free copies.
    for(unsigned i=0;i<3;++i) readers.emplace_back([pin=owner,&start] {
        while(!start.load(std::memory_order_acquire)) std::this_thread::yield();
        int64_t previous=0;
        for(unsigned n=0;n<3000;++n) {
            const auto copy=pin->read();const auto sequence=copy[LCapturedAt];
            assert(sequence>=previous);previous=sequence;
            for(size_t field=0;field<copy.size();++field) if(field!=LTerminal) assert(copy[field]==sequence);
        }
    });
    start.store(true,std::memory_order_release);
    for(int64_t n=2;n<20000;++n) {LiveWords value{};value.fill(n);value[LTerminal]=0;owner->tryPublish(value);}
    LiveWords terminal{};terminal.fill(20000);terminal[LTerminal]=1;owner->publishQuiescent(terminal);
    auto pinned=owner;owner.reset(); // The JNI registry's shared_ptr pin follows this lifetime pattern.
    for(auto& reader:readers) reader.join();
    auto retained=pinned->read();pinned.reset();assert(retained[LTerminal]==1 && retained[LCapturedAt]==20000);
    PublishedLiveDiagnostics newSession;assert(newSession.read()[LVersion]==0); // No inherited terminal data.
}
int main() {rollingAndZeroEvidence();recoverThenFreezeAtThirdNotEleventh();suddenMalformedTimeoutAndUnavailableTimes();
    liveOutputPoolAndUnits();liveGapsStartupAndSparseRejections();concurrentLivePublicationAndRetainedLifetime();}
