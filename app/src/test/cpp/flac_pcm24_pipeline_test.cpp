// Authored for manual standalone native execution only; not part of testDebugUnitTest.
#include "../../main/cpp/flac/flac_prefix_queue.h"
#include "../../main/cpp/usb_iso_tail.h"
#include "../../main/cpp/usb_feedback_policy.h"
#include <cassert>
#include <numeric>
#include <cstdio>
using namespace sazanami::flacverify;
namespace dp=sazanami::directpcm;
namespace ui=sazanami::usbiso;
static void completeTupleMatrix() {
    const std::array<std::array<uint32_t,3>,4> accepted{{{44100,2,16},{48000,2,16},{48000,2,24},{96000,2,24}}};
    // Same independent domains/expected tuple set as UsbPcm24Rate96Test, not a Cartesian rate/width policy.
    for(uint32_t rate : {0U,44100U,48000U,88200U,96000U,176400U,192000U,384000U})
        for(uint32_t channels : {0U,1U,2U,6U,8U}) for(uint32_t bits : {0U,8U,16U,24U,32U}) {
            const std::array<uint32_t,3> tuple{rate,channels,bits};
            const bool expected=std::find(accepted.begin(),accepted.end(),tuple)!=accepted.end();
            assert(supported(rate,channels,bits)==expected);
            assert(bool(dp::Format::create(rate,channels,bits))==expected);
        }
}
static void packingAndPolicy(uint32_t rate) {
    const auto format=dp::Format::create(rate,2,24);assert(format && format->bytesPerFrame==6 && format->subslotBytes==3);
    assert(!dp::Format::create(44100,2,24) && !dp::Format::create(88200,2,24) && !dp::Format::create(192000,2,24));
    assert(!dp::Format::create(48000,1,24) && !dp::Format::create(48000,8,24) && !dp::Format::create(48000,2,32));
    const int32_t samples[]{0,1,-1,8388607,-8388608,0x123456,-0x123456,65537,-65537};
    const uint8_t expected[][3]{{0,0,0},{1,0,0},{255,255,255},{255,255,127},{0,0,128},
        {0x56,0x34,0x12},{0xaa,0xcb,0xed},{1,0,1},{255,255,254}};
    for(size_t i=0;i<9;++i) {
        uint8_t packed[4]{0,0,0,0xa5};dp::packSample(packed,samples[i],3);
        assert(std::memcmp(packed,expected[i],3)==0 && packed[3]==0xa5);
        assert(dp::unpackSample(packed,3)==samples[i]);
    }
    Pcm pcm(format);uint8_t frame[7]{0,0,0,0,0,0,0xa5};
    assert(pcm.append(-8388608,8388607,frame));
    const uint8_t stereo[]{0,0,128,255,255,127};
    assert(std::memcmp(frame,stereo,6)==0 && frame[6]==0xa5 && pcm.bytes==6 && pcm.frames==1);
    assert(pcm.hash==hashBytes(kOffset,stereo,6));
    auto corrupted=std::array<uint8_t,6>{0,0,128,255,255,127};corrupted[2]^=1;
    assert(hashBytes(kOffset,corrupted.data(),6)!=pcm.hash);
    Pcm bad(format);assert(!bad.append(-8388609,0) && bad.failure==Outcome::SampleRange && bad.frames==0);
    Pcm high(format);assert(!high.append(0,8388608));
    Pcm overflow(format);overflow.bytes=kCounterMax-5;assert(!overflow.append(0,0) && overflow.failure==Outcome::CounterOverflow);
    PrefixQueue bounded(false,format);assert(bounded.bytes.size()==16384*6 && kPrefixPrebuffer==8192);
    PrefixQueue legacy;assert(legacy.bytes.size()==16384*4 && legacy.frameBytes()==4);
    assert(!bounded.configure(dp::referenceFormat())); // Selected width cannot change after allocation.
}
static void exactTransactionsAndTails(uint32_t rate) {
    const auto format=dp::Format::create(rate,2,24);
    const uint32_t nominalRaw=static_cast<uint32_t>(static_cast<uint64_t>(rate)*65536/8000);
    const uint32_t envelopeBytes=static_cast<uint32_t>((static_cast<uint64_t>(rate)*105+800000-1)/800000)*6;
    for(uint64_t total : {uint64_t(rate)+1,uint64_t(rate)+5,uint64_t(rate)+6,uint64_t(rate)+7,uint64_t(rate)+11,
        uint64_t(rate)+12,uint64_t(rate)+13,uint64_t(rate)+47,uint64_t(rate)+95,uint64_t(rate)+96}) {
        PrefixQueue queue(true,format);queue.expectedTotal=total;Pcm pcm(format);
        ui::Scheduler scheduler(rate);uint64_t submitted=0,completed=0,usbHash=kOffset;
        while(submitted<total) {
            std::array<uint32_t,ui::kOutPackets> normal{};
            for(auto& count:normal)assert(scheduler.next(1,nominalRaw,count));
            const auto normalFrames=std::accumulate(normal.begin(),normal.end(),uint64_t{0});
            const auto shape=ui::terminalShape(normal,std::min(total-submitted,normalFrames),true);
            assert(shape.valid && shape.packets>=1 && shape.packets<=8);
            std::vector<uint8_t> source(shape.total*6),out(shape.total*6);
            for(uint64_t i=0;i<shape.total;++i) {
                assert(pcm.append((i&1) ? 0x123456 : -8388608,(i&1) ? -0x123456 : 8388607,source.data()+i*6));
                assert(queue.push(submitted+i,source.data()+i*6));
            }
            if(queue.tail==total)queue.eofReached=true;
            const auto head=queue.head.load(),priorHash=queue.consumedHash;
            assert(queue.reserve(out.data(),shape.total)==ReserveResult::Available);
            assert(queue.validate(out.data(),out.size()) && out==source);
            assert(queue.head==head && queue.consumedHash==priorHash); // Failed submit: no commit, no consumption.
            usbHash=hashBytes(usbHash,out.data(),out.size());assert(queue.commit()); // Models successful SUBMITURB only.
            submitted+=shape.total;completed+=shape.total;
            assert(queue.head==submitted && queue.consumedHash==usbHash && usbHash==pcm.hash && queue.enqueuedHash==pcm.hash);
            for(size_t i=0;i<shape.packets;++i)assert(shape.frames[i]*6%6==0 && ui::packetFits(shape.frames[i],envelopeBytes,6));
            for(size_t i=shape.packets;i<8;++i)assert(shape.frames[i]==0); // No unused descriptors or EOF PCM padding.
        }
        assert(pcm.frames==total && pcm.bytes==total*6 && completed==total && queue.depth()==0);
        assert(ui::naturalOutComplete(true,total,total,total,submitted,completed,0,0,true));
        assert(!ui::naturalOutComplete(true,total,total,total,submitted,completed-1,1,0,true));
        assert(classify(true,true,true,true,true,true,true,true,true,total,pcm,0,0,true)==Outcome::Exact);
        assert(classify(true,true,true,true,true,true,true,true,false,total,pcm,0,0,true)==Outcome::Md5Mismatch);
    }
    PrefixQueue mutated(false,format);uint8_t frame[]{0,0,128,255,255,127};assert(mutated.push(0,frame));
    uint8_t out[6];assert(mutated.reserve(out,1)==ReserveResult::Available);out[5]^=1;
    assert(!mutated.validate(out,6) && mutated.head==0 && mutated.faults.load()&PrefixHash);
}
static void callbackAndSchedulerWidthAgreement(uint32_t rate) {
    Verification v(OwnedFd(-1),[]{return false;});
    FLAC__StreamMetadata metadata{};metadata.type=FLAC__METADATA_TYPE_STREAMINFO;
    auto& info=metadata.data.stream_info;info.sample_rate=rate;info.channels=2;info.bits_per_sample=24;
    info.total_samples=2;info.min_blocksize=2;info.max_blocksize=2;
    Verification::metadata(nullptr,&metadata,&v);assert(v.accepted && v.pcm.format->bytesPerFrame==6);
    FLAC__Frame frame{};frame.header.sample_rate=rate;frame.header.channels=2;frame.header.bits_per_sample=24;
    frame.header.blocksize=2;frame.header.number_type=FLAC__FRAME_NUMBER_TYPE_SAMPLE_NUMBER;
    FLAC__int32 left[]{-8388608,65537},right[]{8388607,-65537};const FLAC__int32* channels[]{left,right};
    assert(Verification::write(nullptr,&frame,channels,&v)==FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE);
    assert(v.pcm.frames==2 && v.pcm.bytes==12 && v.pcm.examples[0][3]==0 && v.pcm.examples[0][5]==128);
    frame.header.bits_per_sample=16;frame.header.number.sample_number=2;
    assert(Verification::write(nullptr,&frame,channels,&v)==FLAC__STREAM_DECODER_WRITE_STATUS_ABORT && v.failure==Outcome::FormatContradiction);
    // Accepted metadata configures the same immutable format before source publication.
    info.md5sum[0]=1;
    PrefixSource full(OwnedFd(-1),17,23,true);
    Verification::metadata(nullptr,&metadata,&full.decoder);
    assert(full.metadataReady.load() && full.queue.format==full.decoder.pcm.format && full.queue.expectedTotal==2);
    assert(full.metadataFacts[Rate]==rate && full.queue.bytes.size()==98304);
    Verification wrongHeader(OwnedFd(-1),[]{return false;});
    Verification::metadata(nullptr,&metadata,&wrongHeader);
    frame.header.bits_per_sample=24;frame.header.sample_rate=rate==96000 ? 48000 : 96000;frame.header.number.sample_number=0;
    assert(Verification::write(nullptr,&frame,channels,&wrongHeader)==FLAC__STREAM_DECODER_WRITE_STATUS_ABORT);
    assert(wrongHeader.failure==Outcome::FormatContradiction && wrongHeader.pcm.frames==0);
    Verification bad(OwnedFd(-1),[]{return false;});info.sample_rate=96000;info.bits_per_sample=16;
    Verification::metadata(nullptr,&metadata,&bad);
    assert(!bad.accepted && !bad.pcm.format && bad.failure==Outcome::Unsupported);
}
static void schedulerWidthAgreement48() {
    ui::Scheduler pcm16(48000),pcm24(48000);bool seven=false;
    for(unsigned i=0;i<8000;++i) {
        uint32_t a,b;const uint32_t feedback=i<4000 ? 393216 : 393216*104/100;
        assert(pcm16.next(1,feedback,a) && pcm24.next(1,feedback,b) && a==b);
        assert(ui::packetFits(a,28,4) && ui::packetFits(b,42,6));if(b==7)seven=true;
    }
    assert(seven && pcm16.frames==pcm24.frames && pcm16.remainder==pcm24.remainder);
    assert(!ui::packetFits(7,36,6));
    assert(ui::outUrbCount(125,3)==21 && ui::feedbackUrbCount(1000)==21 && ui::feedbackHoldoverUs(1000)==20000);
}
static void feedbackSchedulerAndTails96() {
    const auto feedback=[](uint32_t raw) {
        uint8_t bytes[4];for(unsigned i=0;i<4;++i)bytes[i]=static_cast<uint8_t>(raw>>(8*i));
        return ui::decodeFeedback(bytes,4,0,96000);
    };
    constexpr uint32_t raw=786432;
    assert(feedback(raw).validity==ui::FeedbackValidity::Valid);
    for(unsigned percent : {96U,100U,104U}) assert(feedback(raw*percent/100).validity==ui::FeedbackValidity::Valid);
    for(unsigned percent : {94U,106U}) assert(feedback(raw*percent/100).validity==ui::FeedbackValidity::Implausible);
    assert(feedback(393216).validity==ui::FeedbackValidity::Implausible);
    assert(feedback(0).validity==ui::FeedbackValidity::Zero);
    for(uint32_t accepted : {0U,raw}) {
        ui::Scheduler nominal(96000);
        for(unsigned i=0;i<8000;++i) { uint32_t frames;assert(nominal.next(1,accepted,frames) && frames==12 && frames*6==72); }
        assert(nominal.frames==96000 && nominal.remainder==0);
    }
    ui::Scheduler drift(96000);bool thirteen=false;
    for(unsigned i=0;i<8000;++i) {
        uint32_t count;assert(drift.next(1,raw*104/100,count) && ui::packetFits(count,78,6));
        thirteen |= count==13;
    }
    assert(thirteen && drift.frames>96000);
    const auto rollback=drift;uint32_t candidate,retried;
    assert(drift.next(1,raw*96/100,candidate));const auto candidatePhase=drift.remainder;
    drift=rollback;assert(drift.nominalRate==96000 && drift.frames==rollback.frames && drift.remainder==rollback.remainder);
    assert(drift.next(1,raw*96/100,retried) && retried==candidate && drift.remainder==candidatePhase);
    assert(ui::packetFits(12,72,6) && !ui::packetFits(13,72,6) && !ui::packetFits(13,77,6) && ui::packetFits(13,78,6));
    assert(!ui::packetFits(UINT32_MAX,3072,6));
    std::array<uint32_t,ui::kOutPackets> normal{};normal.fill(12);
    for(uint64_t remaining : {1ULL,11ULL,12ULL,13ULL,17ULL,95ULL,96ULL}) {
        const auto tail=ui::terminalShape(normal,remaining,true);
        assert(tail.valid && tail.total==remaining && tail.packets==(remaining+11)/12);
        assert(tail.frames[tail.packets-1]==(remaining-1)%12+1);
        for(size_t i=tail.packets;i<normal.size();++i)assert(tail.frames[i]==0);
        assert(!ui::terminalShape(normal,remaining,false).valid);
    }
    assert(!ui::naturalOutComplete(true,96,96,96,96,96,0,0,false)); // Failure cannot become EOF.
    const auto format=dp::Format::create(96000,2,24);
    PrefixQueue cancelled(false,format);cancelled.requestStop();uint8_t payload[6]{};
    assert(cancelled.reserve(payload,1)==ReserveResult::StopRequested && cancelled.head==0);
    PrefixQueue overflow(false,format);overflow.head=kCounterMax/6;overflow.tail=kCounterMax/6;
    assert(!overflow.push(kCounterMax/6,payload) && overflow.faults.load());
}
static void nonFlacInputNeverStartsDecoding() {
    // Synthetic four-byte signatures only; no media conversion or music-file fixtures.
    for(const char* signature : {"RIFF","FORM","OggS","DSD ","alac"}) {
        FILE* file=std::tmpfile();assert(file);
        assert(std::fwrite(signature,1,4,file)==4);std::rewind(file);
        const int duplicate=::dup(::fileno(file));assert(duplicate>=0);
        Verification v(OwnedFd(duplicate),[]{return false;});v.decode();v.finalize();
        assert(v.failure==Outcome::NotNativeFlac && !v.facts[Recognized] && !v.facts[Created] && v.facts[FdClosed]);
        assert(std::fclose(file)==0);
    }
}
int main() {
    completeTupleMatrix();
    for(uint32_t rate : {48000U,96000U}) {
        packingAndPolicy(rate);exactTransactionsAndTails(rate);callbackAndSchedulerWidthAgreement(rate);
    }
    schedulerWidthAgreement48();feedbackSchedulerAndTails96();nonFlacInputNeverStartsDecoding();
}
