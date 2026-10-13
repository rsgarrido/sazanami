// Authored only; standalone native suite, not testDebugUnitTest. Finite evidence models, no ioctl or DAC claims.
#include "../../main/cpp/usb_feedback_policy.h"
#include "../../main/cpp/usb_iso_stop.h"
#include "../../main/cpp/usb_pause_control.h"
#include "../../main/cpp/usb_seek_control.h"
#include <cassert>
#include <cerrno>
using namespace sazanami::usbiso;

static Feedback sample(uint32_t raw) {
    std::array<uint8_t,4> bytes{};
    for(unsigned i=0;i<4;++i) bytes[i]=static_cast<uint8_t>(raw>>(i*8));
    return decodeFeedback(bytes.data(),bytes.size(),0,44100);
}
static void invalidFeedbackAndFreshEpochs() {
    static_assert(kInvalidFeedbackLimit==3 && kPlausibilityPercent==5);
    assert(sample(361267).validity==FeedbackValidity::Valid);
    assert(sample(379616).validity==FeedbackValidity::Implausible);
    FeedbackObservationClock clock;clock.started=1000;clock.accept(1001,1002);
    unsigned streak=0;StopCause cause;Integrity integrity;
    // Finite model of the existing active parser/streak and post-fault reap branch.
    auto observe=[&](uint32_t raw,uint64_t at) {
        const auto decoded=sample(raw);
        if(decoded.validity!=FeedbackValidity::Valid) {
            ++streak;
            if(streak>=kInvalidFeedbackLimit) {integrity.compromise(InvalidFeedback);cause.choose(Stop::Feedback);}
        } else if(!cause.chosen) {streak=0;clock.accept(at-1000,at);}
    };
    observe(379616,2000);assert(streak==1 && !cause.chosen && clock.lastObserved==1002);
    observe(361267,2001);assert(streak==0 && clock.lastObserved==2001);
    for(unsigned i=0;i<10;++i) {
        observe(379616,3000+i);
        assert(cause.chosen==(i>=2)); // Fail at the THIRD observation, not the final reported tenth.
        if(i==2) assert(cause.value==Stop::Feedback && !integrity.valid());
    }
    assert(streak==10 && cause.value==Stop::Feedback && clock.lastObserved==2001);
    cause.choose(Stop::Requested);cause.choose(Stop::Accounting);
    assert(cause.value==Stop::Feedback); // Stop/accounting cannot replace the causal feedback failure.
    observe(361267,5000);assert(streak==10 && clock.lastObserved==2001); // Shutdown does not refresh accepted feedback.
    for(auto transition : {PlaybackPhase::Paused,PlaybackPhase::SeekDraining,PlaybackPhase::SourceRepositioning,PlaybackPhase::SeekPriming}) {
        PauseMachine machine;machine.phase=transition;assert(machine.quiet() && !machine.admitsOut());
        // An old request remains outstanding until its exact identity has been reaped.
        Identity old;assert(old.submit(1,2,1));assert(old.outstanding);
        assert(old.complete(1,2));assert(!old.outstanding);assert(!old.complete(1,2));
        // Matches the production reset only AFTER both pools reconcile; preserve the rational scheduler.
        Scheduler scheduler;uint32_t count=0;assert(scheduler.next(1,0,count));const auto phase=scheduler.remainder;
        clock={};clock.started=9000000;streak=0;
        assert(clock.lastObserved==0 && clock.lastSubmitted==0 && scheduler.remainder==phase);
        assert(clock.expired(9099999,20000)==FeedbackTimeoutKind::None);
        assert(clock.expired(9100000,20000)==FeedbackTimeoutKind::Bootstrap);
        clock.accept(9000001,9001000);assert(clock.expired(9021000,20000)==FeedbackTimeoutKind::None);
        assert(clock.expired(9021001,20000)==FeedbackTimeoutKind::LastValidWatchdog);
    }
    for(auto transition : {PlaybackPhase::SeekDraining,PlaybackPhase::SourceRepositioning,PlaybackPhase::SeekPriming}) {
        PauseMachine stopped;stopped.phase=transition;stopped.finish(100,PlaybackPhase::Stopping);stopped.finish(101,PlaybackPhase::Stopped);
        stopped.pause(102);stopped.resume(103);assert(stopped.terminal() && !stopped.admitsOut());
    }
}
static void partialCancelledOutNeverBecomesVerifiedPcm(unsigned width) {
    const uint64_t successful=939279;
    uint64_t submitted=successful,completed=0,actual=0,cancelledFrames=0;
    Integrity integrity;integrity.compromise(InvalidFeedback);
    // Previously successful packets are still counted normally, including those reaped in fatal cleanup.
    for(uint64_t offset=0;offset<successful;) {
        const auto frames=std::min<uint64_t>(6,successful-offset);
        assert(!outPacketProblem(0,0,frames*width,frames*width,false));
        completed+=frames;actual+=frames*width;offset+=frames;
    }
    std::array<Identity,19> out{};
    uint64_t reportedCancelledActual=0;
    for(unsigned urb=0;urb<19;++urb) {
        assert(out[urb].submit(1,2,urb+1));out[urb].discardRequested=true;
        for(unsigned packet=0;packet<8;++packet) {
            const unsigned index=urb*8+packet,frames=index<119?6:5; // 152 packets total, requesting 879 frames.
            const unsigned partial=index<9?6:index==9?5:0; // 59 reported actual frames in cancelled transfers.
            submitted+=frames;cancelledFrames+=frames;actual+=partial*width;reportedCancelledActual+=partial;
            assert(!outPacketProblem(-ENOENT,0,frames*width,partial*width,true));
            // Cancellation is separately accounted, never added to verified successful frames.
        }
        assert(out[urb].complete(1,2));
    }
    for(const auto& identity:out) assert(!identity.outstanding);
    assert(cancelledFrames==879 && reportedCancelledActual==59 && submitted==940158 && completed==939279);
    assert(submitted*width-actual==820*width && actual-completed*width==59*width);
    if(width==4) assert(submitted*width==3760632 && actual==3757352 && actual-completed*width==236);
    assert(outPacketProblem(0,-EXDEV,24,12,false)&PacketError);
    assert(outPacketProblem(0,0,24,12,false)&ShortPacket);
    assert(outPacketProblem(-EIO,0,24,24,false)&UrbError);
    integrity.compromise(FrameMismatch);assert(!integrity.valid());
    SeekSegments ledger;
    for(uint64_t epoch=0;epoch<6;++epoch) {
        if(epoch) {ledger.begin(epoch,epoch*10,epoch*8,epoch*8,epoch*8*width,epoch*8*width);ledger.targetProven=true;}
        assert(ledger.seal((epoch+1)*8,(epoch+1)*8,(epoch+1)*8,(epoch+1)*8*width,(epoch+1)*8*width,123,123,width,10000000,false));
    }
    const auto examples=ledger.examples;
    ledger.begin(6,7691569,48,48,48*width,48*width);ledger.targetProven=true;
    auto attempted=ledger;
    assert(!attempted.seal(submitted+48,completed+48,submitted+48,(submitted+48)*width,actual+48*width,123,123,width,10000000,true));
    assert(ledger.sealed==6 && ledger.examples==examples && ledger.valid); // Active failed epoch remains unsealed; prior evidence survives.
    assert(attempted.sealed==6 && !attempted.valid);
}
int main() {
    invalidFeedbackAndFreshEpochs();partialCancelledOutNeverBecomesVerifiedPcm(4);partialCancelledOutNeverBecomesVerifiedPcm(6);
}
