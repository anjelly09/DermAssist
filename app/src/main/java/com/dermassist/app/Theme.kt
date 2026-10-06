package com.dermassist.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Parchment = Color(0xFFFEFFFC)
val Linen = Color(0xFFF9FAF7)
val Ink = Color(0xFF2C2C2C)
val Ash = Color(0xFF646464)
val Mist = Color(0xFFDEE2DE)
val Dusk = Color(0xFF1F1F29)
val Signal = Color(0xFF41A1CF)

@Composable
fun DermTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Dusk, onPrimary = Color.White,
            background = Parchment, onBackground = Ink, surface = Color.White,
            onSurface = Ink, surfaceVariant = Linen, onSurfaceVariant = Ash,
            outline = Mist, secondary = Color(0xFF0081C0)),
        typography = Typography(
            displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-1).sp),
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 39.sp, letterSpacing = (-0.6).sp),
            headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 27.sp, lineHeight = 32.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
            labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp),
        ), content = content,
    )
}
