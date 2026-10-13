#include "usb_pcm_reference.h"
#include "usb_iso_stop.h"
#include "usb_iso_tail.h"
#include "usb_feedback_policy.h"
#include "usb_feedback_diagnostics.h"
#include "usb_out_queue_policy.h"
#include "usb_out_continuity.h"
#include "usb_pause_control.h"
#include "usb_seek_control.h"
#include "flac/flac_prefix_queue.h"
#include "flac/flac_snapshot.h"
#include <jni.h>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>
#include <poll.h>
#include <atomic>
#include <chrono>
#include <cstdlib>
#include <cstring>
#include <cerrno>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <vector>
#include <stdexcept>
#include <algorithm>

namespace {
using namespace sazanami::usbiso;
namespace fv = sazanami::flacverify;
// Versioned immutable result schema; mirrored by UsbIsoNativeStats.fields, not a pointer packet.
enum Field : size_t { Api, Attachment, Stream, Started, StateCode, Reasons, StopCode, DurationUs,
    SourceFrames, ScheduledFrames, SubmittedFrames, CompletedFrames, OutSubmitted, OutCompleted,
    OutPacketsSubmitted, OutPacketsCompleted, OutBytesSubmitted, KernelOutActualBytes, OutPacketErrors, OutUrbErrors,
    FeedbackSubmitted, FeedbackCompleted, FeedbackPacketsCompleted, FeedbackValid, FeedbackInvalid, FeedbackTimeouts,
    Discarded, CancellationCompletions, CompletionsAfterStop, UnknownCompletions, DuplicateCompletions,
    OutAtStop, FeedbackAtStop, OutstandingFinal, WorkerStopped, BootstrapPackets, FeedbackScheduledPackets,
    LastFeedbackAge, MinFeedback, MaxFeedback, SumFeedback, FeedbackSamples, InvalidStreak, Remainder,
    Underruns, CancelledFrames, NotSubmittedFrames, PcmByteOr, SubmitErrno, ReapErrno, DiscardErrno,
    FirstUrbStatus, FirstPacketStatus, FirstPacketLength, FirstPacketActual, OutCancelled, FeedbackCancelled,
    FeedbackInterval, DataInterval, EndpointCapacity, FeedbackHoldover, ShutdownIncomplete, FirstUrbErrorCount,
    FeedbackUrbErrors, LifecycleMask, SourceMode, PreflightPass, ReferenceTableHash, SourcePcmHash, SubmittedPcmHash,
    SilenceFrames, LeftToneFrames, RightToneFrames, LeftActiveSamples, RightActiveSamples,
    UnexpectedNonzero, InactiveNonzero, OutsideNonzero, MaximumSample, MinimumSample, PeakSample, ActivePerCycle, PrefixConsumed, PrefixPrimed, PrefixFaults, PrefixStopped, PrefixJoined, EmptyAfterReap, PoolStalls, PoolAtStall, CompletionAgeAtStall, DurationTarget, ActiveAtStop, FullExpected, FullEofReached, FullNaturalProducer, TailRemaining, TailFrames, TailPackets, TailUrbs, TailPacketCompletions, TailFrameCompletions, EofPadding, FullLastSubmitted, FullLastCompleted, FullQueueNatural, FullDiscardNatural, FullMd5Pass, FullDecodedHash, FullEnqueuedHash, SourceVerificationStalls,
    ConfiguredFeedbackUrbs, FeedbackPacketsPerUrb, FeedbackCoverageUs, BootstrapTimeouts, WatchdogTimeouts, ReapedTimeouts,
    TimeoutSubtype, TimeoutObservedUs, TimeoutLastObservedUs, TimeoutLastSubmittedUs, TimeoutOffendingSubmittedUs,
    TimeoutAgeUs, TimeoutOutstanding, TimeoutReapedNotRearmed, TimeoutAcceptedRaw,
    RequestsOverHoldover, MaxRequestAgeUs, LastAcceptedSubmittedUs, LastAcceptedObservedUs, LastAcceptedRequestAgeUs,
    TimeoutReapAttempted, TimeoutReapCount, TimeoutReapFeedbackCount, TimeoutReapRefreshed,
    TimeoutAgeBeforeReap, TimeoutAgeAfterReap, TimeoutWatchdogEvaluated, TimeoutWatchdogFired,
    FeedbackRecoveredGaps,
    ConfiguredOutUrbs, OutPacketsPerUrb, OutUrbDurationUs, OutNominalCoverageUs,
    MaxWorkerLoopGapUs, MaxGapObservedAtUs, OutAtMaxGap, FeedbackAtMaxGap, MaxGapOutRefilled, MaxGapNeedsOut, WorkerGapsBeyondOutCoverage,
    OutContinuityObservations, MinOutOutstanding, OutZeroObservations, OutOneObservations, OutTwoObservations,
    MinEstimatedOutCoverageUs, WorstOutCoverageAtUs, OutCoverageBelowOneUrb, OutCoverageBelowTwoUrbs,
    MaxOutSubmissionBatchGapUs, MaxOutSubmissionGapUs, MaxOutCompletionGapUs, MaxOutRefillOpportunityGapUs, MaxOutRefillLatencyUs,
    OutContinuityRisks, FirstOutRiskAtUs, FirstOutRiskMask, FirstOutRiskStage, FirstOutRiskOut, FirstOutRiskFeedback,
    FirstOutRiskGapUs, FirstOutRiskCoverageUs, FirstOutRiskCompletedFrames, SchedulerNominalRate, FormatChannels, FormatBits, FormatSampleBytes, FormatFrameBytes, FormatSubslot, FormatValidBits, FormatAlternate,
    PlaybackPhaseField, PauseRequests, PauseConfirmed, ResumeRequests, ResumeConfirmed, LastPauseOutcome, LastResumeOutcome,
    FirstPauseFrame, LastPauseFrame, PausedFrame, PausedDurationUs, PausePendingOut, PauseDrainOutcome, ResumeReadiness,
    ResumeFailure, SourceContinuityAcrossPause, PauseDrainFailures,
    SeekCapable, SeekRequests, SeekConfirmed, SeekFailures, SeekSuperseded, SeekOutcomeField, FirstSeekTarget, LastSeekTarget,
    ConfirmedSeekTarget, SeekEpoch, SeekOldBoundary, SeekDrainOutcome, SeekPendingOut, SeekInvalidatedFrames, SeekInvalidatedBytes,
    SeekSourceReady, SeekFirstFrame, SeekFirstProven, SeekSegmentCount, SeekSkipped, SeekRevisited, SeekSourcePosition,
    SeekSegmentCompleted, SeekConfirmedSerial, SeekSegmentIntegrity, SeekChainHash, SeekTailUrbs, SeekTailPackets, SeekTailFrames,
    SeekTailPacketCompletions, SeekTailFrameCompletions, SeekOriginalNext, SeekAttempts, SeekSegmentFrames, SeekSegmentBytes,
    SeekWholeMd5AfterSeek, SeekStartedPaused, FieldCount };
constexpr jint kSnapshotVersion = 13;
constexpr size_t kSnapshotFieldCount=FieldCount+kFeedbackDiagnosticWords;
static_assert(FieldCount==236 && kSnapshotFieldCount==508); // Historical/progress fields do not move.
constexpr size_t kControlFieldCount=SeekCapable-PlaybackPhaseField;
constexpr size_t kSeekFieldCount=FieldCount-SeekCapable;
static_assert(kControlFieldCount==17);
static_assert(kSeekFieldCount==37);
// Preserve unsigned hash bits across JNI without an implementation-defined numeric cast.
jlong hashBits(uint64_t value) { jlong result; static_assert(sizeof(result) == sizeof(value)); std::memcpy(&result, &value, sizeof(result)); return result; }
using Clock = std::chrono::steady_clock;
uint64_t monotonicUs() { return static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::microseconds>(Clock::now().time_since_epoch()).count()); }

struct Slot {
    // calloc alignment is suitable for usbdevfs_urb. UAPI has a flexible array with zero included entries.
    struct Free { void operator()(usbdevfs_urb* pointer) const noexcept { std::free(pointer); } };
    std::unique_ptr<usbdevfs_urb, Free> urb;
    std::vector<uint8_t> buffer;
    std::array<uint32_t, kOutPackets> frames{};
    Identity identity;
    bool feedback = false;
    unsigned packets = 0;
    uint64_t submittedAt = 0, firstSourceFrame = 0;
    uint64_t outReapedAt = 0; // Diagnostic observation time; not a USB service timestamp.
    bool terminal = false;
    uint64_t sourceEpoch=0;
    void allocate(bool isFeedback, unsigned count, unsigned capacityBytes) {
        if (count < 1 || count > kOutPackets || capacityBytes < 1 || capacityBytes > 3072) throw std::invalid_argument("URB bounds");
        const size_t bytes = allocationBytes(sizeof(usbdevfs_urb), sizeof(usbdevfs_iso_packet_desc), count);
        if (!bytes) throw std::invalid_argument("URB size overflow");
        urb.reset(static_cast<usbdevfs_urb*>(std::calloc(1, bytes)));
        if (!urb) throw std::bad_alloc();
        buffer.resize(static_cast<size_t>(count) * capacityBytes, 0);
        feedback = isFeedback; packets = count;
    }
};

struct Session {
    int fd; // Borrowed; Java closes, only after this execution context returns.
    const uint64_t attachment, stream;
    const int dataEndpoint, feedbackEndpoint, dataInterval, feedbackInterval, selectedAlternate;
    const std::shared_ptr<const sazanami::directpcm::Format> format;
    const uint32_t payload, sampleRateHz; // Immutable progress rate; never read mutable scheduler off-worker.
    std::atomic<int> requestedStop{0};
    std::atomic<bool> executing{false};
    const size_t outUrbs, feedbackUrbs;
    std::vector<Slot> slots; // Fixed once before streaming; eight packets per full OUT URB.
    std::array<jlong, FieldCount> stats{};
    std::array<jlong, kFeedbackExamples * 6> feedbackExamples{};
    std::array<jlong, kPacketExamples * 7> packetExamples{};
    size_t feedbackExampleCount = 0, packetExampleCount = 0;
    State state = State::Idle;
    Integrity integrity;
    Scheduler scheduler;
    PcmTestSource source;
    std::shared_ptr<fv::PrefixSource> prefix;
    const uint64_t durationUs;
    const bool fullTrack;
    bool waitingForEof = false;
    std::atomic<uint64_t> completedProgress{0}; // One writer; sampled without reading mutable stats.
    const uint64_t totalProgress;
    uint64_t submittedPrefixHash = fv::kOffset;
    uint64_t started = 0, lastOutCompletion = 0, serial = 0;
    FeedbackObservationClock feedbackClock;
    FeedbackReapPass feedbackReapPass; // Copied to first-timeout evidence before shutdown.
    FeedbackDiagnostics feedbackDiagnostics; // Worker-owned; exported only after run/cleanup.
    uint64_t diagnosticSeekConfirmed=0,diagnosticOutResumed=0,diagnosticFreshPrime=0;
    uint64_t diagnosticAdmissionStopped=0,diagnosticPoolsDrained=0;
    OutContinuity outContinuity;
    uint32_t acceptedFeedback = 0;
    unsigned invalidStreak = 0;
    bool stopping = false, ran = false;
    StopCause stopCause;
    PauseMachine playback;
    const std::shared_ptr<WakeSignal> controlWake;
    struct Command { uint64_t serial=0,pauses=0,resumes=0,firstPause=0,lastPause=0,pauseAt=0,seeks=0,seekSerial=0,target=0,seekAt=0,firstTarget=0;bool play=true; };
    mutable std::mutex controlMutex;
    Command command;
    uint64_t seenPauses=0,seenResumes=0;
    bool commandsClosed=false,resumePrepared=false,playbackFault=false;
    uint64_t seenSeeks=0;
    std::array<uint64_t,2> livePlayback{0,static_cast<uint64_t>(PlaybackPhase::Starting)};
    std::array<jlong,kControlFieldCount> liveControl{};
    uint64_t livePausedStarted=0;
    std::array<jlong,kSeekFieldCount> liveSeek{};
    SeekSegments segments;
    uint64_t activeSeekSerial=0,activeSeekTarget=0,seekStarted=0,confirmedSeekSerial=0,confirmedEpoch=0,frozenPosition=0;
    bool seekSealed=false,seekAuthorized=false,seekWasPaused=false;
    SeekOutcome seekOutcome=SeekOutcome::None;
    uint64_t sourcePosition() const {return activeSeekSerial>confirmedSeekSerial?frozenPosition:segments.start+static_cast<uint64_t>(stats[CompletedFrames])-segments.completedBase;}
    bool hasPendingSeek() const {return fullTrack && intent().seekSerial>activeSeekSerial;}
    Command intent() const { std::lock_guard<std::mutex> lock(controlMutex);return command; }
    bool pauseAdmission() const { return fullTrack && (!intent().play || playback.quiet() || hasPendingSeek()); }
    void publishPlayback() {
        std::lock_guard<std::mutex> lock(controlMutex);
        livePlayback={static_cast<uint64_t>(stats[CompletedFrames]),static_cast<uint64_t>(playback.phase)};
        liveControl={static_cast<jlong>(playback.phase),static_cast<jlong>(command.pauses),static_cast<jlong>(playback.pauses),
            static_cast<jlong>(command.resumes),static_cast<jlong>(playback.resumes),static_cast<jlong>(playback.pauseOutcome),
            static_cast<jlong>(playback.resumeOutcome),static_cast<jlong>(command.firstPause),static_cast<jlong>(command.lastPause),
            static_cast<jlong>(playback.pausedFrame),static_cast<jlong>(playback.pausedUs),0,playback.drainOutcome,
            playback.resumeReadiness,playback.resumeFailure,
            integrity.valid() && !stats[PauseDrainFailures] && !playback.resumeFailure,stats[PauseDrainFailures]};
        livePausedStarted=playback.phaseIs(PlaybackPhase::Paused) ? playback.pausedStarted : 0;
        if(fullTrack) {
            stats[SeekCapable]=prefix->seekCapable();stats[SeekRequests]=command.seeks;stats[LastSeekTarget]=command.target;stats[FirstSeekTarget]=command.firstTarget;
            stats[SeekOutcomeField]=static_cast<jlong>(seekOutcome);stats[SeekSourcePosition]=sourcePosition();
            stats[SeekEpoch]=confirmedEpoch;stats[SeekSegmentCompleted]=stats[CompletedFrames]-segments.completedBase;
            stats[SeekConfirmedSerial]=confirmedSeekSerial;stats[SeekSegmentCount]=segments.sealed;
            stats[SeekSegmentIntegrity]=segments.valid;stats[SeekChainHash]=hashBits(segments.chain);
            stats[SeekSegmentFrames]=segments.frames;stats[SeekSegmentBytes]=segments.bytes;
            stats[SeekAttempts]=prefix->attempted.load();stats[SeekWholeMd5AfterSeek]=stats[SeekAttempts]>0;
            std::copy(stats.begin()+SeekCapable,stats.end(),liveSeek.begin());
        }
        if(playback.terminal() || playback.phaseIs(PlaybackPhase::Stopping)) commandsClosed=true;
    }
    bool requestPlayback(bool play,uint64_t sequence,uint64_t pauses,uint64_t resumes,uint64_t seeks,uint64_t seekSerial,uint64_t target,uint64_t firstTarget) {
        {
            std::lock_guard<std::mutex> lock(controlMutex);
            if(!fullTrack || commandsClosed || requestedStop.load()) return false;
            if(sequence<command.serial || pauses<command.pauses || resumes<command.resumes ||
                seeks<command.seeks || seekSerial<command.seekSerial || seekSerial>sequence ||
                resumes>static_cast<uint64_t>(INT64_MAX) || pauses>static_cast<uint64_t>(INT64_MAX)-resumes ||
                seeks>static_cast<uint64_t>(INT64_MAX)-pauses-resumes || sequence!=pauses+resumes+seeks)
                throw std::invalid_argument("Control sequence");
            if(sequence==command.serial && play!=command.play) throw std::invalid_argument("Control intent");
            if(seeks>command.seeks) {
                if(!prefix->seekCapable() || target>=totalProgress || firstTarget>=totalProgress || seekSerial<=command.seekSerial) return false;
                if(command.seeks && firstTarget!=command.firstTarget) throw std::invalid_argument("First seek target");
                if(!command.seeks) command.firstTarget=firstTarget;
                command.target=target;command.seekSerial=seekSerial;command.seekAt=monotonicUs();
            } else if(seekSerial!=command.seekSerial || target!=command.target || firstTarget!=command.firstTarget) throw std::invalid_argument("Seek intent");
            if(pauses>command.pauses) {
                const auto frame=completedProgress.load();
                if(!command.pauses) command.firstPause=frame;
                command.lastPause=frame;command.pauseAt=monotonicUs();
            }
            command.serial=sequence;command.pauses=pauses;command.resumes=resumes;command.seeks=seeks;command.play=play;
        }
        if(!controlWake->notify()) throw std::runtime_error("Control wake failed");
        return true;
    }

