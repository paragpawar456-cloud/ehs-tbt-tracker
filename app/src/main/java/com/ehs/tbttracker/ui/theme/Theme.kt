package com.ehs.tbttracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "TBT Site Tracker" palette: navy header, blue-biased slate neutrals, emerald for
 * compliant/briefed, amber for attention (not reported, data flags), red for failures.
 */
@Immutable
data class EhsPalette(
    val bg: Color, val surface: Color, val surface2: Color, val ink: Color, val muted: Color, val line: Color,
    val navy: Color, val onNavy: Color, val accent: Color, val accentSoft: Color, val onAccent: Color,
    val warn: Color, val warnSoft: Color, val crit: Color, val critSoft: Color, val barTrack: Color,
    /** Emphasis colour for today's bar in the trend chart. */
    val todayBar: Color,
)

val LightPalette = EhsPalette(
    bg = Color(0xFFF2F5F8), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFE8EEF4), ink = Color(0xFF13202E),
    muted = Color(0xFF5A6A7B), line = Color(0xFFD5DEE7), navy = Color(0xFF0E2A47), onNavy = Color(0xFFF2F7FC),
    accent = Color(0xFF0B8A5F), accentSoft = Color(0xFFDDF3EA), onAccent = Color(0xFFFFFFFF),
    warn = Color(0xFFA55F00), warnSoft = Color(0xFFFBEBD2), crit = Color(0xFFB3322A), critSoft = Color(0xFFF8DEDB),
    barTrack = Color(0xFFE3EAF1), todayBar = Color(0xFF0E2A47),
)

val DarkPalette = EhsPalette(
    bg = Color(0xFF0A1520), surface = Color(0xFF112131), surface2 = Color(0xFF18293B), ink = Color(0xFFE3EBF3),
    muted = Color(0xFF93A4B6), line = Color(0xFF243A50), navy = Color(0xFF0B1D30), onNavy = Color(0xFFE3EBF3),
    accent = Color(0xFF3CC98F), accentSoft = Color(0xFF12382B), onAccent = Color(0xFF062418),
    warn = Color(0xFFF0AE52), warnSoft = Color(0xFF3A2A12), crit = Color(0xFFF08B82), critSoft = Color(0xFF3D1D1B),
    barTrack = Color(0xFF1C2F42), todayBar = Color(0xFFE3EBF3),
)

val LocalEhsPalette = staticCompositionLocalOf { LightPalette }

/** Whether the app theme is dark (follows the system unless a caller forces it). */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Shortcut: `Ehs.colors.accent`. */
object Ehs {
    val colors: EhsPalette @Composable get() = LocalEhsPalette.current
}

/** Kept for existing call sites. */
object EhsColors {
    val Navy900 = LightPalette.navy
    val Navy700 = Color(0xFF16325C)
    val Emerald600 = LightPalette.accent
    val Emerald100 = LightPalette.accentSoft
    val Amber500 = Color(0xFFF59E0B)
    val Amber100 = LightPalette.warnSoft
    val Red600 = LightPalette.crit
}

private fun scheme(p: EhsPalette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.surface2, onPrimaryContainer = p.ink,
        secondary = p.accent, onSecondary = p.onAccent, secondaryContainer = p.accentSoft, onSecondaryContainer = p.ink,
        background = p.bg, onBackground = p.ink, surface = p.surface, onSurface = p.ink,
        surfaceVariant = p.surface2, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line, error = p.crit,
    )
} else {
    lightColorScheme(
        primary = p.navy, onPrimary = p.onNavy, primaryContainer = p.surface2, onPrimaryContainer = p.ink,
        secondary = p.accent, onSecondary = p.onAccent, secondaryContainer = p.accentSoft, onSecondaryContainer = p.ink,
        background = p.bg, onBackground = p.ink, surface = p.surface, onSurface = p.ink,
        surfaceVariant = p.surface2, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line, error = p.crit,
    )
}

/** Condensed display face for headings and big numbers; mono for times and small figures. */
val DisplayFamily: FontFamily = FontFamily.SansSerif
val MonoFamily: FontFamily = FontFamily.Monospace

private val EhsTypography = Typography(
    headlineSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 12.sp, letterSpacing = 0.9.sp),
    displaySmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 34.sp),
)

private val EhsShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun EhsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    androidx.compose.runtime.CompositionLocalProvider(LocalEhsPalette provides palette, LocalDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = scheme(palette, darkTheme), typography = EhsTypography, shapes = EhsShapes, content = content)
    }
}
