package app.kopeechka.finance.data

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Period(val label: String) {
    WEEK("Неделя"),
    MONTH("Месяц"),
    QUARTER("Квартал"),
    YEAR("Год"),
}

enum class Cut(val label: String, val title: String) {
    CATS("Категории", "Разрез по категориям"),
    ACCS("Счета", "Разрез по счетам"),
    DAYS("Дни недели", "Разрез по дням недели"),
    IO("Доходы / расходы", "Доходы против расходов"),
}

/** Календарный отрезок отчёта: неделя, месяц, квартал или год со сдвигом назад. */
data class Range(val from: Long, val to: Long, val title: String, val note: String)

data class BudgetRow(val cat: Category, val spent: Double, val limit: Double) {
    val over get() = spent > limit
    val pct get() = if (limit > 0) min(100, (spent / limit * 100).roundToInt()) else 0
    val rawPct get() = if (limit > 0) spent / limit * 100 else 0.0
}

data class Bar(val label: String, val value: Double, val highlight: Boolean)
data class Slice(val name: String, val value: Double, val pct: String, val color: String? = null)

/** Все вычисления над данными: балансы, бюджеты, отчёты. Суммы — в основной валюте. */
class Calc(val d: AppData, val today: LocalDate = LocalDate.now()) {
    val s = d.settings
    val main = s.mainCur
    val todayDay = today.toEpochDay()

    fun rate(c: String) = s.rates[c] ?: Currencies.RATE_HINTS[c] ?: 1.0
    fun conv(v: Double, from: String, to: String) = v * rate(from) / rate(to)
    fun toMain(v: Double, cur: String) = conv(v, cur, main)
    fun fmt(v: Double, cur: String) = Currencies.fmt(v, cur, s.showKopecks)
    fun fmtMain(v: Double) = fmt(v, main)

    /** Валюты, включённые в настройках (RUB всегда первая — база курсов). */
    val currencies: List<String>
        get() = (listOf(Currencies.BASE) + s.currencyCodes).distinct()

    fun acc(id: String?) = d.accounts.firstOrNull { it.id == id }
    fun cat(id: String): Category = when (id) {
        CAT_TRANSFER -> Category(CAT_TRANSFER, "ПВ", "Перевод", color = "#5D5D60")
        CAT_GOAL -> Category(CAT_GOAL, "ЦЛ", "Цель", color = "#597EA3")
        else -> d.categories.firstOrNull { it.id == id } ?: Category(id, "ПЧ", "Прочее")
    }

    /** Цвет категории: свой из палитры либо запасной по позиции в списке. */
    fun colorOf(id: String): String {
        val c = cat(id)
        if (c.color.isNotBlank()) return c.color
        val idx = d.categories.indexOfFirst { it.id == id }
        return Palette.fallback(if (idx >= 0) idx else 0)
    }

    fun accCur(id: String?) = acc(id)?.cur ?: main
    fun balance(a: Account): Double = a.initial + d.txs.sumOf { t ->
        (if (t.acc == a.id) t.amount else 0.0) + (if (t.toAcc == a.id) (t.toAmount ?: 0.0) else 0.0)
    }

    val balances: Map<String, Double> by lazy { d.accounts.associate { it.id to balance(it) } }
    val totalMain: Double by lazy { d.accounts.filter { it.inTotal }.sumOf { toMain(balances[it.id] ?: 0.0, it.cur) } }

    fun isReal(t: Tx) = t.cat != CAT_TRANSFER && t.cat != CAT_GOAL
    fun txMain(t: Tx) = toMain(t.amount, accCur(t.acc))

    // ——— текущий месяц (главный экран и бюджеты) ———
    val monthStart: Long = today.withDayOfMonth(1).toEpochDay()
    val daysLeft: Int = today.lengthOfMonth() - today.dayOfMonth
    val monthTx: List<Tx> by lazy { d.txs.filter { isReal(it) && it.date in monthStart..todayDay } }
    val monthIncome by lazy { monthTx.filter { it.amount > 0 }.sumOf { txMain(it) } }
    val monthExpense by lazy { monthTx.filter { it.amount < 0 }.sumOf { -txMain(it) } }

    fun spentBy(list: List<Tx>): Map<String, Double> {
        val m = HashMap<String, Double>()
        list.forEach { if (it.amount < 0) m[it.cat] = (m[it.cat] ?: 0.0) - txMain(it) }
        return m
    }

    fun limitMain(c: Category) = toMain(c.limitRub, Currencies.BASE)
    val limitTotal by lazy { d.categories.filter { !it.income }.sumOf { limitMain(it) } }
    val budgets: List<BudgetRow> by lazy {
        val sp = spentBy(monthTx)
        d.categories.filter { !it.income && it.limitRub > 0 }.map { BudgetRow(it, sp[it.id] ?: 0.0, limitMain(it)) }
    }
    val free get() = max(0.0, limitTotal - monthExpense)
    val perDay get() = if (daysLeft > 0) free / (daysLeft + 1) else free

