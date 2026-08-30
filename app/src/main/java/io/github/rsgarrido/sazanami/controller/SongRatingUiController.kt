package io.github.rsgarrido.sazanami.controller

import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.SongRating
import io.github.rsgarrido.sazanami.data.SongRatingDataSource
import io.github.rsgarrido.sazanami.data.membershipKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

enum class SongRatingUiError { LOAD, SAVE, CLEAR }

data class SongRatingDialogState(
    val song: Song,
    val persistedRating: SongRating? = null,
    val selectedValue: Int? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: SongRatingUiError? = null
)

data class SongRatingUiState(
    val dialog: SongRatingDialogState? = null,
    val ratingsByReferenceKey: Map<String, Int> = emptyMap(),
    val ratingsByTrackIdentityId: Map<Long, Int> = emptyMap()
)

class SongRatingUiController(
    private val repository: SongRatingDataSource,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(SongRatingUiState())
    val state: StateFlow<SongRatingUiState> = _state.asStateFlow()

    private var dialogJob: Job? = null
    private var requestGeneration = 0L

    init {
        scope.launch {
            repository.observeRatingSnapshot()
                .catch { /* Retain the last successful shared map if Room observation fails. */ }
                .collect { snapshot ->
                    _state.value = _state.value.copy(
                        ratingsByReferenceKey = snapshot.byReferenceKey
                            .mapValues { (_, rating) -> rating.value },
                        ratingsByTrackIdentityId = snapshot.byTrackIdentityId
                            .mapValues { (_, rating) -> rating.value }
                    )
                }
        }
    }

    fun open(song: Song) {
        dialogJob?.cancel()
        val generation = ++requestGeneration
        val referenceKey = song.membershipKey()
        _state.value = _state.value.copy(dialog = SongRatingDialogState(song = song))
        dialogJob = scope.launch {
            runCatching { repository.getRatingForSong(song) }
                .onSuccess { rating ->
                    updateCurrentDialog(generation, referenceKey) { dialog ->
                        dialog.copy(
                            persistedRating = rating,
                            selectedValue = rating?.value,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .onFailure {
                    updateCurrentDialog(generation, referenceKey) { dialog ->
                        dialog.copy(isLoading = false, error = SongRatingUiError.LOAD)
                    }
                }
        }
    }

    fun close() {
        requestGeneration++
        dialogJob?.cancel()
        dialogJob = null
        _state.value = _state.value.copy(dialog = null)
    }

    fun selectRating(value: Int) {
        if (value !in 1..5) return
        val dialog = _state.value.dialog ?: return
        if (dialog.isLoading || dialog.isSaving) return
        _state.value = _state.value.copy(
            dialog = dialog.copy(selectedValue = value, error = null)
        )
    }

    fun save() {
        val dialog = _state.value.dialog ?: return
        val selectedValue = dialog.selectedValue?.takeIf { it in 1..5 } ?: return
        if (dialog.isLoading || dialog.isSaving) return
        val generation = requestGeneration
        val referenceKey = dialog.song.membershipKey()
        _state.value = _state.value.copy(
            dialog = dialog.copy(isSaving = true, error = null)
        )
        dialogJob = scope.launch {
            runCatching { repository.setRating(dialog.song, selectedValue) }
                .onSuccess {
                    if (isCurrentDialog(generation, referenceKey)) close()
                }
                .onFailure {
                    updateCurrentDialog(generation, referenceKey) { current ->
                        current.copy(isSaving = false, error = SongRatingUiError.SAVE)
                    }
                }
        }
    }

    fun clear() {
        val dialog = _state.value.dialog ?: return
        if (dialog.persistedRating == null || dialog.isLoading || dialog.isSaving) return
        val generation = requestGeneration
        val referenceKey = dialog.song.membershipKey()
        _state.value = _state.value.copy(
            dialog = dialog.copy(isSaving = true, error = null)
        )
        dialogJob = scope.launch {
            runCatching { repository.clearRating(dialog.song) }
                .onSuccess {
                    if (isCurrentDialog(generation, referenceKey)) close()
                }
                .onFailure {
                    updateCurrentDialog(generation, referenceKey) { current ->
                        current.copy(isSaving = false, error = SongRatingUiError.CLEAR)
                    }
                }
        }
    }

    fun setDirectRating(song: Song, value: Int?) {
        if (value != null && value !in 1..5) return
        scope.launch {
            if (value == null) repository.clearRating(song) else repository.setRating(song, value)
        }
    }

    private fun isCurrentDialog(generation: Long, referenceKey: String): Boolean =
        generation == requestGeneration &&
            _state.value.dialog?.song?.membershipKey() == referenceKey

    private inline fun updateCurrentDialog(
        generation: Long,
        referenceKey: String,
        transform: (SongRatingDialogState) -> SongRatingDialogState
    ) {
        if (!isCurrentDialog(generation, referenceKey)) return
        val dialog = _state.value.dialog ?: return
        _state.value = _state.value.copy(dialog = transform(dialog))
    }
}
