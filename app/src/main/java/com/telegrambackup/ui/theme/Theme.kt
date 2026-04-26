package com.telegrambackup.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Telegram-inspired color palette
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2AABEE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFF34D058),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1B5E20),
    onSecondaryContainer = Color(0xFFC8E6C9),
    tertiary = Color(0xFFFF6B35),
    onTertiary = Color.White,
    background = Color(0xFF0E1621),
    onBackground = Color(0xFFE4ECEF),
    surface = Color(0xFF17212B),
    onSurface = Color(0xFFE4ECEF),
    surfaceVariant = Color(0xFF1E2C3A),
    onSurfaceVariant = Color(0xFFB0BEC5),
    error = Color(0xFFFF5252),
    onError = Color.White,
    outline = Color(0xFF455A64)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2AABEE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF34D058),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = Color(0xFFFF6B35),
    onTertiary = Color.White,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    outline = Color(0xFFBDBDBD)
)

@Composable
fun TelegramBackupTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
