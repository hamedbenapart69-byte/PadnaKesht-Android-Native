package com.padnakasht.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * PadnaKesht visual system: orchard-inspired evergreen, warm harvest accent,
 * restrained surfaces and consistent rounded geometry.
 */
private val Orchard = Color(0xFF176B55)
private val OrchardDeep = Color(0xFF0D493A)
private val Harvest = Color(0xFFC18A42)
private val CanvasLight = Color(0xFFF5F7F4)
private val SurfaceLight = Color(0xFFFFFFFF)
private val Ink = Color(0xFF172923)
private val MutedInk = Color(0xFF596B63)
private val Danger = Color(0xFFB5473C)
private val SuccessTint = Color(0xFFE4F2EA)
private val ExpenseTint = Color(0xFFF9E9E5)

private val PadnaLightColors = lightColorScheme(
    primary = Orchard,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EDE2),
    onPrimaryContainer = OrchardDeep,
    secondary = Harvest,
    onSecondary = Color(0xFF2C1A03),
    secondaryContainer = Color(0xFFF5E6CE),
    onSecondaryContainer = Color(0xFF4B3212),
    background = CanvasLight,
    onBackground = Ink,
    surface = SurfaceLight,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9EFEB),
    onSurfaceVariant = MutedInk,
    outline = Color(0xFF87968E),
    outlineVariant = Color(0xFFD5DED8),
    error = Danger,
    onError = Color.White,
    errorContainer = ExpenseTint,
    onErrorContainer = Color(0xFF70251F)
)

private val PadnaDarkColors = darkColorScheme(
    primary = Color(0xFF8CD3B5),
    onPrimary = Color(0xFF063B2C),
    primaryContainer = Color(0xFF14523F),
    onPrimaryContainer = Color(0xFFC7F1DD),
    secondary = Color(0xFFE8BD7F),
    onSecondary = Color(0xFF3E2607),
    secondaryContainer = Color(0xFF5B401D),
    onSecondaryContainer = Color(0xFFFFE7C5),
    background = Color(0xFF101713),
    onBackground = Color(0xFFE0E9E2),
    surface = Color(0xFF171F1A),
    onSurface = Color(0xFFE0E9E2),
    surfaceVariant = Color(0xFF26342C),
    onSurfaceVariant = Color(0xFFB8C8BD),
    outline = Color(0xFF82958A),
    outlineVariant = Color(0xFF3A4A40),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF7E2D26),
    onErrorContainer = Color(0xFFFFDAD4)
)

private val PadnaTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(letterSpacing = (-0.4).dp),
        headlineMedium = base.headlineMedium.copy(letterSpacing = (-0.2).dp),
        titleLarge = base.titleLarge.copy(letterSpacing = 0.dp),
        bodyLarge = base.bodyLarge.copy(lineHeight = 26.dp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 23.dp),
        labelLarge = base.labelLarge.copy(letterSpacing = 0.1.dp)
    )
}

private val PadnaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun PadnaKeshtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PadnaDarkColors else PadnaLightColors,
        typography = PadnaTypography,
        shapes = PadnaShapes,
        content = content
    )
}

/** Semantic brand tints for financial movement states. */
object PadnaFinancialColors {
    val incomeTint: Color get() = SuccessTint
    val expenseTint: Color get() = ExpenseTint
}
