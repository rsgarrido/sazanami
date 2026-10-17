#pragma once
#include "usb_iso_core.h"

namespace sazanami::usbiso {
// FLAC direct paths: measured ~15.4 ms host gaps, a 20 ms engineering
// coverage budget, and one URB of phase margin. Not an Android latency guarantee.
constexpr uint64_t kRequiredDirectOutCoverageUs = 20000;
constexpr size_t kOutPhaseMarginUrbs = 1;
constexpr size_t kMaximumDirectOutUrbs =
    (kRequiredDirectOutCoverageUs+kOutPackets*125-1)/(kOutPackets*125)+kOutPhaseMarginUrbs;
constexpr size_t directOutUrbCount(uint64_t interval) noexcept {
    // Exactly the existing supported HS data intervals; no new configurations.
    if (interval!=125 && interval!=250 && interval!=500) return 0;
    const auto duration=kOutPackets*interval;
    const auto count=(kRequiredDirectOutCoverageUs+duration-1)/duration+kOutPhaseMarginUrbs;
    return count<=kMaximumDirectOutUrbs ? static_cast<size_t>(count) : 0;
}
constexpr size_t outUrbCount(uint64_t interval,int sourceMode) noexcept {
    if (sourceMode<0 || sourceMode>3 || !directOutUrbCount(interval)) return 0;
    // Keep established Session 7/8 zero/reference diagnostics at their legacy depth.
    return sourceMode>=2 ? directOutUrbCount(interval) : kOutPool;
}
} // namespace sazanami::usbiso
