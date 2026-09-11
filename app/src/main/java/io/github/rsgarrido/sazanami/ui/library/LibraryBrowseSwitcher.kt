package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.AppShellTypography
import io.github.rsgarrido.sazanami.ui.AppShellAccent

private const val LibrarySelectionColorDurationMillis = 180
private const val LibrarySongsFilterMotionDurationMillis = 180
private val LibraryPrimaryIndicatorHeight = 42.dp
private val LibraryFilterIndicatorHeight = 34.dp
internal val LibrarySongsFilterRowSlotHeight = 42.dp

val primaryLibraryTabs = listOf(
    LibraryTab.SONGS,
    LibraryTab.ALBUMS,
    LibraryTab.ARTISTS,
    LibraryTab.FOLDERS,
    LibraryTab.PLAYLISTS,
    LibraryTab.GENRES
)

internal data class LibraryTabOverflowAffordances(
    val showStart: Boolean,
    val showEnd: Boolean
)

internal fun libraryTabOverflowAffordances(
    hasMeasuredContent: Boolean,
    canScrollBackward: Boolean,
    canScrollForward: Boolean
): LibraryTabOverflowAffordances = LibraryTabOverflowAffordances(
    showStart = hasMeasuredContent && canScrollBackward,
    showEnd = hasMeasuredContent && canScrollForward
)

val songCollectionTabs = listOf(
    LibraryTab.SONGS,
    LibraryTab.FAVORITES,
    LibraryTab.RATED,
    LibraryTab.RECENTLY_ADDED,
    LibraryTab.RECENTLY_PLAYED,
    LibraryTab.MOST_PLAYED
)

fun LibraryTab.primaryBrowseTab(): LibraryTab? {
    return when (this) {
        LibraryTab.FAVORITES,
        LibraryTab.RATED,
        LibraryTab.RECENTLY_ADDED,
        LibraryTab.RECENTLY_PLAYED,
        LibraryTab.MOST_PLAYED -> LibraryTab.SONGS

        LibraryTab.QUEUE -> null
        else -> this
    }
}

