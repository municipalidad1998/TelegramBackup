package com.telegrambackup.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Premium dark palette (always dark — no light mode, no dynamic color) ──────
private val Background   = Color(0xFF0B0D14)
private val Surface      = Color(0xFF131720)
private val SurfaceVar   = Color(0xFF1C2133)
private val Primary      = Color(0xFF4D9FFF)   // bright premium blue
private val OnPrimary    = Color(0xFF001D3D)
private val PrimaryCont  = Color(0xFF003876)
private val Secondary    = Color(0xFF1DB954)   // Spotify-green for "uploaded"
private val OnSecondary  = Color(0xFF000000)
private val SecondaryCont= Color(0xFF004D21)
private val Error        = Color(0xFFFF453A)
private val Outline      = Color(0xFF2A3347)

private val DarkColorScheme = darkColorScheme(
    primary              = Primary,
    onPrimary            = Color.White,
    primaryContainer     = PrimaryCont,
    onPrimaryContainer   = Color(0xFFD4E8FF),
    secondary            = Secondary,
    onSecondary          = OnSecondary,
    secondaryContainer   = SecondaryCont,
    onSecondaryContainer = Color(0xFFB3F0CA),
    tertiary             = Color(0xFFFFB74D),
    onTertiary           = Color(0xFF1A0F00),
    tertiaryContainer    = Color(0xFF3D2800),
    onTertiaryContainer  = Color(0xFFFFDDB3),
    background           = Background,
    onBackground         = Color(0xFFE8EDF5),
    surface              = Surface,
    onSurface            = Color(0xFFE8EDF5),
    surfaceVariant       = SurfaceVar,
    onSurfaceVariant     = Color(0xFF8A97B0),
    error                = Error,
    onError              = Color.White,
    errorContainer       = Color(0xFF3B1018),
    onErrorContainer     = Color(0xFFFFB3B0),
    outline              = Outline,
    outlineVariant       = Color(0xFF1E2738),
    scrim                = Color(0xFF000000)
)

private val AppTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.Black,  fontSize = 57.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 45.sp),
    displaySmall  = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 36.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium= TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge    = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 22.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.15.sp),
    titleSmall    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp),
    bodyLarge     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = 0.5.sp),
    bodyMedium    = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 0.25.sp),
    bodySmall     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelLarge    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp)
)

@Composable
fun TelegramBackupTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
