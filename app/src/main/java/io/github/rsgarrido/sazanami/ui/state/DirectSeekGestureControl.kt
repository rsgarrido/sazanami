package io.github.rsgarrido.sazanami.ui.state

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlin.math.max
import kotlin.math.min

/** One currently touched control; no commands, source positions or USB ownership live here. */
internal class DirectSeekGestureEnvironment(val authority: ()->DirectScrubAuthority,val preview: (Int?)->Unit) {
    private var active: DirectSeekGestureControl?=null
    private var multitouch=false
    fun pointerSequence() {multitouch=false}
    fun activate(control: DirectSeekGestureControl) {
        if(active!==control) active?.cancel()
        active=control
        if(multitouch) control.cancel()
    }
    fun publish(control: DirectSeekGestureControl,value: Int?) {if(active===control) preview(value)}
    fun cancel() {multitouch=true;active?.cancel()}
    fun observe() {active?.dispatch?.observe()}
    fun release(control: DirectSeekGestureControl) {if(active===control) {preview(null);active=null}}
}
internal val LocalDirectSeekGestureEnvironment=compositionLocalOf<DirectSeekGestureEnvironment?> {null}

/** Shared adapter used by every seek control, without changing callback forwarding through player themes. */
internal class DirectSeekGestureControl(private val environment: DirectSeekGestureEnvironment?,commit: (Int)->Unit) {
    private var callbackPending=false
    private var directTouch=false
    private var interactionSource: MutableInteractionSource?=null
    private var disposed=false
    val dispatch=DirectSeekDispatch({environment?.authority?.invoke() ?: DirectScrubAuthority(false,0,0,true)},commit,
        {environment?.publish(this,it)})
    fun change(position: Int) {if(!disposed && !directTouch) {callbackPending=true;dispatch.change(position)}}
    fun finish() {if(!disposed && !directTouch) {dispatch.complete();callbackPending=false}}
    fun cancel()=dispatch.cancel()
    fun immediate(position: Int) {if(!disposed) dispatch.immediate(position)}
    fun retirePointer()=dispatch.retire()
    fun beginDirectTouch() {directTouch=true;dispatch.retire();dispatch.begin();environment?.activate(this)}
    fun sampleDirectTouch(position: Int)=dispatch.change(position)
    fun releaseDirectTouch()=dispatch.pointerUp()
    fun completeDirectTouch()=dispatch.complete()
    /** Direct touch owns raw control input; Material3's independent tap/drag coroutines cannot compete for its commit.
     * Normal mode leaves every event to Material3. Geometry matches its min/max thumb-centre mapping. */
    fun sliderPointerModifier(thumbWidth: ()->Int,reverse: Boolean): Modifier=Modifier.pointerInput(this,reverse) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            directTouch=environment?.authority?.invoke()?.enabled==true
            if(!directTouch) {
                dispatch.begin();environment?.activate(this@DirectSeekGestureControl)
                try {
                    var pressed: Boolean
                    do {
                        val event=awaitPointerEvent(PointerEventPass.Final)
                        if(event.changes.count {it.pressed}>1) cancel()
                        pressed=event.changes.any {it.pressed}
                    } while(pressed)
                } catch(cancelled: CancellationException) {cancel();if(!callbackPending) finish();throw cancelled}
                return@awaitEachGesture // Normal completion stays with onValueChangeFinished.
            }
            beginDirectTouch()
            if(down.isConsumed) cancel()
            val interaction=DragInteraction.Start()
            interactionSource?.tryEmit(interaction)
            fun sample(x: Float) {
                val maxPx=max(size.width-thumbWidth()/2f,0f)
                val minPx=min(thumbWidth()/2f,maxPx)
                val raw=if(reverse) size.width-x else x
                val fraction=if(maxPx>minPx) ((raw-minPx)/(maxPx-minPx)).coerceIn(0f,1f) else 0f
                sampleDirectTouch((fraction*sliderDuration).toInt())
            }
            try {
                down.consume();sample(down.position.x)
                var pressed: Boolean
                do {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    if(event.changes.count {it.pressed}>1) cancel()
                    val original=event.changes.firstOrNull {it.id==down.id}
                    if(original==null) cancel() else {
                        if(original.isConsumed) cancel()
                        sample(original.position.x);original.consume()
                    }
                    pressed=event.changes.any {it.pressed}
                    event.changes.forEach {it.consume()}
                    awaitPointerEvent(PointerEventPass.Final)
                } while(pressed)
                releaseDirectTouch();completeDirectTouch()
                interactionSource?.tryEmit(DragInteraction.Stop(interaction))
            } catch(cancelled: CancellationException) {
                cancel();dispatch.retire();interactionSource?.tryEmit(DragInteraction.Cancel(interaction));throw cancelled
            }
            // Keep directTouch as a tombstone until another explicit input: queued SDK callbacks cannot seek.
        }
    }
    var sliderDuration: Int=1
    fun pointerModifier(): Modifier=Modifier.pointerInput(this) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            dispatch.begin();environment?.activate(this@DirectSeekGestureControl)
            try {
                var pressed: Boolean
                do {
                    val event=awaitPointerEvent(PointerEventPass.Final)
                    if(event.changes.count {it.pressed}>1) cancel()
                    val original=event.changes.firstOrNull {it.id==down.id}
                    if(original==null) cancel()
                    pressed=event.changes.any {it.pressed}
                } while(pressed)
                dispatch.pointerUp()
                // Custom controls publish synchronously in their Main pass, then commit in their own Final pass.
                finish()
            } catch(cancelled: CancellationException) {
                cancel()
                dispatch.retire()
                throw cancelled
            }
        }
    }
    fun keyboardModifier(): Modifier=Modifier.onPreviewKeyEvent {
        if(it.key in setOf(Key.DirectionLeft,Key.DirectionRight,Key.DirectionUp,Key.DirectionDown,Key.MoveHome,Key.MoveEnd,Key.PageUp,Key.PageDown)) {
            directTouch=false
            dispatch.keyboardInput()
        }
        false // Material3 retains its existing value/key behavior.
    }
    /** Material3 also invokes completion after DragInteraction.Cancel; mark cancellation before forwarding it. */
    fun interactions(delegate: MutableInteractionSource,dragging: ()->Boolean): MutableInteractionSource {
        val result=object : MutableInteractionSource {
        override val interactions: Flow<Interaction> get()=delegate.interactions
        override suspend fun emit(interaction: Interaction) {
            if(!directTouch && interaction is DragInteraction.Start) callbackPending=true
            if(!directTouch && interaction is DragInteraction.Cancel) {
                cancel()
                // Material3 suppresses gestureEndAction while isDragging is true after a cancelled mutateWith.
                // Otherwise its normal completion callback follows this interaction; do not complete twice.
                if(dragging()) finish()
            }
            delegate.emit(interaction)
        }
        override fun tryEmit(interaction: Interaction): Boolean {
            if(!directTouch && interaction is DragInteraction.Start) callbackPending=true
            if(!directTouch && interaction is DragInteraction.Cancel) {cancel();if(dragging()) finish()}
            return delegate.tryEmit(interaction)
        }
    }
        interactionSource=result
        return result
    }
    fun dispose() {disposed=true;dispatch.retire();environment?.release(this)}
}

