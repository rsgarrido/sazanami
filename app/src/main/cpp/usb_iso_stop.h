#pragma once
#include "usb_iso_core.h"
#include <cerrno>
namespace sazanami::usbiso {
// A cancellation acknowledgement is not evidence of physical removal. Inspect
// explicit statuses only; neither generic packet errors nor later cleanup may
// retroactively replace the first authoritative cause.
inline bool completionIndicatesDetach(int urbStatus,int packetStatus,bool cancelled) noexcept {
    return !cancelled && (urbStatus==-ENODEV || packetStatus==-ENODEV);
}
// First observed authoritative cause wins. Cleanup still accumulates integrity evidence.
struct StopCause {
    bool chosen = false;
    Stop value = Stop::Duration;
    void choose(Stop cause) noexcept { if (!chosen) { value = cause; chosen = true; } }
};
inline bool durationReached(uint64_t start, uint64_t now, uint64_t target) noexcept {
    return target>0 && now >= start && now - start >= target;
}
// Evaluate only AFTER freed OUT slots have been offered for resubmission.
inline bool outPoolStalled(size_t outstanding, uint64_t ageUs) noexcept {
    return outstanding == 0 || ageUs > kQueueWatchdogUs;
}
} // namespace sazanami::usbiso
