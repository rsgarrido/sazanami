package io.github.rsgarrido.sazanami.player.usb

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Cancellation and disposal ownership; no Android handles, JNI, or timing assumptions. */
class UsbDetachedResourceReleaseTest {
    private val attachment=UsbAudioInspectionToken(UsbAudioDeviceIdentity("fake",1,2,3),7)
    private val owner=UsbActivationOwner(1)
    private fun terminal()=UsbSilentTransportExperimentResult(7,9,UsbSilentTransportResolution(),
        status=UsbSilentStatus.NON_AUTHORITATIVE,
        configuration=UsbAudioConfigurationResult(7,UsbAudioConfigurationResolution(),
            accessFailure=UsbClockQueryStatus.DEVICE_DISCONNECTED,
            restorationStatus=UsbConfigurationRestorationStatus.INDETERMINATE,closeStatus=UsbAudioCleanupStatus.SUCCEEDED),
        transport=UsbIsoTransportResult(7,9,mapOf("workerStopped" to 1L,"outstandingUrbsFinal" to 3L)),
        nativeMemoryReleased=true,connectionCloseRequired=true,outstandingAfterConnectionClose=0,
        nativeExecutionJoined=true,originalFlacFdClosed=true,
        flacSource=UsbFlacSourceResult(mapOf("generation" to 7L,"streamGeneration" to 9L,
            "producerStopped" to 1L,"producerJoined" to 1L,"decoderReleased" to 1L,"nativeFlacFdClosed" to 1L,"queueReleased" to 1L)))
    private fun proof(result: UsbSilentTransportExperimentResult=terminal(),detached: Boolean=true,idle: Boolean=true)=
        UsbDetachedResourceRelease.verified(owner,attachment,result,detached,
            sourceAbsent=false,inputAbsent=false,connectionAbsent=false,nativeRegistriesIdle=idle)

    @Test fun cancelledWaiterAndCancelledScopeRetainOneOwnerAndActualTerminalEvidence()=runBlocking {
        val scope=CoroutineScope(coroutineContext+SupervisorJob())
        val client=UsbFullPlaybackClient({},UsbFullPlaybackControl(),owner,attachment)
        val cleanup=CompletableDeferred<Unit>();val stop=CompletableDeferred<Unit>()
        val delivered=CompletableDeferred<UsbSilentTransportExperimentResult>();val actual=terminal()
        var owners=0
        client.launchOwned(scope) {
            owners++
            cleanup.await()
            // Publication follows real cleanup, inside the production owner's cancellation shield.
            client.complete(actual)
        }
        val waiter=launch(start=CoroutineStart.UNDISPATCHED) { delivered.complete(client.awaitResult { stop.complete(Unit) }) }
        waiter.cancel();scope.cancel()
        withTimeout(5000) { stop.await() }
        assertFalse(delivered.isCompleted);assertFalse(client.job!!.isCompleted)
        cleanup.complete(Unit)
        assertSame(actual,withTimeout(5000) { delivered.await() });waiter.join()
        assertSame(actual,client.result);assertEquals(1,owners);assertTrue(client.job!!.isCompleted)
    }
    @Test fun completionAlreadyPublishedSurvivesWaiterCancellation()=runBlocking {
        val client=UsbFullPlaybackClient({},UsbFullPlaybackControl(),owner,attachment)
        val actual=terminal();client.launchOwned(this) { client.complete(actual) }.join()
        var delivered: UsbSilentTransportExperimentResult?=null
        val waiter=launch(start=CoroutineStart.LAZY) {
            currentCoroutineContext().cancel()
            delivered=client.awaitResult {}
        }
        waiter.start();waiter.join();assertSame(actual,delivered)
        try { client.complete(actual.copy(nativeMemoryReleased=false));fail("Terminal evidence is single assignment") }
        catch (_: IllegalStateException) { }
        assertSame(actual,client.result)
        try { client.launchOwned(this) { fail("A second cleanup owner must not run") };fail("Duplicate owner accepted") }
        catch (_: IllegalStateException) { }
    }
    @Test fun absentOrIncompleteProofCannotAuthorizeAndroid() {
        val base=terminal()
        assertNotNull(proof());assertFalse(base.integratedCleanupSafe())
        assertNull(proof(detached=false));assertNull(proof(idle=false))
        for (broken in listOf(base.copy(nativeMemoryReleased=false),base.copy(nativeExecutionJoined=false),
            base.copy(originalFlacFdClosed=false),base.copy(configuration=null),base.copy(outstandingAfterConnectionClose=1),
            base.copy(configuration=base.configuration!!.copy(generation=8)),
            base.copy(configuration=base.configuration!!.copy(closeStatus=UsbAudioCleanupStatus.EXCEPTION)),
            base.copy(generation=8),base.copy(transport=base.transport!!.copy(streamGeneration=10)))) assertNull(proof(broken))
        for (field in listOf("producerStopped","producerJoined","decoderReleased","nativeFlacFdClosed","queueReleased","generation","streamGeneration"))
            assertNull(field,proof(base.copy(flacSource=base.flacSource!!.copy(counters=base.flacSource.counters-field))))
    }
    @Test fun proofIsBoundToOwnerAttachmentStreamAndUnmodifiedTerminalEvidence() {
        val result=terminal();val proof=proof(result)!!;val delivered=result.copy(detachedResourceRelease=proof)
        assertTrue(proof.matches(owner,delivered))
        assertFalse(proof.matches(UsbActivationOwner(owner.serial),delivered))
        assertFalse(proof.matches(owner,delivered.copy(streamGeneration=10)))
        assertFalse(proof.matches(owner,delivered.copy(nativeMemoryReleased=false)))
        assertFalse(proof.matches(owner,delivered.copy(generation=8)))
        assertEquals(UsbConfigurationRestorationStatus.INDETERMINATE,delivered.configuration!!.restorationStatus)
        assertEquals(UsbSilentStatus.NON_AUTHORITATIVE,delivered.status);assertFalse(delivered.flacPlaybackComplete)
    }
    @Test fun duplicateDetachIsIdempotentAndStaleAttachmentCannotDetachNewOwner() {
        val client=UsbFullPlaybackClient({},UsbFullPlaybackControl(),owner,attachment)
        client.detached(attachment.copy(generation=6));assertFalse(client.detached)
        client.detached(attachment);client.detached(attachment);assertTrue(client.detached)
        val next=UsbFullPlaybackClient({},UsbFullPlaybackControl(),UsbActivationOwner(2),attachment.copy(generation=8))
        next.detached(attachment);assertFalse(next.detached)
    }
    @Test fun detachmentBeforeUsbOpenStillRequiresJoinedSourceAndInputClosure() {
        val base=terminal().copy(transport=null,connectionCloseRequired=false,
            configuration=terminal().configuration!!.copy(closeStatus=UsbAudioCleanupStatus.NOT_ATTEMPTED))
        assertNotNull(UsbDetachedResourceRelease.verified(owner,attachment,base,true,
            sourceAbsent=false,inputAbsent=false,connectionAbsent=true,nativeRegistriesIdle=true))
        assertNull(UsbDetachedResourceRelease.verified(owner,attachment,base.copy(originalFlacFdClosed=false),true,
            sourceAbsent=false,inputAbsent=false,connectionAbsent=true,nativeRegistriesIdle=true))
    }
}
