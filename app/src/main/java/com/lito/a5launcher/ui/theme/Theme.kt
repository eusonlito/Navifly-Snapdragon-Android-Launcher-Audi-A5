package com.lito.a5launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SLineRed,
    secondary = TitaniumSilver,
    tertiary = NeonGreen,
    background = DeepObsidian,
    surface = AsphaltCharcoal,
    onBackground = TitaniumSilver,
    onSurface = TitaniumSilver
)

@Composable
fun A5LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