    Session(int borrowedFd, uint64_t a, uint64_t s, const std::array<jint, 17>& config, std::shared_ptr<fv::PrefixSource> prefixSource = nullptr)
        : fd(borrowedFd), attachment(a), stream(s), dataEndpoint(config[4]), feedbackEndpoint(config[8]),
          dataInterval(config[7]), feedbackInterval(config[11]), selectedAlternate(config[2]),
          format(prefixSource ? prefixSource->queue.format : sazanami::directpcm::Format::create(config[12],config[13],config[15])), payload(capacity(config[6])), sampleRateHz(static_cast<uint32_t>(config[12])),
          outUrbs(outUrbCount(intervalUs(dataInterval),config[16])),
          feedbackUrbs(feedbackUrbCount(intervalUs(feedbackInterval))), slots(outUrbs+feedbackUrbs),
          scheduler(sampleRateHz),
          source(config[16] >= 2 ? PcmMode::Zero : static_cast<PcmMode>(config[16])),
          prefix(std::move(prefixSource)), durationUs(prefix && prefix->fullTrack ? 0 : prefix ? fv::kPrefixDurationUs : kDurationUs), fullTrack(prefix && prefix->fullTrack), totalProgress(fullTrack ? prefix->queue.expectedTotal.load() : 0),
          outContinuity(outUrbs,kOutPackets,intervalUs(dataInterval)),controlWake(fullTrack ? std::make_shared<WakeSignal>() : nullptr) {
        stats[Api] = kSnapshotVersion; stats[Attachment] = static_cast<jlong>(a); stats[Stream] = static_cast<jlong>(s);
        stats[DataInterval] = static_cast<jlong>(intervalUs(dataInterval));
        stats[FeedbackInterval] = static_cast<jlong>(intervalUs(feedbackInterval));
        stats[EndpointCapacity] = payload;
        stats[SchedulerNominalRate] = scheduler.nominalRate;
        stats[FormatChannels]=format->channels;stats[FormatBits]=format->bitsPerSample;stats[FormatSampleBytes]=format->bytesPerSample;
        stats[FormatFrameBytes]=format->bytesPerFrame;stats[FormatSubslot]=format->subslotBytes;stats[FormatValidBits]=format->validBits;stats[FormatAlternate]=selectedAlternate;
        stats[SourceMode] = config[16]; stats[DurationTarget] = static_cast<jlong>(durationUs);
        if (fullTrack) { stats[FullExpected]=static_cast<jlong>(prefix->queue.expectedTotal.load()); stats[FullLastSubmitted]=stats[FullLastCompleted]=-1; }
        if (source.mode == PcmMode::Reference) {
            const ReferencePreflight preflight;
            stats[PreflightPass] = preflight.pass() ? 1 : 0; stats[ReferenceTableHash] = hashBits(preflight.actualHash);
            stats[ActivePerCycle] = activePerCycle();
            if (!preflight.pass()) throw std::invalid_argument("Reference preflight failed");
        }
        if (!outUrbs) throw std::invalid_argument("OUT interval/mode bounds");
        if (!feedbackUrbs) throw std::invalid_argument("Feedback interval bounds");
        stats[FeedbackHoldover] = static_cast<jlong>(feedbackHoldoverUs(intervalUs(feedbackInterval)));
        stats[ConfiguredFeedbackUrbs]=static_cast<jlong>(feedbackUrbs); stats[FeedbackPacketsPerUrb]=1;
        stats[FeedbackCoverageUs]=static_cast<jlong>(feedbackUrbs*intervalUs(feedbackInterval));
        for (size_t i = 0; i < slots.size(); ++i) slots[i].allocate(i >= outUrbs,
            i >= outUrbs ? 1 : static_cast<unsigned>(kOutPackets), i >= outUrbs ? 4 : payload);
        if(fullTrack) std::atomic_store(&prefix->queue.workerWake,controlWake);
        move(State::Configured);
    }
    void move(State next) {
        if (!transition(state, next)) integrity.compromise(SchedulerInvalid);
        stats[StateCode] = static_cast<int>(state);
        stats[LifecycleMask] |= 1LL << static_cast<int>(state);
    }
    uint32_t frameBytes() const { return format->bytesPerFrame; }
    void fail(uint64_t reason, Stop stop) {
        if(stop==Stop::Feedback && reason!=FeedbackTimeout) freezeFeedback(reason==InvalidFeedback?FeedbackFatalReason::PersistentInvalid:
            reason==MalformedFeedback?FeedbackFatalReason::Malformed:FeedbackFatalReason::QuietCompletion,monotonicUs(),true);
        const bool fatal=stop!=Stop::Requested;
        if(fullTrack && fatal) playbackFault=true;
        if(fullTrack && fatal && playback.seeking()) {
            if(!stats[SeekFailures]) ++stats[SeekFailures];seekOutcome=SeekOutcome::Failed;segments.valid=false;
            if(playback.phaseIs(PlaybackPhase::SeekDraining)) stats[SeekDrainOutcome]=2;
        }
        if(fullTrack && fatal && playback.phaseIs(PlaybackPhase::Pausing)) {
            playback.pauseOutcome=ControlOutcome::Failed;playback.drainOutcome=2;
            if(!stats[PauseDrainFailures]) ++stats[PauseDrainFailures];
        }
        if(fullTrack && fatal && (playback.phaseIs(PlaybackPhase::Resuming) || (playback.resumes && playback.resumeReadiness==1))) {
            if(!playback.resumeFailure) playback.resumeFailure=3;playback.resumeOutcome=ControlOutcome::Failed;
        }
        integrity.compromise(reason); if (!stopping) stopCause.choose(stop);
    }
    void failSource() {
        const auto faults = prefix->queue.faults.load();
        if (faults & fv::PrefixUnderrun) fail(QueueUnderrun | FlacSourceFault, Stop::Underrun);
        else if (faults & fv::PrefixEof) fail(FlacSourceFault, Stop::SourceEof);
        else if (faults == fv::PrefixCancelled) fail(AuthorityChanged, Stop::Requested);
        else fail(FlacSourceFault, Stop::Source);
    }
    size_t outstanding(bool feedback) const {
        size_t count = 0;
        for (const auto& slot : slots) if (slot.feedback == feedback && slot.identity.outstanding) ++count;
        return count;
    }
    void waitPlayback(int timeout,bool drain=false) {
        pollfd descriptors[2]{{fd,static_cast<short>(drain ? POLLOUT : 0),0},{controlWake->fd,POLLIN,0}};
        const auto returned=::poll(descriptors,2,timeout);
        if(returned<0 && errno!=EINTR) fail(ReapError,Stop::Reap);
        if(descriptors[0].revents&(POLLERR|POLLHUP|POLLNVAL)) fail(DeviceDetached,Stop::Detached);
        if(descriptors[1].revents&(POLLERR|POLLHUP|POLLNVAL)) fail(WorkerFailure,Stop::Worker);
        if(descriptors[1].revents&POLLIN) controlWake->consume();
        if(controlWake->failed.load()) fail(WorkerFailure,Stop::Worker);
    }
    bool playbackStep(uint64_t now) {
        if(!fullTrack) return false;
        if(controlWake->failed.load()) { fail(WorkerFailure,Stop::Worker);return true; }
        const auto wanted=intent();
        if(wanted.pauses!=seenPauses) {
            playback.pauseOutcome=playback.phaseIs(PlaybackPhase::Paused) ? ControlOutcome::Already :
                wanted.play ? ControlOutcome::Superseded : ControlOutcome::Requested;seenPauses=wanted.pauses;
        }
        if(wanted.resumes!=seenResumes) {
            playback.resumeOutcome=playback.phaseIs(PlaybackPhase::Playing) ? ControlOutcome::Already : ControlOutcome::Requested;
            seenResumes=wanted.resumes;
        }
        // Decide and execute from one mailbox snapshot. A command arriving afterward blocks admission
        // and wakes the next iteration; it must not dispatch an old intent through an old seek deadline.
        if(wanted.seekSerial>activeSeekSerial || playback.seeking()) return seekStep(now,wanted);
        publishPlayback();
        if((playback.quiet() || playback.phaseIs(PlaybackPhase::Resuming) || !wanted.play) && naturalComplete()) return true;
        if(!wanted.play && playback.admitsOut()) {
            diagnosticAdmissionStopped=now;
            outContinuity.suspendSegment(wanted.pauseAt,outstanding(false),outstanding(true),!fullSubmitted(),static_cast<uint64_t>(stats[CompletedFrames]));
            playback.pause(now);publishPlayback();
        }
        if(playback.phaseIs(PlaybackPhase::Pausing)) {
            // No rearming or DISCARD. Previously admitted OUT and feedback must reconcile normally.
            for(size_t i=0;i<slots.size() && integrity.valid() && !requestedStop.load() && reapOne();++i) {}
            completedProgress.store(static_cast<uint64_t>(stats[CompletedFrames]),std::memory_order_release);publishPlayback();
            if(!integrity.valid() || stopCause.chosen || requestedStop.load()) return true;
            if(prefix->queue.faults.load()) { failSource();return true; }
            if(naturalComplete()) return true;
            if(!outstanding(false) && !outstanding(true)) {
                const auto& q=prefix->queue;
                const PauseBoundary boundary{0,0,static_cast<uint64_t>(stats[SubmittedFrames]),static_cast<uint64_t>(stats[CompletedFrames]),
                    q.consumedTotal.load(),q.prepared,static_cast<uint64_t>(stats[OutBytesSubmitted]),static_cast<uint64_t>(stats[KernelOutActualBytes]),
                    q.consumedHash,submittedPrefixHash,integrity.valid() && stats[ScheduledFrames]==stats[SubmittedFrames] && !stats[OutCancelled]};
                if(!playback.confirmPause(monotonicUs(),boundary)) {
                    ++stats[PauseDrainFailures];playback.drainOutcome=2;fail(TerminalAccounting,Stop::Accounting);return true;
                }
                diagnosticPoolsDrained=monotonicUs();
                publishPlayback();return true;
            }
            if(playback.drainExpired(monotonicUs(),kDrainUs)) {
                ++stats[PauseDrainFailures];playback.drainOutcome=2;fail(ShutdownUnaccounted,Stop::Accounting);return true;
            }
            waitPlayback(1,true);return true;
        }
        if(playback.phaseIs(PlaybackPhase::Paused)) {
            if(wanted.play) { playback.resume(now);resumePrepared=false;publishPlayback(); }
            else { waitPlayback(-1);return true; }
        }
        if(playback.phaseIs(PlaybackPhase::Resuming) && !resumePrepared) {
            const auto head=prefix->queue.sourceHead(),total=prefix->queue.expectedTotal.load();
            if(head>total) { playback.resumeFailure=2;fail(TerminalAccounting,Stop::Accounting);return true; }
            const auto remaining=total-head,target=std::min<uint64_t>(fv::kPrefixPrebuffer,remaining);
            if(!remaining || prefix->queue.depth()<target || (target<fv::kPrefixPrebuffer && !prefix->queue.eofReached.load(std::memory_order_acquire))) {
                const auto elapsed=monotonicUs()-playback.resumeStarted;
                if(elapsed>=5000000) {
                    playback.resumeFailure=1;fail(FlacSourceFault,Stop::SourceVerificationStall);return true;
                }
                waitPlayback(static_cast<int>((5000000-elapsed+999)/1000));return true;
            }
            if(prefix->queue.frameBytes()!=frameBytes() || prefix->metadataFacts[fv::Rate]!=sampleRateHz) {
                playback.resumeFailure=2;fail(SchedulerInvalid,Stop::Accounting);return true;
            }
            acceptedFeedback=0;invalidStreak=0;stats[InvalidStreak]=0;feedbackClock={};feedbackClock.started=monotonicUs();
            feedbackDiagnostics.recovered();diagnosticFreshPrime=feedbackClock.started;diagnosticOutResumed=0;
            lastOutCompletion=feedbackClock.started;outContinuity.resumeSegment();
            for(auto& slot:slots) slot.outReapedAt=0;
            resumePrepared=true;playback.resumeReadiness=1;
            for(size_t i=outUrbs;i<slots.size() && integrity.valid() && !stopCause.chosen;++i) submit(slots[i]);
            outContinuity.beginRefill(monotonicUs(),true);
            for(size_t i=0;i<outUrbs && integrity.valid() && !stopCause.chosen;++i) submit(slots[i]);
            publishPlayback();return true;
        }
        return false;
    }
    bool sealSegment(bool eof) {
        return segments.seal(stats[SubmittedFrames],stats[CompletedFrames],prefix->queue.consumedTotal.load(),
            stats[OutBytesSubmitted],stats[KernelOutActualBytes],prefix->queue.segmentConsumedHash,segments.submittedHash,frameBytes(),totalProgress,eof);
    }
    bool seekStep(uint64_t now,const Command& wanted) {
        if(wanted.seekSerial>activeSeekSerial) {
            const auto coalesced=wanted.seeks>seenSeeks?wanted.seeks-seenSeeks-1:0;
            stats[SeekSuperseded]+=coalesced;seenSeeks=wanted.seeks;
            if(playback.seeking()) ++stats[SeekSuperseded];
            else {
                frozenPosition=sourcePosition();seekWasPaused=playback.phaseIs(PlaybackPhase::Paused);
                if(playback.admitsOut()) diagnosticAdmissionStopped=now;
                if(seekWasPaused) {playback.pausedUs+=now-playback.pausedStarted;playback.pausedStarted=0;}
                outContinuity.suspendSegment(wanted.seekAt,outstanding(false),outstanding(true),!fullSubmitted(),stats[CompletedFrames]);
                playback.phase=PlaybackPhase::SeekDraining;playback.drainStarted=now;seekSealed=false;
                stats[SeekDrainOutcome]=0;
                stats[SeekOldBoundary]=frozenPosition;stats[SeekStartedPaused]=seekWasPaused;
            }
            activeSeekSerial=wanted.seekSerial;activeSeekTarget=wanted.target;seekStarted=now;seekAuthorized=false;
            stats[SeekSourceReady]=0;stats[SeekFirstProven]=0;stats[SeekFirstFrame]=0;stats[SeekPendingOut]=outstanding(false);
            seekOutcome=SeekOutcome::Draining;playback.resumeReadiness=0;
            if(playback.phaseIs(PlaybackPhase::SourceRepositioning) || playback.phaseIs(PlaybackPhase::SeekPriming)) {
                prefix->requestQuiescence();playback.phase=PlaybackPhase::SourceRepositioning;
            }
            publishPlayback();
        }
        if(playback.phaseIs(PlaybackPhase::SeekDraining)) {
            for(size_t i=0;i<slots.size() && integrity.valid() && !requestedStop.load() && reapOne();++i) {}
            completedProgress.store(stats[CompletedFrames]);frozenPosition=segments.start+stats[CompletedFrames]-segments.completedBase;
            stats[SeekOldBoundary]=frozenPosition;stats[SeekPendingOut]=outstanding(false);publishPlayback();
            if(!integrity.valid() || requestedStop.load() || prefix->queue.faults.load()) return true;
            if(outstanding(false) || outstanding(true)) {
                if(playback.drainExpired(monotonicUs(),kDrainUs)) {stats[SeekDrainOutcome]=2;fail(ShutdownUnaccounted,Stop::Accounting);return true;}
                waitPlayback(1,true);return true;
            }
            if(!sealSegment(false) || stats[ScheduledFrames]!=stats[SubmittedFrames] || stats[OutCancelled]) {
                stats[SeekDrainOutcome]=2;fail(TerminalAccounting,Stop::Accounting);return true;
            }
            seekSealed=true;stats[SeekDrainOutcome]=1;stats[SeekPendingOut]=0;
            diagnosticPoolsDrained=monotonicUs();
            prefix->requestQuiescence();playback.phase=PlaybackPhase::SourceRepositioning;seekOutcome=SeekOutcome::Repositioning;publishPlayback();
        }
        if(monotonicUs()-seekStarted>=5000000) {fail(FlacSourceFault,Stop::SourceVerificationStall);return true;}
        const auto age=monotonicUs()-seekStarted;
        const auto waitMs=static_cast<int>(age<5000000?(5000000-age+999)/1000:0);
        if(playback.phaseIs(PlaybackPhase::SourceRepositioning)) {
            if(!seekAuthorized && prefix->seekPhase.load(std::memory_order_acquire)==2) {
                auto& q=prefix->queue;
                if(!seekSealed || outstanding(false) || outstanding(true) || q.prepared) {fail(TerminalAccounting,Stop::Accounting);return true;}
                if(!q.resetEpoch(activeSeekTarget,q.sourceEpoch.load()+1)) {fail(TerminalAccounting,Stop::Accounting);return true;}
                uint64_t invalid=q.invalidatedQueued.load();
                for(auto count:{prefix->decoder.unpublishedCanonical,prefix->decoder.unpublishedRaw,prefix->stagedDiscarded.load()}) {
                    if(!fv::canAdd(invalid,count)) {fail(TerminalAccounting,Stop::Accounting);return true;}invalid+=count;
                }
                if(invalid>fv::kCounterMax/frameBytes()) {fail(TerminalAccounting,Stop::Accounting);return true;}
                stats[SeekInvalidatedFrames]=invalid;stats[SeekInvalidatedBytes]=invalid*frameBytes();
                segments.begin(q.sourceEpoch.load(),activeSeekTarget,stats[SubmittedFrames],stats[CompletedFrames],stats[OutBytesSubmitted],stats[KernelOutActualBytes]);
                for(auto field:{SeekTailUrbs,SeekTailPackets,SeekTailFrames,SeekTailPacketCompletions,SeekTailFrameCompletions}) stats[field]=0;
                if(!prefix->authorizeSeek(activeSeekSerial,activeSeekTarget)) {fail(FlacSourceFault,Stop::Source);return true;}
                seekAuthorized=true;
            }
            if(seekAuthorized && prefix->seekPhase.load(std::memory_order_acquire)==4 && prefix->seekDoneSerial.load()==activeSeekSerial) {
                if(!prefix->seekFirstProven.load() || prefix->seekFirst.load()!=activeSeekTarget) {fail(TerminalAccounting,Stop::Accounting);return true;}
                segments.targetProven=true;stats[SeekFirstFrame]=prefix->seekFirst.load();stats[SeekFirstProven]=1;
                playback.phase=PlaybackPhase::SeekPriming;seekOutcome=SeekOutcome::Priming;publishPlayback();
            } else {waitPlayback(waitMs);return true;}
        }
        if(playback.phaseIs(PlaybackPhase::SeekPriming)) {
            const auto remaining=totalProgress-activeSeekTarget,target=std::min<uint64_t>(fv::kPrefixPrebuffer,remaining);
            if(prefix->queue.depth()<target || (target<fv::kPrefixPrebuffer && !prefix->queue.eofReached.load(std::memory_order_acquire))) {waitPlayback(waitMs);return true;}
            // Linearize confirmation with acceptance of a newer target and the latest Play/Pause intent.
            std::unique_lock<std::mutex> confirmation(controlMutex);
            if(command.seekSerial!=activeSeekSerial || requestedStop.load() || commandsClosed) return true;
            const auto current=command;const auto boundary=static_cast<uint64_t>(stats[SeekOldBoundary]);
            auto& distance=stats[activeSeekTarget>=boundary?SeekSkipped:SeekRevisited];const auto delta=activeSeekTarget>=boundary?activeSeekTarget-boundary:boundary-activeSeekTarget;
            if(!fv::canAdd(distance,delta)) {fail(TerminalAccounting,Stop::Accounting);return true;}distance+=delta;
            ++stats[SeekConfirmed];stats[ConfirmedSeekTarget]=activeSeekTarget;stats[SeekSourceReady]=1;
            diagnosticSeekConfirmed=monotonicUs();
            prefix->beginStreaming();
            confirmedSeekSerial=activeSeekSerial;confirmedEpoch=segments.epoch;seekOutcome=SeekOutcome::Confirmed;seekSealed=false;
            if(current.play) {playback.phase=PlaybackPhase::Resuming;playback.resumeStarted=monotonicUs();playback.countResume=playback.resumeOutcome==ControlOutcome::Requested;resumePrepared=false;}
            else {
                playback.phase=PlaybackPhase::Paused;playback.pausedStarted=monotonicUs();playback.pausedFrame=stats[CompletedFrames];
                if(playback.pauseOutcome==ControlOutcome::Requested) {++playback.pauses;playback.pauseOutcome=ControlOutcome::Confirmed;playback.drainOutcome=1;}
            }
            confirmation.unlock();publishPlayback();return true;
        }
        return true;
    }
    bool fullSubmitted() const { return fullTrack && prefix->queue.sourceHead()==prefix->queue.expectedTotal.load(); }
    bool seekNaturalComplete() {
        const auto& q=prefix->queue;
        if(!q.eofReached.load(std::memory_order_acquire) || q.sourceHead()!=totalProgress || outstanding(false) || q.depth() || q.prepared) return false;
        if(playback.resumeReadiness==1 && monotonicUs()-feedbackClock.started>=kBootstrapUs) {feedbackTimeout(FeedbackTimeoutKind::Bootstrap,monotonicUs());return false;}
        if(!integrity.valid() || !segments.targetProven || !sealSegment(true) || stats[OutCancelled] || stats[ScheduledFrames]!=stats[SubmittedFrames] ||
            q.consumedHash!=submittedPrefixHash || stats[SeekTailUrbs]!=1 || stats[SeekTailPackets]<1 || stats[SeekTailPackets]>8 ||
            stats[SeekTailFrameCompletions]!=stats[SeekTailFrames] || stats[SeekTailPacketCompletions]!=stats[SeekTailPackets]) {
            fail(TerminalAccounting,Stop::Accounting);return false;
        }
        stats[SeekOriginalNext]=prefix->decoder.originalNext;stats[FullQueueNatural]=0;stats[FullDiscardNatural]=0;
        commandsClosed=true;stopCause.choose(Stop::NaturalEof);return true;
    }
    bool naturalComplete() {
        if (!fullTrack || stopCause.chosen || requestedStop.load() || prefix->queue.faults.load()) return false;
        std::unique_lock<std::mutex> eofBoundary(controlMutex);
        if(command.seekSerial>activeSeekSerial || requestedStop.load()) return false;
        if(stats[SeekConfirmed]) return seekNaturalComplete();
        const auto& q=prefix->queue;
        if (!naturalOutComplete(q.eofReached.load(std::memory_order_acquire),q.expectedTotal.load(),q.tail.load(),q.head.load(),
            static_cast<uint64_t>(stats[SubmittedFrames]),static_cast<uint64_t>(stats[CompletedFrames]),outstanding(false),q.prepared,integrity.valid())) return false;
        const auto total=static_cast<jlong>(q.expectedTotal.load());
        // EOF's acquire publication makes the producer's final decoder/hash evidence immutable here.
        if (stats[ScheduledFrames]!=total || prefix->decoder.pcm.frames!=q.expectedTotal.load() ||
            prefix->decoder.pcm.bytes!=q.expectedTotal.load()*frameBytes() || prefix->decoder.facts[fv::Md5Verification]!=1 ||
            prefix->decoder.pcm.hash!=q.enqueuedHash || q.enqueuedHash!=q.consumedHash || q.consumedHash!=submittedPrefixHash ||
            stats[OutBytesSubmitted]!=total*frameBytes() || stats[KernelOutActualBytes]!=stats[OutBytesSubmitted] ||
            stats[FullLastSubmitted]!=total-1 || stats[FullLastCompleted]!=total-1 || stats[TailUrbs]!=1 ||
            stats[TailPackets]<1 || stats[TailPackets]>static_cast<jlong>(kOutPackets) || stats[TailFrames]<1 ||
            stats[TailRemaining]!=stats[TailFrames] || stats[TailPacketCompletions]!=stats[TailPackets] || stats[TailFrameCompletions]!=stats[TailFrames] ||
            stats[OutSubmitted]!=stats[OutCompleted] || stats[OutPacketsSubmitted]!=stats[OutPacketsCompleted] ||
            stats[OutPacketsSubmitted]!=(stats[OutSubmitted]-1)*static_cast<jlong>(kOutPackets)+stats[TailPackets] || stats[OutCancelled] ||
            stats[EofPadding] || q.depth()) { fail(TerminalAccounting,Stop::Accounting); return false; }
        stats[FullQueueNatural]=static_cast<jlong>(q.depth()); stats[FullDiscardNatural]=0;
        commandsClosed=true;stopCause.choose(Stop::NaturalEof); return true;
    }
    void beginStop() {
        if (stopping) return;
        stopCause.choose(Stop::Duration);
        stats[ActiveAtStop] = static_cast<jlong>(monotonicUs() - started);
        stopping = true; stats[OutAtStop] = static_cast<jlong>(outstanding(false)); stats[FeedbackAtStop] = static_cast<jlong>(outstanding(true));
        move(State::StopRequested); move(State::Draining);
        if (prefix) prefix->finish(); // Wake/join producer before restoration; no detached decode worker.
    }
    bool submit(Slot& slot) {
        if(pauseAdmission()) return false;
        if (stopping || stopCause.chosen || requestedStop.load() != 0 || !integrity.valid()) return false;
        if (prefix && prefix->queue.faults.load()) { failSource(); return false; }
        if (durationReached(started, monotonicUs(), durationUs)) { stopCause.choose(Stop::Duration); return false; }
        if (fullTrack && !slot.feedback && fullSubmitted()) return false;
        if (slot.identity.outstanding) { fail(SchedulerInvalid, Stop::Accounting); return false; }
        slot.packets=slot.feedback ? 1 : static_cast<unsigned>(kOutPackets); slot.terminal=false;
        slot.firstSourceFrame=fullTrack?prefix->queue.sourceHead():static_cast<uint64_t>(stats[SubmittedFrames]);
        slot.sourceEpoch=fullTrack?prefix->queue.sourceEpoch.load():0;
        auto* urb = slot.urb.get();
        std::memset(urb, 0, sizeof(usbdevfs_urb) + slot.packets * sizeof(usbdevfs_iso_packet_desc));
        urb->type = USBDEVFS_URB_TYPE_ISO;
        urb->endpoint = static_cast<unsigned char>(slot.feedback ? feedbackEndpoint : dataEndpoint);
        urb->flags = USBDEVFS_URB_ISO_ASAP; // Kernel derives interval from the active endpoint descriptor.
        urb->number_of_packets = static_cast<int>(slot.packets); urb->buffer = slot.buffer.data();
        urb->usercontext = &slot.identity;
        const auto schedulerBefore = scheduler;
        const auto scheduledBefore = stats[ScheduledFrames], bootstrapBefore = stats[BootstrapPackets], feedbackBefore = stats[FeedbackScheduledPackets];
        uint32_t length = 0, frameCount = 0;
        if (slot.feedback) {
            std::fill(slot.buffer.begin(), slot.buffer.end(), 0);
            urb->iso_frame_desc[0].length = 4; length = 4;
        } else {
            for (unsigned i = 0; i < slot.packets; ++i) {
                uint32_t frames = 0;
                if (!scheduler.next(static_cast<unsigned>(intervalUs(dataInterval) / 125), acceptedFeedback, frames)) {
                    fail(SchedulerInvalid, Stop::Accounting); return false;
                }
                if(fullTrack && !fv::canAdd(stats[ScheduledFrames],frames)) {fail(TerminalAccounting,Stop::Accounting);return false;}
                stats[ScheduledFrames] += frames;
                const uint64_t bytes = static_cast<uint64_t>(frames) * frameBytes();
                if (!packetFits(frames, payload, frameBytes()) || length + bytes > slot.buffer.size()) { fail(CapacityExceeded, Stop::Capacity); return false; }
                if (!prefix) source.fill(slot.buffer.data() + length, static_cast<uint64_t>(stats[SubmittedFrames]) + frameCount, frames);
                // Validate the entire exact packed region once, immediately before submission.
                slot.frames[i] = frames; frameCount += frames;
                urb->iso_frame_desc[i].length = static_cast<unsigned>(bytes); length += static_cast<unsigned>(bytes);
                ++stats[acceptedFeedback ? FeedbackScheduledPackets : BootstrapPackets];
            }
        }
        if (fullTrack && !slot.feedback) {
            const uint64_t total=prefix->queue.expectedTotal.load(), position=prefix->queue.sourceHead();
            if (position>total) { fail(TerminalAccounting,Stop::Accounting); return false; }
            const uint64_t remaining=total-position;
            if (remaining<=frameCount && !prefix->queue.eofReached.load(std::memory_order_acquire)) {
                // Total is a bound, not EOF proof. Keep the final candidate until complete decode/MD5 is authoritative.
                const auto supply=prefix->queue.requireFrames(remaining);
                scheduler=schedulerBefore; stats[ScheduledFrames]=scheduledBefore;
                stats[BootstrapPackets]=bootstrapBefore; stats[FeedbackScheduledPackets]=feedbackBefore;
                if (supply!=fv::ReserveResult::Available) { failSource(); if (integrity.valid()) fail(TerminalAccounting,Stop::Accounting); }
                else waitingForEof=true;
                return false;
            }
            if (remaining<=frameCount) {
                const auto shape=terminalShape(slot.frames,remaining,true);
                if (!shape.valid) { fail(TerminalAccounting,Stop::Accounting); return false; }
                slot.terminal=true; slot.frames=shape.frames; slot.packets=shape.packets;
                stats[ScheduledFrames]=scheduledBefore+static_cast<jlong>(shape.total);
                stats[BootstrapPackets]=bootstrapBefore+(acceptedFeedback ? 0 : slot.packets);
                stats[FeedbackScheduledPackets]=feedbackBefore+(acceptedFeedback ? slot.packets : 0);
                frameCount=static_cast<uint32_t>(shape.total); length=frameCount*frameBytes();
                urb->number_of_packets=static_cast<int>(slot.packets);
                for (unsigned i=0;i<slot.packets;++i) urb->iso_frame_desc[i].length=slot.frames[i]*frameBytes();
                stats[TailRemaining]=static_cast<jlong>(remaining);
            }
        }
        // Exact packed lengths only: stale/unused buffer capacity is never submitted.
        urb->buffer_length = static_cast<int>(length);
        if (prefix && !slot.feedback) {
            if (prefix->queue.faults.load()) { failSource(); return false; }
            if (durationReached(started, monotonicUs(), durationUs)) {
                // Deadline crossed while preparing headers: this candidate was never reserved or submitted.
                scheduler = schedulerBefore; stats[ScheduledFrames] = scheduledBefore;
                stats[BootstrapPackets] = bootstrapBefore; stats[FeedbackScheduledPackets] = feedbackBefore;
                stopCause.choose(Stop::Duration); return false;
            }
            const auto reservation = prefix->queue.reserve(slot.buffer.data(), frameCount);
            if (reservation != fv::ReserveResult::Available) {
                switch (reservation) {
                    case fv::ReserveResult::InsufficientFrames: fail(QueueUnderrun | FlacSourceFault, Stop::Underrun); break;
                    case fv::ReserveResult::SourceEof: fail(FlacSourceFault, Stop::SourceEof); break;
                    case fv::ReserveResult::Cancelled:
                    case fv::ReserveResult::StopRequested: fail(AuthorityChanged, Stop::Requested); break;
                    case fv::ReserveResult::InvalidState: fail(SchedulerInvalid | FlacSourceFault, Stop::Accounting); break;
                    default: fail(FlacSourceFault, Stop::Source); break;
                }
                return false;
            }
            if (!prefix->queue.validate(slot.buffer.data(), length)) {
                // A producer fault during copying is not evidence that the copied bytes were mutated.
                const auto faults = prefix->queue.faults.load();
                if (!faults || (faults & fv::PrefixHash)) fail(SourceHashMismatch, Stop::Source);
                else failSource();
                return false;
            }
        }
        if (prefix && prefix->queue.faults.load()) { failSource(); return false; }
        if (!slot.feedback && !prefix && !source.inspect(slot.buffer.data(), length, static_cast<uint64_t>(stats[SubmittedFrames]), frameCount)) {
            fail(source.mode == PcmMode::Zero ? NonzeroPcm : source.reasons, Stop::Accounting); return false;
        }
        if (requestedStop.load() != 0) { fail(AuthorityChanged, Stop::Requested); return false; }
        if(pauseAdmission()) {
            scheduler=schedulerBefore;stats[ScheduledFrames]=scheduledBefore;stats[BootstrapPackets]=bootstrapBefore;stats[FeedbackScheduledPackets]=feedbackBefore;
            if(prefix && !slot.feedback) prefix->queue.abandonReservation();
            return false;
        }
        if(fullTrack && !slot.feedback && (!fv::canAdd(stats[SubmittedFrames],frameCount) || !fv::canAdd(stats[OutBytesSubmitted],length))) {
            fail(TerminalAccounting,Stop::Accounting);return false;
        }
        slot.submittedAt = monotonicUs();
        const int returned = ::ioctl(fd, USBDEVFS_SUBMITURB, urb);
        const int saved = returned < 0 ? errno : 0;
        if (returned != 0) {
            stats[SubmitErrno] = saved; stats[NotSubmittedFrames] += frameCount;
            fail(saved == ENODEV ? DeviceDetached : SubmitError, saved == ENODEV ? Stop::Detached : Stop::Submit); return false;
        }
        if (!slot.identity.submit(attachment, stream, ++serial)) { fail(SchedulerInvalid, Stop::Accounting); return false; }
        ++stats[slot.feedback ? FeedbackSubmitted : OutSubmitted];
        if(prefix && !slot.feedback && frameCount && !diagnosticOutResumed) diagnosticOutResumed=slot.submittedAt;
        if (!slot.feedback) outContinuity.submitted(monotonicUs(),slot.packets,slot.outReapedAt);
        if (!slot.feedback) {
            if (prefix) {
                submittedPrefixHash = fv::hashBytes(submittedPrefixHash, slot.buffer.data(), length);
                if(fullTrack) segments.submittedHash=segments.epoch?fv::hashBytes(segments.submittedHash,slot.buffer.data(),length):submittedPrefixHash;
                if (!prefix->queue.commit()) fail(FlacSourceFault, Stop::Source);
                if (prefix->queue.consumedHash != submittedPrefixHash) fail(SourceHashMismatch, Stop::Source);
            } else source.submitted(slot.buffer.data(), length);
            if (fullTrack) {
                stats[FullLastSubmitted]=static_cast<jlong>(slot.firstSourceFrame+frameCount-1);
                if (slot.terminal) { stats[TailFrames]+=frameCount; stats[TailPackets]+=slot.packets; ++stats[TailUrbs]; }
                if(slot.terminal && stats[SeekConfirmed]) {stats[SeekTailFrames]+=frameCount;stats[SeekTailPackets]+=slot.packets;++stats[SeekTailUrbs];}
            }
            stats[SubmittedFrames] += frameCount; stats[OutBytesSubmitted] += length; stats[OutPacketsSubmitted] += slot.packets;
            if(fullTrack) { playback.submitted();publishPlayback(); }
        }
        return true;
    }
    void packetExample(const Slot& slot, unsigned i, int urbStatus) {
        const auto& packet = slot.urb->iso_frame_desc[i];
        const bool error = urbStatus != 0 || packet.status != 0 || (!slot.feedback && packet.actual_length != packet.length);
        if (!error && packetExampleCount >= 8) return;
        const size_t index = packetExampleCount < kPacketExamples ? packetExampleCount++ : kPacketExamples - 1;
        const std::array<jlong, 7> row{slot.urb->endpoint, urbStatus, static_cast<int>(packet.status), packet.length,
            packet.actual_length, static_cast<jlong>(stream), static_cast<jlong>(slot.identity.submission)};
        std::copy(row.begin(), row.end(), packetExamples.begin() + index * row.size());
    }
    void observeFeedback(const Slot& slot,int urbStatus,uint64_t observed) {
        if(feedbackDiagnostics.frozen()) return;
        const auto& packet=slot.urb->iso_frame_desc[0];
        const bool quiet=fullTrack && playback.quiet();
        const auto decoded=decodeFeedback(slot.buffer.data(),packet.actual_length,static_cast<int>(packet.status),sampleRateHz);
        auto validity=decoded.validity;
        auto rejection=FeedbackRejection::None;
        if(urbStatus || slot.urb->error_count) {validity=FeedbackValidity::CompletionError;rejection=FeedbackRejection::UrbStatus;}
        else if(packet.status) rejection=FeedbackRejection::PacketStatus;
        else if(validity==FeedbackValidity::Length) rejection=FeedbackRejection::Length;
        else if(validity==FeedbackValidity::Zero) rejection=FeedbackRejection::Zero;
        else if(validity==FeedbackValidity::Reserved) rejection=FeedbackRejection::Reserved;
        else if(validity==FeedbackValidity::Implausible) rejection=FeedbackRejection::Implausible;
        else if(quiet) rejection=FeedbackRejection::Quiet;
        else if(stopping || stopCause.chosen) rejection=FeedbackRejection::Terminal;
        int64_t raw=-1;
        if(packet.actual_length==4) {uint32_t value=0;for(unsigned i=0;i<4;++i) value|=static_cast<uint32_t>(slot.buffer[i])<<(8*i);raw=value;}
        feedbackDiagnostics.observe({0,diagnosticElapsed(started,observed),raw,static_cast<int>(validity),static_cast<int>(rejection),
            packet.length,packet.actual_length,urbStatus,static_cast<int>(packet.status),rejection==FeedbackRejection::None,
            diagnosticElapsed(slot.submittedAt,observed),fullTrack?static_cast<int>(playback.phase):-1,
            fullTrack?static_cast<int64_t>(segments.epoch):-1,fullTrack?static_cast<int64_t>(confirmedSeekSerial):-1,slot.urb->error_count});
    }
    void freezeFeedback(FeedbackFatalReason reason,uint64_t observed,bool sample,int timeoutKind=0) {
        // Called BEFORE fail() changes terminal/lifecycle state. An unrelated earlier failure is not relabelled.
        if(feedbackDiagnostics.frozen() || stopping || stopCause.chosen) return;
        FeedbackFatalContext context{};
        const auto* row=sample?feedbackDiagnostics.latest():nullptr;
        if(row && (*row)[DElapsed]>=0 && started<=UINT64_MAX-static_cast<uint64_t>((*row)[DElapsed]))
            observed=started+static_cast<uint64_t>((*row)[DElapsed]); // The causal packet's observation, not a later clock read.
        const bool streak=reason==FeedbackFatalReason::PersistentInvalid;
        context[FReason]=static_cast<int>(reason);context[FSequence]=row?(*row)[DSequence]:-1;
        context[FFirstRejectedSequence]=streak?feedbackDiagnostics.firstRejectedSequence:-1;
        context[FFirstRejectedElapsed]=streak?feedbackDiagnostics.firstRejectedElapsed:-1;
        context[FRaw]=row?(*row)[DRaw]:-1;context[FValidity]=row?(*row)[DValidity]:-1;context[FStreak]=invalidStreak;
        context[FNominalRate]=sampleRateHz;context[FPhase]=fullTrack?static_cast<int>(playback.phase):-1;
        context[FTransportState]=static_cast<int>(state);context[FEpoch]=fullTrack?static_cast<int64_t>(segments.epoch):-1;
        context[FSeekSerial]=fullTrack?static_cast<int64_t>(confirmedSeekSerial):-1;
        context[FSinceSeek]=diagnosticElapsed(diagnosticSeekConfirmed,observed);context[FSinceOut]=diagnosticElapsed(diagnosticOutResumed,observed);
        context[FObserved]=diagnosticElapsed(started,observed);context[FRequestAge]=row?(*row)[DRequestAge]:-1;
        context[FOut]=outstanding(false);context[FFeedback]=outstanding(true);context[FBootstrap]=feedbackClock.lastObserved==0;
        // Worker phase/refill demand, not proof that a pending control command permits the next submission.
        context[FRefill]=(!fullTrack || playback.admitsOut()) && (!fullTrack || !fullSubmitted());
        context[FNominalCoverage]=outContinuity.nominalCoverageUs;context[FEstimatedCoverage]=outContinuity.estimatedCoverage(observed);
        context[FBootstrapElapsed]=diagnosticElapsed(feedbackClock.started,observed);
        context[FAdmissionStopped]=diagnosticElapsed(started,diagnosticAdmissionStopped);context[FPoolsDrained]=diagnosticElapsed(started,diagnosticPoolsDrained);
        context[FSeekConfirmed]=diagnosticElapsed(started,diagnosticSeekConfirmed);context[FFreshPrime]=diagnosticElapsed(started,diagnosticFreshPrime);
        context[FOutResumed]=diagnosticElapsed(started,diagnosticOutResumed);context[FAcceptedRaw]=acceptedFeedback;context[FTimeoutKind]=timeoutKind;
        feedbackDiagnostics.freeze(context);
    }
    void feedbackTimeout(FeedbackTimeoutKind kind,uint64_t observed,uint64_t offendingSubmitted=0) {
        if (stopping || kind==FeedbackTimeoutKind::None) return;
        ++stats[FeedbackTimeouts];
        ++stats[kind==FeedbackTimeoutKind::Bootstrap ? BootstrapTimeouts :
            kind==FeedbackTimeoutKind::LastValidWatchdog ? WatchdogTimeouts : ReapedTimeouts];
        if (!stats[TimeoutSubtype]) {
            stats[TimeoutSubtype]=static_cast<jlong>(kind); stats[TimeoutObservedUs]=static_cast<jlong>(observed);
            stats[TimeoutLastObservedUs]=static_cast<jlong>(feedbackClock.lastObserved);
            stats[TimeoutLastSubmittedUs]=static_cast<jlong>(feedbackClock.lastSubmitted);
            stats[TimeoutOffendingSubmittedUs]=static_cast<jlong>(offendingSubmitted);
            stats[TimeoutAgeUs]=static_cast<jlong>(observed-(feedbackClock.lastObserved ? feedbackClock.lastObserved : feedbackClock.started));
            stats[TimeoutOutstanding]=static_cast<jlong>(outstanding(true));
            size_t reaped=0;
            for (const auto& slot : slots) if (slot.feedback && !slot.identity.outstanding && slot.identity.submission) ++reaped;
            stats[TimeoutReapedNotRearmed]=static_cast<jlong>(reaped); stats[TimeoutAcceptedRaw]=acceptedFeedback;
            stats[TimeoutReapAttempted]=feedbackReapPass.attempted;
            stats[TimeoutReapCount]=static_cast<jlong>(feedbackReapPass.reaped);
            stats[TimeoutReapFeedbackCount]=static_cast<jlong>(feedbackReapPass.feedbackCompletions);
            stats[TimeoutReapRefreshed]=feedbackReapPass.refreshed;
            stats[TimeoutAgeBeforeReap]=static_cast<jlong>(feedbackReapPass.ageBefore);
            stats[TimeoutAgeAfterReap]=static_cast<jlong>(feedbackReapPass.ageAfter);
            stats[TimeoutWatchdogEvaluated]=feedbackReapPass.watchdogEvaluated;
            stats[TimeoutWatchdogFired]=feedbackReapPass.timeout!=FeedbackTimeoutKind::None;
        }
        freezeFeedback(kind==FeedbackTimeoutKind::Bootstrap?FeedbackFatalReason::BootstrapTimeout:
            kind==FeedbackTimeoutKind::LastValidWatchdog?FeedbackFatalReason::WatchdogTimeout:FeedbackFatalReason::ReapedTimeout,observed,false,static_cast<int>(kind));
        fail(FeedbackTimeout,Stop::Feedback);
    }
    void feedbackPacket(Slot& slot, int urbStatus) {
        if(fullTrack && playback.quiet()) {
            observeFeedback(slot,urbStatus,monotonicUs());
            const auto& packet=slot.urb->iso_frame_desc[0];++stats[FeedbackPacketsCompleted];
            if(urbStatus || packet.status || packet.actual_length!=4) { ++stats[FeedbackUrbErrors];fail(UrbError,Stop::Feedback); }
            else {
                const auto decoded=decodeFeedback(slot.buffer.data(),packet.actual_length,0,sampleRateHz);
                if(decoded.validity==FeedbackValidity::Reserved) { ++stats[FeedbackInvalid];fail(MalformedFeedback,Stop::Feedback); }
                else if(decoded.validity!=FeedbackValidity::Valid) ++stats[FeedbackInvalid];
            }
            return;
        }
        const auto& packet = slot.urb->iso_frame_desc[0];
        const auto decoded = decodeFeedback(slot.buffer.data(), packet.actual_length, static_cast<int>(packet.status), scheduler.nominalRate);
        const size_t index = feedbackExampleCount < kFeedbackExamples ? feedbackExampleCount++ : kFeedbackExamples - 1;
        uint32_t raw = 0;
        for (unsigned i = 0; i < std::min<unsigned>(4, packet.actual_length); ++i) raw |= static_cast<uint32_t>(slot.buffer[i]) << (8 * i);
        const uint64_t observed = monotonicUs(), age = FeedbackObservationClock::requestAge(slot.submittedAt,observed);
        observeFeedback(slot,urbStatus,observed);
        stats[MaxRequestAgeUs]=std::max(stats[MaxRequestAgeUs],static_cast<jlong>(age));
        if (age>static_cast<uint64_t>(stats[FeedbackHoldover])) ++stats[RequestsOverHoldover];
        const std::array<jlong, 6> row{raw, packet.actual_length, static_cast<int>(packet.status),
            static_cast<jlong>(observed - started), static_cast<int>(decoded.validity), static_cast<jlong>(age)};
        std::copy(row.begin(), row.end(), feedbackExamples.begin() + index * row.size());
        ++stats[FeedbackPacketsCompleted];
        if (urbStatus != 0 || packet.status != 0 || packet.actual_length > 4 || decoded.validity == FeedbackValidity::Length || decoded.validity == FeedbackValidity::Reserved) {
            ++stats[FeedbackInvalid]; fail(MalformedFeedback, Stop::Feedback); return;
        }
        if (decoded.validity != FeedbackValidity::Valid) {
            ++stats[FeedbackInvalid]; stats[InvalidStreak] = ++invalidStreak;
            if(!stopping && !stopCause.chosen) feedbackDiagnostics.rejected(invalidStreak);
            if (invalidStreak >= kInvalidFeedbackLimit) fail(InvalidFeedback, Stop::Feedback);
            return;
        }
        ++stats[FeedbackValid]; ++stats[FeedbackSamples]; stats[SumFeedback] += decoded.raw;
        if (!stats[MinFeedback] || decoded.raw < static_cast<uint64_t>(stats[MinFeedback])) stats[MinFeedback] = decoded.raw;
        if (decoded.raw > static_cast<uint64_t>(stats[MaxFeedback])) stats[MaxFeedback] = decoded.raw;
        // A queued request can wait many intervals before its one transaction. Its
        // submission age is not sample age. Accept ready valid feedback before the
        // post-reap watchdog decision; do not refresh clocks during drain/failure.
        if (stopping || stopCause.chosen) return;
        feedbackDiagnostics.recovered();
        invalidStreak = 0; stats[InvalidStreak] = 0; acceptedFeedback = decoded.raw;
        feedbackClock.accept(slot.submittedAt,observed);
        if(fullTrack && playback.resumeReadiness) playback.resumeReadiness=2;
        stats[LastAcceptedSubmittedUs]=static_cast<jlong>(slot.submittedAt);
        stats[LastAcceptedObservedUs]=static_cast<jlong>(observed); stats[LastAcceptedRequestAgeUs]=static_cast<jlong>(age);
        if (!stopping && state == State::Bootstrap) move(State::FeedbackLocked);
    }
    bool reapOne() {
        void* completed = nullptr;
        const int returned = ::ioctl(fd, USBDEVFS_REAPURBNDELAY, &completed);
        const int saved = returned < 0 ? errno : 0;
        if (returned < 0) {
            if (saved == EAGAIN || saved == EINTR) return false;
            stats[ReapErrno] = saved; fail(saved == ENODEV ? DeviceDetached : ReapError, saved == ENODEV ? Stop::Detached : Stop::Reap); return false;
        }
        Slot* found = nullptr;
        for (auto& slot : slots) if (slot.urb.get() == completed) { found = &slot; break; }
        if (!found) { ++stats[UnknownCompletions]; fail(UnknownCompletion, Stop::Accounting); return true; }
        auto& slot = *found;
        if (!slot.identity.outstanding) { ++stats[DuplicateCompletions]; fail(DuplicateCompletion, Stop::Accounting); return true; }
        if (!slot.identity.complete(attachment, stream)) { fail(GenerationMismatch, Stop::Accounting); return true; }
        if(fullTrack && !slot.feedback && slot.sourceEpoch!=segments.epoch) {fail(GenerationMismatch,Stop::Accounting);return true;}
        if (!slot.feedback && !stopping && !stopCause.chosen && !requestedStop.load()) {
            slot.outReapedAt=monotonicUs();
            outContinuity.completed(slot.outReapedAt,slot.packets);
        }
        if (stopping) ++stats[CompletionsAfterStop];
        ++stats[slot.feedback ? FeedbackCompleted : OutCompleted];
        const int status = slot.urb->status;
        const bool cancelled = slot.identity.discardRequested && (status == -ENOENT || status == -ECONNRESET);
        if (cancelled) {
            ++stats[CancellationCompletions]; ++stats[slot.feedback ? FeedbackCancelled : OutCancelled];
            if (!slot.feedback) { for (unsigned i = 0; i < slot.packets; ++i) stats[CancelledFrames] += slot.frames[i]; }
        } else if (status != 0 || slot.urb->error_count != 0) {
            if (!stats[FirstUrbStatus]) stats[FirstUrbStatus] = status;
            if (!stats[FirstUrbErrorCount]) stats[FirstUrbErrorCount] = slot.urb->error_count;
            ++stats[slot.feedback ? FeedbackUrbErrors : OutUrbErrors];
            if(slot.feedback) {const auto observed=monotonicUs();observeFeedback(slot,status,observed);freezeFeedback(FeedbackFatalReason::UrbCompletion,observed,true);}
            fail(UrbError, Stop::Worker);
        }
        uint64_t sourceOffset=0;
        for (unsigned i = 0; i < slot.packets; ++i) {
            const auto& packet = slot.urb->iso_frame_desc[i];
            packetExample(slot, i, status);
            if (slot.feedback) continue;
            if(fullTrack && (!fv::canAdd(stats[OutPacketsCompleted],1) || !fv::canAdd(stats[KernelOutActualBytes],packet.actual_length))) {
                fail(TerminalAccounting,Stop::Accounting);continue;
            }
            ++stats[OutPacketsCompleted]; stats[KernelOutActualBytes] += packet.actual_length;
            integrity.compromise(outPacketProblem(status, static_cast<int>(packet.status), packet.length, packet.actual_length, cancelled));
            if (cancelled) continue; // Cancelled frames are explicitly distinct from successful completion frames.
            if (packet.status != 0) {
                ++stats[OutPacketErrors]; fail(PacketError, Stop::Worker);
                if (!stats[FirstPacketStatus]) { stats[FirstPacketStatus] = static_cast<int>(packet.status);
                    stats[FirstPacketLength] = packet.length; stats[FirstPacketActual] = packet.actual_length; }
            } else if (packet.actual_length != packet.length) {
                ++stats[OutPacketErrors]; fail(ShortPacket, Stop::Worker); stats[FirstPacketLength] = packet.length; stats[FirstPacketActual] = packet.actual_length;
            } else if (status == 0) {
                if(fullTrack && !fv::canAdd(stats[CompletedFrames],slot.frames[i])) {fail(TerminalAccounting,Stop::Accounting);continue;}
                stats[CompletedFrames] += slot.frames[i];
                if (fullTrack) {
                    stats[FullLastCompleted]=std::max(stats[FullLastCompleted],static_cast<jlong>(slot.firstSourceFrame+sourceOffset+slot.frames[i]-1));
                    if (slot.terminal) { ++stats[TailPacketCompletions]; stats[TailFrameCompletions]+=slot.frames[i]; }
                }
                if(fullTrack && slot.terminal && stats[SeekConfirmed]) {++stats[SeekTailPacketCompletions];stats[SeekTailFrameCompletions]+=slot.frames[i];}
            }
            sourceOffset+=slot.frames[i];
        }
        if (!slot.feedback) lastOutCompletion = monotonicUs();
        if (slot.feedback && !cancelled) feedbackPacket(slot, status);
        return true;
    }
    void cancel(Slot& slot) {
        if (!slot.identity.outstanding || slot.identity.discardRequested) return;
        // Only known pointers in this generation. DISCARD kills synchronously but DOES NOT reap.
        const int returned = ::ioctl(fd, USBDEVFS_DISCARDURB, slot.urb.get());
        const int saved = returned < 0 ? errno : 0;
        if (returned == 0) { slot.identity.discardRequested = true; ++stats[Discarded]; }
        else if (saved != EINVAL) { stats[DiscardErrno] = saved; integrity.compromise(saved == ENODEV ? DeviceDetached : CancelError); }
        // EINVAL is a possible completion race; identity remains outstanding until actual reap.
    }
    void pollCompletion() {
        pollfd descriptor{fd, POLLOUT, 0};
        const int returned = ::poll(&descriptor, 1, 1);
        const int saved = returned < 0 ? errno : 0;
        if (returned < 0 && saved != EINTR) fail(ReapError, Stop::Reap);
        if (returned > 0 && (descriptor.revents & (POLLERR | POLLHUP | POLLNVAL))) fail(DeviceDetached, Stop::Detached);
    }
    void captureOutContinuity() {
        const auto& c=outContinuity;
        stats[ConfiguredOutUrbs]=static_cast<jlong>(c.configuredUrbs); stats[OutPacketsPerUrb]=static_cast<jlong>(c.packetsPerUrb);
        stats[OutUrbDurationUs]=static_cast<jlong>(c.urbDurationUs); stats[OutNominalCoverageUs]=static_cast<jlong>(c.nominalCoverageUs);
        stats[MaxWorkerLoopGapUs]=static_cast<jlong>(c.maxWorkerLoopGapUs); stats[MaxGapObservedAtUs]=static_cast<jlong>(c.maxGapObservedAtUs);
        stats[OutAtMaxGap]=static_cast<jlong>(c.outAtMaxGap); stats[FeedbackAtMaxGap]=static_cast<jlong>(c.feedbackAtMaxGap);
        stats[MaxGapOutRefilled]=c.maxGapRefilled; stats[MaxGapNeedsOut]=c.maxGapNeedsOut;
        stats[WorkerGapsBeyondOutCoverage]=static_cast<jlong>(c.gapsBeyondCoverage);
        stats[OutContinuityObservations]=static_cast<jlong>(c.observations);
        stats[MinOutOutstanding]=c.observations ? static_cast<jlong>(c.minOutstanding) : -1;
        stats[OutZeroObservations]=static_cast<jlong>(c.zeroObservations); stats[OutOneObservations]=static_cast<jlong>(c.oneObservations);
        stats[OutTwoObservations]=static_cast<jlong>(c.twoObservations);
        stats[MinEstimatedOutCoverageUs]=c.observations ? static_cast<jlong>(c.minEstimatedCoverageUs) : -1;
        stats[WorstOutCoverageAtUs]=static_cast<jlong>(c.worstCoverageAtUs);
        stats[OutCoverageBelowOneUrb]=static_cast<jlong>(c.belowOneUrb); stats[OutCoverageBelowTwoUrbs]=static_cast<jlong>(c.belowTwoUrbs);
        stats[MaxOutSubmissionBatchGapUs]=static_cast<jlong>(c.maxBatchGapUs); stats[MaxOutSubmissionGapUs]=static_cast<jlong>(c.maxSubmissionGapUs);
        stats[MaxOutCompletionGapUs]=static_cast<jlong>(c.maxCompletionGapUs);
        stats[MaxOutRefillOpportunityGapUs]=static_cast<jlong>(c.maxRefillOpportunityGapUs); stats[MaxOutRefillLatencyUs]=static_cast<jlong>(c.maxRefillLatencyUs);
        stats[OutContinuityRisks]=static_cast<jlong>(c.riskCount); stats[FirstOutRiskAtUs]=static_cast<jlong>(c.firstRiskAtUs);
        stats[FirstOutRiskMask]=static_cast<jlong>(c.firstRiskMask); stats[FirstOutRiskStage]=static_cast<jlong>(c.firstRiskStage);
        stats[FirstOutRiskOut]=static_cast<jlong>(c.firstRiskOut); stats[FirstOutRiskFeedback]=static_cast<jlong>(c.firstRiskFeedback);
        stats[FirstOutRiskGapUs]=static_cast<jlong>(c.firstRiskGapUs); stats[FirstOutRiskCoverageUs]=static_cast<jlong>(c.firstRiskCoverageUs);
        stats[FirstOutRiskCompletedFrames]=static_cast<jlong>(c.firstRiskCompletedFrames);
    }
    void shutdown() {
        captureOutContinuity(); // Freeze active telemetry before any stop/drain completions.
        beginStop();
        for (auto& slot : slots) if (slot.feedback || !integrity.valid() || requestedStop.load()) cancel(slot);
        const uint64_t drainStarted = monotonicUs();
        bool cancelledOut = false;
        while (outstanding(false) + outstanding(true) > 0 && monotonicUs() - drainStarted < kDrainUs + kCancelUs) {
            for (unsigned i = 0; i < 64 && reapOne(); ++i) {}
            if (!cancelledOut && (monotonicUs() - drainStarted >= kDrainUs || !integrity.valid())) {
                for (auto& slot : slots) cancel(slot);
                cancelledOut = true;
                if (outstanding(false)) integrity.compromise(ShutdownUnaccounted);
            }
            if (outstanding(false) + outstanding(true)) pollCompletion();
        }
        stats[OutstandingFinal] = static_cast<jlong>(outstanding(false) + outstanding(true));
        if (stats[OutstandingFinal]) { integrity.compromise(ShutdownUnaccounted); stats[ShutdownIncomplete] = 1; }
        if (prefix) {
            stats[SourceFrames] = static_cast<jlong>(prefix->queue.consumedTotal.load());
            stats[PrefixConsumed] = stats[SourceFrames]; stats[PrefixPrimed] = prefix->primed.load();
            stats[PrefixFaults] = static_cast<jlong>(prefix->queue.faults.load()); stats[PrefixStopped] = prefix->stopped.load();
            stats[PrefixJoined] = prefix->joined.load(); stats[SourcePcmHash] = hashBits(prefix->queue.consumedHash);
            stats[Underruns] = static_cast<jlong>(prefix->queue.underruns.load());
            if (prefix->queue.underruns.load()) integrity.compromise(QueueUnderrun);
            stats[SubmittedPcmHash] = hashBits(submittedPrefixHash);
            stats[NotSubmittedFrames] = std::max(stats[NotSubmittedFrames], static_cast<jlong>(prefix->queue.prepared));
            source.exampleCount = prefix->queue.exampleCount;
            // FLAC examples retain width in the queue; snapshot reads them directly.
            if (fullTrack) {
                stats[FullEofReached]=prefix->queue.eofReached.load(); stats[FullNaturalProducer]=stats[FullEofReached] && prefix->stopped.load();
                stats[FullMd5Pass]=prefix->decoder.facts[fv::Md5Verification]==1;
                stats[FullDecodedHash]=hashBits(prefix->decoder.pcm.hash); stats[FullEnqueuedHash]=hashBits(prefix->queue.enqueuedHash);
                stats[SeekOriginalNext]=prefix->decoder.originalNext;stats[SeekAttempts]=prefix->decoder.seekAttempts;
            }
            if (stats[PrefixFaults]) integrity.compromise(FlacSourceFault);
            if (prefix->queue.consumedHash != submittedPrefixHash) integrity.compromise(SourceHashMismatch);
        } else stats[SourceFrames] = static_cast<jlong>(source.generated); stats[PcmByteOr] = source.zero.byteOr;
        if (source.mode == PcmMode::Reference) {
            stats[SourcePcmHash] = hashBits(source.sourceHash); stats[SubmittedPcmHash] = hashBits(source.submittedHash);
            stats[SilenceFrames] = static_cast<jlong>(source.silence);
            stats[LeftToneFrames] = static_cast<jlong>(source.leftFrames); stats[RightToneFrames] = static_cast<jlong>(source.rightFrames);
            stats[LeftActiveSamples] = static_cast<jlong>(source.leftActive); stats[RightActiveSamples] = static_cast<jlong>(source.rightActive);
            stats[UnexpectedNonzero] = static_cast<jlong>(source.unexpected); stats[InactiveNonzero] = static_cast<jlong>(source.inactive);
            stats[OutsideNonzero] = static_cast<jlong>(source.outside);
            stats[MaximumSample] = source.maximum; stats[MinimumSample] = source.minimum; stats[PeakSample] = source.peak;
            integrity.compromise(source.reasons);
            if (source.sourceHash != source.submittedHash) integrity.compromise(SourceHashMismatch);
        }
        stats[NotSubmittedFrames] = std::max(stats[NotSubmittedFrames], stats[SourceFrames] - stats[SubmittedFrames]);
        stats[Remainder] = static_cast<jlong>(scheduler.remainder);
        if (stats[SourceFrames] != stats[ScheduledFrames] || stats[ScheduledFrames] != stats[SubmittedFrames] ||
            stats[SubmittedFrames] != stats[CompletedFrames]) integrity.compromise(FrameMismatch);
        if (stats[OutSubmitted] != stats[OutCompleted] || stats[FeedbackSubmitted] != stats[FeedbackCompleted] ||
            stats[OutPacketsSubmitted] != stats[OutPacketsCompleted]) integrity.compromise(ShutdownUnaccounted);
        if (stats[OutBytesSubmitted] != stats[SubmittedFrames] * frameBytes() || stats[KernelOutActualBytes] != stats[OutBytesSubmitted] ||
            (!fullTrack && stats[OutPacketsSubmitted] != stats[OutSubmitted] * static_cast<jlong>(kOutPackets))) integrity.compromise(FrameMismatch);
        stats[LastFeedbackAge] = feedbackClock.lastObserved ? static_cast<jlong>(monotonicUs() - feedbackClock.lastObserved) : -1;
        stats[DurationUs] = static_cast<jlong>(monotonicUs() - started);
        move(State::Stopped); stats[WorkerStopped] = 1;
        stats[Reasons] = static_cast<jlong>(integrity.reasons); stats[StopCode] = static_cast<int>(stopCause.value);
    }
    void run() {
        if (executing.exchange(true)) throw std::invalid_argument("Transport already executing");
        struct End { Session& session; ~End() { session.executing.store(false); } } end{*this};
        if (ran) throw std::invalid_argument("Transport already used");
        ran = true;
        started = monotonicUs(); lastOutCompletion = started; stats[Started] = 1;
        feedbackClock.started=started;
        diagnosticFreshPrime=started;
        outContinuity.start(started);
        move(State::Starting); move(State::Bootstrap);
        if (prefix) prefix->beginStreaming();
        try {
            // Receive feedback immediately, before priming a modest nominal OUT queue.
            for (size_t i = outUrbs; i < slots.size() && integrity.valid() && !stopCause.chosen; ++i) submit(slots[i]);
            outContinuity.beginRefill(monotonicUs(),true);
            for (size_t i = 0; i < outUrbs && integrity.valid() && !stopCause.chosen; ++i) submit(slots[i]);
            while (integrity.valid() && !stopCause.chosen) {
                waitingForEof=false;
                const uint64_t now = monotonicUs();
                if (prefix && prefix->queue.faults.load()) { failSource(); break; }
                if (requestedStop.load()) { fail(AuthorityChanged, requestedStop.load() == 2 ? Stop::AuthorityLost : Stop::Requested); break; }
                if (durationReached(started, now, durationUs)) { stopCause.choose(Stop::Duration); break; }
                if(playbackStep(now)) continue;
                outContinuity.wake(now,outstanding(false),outstanding(true),!fullTrack || !fullSubmitted(),
                    static_cast<uint64_t>(stats[CompletedFrames]));
                const auto feedbackCompletedBefore=stats[FeedbackCompleted];
                const bool hadValidFeedback=feedbackClock.lastObserved!=0;
                feedbackReapPass=reapBeforeFeedbackWatchdog(feedbackClock,
                    static_cast<uint64_t>(stats[FeedbackHoldover]),now,slots.size(),
                    [this] { return reapOne(); }, [] { return monotonicUs(); },
                    [this] { return integrity.valid() && !stopCause.chosen && !requestedStop.load() &&
                        !(prefix && prefix->queue.faults.load()); });
                feedbackReapPass.feedbackCompletions=static_cast<size_t>(stats[FeedbackCompleted]-feedbackCompletedBefore);
                if (hadValidFeedback && feedbackReapPass.ageBefore>static_cast<uint64_t>(stats[FeedbackHoldover]) &&
                    feedbackReapPass.refreshed && feedbackReapPass.watchdogEvaluated &&
                    feedbackReapPass.timeout==FeedbackTimeoutKind::None &&
                    feedbackReapPass.ageAfter<=static_cast<uint64_t>(stats[FeedbackHoldover]))
                    ++stats[FeedbackRecoveredGaps];
                completedProgress.store(static_cast<uint64_t>(stats[CompletedFrames]),std::memory_order_release);
                if(fullTrack) publishPlayback();
                if (!integrity.valid() || stopCause.chosen) break;
                if (prefix && prefix->queue.faults.load()) { failSource(); break; }
                if (requestedStop.load()) { fail(AuthorityChanged, requestedStop.load() == 2 ? Stop::AuthorityLost : Stop::Requested); break; }
                if(pauseAdmission()) continue; // Process a command arriving during reap before active watchdog evaluation.
                if (feedbackReapPass.timeout!=FeedbackTimeoutKind::None) {
                    feedbackTimeout(feedbackReapPass.timeout,feedbackReapPass.evaluatedAt); break;
                }
                if (naturalComplete()) break;
                if (durationReached(started, monotonicUs(), durationUs)) { stopCause.choose(Stop::Duration); break; }
                if (!prefix && (outstanding(false) == 0 || monotonicUs() - lastOutCompletion > kQueueWatchdogUs)) {
                    // Preserve Session 7/8's established watchdog and evidence semantics.
                    ++stats[Underruns]; fail(QueueUnderrun, Stop::Underrun); break;
                }
                if (prefix && outstanding(false) == 0) ++stats[EmptyAfterReap];
                // A reaped slot is free capacity, not missing source PCM. Refill before judging OUT-pool progress.
                outContinuity.observe(monotonicUs(),outstanding(false),outstanding(true),!fullTrack || !fullSubmitted(),
                    static_cast<uint64_t>(stats[CompletedFrames]),OutObservationStage::BeforeRefill);
                outContinuity.beginRefill(monotonicUs());
                for (auto& slot : slots) if (!slot.identity.outstanding && integrity.valid() && !stopCause.chosen) submit(slot);
                if (!integrity.valid() || stopCause.chosen) break;
                if(pauseAdmission()) continue;
                outContinuity.observe(monotonicUs(),outstanding(false),outstanding(true),!fullTrack || !fullSubmitted(),
                    static_cast<uint64_t>(stats[CompletedFrames]),OutObservationStage::AfterRefill);
                if (naturalComplete()) break;
                if (durationReached(started, monotonicUs(), durationUs)) { stopCause.choose(Stop::Duration); break; }
                const bool terminalAwait=fullTrack && waitingForEof && outstanding(false)==0;
                if (terminalAwait && monotonicUs()-lastOutCompletion>kQueueWatchdogUs) {
                    ++stats[SourceVerificationStalls]; fail(TerminalAccounting,Stop::SourceVerificationStall); break;
                }
                if (prefix && !terminalAwait && outPoolStalled(outstanding(false), monotonicUs() - lastOutCompletion)) {
                    ++stats[PoolStalls]; stats[PoolAtStall] = static_cast<jlong>(outstanding(false));
                    stats[CompletionAgeAtStall] = static_cast<jlong>(monotonicUs() - lastOutCompletion);
                    fail(OutPoolStall, Stop::OutPoolStall); break;
                }
                pollCompletion();
            }
        } catch (...) { fail(WorkerFailure, Stop::Worker); }
        if(fullTrack) {
            if(playback.seeking() && stopCause.value==Stop::Requested && !playbackFault) seekOutcome=SeekOutcome::Stopped;
            playback.finish(monotonicUs(),PlaybackPhase::Stopping);publishPlayback();
        }
        shutdown();
        completedProgress.store(static_cast<uint64_t>(stats[CompletedFrames]),std::memory_order_release);
        if(fullTrack) {
            // Producer errors discovered during join retain precedence over a racing user Stop.
            if(prefix->queue.faults.load() & ~static_cast<uint64_t>(fv::PrefixCancelled)) playbackFault=true;
            if(prefix->seekFailures.load()) {
                if(!stats[SeekFailures]) ++stats[SeekFailures];seekOutcome=SeekOutcome::Failed;segments.valid=false;
            }
            playback.finish(monotonicUs(),integrity.valid() && stopCause.value==Stop::NaturalEof ? PlaybackPhase::Ended :
                stopCause.value==Stop::Requested && !playbackFault && !stats[ShutdownIncomplete] && !(integrity.reasons&(CancelError|DeviceDetached)) ? PlaybackPhase::Stopped : PlaybackPhase::Failed);
            const auto wanted=intent();
            if(wanted.pauses>seenPauses) playback.pauseOutcome=playback.phaseIs(PlaybackPhase::Failed) ? ControlOutcome::Failed : ControlOutcome::Terminal;
            if(wanted.resumes>seenResumes) playback.resumeOutcome=playback.phaseIs(PlaybackPhase::Failed) ? ControlOutcome::Failed : ControlOutcome::Terminal;
            stats[PlaybackPhaseField]=static_cast<jlong>(playback.phase);
            stats[PauseRequests]=wanted.pauses;stats[ResumeRequests]=wanted.resumes;stats[PauseConfirmed]=playback.pauses;stats[ResumeConfirmed]=playback.resumes;
            stats[LastPauseOutcome]=static_cast<jlong>(playback.pauseOutcome);stats[LastResumeOutcome]=static_cast<jlong>(playback.resumeOutcome);
            stats[FirstPauseFrame]=wanted.firstPause;stats[LastPauseFrame]=wanted.lastPause;stats[PausedFrame]=playback.pausedFrame;
            stats[PausedDurationUs]=playback.pausedUs;stats[PausePendingOut]=0;stats[PauseDrainOutcome]=playback.drainOutcome;
            stats[ResumeReadiness]=playback.resumeReadiness;stats[ResumeFailure]=playback.resumeFailure;
            stats[SourceContinuityAcrossPause]=integrity.valid() && !stats[PauseDrainFailures] && !playback.resumeFailure;
            publishPlayback();
        }
        // No execution context returns while continuing to submit/reap in the background.
    }
};

