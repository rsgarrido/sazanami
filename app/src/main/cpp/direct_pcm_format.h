#pragma once
#include <cstdint>

namespace sazanami::directpcm {
constexpr bool supportsRate(uint32_t rate) noexcept { return rate == 44100 || rate == 48000; }
constexpr bool supports(uint32_t rate, uint32_t channels, uint32_t bits) noexcept {
    return supportsRate(rate) && channels == 2 && bits == 16;
}
} // namespace sazanami::directpcm
