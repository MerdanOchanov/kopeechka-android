package app.kopeechka.finance.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Тонкие иконки в духе Lucide, stroke 1.5 — как требует Industry. */
object Icons {
    private fun lucide(name: String, vararg paths: String, stroke: Float = 1.5f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach {
            b.addPath(
                pathData = addPathNodes(it),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val Gear = lucide(
        "gear",
        "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
        "M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-2.87 1.2V21a2 2 0 1 1-4 0v-.07A1.7 1.7 0 0 0 7 19.4a1.7 1.7 0 0 0-1.87.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.7 1.7 0 0 0 3 15a1.7 1.7 0 0 0-1.2-2.87H1.7a2 2 0 1 1 0-4h.1A1.7 1.7 0 0 0 3.6 7a1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.7 1.7 0 0 0 9 3V2.7a2 2 0 1 1 4 0v.1A1.7 1.7 0 0 0 17 3.6a1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.7 1.7 0 0 0 21 9h.3a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.8 2z",
    )
    val Moon = lucide("moon", "M20 14.5A8 8 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5z")
    val Sun = lucide("sun", "M12 4v2M12 18v2M4 12H2M22 12h-2M6.3 6.3 4.9 4.9M19.1 19.1l-1.4-1.4M6.3 17.7l-1.4 1.4M19.1 4.9l-1.4 1.4M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z")
    val Back = lucide("back", "M15 18l-6-6 6-6", stroke = 1.8f)
    val Chevron = lucide("chevron", "M9 6l6 6-6 6")
    val Forward = lucide("forward", "M9 6l6 6-6 6", stroke = 1.8f)
    val Wallet = lucide("wallet", "M3 7h18v11H3zM3 11h18")
    val Tags = lucide("tags", "M4 5h7v7H4zM13 5h7v7h-7zM4 14h7v5H4zM13 14h7v5h-7z")
    val Target = lucide("target", "M12 21s7-5.2 7-10a7 7 0 1 0-14 0c0 4.8 7 10 7 10z", "M12 13a2 2 0 1 0 0-4 2 2 0 0 0 0 4z")
    val Cloud = lucide("cloud", "M17.5 19H7a5 5 0 1 1 .9-9.9A6 6 0 0 1 19 11a4 4 0 0 1-1.5 8z", "M12 12v5M9.5 14.5 12 12l2.5 2.5")
    val Bell = lucide("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.9 1.9 0 0 0 3.4 0")
    val Spark = lucide("spark", "M12 3v4M12 17v4M3 12h4M17 12h4M6.3 6.3l2.5 2.5M15.2 15.2l2.5 2.5M6.3 17.7l2.5-2.5M15.2 8.8l2.5-2.5")
    val User = lucide("user", "M4 20a8 8 0 0 1 16 0M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z")
    val Trash = lucide("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
    val Rate = lucide("rate", "M4 7h13l-3-3M20 17H7l3 3")
    val Clock = lucide("clock", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M12 7.5V12l3 2")
    val Bag = lucide("bag", "M4.5 8h15l-1.2 12H5.7L4.5 8z", "M9 8V6.2a3 3 0 0 1 6 0V8")
}
