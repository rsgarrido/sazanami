// Pure/FD tests for human execution only; no audio fixture or executed tests.
#include "../../main/cpp/flac/flac_pcm_core.h"
#include "../../main/cpp/flac/flac_owned_fd.h"
#include <cassert>
#include <fcntl.h>
#include <cerrno>
#include <utility>
using namespace sazanami::flacverify;
static void packingAndRange() {
    const int32_t values[]{0,1,-1,1024,-1024,32767,-32768};
    const uint8_t expected[][2]{{0,0},{1,0},{255,255},{0,4},{0,252},{255,127},{0,128}};
    for (size_t i = 0; i < 7; ++i) { uint8_t bytes[2]; pack16(bytes, values[i]); assert(bytes[0] == expected[i][0] && bytes[1] == expected[i][1]); assert(inRange(values[i])); }
    assert(!inRange(-32769) && !inRange(32768));
    Pcm low, high; assert(!low.append(-32769,0) && low.failure == Outcome::SampleRange);
    assert(!high.append(0,32768) && high.failure == Outcome::SampleRange);
    assert(low.hash == kOffset && low.frames == 0 && low.rangeErrors == 1);
    assert(!low.append(1,1) && low.hash == kOffset);
}
static void metadataGate() {
    assert(supported(44100,2,16)); assert(supported(48000,2,16)); assert(supported(48000,2,24)); assert(!supported(44100,2,24));
    for (uint32_t rate : {88200,96000,176400,192000}) assert(!supported(rate,2,16));
    for (uint32_t channels : {1,3,8}) assert(!supported(44100,channels,16));
    for (uint32_t bits : {8,24,32}) assert(!supported(44100,2,bits));
}
static void orderedHashAndChunks() {
    const int32_t left[]{0,1,1024,32767,-32768,-1};
    const int32_t right[]{0,-1,-1024,-32768,32767,1};
    Pcm whole, chunked;
    for (size_t i = 0; i < 6; ++i) assert(whole.append(left[i],right[i]));
    for (size_t start : {0,2,4}) for (size_t i = start; i < start+2; ++i) assert(chunked.append(left[i],right[i]));
    assert(whole.hash == 0x255d40cc915e3c4dULL && whole.hash == chunked.hash);
    assert(whole.bytes == 24 && whole.frames == 6 && whole.minL == -32768 && whole.maxL == 32767);
    assert(whole.minR == -32768 && whole.maxR == 32767);
    assert(whole.examples[2][3] == 0 && whole.examples[2][4] == 4 && whole.examples[2][5] == 0 && whole.examples[2][6] == 252);
    const auto before = chunked.hash;
    // An empty callback/chunk does not call append, so cannot mutate the hash.
    assert(chunked.hash == before);
    uint8_t usedAndUnused[]{0,4,0,252,165,165};
    assert(hashBytes(kOffset,usedAndUnused,4) != hashBytes(kOffset,usedAndUnused,6));
}
static void boundedExamplesAndCounters() {
    Pcm pcm;
    for (size_t i = 0; i < 100000; ++i) {
        assert(pcm.append(static_cast<int32_t>(i%31), -static_cast<int32_t>(i%37)));
        assert(pcm.frames == i+1 && pcm.bytes == 4*(i+1));
    }
    assert(pcm.examples.size() == 7 && pcm.examples[6][0] == 99999);
    assert(pcm.minimum == -36 && pcm.maximum == 30);
}
static void overflow() {
    assert(canAdd(kCounterMax-4,4) && !canAdd(kCounterMax-4,5));
    Pcm frames; frames.frames = kCounterMax; assert(!frames.append(0,0));
    assert(frames.failure == Outcome::CounterOverflow && frames.hash == kOffset);
    Pcm bytes; bytes.bytes = kCounterMax-3; assert(!bytes.append(0,0));
    assert(bytes.failure == Outcome::CounterOverflow && bytes.frames == 0);
}
static Outcome result(bool recognized=true, bool initialized=true, bool metadata=true, bool accepted=true,
    bool process=true, bool eos=true, bool md5Enabled=true, bool md5=true, bool finish=true,
    uint64_t total=2, uint64_t errors=0, uint64_t contradictions=0, bool released=true) {
    Pcm pcm; assert(pcm.append(-32768,32767)); assert(pcm.append(1,-1));
    return classify(recognized,initialized,metadata,accepted,process,eos,md5Enabled,md5,finish,total,pcm,errors,contradictions,released);
}
static void classification() {
    assert(result() == Outcome::Exact);
    assert(result(false) == Outcome::NotNativeFlac);
    assert(result(true,false) == Outcome::DecoderError);
    assert(result(true,true,false) == Outcome::DecoderError);
    assert(result(true,true,true,false) == Outcome::Unsupported);
    assert(result(true,true,true,true,false) == Outcome::Truncated);
    assert(result(true,true,true,true,true,false) == Outcome::Truncated);
    assert(result(true,true,true,true,true,true,false) == Outcome::DecoderError);
    assert(result(true,true,true,true,true,true,true,false) == Outcome::Md5Unavailable);
    assert(result(true,true,true,true,true,true,true,true,false) == Outcome::Md5Mismatch);
    assert(result(true,true,true,true,true,true,true,true,true,0) == Outcome::TotalUnavailable);
    assert(result(true,true,true,true,true,true,true,true,true,3) == Outcome::Truncated);
    assert(result(true,true,true,true,true,true,true,true,true,2,1) == Outcome::DecoderError);
    assert(result(true,true,true,true,true,true,true,true,true,2,0,1) == Outcome::FormatContradiction);
    assert(result(true,true,true,true,true,true,true,true,true,2,0,0,false) == Outcome::CleanupFailed);
}
static void duplicateFdLifetime() {
    int pipes[2]; assert(::pipe(pipes) == 0);
    const int original = pipes[0];
    for (unsigned cleanupPath = 0; cleanupPath < 4; ++cleanupPath) {
        const int duplicate = ::dup(original); assert(duplicate >= 0);
        { OwnedFd owned(duplicate); if (cleanupPath == 0) assert(owned.closeNow()); }
        assert(::fcntl(original,F_GETFD) >= 0); assert(::fcntl(duplicate,F_GETFD) < 0 && errno == EBADF);
    }
    assert(::lseek(original,0,SEEK_CUR) < 0 && errno == ESPIPE);
    const uint8_t content[4]{'f','L','a','C'}; assert(::write(pipes[1],content,4) == 4); ::close(pipes[1]);
    OwnedFd sequential(::dup(original)); uint8_t bytes[4]; assert(::read(sequential.value,bytes,4) == 4);
    assert(::read(sequential.value,bytes,4) == 0); assert(sequential.closeNow()); assert(::fcntl(original,F_GETFD) >= 0);
    ::close(original);
}
static void moveTransfersDuplicateOwnership() {
    int pipes[2]; assert(::pipe(pipes)==0);
    const int duplicate=::dup(pipes[0]); assert(duplicate>=0);
    { OwnedFd pending(duplicate); OwnedFd decoder(std::move(pending));
      assert(pending.value==-1 && decoder.value==duplicate); assert(decoder.closeNow()); }
    assert(::fcntl(duplicate,F_GETFD)<0 && errno==EBADF);
    assert(::fcntl(pipes[0],F_GETFD)>=0); ::close(pipes[0]); ::close(pipes[1]);
}
static void sharedCanonicalConsumerPreservesWholeVerification() {
    Pcm verifier, producer; uint64_t queueHash=kOffset;
    for (int32_t value : {0,1,-1,1024,-1024,32767,-32768}) {
        uint8_t canonical[4];
        assert(verifier.append(value, -value == 32768 ? 32767 : -value));
        assert(producer.append(value, -value == 32768 ? 32767 : -value, canonical));
        queueHash=hashBytes(queueHash,canonical,4);
        assert(verifier.hash==producer.hash && queueHash==producer.hash);
        assert(verifier.frames==producer.frames && verifier.bytes==producer.bytes);
    }
}
int main() { moveTransfersDuplicateOwnership(); sharedCanonicalConsumerPreservesWholeVerification(); packingAndRange(); metadataGate(); orderedHashAndChunks(); boundedExamplesAndCounters(); overflow(); classification(); duplicateFdLifetime(); }
