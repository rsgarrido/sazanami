package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.R
import kotlinx.coroutines.delay

internal class PocketHorizonConsoleInput {
    var binding by mutableStateOf<Binding?>(null)
    class Binding(val move: (Int) -> Unit, val confirm: () -> Unit, val canConfirm: Boolean)
}
internal val LocalHorizonConsoleInput = compositionLocalOf<PocketHorizonConsoleInput?> { null }

@Composable
internal fun PocketHorizonControlWing(left: Boolean, enabled: Boolean, playbackEnabled: Boolean,
    navigation: PocketHorizonNavigationState, input: PocketHorizonConsoleInput,
    onPlayPause: () -> Unit, onQueue: (() -> Unit)?, style: PocketHorizonStyle) {
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val actions = enabled && !navigation.showControlGuide
    Column(Modifier.fillMaxHeight().padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly) {
        val up = stringResource(R.string.horizon_control_up)
        val down = stringResource(R.string.horizon_control_down)
        val previous = stringResource(R.string.horizon_control_previous_category)
        val next = stringResource(R.string.horizon_control_next_category)
        Box(Modifier.size(PocketHorizonLayoutMetrics.ControlCluster).testTag(if (left) "horizonDpad" else "horizonFaceButtons")
            .background(style.hardware.shade.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
            if (left) {
                ConsoleButton("▲", up, actions && input.binding != null, style, Modifier.align(Alignment.TopCenter), "horizonUp") { input.binding?.move?.invoke(-1) }
                ConsoleButton("▼", down, actions && input.binding != null, style, Modifier.align(Alignment.BottomCenter), "horizonDown") { input.binding?.move?.invoke(1) }
                ConsoleButton("◀", previous, actions && navigation.category.ordinal > 0, style, Modifier.align(Alignment.CenterStart), "horizonLeft") { navigation.stepCategory(-1) }
                ConsoleButton("▶", next, actions && navigation.category.ordinal < 3, style, Modifier.align(Alignment.CenterEnd), "horizonRight") { navigation.stepCategory(1) }
                Box(Modifier.size(36.dp).background(style.hardware.shade, RoundedCornerShape(4.dp)))
            } else {
                ConsoleButton("X", stringResource(R.string.horizon_control_play_pause), actions && playbackEnabled, style, Modifier.align(Alignment.TopCenter), "horizonX", action = onPlayPause)
                ConsoleButton("A", stringResource(R.string.horizon_control_confirm), actions && input.binding?.canConfirm == true, style, Modifier.align(Alignment.CenterEnd), "horizonA") { input.binding?.confirm?.invoke() }
                ConsoleButton("B", stringResource(R.string.horizon_control_back), enabled, style, Modifier.align(Alignment.BottomCenter), "horizonB") {
                    if (navigation.showControlGuide) navigation.showControlGuide = false
                    else if (!navigation.back()) back?.onBackPressed()
                }
                ConsoleButton("Y", stringResource(R.string.horizon_control_queue), actions && onQueue != null, style, Modifier.align(Alignment.CenterStart), "horizonY") { onQueue?.invoke() }
            }
        }
        if (left) ConsoleAnalog(actions, input, navigation, style)
        else Spacer(Modifier.height(80.dp))
    }
}

@Composable
internal fun PocketHorizonSystemControls(enabled: Boolean, navigation: PocketHorizonNavigationState,
    style: PocketHorizonStyle) {
    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        // Passive speaker grille, deliberately not drawn as an unavailable volume control.
        Canvas(Modifier.size(48.dp, 16.dp)) {
            repeat(5) { index ->
                val x = size.width * (index + 1) / 6f
                drawLine(style.hardware.outline.copy(alpha = .5f), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsoleButton("SELECT", stringResource(R.string.horizon_control_select), enabled, style,
                Modifier.widthIn(min = 80.dp), "horizonSelect") {
                navigation.showControlGuide = !navigation.showControlGuide
            }
            ConsoleButton("START", stringResource(R.string.horizon_control_start), enabled, style,
                Modifier.widthIn(min = 80.dp), "horizonStart") {
                navigation.showControlGuide = false
                navigation.toggleMusicBrowser()
            }
        }
    }
}

@Composable
internal fun PocketHorizonShoulder(left: Boolean, enabled: Boolean, onClick: () -> Unit,
    style: PocketHorizonStyle) {
    ConsoleButton(if (left) "L" else "R",
        stringResource(if (left) R.string.horizon_control_previous_track else R.string.horizon_control_next_track),
        enabled, style, Modifier.fillMaxWidth().height(48.dp), if (left) "horizonL" else "horizonR",
        bumperLeft = left, action = onClick)
}

@Composable
private fun ConsoleButton(label: String, description: String, enabled: Boolean, style: PocketHorizonStyle,
    modifier: Modifier, tag: String, bumperLeft: Boolean? = null, action: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val physical = remember { MutableInteractionSource() }
    val physicalPressed by physical.collectIsPressedAsState()
    val latestEnabled by rememberUpdatedState(enabled)
    val latestAction by rememberUpdatedState(action)
    val live = remember { mutableStateOf(true) }
    DisposableEffect(live) { onDispose { live.value = false } }
    val down = pressed || physicalPressed
    val shoulder = bumperLeft != null
    val shape = RoundedCornerShape(16.dp)
    val pressDescription = stringResource(if (down) R.string.horizon_shoulder_pressed else R.string.horizon_shoulder_resting)
    Box(modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).testTag(tag)
        .background(if (shoulder) androidx.compose.ui.graphics.Color.Transparent else style.hardware.shade, shape)
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val press = PressInteraction.Press(down.position)
                physical.tryEmit(press)
                try {
                    do {
                        val event = awaitPointerEvent()
                        val point = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!point.pressed || event.changes.count { it.pressed } > 1 ||
                            point.position.x < 0f || point.position.x >= size.width ||
                            point.position.y < 0f || point.position.y >= size.height) break
                    } while (true)
                } finally { physical.tryEmit(PressInteraction.Cancel(press)) }
            }
        }
        .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null) {
            if (live.value && latestEnabled) latestAction()
        }.semantics {
            contentDescription = description
            if (shoulder) stateDescription = pressDescription
        }, contentAlignment = Alignment.Center) {
        if (bumperLeft != null) {
            PocketHorizonBumperVisual(bumperLeft, down, style, Modifier.matchParentSize())
        } else {
            // Face and system buttons retain their existing finish, geometry and input contract.
            Box(Modifier.matchParentSize().graphicsLayer {
                this.shape = shape
                clip = true
            }.testTag(tag + "Plane")
                .background(Brush.verticalGradient(listOf(if (down) style.hardware.pressed else style.hardware.controlHighlight, style.hardware.shade)))
                .border(1.dp, if (down) style.hardware.pressed else style.hardware.outline, shape))
            Text(label, Modifier.padding(horizontal = 6.dp).clearAndSetSemantics {},
                color = style.hardware.ink, fontSize = if (label.length > 1) 10.sp else 16.sp,
                lineHeight = if (label.length > 1) 16.sp else 20.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ConsoleAnalog(enabled: Boolean, input: PocketHorizonConsoleInput,
    navigation: PocketHorizonNavigationState, style: PocketHorizonStyle) {
    var displacement by remember { mutableStateOf(Offset.Zero) }
    var held by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(input.binding)
    val active by rememberUpdatedState(enabled)
    val label = stringResource(R.string.horizon_control_analog)
    val command = horizonAnalogDirection(displacement)
    fun step(direction: Int) {
        if (!active || !held || horizonAnalogDirection(displacement) != direction) return
        when (direction) {
            -1 -> latest?.move?.invoke(-1)
            1 -> latest?.move?.invoke(1)
            -2 -> navigation.stepCategory(-1)
            2 -> navigation.stepCategory(1)
        }
    }
    LaunchedEffect(command, held, enabled) {
        if (held && enabled && command != 0) {
            delay(380L)
            repeat(23) {
                if (!active || !held) return@LaunchedEffect
                step(command)
                delay(if (displacement.getDistance() > .8f) 120L else 200L)
            }
        }
    }
    DisposableEffect(enabled) { onDispose { held = false; displacement = Offset.Zero } }
    Box(Modifier.size(80.dp).testTag("horizonAnalog").clip(CircleShape)
        .background(Brush.radialGradient(listOf(style.hardware.controlHighlight, style.hardware.shade))).border(2.dp, style.hardware.outline, CircleShape)
        .semantics { contentDescription = label; if (!enabled) disabled() }
        .then(if (!enabled) Modifier else Modifier.pointerInput(enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(); down.consume(); held = true
                try {
                    do {
                        val event = awaitPointerEvent()
                        val point = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!point.pressed || point.isConsumed || event.changes.count { it.pressed } > 1) break
                        val previous = horizonAnalogDirection(displacement)
                        displacement = clampHorizonAnalog((point.position - Offset(size.width / 2f, size.height / 2f)) / (minOf(size.width, size.height) / 2f))
                        val next = horizonAnalogDirection(displacement)
                        if (next != 0 && next != previous) step(next)
                        point.consume()
                    } while (true)
                } finally { held = false; displacement = Offset.Zero }
            }
        }), contentAlignment = Alignment.Center) {
        Box(Modifier.size(52.dp).testTag("horizonAnalogKnob").graphicsLayer {
            translationX = displacement.x * 14.dp.toPx(); translationY = displacement.y * 14.dp.toPx()
        }.background(Brush.radialGradient(listOf(style.hardware.outline, style.hardware.shell)), CircleShape).border(1.dp, style.hardware.controlHighlight, CircleShape))
    }
}
