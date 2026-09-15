package app.kopeechka.finance.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.FontStyle
import android.os.Build
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * В Barlow нет кириллицы, поэтому на Android 10+ русские буквы берутся из системного
 * sans-serif-condensed / sans-serif — заголовки остаются узкими, а не превращаются в Roboto.
 */
fun initAndroidFonts(ctx: Context, headingRes: Int, bodyRes: Int) {
    KopeechkaFonts.heading = build(ctx, headingRes, 600, "sans-serif-condensed")
    KopeechkaFonts.body = build(ctx, bodyRes, 400, "sans-serif")
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
