package app.kopeechka.finance.data

import kotlinx.serialization.Serializable

object RequestStatus {
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val DECLINED = "declined"
    const val CANCELLED = "cancelled"
}

/**
 * Заявка на расход с общего счёта: один участник просит, второй решает.
 *
 * Заявка едет тем же обменом, что и остальные записи, — отдельного канала
 * у неё нет. Поэтому второй участник увидит её при следующей синхронизации,
 * а не мгновенно.
 *
 * Операцию по одобренной заявке создаёт **только тот, кто просил**, когда
 * увидит у себя решение. Если бы её создавал решающий, после обмена у обоих
 * могло бы оказаться по операции на одну покупку.
 */
@Serializable
data class SpendRequest(
    val id: Long,
    /** Кто просит — id участника и имя для экрана. */
    val by: String,
    val byName: String = "",
    val accId: String,
    /** Сумма в валюте счёта, всегда положительная. */
    val amount: Double,
    val cat: String,
    val title: String,
    val date: Long,
    val note: String = "",
    /** Когда попросили, миллисекунды. */
    val at: Long = 0,
    val status: String = RequestStatus.PENDING,
    val decidedBy: String = "",
    val decidedByName: String = "",
    val decidedAt: Long = 0,
    /** Операция, созданная после одобрения; null — ещё не создана. */
    val txId: Long? = null,
    /** Когда запись меняли, миллисекунды. Нужна слиянию: см. Sync. */
    val changedAt: Long = 0,
)

/** Сколько на счёте ждёт решения — чтобы второй не потратил те же деньги. */
fun AppData.pendingOn(accId: String): Double =
    requests.filter { it.accId == accId && it.status == RequestStatus.PENDING }.sumOf { it.amount }

/**
 * Нужно ли по этому расходу спрашивать второго участника: счёт общий,
 * пространство есть, сумма не ниже порога. Порог 0 — спрашивать всегда.
 */
fun AppData.needsApproval(accId: String, amount: Double): Boolean {
    val acc = accounts.firstOrNull { it.id == accId } ?: return false
    val sp = space ?: return false
    if (!acc.shared || sp.members.size < 2) return false
    return amount >= acc.approveFrom
}
