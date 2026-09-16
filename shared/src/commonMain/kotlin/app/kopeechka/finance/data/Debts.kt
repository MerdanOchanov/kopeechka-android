package app.kopeechka.finance.data

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Долги: кому я должен и кто должен мне.
 *
 * Тело долга не расход и не доход — это перемещение денег, как перевод между счетами.
 * Поэтому движение тела записывается служебной категорией [CAT_DEBT]: остаток счёта
 * меняется, а отчёты о расходах не искажаются. Заработок сверх тела (проценты, наценка)
 * — уже настоящий доход, он идёт отдельной операцией в [CAT_DEBT_GAIN]; переплата
 * по взятому долгу — настоящий расход в [CAT_DEBT_COST].
 */

/** Служебная категория движения тела долга: в расходы и доходы не попадает. */
const val CAT_DEBT = "debt"

/** Заработок на долге и переплата по долгу — обычные категории дохода и расхода. */
const val CAT_DEBT_GAIN = "debtGain"
const val CAT_DEBT_COST = "debtCost"

object DebtKind {
    /** Я дал в долг — деньги должны вернуться мне. */
    const val LENT = "lent"

    /** Я взял в долг — деньги должен вернуть я. */
    const val BORROWED = "borrowed"

    val ALL = listOf(LENT, BORROWED)

    fun key(kind: String) = "debt.kind." + (if (kind in ALL) kind else LENT)
}

@Serializable
data class DebtPayment(
    val id: Long,
    val date: Long,
    /** Сколько прошло в валюте долга. */
    val amount: Double,
    /** Операция движения тела долга. */
    val txId: Long? = null,
    /** Операция дохода (или расхода), если платёж вышел за тело долга. */
    val extraTxId: Long? = null,
)

@Serializable
data class Debt(
    val id: Long,
    val kind: String = DebtKind.LENT,
    /** Кому дали или у кого взяли. */
    val party: String = "",
    /** Тело долга в валюте [cur]. */
    val principal: Double = 0.0,
    /** Сколько должно вернуться всего: тело плюс проценты или наценка. */
    val expected: Double = 0.0,
    val cur: String = "",
    val date: Long = 0,
    /** Когда вернуть; null — без срока. */
    val due: Long? = null,
    /** Счёт, с которого выдали или на который получили. */
    val acc: String = "",
    val txId: Long? = null,
    val payments: List<DebtPayment> = emptyList(),
    /** Закрыт вручную: прощён, списан или закрыт по договорённости. */
    val closed: Boolean = false,
    val note: String = "",
    /** Когда запись меняли, миллисекунды. Нужна слиянию: см. Sync. */
    val changedAt: Long = 0,
)

/** Итоги по долгам за срез времени — для карточки на главном и страницы. */
data class DebtStats(
    val lentLeft: Double,
    val borrowedLeft: Double,
    val gain: Double,
    val overdue: Int,
    val soonest: Debt?,
)

// ——— расчёты ———

fun Calc.debtCur(d: Debt): String = d.cur.ifBlank { main }

/** Сколько уже прошло по долгу в его валюте. */
fun Calc.debtPaid(d: Debt): Double = d.payments.sumOf { it.amount }

/** Сколько осталось вернуть. */
fun Calc.debtLeft(d: Debt): Double = max(0.0, d.expected - debtPaid(d))

/** Ожидаемый заработок (для взятого долга — переплата): всё сверх тела. */
fun Calc.debtGain(d: Debt): Double = d.expected - d.principal

/** Заработок, который уже получен: платежи сверх тела долга. */
fun Calc.debtGainSoFar(d: Debt): Double = max(0.0, debtPaid(d) - d.principal)

fun Calc.debtPct(d: Debt): Int =
    if (d.expected > 0) ((debtPaid(d) / d.expected) * 100).roundToInt().coerceIn(0, 100) else 0

/** Наценка в процентах от тела: «под 10%». */
fun Calc.debtRatePct(d: Debt): Int =
    if (d.principal > 0) (debtGain(d) / d.principal * 100).roundToInt() else 0

fun Calc.debtDone(d: Debt): Boolean = d.closed || debtLeft(d) <= 0.005

fun Calc.debtOverdue(d: Debt): Boolean = d.due != null && !debtDone(d) && d.due < todayDay

/** Дней до срока: отрицательное — просрочено. */
fun Calc.debtDays(d: Debt): Long? = d.due?.minus(todayDay)

fun Calc.debtLeftMain(d: Debt): Double = toMain(debtLeft(d), debtCur(d))

/** Открытые долги: сначала просроченные, потом по сроку, потом без срока. */
fun Calc.openDebts(kind: String): List<Debt> =
    d.debts.filter { it.kind == kind && !debtDone(it) }
        .sortedWith(compareBy({ it.due ?: Long.MAX_VALUE }, { -it.id }))

fun Calc.closedDebts(): List<Debt> =
    d.debts.filter { debtDone(it) }.sortedWith(compareByDescending { it.date })

fun Calc.debtStats(): DebtStats {
    val lent = openDebts(DebtKind.LENT)
    val borrowed = openDebts(DebtKind.BORROWED)
    val open = lent + borrowed
    return DebtStats(
        lentLeft = lent.sumOf { debtLeftMain(it) },
        borrowedLeft = borrowed.sumOf { debtLeftMain(it) },
        // ожидаемый заработок считаем только по тем, кто ещё должен вернуть
        gain = lent.sumOf { toMain(debtGain(it), debtCur(it)) },
        overdue = open.count { debtOverdue(it) },
        soonest = open.filter { it.due != null }.minByOrNull { it.due!! },
    )
}

/** Имена, которые уже встречались — чтобы не набирать заново. */
fun Calc.debtParties(): List<String> =
    d.debts.sortedByDescending { it.date }.map { it.party }.filter { it.isNotBlank() }.distinct().take(12)

/**
 * Как разложить платёж: сначала закрывается тело долга, остаток — заработок или переплата.
 * Возвращает «тело» и «сверх тела» в валюте долга.
 */
fun Calc.splitPayment(d: Debt, amount: Double): Pair<Double, Double> {
    val principalLeft = max(0.0, d.principal - debtPaid(d))
    val body = minOf(amount, principalLeft)
    return body to (amount - body)
}
