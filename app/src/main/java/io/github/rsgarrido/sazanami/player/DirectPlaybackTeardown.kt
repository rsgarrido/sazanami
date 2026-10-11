package io.github.rsgarrido.sazanami.player

import kotlinx.coroutines.CompletableDeferred

/** A recreated service cannot prepare normal audio ahead of its predecessor's USB restoration. */
internal class DirectTeardownLease {
    private val done=CompletableDeferred<Unit>()
    var safe: Boolean?=null;private set
    fun complete(value: Boolean) { safe=value;done.complete(Unit) }
    suspend fun awaitSafe(): Boolean { done.await();return safe==true }
}
internal object DirectPlaybackTeardown {
    var latest: DirectTeardownLease?=null;private set
    fun begin()=DirectTeardownLease().also { latest=it }
}
