package io.github.rsgarrido.sazanami.ui.state

data class SleepTimerUiState(
    val isActive: Boolean = false,
    val remainingSeconds: Int = 0,
    val timerOptionsMinutes: List<Int> = emptyList()
) {
    companion object {
        val Inactive = SleepTimerUiState()
    }
}

val SLEEP_TIMER_OPTIONS_MINUTES = listOf(5, 10, 15, 30, 45, 60)

fun SleepTimerUiState.displayText(): String {
    if (!isActive || remainingSeconds <= 0) return "No sleep timer"
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    return if (minutes > 0) {
        "${minutes}m ${seconds.toString().padStart(2, '0')}s remaining"
    } else {
        "${seconds}s remaining"
    }
}
