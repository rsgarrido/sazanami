package com.example.cdplaya.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal class ExpandedPlayerOrientationSession(
    private val readOrientation: () -> Int,
    private val writeOrientation: (Int) -> Unit,
    private val portraitOrientation: Int
) {
    private var priorOrientation: Int? = null

    fun enter() {
        if (priorOrientation != null) return
        val prior = readOrientation()
        priorOrientation = prior
        if (prior != portraitOrientation) writeOrientation(portraitOrientation)
    }

    fun exit() {
        val prior = priorOrientation ?: return
        priorOrientation = null
        if (readOrientation() != prior) writeOrientation(prior)
    }
}

@Composable
fun ExpandedPlayerPortraitOrientationEffect(isExpandedPlayerVisible: Boolean) {
    val activity = LocalContext.current.findActivity()
    val session = remember(activity) {
        activity?.let {
            ExpandedPlayerOrientationSession(
                readOrientation = { it.requestedOrientation },
                writeOrientation = { value -> it.requestedOrientation = value },
                portraitOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            )
        }
    }
    DisposableEffect(session, isExpandedPlayerVisible) {
        if (isExpandedPlayerVisible) session?.enter()
        onDispose {
            if (isExpandedPlayerVisible) session?.exit()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
