// Pure production-telemetry tests. Authored only; NOT compiled or run by Codex.
#include "../../main/cpp/usb_out_continuity.h"
#include "../../main/cpp/usb_out_queue_policy.h"
#include <cassert>
using namespace sazanami::usbiso;

static void prime(OutContinuity& c,uint64_t now) {
    c.start(now);c.beginRefill(now,true);
    for (size_t i=0;i<kOutPool;++i) c.submitted(now,kOutPackets);
}
static void geometryAndShortTail() {
    static_assert(kOutPool==8 && kOutPackets==8);
    static_assert(nominalOutCoverageUs(8,8,125)==8000);
    static_assert(nominalOutCoverageUs(1,8,125)==1000);
    assert(nominalOutCoverageUs(8,8,500)==32000);
    OutContinuity c(8,8,125);c.start(1000);c.beginRefill(1000,true);
    c.submitted(1000,1); // Exact one-packet terminal URB, not eight packets of padding.
    assert(c.estimatedCoverage(1000)==125 && c.estimatedCoverage(1125)==0);
    c.wake(1000,1,21,false,0);c.observe(1100,1,21,false,0,OutObservationStage::BeforeRefill);
    c.completed(1125,1);c.observe(1125,0,21,false,5,OutObservationStage::AfterRefill);
    assert(c.observations==0 && c.riskCount==0); // Normal terminal depletion is excluded.
}
static void delayedWakeAndFrozenRisk() {
    OutContinuity c(8,8,125);prime(c,1000);
    c.wake(1000,8,21,true,0);
    c.wake(9500,8,21,true,352); // Application flags can all remain outstanding after >8 ms.
    assert(c.maxWorkerLoopGapUs==8500 && c.maxGapObservedAtUs==8500);
    assert(c.outAtMaxGap==8 && c.feedbackAtMaxGap==21 && c.maxGapNeedsOut);
    assert(c.gapsBeyondCoverage==1 && !c.maxGapRefilled);
    assert(c.riskCount==1 && c.firstRiskAtUs==8500 && c.firstRiskOut==8);
    assert(c.firstRiskMask==(WorkerGapBeyondCoverage|EstimatedBelowOneUrb));
    assert(c.firstRiskCoverageUs==0 && c.firstRiskCompletedFrames==352);
    for(size_t i=0;i<8;++i) c.completed(9500,8);
    c.observe(9600,0,0,true,352,OutObservationStage::BeforeRefill);
    c.beginRefill(9600);
    for(size_t i=0;i<8;++i) c.submitted(9700,8,9500);
    c.observe(9800,8,21,true,352,OutObservationStage::AfterRefill);
    assert(c.maxGapRefilled && c.maxSubmissionGapUs==8700 && c.maxBatchGapUs==8700);
    assert(c.maxRefillLatencyUs==200 && c.zeroObservations==1 && c.minOutstanding==0);
    assert(c.minEstimatedCoverageUs==0 && c.worstCoverageAtUs==8500);
    assert(c.riskCount==1 && c.firstRiskOut==8); // Freeze first evidence; once per iteration.
    c.wake(10700,8,21,true,352);c.beginRefill(10700);
    assert(c.maxRefillOpportunityGapUs==1100 && c.riskCount==1);
    // Final logical frame/byte/hash accounting is deliberately not an input to this helper.
}
static void lowCountsAndIntervals() {
    OutContinuity c(8,8,125);prime(c,1000);c.wake(1000,8,21,true,0);
    for(size_t i=0;i<6;++i) c.completed(1000,8);
    c.observe(1000,2,21,true,264,OutObservationStage::BeforeRefill);
    c.completed(1500,8);c.observe(1500,1,21,true,308,OutObservationStage::BeforeRefill);
    c.completed(1750,8);c.observe(1750,0,21,true,352,OutObservationStage::BeforeRefill);
    assert(c.minOutstanding==0 && c.zeroObservations==1 && c.oneObservations==1 && c.twoObservations==1);
    assert(c.observations==4 && c.belowOneUrb==1 && c.belowTwoUrbs==2);
    assert(c.maxCompletionGapUs==500);
    assert(c.firstRiskStage==static_cast<uint64_t>(OutObservationStage::BeforeRefill));
    assert(c.firstRiskMask==(NoApplicationOut|EstimatedBelowOneUrb));
    c.beginRefill(2000);c.submitted(2000,8,1500);c.submitted(2050,8,1750);
    c.beginRefill(4000);c.submitted(4250,1,1750);
    assert(c.maxSubmissionGapUs==2200 && c.maxBatchGapUs==2250);
    assert(c.maxRefillLatencyUs==2500 && c.maxRefillOpportunityGapUs==2000);
    c.beginRefill(5000); // No successful OUT: no new submission batch.
    assert(c.maxBatchGapUs==2250);
}
static void refillLatencyRiskWithoutWakeGap() {
    OutContinuity c(8,8,125);prime(c,1000);c.wake(1000,8,21,true,0);
    c.completed(2000,8);c.observe(2000,7,21,true,44,OutObservationStage::BeforeRefill);
    c.beginRefill(2000);c.submitted(10501,8,2000);
    c.observe(10501,8,21,true,44,OutObservationStage::AfterRefill);
    assert(c.maxRefillLatencyUs==8501 && c.riskCount==1);
    assert(c.firstRiskMask==RefillBeyondCoverage && c.firstRiskGapUs==0);
    assert(c.firstRiskStage==static_cast<uint64_t>(OutObservationStage::AfterRefill));
    assert(c.firstRiskCoverageUs==1000); // Fresh nominal URB does not erase the late refill evidence.
}
static void directDepthUsesNewBudgetWithoutChangingRiskSemantics() {
    const auto count=outUrbCount(125,3);
    OutContinuity c(count,kOutPackets,125);c.start(1000);c.beginRefill(1000,true);
    for(size_t i=0;i<count;++i) c.submitted(1000,kOutPackets);
    assert(count==21 && c.urbDurationUs==1000 && c.nominalCoverageUs==21000);
    c.wake(1000,count,21,true,0);c.wake(16444,count,21,true,0);
    assert(c.maxWorkerLoopGapUs==15444 && c.gapsBeyondCoverage==0 && c.riskCount==0);
    assert(c.estimatedCoverage(16444)==5556); // Ideal model only, not wire evidence.
    c.wake(37445,count,21,true,0);
    assert(c.gapsBeyondCoverage==1 && c.riskCount==1);
    assert(c.firstRiskGapUs==21001 && (c.firstRiskMask&WorkerGapBeyondCoverage));
    OutContinuity low(count,8,125);low.start(1000);low.beginRefill(1000,true);low.submitted(1000,8);
    low.wake(1001,1,21,true,0);
    assert(low.belowOneUrb==1 && low.belowTwoUrbs==1); // Still 1 ms / 2 ms thresholds.
}
static void intentionalPauseRetainsRisksWithoutChargingTheGap() {
    OutContinuity c(21,8,125);c.start(1000);c.beginRefill(1000,true);
    for(unsigned i=0;i<21;++i)c.submitted(1000,8);
    c.wake(1000,21,21,true,0);
    c.suspendSegment(23001,21,21,true,480); // Real scheduling delay BEFORE user command remains visible.
    assert(c.riskCount==1 && c.maxWorkerLoopGapUs==22001 && c.gapsBeyondCoverage==1);
    const auto first=c.firstRiskAtUs,mask=c.firstRiskMask,maxGap=c.maxWorkerLoopGapUs,observations=c.observations;
    for(unsigned i=0;i<21;++i)c.completed(24000,8);
    c.resumeSegment(); // Called with both pools reconciled; preserve cumulative evidence.
    const uint64_t resumed=15024000;
    c.beginRefill(resumed,true);for(unsigned i=0;i<21;++i)c.submitted(resumed,8);
    c.wake(resumed,21,21,true,1008);c.completed(resumed+1000,8);
    c.beginRefill(resumed+1000);c.submitted(resumed+1000,8,resumed+1000);
    assert(c.maxWorkerLoopGapUs==maxGap && c.gapsBeyondCoverage==1 && c.riskCount==1);
    assert(c.firstRiskAtUs==first && c.firstRiskMask==mask && c.observations==observations+1);
    assert(c.maxSubmissionGapUs==1000 && c.maxBatchGapUs==1000 && c.maxRefillLatencyUs==0);
    assert(c.maxCompletionGapUs==0); // No comparison against a completion 15 seconds earlier.
    c.wake(resumed+23001,21,21,true,1056);
    assert(c.riskCount==2 && c.gapsBeyondCoverage==2 && c.firstRiskAtUs==first);
}
static void repeatedQuietTransitionsCannotChargeTheOldActiveBaseline() {
    OutContinuity c(21,8,125);c.start(1000);c.beginRefill(1000,true);
    for(unsigned i=0;i<21;++i)c.submitted(1000,8);
    c.wake(1000,21,21,true,0);
    c.suspendSegment(1100,21,21,true,100); // PLAYING -> PAUSING/SEEK_DRAINING: capture the active boundary once.
    for(unsigned i=0;i<21;++i)c.completed(2000,8);
    const auto observations=c.observations,firstGap=c.maxWorkerLoopGapUs;
    // PAUSED -> seek, superseding reposition/prime, or a new seek before RESUMING has primed its pools.
    c.suspendSegment(1802695,0,0,true,100);
    c.suspendSegment(3802695,0,0,true,100);
    c.suspendSegment(5802695,0,0,true,100);
    assert(c.maxWorkerLoopGapUs==firstGap && c.observations==observations && !c.riskCount && !c.gapsBeyondCoverage);
    c.resumeSegment();c.beginRefill(5803000,true);
    for(unsigned i=0;i<21;++i)c.submitted(5803000,8);
    c.wake(5803000,21,21,true,100);
    assert(!c.riskCount && c.maxWorkerLoopGapUs==firstGap);
    for(unsigned i=0;i<21;++i)c.completed(5804000,8);
    c.wake(5825001,0,0,true,200); // A genuine active zero-OUT starvation remains visible.
    assert(c.riskCount==1 && c.gapsBeyondCoverage==1 && c.firstRiskGapUs==22001);
    assert(c.firstRiskMask==(WorkerGapBeyondCoverage|NoApplicationOut|EstimatedBelowOneUrb));
    const auto riskAt=c.firstRiskAtUs,mask=c.firstRiskMask,maxGap=c.maxWorkerLoopGapUs;
    c.suspendSegment(5825100,0,0,true,200);c.suspendSegment(9000000,0,0,true,200);
    c.resumeSegment();c.beginRefill(9000001,true);
    for(unsigned i=0;i<21;++i)c.submitted(9000001,8);
    c.wake(9000001,21,21,true,200);
    assert(c.firstRiskAtUs==riskAt && c.firstRiskMask==mask && c.maxWorkerLoopGapUs==maxGap);
}
int main() {
    geometryAndShortTail();delayedWakeAndFrozenRisk();lowCountsAndIntervals();refillLatencyRiskWithoutWakeGap();
    directDepthUsesNewBudgetWithoutChangingRiskSemantics();
    intentionalPauseRetainsRisksWithoutChargingTheGap();
    repeatedQuietTransitionsCannotChargeTheOldActiveBaseline();
}
