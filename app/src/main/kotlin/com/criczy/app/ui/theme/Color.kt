package com.criczy.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand: ink (night sky over a floodlit ground) and amber (the floodlights).
private val Ink = Color(0xFF0B1018)
private val InkRaised = Color(0xFF131A24)
private val InkHigh = Color(0xFF1B2431)
private val InkHighest = Color(0xFF243041)
private val Amber = Color(0xFFFFB627)
private val AmberDeep = Color(0xFF8A5300)
private val Paper = Color(0xFFF6F4EF)
private val Navy = Color(0xFF16233D)

internal val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1B00),
    primaryContainer = Color(0xFF4A3200),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFF9FB4D8),
    onSecondary = Color(0xFF0E1D36),
    secondaryContainer = Color(0xFF24344F),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = Color(0xFF6FD6C4),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF0B4A40),
    onTertiaryContainer = Color(0xFFA8F2E3),
    background = Ink,
    onBackground = Color(0xFFE7EAF0),
    surface = Ink,
    onSurface = Color(0xFFE7EAF0),
    surfaceVariant = InkHigh,
    onSurfaceVariant = Color(0xFFA9B3C3),
    surfaceContainerLowest = Color(0xFF070B11),
    surfaceContainerLow = InkRaised,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkHigh,
    surfaceContainerHighest = InkHighest,
    outline = Color(0xFF55627A),
    outlineVariant = Color(0xFF283344),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

internal val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDAE2F9),
    onPrimaryContainer = Color(0xFF0B1730),
    secondary = AmberDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2B3),
    onSecondaryContainer = Color(0xFF2B1800),
    tertiary = Color(0xFF00695B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB5EFE3),
    onTertiaryContainer = Color(0xFF00201B),
    background = Paper,
    onBackground = Color(0xFF151A22),
    surface = Paper,
    onSurface = Color(0xFF151A22),
    surfaceVariant = Color(0xFFE6E3DB),
    onSurfaceVariant = Color(0xFF4A5261),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCFBF8),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEFECE5),
    surfaceContainerHighest = Color(0xFFE6E3DB),
    outline = Color(0xFF7A8292),
    outlineVariant = Color(0xFFD9D5CB),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/** Colours with cricket meaning that Material's roles do not cover. */
@Immutable
data class CricketColors(
    val live: Color,
    val onLive: Color,
    val four: Color,
    val six: Color,
    val wicket: Color,
    val extra: Color,
    val dot: Color,
    val win: Color,
    val accent: Color,
    val onAccent: Color,
    val chartGrid: Color,
    val field: Color,
    val pitch: Color,
)

internal val DarkCricket = CricketColors(
    live = Color(0xFFFF5449), onLive = Color.White,
    four = Color(0xFF58A6FF), six = Color(0xFFC792FF), wicket = Color(0xFFFF6B5E),
    extra = Color(0xFF6FD6C4), dot = Color(0xFF55627A), win = Color(0xFF6FD68B),
    accent = Amber, onAccent = Color(0xFF2A1B00),
    chartGrid = Color(0xFF283344), field = Color(0xFF14382A), pitch = Color(0xFFCDB98A),
)

internal val LightCricket = CricketColors(
    live = Color(0xFFD32F2F), onLive = Color.White,
    four = Color(0xFF1565C0), six = Color(0xFF7B1FA2), wicket = Color(0xFFC62828),
    extra = Color(0xFF00796B), dot = Color(0xFF9AA2B1), win = Color(0xFF2E7D32),
    accent = Color(0xFFE39A00), onAccent = Color(0xFF2A1B00),
    chartGrid = Color(0xFFD9D5CB), field = Color(0xFFCFE8D5), pitch = Color(0xFFE2D2A8),
)

val LocalCricketColors = staticCompositionLocalOf { DarkCricket }

/** Parses the provider's `#RRGGBB` team colours; null when absent or malformed. */
fun parseTeamColor(hex: String?): Color? {
    val value = hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or value)
}
