package app.kopeechka.finance.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Обмен операциями через CSV.
 *
 * Колонки: `date;type;amount;currency;account;to_account;to_amount;category;note`
 * Тип: `expense`, `income`, `transfer` (взносы на цели выгружаются как `goal` и при
 * загрузке пропускаются — их двигает сама цель).
 */
object Csv {
    val COLUMNS = listOf("date", "type", "amount", "currency", "account", "to_account", "to_amount", "category", "note")
    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE

    data class Row(
        val line: Int,
        val date: Long,
        val type: String,
        val amount: Double,
        val cur: String,
        val acc: String,
        val toAcc: String,
        val toAmount: Double?,
        val cat: String,
        val note: String,
    )

    data class Preview(
        val rows: List<Row>,
        val errors: List<String>,
        val newAccounts: List<String>,
        val newCats: List<String>,
        val skipped: Int,
    )

    // ——— выгрузка ———

    private fun esc(v: String): String =
        if (v.any { it == ';' || it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun num(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.2f", v)

    /** `from`/`to` — границы периода в днях эпохи; null — выгрузить всё. */
    fun export(d: AppData, from: Long? = null, to: Long? = null): String {
        val c = Calc(d)
        val rows = d.txs
            .filter { (from == null || it.date >= from) && (to == null || it.date <= to) }
            .sortedWith(compareBy<Tx> { it.date }.thenBy { it.id })
        val sb = StringBuilder("﻿")
        sb.append(COLUMNS.joinToString(";")).append('\n')
        rows.forEach { t ->
            val type = when (t.cat) {
                CAT_TRANSFER -> "transfer"
                CAT_GOAL -> "goal"
                else -> if (t.amount > 0) "income" else "expense"
            }
            val cells = listOf(
                LocalDate.ofEpochDay(t.date).format(DATE),
                type,
                num(t.amount),
                c.accCur(t.acc),
                c.acc(t.acc)?.name ?: "",
                c.acc(t.toAcc)?.name ?: "",
                t.toAmount?.let { num(it) } ?: "",
                if (t.cat == CAT_TRANSFER || t.cat == CAT_GOAL) "" else c.cat(t.cat).name,
                t.note.ifBlank { if (t.title == c.cat(t.cat).name) "" else t.title },
            )
            sb.append(cells.joinToString(";") { esc(it) }).append('\n')
        }
        return sb.toString()
    }

    /** Пустой файл с заголовком и парой строк-примеров. */
    fun template(d: AppData, l: Lang): String {
        val c = Calc(d)
        val acc = d.accounts.firstOrNull()?.name ?: l.t("acc.type.card")
        val acc2 = d.accounts.getOrNull(1)?.name ?: acc
        val cat = d.categories.firstOrNull { !it.income }?.name ?: l.t("demo.cat.food")
        val inc = d.categories.firstOrNull { it.income }?.name ?: l.t("demo.cat.salary")
        val cur = d.accounts.firstOrNull()?.cur ?: d.settings.mainCur
        val today = LocalDate.now().format(DATE)
        val sb = StringBuilder("﻿")
        sb.append(COLUMNS.joinToString(";")).append('\n')
        sb.append(listOf(today, "expense", "-1200", cur, acc, "", "", cat, l.t("demo.tx.grocery1")).joinToString(";") { esc(it) }).append('\n')
        sb.append(listOf(today, "income", "4500", cur, acc, "", "", inc, l.t("demo.tx.salary")).joinToString(";") { esc(it) }).append('\n')
        sb.append(listOf(today, "transfer", "-500", cur, acc, acc2, "500", "", "").joinToString(";") { esc(it) }).append('\n')
        return sb.toString()
    }

    // ——— загрузка ———

    private fun splitLine(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                ch == '"' -> quoted = !quoted
                ch == sep && !quoted -> {
                    out.add(sb.toString().trim()); sb.setLength(0)
                }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString().trim())
        return out
    }

    private fun parseDate(s: String): Long? {
        val v = s.trim()
        if (v.isEmpty()) return null
        runCatching { return LocalDate.parse(v, DATE).toEpochDay() }
        // 14.09.2026 и 14/09/2026
        val parts = v.split('.', '/', '-').mapNotNull { it.trim().toIntOrNull() }
        if (parts.size == 3 && parts[0] <= 31) {
            runCatching { return LocalDate.of(parts[2], parts[1], parts[0]).toEpochDay() }
        }
        return null
    }

    private fun parseAmount(s: String): Double? {
        val v = s.trim()
            .replace(" ", "")
            .replace(" ", "")
            .replace(",", ".")
            .replace(Regex("[^0-9.\\-+]"), "")
        if (v.isEmpty() || v == "-" || v == "+") return null
        return v.toDoubleOrNull()
    }

    fun parse(text: String, d: AppData, l: Lang): Preview {
        val clean = text.removePrefix("﻿")
        val lines = clean.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return Preview(emptyList(), emptyList(), emptyList(), emptyList(), 0)

        val sep = if (lines[0].count { it == ';' } >= lines[0].count { it == ',' }) ';' else ','
        val header = splitLine(lines[0], sep).map { it.lowercase().trim() }
        val hasHeader = header.any { it in COLUMNS }
        val idx = COLUMNS.associateWith { name -> header.indexOf(name) }

        val rows = mutableListOf<Row>()
        val errors = mutableListOf<String>()
        val newAccounts = linkedSetOf<String>()
        val newCats = linkedSetOf<String>()
        var skipped = 0

        val accNames = d.accounts.associateBy { it.name.lowercase() }
        val catNames = d.categories.associateBy { it.name.lowercase() }

        lines.forEachIndexed { i, raw ->
            if (i == 0 && hasHeader) return@forEachIndexed
            val cells = splitLine(raw, sep)
            fun cell(name: String, fallback: Int): String {
                val at = idx[name]?.takeIf { it >= 0 } ?: fallback
                return cells.getOrNull(at)?.trim().orEmpty()
            }
            val lineNo = i + 1
            if (cells.size < 3) {
                errors.add(l.t("csv.line", lineNo, l.t("csv.errColumns")))
                return@forEachIndexed
            }
            val type = cell("type", 1).lowercase()
            if (type == "goal") {
                skipped++
                return@forEachIndexed
            }
            val date = parseDate(cell("date", 0))
            if (date == null) {
                errors.add(l.t("csv.line", lineNo, l.t("csv.errDate")))
                return@forEachIndexed
            }
            val amount = parseAmount(cell("amount", 2))
            if (amount == null || amount == 0.0) {
                errors.add(l.t("csv.line", lineNo, l.t("csv.errAmount")))
                return@forEachIndexed
            }
            val accName = cell("account", 4)
            if (accName.isBlank()) {
                errors.add(l.t("csv.line", lineNo, l.t("csv.errAccount")))
                return@forEachIndexed
            }
            val known = accNames[accName.lowercase()]
            val cur = cell("currency", 3).uppercase().ifBlank { known?.cur ?: d.settings.mainCur }
            if (known == null) newAccounts.add(accName)

            val toAcc = cell("to_account", 5)
            if (toAcc.isNotBlank() && accNames[toAcc.lowercase()] == null) newAccounts.add(toAcc)

            val catName = cell("category", 7)
            val isTransfer = type == "transfer" || toAcc.isNotBlank()
            if (!isTransfer && catName.isNotBlank() && catNames[catName.lowercase()] == null) newCats.add(catName)

            rows.add(
                Row(
                    line = lineNo,
                    date = date,
                    type = if (isTransfer) "transfer" else if (type == "income" || (type.isBlank() && amount > 0)) "income" else "expense",
                    amount = amount,
                    cur = cur,
                    acc = accName,
                    toAcc = toAcc,
                    toAmount = parseAmount(cell("to_amount", 6)),
                    cat = catName,
                    note = cell("note", 8),
                ),
            )
        }
        return Preview(rows, errors, newAccounts.toList(), newCats.toList(), skipped)
    }

    /** Добавляет операции из разбора, попутно создавая недостающие счета и категории. */
    fun apply(d: AppData, p: Preview, l: Lang): AppData {
        var next = d.nextId
        val accounts = d.accounts.toMutableList()
        val categories = d.categories.toMutableList()

        fun accByName(name: String, cur: String): Account {
            accounts.firstOrNull { it.name.equals(name, true) }?.let { return it }
            val a = Account("a$next", name, l.t("acc.type.card"), "", cur, 0.0)
            next++
            accounts.add(a)
            return a
        }

        fun catByName(name: String, income: Boolean): Category {
            categories.firstOrNull { it.name.equals(name, true) }?.let { return it }
            val fallback = categories.firstOrNull { it.income == income }
            if (name.isBlank()) return fallback ?: categories.first()
            val code = name.filter { it.isLetter() }.take(2).uppercase().ifBlank { "??" }
            val c = Category("c$next", code, name, 0.0, income, Palette.fallback(categories.size))
            next++
            categories.add(c)
            return c
        }

        val calc = Calc(d)
        val txs = mutableListOf<Tx>()
        p.rows.forEach { r ->
            val acc = accByName(r.acc, r.cur)
            when (r.type) {
                "transfer" -> {
                    val dst = accByName(r.toAcc.ifBlank { r.acc }, r.cur)
                    val amount = -abs(r.amount)
                    val got = r.toAmount?.let { abs(it) } ?: calc.conv(abs(r.amount), acc.cur, dst.cur)
                    txs.add(
                        Tx(next++, r.date, l.t("msg.transferTitle", acc.name, dst.name), CAT_TRANSFER, acc.id, amount, r.note, dst.id, got),
                    )
                }
                "income" -> {
                    val cat = catByName(r.cat, true)
                    txs.add(Tx(next++, r.date, r.note.ifBlank { cat.name }, cat.id, acc.id, abs(r.amount)))
                }
                else -> {
                    val cat = catByName(r.cat, false)
                    txs.add(Tx(next++, r.date, r.note.ifBlank { cat.name }, cat.id, acc.id, -abs(r.amount)))
                }
            }
        }
        return d.copy(accounts = accounts, categories = categories, txs = txs + d.txs, nextId = next + 1)
    }
}
