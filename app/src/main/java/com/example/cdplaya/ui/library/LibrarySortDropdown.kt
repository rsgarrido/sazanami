package com.example.cdplaya.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.cdplaya.ui.AppShellIconButton
import com.example.cdplaya.ui.AppShellAccent

@Composable
fun LibrarySortDropdown(
    selectedOption: LibrarySortOption,
    options: List<LibrarySortOption>,
    onOptionSelected: (LibrarySortOption) -> Unit,
    optionTitle: (LibrarySortOption) -> String = { option -> option.title },
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedTitle = optionTitle(selectedOption)

    Box(modifier = modifier) {
        AppShellIconButton(
            onClick = {
                isExpanded = true
            },
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = "Sort by $selectedTitle",
            accented = true
        )

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = {
                isExpanded = false
            }
        ) {
            options.forEach { option ->
                val displayTitle = optionTitle(option)
                DropdownMenuItem(
                    text = {
                        Text(text = displayTitle)
                    },
                    leadingIcon = {
                        if (selectedOption == option) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = AppShellAccent
                            )
                        }
                    },
                    onClick = {
                        onOptionSelected(option)
                        isExpanded = false
                    }
                )
            }
        }
    }
}
