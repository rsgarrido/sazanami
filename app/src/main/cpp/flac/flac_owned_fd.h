#pragma once
#include <unistd.h>
namespace sazanami::flacverify {
struct OwnedFd {
    int value;
    explicit OwnedFd(int duplicate) : value(duplicate) {}
    OwnedFd(OwnedFd&& other) noexcept : value(other.value) { other.value=-1; }
    OwnedFd(const OwnedFd&) = delete;
    OwnedFd& operator=(const OwnedFd&) = delete;
    bool closeNow() noexcept {
        if (value < 0) return false;
        const int duplicate = value; value = -1;
        return ::close(duplicate) == 0; // Never retry close(EINTR) on a potentially reused fd.
    }
    ~OwnedFd() { if (value >= 0) ::close(value); }
};
} // namespace sazanami::flacverify
