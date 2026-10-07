package com.ositos.fitness.ui.theme

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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ositos.fitness.R

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

object OsitoColors {
    val Orange = Color(0xFFFF7A45)
    val Pink = Color(0xFFFF4F7B)
    val Purple = Color(0xFF9B5CFF)
    val Blue = Color(0xFF3D8BFF)
    val Mint = Color(0xFF1EC8A5)
    val Yellow = Color(0xFFFFC23D)
    val Fire = Color(0xFFFF5A1F)
    val Water = Color(0xFF29B6F6)
    val Good = Color(0xFF2ECC71)
    val Warn = Color(0xFFFFB020)
    val Bad = Color(0xFFFF4D4D)
}

private val DarkColors = darkColorScheme(
    primary = OsitoColors.Orange,
    onPrimary = Color(0xFF2B1300),
    primaryContainer = Color(0xFF4A2414),
    onPrimaryContainer = Color(0xFFFFDCCB),
    secondary = OsitoColors.Purple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF34245A),
    onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = OsitoColors.Mint,
    onTertiary = Color(0xFF00382D),
    background = Color(0xFF14121C),
    onBackground = Color(0xFFF2EEF8),
    surface = Color(0xFF14121C),
    onSurface = Color(0xFFF2EEF8),
    surfaceVariant = Color(0xFF2A2638),
    onSurfaceVariant = Color(0xFFCBC3DA),
    surfaceContainer = Color(0xFF1E1B29),
    surfaceContainerHigh = Color(0xFF272335),
    surfaceContainerHighest = Color(0xFF302B40),
    surfaceContainerLow = Color(0xFF1A1724),
    outline = Color(0xFF4B4560),
    error = OsitoColors.Bad,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFF2611F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCCB),
    onPrimaryContainer = Color(0xFF3A1200),
    secondary = Color(0xFF7B3FF2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBDDFF),
    onSecondaryContainer = Color(0xFF250059),
    tertiary = Color(0xFF00A383),
    onTertiary = Color.White,
    background = Color(0xFFFFF7F0),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF7F0),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF6E6DA),
    onSurfaceVariant = Color(0xFF5A4A40),
    surfaceContainer = Color(0xFFFFEFE3),
    surfaceContainerHigh = Color(0xFFFBE7D8),
    surfaceContainerHighest = Color(0xFFF6DFCD),
    surfaceContainerLow = Color(0xFFFFF3EA),
    outline = Color(0xFFD9C2B2),
    error = Color(0xFFD32F2F),
)

private fun t(size: Int, weight: FontWeight, line: Int = (size * 1.3).toInt()) =
    TextStyle(fontFamily = Nunito, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private val OsitoTypography = Typography(
    displayLarge = t(52, FontWeight.Black),
    displayMedium = t(42, FontWeight.Black),
    displaySmall = t(34, FontWeight.Black),
    headlineLarge = t(30, FontWeight.ExtraBold),
    headlineMedium = t(26, FontWeight.ExtraBold),
    headlineSmall = t(22, FontWeight.ExtraBold),
    titleLarge = t(20, FontWeight.ExtraBold),
    titleMedium = t(17, FontWeight.Bold),
    titleSmall = t(15, FontWeight.Bold),
    bodyLarge = t(16, FontWeight.SemiBold),
    bodyMedium = t(14, FontWeight.SemiBold),
    bodySmall = t(12, FontWeight.SemiBold),
    labelLarge = t(15, FontWeight.ExtraBold),
    labelMedium = t(13, FontWeight.Bold),
    labelSmall = t(11, FontWeight.Bold),
)

private val OsitoShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun OsitosTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = OsitoTypography,
        shapes = OsitoShapes,
        content = content,
    )
}
