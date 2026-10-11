#pragma once
#include "usb_iso_core.h"
#include <algorithm>

namespace sazanami::usbiso {
constexpr uint64_t nominalOutCoverageUs(size_t urbs,size_t packets,uint64_t intervalUs) noexcept {
    return urbs*packets*intervalUs;
}
enum class OutObservationStage : uint64_t { Wake, BeforeRefill, AfterRefill };
enum OutContinuityRisk : uint64_t {
    WorkerGapBeyondCoverage=1, NoApplicationOut=2, EstimatedBelowOneUrb=4, RefillBeyondCoverage=8
};

// Host timing diagnostics only. Application-pending URBs include completed but
// unreaped requests. Neither that count nor this ideal FIFO/ASAP time model is
// a measurement of kernel-armed work, service frames, or DAC continuity.
struct OutContinuity {
    const size_t configuredUrbs, packetsPerUrb;
    const uint64_t intervalUs, urbDurationUs, nominalCoverageUs;
    uint64_t started=0, observations=0;
    size_t minOutstanding=0;
    uint64_t zeroObservations=0, oneObservations=0, twoObservations=0;
    uint64_t minEstimatedCoverageUs=0, worstCoverageAtUs=0, belowOneUrb=0, belowTwoUrbs=0;
    uint64_t maxWorkerLoopGapUs=0, maxGapObservedAtUs=0;
    size_t outAtMaxGap=0, feedbackAtMaxGap=0;
    bool maxGapRefilled=false, maxGapNeedsOut=false;
    uint64_t gapsBeyondCoverage=0;
    uint64_t maxSubmissionGapUs=0, maxBatchGapUs=0, maxCompletionGapUs=0;
    uint64_t maxRefillOpportunityGapUs=0, maxRefillLatencyUs=0;
    uint64_t riskCount=0, firstRiskAtUs=0, firstRiskMask=0, firstRiskStage=0;
    size_t firstRiskOut=0, firstRiskFeedback=0;
    uint64_t firstRiskGapUs=0, firstRiskCoverageUs=0, firstRiskCompletedFrames=0;

    OutContinuity(size_t urbs,size_t packets,uint64_t interval) noexcept
        : configuredUrbs(urbs), packetsPerUrb(packets), intervalUs(interval),
          urbDurationUs(nominalOutCoverageUs(1,packets,interval)),
          nominalCoverageUs(nominalOutCoverageUs(urbs,packets,interval)) {}

