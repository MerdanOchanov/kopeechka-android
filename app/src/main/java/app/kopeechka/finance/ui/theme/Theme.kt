package app.kopeechka.finance.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.FontStyle
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.R

/** Токены дизайн-системы Industry (styles.css) + тёмная тема из прототипа «Копеечки». */
@Immutable
data class KopeechkaColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val divider: Color,
    val soft: Color,
    val n100: Color,
    val n200: Color,
    val n300: Color,
    val n400: Color,
    val n500: Color,
    val n600: Color,
    val n700: Color,
    val a200: Color,
    val a300: Color,
    val a600: Color,
    val a700: Color,
    val a800: Color,
    val a900: Color,
    val accent: Color,
    val hero: Color,
    val onAccent: Color,
    val hairline: Color,
    val danger: Color,
    val scrim: Color,
    val isDark: Boolean,
)

private val Ink = Color(0xFF1D1F20)
private val Paper = Color(0xFFF2F2F3)
private val DarkInk = Color(0xFFE6EAEC)

val LightColors = KopeechkaColors(
    bg = Paper,
    surface = Color(0xFFE9E9EA),
    text = Ink,
    divider = Ink.copy(alpha = 0.16f),
    soft = Ink.copy(alpha = 0.08f),
    n100 = Color(0xFFF5F5F8),
    n200 = Color(0xFFE7E7EA),
    n300 = Color(0xFFD4D4D7),
    n400 = Color(0xFFB7B7BA),
    n500 = Color(0xFF98989B),
    n600 = Color(0xFF7A7A7D),
    n700 = Color(0xFF5D5D60),
    a200 = Color(0xFFD6EBFF),
    a300 = Color(0xFFB5D9FD),
    a600 = Color(0xFF597EA3),
    a700 = Color(0xFF416180),
    a800 = Color(0xFF2C455D),
    a900 = Color(0xFF1D2D3D),
    accent = Color(0xFF5980A6),
    hero = Color(0xFF1D2D3D),
    onAccent = Paper,
    hairline = Paper.copy(alpha = 0.26f),
    danger = Color(0xFF8A3B3B),
    scrim = Color(0xFF0D1114).copy(alpha = 0.55f),
    isDark = false,
)

val DarkColors = LightColors.copy(
    bg = Color(0xFF14181B),
    surface = Color(0xFF1C2125),
    text = DarkInk,
    divider = DarkInk.copy(alpha = 0.22f),
    soft = DarkInk.copy(alpha = 0.08f),
    n100 = Color(0xFF1B2024),
    n200 = Color(0xFF232A2F),
    n400 = Color(0xFF4A555C),
    n500 = Color(0xFF7B868D),
    n600 = Color(0xFF97A2A9),
    n700 = Color(0xFFB6C0C6),
    a200 = Color(0xFF2B3D4E),
    a300 = Color(0xFFA9C6E0),
    a600 = Color(0xFF7BA0C4),
    a700 = Color(0xFF93B4D3),
    a800 = Color(0xFFAAC6DE),
    a900 = Color(0xFF22384A),
    hero = Color(0xFF22384A),
    onAccent = DarkInk,
    hairline = DarkInk.copy(alpha = 0.24f),
    danger = Color(0xFFD08A8A),
    isDark = true,
)

/** Оттенки для полосы долей в отчётах (ramp акцента, от тёмного к светлому). */
val Shades = listOf(0xFF1D2D3D, 0xFF2C455D, 0xFF416180, 0xFF597EA3, 0xFF749DC4, 0xFF94BCE3, 0xFFB5D9FD).map { Color(it) }

/**
 * Barlow Condensed (заголовки, цифры) и Barlow (текст). В Barlow нет кириллицы,
 * поэтому на Android 10+ русские буквы берутся из системного sans-serif-condensed / sans-serif,
 * а не из обычного Roboto — заголовки остаются узкими.
 */
object KopeechkaFonts {
    var heading: FontFamily = FontFamily.SansSerif
        private set
    var body: FontFamily = FontFamily.SansSerif
        private set

    fun init(ctx: Context) {
        heading = build(ctx, R.font.barlow_condensed_semibold, 600, "sans-serif-condensed")
        body = build(ctx, R.font.barlow_regular, 400, "sans-serif")
    }

    private fun build(ctx: Context, res: Int, weight: Int, fallback: String): FontFamily = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val font = android.graphics.fonts.Font.Builder(ctx.resources, res).setWeight(weight).build()
            val family = android.graphics.fonts.FontFamily.Builder(font).build()
            val tf = Typeface.CustomFallbackBuilder(family)
                .setSystemFallback(fallback)
                .setStyle(FontStyle(weight, FontStyle.FONT_SLANT_UPRIGHT))
                .build()
            FontFamily(tf)
        } else {
            FontFamily(Font(res, FontWeight(weight)))
        }
    }.getOrDefault(FontFamily.SansSerif)
}

val LocalColors = staticCompositionLocalOf { LightColors }
val LocalLang = staticCompositionLocalOf { app.kopeechka.finance.data.Lang.RU }

object T {
    val c: KopeechkaColors @Composable get() = LocalColors.current

    /** Язык интерфейса: `T.l.t("home.total")`. */
    val l: app.kopeechka.finance.data.Lang @Composable get() = LocalLang.current

    fun h(size: TextUnit, color: Color, spacing: TextUnit = 0.sp, lineHeight: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = KopeechkaFonts.heading, fontSize = size, color = color, letterSpacing = spacing, lineHeight = lineHeight)

    fun b(size: TextUnit, color: Color, spacing: TextUnit = 0.sp, lineHeight: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = KopeechkaFonts.body, fontSize = size, color = color, letterSpacing = spacing, lineHeight = lineHeight)

    /** Мелкая подпись капсом: 10px, letter-spacing .16em */
    fun kicker(color: Color) = b(10.sp, color, 0.16.em)
}

@Composable
fun KopeechkaTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalColors provides if (dark) DarkColors else LightColors, content = content)
}
