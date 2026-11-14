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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.delhiraahi.R

/**
 * "Time ribbons" look: warm paper, ink text, a sandstone-red accent (Delhi's red sandstone),
 * and a crowd scale teal → amber → brick that also steps in lightness, so it still reads for
 * colour-blind users. Dark mode is a deep night blue rather than black.
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
    bg = Color(0xFFF5EFE4), surface = Color(0xFFEBE3D4), surface2 = Color(0xFFDFD4C1), card = Color(0xFFFBF8F2),
    text = Color(0xFF221C18), muted = Color(0xFF6E6259), border = Color(0xFFDCD1BE),
    primary = Color(0xFFA63D2A), onPrimary = Color(0xFFFFF8F0), accent = Color(0xFF2E5E63), danger = Color(0xFF9B1C1C),
    low = CrowdColor(Color(0xFF0E5F5A), Color(0xFFD7ECE8), Color(0xFF2A9D8F)),
    moderate = CrowdColor(Color(0xFF6E4A00), Color(0xFFF6E6BE), Color(0xFFE2A832)),
    high = CrowdColor(Color(0xFF853611), Color(0xFFF4DAC6), Color(0xFFD2652A)),
    veryHigh = CrowdColor(Color(0xFF7A1A1F), Color(0xFFF0D2CF), Color(0xFF8E2430)),
    unknown = CrowdColor(Color(0xFF575049), Color(0xFFE6DFD3), Color(0xFFA49B8F)),
)

private val DarkTokens = Tokens(
    dark = true,
    bg = Color(0xFF0D1524), surface = Color(0xFF16213A), surface2 = Color(0xFF213050), card = Color(0xFF121C30),
    text = Color(0xFFF2ECE1), muted = Color(0xFFA3ADBF), border = Color(0xFF25344F),
    primary = Color(0xFFE2795F), onPrimary = Color(0xFF1C0E09), accent = Color(0xFF86CFC6), danger = Color(0xFFF2877D),
    low = CrowdColor(Color(0xFF6FD3C5), Color(0xFF113230), Color(0xFF3BB0A2)),
    moderate = CrowdColor(Color(0xFFF3C568), Color(0xFF392D12), Color(0xFFE6B044)),
    high = CrowdColor(Color(0xFFF3A374), Color(0xFF3B2416), Color(0xFFE07A3F)),
    veryHigh = CrowdColor(Color(0xFFF58A86), Color(0xFF3D1A1E), Color(0xFFD9534F)),
    unknown = CrowdColor(Color(0xFFC6CCD7), Color(0xFF243048), Color(0xFF75839A)),
)

/** Read tokens with `LocalExtra.current` (name kept for existing call sites). */
val LocalExtra = staticCompositionLocalOf { LightTokens }

/** Spacing and radii. */
object Space { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp; val xxl = 32.dp }
object Radius { val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp }
val MaxWidth = 560.dp

/** Mukta: one family for English and Devanagari (SIL Open Font License, bundled in res/font). */
val Mukta = FontFamily(
    Font(R.font.mukta_regular, FontWeight.Normal),
    Font(R.font.mukta_medium, FontWeight.Medium),
    Font(R.font.mukta_semibold, FontWeight.SemiBold),
    Font(R.font.mukta_bold, FontWeight.Bold),
)

/** Same-width digits, so clock times line up. Use as `style.copy(fontFeatureSettings = TabularNums)`. */
const val TabularNums = "tnum"

/** h1 → headlineLarge, h2 → headlineSmall, h3 → titleMedium, body → bodyLarge, small → bodyMedium, tiny → labelSmall. */
private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Mukta, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = Mukta, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Mukta, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Mukta, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = Mukta, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = Mukta, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Mukta, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Mukta, fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = Mukta, fontSize = 13.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Mukta, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = Mukta, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = Mukta, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
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
