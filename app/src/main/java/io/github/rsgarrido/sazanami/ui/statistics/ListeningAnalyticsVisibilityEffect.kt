package io.github.rsgarrido.sazanami.ui.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState

@Composable
internal fun ListeningAnalyticsVisibilityEffect(
    isVisible: Boolean,
    onActiveChanged: (Boolean) -> Unit
) {
    val currentCallback = rememberUpdatedState(onActiveChanged)
    LaunchedEffect(isVisible) {
        currentCallback.value(isVisible)
    }
    DisposableEffect(Unit) {
        onDispose {
            currentCallback.value(false)
        }
    }
}