    void start(uint64_t at) noexcept { started=at; }
    void suspendSegment(uint64_t at,size_t out,size_t feedback,bool needsOut,uint64_t frames) noexcept {
        // Preserve a real gap before the command, without charging request/drain/paused time to active scheduling.
        if(haveLoop && at>lastLoop) wake(at,out,feedback,needsOut,frames);
    }
    // Called only with a fully reconciled pool. Keep maxima/risks; discard time baselines across an intentional gap.
    void resumeSegment() noexcept {
        haveLoop=haveSubmission=haveBatch=haveCompletion=haveRefillOpportunity=false;
        batchHasOut=thisLoopIsMaxGap=riskCountedThisLoop=refillLateThisLoop=false;
        currentGapUs=pendingNominalUs=estimatedTailUs=0;
    }
    void beginRefill(uint64_t now,bool priming=false) noexcept {
        batchHasOut=false;
        if (!priming) {
            if (haveRefillOpportunity) maxRefillOpportunityGapUs=std::max(maxRefillOpportunityGapUs,now-lastRefillOpportunity);
            lastRefillOpportunity=now; haveRefillOpportunity=true;
        }
    }
    void submitted(uint64_t now,size_t packets,uint64_t reusableAt=0) noexcept {
        if (haveSubmission) maxSubmissionGapUs=std::max(maxSubmissionGapUs,now-lastSubmission);
        lastSubmission=now; haveSubmission=true;
        if (!batchHasOut) {
            if (haveBatch) maxBatchGapUs=std::max(maxBatchGapUs,now-lastBatchFirstSubmission);
            lastBatchFirstSubmission=now; haveBatch=true; batchHasOut=true;
        }
        if (reusableAt) {
            const auto latency=now-reusableAt;
            maxRefillLatencyUs=std::max(maxRefillLatencyUs,latency);
            refillLateThisLoop |= latency>nominalCoverageUs;
        }
        if (thisLoopIsMaxGap) maxGapRefilled=true;
        // A submission is placed behind the ideal nominal tail. Reset after all
        // application-pending OUT has been reaped. A shortened EOF URB contributes
        // its actual packet count, never a fictitious full-length URB.
        if (!pendingNominalUs) estimatedTailUs=now;
        const auto duration=nominalOutCoverageUs(1,packets,intervalUs);
        estimatedTailUs=std::max(estimatedTailUs,now)+duration;
        pendingNominalUs+=duration;
    }
    void completed(uint64_t observed,size_t packets) noexcept {
        if (haveCompletion) maxCompletionGapUs=std::max(maxCompletionGapUs,observed-lastCompletion);
        lastCompletion=observed; haveCompletion=true;
        const auto duration=nominalOutCoverageUs(1,packets,intervalUs);
        pendingNominalUs=pendingNominalUs>duration ? pendingNominalUs-duration : 0;
        if (!pendingNominalUs) estimatedTailUs=0;
    }
    uint64_t estimatedCoverage(uint64_t now) const noexcept {
        return std::min(pendingNominalUs,estimatedTailUs>now ? estimatedTailUs-now : 0);
    }
    void wake(uint64_t now,size_t out,size_t feedback,bool needsOut,uint64_t frames) noexcept {
        currentGapUs=haveLoop ? now-lastLoop : 0;
        lastLoop=now; haveLoop=true; thisLoopIsMaxGap=false;
        riskCountedThisLoop=false; refillLateThisLoop=false;
        if (currentGapUs>maxWorkerLoopGapUs) {
            maxWorkerLoopGapUs=currentGapUs; maxGapObservedAtUs=now-started;
            outAtMaxGap=out; feedbackAtMaxGap=feedback;
            maxGapRefilled=false; maxGapNeedsOut=needsOut; thisLoopIsMaxGap=true;
        }
        if (needsOut && currentGapUs>nominalCoverageUs) ++gapsBeyondCoverage;
        observe(now,out,feedback,needsOut,frames,OutObservationStage::Wake);
    }
    void observe(uint64_t now,size_t out,size_t feedback,bool needsOut,uint64_t frames,OutObservationStage stage) noexcept {
        // Normal terminal depletion and shutdown are not a refill-demand risk.
        // The caller excludes shutdown/failure, and needsOut excludes full EOF submission.
        if (!needsOut) return;
        const auto coverage=estimatedCoverage(now);
        if (!observations || out<minOutstanding) minOutstanding=out;
        if (!observations || coverage<minEstimatedCoverageUs) {
            minEstimatedCoverageUs=coverage; worstCoverageAtUs=now-started;
        }
        ++observations;
        if (out==0) ++zeroObservations;
        if (out==1) ++oneObservations;
        if (out==2) ++twoObservations;
        if (coverage<urbDurationUs) ++belowOneUrb;
        if (coverage<2*urbDurationUs) ++belowTwoUrbs;
        uint64_t mask=0;
        if (currentGapUs>nominalCoverageUs) mask|=WorkerGapBeyondCoverage;
        if (!out) mask|=NoApplicationOut;
        if (coverage<urbDurationUs) mask|=EstimatedBelowOneUrb;
        if (refillLateThisLoop) mask|=RefillBeyondCoverage;
        if (!mask || riskCountedThisLoop) return;
        riskCountedThisLoop=true;
        if (!riskCount++) {
            firstRiskAtUs=now-started; firstRiskMask=mask; firstRiskStage=static_cast<uint64_t>(stage);
            firstRiskOut=out; firstRiskFeedback=feedback; firstRiskGapUs=currentGapUs;
            firstRiskCoverageUs=coverage; firstRiskCompletedFrames=frames;
        }
    }

private:
    uint64_t pendingNominalUs=0, estimatedTailUs=0;
    uint64_t lastLoop=0, currentGapUs=0, lastSubmission=0, lastBatchFirstSubmission=0;
    uint64_t lastCompletion=0, lastRefillOpportunity=0;
    bool haveLoop=false, haveSubmission=false, haveBatch=false, haveCompletion=false, haveRefillOpportunity=false;
    bool batchHasOut=false, thisLoopIsMaxGap=false, riskCountedThisLoop=false, refillLateThisLoop=false;
};
} // namespace sazanami::usbiso
