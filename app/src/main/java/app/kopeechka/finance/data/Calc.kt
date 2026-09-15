package app.kopeechka.finance.data

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Period(val key: String, val noteKey: String) {
    WEEK("period.week", "period.note.week"),
    MONTH("period.month", "period.note.month"),
    QUARTER("period.quarter", "period.note.quarter"),
    YEAR("period.year", "period.note.year"),
}

/** Разрез отчёта. Помеченные `biz` показываются, только когда включён модуль «Дело». */
enum class Cut(val key: String, val titleKey: String, val biz: Boolean = false) {
    CATS("cut.cats", "cut.title.cats"),
    ACCS("cut.accs", "cut.title.accs"),
    DAYS("cut.days", "cut.title.days"),
    IO("cut.io", "cut.title.io"),
    PRODUCTS("cut.products", "cut.title.products", biz = true),
    CLIENTS("cut.clients", "cut.title.clients", biz = true),
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
class Calc(
    val d: AppData,
    val today: LocalDate = LocalDate.now(),
    val l: Lang = Lang.of(d.settings.lang),
) {
    val s = d.settings
    val main = s.mainCur
    val todayDay = today.toEpochDay()

    /** Курс: сколько единиц основной валюты стоит одна единица `c`. У основной всегда 1. */
    fun rate(c: String): Double = if (c == main) 1.0 else s.rates[c] ?: Currencies.hintRate(c, main)
    fun conv(v: Double, from: String, to: String) = v * rate(from) / rate(to)
    fun toMain(v: Double, cur: String) = conv(v, cur, main)
    fun fmt(v: Double, cur: String) = Currencies.fmt(v, cur, s.showKopecks)
    fun fmtMain(v: Double) = fmt(v, main)

    /** Валюты, включённые в настройках; основная всегда первая — она же база курсов. */
    val currencies: List<String>
        get() = (listOf(main) + s.currencyCodes).distinct()

    fun acc(id: String?) = d.accounts.firstOrNull { it.id == id }
    fun cat(id: String): Category = when (id) {
        CAT_TRANSFER -> Category(CAT_TRANSFER, l.t("cat.code.transfer"), l.t("kind.transfer"), color = "#5D5D60")
        CAT_GOAL -> Category(CAT_GOAL, l.t("cat.code.goal"), l.t("goal.one"), color = "#597EA3")
        CAT_DEBT -> Category(CAT_DEBT, l.t("cat.code.debt"), l.t("debt.one"), color = "#6B4E8A")
        else -> d.categories.firstOrNull { it.id == id } ?: Category(id, "??", l.t("demo.cat.other"))
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

    fun isReal(t: Tx) = t.cat != CAT_TRANSFER && t.cat != CAT_GOAL && t.cat != CAT_DEBT
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

    /** Лимиты хранятся в основной валюте, поэтому пересчёт не нужен. */
    fun limitMain(c: Category) = c.limitBase
    val limitTotal by lazy { d.categories.filter { !it.income }.sumOf { limitMain(it) } }
    val budgets: List<BudgetRow> by lazy {
        val sp = spentBy(monthTx)
        d.categories.filter { !it.income && it.limitBase > 0 }.map { BudgetRow(it, sp[it.id] ?: 0.0, limitMain(it)) }
    }
    /** Остаток лимитов: может быть отрицательным — тогда это перерасход. */
    val freeRaw get() = limitTotal - monthExpense
    val free get() = max(0.0, freeRaw)
    val perDay get() = if (daysLeft > 0) free / (daysLeft + 1) else free

    // ——— подписи ———
    fun dayLabel(day: Long): String {
        val diff = todayDay - day
        if (diff == 0L) return l.t("date.today")
        if (diff == 1L) return l.t("date.yesterday")
        val dt = LocalDate.ofEpochDay(day)
        val m = l.monthsGen[dt.monthValue - 1]
        return if (dt.year == today.year) l.t("date.dm", dt.dayOfMonth, m) else l.t("date.dmy", dt.dayOfMonth, m, dt.year)
    }

    // ——— периоды отчётов ———

    /** offset: 0 — текущий период, −1 — предыдущий и так далее. */
    fun range(p: Period, offset: Int): Range = when (p) {
        Period.WEEK -> {
            val start = today.with(DayOfWeek.MONDAY).plusWeeks(offset.toLong())
            val end = start.plusDays(6)
            val title = if (start.month == end.month) {
                l.t("date.weekRange", start.dayOfMonth, end.dayOfMonth, l.monthsGen[start.monthValue - 1])
            } else {
                l.t("date.weekRangeCross", start.dayOfMonth, l.monthsShort[start.monthValue - 1], end.dayOfMonth, l.monthsShort[end.monthValue - 1])
            }
            Range(start.toEpochDay(), end.toEpochDay(), title, l.t(p.noteKey))
        }
        Period.MONTH -> {
            val start = today.withDayOfMonth(1).plusMonths(offset.toLong())
            val end = start.withDayOfMonth(start.lengthOfMonth())
            val title = l.months[start.monthValue - 1] + if (start.year != today.year) " ${start.year}" else ""
            Range(start.toEpochDay(), end.toEpochDay(), title, l.t(p.noteKey))
        }
        Period.QUARTER -> {
            val base = today.withDayOfMonth(1).minusMonths(((today.monthValue - 1) % 3).toLong()).plusMonths(offset * 3L)
            val last = base.plusMonths(2)
            val end = last.withDayOfMonth(last.lengthOfMonth())
            val title = l.t("date.quarter", ROMAN[(base.monthValue - 1) / 3]) + if (base.year != today.year) " ${base.year}" else ""
            Range(base.toEpochDay(), end.toEpochDay(), title, l.t(p.noteKey))
        }
        Period.YEAR -> {
            val start = today.withDayOfYear(1).plusYears(offset.toLong())
            val end = start.withDayOfYear(start.lengthOfYear())
            Range(start.toEpochDay(), end.toEpochDay(), l.t("date.year", start.year), l.t(p.noteKey))
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
                agg.withIndex().sortedByDescending { it.value }.map { Slice(l.weekFull[it.index], it.value, pct(it.value)) }
            }
            Cut.IO -> {
                val inc = incomeIn(r)
                val sum = if (inc + exp > 0) inc + exp else 1.0
                listOf(
                    Slice(l.t("report.incomes"), inc, (inc / sum * 100).roundToInt().toString() + "%"),
                    Slice(l.t("report.expenses"), exp, (exp / sum * 100).roundToInt().toString() + "%"),
                    Slice(l.t("report.saldo"), inc - exp, "—"),
                )
            }
            Cut.PRODUCTS -> byProduct(r)
            Cut.CLIENTS -> byCustomer(r)
        }
    }

    /** Выручка оплаченных заказов за отрезок — для столбиков в разрезах «Дела». */
    fun revenueIn(from: Long, to: Long): Double =
        d.orders.filter { it.status == OrderStatus.PAID && it.date in from..to }.sumOf { orderTotalMain(it) }

    fun series(r: Range, p: Period, cut: Cut): List<Bar> {
        val exp = d.txs.filter { isReal(it) && it.amount < 0 }
        // в разрезах «Дела» столбики показывают выручку, в остальных — расходы
        fun sum(from: Long, to: Long) =
            if (cut.biz) revenueIn(from, to) else exp.filter { it.date in from..to }.sumOf { -txMain(it) }

        val raw: List<Pair<String, Double>> = when {
            cut == Cut.DAYS -> {
                val agg = DoubleArray(7)
                exp.filter { it.date in r.from..r.to }.forEach { agg[weekday(it.date)] -= txMain(it) }
                (0..6).map { l.week[it] to agg[it] }
            }
            p == Period.WEEK -> (0..6).map { i ->
                val day = LocalDate.ofEpochDay(r.from).plusDays(i.toLong())
                l.week[i] to sum(day.toEpochDay(), day.toEpochDay())
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
                l.monthsShort[m.monthValue - 1] to sum(m.toEpochDay(), m.withDayOfMonth(m.lengthOfMonth()).toEpochDay())
            }
            else -> (0..11).map { i ->
                val m = LocalDate.ofEpochDay(r.from).plusMonths(i.toLong())
                l.monthsShort[m.monthValue - 1] to sum(m.toEpochDay(), m.withDayOfMonth(m.lengthOfMonth()).toEpochDay())
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
            l.monthsShort[m.monthValue - 1] to exp.filter { it.date in from..to }.sumOf { -txMain(it) }
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
            l.week[i] to exp.filter { it.date == day }.sumOf { -txMain(it) }
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
            l.t("report.fact.avg") to fmtMain(exp / elapsedDays(r)),
            l.t("report.fact.topCat") to (top?.let { "${it.name} · ${fmtMain(it.value)}" } ?: l.t("common.dash")),
            biggest?.let { l.t("report.fact.biggest") to "${it.title} · ${fmt(abs(it.amount), accCur(it.acc))}" },
            l.t("report.fact.count") to within.size.toString(),
        )
    }

    companion object {
        val ROMAN = listOf("I", "II", "III", "IV")
    }
}
