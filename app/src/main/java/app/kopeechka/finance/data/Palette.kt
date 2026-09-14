package app.kopeechka.finance.data

/** Палитра для категорий: приглушённые тона, которые не спорят со стальным акцентом Industry. */
object Palette {
    data class Swatch(val hex: String, val name: String)

    val COLORS = listOf(
        Swatch("#597EA3", "Стальной"),
        Swatch("#2C455D", "Ночной"),
        Swatch("#94BCE3", "Небесный"),
        Swatch("#3E8E8A", "Бирюзовый"),
        Swatch("#4F7A5B", "Хвойный"),
        Swatch("#7E8B4F", "Оливковый"),
        Swatch("#B08A4F", "Песочный"),
        Swatch("#A9762F", "Охра"),
        Swatch("#9A5B4A", "Кирпичный"),
        Swatch("#8A3B5B", "Вишнёвый"),
        Swatch("#6B4E8A", "Сливовый"),
        Swatch("#5D5D60", "Графитовый"),
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
