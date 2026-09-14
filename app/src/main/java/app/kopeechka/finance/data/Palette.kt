package app.kopeechka.finance.data

/** Палитра для категорий: приглушённые тона, которые не спорят со стальным акцентом Industry. */
object Palette {
    data class Swatch(val hex: String, val key: String) {
        fun name(l: Lang) = l.t(key)
    }

    val COLORS = listOf(
        Swatch("#597EA3", "color.steel"),
        Swatch("#2C455D", "color.night"),
        Swatch("#94BCE3", "color.sky"),
        Swatch("#3E8E8A", "color.teal"),
        Swatch("#4F7A5B", "color.pine"),
        Swatch("#7E8B4F", "color.olive"),
        Swatch("#B08A4F", "color.sand"),
        Swatch("#A9762F", "color.ochre"),
        Swatch("#9A5B4A", "color.brick"),
        Swatch("#8A3B5B", "color.cherry"),
        Swatch("#6B4E8A", "color.plum"),
        Swatch("#5D5D60", "color.graphite"),
    )

    val HEXES = COLORS.map { it.hex }

    /** Цвет по умолчанию для категории без своего цвета — по позиции в списке. */
    fun fallback(index: Int) = HEXES[index % HEXES.size]

    /** "#597EA3" -> 0xFF597EA3, иначе null. */
    fun parse(hex: String): Long? {
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 6) return null
        return runCatching { 0xFF000000L or clean.toLong(16) }.getOrNull()
    }
}
