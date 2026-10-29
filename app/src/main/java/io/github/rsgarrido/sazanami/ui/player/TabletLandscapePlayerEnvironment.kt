package io.github.rsgarrido.sazanami.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlin.math.floor
import kotlin.math.min

/** Platform facts only; the resolver separately checks the real, inset-adjusted player host. */
@Immutable
internal data class TabletLandscapePlayerEnvironment(
    val deviceKind: PlayerDeviceKind,
    val smallestScreenWidthDp: Int
)

/** Pure snapshot makes the conservative device-class boundary independently testable. */
internal data class TabletLandscapePlatformSnapshot(
    val configurationSmallestWidthDp: Int,
    val maximumWindowSmallestWidthDp: Int,
    val hasActivity: Boolean,
    val isNormalUiMode: Boolean,
    val hasHingeAngleSensor: Boolean,
    val hasPcFeature: Boolean,
    val isDefaultDisplay: Boolean,
    val isInMultiWindowMode: Boolean,
    val isInPictureInPictureMode: Boolean
)

/**
 * Initial development policy, not a claim that Android can identify physical tablets universally.
 * Both the configured screen class and maximum potential window must qualify. Known hinged,
 * desktop, external-display, and multi-window contexts are deliberately excluded. Rejecting all
 * multi-window contexts also rejects tablet split screen because public APIs do not reliably
 * distinguish it from freeform/desktop windows. This isolated restriction can be revisited later.
 *
 * A foldable without a reported hinge sensor, or a desktop mode reporting ordinary tablet facts,
 * cannot be conclusively distinguished without a future window-device policy. Actual usable host
 * dimensions remain mandatory; maximum metrics are never used as the player's layout dimensions.
 */
internal fun classifyTabletLandscapePlayerEnvironment(
    snapshot: TabletLandscapePlatformSnapshot
): TabletLandscapePlayerEnvironment {
    val smallest = min(
        snapshot.configurationSmallestWidthDp,
        snapshot.maximumWindowSmallestWidthDp
    ).coerceAtLeast(0)
    val kind = when {
        !snapshot.hasActivity || smallest == 0 -> PlayerDeviceKind.UNKNOWN
        snapshot.hasHingeAngleSensor -> PlayerDeviceKind.FOLDABLE
        snapshot.hasPcFeature || !snapshot.isDefaultDisplay -> PlayerDeviceKind.DESKTOP
        !snapshot.isNormalUiMode || snapshot.isInMultiWindowMode ||
            snapshot.isInPictureInPictureMode -> PlayerDeviceKind.UNKNOWN
        smallest < 600 -> PlayerDeviceKind.PHONE
        else -> PlayerDeviceKind.TABLET
    }
    return TabletLandscapePlayerEnvironment(kind, smallest)
}

/**
 * Observe configuration/window-mode facts each composition, but query maximum metrics and classify
 * only when those facts change. Primitive configuration keys avoid retaining a mutable Configuration
 * snapshot. Actual host fit remains uncached in the separate resolver.
 */
@Composable
internal fun rememberTabletLandscapePlayerEnvironment(): TabletLandscapePlayerEnvironment {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val activity = remember(context) { context.findPlayerActivity() }
    val density = context.resources.displayMetrics.density
    val displayId = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.display?.displayId
        } else {
            @Suppress("DEPRECATION")
            val legacyDisplayId = activity?.windowManager?.defaultDisplay?.displayId
            legacyDisplayId
        }
    }.getOrNull()
    val multiWindow = activity?.isInMultiWindowMode == true
    val pictureInPicture = activity?.isInPictureInPictureMode == true
    val (hasHingeAngleSensor, hasPcFeature) = remember(context) {
        val features = context.packageManager
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            features.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)) to
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                features.hasSystemFeature(PackageManager.FEATURE_PC))
    }
    val smallestWidth = configuration.smallestScreenWidthDp
    val uiMode = configuration.uiMode
    return remember(
        context, activity, smallestWidth, configuration.screenWidthDp, configuration.screenHeightDp,
        configuration.orientation, configuration.screenLayout, configuration.densityDpi, density,
        uiMode, displayId, multiWindow, pictureInPicture, hasHingeAngleSensor, hasPcFeature
    ) {
        val maximumSmallestWidthDp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val bounds = context.getSystemService(WindowManager::class.java)
                    .maximumWindowMetrics.bounds
                if (density.isFinite() && density > 0f) {
                    floor(min(bounds.width(), bounds.height()) / density).toInt()
                } else {
                    0
                }
            }.getOrDefault(0)
        } else {
            // Older supported Android versions have no platform WindowMetrics. The configured
            // smallest width includes any compatibility-window restriction.
            smallestWidth
        }
        classifyTabletLandscapePlayerEnvironment(
            TabletLandscapePlatformSnapshot(
                configurationSmallestWidthDp = smallestWidth,
                maximumWindowSmallestWidthDp = maximumSmallestWidthDp,
                hasActivity = activity != null,
                isNormalUiMode = uiMode and Configuration.UI_MODE_TYPE_MASK ==
                    Configuration.UI_MODE_TYPE_NORMAL,
                hasHingeAngleSensor = hasHingeAngleSensor,
                hasPcFeature = hasPcFeature,
                isDefaultDisplay = displayId == Display.DEFAULT_DISPLAY,
                isInMultiWindowMode = multiWindow,
                isInPictureInPictureMode = pictureInPicture
            )
        )
    }
}

private fun Context.findPlayerActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) return null
        current = base
    }
    return current as? Activity
}
