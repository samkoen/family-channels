package com.familychannels.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Teal = Color(0xFF0B6E72)
val TealDark = Color(0xFF084F52)
val TealBright = Color(0xFF14A3A8)
val TealSoft = Color(0x220B6E72)
val Coral = Color(0xFFE85D4C)
val CoralSoft = Color(0x1FE85D4C)
val Ink = Color(0xFF10151C)
val Mist = Color(0xFFEEF3F6)
val MistDeep = Color(0xFFE2EAF0)
val SurfaceWhite = Color(0xFFFAFCFD)
val Muted = Color(0xFF5C6775)
val Danger = Color(0xFFB42318)
val Success = Color(0xFF1B7A4E)

private val Scheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F1F2),
    onPrimaryContainer = TealDark,
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE5E1),
    onSecondaryContainer = Color(0xFF7A2418),
    tertiary = Color(0xFF3D4F63),
    background = Mist,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6EEF3),
    onSurfaceVariant = Muted,
    outline = Color(0xFFC2CDD8),
    outlineVariant = Color(0xFFD9E2EA),
    error = Danger,
    onError = Color.White,
)

private val Display = FontFamily.SansSerif

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        letterSpacing = (-1.6).sp,
        lineHeight = 44.sp,
        color = Ink,
    ),
    displayMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-1.2).sp,
        lineHeight = 38.sp,
        color = Ink,
    ),
    headlineLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.9).sp,
        lineHeight = 32.sp,
        color = Ink,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 28.sp,
        color = Ink,
    ),
    titleLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = (-0.3).sp,
        lineHeight = 24.sp,
        color = Ink,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = (-0.1).sp,
        lineHeight = 22.sp,
        color = Ink,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = Ink,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = Muted,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp,
        color = Muted,
    ),
)

@Composable
fun FamilyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography,
        content = content,
    )
}
