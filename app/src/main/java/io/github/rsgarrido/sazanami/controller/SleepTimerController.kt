package io.github.rsgarrido.sazanami.controller

import io.github.rsgarrido.sazanami.ui.state.SleepTimerUiState
import io.github.rsgarrido.sazanami.ui.state.SLEEP_TIMER_OPTIONS_MINUTES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SleepTimerController(
    private val coroutineScope: CoroutineScope,
    private val onTimerFinished: () -> Unit
) {
    private val _uiState = MutableStateFlow(
        SleepTimerUiState(timerOptionsMinutes = SLEEP_TIMER_OPTIONS_MINUTES)
    )
    val uiState: StateFlow<SleepTimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun startTimer(minutes: Int) {
        if (minutes <= 0) {
            return
        }

        timerJob?.cancel()

        _uiState.value = _uiState.value.copy(
            isActive = true,
            remainingSeconds = minutes * SECONDS_PER_MINUTE
        )

        timerJob = coroutineScope.launch {
            while (_uiState.value.remainingSeconds > 0) {
                delay(ONE_SECOND_MS)
                _uiState.update { state ->
                    state.copy(remainingSeconds = (state.remainingSeconds - 1).coerceAtLeast(0))
                }
            }

            _uiState.update { state -> state.copy(isActive = false) }
            timerJob = null

            onTimerFinished()
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.value = _uiState.value.copy(isActive = false, remainingSeconds = 0)
    }

    fun release() {
        cancelTimer()
    }

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        private const val ONE_SECOND_MS = 1_000L
    }
}