std::mutex registryMutex;
std::unordered_map<jlong, std::shared_ptr<Session>> registry;
std::unordered_map<jlong, std::shared_ptr<fv::PrefixSource>> prefixRegistry;
jlong nextHandle = 1;
void error(JNIEnv* env, const char* type, const char* message) {
    jclass cls = env->FindClass(type);
    if (cls) { env->ThrowNew(cls, message); env->DeleteLocalRef(cls); }
}
std::shared_ptr<Session> lookup(jlong handle, jlong attachment, jlong stream) {
    std::lock_guard<std::mutex> lock(registryMutex);
    auto found = registry.find(handle);
    if (found == registry.end() || found->second->attachment != static_cast<uint64_t>(attachment) || found->second->stream != static_cast<uint64_t>(stream)) return nullptr;
    return found->second;
}
jobject snapshot(JNIEnv* env, const Session& session) {
    jclass cls = env->FindClass("io/github/rsgarrido/sazanami/player/usb/UsbIsoNativeSnapshot");
    if (!cls) return nullptr;
    jmethodID constructor = env->GetMethodID(cls, "<init>", "([J[J[J[J[J)V");
    if (!constructor) return nullptr;
    jlongArray numbers = env->NewLongArray(kSnapshotFieldCount);
    jlongArray feedback = env->NewLongArray(static_cast<jsize>(session.feedbackExampleCount * 6));
    jlongArray packets = env->NewLongArray(static_cast<jsize>(session.packetExampleCount * 7));
    jlongArray pcm = env->NewLongArray(static_cast<jsize>(session.source.exampleCount * 9));
    jlongArray segments=env->NewLongArray(static_cast<jsize>(session.segments.exampleCount*10));
    if (!numbers || !feedback || !packets || !pcm || !segments) return nullptr;
    std::array<jlong,80> segmentRows{};
    for(size_t row=0;row<session.segments.exampleCount;++row) for(size_t col=0;col<10;++col)
        segmentRows[row*10+col]=col==7 || col==8?hashBits(session.segments.examples[row][col]):static_cast<jlong>(session.segments.examples[row][col]);
    env->SetLongArrayRegion(segments,0,static_cast<jsize>(session.segments.exampleCount*10),segmentRows.data());
    std::array<jlong, 13 * 9> examples{};
    for (size_t row = 0; row < session.source.exampleCount; ++row)
        for (size_t column = 0; column < 9; ++column) examples[row * 9 + column] = session.prefix ? session.prefix->queue.examples[row][column] : column<7 ? session.source.examples[row][column] : 0;
    env->SetLongArrayRegion(pcm, 0, static_cast<jsize>(session.source.exampleCount * 9), examples.data());
    env->SetLongArrayRegion(numbers, 0, FieldCount, session.stats.data());
    const auto diagnostics=session.feedbackDiagnostics.serialized();
    std::array<jlong,kFeedbackDiagnosticWords> diagnosticWords{};
    std::copy(diagnostics.begin(),diagnostics.end(),diagnosticWords.begin());
    env->SetLongArrayRegion(numbers,FieldCount,kFeedbackDiagnosticWords,diagnosticWords.data());
    env->SetLongArrayRegion(feedback, 0, static_cast<jsize>(session.feedbackExampleCount * 6), session.feedbackExamples.data());
    env->SetLongArrayRegion(packets, 0, static_cast<jsize>(session.packetExampleCount * 7), session.packetExamples.data());
    if (env->ExceptionCheck()) return nullptr;
    return env->NewObject(cls, constructor, numbers, feedback, packets, pcm,segments);
}
} // namespace

