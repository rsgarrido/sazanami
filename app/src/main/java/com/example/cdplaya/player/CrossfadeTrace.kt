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
        // Local JVM tests use the Android stub implementation of Log. Production diagnostics
        // stay opt-in through Android's normal loggability gate; no envelope frames are logged.
        runCatching {
            if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, message)
        }
    }
}
