// Standalone Android/Linux POSIX synthetic test; NOT executed by testDebugUnitTest. No USB/DAC claims.
#include "../../main/cpp/usb_pause_control.h"
#include "../../main/cpp/usb_iso_tail.h"
#include "../../main/cpp/usb_feedback_policy.h"
#include "../../main/cpp/flac/flac_prefix_queue.h"
#include <cassert>
#include <deque>
#include <future>
#include <poll.h>
#include <numeric>
namespace ui=sazanami::usbiso;
namespace fv=sazanami::flacverify;
namespace dp=sazanami::directpcm;

static void transitionsAndFaults() {
    ui::PauseMachine state;state.submitted();assert(state.phase==ui::PlaybackPhase::Playing);
    ui::PauseBoundary pending{1,1,48,0,48,0,288,0,123,123,true};
    state.pause(100);assert(!state.admitsOut() && state.phase==ui::PlaybackPhase::Pausing);
    assert(!state.drainExpired(100099,100000) && state.drainExpired(100100,100000));
    state.pause(110);assert(state.drainStarted==100 && !state.confirmPause(120,pending));
    pending.out=0;pending.feedback=0;pending.completed=48;pending.actualBytes=288;
    for(unsigned fault=0;fault<7;++fault) {
        auto bad=pending;
        switch(fault) { case 0:bad.completed=47;break;case 1:bad.consumed=49;break;case 2:bad.prepared=1;break;
            case 3:bad.actualBytes=282;break;case 4:bad.sourceHash=999;break;case 5:bad.valid=false;break;case 6:bad.feedback=1;break; }
        assert(!state.confirmPause(130,bad));
    }
    assert(state.confirmPause(200,pending) && state.pausedFrame==48 && state.pauses==1);
    state.pause(300);assert(state.phase==ui::PlaybackPhase::Paused && state.pauses==1);
    state.resume(10200);assert(state.phase==ui::PlaybackPhase::Resuming && state.pausedUs==10000);
    state.pause(10201);assert(state.phase==ui::PlaybackPhase::Pausing && state.resumeOutcome==ui::ControlOutcome::Superseded);
    assert(state.confirmPause(10202,pending));state.resume(20202);state.submitted();
    assert(state.phase==ui::PlaybackPhase::Playing && state.pauses==2 && state.resumes==1 && state.pausedUs==20000);
    state.resume(21000);assert(state.phase==ui::PlaybackPhase::Playing && state.resumes==1);
    for(auto phase : {ui::PlaybackPhase::Starting,ui::PlaybackPhase::Pausing,ui::PlaybackPhase::Paused,ui::PlaybackPhase::Resuming}) {
        ui::PauseMachine stopped;stopped.phase=phase;stopped.finish(50,ui::PlaybackPhase::Stopping);stopped.finish(51,ui::PlaybackPhase::Stopped);
        stopped.resume(52);stopped.pause(53);assert(stopped.terminal() && !stopped.admitsOut());
        ui::PauseMachine failed;failed.phase=phase;failed.finish(50,ui::PlaybackPhase::Failed);failed.resume(51);
        assert(failed.phase==ui::PlaybackPhase::Failed);
    }
    ui::PauseMachine priming;priming.pause(1);
    const ui::PauseBoundary empty{0,0,0,0,0,0,0,0,123,123,true};
    assert(priming.confirmPause(2,empty) && priming.pausedFrame==0);
    ui::PauseMachine eof;eof.pause(1);eof.finish(2,ui::PlaybackPhase::Ended);eof.resume(3);
    assert(eof.phase==ui::PlaybackPhase::Ended && eof.pauses==0 && eof.pauseOutcome==ui::ControlOutcome::Terminal);
}

