#pragma once
#include <sys/eventfd.h>
#include <unistd.h>
#include <cerrno>
#include <atomic>
#include <cstdint>
#include <stdexcept>

namespace sazanami::usbiso {
// Shared ownership lets a source fault wake a paused worker without a dangling/reused fd.
struct WakeSignal final {
    const int fd;
    std::atomic<bool> failed{false};
    WakeSignal() : fd(::eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK)) { if(fd<0) throw std::runtime_error("Control wake unavailable"); }
    ~WakeSignal() { ::close(fd); }
    WakeSignal(const WakeSignal&)=delete;WakeSignal& operator=(const WakeSignal&)=delete;
    bool notify() noexcept {
        const uint64_t one=1;
        for(unsigned i=0;i<2;++i) {
            if(::write(fd,&one,sizeof(one))==static_cast<ssize_t>(sizeof(one))) return true;
            if(errno==EAGAIN) return true; // A wake is already pending.
            if(errno!=EINTR) break;
        }
        failed.store(true);return false;
    }
    void consume() noexcept {
        uint64_t value;
        const auto count=::read(fd,&value,sizeof(value));
        if(count<0 && errno!=EAGAIN && errno!=EINTR) failed.store(true);
    }
};
}
