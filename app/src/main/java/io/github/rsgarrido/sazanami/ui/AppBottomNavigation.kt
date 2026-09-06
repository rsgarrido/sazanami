package io.github.rsgarrido.sazanami.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.ui.navigation.MainDestination

val AppBottomNavigationHeight = 82.dp

private val AppBottomNavigationBarHeight = 68.dp
private val AppBottomNavigationItemHeight = 50.dp
private val AppBottomNavigationItemSpacing = 6.dp

private data class AppNavigationItem(
    val destination: MainDestination,
    val label: String,
    val icon: ImageVector
)

private val appNavigationItems = listOf(
    AppNavigationItem(MainDestination.HOME, "Home", AppShellIcons.Deck),
    AppNavigationItem(MainDestination.LIBRARY, "Library", AppShellIcons.AlbumStack),
    AppNavigationItem(MainDestination.SEARCH, "Search", AppShellIcons.Search)
)

@Composable
fun AppBottomNavigation(
    selectedDestination: MainDestination,
    onDestinationSelected: (MainDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            ),
            tonalElevation = 4.dp,
            shadowElevation = 14.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppBottomNavigationBarHeight)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                val itemWidth = (
                        maxWidth - AppBottomNavigationItemSpacing *
                                (appNavigationItems.size - 1)
                        ) / appNavigationItems.size
                val selectedIndex = appNavigationItems.indexOfFirst { item ->
                    item.destination == selectedDestination
                }.coerceAtLeast(0)
                val indicatorOffset by animateDpAsState(
                    targetValue = (itemWidth + AppBottomNavigationItemSpacing) * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "bottomNavigationIndicatorOffset"
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .height(AppBottomNavigationItemHeight),
                    color = AppShellAccent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(20.dp)
                ) {}

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(
                        AppBottomNavigationItemSpacing
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    appNavigationItems.forEach { item ->
                        AppBottomNavigationItem(
                            item = item,
                            selected = selectedDestination == item.destination,
                            onClick = { onDestinationSelected(item.destination) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBottomNavigationItem(
    item: AppNavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            AppShellAccent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "bottomNavigationContentColor"
    )

    Surface(
        modifier = modifier
            .height(AppBottomNavigationItemHeight)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            ),
        color = Color.Transparent,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(21.dp),
                tint = contentColor
            )
            Text(
                text = item.label.uppercase(),
                style = AppShellTypography.NavigationLabel,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