// Pure CPU preflight: no fd, USB ownership, or endpoint traffic.
extern "C" JNIEXPORT jlongArray JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeReferencePreflight(JNIEnv* env, jobject) {
    const ReferencePreflight p;
    const std::array<jlong, 12> values{p.pass() ? 1 : 0, static_cast<jlong>(p.reasons),
        static_cast<jlong>(kReferenceTone.size()), hashBits(kReferenceHash), hashBits(p.actualHash),
        p.maximum, p.minimum, p.peak, p.packing ? 1 : 0, p.channels ? 1 : 0,
        p.deterministic ? 1 : 0, activePerCycle()};
    auto result = env->NewLongArray(values.size());
    if (result) env->SetLongArrayRegion(result, 0, values.size(), values.data());
    return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeCreateSilentTransport(
    JNIEnv* env, jobject, jint fd, jlong attachment, jlong stream, jintArray configuration, jlong prefixHandle) {
    if (fd < 0 || attachment < 1 || stream < 1 || !configuration || env->GetArrayLength(configuration) != 17) {
        error(env, "java/lang/IllegalArgumentException", "Invalid silent transport identity/configuration"); return 0;
    }
    std::array<jint, 17> config{}; env->GetIntArrayRegion(configuration, 0, 17, config.data());
    if (env->ExceptionCheck()) return 0;
    if (config[0] < 0 || config[0] > 255 || config[1] < 0 || config[1] > 255 || config[0] == config[1] ||
        config[2] < 1 || config[2] > 255 || config[3] < 0 || config[3] > 255 || config[2] == config[3] ||
        config[4] < 1 || config[4] > 15 || config[5] != 5 || !capacity(config[6]) || config[7] < 1 || config[7] > 3 ||
        config[8] < 0x81 || config[8] > 0x8f || config[9] != 0x11 || config[10] != 4 || config[11] < 1 || config[11] > 7 ||
        !sazanami::directpcm::supports(config[12],config[13],config[15]) || config[14] != config[15]/8 ||
        (config[16] < 2 && (config[12] != static_cast<int>(kRate) || config[15] != 16)) || config[16] < 0 || config[16] > 3) {
        error(env, "java/lang/IllegalArgumentException", "Unsupported bounded UAC2 silence target"); return 0;
    }
    std::shared_ptr<fv::PrefixSource> prefixSource;
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (config[16] >= 2) {
            auto found = prefixRegistry.find(prefixHandle);
            if (found == prefixRegistry.end() || found->second->attachment != static_cast<uint64_t>(attachment) ||
                found->second->stream != static_cast<uint64_t>(stream) || found->second->fullTrack != (config[16]==3) || found->second->primed.load() < found->second->primeTarget() ||
                !found->second->metadataReady.load(std::memory_order_acquire) ||
                found->second->metadataFacts[fv::Rate] != config[12] ||
                found->second->metadataFacts[fv::Channels] != config[13] ||
                found->second->metadataFacts[fv::Bits] != config[15] ||
                found->second->queue.faults.load() || found->second->queue.stop.load()) {
                error(env, "java/lang/IllegalStateException", "FLAC source not primed/current/healthy"); return 0;
            }
            prefixSource=found->second;
        } else if (prefixHandle || !prefixRegistry.empty()) {
            error(env, "java/lang/IllegalStateException", "FLAC source registry busy"); return 0;
        }
    }
    const uint32_t selectedFrameBytes=static_cast<uint32_t>(config[13]*(config[15]/8)); // Validated finite format matrix above.
    if(prefixSource && (!prefixSource->queue.format || prefixSource->queue.format->subslotBytes!=static_cast<uint32_t>(config[14]) ||
        prefixSource->queue.format->bitsPerSample!=static_cast<uint32_t>(config[15]) || prefixSource->queue.frameBytes()!=selectedFrameBytes)) {
        error(env,"java/lang/IllegalStateException","Source/USB format mismatch");return 0;
    }
    const int speed = ::ioctl(fd, USBDEVFS_GET_SPEED);
    const int saved = speed < 0 ? errno : 0;
    if (speed < 0) return -static_cast<jlong>(saved);
    if (speed != 3) { error(env, "java/lang/IllegalStateException", "Actual USB High Speed required"); return 0; }
    constexpr uint64_t scaledSecond = 100 * kDurationUs;
    const uint64_t worstFrames = (static_cast<uint64_t>(config[12]) * intervalUs(config[7]) * (100 + kPlausibilityPercent) + scaledSecond - 1) / scaledSecond;
    if (((config[6] & 0x1800) != 0 && config[7] != 1) || capacity(config[6]) < worstFrames * selectedFrameBytes) {
        error(env, "java/lang/IllegalArgumentException", "Insufficient/invalid service-interval capacity"); return 0;
    }
    try {
        auto session = std::make_shared<Session>(fd, static_cast<uint64_t>(attachment), static_cast<uint64_t>(stream), config, prefixSource);
        std::lock_guard<std::mutex> lock(registryMutex);
        if (!registry.empty() || nextHandle == std::numeric_limits<jlong>::max()) throw std::invalid_argument("Transport registry busy/exhausted");
        const jlong handle = nextHandle++; registry.emplace(handle, std::move(session)); return handle;
    } catch (...) { error(env, "java/lang/IllegalStateException", "Silent transport allocation/registration unavailable"); return 0; }
}

