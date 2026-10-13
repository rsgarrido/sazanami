#pragma once
#include "flac/flac_pcm_core.h"
#include <array>
namespace sazanami::usbiso {
enum class SeekOutcome : int { None, Requested, Draining, Repositioning, Priming, Confirmed, Superseded, Failed, Stopped };
// Online verification never depends on the diagnostic example cap.
struct SeekSegments {
    uint64_t epoch=0,start=0,submittedBase=0,completedBase=0,byteBase=0,actualBase=0;
    uint64_t submittedHash=sazanami::flacverify::kOffset;
    uint64_t sealed=0,frames=0,bytes=0,chain=sazanami::flacverify::kOffset;
    bool valid=true,targetProven=true;
    std::array<std::array<uint64_t,10>,8> examples{};
    size_t exampleCount=0;
    bool seal(uint64_t submitted,uint64_t completed,uint64_t consumed,uint64_t sent,uint64_t actual,
              uint64_t source,uint64_t payload,unsigned width,uint64_t total,bool eof) {
        if(!valid || !targetProven || start>total || submitted<submittedBase || completed<completedBase || sent<byteBase || actual<actualBase ||
            submitted!=completed || consumed!=submitted || source!=payload || !width ||
            submitted-submittedBase!=completed-completedBase || sent-byteBase!=actual-actualBase ||
            submitted-submittedBase>(total-start) || (submitted-submittedBase)>sazanami::flacverify::kCounterMax/width ||
            sent-byteBase!=(submitted-submittedBase)*width || (eof && start+submitted-submittedBase!=total)) return valid=false;
        const auto count=submitted-submittedBase,size=sent-byteBase;
        if(!sazanami::flacverify::canAdd(frames,count) || !sazanami::flacverify::canAdd(bytes,size) || !sazanami::flacverify::canAdd(sealed,1)) return valid=false;
        const std::array<uint64_t,10> row{epoch,start,start+count,count,count,size,size,source,payload,eof?1ULL:0ULL};
        // Explicit endian-independent chain of verified segment evidence.
        for(auto value:row) for(unsigned b=0;b<8;++b) {const uint8_t byte=static_cast<uint8_t>(value>>(b*8));chain=sazanami::flacverify::hashBytes(chain,&byte,1);}
        if(exampleCount<examples.size()) examples[exampleCount++]=row;
        ++sealed;frames+=count;bytes+=size;return true;
    }
    void begin(uint64_t e,uint64_t target,uint64_t submitted,uint64_t completed,uint64_t sent,uint64_t actual) {
        epoch=e;start=target;submittedBase=submitted;completedBase=completed;byteBase=sent;actualBase=actual;
        submittedHash=sazanami::flacverify::kOffset;targetProven=false;
    }
};
} // namespace sazanami::usbiso
