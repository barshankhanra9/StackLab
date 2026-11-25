package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StackLabDarkColorScheme = darkColorScheme(
    primary = AmberAccent,
    onPrimary = OnAmber,
    primaryContainer = AmberDim,
    onPrimaryContainer = AmberAccent,
    secondary = CyanAccent,
    onSecondary = OnCyan,
    secondaryContainer = CyanDim,
    onSecondaryContainer = CyanAccent,
    tertiary = EmeraldSuccess,
    background = ObsidianBackground,
    onBackground = CrispWhite,
    surface = CharcoalSurface,
    onSurface = CrispWhite,
    surfaceVariant = ElevatedCharcoal,
    onSurfaceVariant = SecondaryText,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = SubtleSurface,
    surfaceContainer = CharcoalSurface,
    surfaceContainerHigh = ElevatedCharcoal,
    outline = HairlineBorder,
    outlineVariant = HairlineBorder,
    error = CoralError,
    errorContainer = ErrorContainerDark,
    onErrorContainer = CoralError
)

private val StackLabShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun StackLabTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StackLabDarkColorScheme,
        typography = Typography,
        shapes = StackLabShapes,
        content = content
    )
}
