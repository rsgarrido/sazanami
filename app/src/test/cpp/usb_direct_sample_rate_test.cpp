// Standalone CPU test, authored only; NOT part of testDebugUnitTest.
#include "../../main/cpp/usb_iso_core.h"
#include "../../main/cpp/usb_iso_tail.h"
#include "../../main/cpp/usb_out_queue_policy.h"
#include "../../main/cpp/usb_feedback_policy.h"
#include "../../main/cpp/flac/flac_pcm_core.h"
#include <cassert>
#include <algorithm>
using namespace sazanami::usbiso;
namespace fv = sazanami::flacverify;
static Feedback feedback(uint32_t raw, uint32_t rate) {
    uint8_t bytes[4];for (unsigned i=0;i<4;++i) bytes[i]=static_cast<uint8_t>(raw>>(i*8));
    return decodeFeedback(bytes,4,0,rate);
}
int main() {
    for (uint32_t rate : {44100,48000}) {
        assert(fv::supported(rate,2,16));assert(!fv::supported(rate,2,24));
        assert(!fv::supported(rate,1,16));assert(!fv::supported(rate,8,16));
        Scheduler nominal(rate);uint32_t frames=0;
        for (unsigned packet=0;packet<8000;++packet) {
            assert(nominal.next(1,0,frames));assert(packetFits(frames,28));
            if (rate==48000) assert(frames==6 && frames*kBytesPerFrame==24);
        }
        assert(nominal.frames==rate && nominal.remainder==0);
        const uint32_t raw=static_cast<uint32_t>(static_cast<uint64_t>(rate)*65536/8000);
        for (unsigned percent : {96,100,104}) assert(feedback(raw*percent/100,rate).validity==FeedbackValidity::Valid);
        for (unsigned percent : {94,106}) assert(feedback(raw*percent/100,rate).validity==FeedbackValidity::Implausible);
        Scheduler drift(rate);bool varied=false;
        for (unsigned packet=0;packet<8000;++packet) {
            assert(drift.next(1,raw*104/100,frames));assert(packetFits(frames,28));
            if(rate==48000 && frames==7) varied=true;
        }
        assert(drift.frames>rate);if(rate==48000)assert(varied);
        const auto phase=drift.remainder;
        const auto rollback=drift;
        assert(drift.next(1,raw*96/100,frames));
        assert(frames==(phase+static_cast<uint64_t>(raw*96/100)*8000)/Scheduler::denominator);
        drift=rollback;assert(drift.nominalRate==rate && drift.remainder==phase && drift.frames==rollback.frames);
    }
    for (uint32_t rate : {88200,96000,176400,192000}) assert(!fv::supported(rate,2,16));
    assert(feedback(393216,44100).validity==FeedbackValidity::Implausible);
    assert(feedback(361267,48000).validity==FeedbackValidity::Implausible);
    assert(outUrbCount(125,2)==21 && outUrbCount(125,3)==21);
    assert(kOutPackets==8 && 21*kOutPackets*125==21000);
    assert(feedbackUrbCount(1000)==21 && feedbackHoldoverUs(1000)==20000);
    // Synthetic source totals, no music-fixture hash or tail assumptions. Exact PCM and no resampling.
    for (uint64_t total : {48001ULL,48005ULL,48006ULL,48007ULL,48047ULL}) {
        Scheduler scheduler(48000);fv::Pcm decoded;uint64_t submitted=0,completed=0,submittedHash=fv::kOffset;
        while (submitted<total) {
            std::array<uint32_t,kOutPackets> normal{};
            for(auto& frames:normal) assert(scheduler.next(1,393216,frames));
            const uint64_t remaining=total-submitted;
            const auto shape=terminalShape(normal,std::min<uint64_t>(remaining,48),true);
            assert(shape.valid && shape.total==std::min<uint64_t>(remaining,48));
            assert(shape.packets>=1 && shape.packets<=8);
            for (size_t i=0;i<shape.packets;++i) for(uint32_t n=0;n<shape.frames[i];++n) {
                uint8_t bytes[4];assert(decoded.append(-32768,32767,bytes));
                assert(bytes[0]==0 && bytes[1]==128 && bytes[2]==255 && bytes[3]==127);
                submittedHash=fv::hashBytes(submittedHash,bytes,4);++submitted;++completed;
            }
            for(size_t i=shape.packets;i<8;++i)assert(shape.frames[i]==0); // Unused packets excluded; zero EOF padding.
        }
        assert(decoded.frames==total && submitted==total && completed==total && decoded.bytes==total*4);
        assert(decoded.hash==submittedHash);
        assert(naturalOutComplete(true,total,total,total,submitted,completed,0,0,true));
        assert(!naturalOutComplete(true,total,total,total,submitted,completed-1,1,0,true));
        assert(fv::classify(true,true,true,true,true,true,true,true,true,total,decoded,0,0,true)==fv::Outcome::Exact);
        assert(fv::classify(true,true,true,true,true,true,true,true,false,total,decoded,0,0,true)==fv::Outcome::Md5Mismatch);
    }
}
