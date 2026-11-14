package app.delhiraahi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Uber/Rapido-style: black-and-white surfaces, one teal accent, crowd colours carry meaning. */
private val Light = lightColorScheme(
    primary = Color(0xFF000000), onPrimary = Color.White,
    secondary = Color(0xFF0F766E), onSecondary = Color.White,
    background = Color.White, onBackground = Color(0xFF111111),
    surface = Color.White, onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF3F3F3), onSurfaceVariant = Color(0xFF5E5E5E),
    surfaceContainer = Color(0xFFF6F6F6), surfaceContainerHigh = Color(0xFFEEEEEE),
    outline = Color(0xFFE2E2E2), error = Color(0xFFB91C1C),
)
private val Dark = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    secondary = Color(0xFF2DD4BF), onSecondary = Color.Black,
    background = Color.Black, onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF121212), onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1E1E1E), onSurfaceVariant = Color(0xFFA6A6A6),
    surfaceContainer = Color(0xFF1A1A1A), surfaceContainerHigh = Color(0xFF242424),
    outline = Color(0xFF2E2E2E), error = Color(0xFFF87171),
)

@Immutable
data class CrowdColor(val fg: Color, val bg: Color, val line: Color)

@Immutable
data class Extra(val dark: Boolean, val low: CrowdColor, val moderate: CrowdColor, val high: CrowdColor, val veryHigh: CrowdColor, val unknown: CrowdColor)

private val ExtraLight = Extra(
    dark = false,
    low = CrowdColor(Color(0xFF166534), Color(0xFFDCFCE7), Color(0xFF16A34A)),
    moderate = CrowdColor(Color(0xFF854D0E), Color(0xFFFEF9C3), Color(0xFFCA8A04)),
    high = CrowdColor(Color(0xFF9A3412), Color(0xFFFFEDD5), Color(0xFFEA580C)),
    veryHigh = CrowdColor(Color(0xFF991B1B), Color(0xFFFEE2E2), Color(0xFFDC2626)),
    unknown = CrowdColor(Color(0xFF525252), Color(0xFFF1F1F1), Color(0xFF9CA3AF)),
)
private val ExtraDark = Extra(
    dark = true,
    low = CrowdColor(Color(0xFF86EFAC), Color(0xFF0F2E1B), Color(0xFF22C55E)),
    moderate = CrowdColor(Color(0xFFFDE047), Color(0xFF332B07), Color(0xFFEAB308)),
    high = CrowdColor(Color(0xFFFDBA74), Color(0xFF3A1D0B), Color(0xFFF97316)),
    veryHigh = CrowdColor(Color(0xFFFCA5A5), Color(0xFF3B1010), Color(0xFFEF4444)),
    unknown = CrowdColor(Color(0xFFBDBDBD), Color(0xFF262626), Color(0xFF6B7280)),
)

val LocalExtra = staticCompositionLocalOf { ExtraLight }

private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
)

private val Shape = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(22.dp),
)

@Composable
fun DelhiRaahiTheme(dark: Boolean, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides if (dark) ExtraDark else ExtraLight) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Shape, content = content)
    }
}
