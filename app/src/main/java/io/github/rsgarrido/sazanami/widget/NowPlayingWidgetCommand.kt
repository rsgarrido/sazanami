package io.github.rsgarrido.sazanami.widget

import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.rsgarrido.sazanami.player.PlaybackService
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal enum class WidgetPlaybackCommand { PREVIOUS, PLAY_PAUSE, NEXT }

internal interface WidgetCommandTarget {
    val hasUsableCurrentItem: Boolean
    val playWhenReady: Boolean
    fun isCommandAvailable(command: Int): Boolean
    fun play()
    fun pause()
    fun seekToPrevious()
    fun seekToNextMediaItem()
}

internal fun dispatchWidgetCommand(
    target: WidgetCommandTarget,
    command: WidgetPlaybackCommand
): Boolean {
    if (!target.hasUsableCurrentItem) return false
    return when (command) {
        WidgetPlaybackCommand.PREVIOUS -> {
            if (!target.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) return false
            target.seekToPrevious()
            true
        }
        WidgetPlaybackCommand.PLAY_PAUSE -> {
            if (!target.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) return false
            // playWhenReady is deliberately sampled at click time, including while buffering.
            if (target.playWhenReady) target.pause() else target.play()
            true
        }
        WidgetPlaybackCommand.NEXT -> {
            if (!target.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) return false
            target.seekToNextMediaItem()
            true
        }
    }
}

internal enum class WidgetReadiness { READY, WAITING, TIMED_OUT }

internal class WidgetPlaybackReadiness(hasCurrentItem: Boolean) {
    var state: WidgetReadiness = if (hasCurrentItem) WidgetReadiness.READY else WidgetReadiness.WAITING
        private set

    fun onPlayerState(hasCurrentItem: Boolean): WidgetReadiness {
        if (state == WidgetReadiness.WAITING && hasCurrentItem) state = WidgetReadiness.READY
        return state
    }

    fun onTimeout(): WidgetReadiness {
        if (state == WidgetReadiness.WAITING) state = WidgetReadiness.TIMED_OUT
        return state
    }
}

private class ControllerCommandTarget(private val controller: MediaController) : WidgetCommandTarget {
    override val hasUsableCurrentItem: Boolean
        get() = controller.mediaItemCount > 0 && controller.currentMediaItem != null
    override val playWhenReady: Boolean get() = controller.playWhenReady
    override fun isCommandAvailable(command: Int) = controller.isCommandAvailable(command)
    override fun play() = controller.play()
    override fun pause() = controller.pause()
    override fun seekToPrevious() = controller.seekToPrevious()
    override fun seekToNextMediaItem() = controller.seekToNextMediaItem()
}

internal object NowPlayingWidgetCommandClient {
    suspend fun perform(context: Context, command: WidgetPlaybackCommand): Boolean =
        withContext(Dispatchers.Main.immediate) {
        val token = SessionToken(
            context.applicationContext,
            ComponentName(context.applicationContext, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        try {
            val controller = withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) { future.awaitValue() }
                ?: return@withContext false
            if (!awaitUsableCurrentItem(controller)) return@withContext false
            dispatchWidgetCommand(ControllerCommandTarget(controller), command)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            MediaController.releaseFuture(future)
        }
    }

    private suspend fun awaitUsableCurrentItem(controller: MediaController): Boolean {
        if (ControllerCommandTarget(controller).hasUsableCurrentItem) return true
        return withTimeoutOrNull(READY_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        if (controller.mediaItemCount > 0 && controller.currentMediaItem != null) {
                            controller.removeListener(this)
                            if (continuation.isActive) continuation.resume(true)
                        }
                    }
                }
                controller.addListener(listener)
                continuation.invokeOnCancellation { controller.removeListener(listener) }
                // Close the listener-registration race without sleeping.
                if (controller.mediaItemCount > 0 && controller.currentMediaItem != null) {
                    controller.removeListener(listener)
                    if (continuation.isActive) continuation.resume(true)
                }
            }
        } ?: false
    }

    private const val CONNECT_TIMEOUT_MILLIS = 4_000L
    private const val READY_TIMEOUT_MILLIS = 4_000L
}

private suspend fun <T> ListenableFuture<T>.awaitValue(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            runCatching { get() }
                .onSuccess { value -> if (continuation.isActive) continuation.resume(value) }
                .onFailure { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        },
        Executor { runnable -> runnable.run() }
    )
    continuation.invokeOnCancellation { cancel(false) }
}

class PreviousWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        NowPlayingWidgetCommandClient.perform(context, WidgetPlaybackCommand.PREVIOUS)
    }
}

class PlayPauseWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        NowPlayingWidgetCommandClient.perform(context, WidgetPlaybackCommand.PLAY_PAUSE)
    }
}

class NextWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        NowPlayingWidgetCommandClient.perform(context, WidgetPlaybackCommand.NEXT)
    }
}
