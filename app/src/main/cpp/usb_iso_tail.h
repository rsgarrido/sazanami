#pragma once
#include "usb_iso_core.h"
#include <algorithm>
namespace sazanami::usbiso {
// Only the final, verified-EOF candidate may depart from the normal feedback request.
struct TerminalShape {
    std::array<uint32_t,kOutPackets> frames{};
    unsigned packets=0;
    uint64_t total=0;
    bool shortened=false, valid=false;
};
inline TerminalShape terminalShape(const std::array<uint32_t,kOutPackets>& normal, uint64_t remaining, bool verifiedEof) noexcept {
    TerminalShape result;
    if (!verifiedEof || !remaining) return result;
    for (auto count:normal) {
        if (!count) return TerminalShape{};
        const auto n=static_cast<uint32_t>(std::min<uint64_t>(remaining-result.total,count));
        if (!n) break;
        result.frames[result.packets++]=n; result.total+=n;
        result.shortened |= n!=count;
        if (result.total==remaining) break;
    }
    result.valid=result.total==remaining;
    return result;
}
inline bool naturalOutComplete(bool verifiedEof, uint64_t total, uint64_t enqueued, uint64_t consumed,
    uint64_t submitted, uint64_t completed, size_t pendingOut, uint64_t prepared, bool valid) noexcept {
    return valid && verifiedEof && total>0 && enqueued==total && consumed==total && submitted==total &&
        completed==total && pendingOut==0 && prepared==0;
}
} // namespace sazanami::usbiso
