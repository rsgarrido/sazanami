#pragma once
#include "flac_decode_core.h"
#include <atomic>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <chrono>

namespace sazanami::flacverify {
constexpr size_t kPrefixCapacity = 16384, kPrefixPrebuffer = 8192;
constexpr uint64_t kPrefixDurationUs = 5000000;
enum PrefixFault : uint64_t { PrefixDecode = 1, PrefixInit = 2, PrefixFormat = 4, PrefixRange = 8,
    PrefixEof = 16, PrefixPrime = 32, PrefixUnderrun = 64, PrefixGap = 128, PrefixDuplicate = 256,
    PrefixOrder = 512, PrefixHash = 1024, PrefixCancelled = 2048, PrefixCleanup = 4096, PrefixInput = 8192, PrefixNotNative = 16384, PrefixMetadata = 32768, PrefixContradiction = 65536, PrefixMd5Unavailable = 131072, PrefixMd5Mismatch = 262144, PrefixTotal = 524288, PrefixTail = 1048576 };
enum PrefixField : size_t { PApi, PAttachment, PStream, PFault, PPrimed, PMetadata, PRate, PChannels, PBits, PTotal,
    PDecoded, PEnqueued, PConsumed, PPrepared, PAtStop, PDiscarded, PMinDepth, PMaxDepth, PFullWaits, PEmpty,
    PUnderruns, PFirstEnqueued, PLastEnqueued, PFirstConsumed, PLastConsumed, PNextEnqueue, PNextConsume,
    PGaps, PDuplicates, POrder, PConsumedHash, PEnqueuedHash, PStopped, PJoined, PDecoderReleased,
    PFdClosed, PQueueReleased, PFull, PEof, PNaturalProducer, PMd5Pass, PTotalMatch, PrefixFieldCount };
enum class ReserveResult { Available, InsufficientFrames, StopRequested, SourceFault, SourceEof, Cancelled, InvalidState, EndOfSource };
// Fixed canonical-byte SPSC ring. tail publishes producer writes; head publishes committed reads.
struct PrefixQueue {
    const bool fullTrack;
    std::atomic<uint64_t> expectedTotal{0};
    std::atomic<bool> eofReached{false};
    explicit PrefixQueue(bool full=false) : fullTrack(full) {}
    std::array<std::array<uint8_t,4>, kPrefixCapacity> bytes{};
    std::array<uint64_t, kPrefixCapacity> indices{};
    std::atomic<uint64_t> head{0}, tail{0}, faults{0}, decoded{0}, fullWaits{0}, emptyEvents{0}, underruns{0};
    std::atomic<uint64_t> gaps{0}, duplicates{0}, orderErrors{0}, minDepth{kPrefixCapacity}, maxDepth{0};
    std::atomic<bool> stop{false}, streaming{false};
    std::mutex waitMutex;
    std::condition_variable changed;
    uint64_t consumedHash = kOffset, enqueuedHash = kOffset, prepared = 0, reservedStart = 0;
    std::array<std::array<int64_t,7>,5> examples{};
    size_t exampleCount = 0;
    uint64_t depth() const { return tail.load(std::memory_order_acquire) - head.load(std::memory_order_acquire); }
    void fail(uint64_t reason) { faults.fetch_or(reason); requestStop(); }
    void requestStop() { stop.store(true); changed.notify_all(); }
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
        if (fullTrack && (!expectedTotal.load() || index>=expectedTotal.load())) { fail(PrefixTotal); return false; }
        decoded.store(index + 1);
        const uint64_t position = tail.load(std::memory_order_relaxed);
        if (!identity(index, position) || position >= kCounterMax / 4) { fail(PrefixOrder); return false; }
        if (depth() == kPrefixCapacity && !stop.load()) {
            ++fullWaits;
            std::unique_lock<std::mutex> lock(waitMutex);
            // Atomic predicate + finite wait prevents lost-notification deadlock without locking the consumer.
            while (!stop.load() && depth() == kPrefixCapacity) changed.wait_for(lock, std::chrono::milliseconds(1));
        }
        if (stop.load() || faults.load()) return false;
        const size_t slot = position % kPrefixCapacity;
        std::memcpy(bytes[slot].data(), canonical, 4); indices[slot] = index;
        enqueuedHash = hashBytes(enqueuedHash, canonical, 4);
        tail.store(position + 1, std::memory_order_release);
        trackDepth(depth()); changed.notify_all();
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
        if (!count || count>kPrefixCapacity || (fullTrack && (head.load()>expectedTotal.load() || count>expectedTotal.load()-head.load())))
            return ReserveResult::InvalidState;
        if (depth()>=count) return ReserveResult::Available;
        const auto current=availability();
        if (current!=ReserveResult::Available) return current;
        if (fullTrack && eofReached.load(std::memory_order_acquire)) return ReserveResult::EndOfSource;
        ++emptyEvents; ++underruns; fail(PrefixUnderrun); return ReserveResult::InsufficientFrames;
    }
    ReserveResult reserve(uint8_t* payload, uint64_t count) {
        const auto state = availability();
        if (state != ReserveResult::Available) return state;
        if (!payload || prepared || !count || count > kPrefixCapacity) return ReserveResult::InvalidState;
        const uint64_t position = head.load(std::memory_order_relaxed);
        const auto available=requireFrames(count);
        if (available!=ReserveResult::Available) return available;
        reservedStart = position; prepared = count;
        for (uint64_t i = 0; i < count; ++i) {
            const size_t slot = (position + i) % kPrefixCapacity;
            if (!identity(indices[slot], position + i)) return ReserveResult::SourceFault;
            std::memcpy(payload + i*4, bytes[slot].data(), 4);
        }
        return ReserveResult::Available;
    }
    bool validate(const uint8_t* payload, size_t length) {
        if (length != prepared * 4 || reservedStart != head.load() || faults.load()) return false;
        for (uint64_t i = 0; i < prepared; ++i)
            if (std::memcmp(payload + i*4, bytes[(reservedStart+i)%kPrefixCapacity].data(), 4)) { fail(PrefixHash); return false; }
        return true;
    }
    // Called ONLY after successful USBDEVFS_SUBMITURB; failed submission leaves head/hash untouched.
    bool commit() {
        if (!prepared || reservedStart != head.load()) { fail(PrefixOrder); return false; }
        for (uint64_t i = 0; i < prepared; ++i) {
            const auto& frame = bytes[(reservedStart+i)%kPrefixCapacity];
            consumedHash = hashBytes(consumedHash, frame.data(), 4);
            const uint64_t index = reservedStart+i;
            if (index < 4 || i == prepared-1) {
                const size_t slot = index < 4 ? static_cast<size_t>(index) : 4;
                const auto decode = [](const uint8_t* b) { int value=b[0]+(b[1]<<8); return value>=32768 ? value-65536 : value; };
                examples[slot] = {static_cast<int64_t>(index),decode(frame.data()),decode(frame.data()+2),frame[0],frame[1],frame[2],frame[3]};
                exampleCount = std::max(exampleCount,slot+1);
            }
        }
        head.store(reservedStart+prepared,std::memory_order_release); prepared=0;
        trackDepth(depth()); changed.notify_all(); return true;
    }
};
struct PrefixSource {
    const uint64_t attachment, stream;
    const bool fullTrack;
    PrefixQueue queue;
    Verification decoder;
    std::array<int64_t, FieldCount> metadataFacts{};
    std::atomic<bool> metadataReady{false}, stopped{false}, joined{false};
    std::thread producer;
    std::atomic<uint64_t> primed{0};
    std::mutex finishMutex;
    uint64_t atStop=0, discarded=0;
    PrefixSource(OwnedFd duplicate,uint64_t a,uint64_t s,bool full=false)
        : attachment(a),stream(s),fullTrack(full),queue(full),decoder(std::move(duplicate),[this]{return queue.stop.load();},full) {
        decoder.enforceExpectedTotal=fullTrack;
        duplicate.value=-1; // Decoder owns it now; constructor/allocate exceptions cannot leak or double-close.
        decoder.metadataAccepted=[this](const Verification& v) {
            if (fullTrack) {
                if (!v.facts[Total] || static_cast<uint64_t>(v.facts[Total])>kCounterMax/4) { queue.fail(PrefixTotal); return; }
                if (!v.facts[Md5Available]) { queue.fail(PrefixMd5Unavailable); return; }
                queue.expectedTotal.store(static_cast<uint64_t>(v.facts[Total]));
            }
            metadataFacts=v.facts; metadataReady.store(true,std::memory_order_release); queue.changed.notify_all();
        };
        decoder.consumeCanonical=[this](uint64_t frame,const uint8_t* bytes){return queue.push(frame,bytes);};
        decoder.failureObserved=[this](Outcome value) {
            if (value==Outcome::Cancelled && queue.stop.load()) return; // Intentional owner stop; preserve the already-recorded causal faults.
            uint64_t reason=value==Outcome::NotNativeFlac ? PrefixNotNative :
                value==Outcome::Unsupported ? PrefixFormat : value==Outcome::SampleRange ? PrefixRange :
                value==Outcome::InputOpenFailed ? PrefixInput :
                value==Outcome::FormatContradiction ? PrefixContradiction :
                decoder.facts[Recognized] && decoder.facts[InitStatus]!=0 ? PrefixInit :
                !decoder.accepted ? PrefixMetadata : PrefixDecode;
            if (fullTrack && decoder.facts[Total]>0 && decoder.pcm.frames>=static_cast<uint64_t>(decoder.facts[Total]) && value==Outcome::FormatContradiction) reason |= PrefixTotal;
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
                if (decoder.failure==Outcome::Exact && queue.tail.load()==static_cast<uint64_t>(decoder.facts[Total]))
                    queue.eofReached.store(true,std::memory_order_release);
                else queue.fail(decoder.failure==Outcome::Md5Mismatch ? PrefixMd5Mismatch :
                    decoder.failure==Outcome::Md5Unavailable ? PrefixMd5Unavailable :
                    decoder.pcm.frames!=static_cast<uint64_t>(decoder.facts[Total]) ? PrefixTotal : PrefixDecode);
            }
            queue.changed.notify_all();
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
            if (std::chrono::steady_clock::now()>=deadline) { queue.fail(PrefixPrime); break; }
        }
        if (ready()) {
            primed=queue.depth(); return true;
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
        const uint64_t enqueued=queue.tail.load(),consumed=queue.head.load();
        std::array<int64_t,PrefixFieldCount> facts{};
        facts[PApi]=2;facts[PAttachment]=attachment;facts[PStream]=stream;facts[PFault]=queue.faults.load();
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
        if (joined.load() || queue.eofReached.load(std::memory_order_acquire)) {
            facts[PMd5Pass]=decoder.facts[Md5Verification]==1;
            facts[PTotalMatch]=decoder.pcm.frames==static_cast<uint64_t>(decoder.facts[Total]);
            facts[PDecoderReleased]=!decoder.facts[Created] || decoder.facts[DecoderDeleted];
            facts[PFdClosed]=decoder.facts[FdClosed]; facts[PDecoded]=decoder.pcm.frames;
        }
        if (joined.load()) { facts[PDecoderReleased]=!decoder.facts[Created] || decoder.facts[DecoderDeleted];facts[PFdClosed]=decoder.facts[FdClosed];facts[PDecoded]=decoder.pcm.frames; }
        return facts;
    }
};
} // namespace sazanami::flacverify
