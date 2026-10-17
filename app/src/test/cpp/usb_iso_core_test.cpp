// Standalone host/NDK pure-helper tests. Not part of APK production code. Created, NOT executed.
#include "../../main/cpp/usb_iso_core.h"
#include <cassert>
#include <array>
#include <climits>
using namespace sazanami::usbiso;

static void intervalAndCapacity() {
    assert(intervalUs(1) == 125); assert(intervalUs(4) == 1000);
    assert(intervalUs(0) == 0 && intervalUs(17) == 0);
    assert(capacity(776) == 776); assert(capacity(1024 | (2 << 11)) == 3072);
    assert(capacity(1025) == 0 && capacity(4 | (3 << 11)) == 0);
    assert(packetFits(6, 24)); assert(!packetFits(7, 24)); assert(!packetFits(UINT_MAX, 3072));
}
static void checkedAllocation() {
    assert(allocationBytes(64, 12, 8) == 160);
    assert(allocationBytes(64, 12, 0) == 0 && allocationBytes(64, 12, 9) == 0);
    assert(allocationBytes(SIZE_MAX - 3, 12, 1) == 0);
    assert(allocationBytes(64, SIZE_MAX, 8) == 0);
}
static void feedbackFormat() {
    const std::array<uint8_t, 4> valid{0x33, 0x83, 0x05, 0x00};
    const auto decoded = decodeFeedback(valid.data(), 4, 0);
    assert(decoded.validity == FeedbackValidity::Valid && decoded.raw == 361267);
    assert(decodeFeedback(valid.data(), 3, 0).validity == FeedbackValidity::Length);
    assert(decodeFeedback(valid.data(), 4, -18).validity == FeedbackValidity::CompletionError);
    const std::array<uint8_t, 4> zero{}; assert(decodeFeedback(zero.data(), 4, 0).validity == FeedbackValidity::Zero);
    const std::array<uint8_t, 4> invalid{1, 0, 0, 0}; assert(decodeFeedback(invalid.data(), 4, 0).validity == FeedbackValidity::Implausible);
    const std::array<uint8_t, 4> reserved{255, 255, 255, 255}; assert(decodeFeedback(reserved.data(), 4, 0).validity == FeedbackValidity::Reserved);
}
static void exactNominal() {
    Scheduler scheduler; uint32_t frames = 0;
    for (unsigned i = 0; i < 8000; ++i) assert(scheduler.next(1, 0, frames));
    assert(scheduler.frames == 44100 && scheduler.remainder == 0);
    assert(!scheduler.next(0, 0, frames) && !scheduler.next(32768, 0, frames));
}
static void feedbackSwitch() {
    Scheduler scheduler; uint32_t count = 0;
    for (unsigned i = 0; i < 7; ++i) assert(scheduler.next(1, 0, count));
    const uint64_t remainder = scheduler.remainder, generated = scheduler.frames;
    assert(scheduler.next(1, 361267, count));
    assert(count == (remainder + 361267ULL * 8000) / Scheduler::denominator);
    assert(scheduler.frames == generated + count);
}
static void silenceHasNoUnusedByteLeak() {
    std::array<uint8_t, 128> buffer; buffer.fill(0xa5);
    ZeroSource source; source.fill(buffer.data(), 6);
    assert(source.generated == 6 && source.inspect(buffer.data(), 24));
    assert(buffer[24] == 0xa5);
    buffer[0] = 1; assert(!source.inspect(buffer.data(), 24));
    source.fill(buffer.data(), 6); assert(!source.inspect(buffer.data(), 24));
}
static void identities() {
    Identity identity; assert(identity.submit(17, 23, 1)); assert(!identity.submit(17, 23, 2));
    assert(!identity.complete(17, 24)); assert(identity.outstanding);
    assert(identity.complete(17, 23)); assert(!identity.complete(17, 23));
    assert(identity.submit(17, 23, 2)); assert(!identity.discardRequested);
}
static void stickyIntegrity() {
    Integrity integrity; assert(integrity.valid()); integrity.compromise(PacketError); assert(!integrity.valid());
    integrity.compromise(0); assert(!integrity.valid()); integrity.compromise(UnknownCompletion);
    assert(integrity.reasons == (PacketError | UnknownCompletion));
}
static void lifecycle() {
    State state = State::Idle; assert(!transition(state, State::FeedbackLocked)); assert(state == State::Idle);
    for (State next : {State::Configured, State::Starting, State::Bootstrap, State::FeedbackLocked,
        State::StopRequested, State::Draining, State::Stopped}) assert(transition(state, next));
    assert(!transition(state, State::Bootstrap));
}
static void boundedFeedbackAge() {
    assert(!feedbackExpired(1000, 0, 100999, 20000)); assert(feedbackExpired(1000, 0, 101000, 20000));
    assert(!feedbackExpired(1000, 5000, 25000, 20000)); assert(feedbackExpired(1000, 5000, 25001, 20000));
}
static void completionEvidence() {
    assert(outPacketProblem(0, 0, 24, 24, false) == 0);
    assert(outPacketProblem(-18, 0, 24, 24, false) == UrbError);
    assert(outPacketProblem(0, -18, 24, 24, false) == PacketError);
    assert(outPacketProblem(0, 0, 24, 0, false) == ShortPacket);
    assert(outPacketProblem(-2, -2, 24, 0, true) == 0);
}
int main() {
    intervalAndCapacity(); checkedAllocation(); feedbackFormat(); exactNominal(); feedbackSwitch();
    silenceHasNoUnusedByteLeak(); identities(); stickyIntegrity(); lifecycle(); boundedFeedbackAge(); completionEvidence();
}
