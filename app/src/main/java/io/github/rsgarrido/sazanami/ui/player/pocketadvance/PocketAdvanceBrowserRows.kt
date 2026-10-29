package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.R

/** The existing Songs row, shared with artist names and artist tracks without changing its geometry. */
@Composable
internal fun PocketAdvanceBrowserRow(
    title: String, subtitle: String, isSelected: Boolean, actionLabel: String,
    onClick: () -> Unit, modifier: Modifier = Modifier
) {
    val colors = LocalPocketAdvancePalette.current
    Column(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .background(if (isSelected) colors.Ink else colors.Screen, RoundedCornerShape(4.dp))
            .border(1.dp, if (isSelected) colors.ScreenAccent else colors.ScreenShade, RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick)
            .semantics { selected = isSelected; contentDescription = "$title, $subtitle" }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val ink = if (isSelected) colors.Screen else colors.Ink
        Text(title, color = ink, fontFamily = FontFamily.Monospace,
            fontSize = 15.sp, lineHeight = 20.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = if (isSelected) colors.Screen else colors.MutedInk,
            fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun PocketAdvanceBrowserEmpty(title: String, modifier: Modifier = Modifier) {
    val colors = LocalPocketAdvancePalette.current
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = colors.Ink, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
            Text(stringResource(R.string.player_wheel_check_library), color = colors.MutedInk,
                fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

/** All browse rows use the same two single-line text slots, padding and minimum height. */
internal suspend fun LazyListState.revealPocketAdvanceBrowseItem(index: Int) {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    val measured = visible.firstOrNull { it.index == index }
    val anchor = measured ?: (if (index < firstVisibleItemIndex) visible.firstOrNull() else visible.lastOrNull())
    if (anchor == null) {
        scrollToItem(index)
        return
    }
    if (index < visible.first().index - 1 || index > visible.last().index + 1) {
        // A wrap/reorder can cross the library. Reveal its nearest edge without animating every row.
        val bottomOffset = -(info.viewportEndOffset - info.viewportStartOffset - anchor.size).coerceAtLeast(0)
        scrollToItem(index, if (index > anchor.index) bottomOffset else 0)
        return
    }
    val start = anchor.offset + (index - anchor.index) * (anchor.size + info.mainAxisItemSpacing)
    val end = start + anchor.size
    val distance = when {
        start < info.viewportStartOffset -> start - info.viewportStartOffset
        end > info.viewportEndOffset -> end - info.viewportEndOffset
        else -> 0
    }
    // Complete within one repeat interval. A newer request cancels this effect instead of queuing work.
    if (distance != 0) animateScrollBy(distance.toFloat(), tween(durationMillis = 90))
}
