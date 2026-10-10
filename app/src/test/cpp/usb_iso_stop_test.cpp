// Authored for human execution only: pure causal stop/time/OUT-pool state helpers, no USB IO.
#include "../../main/cpp/usb_iso_stop.h"
#include <cassert>
using namespace sazanami::usbiso;
static void firstCauseAndCleanup() {
    StopCause normal; normal.choose(Stop::Duration);
    for (Stop cleanup : {Stop::Requested,Stop::Underrun,Stop::Source,Stop::SourceEof,Stop::OutPoolStall}) normal.choose(cleanup);
    assert(normal.chosen && normal.value==Stop::Duration);
    for (Stop failure : {Stop::Underrun,Stop::Source,Stop::SourceEof,Stop::Requested}) {
        StopCause early; early.choose(failure); early.choose(Stop::Duration); early.choose(Stop::Worker);
        assert(early.value==failure);
    }
}
static void completedPoolMustBeRefilledBeforeWatchdog() {
    const size_t afterReap=0, afterRefill=kOutPool;
    assert(outPoolStalled(afterReap,0)); // The old pre-refill check stops on this temporary state.
    assert(!outPoolStalled(afterRefill,0));
    assert(!outPoolStalled(afterRefill,kQueueWatchdogUs));
    assert(outPoolStalled(afterRefill,kQueueWatchdogUs+1)); // real lack of completion progress still fails
    assert(outPoolStalled(0,0)); // a pool still empty after attempted refill is a distinct transport stall
}
static void clockIsTransportRelative() {
    // Open/init/prime/configure elapsed before run; they are not the start timestamp passed to this helper.
    const uint64_t transportStart=9000000, prefixTarget=5000000;
    assert(!durationReached(transportStart,transportStart,prefixTarget));
    assert(!durationReached(transportStart,transportStart+3483383,prefixTarget));
    assert(!durationReached(transportStart,transportStart+4999999,prefixTarget));
    assert(durationReached(transportStart,transportStart+5000000,prefixTarget));
    assert(durationReached(transportStart,transportStart+kDurationUs,kDurationUs)); // Sessions 7/8 one second
    Scheduler candidate; const auto before=candidate;
    uint32_t prepared=0; assert(candidate.next(1,0,prepared));
    candidate=before; // deadline before reservation abandons only the unsubmitted header candidate
    assert(candidate.frames==0 && candidate.remainder==0);
    Scheduler scheduler; uint64_t frames=0;
    for (size_t i=0;i<40000;++i) { uint32_t n=0; assert(scheduler.next(1,0,n)); frames+=n; }
    assert(frames==220500); // 5 s nominal; real explicit feedback may adjust total
}
int main() { firstCauseAndCleanup(); completedPoolMustBeRefilledBeforeWatchdog(); clockIsTransportRelative(); }
