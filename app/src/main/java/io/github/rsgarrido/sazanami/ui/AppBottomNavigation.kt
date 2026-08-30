package io.github.rsgarrido.sazanami.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppBottomNavigationBarHeight)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
            .height(50.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            ),
        color = if (selected) {
            AppShellAccent.copy(alpha = 0.16f)
        } else {
            Color.Transparent
        },
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
