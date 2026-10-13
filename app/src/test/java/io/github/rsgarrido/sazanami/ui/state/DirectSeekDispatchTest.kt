package io.github.rsgarrido.sazanami.ui.state

import org.junit.Assert.*
import org.junit.Test

/** Exercises the same dispatch adapter used by real controls, including release before queued Slider callbacks. */
class DirectSeekDispatchTest {
    @Test fun actualControlAdapterOwnsReleaseAndDiscardsDelayedSdkChangesAndCompletions() {
        var authority=DirectScrubAuthority(true,7,0,true)
        val requests=mutableListOf<Int>();var preview: Int?=null
        val environment=DirectSeekGestureEnvironment({authority},{preview=it})
        val control=DirectSeekGestureControl(environment) {requests+=it;authority=authority.copy(seekSerial=authority.seekSerial+1)}
        control.beginDirectTouch();control.sampleDirectTouch(89983);assertTrue(requests.isEmpty())
        control.sampleDirectTouch(89792);control.releaseDirectTouch();assertEquals(89792,preview)
        control.change(89792) // Queued Material3 value after pointer-up: cannot compete with the control's target.
        control.completeDirectTouch();assertEquals(listOf(89792),requests)
        control.change(89900);control.finish();control.completeDirectTouch();assertEquals(listOf(89792),requests)
        control.beginDirectTouch();control.sampleDirectTouch(5000);control.releaseDirectTouch();control.completeDirectTouch()
        assertEquals(listOf(89792,5000),requests)
        control.beginDirectTouch();control.sampleDirectTouch(10000);control.cancel();control.releaseDirectTouch();control.completeDirectTouch()
        control.change(11000);control.finish();assertEquals(listOf(89792,5000),requests)
        control.immediate(12000);assertEquals(listOf(89792,5000,12000),requests)
    }
    private class Fixture(direct: Boolean=true) {
        var authority=DirectScrubAuthority(direct,7,0,true)
        val requests=mutableListOf<Int>()
        var preview: Int?=null
        val control=DirectSeekDispatch({authority},{position ->
            requests+=position
            if(authority.enabled) authority=authority.copy(seekSerial=authority.seekSerial+1)
        },{preview=it})
    }
    @Test fun pointerUpBeforeFinalValueCommitsOnlyAtControlCompletionAndKeepsFinalTarget() {
        val f=Fixture();f.control.begin()
        f.control.change(89983);assertEquals(89983,f.preview);assertTrue(f.requests.isEmpty())
        f.control.pointerUp();assertTrue(f.requests.isEmpty())
        f.control.change(89792);assertEquals(89792,f.preview);assertTrue(f.requests.isEmpty())
        f.control.complete();assertEquals(listOf(89792),f.requests);assertNull(f.preview)
        f.control.change(89800);f.control.complete();f.control.complete()
        assertEquals(listOf(89792),f.requests) // A stale touch callback never becomes an accessibility/immediate seek.
    }
    @Test fun independentTapsAndBackToBackPointerSequencesAreNotDropped() {
        val f=Fixture()
        repeat(3) {f.control.begin();f.control.change(1000+it);f.control.complete();f.control.pointerUp()}
        assertEquals(listOf(1000,1001,1002),f.requests)
        f.control.begin();f.control.change(2000);f.control.pointerUp()
        f.control.begin() // Second physical down before the first queued control completion.
        f.control.change(2001);f.control.complete()
        f.control.complete() // A duplicate completion must not retire the next queued gesture before its value arrives.
        f.control.change(3000);f.control.pointerUp();f.control.complete()
        assertEquals(listOf(1000,1001,1002,2001,3000),f.requests)
    }
    @Test fun cancellationAndAllAuthorityChangesRejectPendingAndLateTouchCallbacks() {
        for(change in listOf<(DirectScrubAuthority)->DirectScrubAuthority>(
            {it.copy(generation=8)},{it.copy(enabled=false)},{it.copy(allowed=false)},{it.copy(seekSerial=20)})) {
            val f=Fixture();f.control.begin();f.control.change(1000);f.control.pointerUp()
            f.authority=change(f.authority);f.control.observe();assertNull(f.preview)
            f.control.change(2000);f.control.complete();f.control.change(3000);f.control.complete()
            assertTrue(f.requests.isEmpty())
        }
        val f=Fixture();f.control.begin();f.control.change(1000);f.control.cancel()
        f.control.pointerUp();f.control.change(2000);f.control.complete();assertTrue(f.requests.isEmpty())
        f.control.begin();f.control.change(3000);f.control.complete();assertEquals(listOf(3000),f.requests)
    }
    @Test fun generationChangeCancelsOldQueuedGesturesButPermitsNewIndependentGeneration() {
        val f=Fixture();f.control.begin();f.control.pointerUp();f.control.begin();f.control.pointerUp()
        f.authority=f.authority.copy(generation=8);f.control.observe();f.control.begin()
        f.control.change(1000);f.control.complete();f.control.change(2000);f.control.complete()
        assertTrue(f.requests.isEmpty())
        f.control.change(3000);f.control.complete();assertEquals(listOf(3000),f.requests)
    }
    @Test fun accessibilityKeyboardAndExternalCommandsAreIndependentOfCancelledTouch() {
        val f=Fixture();f.control.begin();f.control.change(1000)
        f.control.immediate(5000);f.control.complete();assertEquals(listOf(5000),f.requests)
        f.control.immediate(6000);assertEquals(listOf(5000,6000),f.requests)
        f.control.keyboardInput();f.control.change(7000);f.control.complete();assertEquals(listOf(5000,6000,7000),f.requests)
        f.control.begin();f.control.change(10000)
        // A real MediaSession command goes directly to the player; it changes the serial observed by this adapter.
        f.requests+=12000;f.authority=f.authority.copy(seekSerial=f.authority.seekSerial+1)
        f.control.observe();f.control.change(11000);f.control.complete()
        assertEquals(listOf(5000,6000,7000,12000),f.requests)
    }
    @Test fun normalPlaybackKeepsEveryImmediateDeltaWithoutAnExtraCompletionSeek() {
        val f=Fixture(false);f.control.begin();f.control.change(1000);f.control.change(2000)
        f.control.pointerUp();f.control.change(3000);f.control.complete();f.control.complete()
        assertEquals(listOf(1000,2000,3000),f.requests)
        f.control.immediate(4000);assertEquals(4000,f.requests.last())
    }
}