    // ——— подписи ———
    fun dayLabel(day: Long): String {
        val diff = todayDay - day
        if (diff == 0L) return "Сегодня"
        if (diff == 1L) return "Вчера"
        val dt = LocalDate.ofEpochDay(day)
        val m = MONTHS_GEN[dt.monthValue - 1]
        return if (dt.year == today.year) "${dt.dayOfMonth} $m" else "${dt.dayOfMonth} $m ${dt.year}"
    }

    // ——— периоды отчётов ———

    /** offset: 0 — текущий период, −1 — предыдущий и так далее. */
    fun range(p: Period, offset: Int): Range = when (p) {
        Period.WEEK -> {
            val start = today.with(DayOfWeek.MONDAY).plusWeeks(offset.toLong())
            val end = start.plusDays(6)
            val title = if (start.month == end.month) {
                "${start.dayOfMonth}–${end.dayOfMonth} ${MONTHS_GEN[start.monthValue - 1]}"
            } else {
                "${start.dayOfMonth} ${MONTHS_SHORT[start.monthValue - 1]} – ${end.dayOfMonth} ${MONTHS_SHORT[end.monthValue - 1]}"
            }
            Range(start.toEpochDay(), end.toEpochDay(), title, "за неделю")
        }
        Period.MONTH -> {
            val start = today.withDayOfMonth(1).plusMonths(offset.toLong())
            val end = start.withDayOfMonth(start.lengthOfMonth())
            val title = MONTHS_NOM[start.monthValue - 1] + if (start.year != today.year) " ${start.year}" else ""
            Range(start.toEpochDay(), end.toEpochDay(), title, "за месяц")
        }
        Period.QUARTER -> {
            val base = today.withDayOfMonth(1).minusMonths(((today.monthValue - 1) % 3).toLong()).plusMonths(offset * 3L)
            val last = base.plusMonths(2)
            val end = last.withDayOfMonth(last.lengthOfMonth())
            val title = ROMAN[(base.monthValue - 1) / 3] + " квартал" + if (base.year != today.year) " ${base.year}" else ""
            Range(base.toEpochDay(), end.toEpochDay(), title, "за квартал")
        }
        Period.YEAR -> {
            val start = today.withDayOfYear(1).plusYears(offset.toLong())
            val end = start.withDayOfYear(start.lengthOfYear())
            Range(start.toEpochDay(), end.toEpochDay(), start.year.toString() + " год", "за год")
        }
    }

    /** Сколько дней периода уже прошло (для среднего расхода в день). */
    fun elapsedDays(r: Range): Int = (min(r.to, todayDay) - r.from + 1).toInt().coerceAtLeast(1)
    fun isCurrent(r: Range) = todayDay in r.from..r.to

    fun txIn(r: Range) = d.txs.filter { isReal(it) && it.date in r.from..r.to }
    fun expenseIn(r: Range) = txIn(r).filter { it.amount < 0 }.sumOf { -txMain(it) }
    fun incomeIn(r: Range) = txIn(r).filter { it.amount > 0 }.sumOf { txMain(it) }

    fun weekday(day: Long) = LocalDate.ofEpochDay(day).dayOfWeek.value - 1 // 0 = пн

    fun breakdown(r: Range, cut: Cut): List<Slice> {
        val within = txIn(r)
        val exp = expenseIn(r)
        fun pct(v: Double) = (if (exp > 0) (v / exp * 100).roundToInt() else 0).toString() + "%"
        return when (cut) {
            Cut.CATS -> spentBy(within).entries.sortedByDescending { it.value }
                .map { Slice(cat(it.key).name, it.value, pct(it.value), colorOf(it.key)) }
            Cut.ACCS -> d.accounts.map { a -> a to within.filter { it.acc == a.id && it.amount < 0 }.sumOf { -txMain(it) } }
                .sortedByDescending { it.second }
                .map { Slice(it.first.name + " · " + it.first.cur, it.second, pct(it.second)) }
            Cut.DAYS -> {
                val agg = DoubleArray(7)
                within.forEach { if (it.amount < 0) agg[weekday(it.date)] -= txMain(it) }
                agg.withIndex().sortedByDescending { it.value }.map { Slice(WEEK_FULL[it.index], it.value, pct(it.value)) }
            }
            Cut.IO -> {
                val inc = incomeIn(r)
                val sum = if (inc + exp > 0) inc + exp else 1.0
                listOf(
                    Slice("Доходы", inc, (inc / sum * 100).roundToInt().toString() + "%"),
                    Slice("Расходы", exp, (exp / sum * 100).roundToInt().toString() + "%"),
                    Slice("Сальдо", inc - exp, "—"),
                )
            }
        }
    }

