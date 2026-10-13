// Standalone native suite, NOT testDebugUnitTest. Link the unchanged vendored decoder-only libFLAC.
// Finite test-only FLAC fixture with verbatim subframes, independent CRCs and a genuine STREAMINFO MD5.
// This tests real libFLAC callbacks/producer/ring; it does not simulate a physical USB device.
#include "../../main/cpp/flac/flac_prefix_queue.h"
extern "C" {
#include "../../main/cpp/third_party/flac/upstream/src/libFLAC/include/private/md5.h"
}
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <functional>
#include <future>
#include <limits>
using namespace sazanami::flacverify;
static int32_t sample(uint64_t frame,unsigned channel,unsigned bits) {
    const int32_t limit=bits==16?32768:8388608;
    return static_cast<int32_t>((frame*37+channel*901)%static_cast<uint64_t>(limit*2))-limit;
}
static void be(std::vector<uint8_t>& bytes,uint64_t value,unsigned count) {
    while(count) bytes.push_back(static_cast<uint8_t>(value>>(--count*8)));
}
static void utf8(std::vector<uint8_t>& bytes,uint64_t value) {
    assert(value<65536);
    if(value<128) bytes.push_back(static_cast<uint8_t>(value));
    else if(value<2048) {bytes.push_back(static_cast<uint8_t>(0xc0|(value>>6)));bytes.push_back(static_cast<uint8_t>(0x80|(value&63)));}
    else {bytes.push_back(static_cast<uint8_t>(0xe0|(value>>12)));bytes.push_back(static_cast<uint8_t>(0x80|((value>>6)&63)));bytes.push_back(static_cast<uint8_t>(0x80|(value&63)));}
}
static unsigned crc(const uint8_t* bytes,size_t length,unsigned bits,unsigned polynomial) {
    unsigned value=0,mask=(1U<<bits)-1;
    for(size_t i=0;i<length;++i) {
        value^=static_cast<unsigned>(bytes[i])<<(bits-8);
        for(unsigned b=0;b<8;++b) value=((value<<1)^((value&(1U<<(bits-1)))?polynomial:0))&mask;
    }
    return value;
}
static std::vector<uint8_t> fixture(unsigned rate,unsigned bits,uint64_t total) {
    assert(total && total<=32768 && total%512==0);
    std::vector<int32_t> left(total),right(total);
    for(uint64_t i=0;i<total;++i) {left[i]=sample(i,0,bits);right[i]=sample(i,1,bits);}
    const int32_t* channels[]{left.data(),right.data()};
    FLAC__MD5Context context{};FLAC__MD5Init(&context);
    assert(FLAC__MD5Accumulate(&context,channels,2,static_cast<uint32_t>(total),bits/8));
    std::array<uint8_t,16> md5{};FLAC__MD5Final(md5.data(),&context);
    std::vector<uint8_t> bytes{'f','L','a','C',0x80,0,0,34};
    be(bytes,512,2);be(bytes,512,2);be(bytes,0,3);be(bytes,0,3);
    be(bytes,(static_cast<uint64_t>(rate)<<44)|(1ULL<<41)|(static_cast<uint64_t>(bits-1)<<36)|total,8);
    bytes.insert(bytes.end(),md5.begin(),md5.end());
    for(uint64_t first=0;first<total;first+=512) {
        const auto start=bytes.size();
        bytes.insert(bytes.end(),{0xff,0xf9,0x70,0x10});utf8(bytes,first);be(bytes,511,2);
        bytes.push_back(static_cast<uint8_t>(crc(bytes.data()+start,bytes.size()-start,8,0x07)));
        for(unsigned channel=0;channel<2;++channel) {
            bytes.push_back(0x02); // VERBATIM, no wasted bits.
            for(uint64_t i=first;i<first+512;++i) be(bytes,static_cast<uint32_t>(sample(i,channel,bits)),bits/8);
        }
        const auto footer=crc(bytes.data()+start,bytes.size()-start,16,0x8005);be(bytes,footer,2);
    }
    return bytes;
}
static void writeAll(int fd,const std::vector<uint8_t>& bytes) {
    size_t done=0;
    while(done<bytes.size()) {const auto count=::write(fd,bytes.data()+done,bytes.size()-done);assert(count>0);done+=static_cast<size_t>(count);}
}
static OwnedFd file(const std::vector<uint8_t>& bytes) {
    char path[]="/tmp/sazanami-flac-seek-XXXXXX";
    const auto fd=::mkstemp(path);assert(fd>=0);assert(::unlink(path)==0);
    writeAll(fd,bytes);assert(::lseek(fd,0,SEEK_SET)==0);return OwnedFd(fd);
}
constexpr uint64_t kUnavailableContext=std::numeric_limits<uint64_t>::max();
struct AwaitContext {
    const char* scenario;
    unsigned rate,bits;
    uint64_t target=kUnavailableContext,serial=kUnavailableContext;
};
struct AwaitDemand {
    uint64_t queueFrames=kUnavailableContext,consumptionEnd=kUnavailableContext,remainingFrames=kUnavailableContext;
};
static void printContextValue(const char* name,uint64_t value) {
    if(value==kUnavailableContext) std::fprintf(stderr," %s=unavailable",name);
    else std::fprintf(stderr," %s=%llu",name,static_cast<unsigned long long>(value));
}
static void reportTimeout(const PrefixSource& source,const AwaitContext& context,const char* phase,
    const char* predicate,std::chrono::steady_clock::duration elapsed,const AwaitDemand& demand) {
    std::fprintf(stderr,"FLAC_SEEK_TIMEOUT scenario=%s rate=%u bits=%u phase=%s elapsed_ms=%lld deadline_ms=3000 poll_ms=1\n",
        context.scenario,context.rate,context.bits,phase,
        static_cast<long long>(std::chrono::duration_cast<std::chrono::milliseconds>(elapsed).count()));
    std::fprintf(stderr,"predicate=%s\ncontext:",predicate);
    printContextValue("target_frame",context.target);printContextValue("requested_serial",context.serial);
    printContextValue("requested_queue_frames",demand.queueFrames);
    printContextValue("consumption_end_frame",demand.consumptionEnd);
    printContextValue("remaining_consumption_frames",demand.remainingFrames);
    // Each load is safe independently; producer progress can occur between loads.
    // Do not inspect decoder facts/PCM, staged buffers or producer hashes here.
    const auto& queue=source.queue;
    const auto head=queue.head.load(),tail=queue.tail.load(),base=queue.sourceBase.load();
    const auto faults=queue.faults.load();
    const std::pair<const char*,uint64_t> snapshot[]{
        {"metadata_ready",source.metadataReady.load()},{"producer_stopped",source.stopped.load()},
        {"producer_joined",source.joined.load()},{"initial_primed_frames",source.primed.load()},
        {"completed_seek_serial",source.seekDoneSerial.load()},{"seek_attempts",source.attempted.load()},
        {"seek_failures",source.seekFailures.load()},{"seek_first_frame",source.seekFirst.load()},
        {"seek_first_proven",source.seekFirstProven.load()},{"staged_discarded",source.stagedDiscarded.load()},
        {"head",head},{"tail",tail},{"source_base",base},{"source_epoch",queue.sourceEpoch.load()},
        {"expected_total",queue.expectedTotal.load()},{"queue_decoded_counter",queue.decoded.load()},
        {"enqueued_total",queue.enqueuedTotal.load()},{"consumed_total",queue.consumedTotal.load()},
        {"invalidated_queued",queue.invalidatedQueued.load()},{"full_waits",queue.fullWaits.load()},
        {"stop_requested",queue.stop.load()},{"quiescence_requested",queue.quiesceRequested.load()},
        {"eof_reached",queue.eofReached.load()}
    };
    std::fprintf(stderr,"\napproximate_atomic_snapshot (independent loads): seek_phase=%d faults=0x%llx",
        source.seekPhase.load(),static_cast<unsigned long long>(faults));
    for(const auto& field:snapshot) std::fprintf(stderr," %s=%llu",field.first,static_cast<unsigned long long>(field.second));
    printContextValue("depth_from_sampled_cursors",tail>=head?tail-head:kUnavailableContext);
    printContextValue("source_head_from_samples",base<=UINT64_MAX-head?base+head:kUnavailableContext);
    std::fprintf(stderr,"\nlive_decoder_result/state/pcm/lifecycle/staged_buffers/producer_hashes=unavailable (observer is not decoder owner)\n");
    std::fflush(stderr);
}
static void await(PrefixSource& source,const AwaitContext& context,const char* phase,const char* predicate,
    const std::function<bool()>& ready,AwaitDemand demand={}) {
    const auto started=std::chrono::steady_clock::now();
    const auto deadline=started+std::chrono::seconds(3);
    while(!ready()) {
        const auto now=std::chrono::steady_clock::now();
        if(now>=deadline) reportTimeout(source,context,phase,predicate,now-started,demand);
        assert(now<deadline);
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
}
static void reposition(PrefixSource& source,uint64_t target,uint64_t serial,AwaitContext& context) {
    context.target=target;context.serial=serial; // Observer-owned diagnostic context only.
    source.requestQuiescence();source.requestQuiescence(); // Duplicate request cannot overwrite an existing acknowledgement.
    await(source,context,"reposition-quiescence-ack","seekPhase == 2 || faults != 0",
        [&]{return source.seekPhase.load(std::memory_order_acquire)==2 || source.queue.faults.load();});
    source.requestQuiescence();assert(source.seekPhase.load()==2); // Already-parked acknowledgement survives supersession.
    assert(!source.queue.faults.load());assert(source.queue.resetEpoch(target,serial));
    assert(source.authorizeSeek(serial,target));
    await(source,context,"reposition-seek-completion","seekDoneSerial == requestedSerial || faults != 0",
        [&]{return source.seekDoneSerial.load(std::memory_order_acquire)==serial || source.queue.faults.load();});
    assert(!source.queue.faults.load() && source.seekFirstProven && source.seekFirst==target);
    const auto prime=std::min<uint64_t>(8192,source.queue.expectedTotal.load()-target);
    await(source,context,"reposition-source-prime","depth >= prime && (prime == 8192 || eofReached)",
        [&]{return source.queue.depth()>=prime && (prime==8192 || source.queue.eofReached.load(std::memory_order_acquire));},{prime});
    assert(source.queue.sourceHead()==target);
}
static void consume(PrefixSource& source,uint64_t count,const AwaitContext& context) {
    const auto width=source.queue.frameBytes(),bits=source.queue.format->bitsPerSample;
    const auto consumptionEnd=source.queue.sourceHead()+count;
    std::vector<uint8_t> payload(256*width);
    while(count) {
        const auto amount=std::min<uint64_t>(count,256);
        await(source,context,"consume-next-chunk","depth >= amount || faults != 0",
            [&]{return source.queue.depth()>=amount || source.queue.faults.load();},{amount,consumptionEnd,count});assert(!source.queue.faults.load());
        const auto first=source.queue.sourceHead();
        assert(source.queue.reserve(payload.data(),amount)==ReserveResult::Available && source.queue.validate(payload.data(),amount*width));
        for(uint64_t i=0;i<amount;++i) for(unsigned ch=0;ch<2;++ch)
            assert(sazanami::directpcm::unpackSample(payload.data()+i*width+ch*(bits/8),bits/8)==sample(first+i,ch,bits));
        assert(source.queue.commit()); // Test models an already-successful submission, no ioctl.
        count-=amount;
    }
}
static void finishBounded(PrefixSource& source,const AwaitContext& context) {
    auto finished=std::async(std::launch::async,[&]{source.finish();});
    await(source,context,"producer-shutdown","finish() returned after producer join",
        [&]{return finished.wait_for(std::chrono::seconds(0))==std::future_status::ready;});
    finished.get(); // Join completion precedes all existing decoder/cleanup assertions.
}
static void missingReferenceCompatibility() {
    for(auto tuple:{std::pair<unsigned,unsigned>{44100,16},{48000,16},{48000,24},{96000,24}}) {
        auto bytes=fixture(tuple.first,tuple.second,4096);
        // STREAMINFO begins at byte 8; only its sixteen reference bytes change.
        std::fill(bytes.begin()+26,bytes.begin()+42,0);
        AwaitContext context{"missing-reference-eof",tuple.first,tuple.second};
        auto source=std::make_unique<PrefixSource>(file(bytes),61,62,true);
        source->start();assert(source->prime());consume(*source,4096,context);finishBounded(*source,context);
        assert(!source->queue.faults && source->queue.eofReached && source->decoder.sequentialDecodeComplete());
        assert(source->decoder.failure==Outcome::Md5Unavailable && source->decoder.facts[Md5Available]==0 && source->decoder.facts[Md5Verification]==0);
        assert(source->decoder.pcm.frames==4096 && source->decoder.pcm.bytes==4096*source->queue.frameBytes());
        assert(source->queue.consumedTotal==4096 && source->queue.depth()==0);
        assert(source->queue.enqueuedHash==source->queue.consumedHash && source->queue.consumedHash==source->decoder.pcm.hash);
        assert(source->joined && source->decoder.facts[FdClosed] && source->decoder.facts[DecoderDeleted]);

        auto longBytes=fixture(tuple.first,tuple.second,32768);
        std::fill(longBytes.begin()+26,longBytes.begin()+42,0);
        context={"missing-reference-seek",tuple.first,tuple.second};
        source=std::make_unique<PrefixSource>(file(longBytes),63,64,true);
        source->start();assert(source->prime() && source->seekCapable());consume(*source,512,context);
        reposition(*source,10037,1,context);consume(*source,32768-10037,context);
        await(*source,context,"missing-reference-seek-eof","eofReached",[&]{return source->queue.eofReached.load(std::memory_order_acquire);});
        finishBounded(*source,context);
        assert(!source->queue.faults && source->decoder.failure==Outcome::SeekedEof && source->decoder.originalNext==32768);
        assert(source->seekFirstProven && source->seekFirst==10037 && source->queue.sourceEpoch==1);
        assert(source->queue.consumedTotal==512+32768-10037 && source->queue.sourceHead()==32768);
        assert(source->decoder.facts[Md5Available]==0 && source->decoder.facts[Md5Verification]==0 && !source->decoder.sequentialDecodeComplete());

        context={"missing-reference-stop",tuple.first,tuple.second};
        source=std::make_unique<PrefixSource>(file(longBytes),69,70,true);
        source->start();assert(source->prime());consume(*source,512,context);finishBounded(*source,context);
        assert(!source->queue.eofReached && !source->decoder.sequentialDecodeComplete());
        assert(source->decoder.failure==Outcome::Cancelled && source->decoder.facts[Md5Verification]==0);

        // Valid frames with a wrong, nonzero reference fail only at final verification.
        auto mismatch=fixture(tuple.first,tuple.second,4096);mismatch[26]^=1;
        context={"nonzero-reference-mismatch",tuple.first,tuple.second};
        source=std::make_unique<PrefixSource>(file(mismatch),65,66,true);
        source->start();assert(!source->prime());finishBounded(*source,context);
        assert(source->queue.faults.load()&PrefixMd5Mismatch);
        assert(source->decoder.pcm.frames==4096 && source->decoder.facts[Md5Verification]==2 && !source->decoder.sequentialDecodeComplete());

        for(unsigned damage=0;damage<3;++damage) {
            auto invalid=bytes;
            if(damage==0) invalid.back()^=1; // Frame CRC.
            else if(damage==1) invalid.resize(invalid.size()-128); // Truncated audio.
            else {invalid[21]&=0xf0;std::fill(invalid.begin()+22,invalid.begin()+26,0);} // Unknown 36-bit total.
            context={damage==0?"missing-reference-crc":damage==1?"missing-reference-truncated":"missing-reference-unknown-total",tuple.first,tuple.second};
            source=std::make_unique<PrefixSource>(file(invalid),67,68,true);
            source->start();assert(!source->prime());finishBounded(*source,context);
            assert(source->queue.faults && !source->queue.eofReached && !source->decoder.sequentialDecodeComplete());
            if(damage==2) assert(source->queue.faults.load()&PrefixTotal);
            else assert(source->decoder.failure!=Outcome::Exact && source->decoder.failure!=Outcome::Md5Unavailable);
            assert(source->joined && source->decoder.facts[FdClosed] && source->decoder.facts[DecoderDeleted]);
        }
    }
}
static void predicateTransitionWakeups() {
    // Finite waiters exercise the production publication APIs, not a decoder/USB worker.
    // Handshakes put publication next to a false predicate check; no timing sleeps/hooks.
    enum class Transition { Authorize, Quiesce, StopAuthorization, StopEof, FaultStop };
    for(auto transition:{Transition::Authorize,Transition::Quiesce,Transition::StopAuthorization,Transition::StopEof,Transition::FaultStop}) {
        const bool authorization=transition==Transition::Authorize || transition==Transition::StopAuthorization || transition==Transition::FaultStop;
        PrefixSource source(OwnedFd(-1),51,52,true);
        source.seekPhase=authorization?2:4;
        source.queue.quiesceRequested=authorization;
        source.queue.eofReached=!authorization;
        const char* scenario=transition==Transition::Authorize?"protocol-authorization":
            transition==Transition::Quiesce?"protocol-retained-eof-quiescence":
            transition==Transition::StopAuthorization?"protocol-authorization-stop":
            transition==Transition::StopEof?"protocol-retained-eof-stop":"protocol-fault-stop";
        const AwaitContext context{scenario,48000,16,10037,1};
        std::promise<void> checked,enteringPublication;
        auto checkedFuture=checked.get_future(),enteringFuture=enteringPublication.get_future();
        auto waiter=std::async(std::launch::async,[&] {
            std::unique_lock<std::mutex> lock(source.queue.waitMutex);
            bool announced=false;
            source.queue.changed.wait(lock,[&] {
                const bool ready=source.queue.stop.load() || (authorization?
                    source.seekPhase.load(std::memory_order_acquire)==3:source.queue.quiesceRequested.load());
                if(ready) return true;
                if(!announced) {
                    announced=true;checked.set_value();
                    // This handshake is signalled BEFORE the publisher tries waitMutex.
                    // It never waits for a transition requiring this held mutex.
                    enteringFuture.wait();
                }
                return false;
            });
        });
        auto publisher=std::async(std::launch::async,[&] {
            checkedFuture.wait();enteringPublication.set_value();
            if(transition==Transition::Authorize) assert(source.authorizeSeek(1,10037));
            else if(transition==Transition::Quiesce) source.requestQuiescence();
            else if(transition==Transition::FaultStop) source.queue.fail(PrefixDecode);
            else source.queue.requestStop();
        });
        await(source,context,"predicate-transition-wakeup","untimed waiter and publisher both returned",
            [&]{return waiter.wait_for(std::chrono::seconds(0))==std::future_status::ready &&
                publisher.wait_for(std::chrono::seconds(0))==std::future_status::ready;});
        waiter.get();publisher.get();
        if(transition==Transition::Authorize) {
            assert(source.seekPhase==3 && !source.queue.quiesceRequested && !source.queue.stop);
            assert(source.seekSerial==1 && source.seekTarget==10037); // Publisher is joined; plain fields are safe.
        } else if(transition==Transition::Quiesce) assert(source.seekPhase==1 && source.queue.quiesceRequested && !source.queue.stop);
        else {
            assert(source.queue.stop && !source.authorizeSeek(2,37));
            if(transition==Transition::FaultStop) assert(source.queue.faults.load()&PrefixDecode);
        }
        assert(source.attempted==0); // This protocol harness never starts the actual decoder.
    }
}
int main() {
    missingReferenceCompatibility();
    for(auto tuple: {std::pair<unsigned,unsigned>{44100,16},{48000,16},{48000,24},{96000,24}}) {
        AwaitContext context{"absolute-seeks",tuple.first,tuple.second};
        auto source=std::make_unique<PrefixSource>(file(fixture(tuple.first,tuple.second,32768)),1,2,true);
        const auto observerThread=std::this_thread::get_id();std::atomic<unsigned> ownerCallbacks{0};
        source->decoder.stopRequested=[&]{assert(std::this_thread::get_id()!=observerThread);++ownerCallbacks;return source->queue.stop.load();};
        source->start();assert(source->prime() && source->seekCapable());consume(*source,1024,context);
        reposition(*source,10037,1,context);consume(*source,512,context); // Inside block: no prefix of the containing block is emitted.
        const auto cumulative=source->queue.consumedTotal.load();
        reposition(*source,37,2,context);assert(source->queue.consumedTotal==cumulative);consume(*source,600,context);
        reposition(*source,0,3,context);consume(*source,256,context);
        reposition(*source,32767,4,context);consume(*source,1,context);
        await(*source,context,"last-frame-eof","eofReached",[&]{return source->queue.eofReached.load(std::memory_order_acquire);});
        // Producer stays alive at real EOF so a queued tail/paused session can seek on the same owner.
        assert(!source->stopped && !source->joined);
        reposition(*source,123,5,context);consume(*source,32768-123,context);
        await(*source,context,"retained-eof-suffix","eofReached",[&]{return source->queue.eofReached.load(std::memory_order_acquire);});finishBounded(*source,context);
        assert(ownerCallbacks.load()>0); // Read/write/seek cancellation probes ran only on the producer owner.
        assert(source->decoder.failure==Outcome::SeekedEof && source->decoder.originalNext==32768 && source->decoder.facts[Md5Verification]==0);
        auto evidence=source->evidence(true);
        assert(evidence[PConsumed]>0 && evidence[PEnqueued]-evidence[PConsumed]==evidence[PInvalidatedQueued]);
        assert(evidence[PDecoded]-evidence[PEnqueued]==evidence[PInvalidatedCanonical]);
        assert(evidence[PInvalidatedCanonical]>=0 && evidence[PInvalidatedRaw]>=0 && evidence[PDecoderReleased]==1 && evidence[PFdClosed]==1);
        // Several newer targets while the actual producer is parked: authorize only the latest, on the same decoder owner.
        context={"latest-authorized-target",tuple.first,tuple.second,18000,3};
        source=std::make_unique<PrefixSource>(file(fixture(tuple.first,tuple.second,32768)),15,16,true);
        source->start();assert(source->prime());source->requestQuiescence();
        await(*source,context,"latest-target-quiescence-ack","seekPhase == 2",[&]{return source->seekPhase==2;});
        for(uint64_t target : {10000ULL,37ULL,18000ULL}) {
            source->requestQuiescence();assert(source->seekPhase==2 && source->attempted==0);
            assert(target<source->queue.expectedTotal); // No decoder call or source release before worker authorization.
        }
        assert(source->queue.resetEpoch(18000,3) && source->authorizeSeek(3,18000));
        await(*source,context,"latest-target-seek-completion","seekDoneSerial == 3 || faults != 0",[&]{return source->seekDoneSerial==3 || source->queue.faults.load();});
        assert(!source->queue.faults && source->seekFirstProven && source->seekFirst==18000 && source->attempted==1);
        consume(*source,128,context);
        source->requestQuiescence();
        await(*source,context,"stop-park-ack","seekPhase == 2",[&]{return source->seekPhase==2;});finishBounded(*source,context); // Stop wakes a parked seek owner.
        assert(source->joined && source->decoder.facts[FdClosed]==1 && source->decoder.facts[DecoderDeleted]==1);
        assert(source->decoder.facts[Md5Verification]==0);
        // Uninterrupted, short-file EOF still demands real MD5 and the original whole-file result.
        context={"short-file-md5",tuple.first,tuple.second};
        source=std::make_unique<PrefixSource>(file(fixture(tuple.first,tuple.second,4096)),3,4,true);
        source->start();assert(source->prime());consume(*source,4096,context);source->finish();
        assert(source->decoder.failure==Outcome::Exact && source->decoder.facts[Md5Verification]==1 && source->decoder.sequentialDecodeComplete());
        assert(source->queue.consumedHash==source->decoder.pcm.hash && source->decoder.pcm.frames==4096);
        context={"uninterrupted-backpressure-md5",tuple.first,tuple.second};
        source=std::make_unique<PrefixSource>(file(fixture(tuple.first,tuple.second,32768)),3,4,true);
        source->start();assert(source->prime());consume(*source,1024,context);
        await(*source,context,"backpressure-full-ring","depth == 16384 && fullWaits > 0",
            [&]{return source->queue.depth()==16384 && source->queue.fullWaits.load()>0;},{16384});
        const auto pausedHash=source->queue.consumedHash,pausedFrame=source->queue.sourceHead();
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
        assert(source->queue.consumedHash==pausedHash && source->queue.sourceHead()==pausedFrame);
        consume(*source,32768-1024,context);
        await(*source,context,"uninterrupted-eof","eofReached",[&]{return source->queue.eofReached.load(std::memory_order_acquire);});source->finish();
        assert(source->decoder.failure==Outcome::Exact && source->decoder.facts[Md5Verification]==1 && source->decoder.sequentialDecodeComplete() && source->queue.consumedHash==source->decoder.pcm.hash);
    }
    AwaitContext context{"invalid-bound",48000,24,4096,1};
    auto source=std::make_unique<PrefixSource>(file(fixture(48000,24,4096)),5,6,true);
    source->start();assert(source->prime());source->requestQuiescence();
    await(*source,context,"invalid-bound-quiescence-ack","seekPhase == 2",[&]{return source->seekPhase==2;});
    assert(!source->queue.resetEpoch(4096,1));finishBounded(*source,context);assert(source->joined); // Invalid bound, Stop wakes parked producer.
    int pipeFd[2];assert(::pipe(pipeFd)==0);
    const auto bytes=fixture(48000,16,4096);
    std::thread writer([&]{writeAll(pipeFd[1],bytes);::close(pipeFd[1]);});
    context={"sequential-pipe-md5",48000,16};
    source=std::make_unique<PrefixSource>(OwnedFd(pipeFd[0]),7,8,true);source->start();assert(source->prime());
    assert(!source->seekCapable());consume(*source,4096,context);source->finish();writer.join();
    assert(source->decoder.failure==Outcome::Exact && source->decoder.facts[Md5Verification]==1);
    Verification failed(OwnedFd(-1),[]{return false;});failed.facts[Seekable]=1;failed.facts[FileSize]=100;
    assert(Verification::seek(nullptr,0,&failed)==FLAC__STREAM_DECODER_SEEK_STATUS_ERROR && failed.facts[ReadErrno]==EBADF);
    failed.facts[Seekable]=0;assert(Verification::seek(nullptr,0,&failed)==FLAC__STREAM_DECODER_SEEK_STATUS_UNSUPPORTED);
    context={"provider-lost-random-access",48000,16,37,1};
    source=std::make_unique<PrefixSource>(file(fixture(48000,16,32768)),9,10,true);
    source->start();assert(source->prime());source->requestQuiescence();
    await(*source,context,"provider-quiescence-ack","seekPhase == 2",[&]{return source->seekPhase==2;});
    assert(::pipe(pipeFd)==0);
    // Provider loses random access after its initial nondestructive probe: replace FD only while owner is quiescent.
    assert(::dup2(pipeFd[0],source->decoder.input.value)==source->decoder.input.value);::close(pipeFd[0]);::close(pipeFd[1]);
    assert(source->queue.resetEpoch(37,1) && source->authorizeSeek(1,37));
    await(*source,context,"provider-seek-fault","faults != 0",[&]{return source->queue.faults.load()!=0;});source->finish();
    assert(source->seekFailures==1 && (source->queue.faults.load()&PrefixSeek) && source->decoder.failure!=Outcome::Exact && source->decoder.failure!=Outcome::SeekedEof);
    assert(source->decoder.failure==Outcome::DecoderError); // Callback error is not relabeled Cancelled by its Stop wake.
    auto badTarget=fixture(48000,24,32768);badTarget.back()^=1;
    context={"corrupt-target",48000,24,32767,1};
    source=std::make_unique<PrefixSource>(file(badTarget),11,12,true);source->start();assert(source->prime());
    source->requestQuiescence();
    await(*source,context,"corrupt-target-quiescence-ack","seekPhase == 2",[&]{return source->seekPhase==2;});
    assert(source->queue.resetEpoch(32767,1) && source->authorizeSeek(1,32767));
    await(*source,context,"corrupt-target-seek-fault","faults != 0",[&]{return source->queue.faults.load()!=0;});source->finish();
    assert(!source->seekFirstProven && source->seekFailures.load()>0 && source->decoder.facts[Md5Verification]==0);
    // A corrupt containing target block cannot become a successful synthetic-silence target.
    auto corrupt=fixture(48000,16,4096);corrupt.back()^=1;
    source=std::make_unique<PrefixSource>(file(corrupt),9,10,true);source->start();assert(!source->prime());source->finish();
    assert(source->queue.faults.load() && source->decoder.facts[Md5Verification]!=1);
    predicateTransitionWakeups();
}