@Composable
internal fun rememberDirectSeekGestureControl(onSeekChange: (Int)->Unit): DirectSeekGestureControl {
    val environment=LocalDirectSeekGestureEnvironment.current
    val latest=rememberUpdatedState(onSeekChange)
    val control=remember(environment) {DirectSeekGestureControl(environment) {latest.value(it)}}
    DisposableEffect(control) {onDispose {control.dispose()}}
    return control
}

/** Existing position-from-x controls publish in Main, then finish in their own Final pass. */
internal fun Modifier.directSeekPositionInput(control: DirectSeekGestureControl,duration: Int): Modifier=
    then(control.pointerModifier()).pointerInput(control,duration) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            try {
                fun change(x: Float) {control.change(((x/size.width.toFloat()).coerceIn(0f,1f)*duration).toInt())}
                change(down.position.x)
                while(true) {
                    val event=awaitPointerEvent()
                    val pointer=event.changes.firstOrNull {it.id==down.id}
                    if(pointer==null) {control.cancel();break}
                    if(!pointer.pressed) break
                    if(pointer.isConsumed && control.dispatch.directGesture) {control.cancel();break}
                    change(pointer.position.x);pointer.consume()
                }
            } catch(cancelled: CancellationException) {control.retirePointer();throw cancelled}
        }
    }
