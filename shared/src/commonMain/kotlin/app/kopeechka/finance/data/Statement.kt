package app.kopeechka.finance.data

/** Строка выписки: дата, сумма со знаком (минус — списание), описание, валюта. */
data class StatementRow(val date: Long, val amount: Double, val text: String, val cur: String)

/**
 * Где в выписке что лежит: номера колонок. -1 — такой колонки нет.
 * Сумма бывает одной колонкой со знаком или двумя — «расход» и «приход».
 */
data class StatementLayout(
    val date: Int = -1,
    val amount: Int = -1,
    val debit: Int = -1,
    val credit: Int = -1,
    val text: Int = -1,
    val cur: Int = -1,
) {
    val ready: Boolean get() = date >= 0 && (amount >= 0 || debit >= 0 || credit >= 0)
}

/**
 * Импорт банковской выписки в CSV.
 *
 * Банки выгружают выписки по-разному: разделитель, порядок колонок, формат дат
 * и сумм. Поэтому шаблонов под конкретные банки нет — колонки узнаются по
 * заголовкам, а если не узнались, человек укажет их сам.
 */
object Statement {

    /** Разбить CSV на строки и ячейки. Разделитель — тот, которого в заголовке больше. */
    fun split(text: String): List<List<String>> {
        val clean = text.removePrefix("﻿")
        val head = clean.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val sep = listOf(';', '\t', ',').maxBy { ch -> head.count { it == ch } }
        val rows = mutableListOf<List<String>>()
        var cell = StringBuilder()
        var row = mutableListOf<String>()
        var quoted = false
        var i = 0
        while (i < clean.length) {
            val ch = clean[i]
            when {
                quoted && ch == '"' && i + 1 < clean.length && clean[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == sep -> { row += cell.toString().trim(); cell = StringBuilder() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < clean.length && clean[i + 1] == '\n') i++
                    row += cell.toString().trim()
                    if (row.any { it.isNotEmpty() }) rows += row
                    row = mutableListOf(); cell = StringBuilder()
                }
                else -> cell.append(ch)
            }
            i++
        }
        row += cell.toString().trim()
        if (row.any { it.isNotEmpty() }) rows += row
        return rows
    }

    /** Узнать колонки по заголовку. Ищем от точного к общему: «дата операции» раньше «даты». */
    fun guess(header: List<String>): StatementLayout {
        val h = header.map { it.lowercase().trim() }
        fun find(vararg words: String): Int {
            for (w in words) {
                val i = h.indexOfFirst { it == w }
                if (i >= 0) return i
            }
            for (w in words) {
                val i = h.indexOfFirst { it.contains(w) }
                if (i >= 0) return i
            }
            return -1
        }
        val debit = find("расход", "списание", "дебет", "debit", "withdrawal", "chiqim", "шығыс")
        val credit = find("приход", "зачисление", "поступление", "кредит", "credit", "deposit", "kirim", "кіріс")
        return StatementLayout(
            date = find("дата операции", "дата", "date", "sana", "күні", "күн"),
            amount = find("сумма операции", "сумма в валюте операции", "сумма", "amount", "summa", "сома", "sum"),
            debit = debit,
            credit = credit,
            text = find("описание", "назначение", "детали", "получатель", "description", "details", "merchant", "tavsif", "сипаттама"),
            cur = find("валюта операции", "валюта", "currency", "valyuta"),
        )
    }

    fun parse(rows: List<List<String>>, layout: StatementLayout, defaultCur: String): List<StatementRow> =
        rows.drop(1).mapNotNull { r ->
            val date = r.getOrNull(layout.date)?.let(::date) ?: return@mapNotNull null
            val amount = when {
                layout.amount >= 0 -> r.getOrNull(layout.amount)?.let(::amount)
                else -> {
                    val out = r.getOrNull(layout.debit)?.let(::amount)?.let { -kotlin.math.abs(it) }
                    val inc = r.getOrNull(layout.credit)?.let(::amount)?.let { kotlin.math.abs(it) }
                    out?.takeIf { it != 0.0 } ?: inc
                }
            } ?: return@mapNotNull null
            if (amount == 0.0) return@mapNotNull null
            StatementRow(
                date = date,
                amount = amount,
                text = r.getOrNull(layout.text).orEmpty().take(80),
                cur = r.getOrNull(layout.cur)?.uppercase()?.take(3)?.takeIf { it.length == 3 } ?: defaultCur,
            )
        }

    /** «17.09.2026», «17.09.2026 15:30:00», «2026-09-17», «17/09/2026». */
    fun date(raw: String): Long? {
        val s = raw.trim()
        ISO.find(s)?.let { m -> return localDateOrNull(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.toEpochDay() }
        DMY.find(s)?.let { m ->
            val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            return localDateOrNull(y, m.groupValues[2].toInt(), m.groupValues[1].toInt())?.toEpochDay()
        }
        return null
    }

    /** «−1 234,56», «-1234.56», «1 234,56 ₽», «(500.00)» — последнее в бухгалтерии значит минус. */
    fun amount(raw: String): Double? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        val negative = s.startsWith("-") || s.startsWith("−") || (s.startsWith("(") && s.endsWith(")"))
        s = s.filter { it.isDigit() || it == ',' || it == '.' }
        if (s.isEmpty()) return null
        // десятичный разделитель — последний из «,» и «.», остальные — разряды
        val last = maxOf(s.lastIndexOf(','), s.lastIndexOf('.'))
        val clean = if (last >= 0 && s.length - last - 1 in 1..2) {
            s.substring(0, last).filter { it.isDigit() } + "." + s.substring(last + 1)
        } else {
            s.filter { it.isDigit() }
        }
        val v = clean.toDoubleOrNull() ?: return null
        return if (negative) -v else v
    }

    private val ISO = Regex("(\\d{4})-(\\d{2})-(\\d{2})")
    private val DMY = Regex("(\\d{1,2})[./](\\d{1,2})[./](\\d{2,4})")
}
