package com.example.englishvoicetutor.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// «Тёплый минимал»: спокойный индиговый акцент и мягкая бирюза на тёплом
// off-white фоне. Поверхности тёплые, различаются мягкими тональными
// градациями (surfaceContainer*), что даёт аккуратную глубину без пестроты.
private val LightColors = lightColorScheme(
    primary = Color(0xFF4C5BC7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E3FB),
    onPrimaryContainer = Color(0xFF10164B),
    secondary = Color(0xFF2C9E95),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDEFEA),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF8A5A2B),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF9F6),
    onBackground = Color(0xFF1D1B18),
    surface = Color(0xFFFBF9F6),
    onSurface = Color(0xFF1D1B18),
    surfaceVariant = Color(0xFFECE8E1),
    onSurfaceVariant = Color(0xFF6D695F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2EC),
    surfaceContainer = Color(0xFFF1ECE5),
    surfaceContainerHigh = Color(0xFFEBE6DE),
    surfaceContainerHighest = Color(0xFFE5E0D8),
    outline = Color(0xFFD6D1C7),
    outlineVariant = Color(0xFFE7E2D9),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7BEFF),
    onPrimary = Color(0xFF1B2469),
    primaryContainer = Color(0xFF333C82),
    onPrimaryContainer = Color(0xFFE0E1FF),
    secondary = Color(0xFF7CD6CB),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF1E4E48),
    onSecondaryContainer = Color(0xFF98F2E7),
    tertiary = Color(0xFFF2BB84),
    onTertiary = Color(0xFF4A2800),
    background = Color(0xFF15140F),
    onBackground = Color(0xFFEAE6DD),
    surface = Color(0xFF15140F),
    onSurface = Color(0xFFEAE6DD),
    surfaceVariant = Color(0xFF48453D),
    onSurfaceVariant = Color(0xFFCAC5BA),
    surfaceContainerLowest = Color(0xFF0F0E0A),
    surfaceContainerLow = Color(0xFF1D1C17),
    surfaceContainer = Color(0xFF21201B),
    surfaceContainerHigh = Color(0xFF2C2A25),
    surfaceContainerHighest = Color(0xFF373530),
    outline = Color(0xFF938F86),
    outlineVariant = Color(0xFF48453D),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

// Немного «выше» стандартной M3: заголовки плотнее и выразительнее, тело —
// с чуть увеличенным межстрочным для читаемости длинных реплик репетитора.
private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)

// Крупные мягкие скругления — фирменная черта «тёплого минимала».
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun EnglishVoiceTutorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
