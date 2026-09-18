package app.kopeechka.finance.data

import kotlinx.serialization.Serializable

/** Как часто повторяется платёж. */
object Every {
    const val WEEK = "week"
    const val MONTH = "month"
    const val YEAR = "year"
    val ALL = listOf(WEEK, MONTH, YEAR)
}

/**
 * Регулярный платёж или рассрочка: кредит, «Долями», Kaspi Red, подписка,
 * коммуналка, аренда — всё, что повторяется с одной суммой.
 *
 * Дата каждого платежа считается от первой: второй платёж — через месяц после
 * первого, третий — через два. Так дата не «уплывает» после короткого февраля:
 * платёж 31-го числа в феврале придётся на 28-е, а в марте снова на 31-е.
 */
@Serializable
data class Recurring(
    val id: String,
    val title: String,
    /** В валюте счёта, всегда положительная; направление — в [income]. */
    val amount: Double,
    val income: Boolean = false,
    val accId: String,
    val cat: String,
    val every: String = Every.MONTH,
    /** Дата первого платежа, день от 1970-01-01. */
    val start: Long,
    /** Сколько платежей всего — для рассрочки. 0 — бессрочно. */
    val total: Int = 0,
    /** Сколько уже проведено. */
    val done: Int = 0,
    /** Записывать сразу, без подтверждения. Иначе платёж ждёт в «На проверку». */
    val auto: Boolean = false,
    /** За сколько дней напоминать. */
    val remindDays: Int = 1,
    /** О каком платеже уже напомнили — чтобы не напоминать дважды. */
    val remindedFor: Long = 0,
    /**
     * Кто завёл. Платёж проводит только автор: в общем бюджете записи
     * у двоих одинаковые, и без этого каждый платёж появился бы дважды.
     */
    val by: String = "",
    val active: Boolean = true,
    /** Когда запись меняли, миллисекунды. Нужна слиянию: см. Sync. */
    val changedAt: Long = 0,
)

/** Дата платежа номер [n], считая с нуля. */
fun Recurring.dateOf(n: Int): Long {
    val first = epochDate(start)
    return when (every) {
        Every.WEEK -> start + 7L * n
        Every.YEAR -> first.plusYears(n.toLong()).toEpochDay()
        else -> first.plusMonths(n.toLong()).toEpochDay()
    }
}

/** Рассрочка выплачена целиком. */
val Recurring.finished: Boolean get() = total in 1..done

/** Дата следующего платежа или null, если платежей больше не будет. */
val Recurring.next: Long? get() = if (!active || finished) null else dateOf(done)

/** Сколько осталось заплатить по рассрочке; для бессрочного — null. */
val Recurring.leftAmount: Double? get() = if (total > 0) amount * (total - done).coerceAtLeast(0) else null

/** Результат проведения: новое состояние и названия того, что появилось. */
data class RecurringRun(val data: AppData, val made: List<String>)

object Recurrings {

    /** Больше этого за один раз не проводим: если телефон долго не открывали, не засыпаем человека. */
    private const val MAX_AT_ONCE = 24

    /**
     * Провести платежи, срок которых наступил. Пропущенные тоже — каждый своей
     * датой, чтобы история совпала с выпиской банка.
     *
     * [me] — id участника общего пространства; без пространства пустая строка.
     */
    fun due(d: AppData, today: Long, me: String, installmentTitle: (Recurring, Int) -> String): RecurringRun {
        var nextId = d.nextId
        val txs = mutableListOf<Tx>()
        val drafts = mutableListOf<InboxItem>()
        val made = mutableListOf<String>()

        val recurring = d.recurring.map { r ->
            if (!isMine(r, me, d)) return@map r
            var cur = r
            var guard = 0
            while (guard < MAX_AT_ONCE) {
                val day = cur.next ?: break
                if (day > today) break
                val title = if (cur.total > 0) installmentTitle(cur, cur.done + 1) else cur.title
                if (cur.auto) {
                    txs += Tx(
                        id = nextId++,
                        date = day,
                        title = title,
                        cat = cur.cat,
                        acc = cur.accId,
                        amount = if (cur.income) cur.amount else -cur.amount,
                        by = me,
                    )
                } else {
                    drafts += InboxItem(
                        id = nextId++,
                        source = INBOX_RECURRING,
                        at = 0,
                        date = day,
                        title = title,
                        amount = cur.amount,
                        income = cur.income,
                        accId = cur.accId,
                        cat = cur.cat,
                    )
                }
                made += title
                cur = cur.copy(done = cur.done + 1)
                guard++
            }
            cur
        }
        if (made.isEmpty()) return RecurringRun(d, emptyList())
        return RecurringRun(
            d.copy(
                recurring = recurring,
                txs = txs.reversed() + d.txs,
                inbox = drafts.reversed() + d.inbox,
                nextId = nextId,
            ),
            made,
        )
    }

    /** Ближайшие платежи на [days] дней вперёд, начиная с сегодняшнего. */
    fun upcoming(d: AppData, today: Long, days: Int): List<Pair<Recurring, Long>> =
        d.recurring.mapNotNull { r -> r.next?.takeIf { it in today..today + days }?.let { r to it } }
            .sortedBy { it.second }

    /**
     * О чём пора напомнить: до платежа осталось не больше [Recurring.remindDays]
     * дней, и об этом платеже ещё не напоминали. Возвращает новое состояние —
     * с отметками — и сами платежи.
     */
    fun reminders(d: AppData, today: Long, me: String): Pair<AppData, List<Pair<Recurring, Long>>> {
        val hits = mutableListOf<Pair<Recurring, Long>>()
        val recurring = d.recurring.map { r ->
            val day = r.next ?: return@map r
            if (!isMine(r, me, d) || r.remindDays <= 0 || r.remindedFor == day) return@map r
            if (day - today in 1..r.remindDays.toLong()) {
                hits += r to day
                r.copy(remindedFor = day)
            } else {
                r
            }
        }
        return (if (hits.isEmpty()) d else d.copy(recurring = recurring)) to hits
    }

    /**
     * Свой ли платёж. Без общего пространства — все свои. В пространстве —
     * только заведённые мной: платежи второго участника проведёт его телефон.
     */
    private fun isMine(r: Recurring, me: String, d: AppData): Boolean =
        if (d.space == null) true else r.by == me
}