@Composable
fun LibraryBrowseSwitcher(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedPrimaryTab = selectedTab.primaryBrowseTab() ?: return
    val primaryTabScrollState = rememberScrollState()
    val filterScrollState = rememberScrollState()
    val primaryTabBounds = remember { mutableStateMapOf<LibraryTab, Rect>() }
    val filterBounds = remember { mutableStateMapOf<LibraryTab, Rect>() }
    val primaryTabContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val overflowAffordances = libraryTabOverflowAffordances(
        hasMeasuredContent = primaryTabScrollState.maxValue != Int.MAX_VALUE,
        canScrollBackward = primaryTabScrollState.canScrollBackward,
        canScrollForward = primaryTabScrollState.canScrollForward
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = primaryTabContainerColor,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .horizontalScroll(primaryTabScrollState)
                        .selectableGroup()
                ) {
                    MovingSelectionIndicator(
                        targetBounds = primaryTabBounds[selectedPrimaryTab],
                        height = LibraryPrimaryIndicatorHeight,
                        shape = RoundedCornerShape(17.dp),
                        color = AppShellAccent.copy(alpha = 0.16f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        primaryLibraryTabs.forEach { tab ->
                            LibraryPrimaryTab(
                                tab = tab,
                                selected = tab == selectedPrimaryTab,
                                onClick = { onTabSelected(tab) },
                                onBoundsChanged = { bounds ->
                                    if (primaryTabBounds[tab] != bounds) {
                                        primaryTabBounds[tab] = bounds
                                    }
                                },
                                modifier = Modifier.widthIn(min = 84.dp)
                            )
                        }
                    }
                }

                if (overflowAffordances.showStart) {
                    LibraryTabOverflowFade(
                        modifier = Modifier.align(Alignment.CenterStart),
                        colors = listOf(
                            primaryTabContainerColor,
                            Color.Transparent
                        )
                    )
                }

                if (overflowAffordances.showEnd) {
                    LibraryTabOverflowFade(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        colors = listOf(
                            Color.Transparent,
                            primaryTabContainerColor
                        )
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(LibraryFilterIndicatorHeight)
                .clipToBounds()
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedTab.showsSongsFilterRow(),
                enter = slideInVertically(
                    animationSpec = tween(LibrarySongsFilterMotionDurationMillis),
                    initialOffsetY = { height -> -height }
                ) + fadeIn(tween(LibrarySongsFilterMotionDurationMillis)),
                exit = slideOutVertically(
                    animationSpec = tween(LibrarySongsFilterMotionDurationMillis),
                    targetOffsetY = { height -> -height }
                ) + fadeOut(tween(LibrarySongsFilterMotionDurationMillis))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(filterScrollState)
                        .padding(horizontal = 16.dp)
                        .selectableGroup()
                ) {
                    MovingSelectionIndicator(
                        targetBounds = filterBounds[selectedTab],
                        height = LibraryFilterIndicatorHeight,
                        shape = RoundedCornerShape(14.dp),
                        color = AppShellAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, AppShellAccent.copy(alpha = 0.42f))
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        songCollectionTabs.forEach { tab ->
                            LibraryFilterPill(
                                tab = tab,
                                selected = selectedTab == tab,
                                onClick = { onTabSelected(tab) },
                                onBoundsChanged = { bounds ->
                                    if (filterBounds[tab] != bounds) {
                                        filterBounds[tab] = bounds
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTabOverflowFade(
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .background(Brush.horizontalGradient(colors = colors))
    )
}

@Composable
private fun LibraryPrimaryTab(
    tab: LibraryTab,
    selected: Boolean,
    onClick: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(17.dp)
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            AppShellAccent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(LibrarySelectionColorDurationMillis),
        label = "libraryPrimaryTabContentColor"
    )

    Surface(
        modifier = modifier
            .height(LibraryPrimaryIndicatorHeight)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInParent())
            },
        color = Color.Transparent,
        contentColor = contentColor,
        shape = shape
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick
                )
                .padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tab.title.uppercase(),
                style = AppShellTypography.ControlLabel,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LibraryFilterPill(
    tab: LibraryTab,
    selected: Boolean,
    onClick: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (tab) {
        LibraryTab.SONGS -> "All"
        LibraryTab.RECENTLY_ADDED -> stringResource(R.string.recently_added_filter)
        LibraryTab.RECENTLY_PLAYED -> "Recent"
        LibraryTab.MOST_PLAYED -> "Most played"
        LibraryTab.RATED -> "Rated"
        else -> tab.title
    }

    val shape = RoundedCornerShape(14.dp)
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            AppShellAccent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(LibrarySelectionColorDurationMillis),
        label = "libraryFilterContentColor"
    )

    Surface(
        modifier = modifier
            .height(LibraryFilterIndicatorHeight)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInParent())
            },
        shape = shape,
        color = if (selected) Color.Transparent else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = contentColor,
        border = if (selected) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
        )
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick
                )
                .padding(horizontal = 13.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.uppercase(),
                style = AppShellTypography.ControlLabel,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

internal fun LibraryTab.showsSongsFilterRow(): Boolean =
    primaryBrowseTab() == LibraryTab.SONGS

internal fun libraryContentTopPadding(
    chromeTopPadding: Dp,
    visibleTab: LibraryTab
): Dp = if (visibleTab.primaryBrowseTab() == null || visibleTab.showsSongsFilterRow()) {
    chromeTopPadding
} else {
    maxOf(0.dp, chromeTopPadding - LibrarySongsFilterRowSlotHeight)
}

@Composable
private fun MovingSelectionIndicator(
    targetBounds: Rect?,
    height: Dp,
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null
) {
    if (targetBounds == null) return

    val density = LocalDensity.current
    val targetOffset = with(density) { targetBounds.left.toDp() }
    val targetWidth = with(density) { targetBounds.width.toDp() }
    val animatedOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "librarySelectionIndicatorOffset"
    )
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "librarySelectionIndicatorWidth"
    )

    Surface(
        modifier = modifier
            .offset(x = animatedOffset)
            .width(animatedWidth)
            .height(height),
        shape = shape,
        color = color,
        border = border
    ) {}
}
