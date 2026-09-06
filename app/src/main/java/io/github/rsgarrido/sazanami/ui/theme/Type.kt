package io.github.rsgarrido.sazanami.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.preferences.AppFont

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold)
)

private val DeviceTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

internal fun typographyFor(appFont: AppFont): Typography {
    val fontFamily = when (appFont) {
        AppFont.SAZANAMI -> SpaceGroteskFontFamily
        AppFont.DEVICE -> FontFamily.Default
    }

    return DeviceTypography.copy(
        displayLarge = DeviceTypography.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = DeviceTypography.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = DeviceTypography.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = DeviceTypography.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = DeviceTypography.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = DeviceTypography.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = DeviceTypography.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = DeviceTypography.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = DeviceTypography.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = DeviceTypography.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = DeviceTypography.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = DeviceTypography.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = DeviceTypography.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = DeviceTypography.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = DeviceTypography.labelSmall.copy(fontFamily = fontFamily)
    )
}
