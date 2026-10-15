package com.zairvek.zairauto.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zairvek.zairauto.data.SettingsState
import com.zairvek.zairauto.data.ThemeMode

private val Violet = Color(0xFF6C4BF4)
private val VioletLight = Color(0xFFB9A8FF)
private val Coral = Color(0xFFFF7A59)

private val DarkScheme = darkColorScheme(
    primary = VioletLight,
    onPrimary = Color(0xFF22106B),
    primaryContainer = Color(0xFF3B2A99),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFFFB59F),
    secondaryContainer = Color(0xFF5C2A1A),
    tertiary = Color(0xFF7EE3C8),
    background = Color(0xFF0E0B1A),
    surface = Color(0xFF0E0B1A),
    surfaceContainer = Color(0xFF1A1630),
    surfaceContainerHigh = Color(0xFF231E3D),
    surfaceContainerHighest = Color(0xFF2D274A),
    surfaceContainerLow = Color(0xFF151125),
)

private val LightScheme = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF22106B),
    secondary = Coral,
    secondaryContainer = Color(0xFFFFDBD0),
    tertiary = Color(0xFF00897B),
    background = Color(0xFFFCFAFF),
    surface = Color(0xFFFCFAFF),
)

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.1.sp),
    )
}

@Composable
fun ZairTheme(settings: SettingsState, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    var scheme: ColorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    if (dark && settings.amoled) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
