package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.player.usb.*
import org.junit.Assert.*
import org.junit.Test

class DirectSourceStartTest {
    private fun progress(phase: UsbFullTransportPhase=UsbFullTransportPhase.STARTING,
        position: Long=0,epoch: Long=0,serial: Long=0,seekable: Boolean=true)=UsbFullProgress(
        0,480000,48000,phase,mapOf("sourcePositionFrame" to position,"seekSourceEpoch" to epoch,
            "confirmedSeekSerial" to serial,"sourceSeekCapable" to if(seekable) 1L else 0L))

    @Test fun zeroStartUsesRequestedIntentAtTheNativeBindBoundary() {
        for(playing in listOf(false,true)) {
            val start=DirectSourceStart(0,playing)
            var bound: UsbFullControlIntent?=null
            start.control.bind({ bound=it;true },{})
            assertEquals(playing,bound!!.play)
            assertEquals(0L,bound!!.seeks)
            assertFalse(start.positionPending)
            assertEquals(playing,start.playWhenReady)
        }
    }

    @Test fun nonzeroPlayingStartHoldsAdmissionUntilValidatedQuietSeekConfirmation() {
        val start=DirectSourceStart(3000,true)
        assertFalse(start.control.playWhenReady)
        start.stagePosition(progress())
        var bound: UsbFullControlIntent?=null
        start.control.bind({ bound=it;true },{})
        assertFalse(bound!!.play)
        assertEquals(144000L,bound!!.targetFrame)
        assertEquals(1L,bound!!.seeks)
        val serial=bound!!.seekSerial
        start.confirmPosition(progress(UsbFullTransportPhase.SEEK_PRIMING,serial=serial-1))
        assertTrue(start.positionPending)
        assertFalse(bound!!.play)
        // A Play request during initial seeking still cannot admit samples from frame zero.
        assertTrue(start.requestPlay(true));assertFalse(bound!!.play)
        start.confirmPosition(progress(UsbFullTransportPhase.PAUSED,144000,1,serial))
        assertFalse(start.positionPending)
        assertTrue(bound!!.play)
        assertEquals(1L,bound!!.seeks)
    }

    @Test fun nonzeroPausedStartAndLaterPlayRetainTheNewSourcesTarget() {
        val start=DirectSourceStart(3000,false)
        start.stagePosition(progress())
        val serial=start.control.intent().seekSerial
        start.control.bind({true},{})
        start.confirmPosition(progress(UsbFullTransportPhase.PAUSED,144000,1,serial))
        assertFalse(start.positionPending);assertFalse(start.control.playWhenReady)
        assertTrue(start.requestPlay(true));assertTrue(start.control.playWhenReady)
        assertEquals(144000L,start.control.intent().targetFrame)
    }

    @Test fun nonseekableInitialPositionFailsClosedInsteadOfStartingAtZero() {
        val start=DirectSourceStart(3000,true)
        start.stagePosition(progress(seekable=false))
        var stops=0
        start.control.bind({ fail("Failed initial position must never publish a playing command");true },{ stops++ })
        assertEquals("DIRECT_INITIAL_POSITION_UNSUPPORTED",start.failure)
        assertTrue(start.control.stopped);assertFalse(start.control.playWhenReady)
        assertEquals(1,stops)
    }

    @Test fun wrongInitialConfirmationCannotReleaseAudioAdmission() {
        for(confirmation in listOf(progress(UsbFullTransportPhase.PAUSED,144001,1,2),
            progress(UsbFullTransportPhase.PLAYING,144000,1,2),progress(UsbFullTransportPhase.PAUSED,144000,0,2))) {
            val start=DirectSourceStart(3000,true)
            start.stagePosition(progress())
            try { start.confirmPosition(confirmation);fail("Unproven initial position accepted") }
            catch(_: IllegalArgumentException) { }
            assertTrue(start.positionPending);assertFalse(start.control.playWhenReady)
        }
    }

    @Test fun supersedingSeekAndPauseStayOnOneMailboxUntilLatestConfirmation() {
        val start=DirectSourceStart(3000,true)
        start.stagePosition(progress())
        val oldSerial=start.control.intent().seekSerial
        assertTrue(start.requestSeek(48000))
        val latestSerial=start.control.intent().seekSerial
        assertTrue(start.requestPlay(false))
        start.confirmPosition(progress(UsbFullTransportPhase.PAUSED,144000,1,oldSerial))
        assertTrue(start.positionPending)
        start.confirmPosition(progress(UsbFullTransportPhase.PAUSED,48000,2,latestSerial))
        assertFalse(start.positionPending);assertFalse(start.control.playWhenReady)
    }

    @Test fun initialSeekRejectionAndStopCannotResumeAnOldMailbox() {
        val start=DirectSourceStart(3000,true)
        start.control.bind({false},{})
        start.stagePosition(progress())
        assertEquals("DIRECT_INITIAL_SEEK_REJECTED",start.failure)
        assertTrue(start.control.stopped);assertFalse(start.requestPlay(true))
        val successor=DirectSourceStart(0,false)
        successor.control.bind({true},{})
        assertFalse(successor.control.playWhenReady)
    }
}
