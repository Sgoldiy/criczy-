package com.criczy.app.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.criczy.app.R
import com.criczy.app.core.data.ThemeMode

/** Condensed scoreboard face for scores, headings and anything numeric that must be read fast. */
val ScoreFont = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) =
    Font(R.font.manrope, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val BodyFont = FontFamily(
    manrope(FontWeight.Normal), manrope(FontWeight.Medium), manrope(FontWeight.SemiBold), manrope(FontWeight.Bold),
)

private val CriczyTypography = Typography().run {
    fun TextStyle.score(weight: FontWeight, spacing: Double = 0.0) =
        copy(fontFamily = ScoreFont, fontWeight = weight, letterSpacing = spacing.sp)
    fun TextStyle.body(weight: FontWeight = FontWeight.Normal) = copy(fontFamily = BodyFont, fontWeight = weight)
    copy(
        displayLarge = displayLarge.score(FontWeight.Bold),
        displayMedium = displayMedium.score(FontWeight.Bold),
        displaySmall = displaySmall.score(FontWeight.Bold),
        headlineLarge = headlineLarge.score(FontWeight.Bold),
        headlineMedium = headlineMedium.score(FontWeight.Bold),
        headlineSmall = headlineSmall.score(FontWeight.SemiBold),
        titleLarge = titleLarge.score(FontWeight.SemiBold, 0.2),
        titleMedium = titleMedium.body(FontWeight.SemiBold),
        titleSmall = titleSmall.body(FontWeight.SemiBold),
        bodyLarge = bodyLarge.body(),
        bodyMedium = bodyMedium.body(),
        bodySmall = bodySmall.body(),
        labelLarge = labelLarge.body(FontWeight.SemiBold),
        labelMedium = labelMedium.body(FontWeight.SemiBold),
        labelSmall = labelSmall.body(FontWeight.SemiBold),
    )
}

private val CriczyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CriczyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    SystemBarIcons(dark)
    CompositionLocalProvider(LocalCricketColors provides if (dark) DarkCricket else LightCricket) {
        MaterialTheme(colorScheme = scheme, typography = CriczyTypography, shapes = CriczyShapes, content = content)
    }
}

val cricketColors: CricketColors
    @Composable get() = LocalCricketColors.current

/**
 * White clock, battery and navigation icons on the dark theme, dark ones on the light theme. Follows the
 * app's own theme setting: `enableEdgeToEdge()` alone follows the system's, which is wrong when they differ.
 */
@Composable
private fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        var context = view.context
        while (context !is Activity && context is ContextWrapper) context = context.baseContext
        val window = (context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
