package io.github.rsgarrido.sazanami.ui.player.classicwheel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

@Composable
fun ClassicBatteryIndicator(
    modifier: Modifier = Modifier
) {
    val batteryLevel = rememberBatteryLevel()
    val level = batteryLevel.value.coerceIn(0, 100)

    val fillColor = when {
        level <= CRITICAL_BATTERY_LEVEL -> Color(0xFFD84A3A)
        level <= LOW_BATTERY_LEVEL -> Color(0xFFE0A13A)
        else -> Color(0xFF9BCB61)
    }

    Canvas(
        modifier = modifier.size(
            width = 40.dp,
            height = 18.dp
        )
    ) {
        val strokeWidth = 1.5.dp.toPx()
        val capWidth = 3.5.dp.toPx()
        val capHeight = 8.dp.toPx()
        val capSpacing = 1.dp.toPx()

        val bodyWidth = size.width - capWidth - capSpacing
        val bodyHeight = size.height
        val cornerRadius = 3.dp.toPx()

        drawRoundRect(
            color = Color.Black,
            topLeft = Offset.Zero,
            size = Size(
                width = bodyWidth,
                height = bodyHeight
            ),
            cornerRadius = CornerRadius(
                x = cornerRadius,
                y = cornerRadius
            ),
            style = Stroke(width = strokeWidth)
        )

        val innerPadding = 3.dp.toPx()
        val availableFillWidth = bodyWidth - innerPadding * 2
        val fillWidth = availableFillWidth * (level / 100f)

        drawRoundRect(
            color = fillColor,
            topLeft = Offset(
                x = innerPadding,
                y = innerPadding
            ),
            size = Size(
                width = fillWidth.coerceAtLeast(1.dp.toPx()),
                height = bodyHeight - innerPadding * 2
            ),
            cornerRadius = CornerRadius(
                x = 2.dp.toPx(),
                y = 2.dp.toPx()
            )
        )

        drawRoundRect(
            color = Color.Black,
            topLeft = Offset(
                x = bodyWidth + capSpacing,
                y = (bodyHeight - capHeight) / 2f
            ),
            size = Size(
                width = capWidth,
                height = capHeight
            ),
            cornerRadius = CornerRadius(
                x = 1.dp.toPx(),
                y = 1.dp.toPx()
            )
        )
    }
}

@Composable
internal fun rememberBatteryLevel(): State<Int> {
    val context = LocalContext.current
    val batteryLevel = remember {
        mutableIntStateOf(readBatteryLevel(context))
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent
            ) {
                batteryLevel.intValue = readBatteryLevel(intent)
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        val stickyBatteryIntent = ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (stickyBatteryIntent != null) {
            batteryLevel.intValue = readBatteryLevel(stickyBatteryIntent)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    return batteryLevel
}

private fun readBatteryLevel(context: Context): Int {
    val batteryIntent = context.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )

    return if (batteryIntent == null) {
        DEFAULT_BATTERY_LEVEL
    } else {
        readBatteryLevel(batteryIntent)
    }
}

private fun readBatteryLevel(intent: Intent): Int {
    val level = intent.getIntExtra(
        BatteryManager.EXTRA_LEVEL,
        UNKNOWN_BATTERY_VALUE
    )

    val scale = intent.getIntExtra(
        BatteryManager.EXTRA_SCALE,
        UNKNOWN_BATTERY_VALUE
    )

    if (level < 0 || scale <= 0) {
        return DEFAULT_BATTERY_LEVEL
    }

    return ((level / scale.toFloat()) * 100f)
        .roundToInt()
        .coerceIn(0, 100)
}

private const val LOW_BATTERY_LEVEL = 30
private const val CRITICAL_BATTERY_LEVEL = 15
private const val DEFAULT_BATTERY_LEVEL = 100
private const val UNKNOWN_BATTERY_VALUE = -1