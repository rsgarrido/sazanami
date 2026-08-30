package io.github.rsgarrido.sazanami.ui.player.classicwheel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ClassicWheelMenuDisplay(
    title: String,
    menuItems: List<ClassicWheelMenuItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    val safeSelectedIndex = if (menuItems.isEmpty()) {
        0
    } else {
        selectedIndex.coerceIn(0, menuItems.lastIndex)
    }

    val listState = rememberLazyListState()

    LaunchedEffect(safeSelectedIndex, menuItems.size) {
        if (menuItems.isNotEmpty()) {
            listState.animateScrollToItem(safeSelectedIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        itemsIndexed(menuItems) { index, item ->
            ClassicWheelMenuRow(
                item = item,
                isSelected = index == safeSelectedIndex
            )
        }
    }
}

@Composable
private fun ClassicWheelMenuRow(
    item: ClassicWheelMenuItem,
    isSelected: Boolean
) {
    val backgroundColor = if (isSelected) {
        ClassicWheelColors.selectionAccent
    } else {
        Color.Transparent
    }

    val contentColor = if (isSelected) {
        ClassicWheelColors.selectionContent
    } else {
        ClassicWheelColors.screenText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.title,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.subtitle != null) {
                Text(
                    text = item.subtitle,
                    color = contentColor.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = contentColor
        )
    }
}
