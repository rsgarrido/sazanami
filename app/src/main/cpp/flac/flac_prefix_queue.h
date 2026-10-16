#pragma once
#include "flac_decode_core.h"
#include <atomic>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <chrono>
#include <vector>
#include "../usb_wake_signal.h"

namespace sazanami::flacverify {
inline uint64_t sourceObservationUs() noexcept {
    return static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count());
}
constexpr size_t kPrefixCapacity = 16384, kPrefixPrebuffer = 8192;
constexpr uint64_t kPrefixDurationUs = 5000000;
enum PrefixFault : uint64_t { PrefixDecode = 1, PrefixInit = 2, PrefixFormat = 4, PrefixRange = 8,
    PrefixEof = 16, PrefixPrime = 32, PrefixUnderrun = 64, PrefixGap = 128, PrefixDuplicate = 256,
    PrefixOrder = 512, PrefixHash = 1024, PrefixCancelled = 2048, PrefixCleanup = 4096, PrefixInput = 8192, PrefixNotNative = 16384, PrefixMetadata = 32768, PrefixContradiction = 65536, PrefixMd5Unavailable = 131072, PrefixMd5Mismatch = 262144, PrefixTotal = 524288, PrefixTail = 1048576, PrefixSeek = 2097152 };
enum PrefixField : size_t { PApi, PAttachment, PStream, PFault, PPrimed, PMetadata, PRate, PChannels, PBits, PTotal,
    PDecoded, PEnqueued, PConsumed, PPrepared, PAtStop, PDiscarded, PMinDepth, PMaxDepth, PFullWaits, PEmpty,
    PUnderruns, PFirstEnqueued, PLastEnqueued, PFirstConsumed, PLastConsumed, PNextEnqueue, PNextConsume,
    PGaps, PDuplicates, POrder, PConsumedHash, PEnqueuedHash, PStopped, PJoined, PDecoderReleased,
    PFdClosed, PQueueReleased, PFull, PEof, PNaturalProducer, PMd5Pass, PTotalMatch,
    PSeekCapable, PSeekAttempts, PSeekFailures, PSourceEpoch, PSourceBase, POriginalNext, PInvalidatedQueued,
    PInvalidatedCanonical, PInvalidatedRaw, PSeekFirstFrame, PSeekFirstProven, PrefixFieldCount };
