package com.example.telegramtextapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = PrimaryText,
    surface = SurfaceLight,
    onSurface = TextOnSurface,
    secondary = PrimaryBlue
)

private val DarkColors = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = PrimaryText,
    surface = Color.Black,
    onSurface = PrimaryText,
    secondary = PrimaryBlue
)

@Composable
fun TelegramTextAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
