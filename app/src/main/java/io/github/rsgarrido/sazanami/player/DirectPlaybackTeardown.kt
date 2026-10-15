package io.github.rsgarrido.sazanami.player

import kotlinx.coroutines.CompletableDeferred

/** A recreated service cannot prepare normal audio ahead of its predecessor's USB restoration. */
internal class DirectTeardownLease {
    private val done=CompletableDeferred<Unit>()
    @Volatile var safe: Boolean?=null;private set
    @Synchronized fun complete(value: Boolean) {
        // One final, joined outcome. Obsolete completion callbacks cannot rewrite it.
        if(done.isCompleted) return
        safe=value;done.complete(Unit)
    }
    suspend fun awaitSafe(): Boolean { done.await();return safe==true }
}
internal object DirectPlaybackTeardown {
    @Volatile var latest: DirectTeardownLease?=null;private set
    fun begin()=DirectTeardownLease().also { latest=it }
}
