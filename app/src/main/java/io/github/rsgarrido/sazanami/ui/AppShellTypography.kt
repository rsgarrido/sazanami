package io.github.rsgarrido.sazanami.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppShellTypography {
    val ScreenTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.7).sp
    )

    val SectionTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp
    )

    val Eyebrow: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.25.sp
    )

    val StatNumber: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 17.sp,
        letterSpacing = (-0.2).sp
    )

    val StatLabel: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 8.sp,
        lineHeight = 11.sp,
        letterSpacing = 0.8.sp
    )

    val NavigationLabel: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.85.sp
    )

    val ControlLabel: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.55.sp
    )

    val CompactAction: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.75.sp
    )

    val SongTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.1).sp
    )

    val FeaturedSongTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.3).sp
    )

    val SongSubtitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.05.sp
    )

    val SearchInput: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp
    )
}
