package com.localdev.studiox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LocalDevDarkColorScheme = darkColorScheme(
    primary = DevAccentCyan,
    onPrimary = DevBackground,
    secondary = DevAccentBlue,
    onSecondary = DevTextPrimary,
    tertiary = DevSuccess,
    background = DevBackground,
    onBackground = DevTextPrimary,
    surface = DevSurfacePrimary,
    onSurface = DevTextPrimary,
    surfaceVariant = DevSurfaceSecondary,
    onSurfaceVariant = DevTextSecondary,
    error = DevError,
    onError = DevTextPrimary
)

@Composable
fun LocalDevStudioXTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LocalDevDarkColorScheme,
        content = content
    )
}
