#pragma once
#include <cstdint>

namespace sazanami::usbiso {
enum class PlaybackPhase : int { Starting, Playing, Pausing, Paused, Resuming, Stopping, Stopped, Ended, Failed };
enum class ControlOutcome : int { None, Requested, Already, Confirmed, Superseded, Terminal, Failed };
struct PauseBoundary {
    uint64_t out, feedback, submitted, completed, consumed, prepared, bytes, actualBytes, sourceHash, submittedHash;
    bool valid;
    bool reconciled() const noexcept {
        return valid && !out && !feedback && !prepared && submitted==completed && submitted==consumed &&
            bytes==actualBytes && sourceHash==submittedHash;
    }
};
// Worker-owned lifecycle only. Command intent is a separate, generation-bound mailbox.
struct PauseMachine {
    PlaybackPhase phase=PlaybackPhase::Starting;
    uint64_t pauses=0, resumes=0, drainStarted=0, resumeStarted=0, pausedStarted=0, pausedUs=0, pausedFrame=0;
    ControlOutcome pauseOutcome=ControlOutcome::None, resumeOutcome=ControlOutcome::None;
    int drainOutcome=0, resumeReadiness=0, resumeFailure=0;
    bool phaseIs(PlaybackPhase value) const noexcept { return phase==value; }
    bool terminal() const noexcept { return phase==PlaybackPhase::Stopped || phase==PlaybackPhase::Ended || phase==PlaybackPhase::Failed; }
    bool admitsOut() const noexcept { return phase==PlaybackPhase::Starting || phase==PlaybackPhase::Playing || phase==PlaybackPhase::Resuming; }
    bool quiet() const noexcept { return phase==PlaybackPhase::Pausing || phase==PlaybackPhase::Paused; }
    bool drainExpired(uint64_t now,uint64_t limit) const noexcept { return phase==PlaybackPhase::Pausing && now-drainStarted>=limit; }
    void pause(uint64_t now) noexcept {
        if(terminal() || phase==PlaybackPhase::Stopping || quiet()) return;
        if(phase==PlaybackPhase::Resuming) resumeOutcome=ControlOutcome::Superseded;
        phase=PlaybackPhase::Pausing;drainStarted=now;drainOutcome=0;pauseOutcome=ControlOutcome::Requested;
    }
    bool confirmPause(uint64_t now,const PauseBoundary& boundary) noexcept {
        if(phase!=PlaybackPhase::Pausing || !boundary.reconciled()) return false;
        phase=PlaybackPhase::Paused;pausedStarted=now;pausedFrame=boundary.completed;++pauses;
        drainOutcome=1;pauseOutcome=ControlOutcome::Confirmed;return true;
    }
    void resume(uint64_t now) noexcept {
        if(phase!=PlaybackPhase::Paused) return;
        pausedUs+=now-pausedStarted;pausedStarted=0;phase=PlaybackPhase::Resuming;resumeStarted=now;
        resumeReadiness=0;resumeOutcome=ControlOutcome::Requested;
    }
    void submitted() noexcept {
        if(phase==PlaybackPhase::Resuming) { ++resumes;resumeOutcome=ControlOutcome::Confirmed; }
        if(admitsOut()) phase=PlaybackPhase::Playing;
    }
    void finish(uint64_t now,PlaybackPhase final) noexcept {
        if(phase==PlaybackPhase::Paused) pausedUs+=now-pausedStarted;
        pausedStarted=0;
        if(pauseOutcome==ControlOutcome::Requested) pauseOutcome=final==PlaybackPhase::Failed ? ControlOutcome::Failed : ControlOutcome::Terminal;
        if(resumeOutcome==ControlOutcome::Requested) resumeOutcome=final==PlaybackPhase::Failed ? ControlOutcome::Failed : ControlOutcome::Terminal;
        phase=final;
    }
};
} // namespace sazanami::usbiso