static std::pair<int32_t,int32_t> samples(uint64_t index,unsigned bits) {
    const int32_t limit=bits==16 ? 32768 : 8388608;
    if(index%7==0) return {-limit,limit-1};
    const int32_t sample=static_cast<int32_t>(index%2048)-1024;
    return {sample,-sample};
}
struct SyntheticTransport {
    const std::shared_ptr<const dp::Format> format;
    fv::PrefixQueue queue;
    fv::Pcm decoded;
    ui::Scheduler scheduler;
    ui::PauseMachine state;
    uint64_t submitted=0,completed=0,bytes=0,actual=0,submittedHash=fv::kOffset;
    uint64_t now=1;
    struct Pending { uint64_t first,frames;std::vector<uint8_t> pcm; };
    std::deque<Pending> pending;
    SyntheticTransport(uint32_t rate,unsigned bits,uint64_t total) : format(dp::Format::create(rate,2,bits)),
        queue(true,format),decoded(format),scheduler(rate) { queue.expectedTotal=total; }
    void produce() {
        while(decoded.frames<queue.expectedTotal && queue.depth()<fv::kPrefixCapacity) {
            uint8_t frame[6]{};const auto pair=samples(decoded.frames,format->bitsPerSample);const auto index=decoded.frames;
            assert(decoded.append(pair.first,pair.second,frame));assert(queue.push(index,frame));
        }
        if(decoded.frames==queue.expectedTotal) queue.eofReached=true;
        assert(queue.depth()<=16384 && queue.bytes.size()==16384*format->bytesPerFrame);
    }
    bool submit(bool successful=true) {
        if(!state.admitsOut() || pending.size()==21 || submitted==queue.expectedTotal) return false;
        produce();const auto before=scheduler;
        std::array<uint32_t,8> normal{};for(auto& frames:normal) assert(scheduler.next(1,0,frames));
        const auto available=std::accumulate(normal.begin(),normal.end(),uint64_t{0});
        const auto count=std::min<uint64_t>(queue.expectedTotal-submitted,available);
        const auto shape=ui::terminalShape(normal,count,true);assert(shape.valid && shape.total==count);
        std::vector<uint8_t> pcm(count*format->bytesPerFrame);
        const auto oldHead=queue.head.load(),oldHash=queue.consumedHash;
        assert(queue.reserve(pcm.data(),count)==fv::ReserveResult::Available && queue.validate(pcm.data(),pcm.size()));
        assert(queue.head==oldHead && queue.consumedHash==oldHash);
        if(!successful) { queue.abandonReservation();scheduler=before;assert(queue.head==oldHead && queue.consumedHash==oldHash && !queue.prepared);return false; }
        pending.push_back({submitted,count,pcm});assert(queue.commit());
        submittedHash=fv::hashBytes(submittedHash,pcm.data(),pcm.size());submitted+=count;bytes+=pcm.size();state.submitted();
        assert(queue.head==submitted && queue.consumedHash==submittedHash);return true;
    }
    void complete() {
        assert(!pending.empty());const auto& urb=pending.front();assert(urb.first==completed);
        completed+=urb.frames;actual+=urb.pcm.size();pending.pop_front();
    }
    ui::PauseBoundary boundary() const {
        return {pending.size(),0,submitted,completed,queue.head.load(),queue.prepared,bytes,actual,queue.consumedHash,submittedHash,true};
    }
    void cycle() {
        const auto head=queue.head.load(),hash=queue.consumedHash,phase=scheduler.remainder;
        state.pause(++now);assert(!submit() && queue.head==head && queue.consumedHash==hash);
        if(!pending.empty()) assert(!state.confirmPause(++now,boundary()));
        while(!pending.empty()) complete();assert(state.confirmPause(++now,boundary()));
        produce();const auto tail=queue.tail.load();now+=15000000;
        assert(queue.head==head && scheduler.remainder==phase && queue.tail==tail);
        state.resume(now);assert(queue.head==head && scheduler.remainder==phase);
        assert(submit());assert(pending.back().first==head); // Exact next unsubmitted frame, not decoder-ahead position.
    }
};
static void allFormatsExactContinuity() {
    for(auto tuple : {std::pair<uint32_t,unsigned>{44100,16},{48000,16},{48000,24},{96000,24}}) {
        const uint64_t total=2*fv::kPrefixCapacity+53;
        SyntheticTransport transport(tuple.first,tuple.second,total);fv::Pcm uninterrupted(transport.format);
        for(uint64_t frame=0;frame<total;++frame) { const auto pair=samples(frame,tuple.second);assert(uninterrupted.append(pair.first,pair.second)); }
        transport.produce();assert(transport.queue.depth()==16384 && fv::kPrefixPrebuffer==8192);
        assert(!transport.submit(false) && transport.queue.head==0 && transport.scheduler.frames==0);
        for(unsigned i=0;i<21;++i)assert(transport.submit());transport.cycle();
        while(transport.submitted<total/2) { while(!transport.pending.empty())transport.complete();for(unsigned i=0;i<21;++i)transport.submit(); }
        transport.cycle();
        while(transport.submitted<total) { while(!transport.pending.empty())transport.complete();for(unsigned i=0;i<21;++i)transport.submit(); }
        // Pause at the final drain must allow actual natural EOF to win rather than strand a completed file.
        transport.state.pause(++transport.now);while(!transport.pending.empty())transport.complete();
        assert(ui::naturalOutComplete(true,total,total,total,transport.submitted,transport.completed,0,0,true));
        transport.state.finish(++transport.now,ui::PlaybackPhase::Ended);
        assert(transport.state.pauses==2 && transport.state.resumes==2 && transport.state.phase==ui::PlaybackPhase::Ended);
        assert(transport.decoded.frames==total && transport.bytes==total*transport.format->bytesPerFrame && transport.actual==transport.bytes);
        assert(transport.decoded.hash==uninterrupted.hash && transport.queue.enqueuedHash==uninterrupted.hash &&
            transport.queue.consumedHash==uninterrupted.hash && transport.submittedHash==uninterrupted.hash);
        assert(fv::classify(true,true,true,true,true,true,true,true,true,total,transport.decoded,0,0,true)==fv::Outcome::Exact);
        assert(fv::classify(true,true,true,true,true,true,true,true,false,total,transport.decoded,0,0,true)==fv::Outcome::Md5Mismatch);
        assert(!ui::naturalOutComplete(true,total,total,total,total,total,0,0,false));
        assert(ui::outUrbCount(125,3)==21 && ui::kOutPackets==8 && ui::feedbackUrbCount(1000)==21);
    }
}
static void backpressureAndWake() {
    auto format=dp::Format::create(48000,2,24);uint8_t frame[6]{1,2,3,4,5,6};
    for(bool stop : {false,true}) {
        fv::PrefixQueue queue(false,format);
        for(uint64_t i=0;i<16384;++i) assert(queue.push(i,frame));
        auto pushed=std::async(std::launch::async,[&]{return queue.push(16384,frame);});
        assert(pushed.wait_for(std::chrono::milliseconds(10))==std::future_status::timeout);
        if(stop) queue.requestStop();
        else { uint8_t pcm[6];assert(queue.reserve(pcm,1)==fv::ReserveResult::Available);assert(queue.validate(pcm,6) && queue.commit()); }
        assert(pushed.wait_for(std::chrono::seconds(1))==std::future_status::ready && pushed.get()==!stop);
    }
    ui::WakeSignal wake;pollfd descriptor{wake.fd,POLLIN,0};
    assert(wake.notify() && ::poll(&descriptor,1,0)==1);wake.consume();descriptor.revents=0;
    assert(::poll(&descriptor,1,0)==0 && !wake.failed.load());
    fv::PrefixQueue source(false,format);auto signal=std::make_shared<ui::WakeSignal>();std::atomic_store(&source.workerWake,signal);
    source.fail(fv::PrefixDecode);pollfd fault{signal->fd,POLLIN,0};assert(::poll(&fault,1,0)==1);
}
static void resumedFeedbackEpochAndRationalPhase() {
    for(uint32_t rate : {44100U,48000U,96000U}) {
        ui::Scheduler scheduler(rate);uint32_t count=0;
        const auto validRaw=static_cast<uint32_t>(static_cast<uint64_t>(rate)*65536/8000);
        for(unsigned i=0;i<17;++i)assert(scheduler.next(1,validRaw,count));
        const auto before=scheduler;
        ui::FeedbackObservationClock feedback;feedback.started=1;feedback.accept(2,3);
        assert(feedback.expired(15000003,20000)==ui::FeedbackTimeoutKind::LastValidWatchdog);
        // Resume discards pre-pause feedback evidence, while preserving the production rational scheduler.
        feedback={};feedback.started=15000003;
        assert(!feedback.lastObserved && !feedback.lastSubmitted);
        assert(feedback.expired(feedback.started+99999,20000)==ui::FeedbackTimeoutKind::None);
        assert(feedback.expired(feedback.started+100000,20000)==ui::FeedbackTimeoutKind::Bootstrap);
        assert(scheduler.remainder==before.remainder && scheduler.frames==before.frames);
        assert(scheduler.next(1,0,count)); // Proven selected-rate nominal bootstrap, never old raw feedback.
        const uint8_t bytes[4]{static_cast<uint8_t>(validRaw),static_cast<uint8_t>(validRaw>>8),
            static_cast<uint8_t>(validRaw>>16),static_cast<uint8_t>(validRaw>>24)};
        assert(ui::decodeFeedback(bytes,4,0,rate).validity==ui::FeedbackValidity::Valid);
        feedback.accept(feedback.started+1,feedback.started+1000);
        assert(feedback.expired(feedback.started+21000,20000)==ui::FeedbackTimeoutKind::None);
        assert(feedback.expired(feedback.started+21001,20000)==ui::FeedbackTimeoutKind::LastValidWatchdog);
        assert(ui::outPacketProblem(0,0,6,5,false)&ui::ShortPacket);
        assert(ui::outPacketProblem(-1,0,6,6,false)&ui::UrbError);
        // Cancellation exemptions remain shutdown-only: PauseBoundary validity must never accept them as clean drain.
        ui::PauseMachine state;state.pause(1);
        const ui::PauseBoundary bad{0,0,1,0,1,0,6,0,1,1,false};assert(!state.confirmPause(2,bad));
    }
}
int main() { transitionsAndFaults();allFormatsExactContinuity();backpressureAndWake();resumedFeedbackEpochAndRationalPhase(); }
