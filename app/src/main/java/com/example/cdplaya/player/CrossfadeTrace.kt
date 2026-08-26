package com.example.cdplaya.player

import android.util.Log

/** Concise production diagnostics for one automatic crossfade decision path. */
internal object CrossfadeTrace {
    private const val TAG = "CrossfadeTrace"

    internal var sinkForTest: ((String) -> Unit)? = null

    fun log(message: String) {
        val sink = sinkForTest
        if (sink != null) {
            sink(message)
            return
        }
        // Local JVM tests use the Android stub implementation of Log.
        runCatching { Log.d(TAG, message) }
    }
}