extern "C" JNIEXPORT jobject JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeRunSilentTransport(JNIEnv* env, jobject, jlong handle, jlong attachment, jlong stream) {
    const auto session = lookup(handle, attachment, stream);
    if (!session) { error(env, "java/lang/IllegalArgumentException", "Stale silent transport handle"); return nullptr; }
    try { session->run(); } catch (...) { error(env, "java/lang/IllegalStateException", "Silent transport execution rejected"); return nullptr; }
    auto result = snapshot(env, *session);
    session->prefix.reset(); // Snapshot has copied the joined source's width-aware examples; URBs own separate payloads.
    return result;
}

// Snapshot only atomic progress and immutable total; no concurrent access to the mutable evidence arrays.
extern "C" JNIEXPORT jlongArray JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeFullProgress(JNIEnv* env,jobject,jlong handle,jlong attachment,jlong stream) {
    const auto session=lookup(handle,attachment,stream);
    if (!session || !session->fullTrack) { error(env,"java/lang/IllegalArgumentException","Stale full playback progress"); return nullptr; }
    std::array<jlong,4+kControlFieldCount+kSeekFieldCount> values{};
    { std::lock_guard<std::mutex> lock(session->controlMutex);
      values[0]=static_cast<jlong>(session->livePlayback[0]);values[1]=static_cast<jlong>(session->totalProgress);
      values[2]=static_cast<jlong>(session->sampleRateHz);values[3]=static_cast<jlong>(session->livePlayback[1]);
      std::copy(session->liveControl.begin(),session->liveControl.end(),values.begin()+4);
      std::copy(session->liveSeek.begin(),session->liveSeek.end(),values.begin()+4+kControlFieldCount);
      if(session->livePausedStarted) values[4+PausedDurationUs-PlaybackPhaseField]+=static_cast<jlong>(monotonicUs()-session->livePausedStarted); }
    auto result=env->NewLongArray(values.size());
    if (result) env->SetLongArrayRegion(result,0,values.size(),values.data());
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeStopSilentTransport(JNIEnv*, jobject, jlong handle, jlong attachment, jlong stream, jint reason) {
    const auto session = lookup(handle, attachment, stream);
    if (session) { session->requestedStop.store(reason == 2 ? 2 : 1);if(session->controlWake) session->controlWake->notify(); }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeSetFullPlayWhenReady(JNIEnv* env,jobject,jlong handle,jlong attachment,jlong stream,
        jboolean play,jlong sequence,jlong pauses,jlong resumes,jlong seeks,jlong seekSerial,jlong target,jlong firstTarget) {
    auto session=lookup(handle,attachment,stream);
    if(!session || !session->fullTrack || sequence<0 || pauses<0 || resumes<0 || seeks<0 || seekSerial<0 || target<0 || firstTarget<0) {
        error(env,"java/lang/IllegalArgumentException","Stale/invalid full playback control");return JNI_FALSE;
    }
    try { return session->requestPlayback(play,sequence,pauses,resumes,seeks,seekSerial,target,firstTarget) ? JNI_TRUE : JNI_FALSE; }
    catch (...) { error(env,"java/lang/IllegalStateException","Full playback control failed");return JNI_FALSE; }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeSilentTransportIdle(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(registryMutex);
    return registry.empty() && prefixRegistry.empty() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeDestroySilentTransport(JNIEnv*, jobject, jlong handle, jlong attachment, jlong stream, jboolean connectionClosed) {
    std::lock_guard<std::mutex> lock(registryMutex);
    auto found = registry.find(handle);
    if (found == registry.end()) return JNI_FALSE;
    const auto& session = found->second;
    if (session->attachment != static_cast<uint64_t>(attachment) || session->stream != static_cast<uint64_t>(stream) || session->executing.load()) return JNI_FALSE;
    if (!connectionClosed && session->outstanding(false) + session->outstanding(true) != 0) return JNI_FALSE;
    // Close has synchronously destroyed all usbfs async work; only then may unreaped pools be freed.
    session->fd = -1; registry.erase(found); return JNI_TRUE;
}

namespace {
std::shared_ptr<fv::PrefixSource> lookupPrefix(jlong handle,jlong attachment,jlong stream) {
    std::lock_guard<std::mutex> lock(registryMutex);
    auto found=prefixRegistry.find(handle);
    if (found==prefixRegistry.end() || found->second->attachment!=static_cast<uint64_t>(attachment) ||
        found->second->stream!=static_cast<uint64_t>(stream)) return nullptr;
    return found->second;
}
jobject prefixSnapshot(JNIEnv* env,const fv::PrefixSource& source,bool released) {
    jclass cls=env->FindClass("io/github/rsgarrido/sazanami/player/usb/UsbFlacSourceSnapshot");
    if (!cls) return nullptr;
    auto constructor=env->GetMethodID(cls,"<init>","([J[JLjava/lang/Object;)V");
    if (!constructor) return nullptr;
    const auto evidence=source.evidence(released);
    std::array<jlong,fv::PrefixFieldCount> values{};
    std::copy(evidence.begin(),evidence.end(),values.begin());
    auto numbers=env->NewLongArray(fv::PrefixFieldCount);
    auto examples=env->NewLongArray(static_cast<jsize>(source.queue.exampleCount*9));
    if (!numbers || !examples) return nullptr;
    std::array<jlong,5*9> rows{};
    for (size_t i=0;i<source.queue.exampleCount;++i)
        for (size_t j=0;j<9;++j) rows[i*9+j]=source.queue.examples[i][j];
    env->SetLongArrayRegion(numbers,0,fv::PrefixFieldCount,values.data());
    env->SetLongArrayRegion(examples,0,static_cast<jsize>(source.queue.exampleCount*9),rows.data());
    // EOF is now a reusable source checkpoint; only joined teardown makes decoder evidence immutable to observers.
    jobject decoder=source.joined.load()?fv::snapshotFlac(env,source.decoder):nullptr;
    if (env->ExceptionCheck()) return nullptr;
    return env->NewObject(cls,constructor,numbers,examples,decoder);
}
}
extern "C" JNIEXPORT jlong JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFlacPrefixNative_nativeCreate(JNIEnv* env,jobject,jint borrowedFd,jlong attachment,jlong stream,jboolean fullTrack) {
    if (borrowedFd<0 || attachment<1 || stream<1) { error(env,"java/lang/IllegalArgumentException","Invalid FLAC prefix identity");return 0; }
    const int duplicate=::dup(borrowedFd);
    if (duplicate<0) return -static_cast<jlong>(errno);
    fv::OwnedFd pending(duplicate);
    try {
        auto source=std::make_shared<fv::PrefixSource>(std::move(pending),attachment,stream,fullTrack==JNI_TRUE);
        std::lock_guard<std::mutex> lock(registryMutex);
        if (!registry.empty() || !prefixRegistry.empty() || nextHandle==std::numeric_limits<jlong>::max())
            throw std::invalid_argument("Endpoint/source registry busy");
        const jlong handle=nextHandle++;
        source->start();prefixRegistry.emplace(handle,std::move(source));
        return handle;
    } catch (...) { error(env,"java/lang/IllegalStateException","FLAC prefix allocation/start unavailable");return 0; }
}
extern "C" JNIEXPORT jobject JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFlacPrefixNative_nativePrime(JNIEnv* env,jobject,jlong handle,jlong attachment,jlong stream) {
    auto source=lookupPrefix(handle,attachment,stream);
    if (!source) { error(env,"java/lang/IllegalArgumentException","Stale FLAC prefix");return nullptr; }
    source->prime();
    return prefixSnapshot(env,*source,false);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFlacPrefixNative_nativeHealthy(JNIEnv*,jobject,jlong handle,jlong attachment,jlong stream) {
    auto source=lookupPrefix(handle,attachment,stream);
    return source && source->primed.load()>=source->primeTarget() && source->metadataReady.load() &&
        !source->queue.faults.load() && !source->queue.stop.load() ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFlacPrefixNative_nativeStop(JNIEnv*,jobject,jlong handle,jlong attachment,jlong stream) {
    auto source=lookupPrefix(handle,attachment,stream);
    if (source && !source->queue.stop.load()) source->queue.fail(fv::PrefixCancelled);
}
extern "C" JNIEXPORT jobject JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFlacPrefixNative_nativeFinish(JNIEnv* env,jobject,jlong handle,jlong attachment,jlong stream) {
    auto source=lookupPrefix(handle,attachment,stream);
    if (!source) { error(env,"java/lang/IllegalArgumentException","Stale FLAC prefix");return nullptr; }
    source->finish();
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        // Inactive sessions own only copied URB payloads; discard any source reference on failed-start paths.
        for (auto& entry : registry) {
            if (entry.second->prefix == source && !entry.second->executing.load()) entry.second->prefix.reset();
        }
        prefixRegistry.erase(handle);
    }
    // Queue is only held here; USB run released its source reference after copying final evidence.
    return prefixSnapshot(env,*source,true);
}
