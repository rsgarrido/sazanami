package io.github.rsgarrido.sazanami.player.audio

import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-local publication point for service-owned audio facts.
 *
 * PlaybackService remains authoritative. Consumers only receive immutable snapshots and never
 * receive an ExoPlayer, route callback, Format, or AudioManager object.
 */
internal object AdvancedAudioRuntimeBridge {
    private val _state = MutableStateFlow(AudioOutputRuntimeSnapshot())
    val state: StateFlow<AudioOutputRuntimeSnapshot> = _state.asStateFlow()

    fun onPlayerConnected(preference: AudioOffloadPreference) {
        _state.update { current ->
            current.copy(
                isPlayerConnected = true,
                offloadState = AudioOffloadRuntimeState.create(
                    requestedPreference = preference,
                    isOffloadedPlayback = current.offloadState.isOffloadedPlayback,
                    isSleepingForOffload = current.offloadState.isSleepingForOffload,
                    knownCompatibilityConstraints =
                        current.offloadState.knownCompatibilityConstraints
                )
            )
        }
    }

    fun updateOffloadPreference(preference: AudioOffloadPreference) {
        _state.update { current ->
            current.copy(
                offloadState = AudioOffloadRuntimeState.create(
                    requestedPreference = preference,
                    isOffloadedPlayback = current.offloadState.isOffloadedPlayback,
                    isSleepingForOffload = current.offloadState.isSleepingForOffload,
                    knownCompatibilityConstraints =
                        current.offloadState.knownCompatibilityConstraints
                )
            )
        }
    }

    fun updateOffloadPlayback(isOffloadedPlayback: Boolean) {
        _state.update { current ->
            current.copy(
                offloadState = AudioOffloadRuntimeState.create(
                    requestedPreference = current.offloadState.requestedPreference,
                    isOffloadedPlayback = isOffloadedPlayback,
                    isSleepingForOffload = current.offloadState.isSleepingForOffload,
                    knownCompatibilityConstraints =
                        current.offloadState.knownCompatibilityConstraints
                )
            )
        }
    }

    fun updateSleepingForOffload(isSleepingForOffload: Boolean) {
        _state.update { current ->
            current.copy(
                offloadState = AudioOffloadRuntimeState.create(
                    requestedPreference = current.offloadState.requestedPreference,
                    isOffloadedPlayback = current.offloadState.isOffloadedPlayback,
                    isSleepingForOffload = isSleepingForOffload,
                    knownCompatibilityConstraints =
                        current.offloadState.knownCompatibilityConstraints
                )
            )
        }
    }

    fun updateSourceFormat(sourceFormat: AudioSourceFormat?) {
        _state.update { current -> current.copy(sourceFormat = sourceFormat) }
    }

    fun updateRouteInfo(routeInfo: AudioRouteInfo) {
        _state.update { current -> current.copy(routeInfo = routeInfo) }
    }

    fun updateAudioSessionId(audioSessionId: Int?) {
        _state.update { current -> current.copy(audioSessionId = audioSessionId) }
    }

    fun updateEqualizerRuntimeState(
        equalizerRuntimeState: EqualizerRuntimeState
    ) {
        _state.update { current ->
            val constraints = if (
                equalizerRuntimeState.requiresDecodedPcm
            ) {
                AudioOffloadRuntimeState.DEFAULT_COMPATIBILITY_CONSTRAINTS +
                    AudioCompatibilityConstraint.EQUALIZER_REQUIRES_DECODED_PCM
            } else {
                AudioOffloadRuntimeState.DEFAULT_COMPATIBILITY_CONSTRAINTS
            }
            current.copy(
                equalizerRuntimeState = equalizerRuntimeState,
                offloadState = current.offloadState.copy(
                    knownCompatibilityConstraints = constraints
                )
            )
        }
    }

    fun disconnect() {
        _state.value = AudioOutputRuntimeSnapshot()
    }
}
