package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.R

/** Tap-based tabs. Horizontal scrolling only reveals labels; it never changes categories or captures seeks. */
@Composable
internal fun PocketHorizonCategoryNavigation(
    navigationState: PocketHorizonNavigationState,
    enabled: Boolean,
    style: PocketHorizonStyle,
    modifier: Modifier = Modifier
) {
    val height = PocketHorizonLayoutMetrics.categoryHeight(LocalDensity.current.fontScale)
    Row(modifier.testTag("pocketHorizonCategories").horizontalScroll(rememberScrollState(), enabled = enabled).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PocketHorizonCategory.entries.forEach { category ->
            val selected = navigationState.category == category
            val reveal = remember { BringIntoViewRequester() }
            var placedSize by remember { mutableStateOf<IntSize?>(null) }
            LaunchedEffect(selected, placedSize) {
                if (selected && placedSize != null) reveal.bringIntoView()
            }
            val label = stringResource(when (category) {
                PocketHorizonCategory.NowPlaying -> R.string.pocket_horizon_category_now_playing
                PocketHorizonCategory.Songs -> R.string.pocket_horizon_category_songs
                PocketHorizonCategory.Albums -> R.string.pocket_horizon_category_albums
                PocketHorizonCategory.Artists -> R.string.pocket_horizon_category_artists
            })
            val shape = RoundedCornerShape(2.dp)
            Box(Modifier.height(height).bringIntoViewRequester(reveal)
                    .onPlaced { placedSize = it.size }
                    .testTag("pocketHorizonCategory" + category.name)
                    .background(if (selected) style.surface else androidx.compose.ui.graphics.Color.Transparent, shape)
                    .border(if (selected) 2.dp else 0.dp, if (selected) style.accent else androidx.compose.ui.graphics.Color.Transparent, shape)
                    .selectable(selected, enabled = enabled, role = Role.Tab) {
                        if (enabled) navigationState.selectCategory(category)
                    }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(when(category) {
                        PocketHorizonCategory.NowPlaying -> Icons.Filled.PlayArrow
                        PocketHorizonCategory.Songs -> Icons.Filled.MusicNote
                        PocketHorizonCategory.Albums -> Icons.Filled.Album
                        PocketHorizonCategory.Artists -> Icons.Filled.Person
                    }, null, Modifier.size(20.dp), tint = if (selected) style.accent else style.secondaryText)
                Text(label, color = if (selected) style.accent else style.text,
                style = TextStyle(fontFamily = FontFamily.SansSerif,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)),
                fontSize = 14.sp, lineHeight = 22.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1)
                }
            }
        }
    }
}