    fun series(r: Range, p: Period, cut: Cut): List<Bar> {
        val exp = d.txs.filter { isReal(it) && it.amount < 0 }
        fun sum(from: Long, to: Long) = exp.filter { it.date in from..to }.sumOf { -txMain(it) }

        val raw: List<Pair<String, Double>> = when {
            cut == Cut.DAYS -> {
                val agg = DoubleArray(7)
                exp.filter { it.date in r.from..r.to }.forEach { agg[weekday(it.date)] -= txMain(it) }
                WEEK.indices.map { WEEK[it] to agg[it] }
            }
            p == Period.WEEK -> (0..6).map { i ->
                val day = LocalDate.ofEpochDay(r.from).plusDays(i.toLong())
                WEEK[i] to sum(day.toEpochDay(), day.toEpochDay())
            }
            p == Period.MONTH -> {
                val start = LocalDate.ofEpochDay(r.from)
                val len = start.lengthOfMonth()
                val out = mutableListOf<Pair<String, Double>>()
                var day = 1
                while (day <= len) {
                    val end = min(day + 6, len)
                    out += "$day–$end" to sum(start.withDayOfMonth(day).toEpochDay(), start.withDayOfMonth(end).toEpochDay())
                    day = end + 1
                }
                out
            }
            p == Period.QUARTER -> (0..2).map { i ->
                val m = LocalDate.ofEpochDay(r.from).plusMonths(i.toLong())
                MONTHS_SHORT[m.monthValue - 1] to sum(m.toEpochDay(), m.withDayOfMonth(m.lengthOfMonth()).toEpochDay())
            }
            else -> (0..11).map { i ->
                val m = LocalDate.ofEpochDay(r.from).plusMonths(i.toLong())
                MONTHS_SHORT[m.monthValue - 1] to sum(m.toEpochDay(), m.withDayOfMonth(m.lengthOfMonth()).toEpochDay())
            }
        }
        val mx = raw.maxOfOrNull { it.second } ?: 0.0
        return raw.map { Bar(it.first, it.second, it.second > 0 && it.second == mx) }
    }

    /** Расходы за последние n месяцев — для карточки «Динамика» на главном. */
    fun lastMonths(n: Int): List<Bar> {
        val exp = d.txs.filter { isReal(it) && it.amount < 0 }
        val raw = (n - 1 downTo 0).map { back ->
            val m = today.minusMonths(back.toLong())
            val from = m.withDayOfMonth(1).toEpochDay()
            val to = m.withDayOfMonth(m.lengthOfMonth()).toEpochDay()
            MONTHS_SHORT[m.monthValue - 1] to exp.filter { it.date in from..to }.sumOf { -txMain(it) }
        }
        val mx = raw.maxOfOrNull { it.second } ?: 0.0
        return raw.map { Bar(it.first, it.second, it.second > 0 && it.second == mx) }
    }

    /** Расходы по дням текущей недели — для карточки на главном. */
    fun weekDays(): List<Bar> {
        val r = range(Period.WEEK, 0)
        val exp = d.txs.filter { isReal(it) && it.amount < 0 }
        val raw = (0..6).map { i ->
            val day = LocalDate.ofEpochDay(r.from).plusDays(i.toLong()).toEpochDay()
            WEEK[i] to exp.filter { it.date == day }.sumOf { -txMain(it) }
        }
        val mx = raw.maxOfOrNull { it.second } ?: 0.0
        return raw.map { Bar(it.first, it.second, it.second > 0 && it.second == mx) }
    }

    /** Расход за тот же отрезок прошлого периода и изменение в процентах. */
    fun compare(p: Period, offset: Int): Pair<Double, Double>? {
        val cur = range(p, offset)
        val prev = range(p, offset - 1)
        val prevTo = if (isCurrent(cur)) min(prev.to, prev.from + (todayDay - cur.from)) else prev.to
        val prevExp = d.txs.filter { isReal(it) && it.amount < 0 && it.date in prev.from..prevTo }.sumOf { -txMain(it) }
        if (prevExp <= 0.0) return null
        val curExp = expenseIn(cur)
        return prevExp to (curExp - prevExp) / prevExp * 100
    }

    fun facts(r: Range): List<Pair<String, String>> {
        val within = txIn(r)
        val exp = expenseIn(r)
        val top = breakdown(r, Cut.CATS).firstOrNull()
        val biggest = within.filter { it.amount < 0 }.minByOrNull { it.amount }
        return listOfNotNull(
            "Средний расход в день" to fmtMain(exp / elapsedDays(r)),
            "Самая дорогая категория" to (top?.let { "${it.name} · ${fmtMain(it.value)}" } ?: "—"),
            biggest?.let { "Крупнейшая трата" to "${it.title} · ${fmt(abs(it.amount), accCur(it.acc))}" },
            "Операций за период" to within.size.toString(),
        )
    }

    companion object {
        val WEEK = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
        val WEEK_FULL = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
        val MONTHS_SHORT = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
        val MONTHS_NOM = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
        val MONTHS_GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
        val ROMAN = listOf("I", "II", "III", "IV")
    }
}
