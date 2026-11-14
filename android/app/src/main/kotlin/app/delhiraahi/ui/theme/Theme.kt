package app.delhiraahi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Same design tokens as the Expo app (mobile/src/lib/theme.ts): monochrome "ride app" look,
 * colour reserved for crowd levels.
 */
@Immutable
data class CrowdColor(val fg: Color, val bg: Color, val line: Color)

@Immutable
data class Tokens(
    val dark: Boolean,
    val bg: Color, val surface: Color, val surface2: Color, val card: Color,
    val text: Color, val muted: Color, val border: Color,
    val primary: Color, val onPrimary: Color, val accent: Color, val danger: Color,
    val low: CrowdColor, val moderate: CrowdColor, val high: CrowdColor, val veryHigh: CrowdColor, val unknown: CrowdColor,
)

private val LightTokens = Tokens(
    dark = false,
    bg = Color(0xFFFFFFFF), surface = Color(0xFFF3F3F3), surface2 = Color(0xFFE8E8E8), card = Color(0xFFFFFFFF),
    text = Color(0xFF000000), muted = Color(0xFF5E5E5E), border = Color(0xFFE5E5E5),
    primary = Color(0xFF000000), onPrimary = Color(0xFFFFFFFF), accent = Color(0xFF0F766E), danger = Color(0xFFB42318),
    low = CrowdColor(Color(0xFF0E7A3B), Color(0xFFDFF5E7), Color(0xFF16A34A)),
    moderate = CrowdColor(Color(0xFF8A5A00), Color(0xFFFFF1CC), Color(0xFFD19A00)),
    high = CrowdColor(Color(0xFFB33D00), Color(0xFFFFE4D1), Color(0xFFEA580C)),
    veryHigh = CrowdColor(Color(0xFFB11B1B), Color(0xFFFDE0E0), Color(0xFFDC2626)),
    unknown = CrowdColor(Color(0xFF4B5563), Color(0xFFECEDEF), Color(0xFF9CA3AF)),
)

private val DarkTokens = Tokens(
    dark = true,
    bg = Color(0xFF000000), surface = Color(0xFF1C1C1E), surface2 = Color(0xFF2C2C2E), card = Color(0xFF141414),
    text = Color(0xFFFFFFFF), muted = Color(0xFFA1A1A6), border = Color(0xFF2C2C2E),
    primary = Color(0xFFFFFFFF), onPrimary = Color(0xFF000000), accent = Color(0xFF2DD4BF), danger = Color(0xFFF87171),
    low = CrowdColor(Color(0xFF4ADE80), Color(0xFF12301F), Color(0xFF22C55E)),
    moderate = CrowdColor(Color(0xFFFACC15), Color(0xFF3A2F0B), Color(0xFFEAB308)),
    high = CrowdColor(Color(0xFFFB923C), Color(0xFF3B2210), Color(0xFFF97316)),
    veryHigh = CrowdColor(Color(0xFFF87171), Color(0xFF3D1515), Color(0xFFEF4444)),
    unknown = CrowdColor(Color(0xFFCBD5E1), Color(0xFF2A313B), Color(0xFF6B7280)),
)

/** Read tokens with `LocalExtra.current` (name kept for existing call sites). */
val LocalExtra = staticCompositionLocalOf { LightTokens }

/** Spacing and radii, same scale as the Expo app. */
object Space { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp; val xxl = 32.dp }
object Radius { val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp }
val MaxWidth = 560.dp

/** h1 → headlineLarge, h2 → headlineSmall, h3 → titleMedium, body → bodyLarge, small → bodyMedium, tiny → labelSmall. */
private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

private fun scheme(t: Tokens) = if (t.dark) darkColorScheme(
    primary = t.primary, onPrimary = t.onPrimary, secondary = t.accent,
    background = t.bg, onBackground = t.text, surface = t.card, onSurface = t.text,
    surfaceVariant = t.surface, onSurfaceVariant = t.muted, surfaceContainer = t.card, surfaceContainerHigh = t.surface2,
    outline = t.border, error = t.danger,
) else lightColorScheme(
    primary = t.primary, onPrimary = t.onPrimary, secondary = t.accent,
    background = t.bg, onBackground = t.text, surface = t.card, onSurface = t.text,
    surfaceVariant = t.surface, onSurfaceVariant = t.muted, surfaceContainer = t.card, surfaceContainerHigh = t.surface2,
    outline = t.border, error = t.danger,
)

@Composable
fun DelhiRaahiTheme(dark: Boolean, content: @Composable () -> Unit) {
    val t = if (dark) DarkTokens else LightTokens
    CompositionLocalProvider(LocalExtra provides t) {
        MaterialTheme(
            colorScheme = scheme(t), typography = Type,
            shapes = Shapes(small = RoundedCornerShape(Radius.sm), medium = RoundedCornerShape(Radius.md), large = RoundedCornerShape(Radius.xl)),
            content = content,
        )
    }
}
