// Human-executed native helper tests. No USB, hardware, FLAC fixture or test execution by Codex.
#include "../../main/cpp/flac/flac_prefix_queue.h"
#include <cassert>
#include <future>
using namespace sazanami::flacverify;

static bool available(PrefixQueue& q, uint8_t* payload, uint64_t count) { return q.reserve(payload,count)==ReserveResult::Available; }
static const uint8_t a[4]{0,128,255,127}, b[4]{1,0,255,255};
static void transactionalSubmission() {
    PrefixQueue q;
    assert(q.push(0,a) && q.push(1,b));
    uint8_t payload[12]{}; payload[8]=165;
    assert(available(q,payload,2) && q.validate(payload,8));
    // Failed SUBMITURB: owner does NOT call commit. Cursor and hash remain unchanged.
    assert(q.head.load()==0 && q.prepared==2 && q.consumedHash==kOffset && q.depth()==2);
    const auto usbHash=hashBytes(kOffset,payload,8);
    assert(usbHash!=hashBytes(kOffset,payload,12)); // unused capacity excluded
    assert(q.commit()); // success path commits exactly the reservation
    assert(q.head.load()==2 && q.prepared==0 && q.depth()==0 && q.consumedHash==usbHash);
    assert(q.examples[0][1]==-32768 && q.examples[0][2]==32767);
    assert(q.push(2,a)); // prefetched PCM does not enter committed hash
    assert(q.consumedHash==usbHash && q.enqueuedHash!=usbHash && q.depth()==1);
    q.requestStop(); assert(!available(q,payload,1));
}
static void noConcealmentOrMutation() {
    PrefixQueue empty; uint8_t out[8]{165,165,165,165,165,165,165,165};
    assert(!available(empty,out,1) && empty.underruns==1 && empty.emptyEvents==1);
    for (auto byte:out) assert(byte==165); // no zero/repeat/interpolation fill
    assert(empty.faults.load() & PrefixUnderrun);
    PrefixQueue partial; assert(partial.push(0,a)); assert(!available(partial,out,2));
    assert(partial.head.load()==0 && partial.consumedHash==kOffset);
    PrefixQueue mutated; assert(mutated.push(0,a) && mutated.push(1,b) && available(mutated,out,2));
    const auto expected=hashBytes(kOffset,out,8); out[0]^=1;
    assert(!mutated.validate(out,8) && (mutated.faults.load() & PrefixHash));
    assert(hashBytes(kOffset,out,8)!=expected && mutated.head.load()==0 && mutated.prepared==2);
    PrefixQueue reordered; assert(reordered.push(0,a) && reordered.push(1,b) && available(reordered,out,2));
    for (int i=0;i<4;++i) std::swap(out[i],out[i+4]);
    assert(!reordered.validate(out,8)); assert(hashBytes(kOffset,out,8)!=expected);
}
static void absoluteIdentity() {
    PrefixQueue nonzero; assert(!nonzero.push(1,a) && nonzero.gaps==1 && nonzero.orderErrors==1);
    PrefixQueue duplicate; assert(duplicate.push(0,a)); assert(!duplicate.push(0,b));
    assert(duplicate.duplicates==1 && duplicate.orderErrors==1);
    duplicate.fail(PrefixDecode); assert(duplicate.faults.load() & PrefixDuplicate); // sticky
    PrefixQueue reordered; assert(reordered.push(0,a) && reordered.push(1,b));
    reordered.indices[0]=1; reordered.indices[1]=0; uint8_t payload[8];
    assert(!available(reordered,payload,2) && reordered.orderErrors==1 && reordered.head.load()==0);
}
static void backpressureAndWake() {
    PrefixQueue q;
    static_assert(kPrefixCapacity==16384 && kPrefixPrebuffer==8192 && kPrefixDurationUs==5000000);
    static_assert(sizeof(q.bytes)==65536);
    for (size_t i=0;i<kPrefixCapacity;++i) assert(q.push(i,a));
    q.streaming.store(true); q.trackDepth(q.depth());
    auto blocked=std::async(std::launch::async,[&]{return q.push(kPrefixCapacity,b);});
    while (q.fullWaits.load()==0) std::this_thread::yield();
    assert(blocked.wait_for(std::chrono::milliseconds(1))==std::future_status::timeout);
    assert(q.depth()==kPrefixCapacity && q.bytes[0][0]==a[0] && q.faults==0);
    uint8_t payload[4]; assert(available(q,payload,1) && q.commit());
    assert(blocked.get()); assert(q.depth()==kPrefixCapacity && q.faults==0);
    assert(q.minDepth.load()>=kPrefixCapacity-1 && q.maxDepth.load()==kPrefixCapacity);
    auto stopWait=std::async(std::launch::async,[&]{return q.push(kPrefixCapacity+1,a);});
    while(q.fullWaits.load()<2) std::this_thread::yield();
    q.requestStop(); assert(!stopWait.get()); // stop wakes, joins via future, no dropped/overwritten PCM
    assert(q.tail.load()==kPrefixCapacity+1 && q.head.load()==1);
}
static void contiguousRingWrapAndSerializer() {
    PrefixQueue q; Pcm pcm; uint64_t usbHash=kOffset;
    for (uint64_t batch=0;batch<10000;++batch) {
        uint8_t expected[12], payload[12];
        for (size_t i=0;i<3;++i) {
            assert(pcm.append(static_cast<int32_t>((batch+i)%30000),-1,expected+i*4));
            assert(q.push(batch*3+i,expected+i*4));
        }
        assert(available(q,payload,3) && q.validate(payload,12));
        assert(std::memcmp(payload,expected,12)==0);
        usbHash=hashBytes(usbHash,payload,12); assert(q.commit());
        assert(q.head.load()==batch*3+3 && q.consumedHash==usbHash && usbHash==pcm.hash);
    }
    assert(q.gaps==0 && q.duplicates==0 && q.orderErrors==0 && q.underruns==0);
    assert(q.exampleCount<=5);
}
static void preciseReservationStatesAndFullWrap() {
    PrefixQueue q; uint8_t frame[4], payload[kPrefixCapacity*4];
    for (uint64_t i=0;i<kPrefixCapacity;++i) { pack16(frame,static_cast<int32_t>(i)); pack16(frame+2,-1); assert(q.push(i,frame)); }
    assert(q.reserve(payload,kPrefixCapacity-4)==ReserveResult::Available && q.commit());
    for (uint64_t i=kPrefixCapacity;i<2*kPrefixCapacity-4;++i) {
        pack16(frame,static_cast<int32_t>(i%30000)); pack16(frame+2,-1); assert(q.push(i,frame));
    }
    assert(q.depth()==kPrefixCapacity);
    // head is capacity-4: a 44-frame logical reservation crosses the physical end after four frames.
    assert(q.reserve(payload,44)==ReserveResult::Available && q.validate(payload,176));
    assert(q.underruns==0 && q.emptyEvents==0 && q.head.load()==kPrefixCapacity-4 && q.prepared==44);
    assert(q.commit()); assert(q.depth()==kPrefixCapacity-44 && q.underruns==0);
    q.requestStop();
    assert(q.reserve(payload,44)==ReserveResult::StopRequested && q.underruns==0 && q.emptyEvents==0);
    auto terminal=std::make_unique<PrefixQueue>();
    terminal->requestStop(); assert(terminal->reserve(payload,44)==ReserveResult::StopRequested && terminal->underruns==0);
    for (auto expected : {ReserveResult::Cancelled,ReserveResult::SourceEof,ReserveResult::SourceFault}) {
        terminal=std::make_unique<PrefixQueue>();
        terminal->fail(expected==ReserveResult::Cancelled ? PrefixCancelled : expected==ReserveResult::SourceEof ? PrefixEof : PrefixDecode);
        assert(terminal->reserve(payload,44)==expected && terminal->underruns==0 && terminal->emptyEvents==0);
    }
    PrefixQueue invalid;
    assert(invalid.reserve(payload,0)==ReserveResult::InvalidState && invalid.emptyEvents==0);
    assert(invalid.reserve(payload,kPrefixCapacity+1)==ReserveResult::InvalidState && invalid.underruns==0);
    assert(invalid.reserve(payload,44)==ReserveResult::InsufficientFrames && invalid.underruns==1 && invalid.emptyEvents==1);
    assert(invalid.reserve(payload,44)==ReserveResult::InsufficientFrames && invalid.underruns==1); // sticky event, not a second shortage
}
static void verifiedEofQueueDrainsAcrossRingWrap() {
    PrefixQueue q(true); q.expectedTotal=kPrefixCapacity+3;
    for (size_t i=0;i<kPrefixCapacity;++i) assert(q.push(i,a));
    uint8_t payload[kPrefixCapacity*4];
    assert(available(q,payload,kPrefixCapacity-2) && q.validate(payload,(kPrefixCapacity-2)*4) && q.commit());
    for (size_t i=kPrefixCapacity;i<kPrefixCapacity+3;++i) assert(q.push(i,b));
    q.eofReached.store(true,std::memory_order_release); // successful producer is done; object/ring remains
    assert(!q.stop.load() && q.depth()==5 && q.faults==0);
    assert(available(q,payload,5) && q.validate(payload,20));
    assert(std::memcmp(payload,a,4)==0 && std::memcmp(payload+4,a,4)==0);
    for (size_t i=2;i<5;++i) assert(std::memcmp(payload+i*4,b,4)==0);
    assert(q.commit() && q.depth()==0 && q.head==q.expectedTotal && q.consumedHash==q.enqueuedHash);
    assert(q.underruns==0 && q.emptyEvents==0 && q.prepared==0);
    assert(q.exampleCount<=5 && q.examples[4][0]==static_cast<int64_t>(kPrefixCapacity+2));
    assert(q.requireFrames(1)==ReserveResult::InvalidState && q.underruns==0); // no extra source frame
    assert(!q.push(kPrefixCapacity+3,a) && (q.faults & PrefixTotal));
}
static void fullSourceShortageAndTerminalMutation() {
    PrefixQueue active(true); active.expectedTotal=2; assert(active.push(0,a)); uint8_t payload[8];
    assert(active.reserve(payload,2)==ReserveResult::InsufficientFrames && active.underruns==1);
    PrefixQueue terminal(true); terminal.expectedTotal=2; assert(terminal.push(0,a)); terminal.eofReached=true;
    assert(terminal.reserve(payload,2)==ReserveResult::EndOfSource && terminal.underruns==0 && terminal.prepared==0);
    // Inconsistent verified EOF never fabricates the missing PCM. Native natural gate rejects it.
    PrefixQueue mutated(true); mutated.expectedTotal=2; assert(mutated.push(0,a) && mutated.push(1,b)); mutated.eofReached=true;
    assert(available(mutated,payload,2)); payload[7]^=1;
    assert(!mutated.validate(payload,8) && (mutated.faults & PrefixHash)); assert(mutated.head==0);
    PrefixQueue reordered(true); reordered.expectedTotal=2; assert(reordered.push(0,a) && reordered.push(1,b));
    reordered.indices[1]=0; assert(reordered.reserve(payload,2)==ReserveResult::SourceFault && reordered.duplicates==1);
    PrefixQueue missingTotal(true); assert(!missingTotal.push(0,a) && (missingTotal.faults & PrefixTotal));
}
static void decoderTotalGatePrecedesCanonicalBytes() {
    Verification v(OwnedFd(-1),[]{ return false; },true); v.accepted=true; v.enforceExpectedTotal=true;
    v.facts[Total]=1; v.facts[MaxBlock]=2;
    FLAC__Frame frame{}; frame.header.sample_rate=44100; frame.header.channels=2; frame.header.bits_per_sample=16;
    frame.header.blocksize=2; frame.header.number_type=FLAC__FRAME_NUMBER_TYPE_SAMPLE_NUMBER;
    FLAC__int32 left[2]{1,2},right[2]{-1,-2}; const FLAC__int32* channels[]{left,right};
    uint64_t enqueued=0;
    v.consumeCanonical=[&](uint64_t index,const uint8_t*) { assert(index==0); ++enqueued; return true; };
    assert(Verification::write(nullptr,&frame,channels,&v)==FLAC__STREAM_DECODER_WRITE_STATUS_ABORT);
    assert(v.failure==Outcome::FormatContradiction && v.pcm.frames==1 && v.pcm.bytes==4 && enqueued==1);
    Verification bounded(OwnedFd(-1),[]{ return false; },false);
    assert(!bounded.wholeFile && !bounded.enforceExpectedTotal); // Session 10 defaults retained
    Verification whole(OwnedFd(-1),[]{ return false; }); assert(whole.wholeFile && !whole.enforceExpectedTotal); // Session 9
}
int main() { verifiedEofQueueDrainsAcrossRingWrap(); fullSourceShortageAndTerminalMutation(); decoderTotalGatePrecedesCanonicalBytes(); preciseReservationStatesAndFullWrap(); transactionalSubmission(); noConcealmentOrMutation(); absoluteIdentity(); backpressureAndWake(); contiguousRingWrapAndSerializer(); }
