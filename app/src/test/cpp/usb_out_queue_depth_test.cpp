// Authored only. Do not compile/run as part of this correction pass.
// Uses production policy, queue, scheduler, identity and EOF helpers; USB IO is modelled.
#include "../../main/cpp/usb_out_queue_policy.h"
#include "../../main/cpp/usb_feedback_policy.h"
#include "../../main/cpp/usb_iso_stop.h"
#include "../../main/cpp/usb_iso_tail.h"
#include "../../main/cpp/flac/flac_prefix_queue.h"
#include <cassert>
#include <vector>
using namespace sazanami::usbiso;
namespace fv=sazanami::flacverify;

struct ModelSlot {
    Identity identity;
    std::array<uint32_t,kOutPackets> frames{};
    std::array<uint8_t,kOutPackets*28> bytes{};
    uint64_t first=0,total=0;
    unsigned packets=0;
    bool terminal=false;
};
struct DepthModel {
    fv::PrefixQueue queue{true};
    std::vector<ModelSlot> slots=std::vector<ModelSlot>(outUrbCount(125,3));
    Scheduler scheduler;
    StopCause cause;
    uint64_t total,submitted=0,completed=0,cancelled=0,serial=0,submittedHash=fv::kOffset;
    size_t terminalUrbs=0;
    uint32_t feedback=0;
    explicit DepthModel(uint64_t frames):total(frames) {
        queue.expectedTotal.store(frames);
        for(uint64_t i=0;i<frames;++i) {
            const std::array<uint8_t,4> frame{static_cast<uint8_t>(i),static_cast<uint8_t>(i>>8),
                static_cast<uint8_t>(i^0x55),static_cast<uint8_t>((i>>8)^0xaa)};
            assert(queue.push(i,frame.data()));
        }
        queue.eofReached.store(true);
    }
    bool submit(size_t i,bool submitSucceeds=true,uint64_t now=0,uint64_t target=0) {
        if(cause.chosen || submitted==total) return false;
        if(durationReached(0,now,target)) { cause.choose(Stop::Duration);return false; }
        auto& slot=slots[i];assert(!slot.identity.outstanding);
        slot.first=submitted;slot.total=0;slot.packets=static_cast<unsigned>(kOutPackets);slot.terminal=false;
        for(auto& n:slot.frames) { assert(scheduler.next(1,feedback,n));slot.total+=n; }
        const auto remaining=total-submitted;
        if(remaining<=slot.total) {
            const auto shape=terminalShape(slot.frames,remaining,queue.eofReached.load());
            assert(shape.valid);slot.frames=shape.frames;slot.packets=shape.packets;slot.total=shape.total;slot.terminal=true;
        }
        assert(queue.reserve(slot.bytes.data(),slot.total)==fv::ReserveResult::Available);
        assert(queue.validate(slot.bytes.data(),slot.total*4));
        for(uint64_t frame=0;frame<slot.total;++frame) {
            const auto index=slot.first+frame;
            assert(slot.bytes[frame*4]==static_cast<uint8_t>(index));
            assert(slot.bytes[frame*4+1]==static_cast<uint8_t>(index>>8));
        }
        if(!submitSucceeds) { cause.choose(Stop::Submit);return false; } // No source commit.
        assert(slot.identity.submit(1,2,++serial));
        submittedHash=fv::hashBytes(submittedHash,slot.bytes.data(),slot.total*4);
        assert(queue.commit());submitted+=slot.total;
        assert(queue.consumedHash==submittedHash && queue.head.load()==submitted);
        if(slot.terminal) ++terminalUrbs;
        return true;
    }
    void prime() { for(size_t i=0;i<slots.size() && !cause.chosen;++i) submit(i); }
    size_t pending() const { size_t n=0;for(const auto& s:slots) n+=s.identity.outstanding;return n; }
    void complete(size_t i,bool cancel=false) {
        auto& slot=slots[i];assert(slot.identity.complete(1,2));
        if(cancel) cancelled+=slot.total;else completed+=slot.total;
    }
    bool natural() const {
        return naturalOutComplete(queue.eofReached.load(),total,queue.tail.load(),queue.head.load(),
            submitted,completed,pending(),queue.prepared,!cause.chosen);
    }
};
static void policyBoundsAndStorage() {
    static_assert(kOutPackets==8 && kOutPool==8 && kMaximumDirectOutUrbs==21 && kReadyReapLimit==182);
    static_assert(directOutUrbCount(125)==21 && directOutUrbCount(250)==11 && directOutUrbCount(500)==6);
    for(uint64_t invalid : std::array<uint64_t,5>{0,124,126,1000,UINT64_MAX}) assert(!directOutUrbCount(invalid));
    assert(!outUrbCount(125,-1) && !outUrbCount(125,4));
    for(int mode=0;mode<4;++mode) assert(outUrbCount(125,mode)==(mode<2 ? 8 : 21));
    assert(feedbackUrbCount(1000)==21 && outUrbCount(125,3)+feedbackUrbCount(1000)==42);
    DepthModel m(3000);m.prime();assert(m.pending()==21 && m.submitted==926);
    assert(m.submitted<fv::kPrefixPrebuffer && fv::kPrefixPrebuffer<fv::kPrefixCapacity);
    for(size_t i=0;i<m.slots.size();++i) for(size_t j=i+1;j<m.slots.size();++j) {
        assert(&m.slots[i].identity!=&m.slots[j].identity);
        assert(reinterpret_cast<uintptr_t>(m.slots[i].bytes.data())+m.slots[i].bytes.size()<=
            reinterpret_cast<uintptr_t>(m.slots[j].bytes.data()));
        assert(m.slots[i].identity.submission!=m.slots[j].identity.submission);
        assert(m.slots[i].first+m.slots[i].total==m.slots[i+1].first);
    }
    const auto queued=m.slots[1].frames;
    m.complete(0);const auto before=m.scheduler;Scheduler expected=before;
    m.feedback=361267;assert(m.submit(0));
    for(auto actual:m.slots[0].frames) { uint32_t n=0;assert(expected.next(1,m.feedback,n) && n==actual); }
    assert(m.scheduler.remainder==expected.remainder && m.scheduler.frames==expected.frames);
    assert(m.slots[1].frames==queued); // Received feedback changes future URBs only.
}
static void transactionsStopAndDuration() {
    DepthModel failed(3000);const auto head=failed.queue.head.load(),hash=failed.queue.consumedHash;
    assert(!failed.submit(0,false));assert(failed.cause.value==Stop::Submit && failed.pending()==0);
    assert(failed.queue.head.load()==head && failed.queue.consumedHash==hash && failed.queue.prepared>0);
    assert(failed.submitted==0 && !failed.submit(1)); // Fatal submit is never retried/batch-committed.
    DepthModel stopped(3000);stopped.prime();const auto consumed=stopped.submitted;
    stopped.cause.choose(Stop::Requested);stopped.queue.requestStop();
    for(auto& slot:stopped.slots) if(slot.identity.outstanding) slot.identity.discardRequested=true;
    for(size_t i=0;i<stopped.slots.size();++i) if(stopped.slots[i].identity.outstanding) stopped.complete(i,true);
    assert(stopped.pending()==0 && stopped.cancelled==consumed && stopped.completed==0);
    assert(stopped.queue.head.load()==consumed && stopped.queue.depth()==stopped.total-consumed);
    assert(!stopped.submit(0) && !stopped.natural());
    DepthModel duration(10000);
    for(size_t i=0;i<duration.slots.size();++i) duration.submit(i,true,4999980+i,5000000);
    assert(duration.cause.value==Stop::Duration && duration.pending()==20 && duration.queue.prepared==0);
    const auto committed=duration.submitted;
    for(size_t i=0;i<duration.slots.size();++i) if(duration.slots[i].identity.outstanding) duration.complete(i);
    assert(duration.pending()==0 && duration.completed==committed); // Normal deadline drains committed work.
}
static void exactEofWithDeeperPool() {
    for(uint64_t total : {883ULL,899ULL,1400ULL}) {
        DepthModel m(total);m.prime();
        while(m.pending()) {
            for(size_t i=0;i<m.slots.size();++i) if(m.slots[i].identity.outstanding) {
                assert(!m.natural());m.complete(i);m.submit(i);
            }
        }
        assert(m.natural() && m.submitted==total && m.completed==total && m.terminalUrbs==1);
        assert(m.queue.depth()==0 && m.queue.prepared==0 && m.submittedHash==m.queue.enqueuedHash);
        size_t tails=0;
        for(const auto& slot:m.slots) if(slot.terminal) {
            ++tails;assert(slot.packets>=1 && slot.packets<=8);
            for(size_t p=slot.packets;p<8;++p) assert(slot.frames[p]==0); // No source-free EOF packets/padding.
            if(total==883) assert(slot.packets==1 && slot.total==1);
            if(total==899) assert(slot.packets>1);
        }
        assert(tails==1 && !m.submit(0));
    }
}
int main() { policyBoundsAndStorage();transactionsStopAndDuration();exactEofWithDeeperPool(); }
