package io.github.rsgarrido.sazanami.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import io.github.rsgarrido.sazanami.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI observes immutable service state. This bridge owns no player, job, FD or USB handle. */
internal object IntegratedDirectPlaybackBridge {
    private var owner: ExperimentalUsbPlaybackPlayer?=null
    private val mutableState=MutableStateFlow(IntegratedDirectPlaybackState())
    val state=mutableState.asStateFlow()
    private val attempts=IntegratedDirectAttemptEvidence()
    val latestAttempt=attempts.latest
    val reportState get()=latestAttempt.value ?: state.value
    val directSelected get()=state.value.enabled
    fun attach(player: ExperimentalUsbPlaybackPlayer) { owner=player; publish(player,player.directState) }
    fun detach(player: ExperimentalUsbPlaybackPlayer) { if(owner===player) { owner=null; mutableState.value=IntegratedDirectPlaybackState() } }
    fun publish(player: ExperimentalUsbPlaybackPlayer,value: IntegratedDirectPlaybackState) {
        if(owner===player) { attempts.record(value); mutableState.value=value }
    }
    fun select(enabled: Boolean) { if(BuildConfig.DEBUG) owner?.selectDirect(enabled) }
    fun stop() { if(BuildConfig.DEBUG) owner?.stopDirect() }
    @OptIn(UnstableApi::class)
    fun start() { if(BuildConfig.DEBUG) owner?.prepare() }
    fun reject(command: String) { owner?.rejectDirectCommand(command) }
}
