// Standalone C++ suite. Author-only: not part of testDebugUnitTest; no USB hardware.
#include "../../main/cpp/usb_seek_control.h"
#include "../../main/cpp/flac/flac_prefix_queue.h"
#include "../../main/cpp/usb_pause_control.h"
#include <cassert>
using namespace sazanami::flacverify;
using namespace sazanami::usbiso;
int main() {
    for(auto tuple: {std::pair<unsigned,unsigned>{44100,16},{48000,16},{48000,24},{96000,24}}) {
        auto format=sazanami::directpcm::Format::create(tuple.first,2,tuple.second);
        assert(format && kPrefixCapacity==16384 && kPrefixPrebuffer==8192);
        PrefixQueue q(true,format);q.expectedTotal=1000;
        auto frame=[&](uint64_t index) {
            std::array<uint8_t,6> bytes{};
            sazanami::directpcm::packSample(bytes.data(),static_cast<int32_t>(index),format->bytesPerSample);
            sazanami::directpcm::packSample(bytes.data()+format->bytesPerSample,-static_cast<int32_t>(index),format->bytesPerSample);
            return bytes;
        };
        for(uint64_t i=0;i<100;++i) {auto bytes=frame(i);assert(q.push(i,bytes.data()));}
        std::array<uint8_t,6000> payload{};
        assert(q.reserve(payload.data(),20)==ReserveResult::Available && q.validate(payload.data(),20*format->bytesPerFrame));
        q.abandonReservation();assert(q.head==0 && q.consumedTotal==0);
        assert(q.reserve(payload.data(),20)==ReserveResult::Available && q.commit());
        SeekSegments ledger;
        const auto hash=hashBytes(kOffset,payload.data(),20*format->bytesPerFrame);
        assert(ledger.seal(20,20,20,20*format->bytesPerFrame,20*format->bytesPerFrame,q.segmentConsumedHash,hash,format->bytesPerFrame,1000,false));
        assert(!q.resetEpoch(500,1));q.quiesceRequested=true;
        assert(q.resetEpoch(500,1) && q.invalidatedQueued==80 && q.sourceHead()==500 && q.consumedTotal==20);
        q.quiesceRequested=false;ledger.begin(1,500,20,20,20*format->bytesPerFrame,20*format->bytesPerFrame);ledger.targetProven=true;
        for(uint64_t i=500;i<550;++i) {auto bytes=frame(i);assert(q.push(i,bytes.data()));}
        assert(q.reserve(payload.data(),50)==ReserveResult::Available && q.validate(payload.data(),50*format->bytesPerFrame));
        assert(sazanami::directpcm::unpackSample(payload.data(),format->bytesPerSample)==500);assert(q.commit());
        const auto second=hashBytes(kOffset,payload.data(),50*format->bytesPerFrame);
        assert(ledger.seal(70,70,70,70*format->bytesPerFrame,70*format->bytesPerFrame,q.segmentConsumedHash,second,format->bytesPerFrame,1000,false));
        q.quiesceRequested=true;assert(q.resetEpoch(10,2));q.quiesceRequested=false;
        ledger.begin(2,10,70,70,70*format->bytesPerFrame,70*format->bytesPerFrame);ledger.targetProven=true;
        assert(q.sourceHead()==10 && q.consumedTotal==70); // Paused target, no manufactured completion.
        auto bytes=frame(10);assert(q.push(10,bytes.data()));assert(q.reserve(payload.data(),1)==ReserveResult::Available && q.commit());
        assert(sazanami::directpcm::unpackSample(payload.data(),format->bytesPerSample)==10);
        auto bad=ledger;assert(!bad.seal(71,71,71,71*format->bytesPerFrame,71*format->bytesPerFrame,q.segmentConsumedHash,0,format->bytesPerFrame,1000,false));
        bad=ledger;bad.targetProven=false;assert(!bad.seal(71,71,71,71*format->bytesPerFrame,71*format->bytesPerFrame,second,second,format->bytesPerFrame,1000,false));
        bad=ledger;assert(!bad.seal(71,70,71,71*format->bytesPerFrame,70*format->bytesPerFrame,second,second,format->bytesPerFrame,1000,false));
        q.quiesceRequested=true;assert(!q.resetEpoch(1000,3));assert(!q.resetEpoch(0,kCounterMax+1));assert(!q.resetEpoch(0,2));
    }
    SeekSegments many;
    for(uint64_t epoch=0;epoch<100;++epoch) {
        if(epoch) {many.begin(epoch,epoch,epoch,epoch,epoch*4,epoch*4);many.targetProven=true;}
        assert(many.seal(epoch+1,epoch+1,epoch+1,(epoch+1)*4,(epoch+1)*4,123,123,4,1000,false));
    }
    assert(many.sealed==100 && many.exampleCount==8 && many.frames==100 && many.bytes==400);
    many.begin(100,100,100,100,400,400);many.targetProven=true;
    assert(!many.seal(101,101,101,404,403,123,123,4,1000,false)); // A failure beyond cap stays fatal.
    SeekSegments overflow;overflow.frames=kCounterMax;
    assert(!overflow.seal(1,1,1,4,4,123,123,4,1000,false));
    SeekSegments tail;tail.begin(1,999,0,0,0,0);tail.targetProven=true;
    assert(tail.seal(1,1,1,6,6,123,123,6,1000,true));
    PauseMachine machine;machine.phase=PlaybackPhase::SeekDraining;machine.drainStarted=10;
    assert(machine.quiet() && machine.drainExpired(100011,100000));
    machine.phase=PlaybackPhase::SeekPriming;assert(machine.quiet() && !machine.admitsOut());
}
