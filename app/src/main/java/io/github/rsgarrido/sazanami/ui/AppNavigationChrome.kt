package io.github.rsgarrido.sazanami.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination
import io.github.rsgarrido.sazanami.ui.player.playerEndpointInput

/** Both presentations dispatch to the same MusicScreen action and selected destination. */
@Composable
internal fun BoxScope.AppNavigationChrome(
    layout: MusicShellLayout,
    visible: Boolean,
    selectedDestination: MainDestination,
    onDestinationSelected: (MainDestination) -> Unit
) {
    if (layout.usesRail) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.align(AbsoluteAlignment.CenterLeft).absolutePadding(
                left = layout.insets.left + AppNavigationRailMargin,
                top = layout.insets.top + AppNavigationRailMargin,
                bottom = layout.insets.bottom + AppNavigationRailMargin
            )
        ) {
            AppFloatingNavigationRail(
                selectedDestination, onDestinationSelected,
                // An exiting rail must yield input as well as its visuals to the player.
                Modifier.playerEndpointInput(visible)
            )
        }
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(220)) { height -> height } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(200)) { height -> height } + fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        ) {
            AppBottomNavigation(selectedDestination, onDestinationSelected)
        }
    }
}
