package io.github.rsgarrido.sazanami.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal data class SystemBarPresentation(
    val statusBarsVisible: Boolean,
    val navigationBarsVisible: Boolean,
    val behavior: Int,
    val lightStatusBars: Boolean,
    val lightNavigationBars: Boolean
)

/** Capture the owning Activity's presentation once per opening; never change that Activity. */
@Composable
internal fun rememberPlayerSystemBarPresentation(opening: Any): SystemBarPresentation? {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return null
    val window = activity.window
    fun capture(): SystemBarPresentation? {
        if (activity.window !== window || !view.isAttachedToWindow ||
            view.rootView !== window.decorView || view.windowToken == null ||
            view.windowToken != window.decorView.windowToken
        ) return null
        return readSystemBarPresentation(window)
    }
    val presentation = remember(opening, view, window) { mutableStateOf(capture()) }
    // Normally captured above, before Dialog creation. If the host is not ready yet,
    // wait for real insets instead of inventing visibility or remembering null forever.
    LaunchedEffect(presentation) {
        while (presentation.value == null) {
            withFrameNanos { }
            presentation.value = capture()
        }
    }
    return presentation.value
}

internal fun readSystemBarPresentation(window: Window): SystemBarPresentation? {
    val view = window.decorView
    if (!view.isAttachedToWindow || view.windowToken == null || view.rootView !== view) return null
    val rootInsets = view.rootWindowInsets ?: return null
    // API 29 needs this View's window visibility as well as the raw inset dimensions.
    val insets = WindowInsetsCompat.toWindowInsetsCompat(rootInsets, view)
    val controller = WindowCompat.getInsetsController(window, view)
    return SystemBarPresentation(
        statusBarsVisible = insets.isVisible(WindowInsetsCompat.Type.statusBars()),
        navigationBarsVisible = insets.isVisible(WindowInsetsCompat.Type.navigationBars()),
        behavior = controller.systemBarsBehavior,
        lightStatusBars = controller.isAppearanceLightStatusBars,
        lightNavigationBars = controller.isAppearanceLightNavigationBars
    )
}

internal fun applySystemBarPresentation(controller: WindowInsetsControllerCompat, presentation: SystemBarPresentation) {
    controller.systemBarsBehavior = presentation.behavior
    controller.isAppearanceLightStatusBars = presentation.lightStatusBars
    controller.isAppearanceLightNavigationBars = presentation.lightNavigationBars
    if (presentation.statusBarsVisible) controller.show(WindowInsetsCompat.Type.statusBars())
    else controller.hide(WindowInsetsCompat.Type.statusBars())
    if (presentation.navigationBarsVisible) controller.show(WindowInsetsCompat.Type.navigationBars())
    else controller.hide(WindowInsetsCompat.Type.navigationBars())
}

/** A Dialog has its own controller. Preserve the source presentation through focus acquisition. */
@Composable
internal fun PreserveDialogSystemBarsEffect(window: Window?, presentation: SystemBarPresentation?) {
    DisposableEffect(window, presentation) {
        if (window == null || presentation == null) {
            onDispose {}
        } else {
            val view = window.decorView
            val controller = WindowCompat.getInsetsController(window, view)
            val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
                if (focused) applySystemBarPresentation(controller, presentation)
            }
            val observer = view.viewTreeObserver
            observer.addOnWindowFocusChangeListener(listener)
            applySystemBarPresentation(controller, presentation)
            onDispose {
                if (observer.isAlive) observer.removeOnWindowFocusChangeListener(listener)
                else view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
                // This window is being discarded. Do not show bars on the still-mounted player.
            }
        }
    }
}

@Composable
fun ImmersiveSystemBarsEffect(
    isImmersive: Boolean
) {
    val view = LocalView.current

    DisposableEffect(
        isImmersive,
        view
    ) {
        val window = view.context.findActivity()?.window

        if (window == null) {
            onDispose {}
        } else {
            val controller = WindowCompat.getInsetsController(
                window,
                view
            )

            fun applyPolicy() {
                if (isImmersive) {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            val decor = window.decorView
            val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
                if (focused && view.context.findActivity()?.window === window && decor.isAttachedToWindow) applyPolicy()
            }
            var focusObserver: ViewTreeObserver? = null
            fun removeFocusListener() {
                val observer = focusObserver ?: return
                if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focusListener)
                else decor.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
                focusObserver = null
            }
            fun registerFocusListener() {
                removeFocusListener()
                if (decor.isAttachedToWindow) {
                    decor.viewTreeObserver.also {
                        it.addOnWindowFocusChangeListener(focusListener)
                        focusObserver = it
                    }
                }
            }
            val attachmentListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = registerFocusListener()
                override fun onViewDetachedFromWindow(v: View) = removeFocusListener()
            }
            // Follow the Activity decor's observer across attachment, never the Dialog's root.
            decor.addOnAttachStateChangeListener(attachmentListener)
            registerFocusListener()
            applyPolicy()

            onDispose {
                decor.removeOnAttachStateChangeListener(attachmentListener)
                removeFocusListener()
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
