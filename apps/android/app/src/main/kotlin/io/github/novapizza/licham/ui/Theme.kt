package io.github.novapizza.licham.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.R
import io.github.novapizza.licham.core.CalendarData
import io.github.novapizza.licham.core.Element

/**
 * The app's colours, read from shared/design/tokens.json (the same values as the web app):
 * base light/dark colours, overridden by the palette of the chosen element.
 */
@Immutable
class LichAmColors(private val values: Map<String, String>) {
    private fun c(name: String): Color = parse(values[name] ?: error("tokens.json has no colour '$name'"))

    val bg = c("bg"); val surface = c("surface"); val surface2 = c("surface-2"); val surface3 = c("surface-3")
    val line = c("line"); val fg = c("fg"); val fg2 = c("fg-2"); val fg3 = c("fg-3")
    val brand = c("brand"); val brandStrong = c("brand-strong"); val brandSoft = c("brand-soft"); val onBrand = c("on-brand")
    val lunar = c("lunar"); val lunarSoft = c("lunar-soft"); val gold = c("gold"); val goldSoft = c("gold-soft")
    val good = c("good"); val goodSoft = c("good-soft"); val bad = c("bad"); val badSoft = c("bad-soft")
    val clash = c("clash"); val clashSoft = c("clash-soft")
    val heroFrom = c("hero-from"); val heroVia = c("hero-via"); val heroTo = c("hero-to")
    val heroAccent = c("hero-accent"); val heroAccentInk = c("hero-accent-ink")
    /** Logo background: the light palette's value, kept in dark mode (as on the web). */
    val logo = c("logo")
    /** --shadow-tint ("r g b"), the colour of card shadows. */
    val shadowTint: Color = values["shadow-tint"]?.split(' ')?.map { it.toInt() }?.let { (r, g, b) -> Color(r, g, b) } ?: Color.Black

    companion object {
        /** "#rrggbb" → Color. */
        fun parse(hex: String): Color {
            val v = hex.removePrefix("#").toLong(16)
            return Color(0xFF000000 or v)
        }

        fun of(palette: Element, dark: Boolean): LichAmColors {
            val t = CalendarData.tokens
            val base = if (dark) t.base.dark else t.base.light
            val pal = t.palettes.getValue(palette.key)
            val p = if (dark) pal.dark else pal.light
            val logo = pal.light["logo"] ?: t.base.light.getValue("logo")
            return LichAmColors(base + p + ("logo" to logo))
        }
    }
}

/** The web app's typeface (apps/web/index.html loads Be Vietnam Pro 400–800). */
val BeVietnamPro = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
    Font(R.font.be_vietnam_pro_extrabold, FontWeight.ExtraBold),
)

/**
 * The tear-off calendar sheet's serif (TearOffSheet; the web loads Playfair Display 600, 700 and
 * 400 italic). Both files are variable fonts (wght 400–900): each Font picks its weight from them.
 */
@OptIn(ExperimentalTextApi::class)
val PlayfairDisplay = FontFamily(
    // The wght axis is set explicitly: measured 2026-11-22, without it Android drew the default
    // instance (400) for every weight.
    Font(R.font.playfair_display, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.playfair_display, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.playfair_display_italic, FontWeight.Normal, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
)

/**
 * Text as the web renders it: no Material letter spacing, line height relative to the
 * font size (Tailwind: 12px/16, 14px/20, 16px/24), half-leading above and below.
 */
private val WebText = TextStyle(
    fontFamily = BeVietnamPro,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 1.42.em,
    letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    // Browsers lay a line out at exactly its line height; Android adds font padding unless told not to.
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val WebTypography = Typography().let { t ->
    fun TextStyle.web() = merge(WebText.copy(fontSize = fontSize, fontWeight = fontWeight))
    Typography(
        displayLarge = t.displayLarge.web(), displayMedium = t.displayMedium.web(), displaySmall = t.displaySmall.web(),
        headlineLarge = t.headlineLarge.web(), headlineMedium = t.headlineMedium.web(), headlineSmall = t.headlineSmall.web(),
        titleLarge = t.titleLarge.web(), titleMedium = t.titleMedium.web(), titleSmall = t.titleSmall.web(),
        bodyLarge = WebText, bodyMedium = t.bodyMedium.web(), bodySmall = t.bodySmall.web(),
        labelLarge = t.labelLarge.web(), labelMedium = t.labelMedium.web(), labelSmall = t.labelSmall.web(),
    )
}

val LocalLichAmColors = staticCompositionLocalOf { LichAmColors.of(Element.HOA, dark = false) }

@Composable
fun LichAmTheme(palette: Element = Element.HOA, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = LichAmColors.of(palette, dark)
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.brand, onPrimary = colors.onBrand, primaryContainer = colors.brandSoft, onPrimaryContainer = colors.brand,
            background = colors.bg, onBackground = colors.fg, surface = colors.surface, onSurface = colors.fg,
            surfaceVariant = colors.surface2, onSurfaceVariant = colors.fg2, outline = colors.line, error = colors.bad,
        )
    } else {
        lightColorScheme(
            primary = colors.brand, onPrimary = colors.onBrand, primaryContainer = colors.brandSoft, onPrimaryContainer = colors.brand,
            background = colors.bg, onBackground = colors.fg, surface = colors.surface, onSurface = colors.fg,
            surfaceVariant = colors.surface2, onSurfaceVariant = colors.fg2, outline = colors.line, error = colors.bad,
        )
    }
    CompositionLocalProvider(LocalLichAmColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = WebTypography, content = content)
    }
}

/**
 * Largest system font scale the app honours (Settings → Display → Font size); applied to the
 * activity's configuration in MainActivity.attachBaseContext, so sheets and menus get it too.
 */
const val MAX_FONT_SCALE = 1.3f
