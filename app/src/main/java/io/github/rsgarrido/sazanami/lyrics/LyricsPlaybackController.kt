package io.github.rsgarrido.sazanami.lyrics

import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgressUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

fun interface LyricsPositionSource {
    fun currentPositionMs(): Long
}

class LyricsPlaybackController(
    private val repository: LocalLyricsRepository,
    playbackState: StateFlow<PlaybackUiState>,
    private val positionSource: LyricsPositionSource,
    private val scope: CoroutineScope,
    private val tickerIntervalMs: Long = DEFAULT_TICKER_INTERVAL_MS,
    private val monotonicTimeMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val authoritativeProgress: StateFlow<PlaybackProgressUiState>? = null
) {
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow<LyricsPlaybackUiState>(
        LyricsPlaybackUiState.Hidden
    )
    val uiState: StateFlow<LyricsPlaybackUiState> = _uiState

    private var currentSong: Song? = null
    private var currentIdentity: SongLyricsIdentity? = null
    private var lookupGeneration = 0L
    private var currentContent: Content = Content.None
    private var isVisible = false
    private var isPlaying = playbackState.value.isPlaying
    private var autoFollowEnabled = true
    private var followRequestId = 0L
    private var lookupJob: Job? = null
    private var tickerJob: Job? = null
    private var pendingSeek: PendingSeek? = null

    init {
        scope.launch {
            playbackState.collect { state ->
                if (currentIdentity != state.currentSong?.toLyricsIdentity()) {
                    onSongChanged(state.currentSong)
                }
                if (isPlaying != state.isPlaying) {
                    isPlaying = state.isPlaying
                    updatePositionNow()
                    updateTicker()
                }
            }
        }
        scope.launch {
            repository.invalidations.collect { change ->
                val identity = currentIdentity ?: return@collect
                if (change.trackKey == null || change.trackKey == identity.trackKey) {
                    startLookup(currentSong, refreshIndex = false)
                }
            }
        }
        authoritativeProgress?.let { progress ->
            scope.launch {
                progress.collect {
                    if (isVisible) updatePositionNow()
                }
            }
        }
    }

    fun setVisible(visible: Boolean) {
        if (isVisible == visible) return
        isVisible = visible
        if (visible) {
            autoFollowEnabled = true
            updatePositionNow()
        }
        publish()
        updateTicker()
    }

    fun suspendAutoFollow() {
        if (!autoFollowEnabled) return
        autoFollowEnabled = false
        publish()
    }

    fun returnToCurrentLine() {
        autoFollowEnabled = true
        followRequestId++
        // A follow action is observable even while paused on the same cue (or during a seek).
        publish()
        updatePositionNow()
    }

    fun onSeek(positionMs: Long) {
        // Direct acceptance is not completion. Rejected and superseded requests must not
        // invent a lyric position; the service already withholds unconfirmed source epochs.
        if (authoritativeProgress?.value?.isDirect == true) {
            pendingSeek = null
            updatePositionNow()
            return
        }
        pendingSeek = PendingSeek(
            positionMs = positionMs,
            expiresAtMs = monotonicTimeMs() + PENDING_SEEK_WINDOW_MS
        )
        // A requested destination is not playback evidence. A bounded paused refresh uses the
        // same lyrics updater until the player reports the destination (or the window expires).
        updatePositionNow()
        updateTicker()
    }

    fun rescan() {
        startLookup(currentSong, refreshIndex = true)
    }

    private fun onSongChanged(song: Song?) {
        currentSong = song
        currentIdentity = song?.toLyricsIdentity()
        currentContent = if (song == null) Content.None else Content.Loading
        autoFollowEnabled = true
        followRequestId = 0L
        pendingSeek = null
        publish()
        startLookup(song, refreshIndex = false)
        updatePositionNow()
    }

    private fun startLookup(song: Song?, refreshIndex: Boolean) {
        lookupJob?.cancel()
        val generation = ++lookupGeneration
        val identity = currentIdentity
        if (song == null) return
        currentContent = Content.Loading
        publish()
        updateTicker()
        lookupJob = scope.launch {
            try {
                if (refreshIndex) {
                    repository.refreshIndex()
                }
                val result = repository.findLyrics(requireNotNull(identity))
                coroutineContext.ensureActive()
                if (lookupGeneration != generation || currentIdentity != identity) return@launch
                currentContent = result.toContent()
                publish()
                updatePositionNow()
                updateTicker()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (lookupGeneration == generation && currentIdentity == identity) {
                    currentContent = unavailable(LyricsUnavailableReason.SourcesUnavailable(listOf(
                        LyricsSourceIssue(identity?.trackKey.orEmpty(), LyricsSourceIssue.Kind.LOOKUP_FAILURE))))
                    publish()
                    updateTicker()
                }
            } catch (_: LinkageError) {
                if (lookupGeneration == generation && currentIdentity == identity) {
                    currentContent = unavailable(LyricsUnavailableReason.SourcesUnavailable(listOf(
                        LyricsSourceIssue(identity?.trackKey.orEmpty(), LyricsSourceIssue.Kind.METADATA_FAILURE))))
                    publish()
                    updateTicker()
                }
            }
        }
    }

    private fun updateTicker() {
        tickerJob?.cancel()
        tickerJob = null
        if (!isVisible || (!isPlaying && pendingSeek == null) || currentContent !is Content.Synced) return
        tickerJob = scope.launch {
            while (true) {
                updatePositionNow()
                if (!isPlaying && pendingSeek == null) break
                // Reuse the visible lyrics clock; Enhanced LRC benefits from a 50 ms sample bound.
                val enhanced = (currentContent as? Content.Synced)?.timings?.isNotEmpty() == true
                delay(if (enhanced) minOf(tickerIntervalMs, KARAOKE_TICKER_INTERVAL_MS) else tickerIntervalMs)
            }
        }
    }

    private fun updatePositionNow() {
        if (currentContent is Content.Synced) {
            val confirmed = authoritativeProgress?.value
            if (confirmed?.isDirect == true) {
                pendingSeek = null
                if (!confirmed.isSeeking) updatePosition(confirmed.currentPosition.toLong())
            } else {
                updateReportedPosition(positionSource.currentPositionMs())
            }
        } else {
            publish()
        }
    }

    private fun updateReportedPosition(positionMs: Long) {
        val pending = pendingSeek
        if (pending != null) {
            // Exact equality also handles paused sub-word seeks. A coarse line-level tolerance
            // could stop refresh just before a word boundary. Never withhold the real position.
            if (positionMs == pending.positionMs || monotonicTimeMs() >= pending.expiresAtMs) pendingSeek = null
        }
        updatePosition(positionMs)
    }

    private fun updatePosition(positionMs: Long) {
        val content = currentContent as? Content.Synced ?: return
        val active = ActiveLyricResolver.resolve(content.document.cues, positionMs)
        val karaoke = if (active == null) emptyList() else content.timings[active.timestampMs].orEmpty().map { (index, timing) ->
            KaraokeLineState(index, KaraokeResolver.resolve(timing, positionMs))
        }
        content.karaoke.value = karaoke
        if (active != content.activeGroup) {
            currentContent = content.copy(activeGroup = active)
            publish()
        } else if (isVisible && _uiState.value is LyricsPlaybackUiState.Hidden) {
            publish()
        }
    }

    private fun publish() {
        _uiState.value = if (!isVisible) {
            LyricsPlaybackUiState.Hidden
        } else {
            when (val content = currentContent) {
                Content.None -> LyricsPlaybackUiState.Hidden
                Content.Loading -> currentSong?.let(LyricsPlaybackUiState::Loading)
                    ?: LyricsPlaybackUiState.Hidden
                is Content.Synced -> LyricsPlaybackUiState.Synced(
                    song = content.song,
                    lyrics = content.document,
                    activeGroup = content.activeGroup,
                    autoFollowEnabled = autoFollowEnabled,
                    sourcedLyrics = content.sourcedLyrics,
                    karaoke = content.karaoke,
                    followRequestId = followRequestId
                )
                is Content.Unsynced -> LyricsPlaybackUiState.Unsynced(
                    song = content.song,
                    lyrics = content.document,
                    sourcedLyrics = content.sourcedLyrics
                )
                is Content.Unavailable -> LyricsPlaybackUiState.Unavailable(
                    song = content.song,
                    reason = content.reason
                )
            }
        }
    }

    private fun LyricsLookupResult.toContent(): Content = when (this) {
        is LyricsLookupResult.Found -> when (val document = lyrics.document) {
            is LyricsDocument.Synced -> Content.Synced(
                song = requireNotNull(currentSong),
                document = document,
                sourcedLyrics = lyrics,
                timings = document.cues.mapIndexedNotNull { index, cue ->
                    KaraokeResolver.prepare(cue)?.let { index to it }
                }.groupBy { (index, _) -> document.cues[index].timestampMs }
            )
            is LyricsDocument.Unsynced -> Content.Unsynced(
                song = requireNotNull(currentSong),
                document = document,
                sourcedLyrics = lyrics
            )
        }
        LyricsLookupResult.NoRootsConfigured -> unavailable(
            LyricsUnavailableReason.NoRootsConfigured
        )
        LyricsLookupResult.NotFound -> unavailable(LyricsUnavailableReason.NotFound)
        is LyricsLookupResult.Ambiguous -> unavailable(
            LyricsUnavailableReason.Ambiguous(candidates)
        )
        is LyricsLookupResult.PermissionLost -> unavailable(
            LyricsUnavailableReason.PermissionLost(rootUri)
        )
        is LyricsLookupResult.RootScanError -> unavailable(
            LyricsUnavailableReason.RootScanError(rootUri)
        )
        is LyricsLookupResult.StaleFile -> unavailable(
            LyricsUnavailableReason.StaleFile(documentUri)
        )
        is LyricsLookupResult.ReadError -> unavailable(
            LyricsUnavailableReason.ReadError(documentUri)
        )
        is LyricsLookupResult.InvalidLyrics -> unavailable(
            LyricsUnavailableReason.InvalidLyrics(documentUri)
        )
        is LyricsLookupResult.Unavailable -> unavailable(LyricsUnavailableReason.SourcesUnavailable(issues))
    }

    private fun unavailable(reason: LyricsUnavailableReason): Content =
        Content.Unavailable(requireNotNull(currentSong), reason)

    private sealed interface Content {
        data object None : Content
        data object Loading : Content
        data class Synced(
            val song: Song,
            val document: LyricsDocument.Synced,
            val sourcedLyrics: SourcedLyrics,
            val activeGroup: ActiveLyricGroup? = null,
            val timings: Map<Long, List<Pair<Int, KaraokeTiming>>> = emptyMap(),
            val karaoke: MutableStateFlow<List<KaraokeLineState>> = MutableStateFlow(emptyList())
        ) : Content
        data class Unsynced(
            val song: Song,
            val document: LyricsDocument.Unsynced,
            val sourcedLyrics: SourcedLyrics
        ) : Content
        data class Unavailable(
            val song: Song,
            val reason: LyricsUnavailableReason
        ) : Content
    }

    private data class PendingSeek(
        val positionMs: Long,
        val expiresAtMs: Long
    )

    companion object {
        const val DEFAULT_TICKER_INTERVAL_MS = 100L
        const val KARAOKE_TICKER_INTERVAL_MS = 50L
        const val PENDING_SEEK_WINDOW_MS = 750L
    }
}
