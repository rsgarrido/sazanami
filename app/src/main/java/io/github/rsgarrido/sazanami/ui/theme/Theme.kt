package io.github.rsgarrido.sazanami.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SazanamiAccent,
    onPrimary = SazanamiOnAccent,
    primaryContainer = SazanamiAccentContainer,
    onPrimaryContainer = SazanamiOnAccentContainer,
    secondary = SazanamiOnSurfaceVariant,
    onSecondary = SazanamiBackground,
    secondaryContainer = SazanamiSurfaceHighest,
    onSecondaryContainer = SazanamiOnSurface,
    tertiary = SazanamiOnAccentContainer,
    background = SazanamiBackground,
    onBackground = SazanamiOnSurface,
    surface = SazanamiSurface,
    onSurface = SazanamiOnSurface,
    surfaceVariant = SazanamiSurfaceHigh,
    onSurfaceVariant = SazanamiOnSurfaceVariant,
    outline = SazanamiOutline,
    outlineVariant = SazanamiOutlineVariant,
    surfaceTint = SazanamiAccent,
    surfaceContainerLowest = SazanamiBackground,
    surfaceContainerLow = SazanamiSurfaceLow,
    surfaceContainer = SazanamiSurfaceContainer,
    surfaceContainerHigh = SazanamiSurfaceHigh,
    surfaceContainerHighest = SazanamiSurfaceHighest
)

private val LightColorScheme = lightColorScheme(
    primary = SazanamiAccentContainer,
    onPrimary = SazanamiOnAccentContainer,
    primaryContainer = SazanamiOnAccentContainer,
    onPrimaryContainer = SazanamiOnAccent,
    background = SazanamiLightBackground,
    onBackground = SazanamiLightOnSurface,
    surface = SazanamiLightSurface,
    onSurface = SazanamiLightOnSurface,
    surfaceTint = SazanamiAccentContainer
)

@Composable
fun SazanamiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