enum class ReserveResult { Available, InsufficientFrames, StopRequested, SourceFault, SourceEof, Cancelled, InvalidState, EndOfSource };
// Fixed canonical-byte SPSC ring. tail publishes producer writes; head publishes committed reads.
struct PrefixQueue {
    const bool fullTrack;
    std::atomic<uint64_t> expectedTotal{0};
    std::atomic<bool> eofReached{false};
    std::shared_ptr<const sazanami::directpcm::Format> format;
    std::vector<uint8_t> bytes;
    explicit PrefixQueue(bool full=false,std::shared_ptr<const sazanami::directpcm::Format> selected=sazanami::directpcm::referenceFormat()) : fullTrack(full) { if(selected) configure(std::move(selected)); }
    bool configure(std::shared_ptr<const sazanami::directpcm::Format> selected) {
        if(format || !selected || head.load() || tail.load() || kPrefixCapacity>SIZE_MAX/selected->bytesPerFrame) return false;
        bytes.resize(kPrefixCapacity*selected->bytesPerFrame); format=std::move(selected);return true;
    }
    uint32_t frameBytes() const { return format ? format->bytesPerFrame : 0; }
    std::array<uint64_t, kPrefixCapacity> indices{};
    std::atomic<uint64_t> head{0}, tail{0}, faults{0}, decoded{0}, fullWaits{0}, emptyEvents{0}, underruns{0};
    std::atomic<uint64_t> sourceBase{0},sourceEpoch{0},enqueuedTotal{0},consumedTotal{0},invalidatedQueued{0};
    std::atomic<uint64_t> firstPcmAtUs{0}; // One clock read per epoch, after its first successful publication.
    std::atomic<uint64_t> shortageDepth{0},shortageRequested{0},shortageAtUs{0}; // Consumer-only writes, on a failed reservation only.
    std::atomic<bool> quiesceRequested{false};
    uint64_t segmentConsumedHash=kOffset;
    uint64_t sourceHead() const { return sourceBase.load()+head.load(); }
    // Both owners must be quiescent; the worker calls this only after exact OUT drain and producer acknowledgement.
    bool resetEpoch(uint64_t target,uint64_t epoch) {
        if(!quiesceRequested.load() || prepared || target>=expectedTotal.load() || !epoch || epoch>kCounterMax || epoch<=sourceEpoch.load()) return false;
        const auto obsolete=depth();
        if(!canAdd(invalidatedQueued.load(),obsolete)) return false;
        invalidatedQueued.fetch_add(obsolete);head=0;tail=0;reservedStart=0;sourceBase=target;sourceEpoch=epoch;
        segmentConsumedHash=kOffset;eofReached=false;streaming=false;firstPcmAtUs=0;return true;
    }
    std::atomic<uint64_t> gaps{0}, duplicates{0}, orderErrors{0}, minDepth{kPrefixCapacity}, maxDepth{0};
    std::atomic<bool> stop{false}, streaming{false};
    std::mutex waitMutex;
    std::condition_variable changed;
    std::shared_ptr<sazanami::usbiso::WakeSignal> workerWake;
    void wakeWorker() { auto wake=std::atomic_load(&workerWake);if(wake) wake->notify(); }
    uint64_t consumedHash = kOffset, enqueuedHash = kOffset, prepared = 0, reservedStart = 0;
    std::array<std::array<int64_t,9>,5> examples{};
    size_t exampleCount = 0;
    uint64_t depth() const { return tail.load(std::memory_order_acquire) - head.load(std::memory_order_acquire); }
    void fail(uint64_t reason) { faults.fetch_or(reason); requestStop(); }
    void requestStop() {
        // Untimed producer waits check this predicate under waitMutex.
        { std::lock_guard<std::mutex> lock(waitMutex); stop.store(true); }
        changed.notify_all(); wakeWorker();
    }
    // Consumer-only rollback of an unsubmitted candidate. No source/hash advancement.
    void abandonReservation() { prepared=0;reservedStart=head.load(); }
    void trackDepth(uint64_t depth) {
        if (!streaming.load()) return;
        auto low = minDepth.load(); while (depth < low && !minDepth.compare_exchange_weak(low, depth)) {}
        auto high = maxDepth.load(); while (depth > high && !maxDepth.compare_exchange_weak(high, depth)) {}
    }
    bool identity(uint64_t actual, uint64_t expected) {
        if (actual == expected) return true;
        ++orderErrors;
        if (actual > expected) { ++gaps; fail(PrefixGap | PrefixOrder); }
        else { ++duplicates; fail(PrefixDuplicate | PrefixOrder); }
        return false;
    }
    bool push(uint64_t index, const uint8_t* canonical) {
        if (!format || !canonical) { fail(PrefixFormat);return false; }
        if (fullTrack && (!expectedTotal.load() || index>=expectedTotal.load())) { fail(PrefixTotal); return false; }
        if(!canAdd(decoded.load(),1)) {fail(PrefixOrder);return false;}decoded.fetch_add(1);
        const uint64_t position = tail.load(std::memory_order_relaxed);
        if (!identity(index, sourceBase.load()+position) || position >= kCounterMax / frameBytes() || !canAdd(enqueuedTotal.load(),1)) { fail(PrefixOrder); return false; }
        if (depth() == kPrefixCapacity && !stop.load()) {
            ++fullWaits;
            std::unique_lock<std::mutex> lock(waitMutex);
            // Atomic predicate + finite wait prevents lost-notification deadlock without locking the consumer.
            while (!stop.load() && !quiesceRequested.load() && depth() == kPrefixCapacity) changed.wait_for(lock, std::chrono::milliseconds(1));
        }
        if (stop.load() || faults.load() || quiesceRequested.load()) return false;
        const size_t slot = position % kPrefixCapacity;
        std::memcpy(bytes.data()+slot*frameBytes(), canonical, frameBytes()); indices[slot] = index;
        enqueuedHash = hashBytes(enqueuedHash, canonical, frameBytes());
        tail.store(position + 1, std::memory_order_release);
        if(position==0) firstPcmAtUs.store(sourceObservationUs(),std::memory_order_release);
        enqueuedTotal.fetch_add(1);
        const auto publishedDepth=depth();trackDepth(publishedDepth);changed.notify_all();
        if(publishedDepth==kPrefixPrebuffer) wakeWorker();
        return true;
    }
    ReserveResult availability() const {
        const auto reason = faults.load();
        if (reason & PrefixUnderrun) return ReserveResult::InsufficientFrames;
        if (reason & PrefixEof) return ReserveResult::SourceEof;
        if (reason & ~static_cast<uint64_t>(PrefixCancelled)) return ReserveResult::SourceFault;
        if (reason & PrefixCancelled) return ReserveResult::Cancelled;
        return stop.load() ? ReserveResult::StopRequested : ReserveResult::Available;
    }
    ReserveResult requireFrames(uint64_t count) {
        const auto state=availability();
        if (state!=ReserveResult::Available) return state;
        if (!count || count>kPrefixCapacity || (fullTrack && (sourceHead()>expectedTotal.load() || count>expectedTotal.load()-sourceHead())))
            return ReserveResult::InvalidState;
        const auto available=depth();
        if (available>=count) return ReserveResult::Available;
        const auto current=availability();
        if (current!=ReserveResult::Available) return current;
        if (fullTrack && eofReached.load(std::memory_order_acquire)) return ReserveResult::EndOfSource;
        shortageDepth.store(available);shortageRequested.store(count);shortageAtUs.store(sourceObservationUs());
        ++emptyEvents; ++underruns; fail(PrefixUnderrun); return ReserveResult::InsufficientFrames;
    }
    ReserveResult reserve(uint8_t* payload, uint64_t count) {
        const auto state = availability();
        if (state != ReserveResult::Available) return state;
        if (!format || !payload || prepared || !count || count > kPrefixCapacity) return ReserveResult::InvalidState;
        const uint64_t position = head.load(std::memory_order_relaxed);
        const auto available=requireFrames(count);
        if (available!=ReserveResult::Available) return available;
        reservedStart = position; prepared = count;
        for (uint64_t i = 0; i < count; ++i) {
            const size_t slot = (position + i) % kPrefixCapacity;
            if (!identity(indices[slot], sourceBase.load()+position + i)) return ReserveResult::SourceFault;
            std::memcpy(payload + i*frameBytes(), bytes.data()+slot*frameBytes(), frameBytes());
        }
        return ReserveResult::Available;
    }
    bool validate(const uint8_t* payload, size_t length) {
        if (!format || length != prepared * frameBytes() || reservedStart != head.load() || faults.load()) return false;
        for (uint64_t i = 0; i < prepared; ++i)
            if (std::memcmp(payload + i*frameBytes(), bytes.data()+((reservedStart+i)%kPrefixCapacity)*frameBytes(), frameBytes())) { fail(PrefixHash); return false; }
        return true;
    }
    // Called ONLY after successful USBDEVFS_SUBMITURB; failed submission leaves head/hash untouched.
    bool commit() {
        if (!format || !prepared || reservedStart != head.load()) { fail(PrefixOrder); return false; }
        if(!canAdd(consumedTotal.load(),prepared)) {fail(PrefixOrder);return false;}
        const auto epoch=sourceEpoch.load();
        for (uint64_t i = 0; i < prepared; ++i) {
            const auto* frame = bytes.data()+((reservedStart+i)%kPrefixCapacity)*frameBytes();
            consumedHash = hashBytes(consumedHash, frame, frameBytes());
            segmentConsumedHash=epoch?hashBytes(segmentConsumedHash,frame,frameBytes()):consumedHash;
            const uint64_t index = epoch?consumedTotal.load()+i:reservedStart+i;
            if (index < 4 || i == prepared-1) {
                const size_t slot = index < 4 ? static_cast<size_t>(index) : 4;
                examples[slot]={};examples[slot][0]=static_cast<int64_t>(index);
                examples[slot][1]=sazanami::directpcm::unpackSample(frame,format->bytesPerSample);
                examples[slot][2]=sazanami::directpcm::unpackSample(frame+format->bytesPerSample,format->bytesPerSample);
                for(uint32_t b=0;b<frameBytes();++b) examples[slot][3+b]=frame[b];
                exampleCount = std::max(exampleCount,slot+1);
            }
        }
        head.store(reservedStart+prepared,std::memory_order_release); prepared=0;
        consumedTotal.store(consumedTotal.load()+head.load()-reservedStart);
        trackDepth(depth()); changed.notify_all(); return true;
    }
};
struct PrefixSource {
    const uint64_t attachment, stream;
    const bool fullTrack;
    const uint64_t admittedAtUs=sourceObservationUs();
    std::atomic<uint64_t> metadataAtUs{0},readyAtUs{0};
    PrefixQueue queue;
    Verification decoder;
    std::array<int64_t, FieldCount> metadataFacts{};
    std::atomic<bool> metadataReady{false}, stopped{false}, joined{false};
    std::thread producer;
    std::atomic<uint64_t> primed{0};
    std::mutex finishMutex;
    uint64_t atStop=0, discarded=0;
    // Producer acknowledgement (2) precedes worker queue reset; authorization (3) precedes decoder seek.
    std::atomic<int> seekPhase{0};
    uint64_t seekSerial=0,seekTarget=0;
    std::atomic<uint64_t> seekDoneSerial{0},seekFirst{0},seekFailures{0},stagedDiscarded{0},attempted{0};
    std::atomic<bool> seekFirstProven{false};
    bool seekCapable() const { return metadataReady.load(std::memory_order_acquire) && fullTrack && metadataFacts[Seekable]==1 && metadataFacts[FileSize]>0; }
    void requestQuiescence() {
        {
            std::lock_guard<std::mutex> lock(queue.waitMutex);
            queue.quiesceRequested=true;
            // Preserve an acknowledgement already parked on authorization, and a command already in flight.
            auto phase=seekPhase.load();
            while(phase!=2 && phase!=3 && phase!=-1 && !seekPhase.compare_exchange_weak(phase,1)) {}
        }
        queue.changed.notify_all();
    }
    bool authorizeSeek(uint64_t serial,uint64_t target) {
        {
            // Publish before notifying, synchronized with the waiter's predicate check.
            std::lock_guard<std::mutex> lock(queue.waitMutex);
            if(seekPhase.load()!=2 || queue.stop.load()) return false;
            seekSerial=serial;seekTarget=target;queue.quiesceRequested=false;seekPhase.store(3,std::memory_order_release);
        }
        queue.changed.notify_all();return true;
    }
    void seekFailure() {++seekFailures;seekPhase=-1;decoder.fail(Outcome::DecoderError);queue.fail(PrefixSeek);}
    void processInteractive(FLAC__StreamDecoder* native) {
        bool initialized=true,ended=false;
        uint64_t staged=0;
        while(!queue.stop.load() && !queue.faults.load() && decoder.failure==Outcome::Pending) {
            if(queue.quiesceRequested.load()) {
                if(decoder.targetBlockFrames>staged) {
                    const auto obsolete=decoder.targetBlockFrames-staged;
                    if(!canAdd(stagedDiscarded.load(),obsolete) || !canAdd(decoder.unpublishedCanonical,stagedDiscarded.load()+obsolete)) {seekFailure();break;}
                    stagedDiscarded.fetch_add(obsolete);
                }
                decoder.targetBlockFrames=0;staged=0;
                seekPhase.store(2,std::memory_order_release);queue.wakeWorker();
                std::unique_lock<std::mutex> lock(queue.waitMutex);
                queue.changed.wait(lock,[this]{return queue.stop.load() || seekPhase.load(std::memory_order_acquire)==3;});
                lock.unlock();if(queue.stop.load()) break;
                if(!seekCapable() || seekTarget>=queue.expectedTotal.load() || !canAdd(decoder.seekAttempts,1)) {seekFailure();break;}
                ++decoder.seekAttempts;attempted.store(decoder.seekAttempts);decoder.seekTarget=seekTarget;decoder.originalNext=seekTarget;
                decoder.facts[Md5Verification]=0;decoder.facts[CleanEos]=0;decoder.facts[ProcessOk]=0;
                decoder.physicalEof=false;ended=false;seekFirstProven=false;
                if(!initialized) {
                    // finish() verified old sequential EOF but uninitialized the same object. Re-read metadata only.
                    if(Verification::seek(native,0,&decoder)!=FLAC__STREAM_DECODER_SEEK_STATUS_OK) {seekFailure();break;}
                    decoder.reloadMetadata=true;
                    if(!FLAC__stream_decoder_set_md5_checking(native,false) || !FLAC__stream_decoder_set_metadata_ignore_all(native) ||
                        !FLAC__stream_decoder_set_metadata_respond(native,FLAC__METADATA_TYPE_STREAMINFO) ||
                        FLAC__stream_decoder_init_stream(native,Verification::read,Verification::seek,Verification::tell,Verification::length,
                            Verification::eof,Verification::write,Verification::metadata,Verification::error,&decoder)!=FLAC__STREAM_DECODER_INIT_STATUS_OK) {seekFailure();break;}
                    initialized=true;decoder.facts[Md5Enabled]=0;
                    if(!FLAC__stream_decoder_process_until_end_of_metadata(native) || decoder.reloadMetadata || decoder.failure!=Outcome::Pending) {seekFailure();break;}
                }
                decoder.stagingSeek=true;decoder.targetBlockFrames=0;
                const bool sought=FLAC__stream_decoder_seek_absolute(native,seekTarget);
                decoder.stagingSeek=false;
                if(!sought || decoder.failure!=Outcome::Pending || !decoder.targetBlockFrames || decoder.targetBlockFirst!=seekTarget) {
                    if(queue.stop.load() && decoder.failure==Outcome::Cancelled) break;
                    seekFailure();break; // Stop cannot erase a real callback/API/target-proof failure.
                }
                if(queue.stop.load()) break;
                seekFirst=decoder.targetBlockFirst;seekFirstProven=true;staged=0;
                seekDoneSerial.store(seekSerial,std::memory_order_release);seekPhase.store(4,std::memory_order_release);queue.wakeWorker();
            }
            if(decoder.targetBlockFrames) {
                while(staged<decoder.targetBlockFrames && !queue.stop.load() && !queue.quiesceRequested.load()) {
                    if(!queue.push(decoder.targetBlockFirst+staged,decoder.targetBlock.data()+staged*queue.frameBytes())) break;
                    ++staged;
                }
                if(queue.stop.load()) break;
                if(queue.quiesceRequested.load()) continue;
                if(staged!=decoder.targetBlockFrames) {seekFailure();break;}
                decoder.targetBlockFrames=0;staged=0;
            }
            if(!initialized) {
                std::unique_lock<std::mutex> lock(queue.waitMutex);
                queue.changed.wait(lock,[this]{return queue.stop.load() || queue.quiesceRequested.load();});continue;
            }
            if(!FLAC__stream_decoder_process_single(native) || decoder.failure!=Outcome::Pending) break;
            const auto state=FLAC__stream_decoder_get_state(native);
            if(state!=FLAC__STREAM_DECODER_END_OF_STREAM) continue;
            // seek_absolute may mark END_OF_STREAM at the final target suffix without reading physical EOF.
            // Sequential no-seek decoding keeps its original read-callback EOF requirement unchanged.
            if(decoder.seekAttempts && !decoder.physicalEof && decoder.failure==Outcome::Pending) {
                uint8_t extra=0;const auto count=decoder.readInput(&extra,1);
                if(count>0) {decoder.fail(Outcome::Truncated);queue.fail(PrefixDecode);break;}
            }
            decoder.facts[ProcessOk]=1;decoder.facts[DecoderState]=state;
            decoder.facts[CleanEos]=decoder.physicalEof?1:0;
            decoder.phase(Phase::EndOfStream);decoder.phase(Phase::Verifying);decoder.facts[FinishCalled]=1;
            decoder.facts[FinishOk]=FLAC__stream_decoder_finish(native)?1:0;initialized=false;
            if(!decoder.seekAttempts && decoder.failure==Outcome::Pending && decoder.facts[CleanEos] && decoder.facts[Md5Available] && decoder.facts[Md5Enabled] && !decoder.facts[DecoderErrors])
                decoder.facts[Md5Verification]=decoder.facts[FinishOk]?1:2;
            if(!decoder.facts[CleanEos] || decoder.originalNext!=queue.expectedTotal.load()) {decoder.fail(Outcome::Truncated);queue.fail(PrefixTotal);break;}
            if(!decoder.facts[FinishOk]) {decoder.fail(decoder.seekAttempts?Outcome::DecoderError:Outcome::Md5Mismatch);queue.fail(decoder.seekAttempts?PrefixDecode:PrefixMd5Mismatch);break;}
            ended=true;queue.eofReached.store(true,std::memory_order_release);queue.changed.notify_all();queue.wakeWorker();
        }
        if(!ended && decoder.failure==Outcome::Pending) {
            if(queue.stop.load() && !queue.faults.load()) decoder.cancelled();
            else decoder.fail(Outcome::DecoderError);
        }
        if(initialized) {
            decoder.facts[DecoderState]=FLAC__stream_decoder_get_state(native);
            decoder.facts[FinishCalled]=1;decoder.facts[FinishOk]=FLAC__stream_decoder_finish(native)?1:0;
        }
    }
    PrefixSource(OwnedFd duplicate,uint64_t a,uint64_t s,bool full=false)
        : attachment(a),stream(s),fullTrack(full),queue(full,nullptr),decoder(std::move(duplicate),[this]{return queue.stop.load();},full) {
        decoder.enforceExpectedTotal=fullTrack;
        if(fullTrack) {
            decoder.interactiveProcess=[this](FLAC__StreamDecoder* native){processInteractive(native);};
            decoder.quiesceRequested=[this]{return queue.quiesceRequested.load();};
        }
        duplicate.value=-1; // Decoder owns it now; constructor/allocate exceptions cannot leak or double-close.
        decoder.metadataAccepted=[this](const Verification& v) {
            if(!queue.configure(v.pcm.format)) { queue.fail(PrefixFormat);return; }
            if (fullTrack) {
                if (!v.facts[Total] || static_cast<uint64_t>(v.facts[Total])>kCounterMax/queue.frameBytes()) { queue.fail(PrefixTotal); return; }
                queue.expectedTotal.store(static_cast<uint64_t>(v.facts[Total]));
            }
            metadataFacts=v.facts; metadataAtUs.store(sourceObservationUs(),std::memory_order_release);
            metadataReady.store(true,std::memory_order_release); queue.changed.notify_all();
        };
        decoder.consumeCanonical=[this](uint64_t frame,const uint8_t* bytes){return queue.push(frame,bytes);};
        decoder.failureObserved=[this](Outcome value) {
            if (value==Outcome::Cancelled && queue.stop.load()) return; // Intentional owner stop; preserve the already-recorded causal faults.
            uint64_t reason=value==Outcome::NotNativeFlac ? PrefixNotNative :
                value==Outcome::Unsupported ? PrefixFormat : value==Outcome::SampleRange ? PrefixRange :
                value==Outcome::InputOpenFailed ? PrefixInput :
                value==Outcome::CleanupFailed ? PrefixCleanup :
                value==Outcome::FormatContradiction ? PrefixContradiction :
                decoder.facts[Recognized] && decoder.facts[InitStatus]!=0 ? PrefixInit :
                !decoder.accepted ? PrefixMetadata : PrefixDecode;
            const auto original=decoder.seekAttempts?decoder.originalNext:decoder.pcm.frames;
            if (fullTrack && decoder.facts[Total]>0 && original>=static_cast<uint64_t>(decoder.facts[Total]) && value==Outcome::FormatContradiction) reason |= PrefixTotal;
            if (decoder.facts[FrameOrderErrors]) { ++queue.orderErrors; reason |= PrefixOrder; }
            queue.fail(reason);
        };
    }
    ~PrefixSource() { finish(); }
    void start() {
        producer=std::thread([this] {
            try { decoder.decode(); }
            catch (...) { decoder.fail(Outcome::DecoderError); queue.fail(PrefixDecode); }
            // Session 10: real EOF before owner-duration stop is terminal even with unread prefetch.
            if (!fullTrack) {
                if (decoder.physicalEof) queue.fail(PrefixEof);
                if (!queue.stop.load()) queue.fail(PrefixDecode);
            }
            decoder.finalize();
            if (decoder.facts[DecoderErrors] || decoder.facts[RangeErrors] || decoder.facts[Contradictions]) queue.fail(PrefixDecode);
            if (!decoder.facts[FdClosed] || (decoder.facts[Created] && !decoder.facts[DecoderDeleted])) queue.fail(PrefixCleanup);
            stopped.store(true);
            if (fullTrack && !queue.stop.load() && !queue.faults.load()) {
                if ((decoder.sequentialDecodeComplete() || decoder.failure==Outcome::SeekedEof) && queue.sourceBase.load()+queue.tail.load()==static_cast<uint64_t>(decoder.facts[Total]))
                    queue.eofReached.store(true,std::memory_order_release);
                else queue.fail(decoder.failure==Outcome::Md5Mismatch ? PrefixMd5Mismatch :
                    decoder.failure==Outcome::Md5Unavailable ? PrefixMd5Unavailable :
                    decoder.pcm.frames!=static_cast<uint64_t>(decoder.facts[Total]) ? PrefixTotal : PrefixDecode);
            }
            queue.changed.notify_all();queue.wakeWorker();
        });
    }
    uint64_t primeTarget() const { return fullTrack && queue.expectedTotal.load() ? std::min<uint64_t>(kPrefixPrebuffer,queue.expectedTotal.load()) : kPrefixPrebuffer; }
    bool ready() const { return metadataReady.load() && !queue.faults.load() && !queue.stop.load() &&
        queue.depth()>=primeTarget() && (primeTarget()==kPrefixPrebuffer || queue.eofReached.load()); }
    bool prime() {
        const auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(5);
        std::unique_lock<std::mutex> lock(queue.waitMutex);
        while (!queue.faults.load() && !queue.stop.load() &&
            !ready()) {
            queue.changed.wait_for(lock,std::chrono::milliseconds(1));
            if (std::chrono::steady_clock::now()>=deadline) {
                lock.unlock(); // fail() requests Stop, which acquires waitMutex.
                queue.fail(PrefixPrime); break;
            }
        }
        if (ready()) {
            primed=queue.depth(); readyAtUs.store(sourceObservationUs(),std::memory_order_release);return true;
        }
        return false;
    }
    void beginStreaming() { queue.streaming.store(true); queue.trackDepth(queue.depth()); }
    void finish() {
        queue.requestStop();
        std::lock_guard<std::mutex> lock(finishMutex); // Serializes owner teardown and its idempotent fallback.
        if (producer.joinable()) producer.join();
        joined.store(true);
        atStop=queue.depth(); discarded=atStop;
    }
    std::array<int64_t,PrefixFieldCount> evidence(bool released=false) const {
        const uint64_t enqueued=queue.enqueuedTotal.load(),consumed=queue.consumedTotal.load();
        std::array<int64_t,PrefixFieldCount> facts{};
        facts[PApi]=4;facts[PAttachment]=attachment;facts[PStream]=stream;facts[PFault]=queue.faults.load();
        facts[PPrimed]=primed.load();facts[PMetadata]=metadataReady.load();
        if (facts[PMetadata]) { facts[PRate]=metadataFacts[Rate];facts[PChannels]=metadataFacts[Channels];facts[PBits]=metadataFacts[Bits];facts[PTotal]=metadataFacts[Total]; }
        facts[PDecoded]=queue.decoded.load();facts[PEnqueued]=enqueued;facts[PConsumed]=consumed;facts[PPrepared]=queue.prepared;
        facts[PAtStop]=atStop;facts[PDiscarded]=discarded;facts[PMinDepth]=queue.minDepth.load();facts[PMaxDepth]=queue.maxDepth.load();
        facts[PFullWaits]=queue.fullWaits.load();facts[PEmpty]=queue.emptyEvents.load();facts[PUnderruns]=queue.underruns.load();
        facts[PFirstEnqueued]=enqueued?0:-1;facts[PLastEnqueued]=enqueued?enqueued-1:-1;facts[PFirstConsumed]=consumed?0:-1;
        facts[PLastConsumed]=consumed?consumed-1:-1;facts[PNextEnqueue]=enqueued;facts[PNextConsume]=consumed;
        facts[PGaps]=queue.gaps.load();facts[PDuplicates]=queue.duplicates.load();facts[POrder]=queue.orderErrors.load();
        facts[PConsumedHash]=hashBits(queue.consumedHash);
        facts[PEnqueuedHash]=joined.load()?hashBits(queue.enqueuedHash):0;
        facts[PStopped]=stopped.load();facts[PJoined]=joined.load();facts[PQueueReleased]=released; facts[PFull]=fullTrack; facts[PEof]=queue.eofReached.load();
        facts[PNaturalProducer]=queue.eofReached.load() && stopped.load();
        // EOF can be followed by a new seek. Only join freezes raw decoder facts for observers.
        if (joined.load()) {
            facts[PMd5Pass]=decoder.facts[Md5Verification]==1;
            facts[PTotalMatch]=decoder.pcm.frames==static_cast<uint64_t>(decoder.facts[Total]);
            facts[PDecoderReleased]=!decoder.facts[Created] || decoder.facts[DecoderDeleted];
            facts[PFdClosed]=decoder.facts[FdClosed]; facts[PDecoded]=decoder.pcm.frames;
        }
        if (joined.load()) { facts[PDecoderReleased]=!decoder.facts[Created] || decoder.facts[DecoderDeleted];facts[PFdClosed]=decoder.facts[FdClosed];facts[PDecoded]=decoder.pcm.frames; }
        facts[PSeekCapable]=seekCapable();facts[PSourceEpoch]=queue.sourceEpoch.load();facts[PSourceBase]=queue.sourceBase.load();
        facts[PInvalidatedQueued]=queue.invalidatedQueued.load();facts[PSeekFailures]=seekFailures.load();facts[PSeekAttempts]=attempted.load();
        facts[PSeekFirstFrame]=seekFirst.load();facts[PSeekFirstProven]=seekFirstProven.load();
        if(joined.load()) {
            facts[PSeekAttempts]=decoder.seekAttempts;facts[POriginalNext]=decoder.originalNext;
            facts[PInvalidatedCanonical]=decoder.unpublishedCanonical+stagedDiscarded.load();facts[PInvalidatedRaw]=decoder.unpublishedRaw;
        }
        return facts;
    }
};
} // namespace sazanami::flacverify
