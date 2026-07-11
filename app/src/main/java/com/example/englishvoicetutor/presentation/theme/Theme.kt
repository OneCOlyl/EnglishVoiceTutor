package com.example.englishvoicetutor.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6F4F),
    secondary = Color(0xFF6750A4)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD9B1),
    secondary = Color(0xFFCFBCFF)
)

@Composable
fun EnglishVoiceTutorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
