package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.waveform.WaveformData
import io.github.rsgarrido.sazanami.player.waveform.WaveformRepository
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun rememberExpandedPlayerWaveformData(
    currentSong: Song?,
    shouldLoad: Boolean,
    prefetchSongs: List<Song> = emptyList()
): WaveformData? {
    val appContext = LocalContext.current.applicationContext
    val repository = remember(appContext) { WaveformRepository.shared(appContext) }
    var waveformData by remember(
        currentSong?.id,
        currentSong?.filePath,
        currentSong?.uri,
        shouldLoad
    ) {
        mutableStateOf<WaveformData?>(null)
    }

    LaunchedEffect(
        currentSong?.id,
        currentSong?.filePath,
        currentSong?.uri,
        shouldLoad,
        prefetchSongs
    ) {
        if (!shouldLoad || currentSong == null) return@LaunchedEffect

        waveformData = try {
            repository.load(currentSong)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }

        if (prefetchSongs.isNotEmpty()) {
            delay(WAVEFORM_PREFETCH_DELAY_MILLIS)
            repository.prefetch(prefetchSongs)
        }
    }

    return waveformData
}

@Composable
internal fun WarmCurrentSongWaveform(
    currentSong: Song?,
    shouldWarm: Boolean
) {
    val appContext = LocalContext.current.applicationContext
    val repository = remember(appContext) { WaveformRepository.shared(appContext) }

    LaunchedEffect(
        currentSong?.id,
        currentSong?.filePath,
        currentSong?.uri,
        shouldWarm
    ) {
        if (!shouldWarm || currentSong == null) return@LaunchedEffect

        try {
            repository.load(currentSong)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Warm-up is opportunistic. The expanded player can retry normally.
        }
    }
}

private const val WAVEFORM_PREFETCH_DELAY_MILLIS = 250L

internal fun shouldLoadExpandedPlayerWaveform(
    selectedPlayerTheme: PlayerTheme,
    modernSeekbarStyle: ModernSeekbarStyle
): Boolean = when (selectedPlayerTheme) {
    PlayerTheme.DEFAULT -> modernSeekbarStyle.usesWaveformData
    PlayerTheme.POCKET_FLIP, PlayerTheme.RETRO_RACK -> true
    PlayerTheme.CLASSIC_WHEEL, PlayerTheme.POCKET_CASSETTE -> false
}
