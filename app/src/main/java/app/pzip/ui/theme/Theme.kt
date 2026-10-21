package app.pzip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.pzip.core.Accent
import app.pzip.core.FileKind
import app.pzip.core.Prefs
import app.pzip.core.ThemeMode

/** pzip palette: black & white base with one soft pastel accent. */
@Immutable
data class PzipColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val card: Color,
    val cardStrong: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val ink: Color,
    val onInk: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val success: Color,
)

private data class AccentSet(val accent: Color, val soft: Color, val onSoft: Color, val darkAccent: Color, val darkSoft: Color, val darkOnSoft: Color)

private fun accentSet(a: Accent) = when (a) {
    Accent.SKY -> AccentSet(Color(0xFF4A9FD8), Color(0xFFE4F2FC), Color(0xFF0F3D5E), Color(0xFF8CC8F0), Color(0xFF1A2C38), Color(0xFFCDE7F8))
    Accent.MINT -> AccentSet(Color(0xFF34A574), Color(0xFFE2F5EB), Color(0xFF0E4430), Color(0xFF86D9B2), Color(0xFF173027), Color(0xFFCDF0DE))
    Accent.LAVENDER -> AccentSet(Color(0xFF8170E3), Color(0xFFEEEBFC), Color(0xFF2E2368), Color(0xFFB7ACF5), Color(0xFF26223D), Color(0xFFE1DCFB))
    Accent.PEACH -> AccentSet(Color(0xFFE0875D), Color(0xFFFCEDE4), Color(0xFF5C2A12), Color(0xFFF2B595), Color(0xFF3A271E), Color(0xFFF9DECF))
}

fun accentPreview(a: Accent): Pair<Color, Color> = accentSet(a).let { it.accent to it.soft }

fun pzipColors(dark: Boolean, accent: Accent): PzipColors {
    val a = accentSet(accent)
    return if (!dark) PzipColors(
        dark = false,
        bg = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        card = Color(0xFFF6F6F4),
        cardStrong = Color(0xFFEDEDEA),
        border = Color(0xFFE8E8E4),
        text = Color(0xFF141413),
        textMuted = Color(0xFF66665F),
        textFaint = Color(0xFF9E9E97),
        ink = Color(0xFF141413),
        onInk = Color(0xFFFFFFFF),
        accent = a.accent,
        accentSoft = a.soft,
        onAccentSoft = a.onSoft,
        danger = Color(0xFFD6453D),
        dangerSoft = Color(0xFFFDECEA),
        success = Color(0xFF2E9E6A),
    ) else PzipColors(
        dark = true,
        bg = Color(0xFF111111),
        surface = Color(0xFF161616),
        card = Color(0xFF1D1D1C),
        cardStrong = Color(0xFF262625),
        border = Color(0xFF2C2C2A),
        text = Color(0xFFF3F3F0),
        textMuted = Color(0xFFA6A69F),
        textFaint = Color(0xFF71716B),
        ink = Color(0xFFF3F3F0),
        onInk = Color(0xFF111111),
        accent = a.darkAccent,
        accentSoft = a.darkSoft,
        onAccentSoft = a.darkOnSoft,
        danger = Color(0xFFF07C74),
        dangerSoft = Color(0xFF3A1F1D),
        success = Color(0xFF6FD3A2),
    )
}

/** Soft tile colors per file type, used for file icons. */
fun kindTint(kind: FileKind, c: PzipColors): Pair<Color, Color> {
    val (fg, bg) = when (kind) {
        FileKind.FOLDER -> return c.accent to c.accentSoft
        FileKind.ARCHIVE -> Color(0xFF8C6BD9) to Color(0xFFF0EBFC)
        FileKind.IMAGE -> Color(0xFF2E9E6A) to Color(0xFFE3F5EC)
        FileKind.VIDEO -> Color(0xFFD6574D) to Color(0xFFFCEAE8)
        FileKind.AUDIO -> Color(0xFFDA7B2E) to Color(0xFFFCEFE3)
        FileKind.PDF -> Color(0xFFC94A4A) to Color(0xFFFBE9E9)
        FileKind.DOCUMENT -> Color(0xFF3F7FD6) to Color(0xFFE6EFFC)
        FileKind.SHEET -> Color(0xFF2C9A5B) to Color(0xFFE2F4E9)
        FileKind.SLIDES -> Color(0xFFD08A1E) to Color(0xFFFBF1DF)
        FileKind.APK -> Color(0xFF55A630) to Color(0xFFEAF5E2)
        else -> Color(0xFF6B6B66) to Color(0xFFF0F0EE)
    }
    return if (c.dark) fg.copy(alpha = 1f).let { Color(it.red * 0.6f + 0.4f, it.green * 0.6f + 0.4f, it.blue * 0.6f + 0.4f) } to fg.copy(alpha = 0.16f) else fg to bg
}

val LocalPzip = staticCompositionLocalOf { pzipColors(false, Accent.SKY) }

object P {
    val c: PzipColors @Composable get() = LocalPzip.current
}

val Serif = FontFamily.Serif

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 23.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp),
)

@Composable
fun isDarkTheme(): Boolean = when (Prefs.themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun PzipTheme(forceDark: Boolean? = null, content: @Composable () -> Unit) {
    val dark = forceDark ?: isDarkTheme()
    val c = pzipColors(dark, Prefs.accent)
    val scheme = if (dark) darkColorScheme(
        primary = c.ink, onPrimary = c.onInk, secondary = c.accent, onSecondary = c.onInk,
        secondaryContainer = c.accentSoft, onSecondaryContainer = c.onAccentSoft,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.card, onSurfaceVariant = c.textMuted, outline = c.border, outlineVariant = c.border,
        surfaceContainer = c.card, surfaceContainerHigh = c.cardStrong, surfaceContainerHighest = c.cardStrong,
        surfaceContainerLow = c.surface, surfaceContainerLowest = c.bg, error = c.danger,
    ) else lightColorScheme(
        primary = c.ink, onPrimary = c.onInk, secondary = c.accent, onSecondary = Color.White,
        secondaryContainer = c.accentSoft, onSecondaryContainer = c.onAccentSoft,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.card, onSurfaceVariant = c.textMuted, outline = c.border, outlineVariant = c.border,
        surfaceContainer = c.card, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.cardStrong,
        surfaceContainerLow = c.surface, surfaceContainerLowest = c.bg, error = c.danger,
    )
    CompositionLocalProvider(LocalPzip provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
