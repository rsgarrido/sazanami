package io.github.rsgarrido.sazanami.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import io.github.rsgarrido.sazanami.data.preferences.AppFont
import org.junit.Assert.assertEquals
import org.junit.Test

class AppTypographyTest {
    @Test
    fun sazanamiUsesSpaceGroteskForEveryMaterialTextStyle() {
        assertTypographyFontFamily(
            typography = typographyFor(AppFont.SAZANAMI),
            expected = SpaceGroteskFontFamily
        )
    }

    @Test
    fun deviceUsesThePlatformDefaultForEveryMaterialTextStyle() {
        assertTypographyFontFamily(
            typography = typographyFor(AppFont.DEVICE),
            expected = FontFamily.Default
        )
    }

    private fun assertTypographyFontFamily(
        typography: Typography,
        expected: FontFamily
    ) {
        val styles = listOf(
            typography.displayLarge,
            typography.displayMedium,
            typography.displaySmall,
            typography.headlineLarge,
            typography.headlineMedium,
            typography.headlineSmall,
            typography.titleLarge,
            typography.titleMedium,
            typography.titleSmall,
            typography.bodyLarge,
            typography.bodyMedium,
            typography.bodySmall,
            typography.labelLarge,
            typography.labelMedium,
            typography.labelSmall
        )

        styles.forEach { style -> assertEquals(expected, style.fontFamily) }
    }
}
