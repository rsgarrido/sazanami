#pragma once
#include <cstdint>
#include <memory>

namespace sazanami::directpcm {
constexpr bool supportsRate(uint32_t rate) noexcept { return rate == 44100 || rate == 48000; }
constexpr bool supports(uint32_t rate, uint32_t channels, uint32_t bits) noexcept {
    return channels == 2 && ((supportsRate(rate) && bits == 16) || (rate == 48000 && bits == 24));
}
struct Format final {
    const uint32_t sampleRateHz, channels, bitsPerSample, bytesPerSample, bytesPerFrame, subslotBytes, validBits;
    static std::shared_ptr<const Format> create(uint32_t rate, uint32_t channels, uint32_t bits) {
        return supports(rate,channels,bits) ? std::shared_ptr<const Format>(new Format(rate,channels,bits)) : nullptr;
    }
private:
    Format(uint32_t rate,uint32_t channels,uint32_t bits) : sampleRateHz(rate),channels(channels),bitsPerSample(bits),
        bytesPerSample(bits/8),bytesPerFrame(channels*(bits/8)),subslotBytes(bits/8),validBits(bits) {}
};
inline std::shared_ptr<const Format> referenceFormat() { return Format::create(44100,2,16); }
inline bool sampleInRange(int32_t sample,uint32_t bits) noexcept {
    return (bits==16 && sample>=-32768 && sample<=32767) || (bits==24 && sample>=-8388608 && sample<=8388607);
}
inline void packSample(uint8_t* bytes,int32_t sample,uint32_t bytesPerSample) noexcept {
    const uint32_t raw=static_cast<uint32_t>(sample);
    for(uint32_t i=0;i<bytesPerSample;++i) bytes[i]=static_cast<uint8_t>(raw>>(8*i));
}
inline int32_t unpackSample(const uint8_t* bytes,uint32_t bytesPerSample) noexcept {
    uint32_t raw=0;
    for(uint32_t i=0;i<bytesPerSample;++i) raw|=static_cast<uint32_t>(bytes[i])<<(8*i);
    const uint32_t bits=bytesPerSample*8;
    return static_cast<int32_t>(raw&(1U<<(bits-1)) ? static_cast<int64_t>(raw)-(1LL<<bits) : raw);
}
} // namespace sazanami::directpcm
