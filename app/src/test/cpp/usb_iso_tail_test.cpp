// Authored for human execution only. Pure CPU helpers; no USB or playback.
#include "../../main/cpp/usb_iso_tail.h"
#include "../../main/cpp/usb_iso_stop.h"
#include <cassert>
#include <numeric>
using namespace sazanami::usbiso;
static void exactTerminalShapes() {
    const std::array<uint32_t,kOutPackets> normal{5,6,5,6,5,6,5,6};
    const uint64_t full=44;
    for (uint64_t remaining=1;remaining<=full;++remaining) {
        const auto shape=terminalShape(normal,remaining,true);
        assert(shape.valid && shape.total==remaining && shape.packets>=1 && shape.packets<=kOutPackets);
        uint64_t bytes=0;
        for (size_t i=0;i<kOutPackets;++i) {
            if (i<shape.packets) { assert(shape.frames[i]>0 && shape.frames[i]<=normal[i]); bytes+=shape.frames[i]*4; }
            else assert(shape.frames[i]==0); // no unused descriptor is submitted
        }
        assert(bytes==remaining*4);
        assert(shape.shortened==(shape.frames[shape.packets-1]!=normal[shape.packets-1]));
    }
    const auto one=terminalShape(normal,1,true); assert(one.packets==1 && one.frames[0]==1 && one.shortened);
    const auto packet=terminalShape(normal,5,true); assert(packet.packets==1 && !packet.shortened);
    const auto cross=terminalShape(normal,6,true); assert(cross.packets==2 && cross.frames[1]==1);
    const auto urb=terminalShape(normal,44,true); assert(urb.packets==8 && !urb.shortened);
    // More than one URB: preceding normal requests retain all 44 frames; terminal remainder is one.
    assert(!terminalShape(normal,45,true).valid);
    assert(44+terminalShape(normal,45-44,true).total==45);
    assert(!terminalShape(normal,0,true).valid && !terminalShape(normal,1,false).valid);
    auto invalid=normal; invalid[0]=0; assert(!terminalShape(invalid,1,true).valid);
}
static void producerEofIsNotTransportCompletion() {
    assert(!naturalOutComplete(true,45,45,44,44,44,0,0,true)); // queued final frame
    assert(!naturalOutComplete(true,45,45,45,45,44,1,0,true)); // final OUT pending
    assert(naturalOutComplete(true,45,45,45,45,45,0,0,true));
    assert(!naturalOutComplete(false,45,45,45,45,45,0,0,true));
    assert(!naturalOutComplete(true,0,0,0,0,0,0,0,true));
    for (size_t field=0;field<4;++field) {
        std::array<uint64_t,4> counts{45,45,45,45}; --counts[field];
        assert(!naturalOutComplete(true,45,counts[0],counts[1],counts[2],counts[3],0,0,true));
        counts[field]=46;
        assert(!naturalOutComplete(true,45,counts[0],counts[1],counts[2],counts[3],0,0,true));
    }
    assert(!naturalOutComplete(true,45,45,45,45,45,0,1,true));
    assert(!naturalOutComplete(true,45,45,45,45,45,0,0,false));
}
static void causalStopsAndNoSuccessTimer() {
    assert(!durationReached(1,UINT64_MAX,0)); // no fixed ceiling even for a long track
    for (Stop first : {Stop::Underrun,Stop::OutPoolStall,Stop::Source,Stop::Requested,Stop::Detached,Stop::SourceVerificationStall}) {
        StopCause cause; cause.choose(first); cause.choose(Stop::NaturalEof); assert(cause.value==first);
    }
    StopCause eof; eof.choose(Stop::NaturalEof); eof.choose(Stop::Requested); eof.choose(Stop::Worker);
    assert(eof.value==Stop::NaturalEof);
}
int main() { exactTerminalShapes(); producerEofIsNotTransportCompletion(); causalStopsAndNoSuccessTimer(); }
