package io.github.rsgarrido.sazanami.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkShape
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBackgroundStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlAccent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class WidgetAppearanceRevision(
    val playerTheme: PlayerTheme,
    val backgroundStyle: ModernBackgroundStyle,
    val solidColorArgb: Long,
    val artworkShape: ModernArtworkShape,
    val controlAccent: ModernControlAccent
)

internal fun interface WidgetUpdateScheduler {
    fun schedule(task: () -> Unit)
}

internal class WidgetUpdateCoalescer(
    private val scheduler: WidgetUpdateScheduler,
    private val update: () -> Unit
) {
    private var pending = false

    fun request() {
        if (pending) return
        pending = true
        scheduler.schedule {
            pending = false
            update()
        }
    }
}

private val MEANINGFUL_WIDGET_PLAYER_EVENTS = intArrayOf(
    Player.EVENT_MEDIA_ITEM_TRANSITION,
    Player.EVENT_MEDIA_METADATA_CHANGED,
    Player.EVENT_TIMELINE_CHANGED,
    Player.EVENT_IS_PLAYING_CHANGED,
    Player.EVENT_PLAY_WHEN_READY_CHANGED,
    Player.EVENT_PLAYBACK_STATE_CHANGED,
    Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
    Player.EVENT_REPEAT_MODE_CHANGED
)

internal fun isMeaningfulWidgetPlayerEvent(event: Int): Boolean =
    event in MEANINGFUL_WIDGET_PLAYER_EVENTS

internal enum class WidgetEmptyStateDisposition { PUBLISH, RETAIN_LAST_PRESENTATION }

internal fun widgetEmptyStateDisposition(
    snapshot: NowPlayingWidgetSnapshot,
    restorationComplete: Boolean
): WidgetEmptyStateDisposition = if (!snapshot.hasMedia && !restorationComplete) {
    WidgetEmptyStateDisposition.RETAIN_LAST_PRESENTATION
} else {
    WidgetEmptyStateDisposition.PUBLISH
}

internal class WidgetSnapshotDeduplicator {
    private var lastPublished: NowPlayingWidgetSnapshot? = null

    fun shouldPublish(snapshot: NowPlayingWidgetSnapshot): Boolean {
        if (snapshot == lastPublished) return false
        lastPublished = snapshot
        return true
    }

    fun reset() {
        lastPublished = null
    }
}

/** Publishes presentation state from the exact Player owned by MediaLibrarySession. */
class NowPlayingWidgetPublisher(
    context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
    restorationComplete: Boolean
) : Player.Listener {
    private val appContext = context.applicationContext
    private val store = NowPlayingWidgetSnapshotStore(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coalescer = WidgetUpdateCoalescer(
        scheduler = WidgetUpdateScheduler { task ->
            mainHandler.postDelayed({ task() }, COALESCE_MILLIS)
        },
        update = { if (attached) scope.launch { publishCurrent() } }
    )
    private var attached = false
    private var restorationComplete = restorationComplete
    private val deduplicator = WidgetSnapshotDeduplicator()
    private var appearanceObservationJob: Job? = null

    fun attach() {
        if (attached) return
        attached = true
        player.addListener(this)
        appearanceObservationJob = scope.launch {
            AppPreferencesRepository.getInstance(appContext).state
                .filter { preferences -> preferences.isLoaded }
                .map { preferences ->
                    WidgetAppearanceRevision(
                        playerTheme = preferences.selectedPlayerTheme,
                        backgroundStyle = preferences.modernPlayerAppearance.background.style,
                        solidColorArgb = preferences.modernPlayerAppearance.background.solidColorArgb,
                        artworkShape = preferences.modernPlayerAppearance.artwork.shape,
                        controlAccent = preferences.modernPlayerAppearance.controls.accent
                    )
                }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (attached) {
                        runCatching { invalidateNowPlayingWidgetPresentations(appContext) }
                    }
                }
        }
        requestUpdate()
    }

    fun requestUpdate() = coalescer.request()

    fun onQueueRestorationCompleted() {
        if (restorationComplete) return
        restorationComplete = true
        requestUpdate()
    }

    fun close() {
        attached = false
        appearanceObservationJob?.cancel()
        appearanceObservationJob = null
        player.removeListener(this)
        NowPlayingWidgetLiveState.snapshot = null
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (MEANINGFUL_WIDGET_PLAYER_EVENTS.any { event -> events.contains(event) }) requestUpdate()
    }

    private suspend fun publishCurrent() {
        if (!attached) return
        val raw = player.toNowPlayingWidgetSnapshot()
        if (
            widgetEmptyStateDisposition(raw, restorationComplete) ==
            WidgetEmptyStateDisposition.RETAIN_LAST_PRESENTATION
        ) {
            return
        }
        publishIfChanged(raw)
    }

    private suspend fun publishIfChanged(snapshot: NowPlayingWidgetSnapshot) {
        if (!deduplicator.shouldPublish(snapshot)) return
        NowPlayingWidgetLiveState.snapshot = snapshot
        store.write(snapshot)
        runCatching { invalidateNowPlayingWidgetPresentations(appContext) }
            .onFailure { deduplicator.reset() }
    }

    private companion object {
        const val COALESCE_MILLIS = 100L
    }
}
