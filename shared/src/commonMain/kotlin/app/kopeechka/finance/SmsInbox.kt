package app.kopeechka.finance

import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.Account
import app.kopeechka.finance.data.CAT_TRANSFER
import app.kopeechka.finance.data.INBOX_PUSH
import app.kopeechka.finance.data.INBOX_SMS
import app.kopeechka.finance.data.InboxItem
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.data.ParsedSms
import app.kopeechka.finance.data.SmsParse
import app.kopeechka.finance.data.Tx
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Что происходит с пришедшей банковской СМС.
 *
 * Логика общая, хотя читать сообщения умеет только Android: так её видно
 * целиком в одном месте, и разбор истории на экране настроек работает тем же
 * кодом, что и приём нового сообщения.
 */
/** Что получилось из сообщения: название для уведомления и записали ли сразу. */
data class SmsResult(val title: String, val auto: Boolean)

object SmsInbox {

    /** Разобрать сообщение и положить в «на проверку». null — сообщение не про деньги. */
    fun handle(store: Storage, sender: String, text: String, at: Long, l: Lang, fromPush: Boolean = false): SmsResult? {
        val d = store.current
        if (if (fromPush) !d.settings.bankPush else !d.settings.sms) return null
        val src = SmsParse.pick(d.smsSources, sender, text) ?: return null
        val acc = d.accounts.firstOrNull { it.id == src.accId } ?: return null
        val parsed = SmsParse.parse(text, src, acc.cur) ?: return null

        // у правила есть цифры карты — его счёт и есть ответ; иначе подсказка из маски счёта
        val target = if (src.cardMask.isNotBlank()) {
            acc
        } else {
            parsed.mask.takeIf { it.isNotEmpty() }
                ?.let { mask -> d.accounts.firstOrNull { it.mask.takeLast(4) == mask } }
                ?: acc
        }

        val day = dayOf(at)
        if (alreadyKnown(d, parsed, target.id, day)) return null

        val cat = guessCategory(d, parsed)
        // Снятие наличных — перевод с карты в кошелёк той же валюты, а не трата.
        // Настоящий расход здесь только комиссия банка.
        val wallet = if (parsed.cash && parsed.cur == target.cur) walletFor(d, target) else null
        val fee = if (parsed.cash && src.cashFee > 0) round2(parsed.amount * src.cashFee / 100) else 0.0
        val title = if (parsed.cash) l.t("sms.cashTitle") else parsed.title
        store.update { s ->
            if (src.auto) {
                val main = if (wallet != null) {
                    Tx(
                        id = s.nextId, date = day, title = title, cat = CAT_TRANSFER, acc = target.id,
                        amount = -parsed.amount, note = l.t("sms.noteAuto", src.name),
                        toAcc = wallet.id, toAmount = parsed.amount,
                    )
                } else {
                    Tx(
                        id = s.nextId,
                        date = day,
                        title = title,
                        cat = cat,
                        acc = target.id,
                        amount = if (parsed.income) parsed.amount else -parsed.amount,
                        note = l.t("sms.noteAuto", src.name),
                    )
                }
                val feeTx = if (fee > 0) {
                    Tx(id = s.nextId + 1, date = day, title = l.t("sms.feeTitle"), cat = feeCategory(s), acc = target.id, amount = -fee)
                } else {
                    null
                }
                s.copy(
                    txs = listOfNotNull(feeTx, main) + s.txs,
                    nextId = s.nextId + if (feeTx != null) 2 else 1,
                )
            } else {
                s.copy(
                    inbox = listOf(
                        InboxItem(
                            id = s.nextId,
                            source = if (fromPush) INBOX_PUSH else INBOX_SMS,
                            at = at,
                            date = day,
                            title = title,
                            amount = parsed.amount,
                            cur = parsed.cur,
                            income = parsed.income,
                            accId = target.id,
                            cat = cat,
                            raw = text.take(300),
                            cash = parsed.cash,
                            toAcc = wallet?.id.orEmpty(),
                            fee = fee,
                        ),
                    ) + s.inbox,
                    nextId = s.nextId + 1,
                )
            }
        }
        return SmsResult(title, src.auto)
    }

    /** Кошелёк для наличных: счёт типа «Кошелёк» в той же валюте, что и карта. */
    fun walletFor(d: AppData, card: Account): Account? {
        val cashTypes = Lang.ALL.map { it.t("acc.type.cash").lowercase() }.toSet()
        return d.accounts.firstOrNull { it.id != card.id && it.cur == card.cur && it.type.lowercase() in cashTypes }
    }

    /** Категория комиссии: «Прочее», а если её удалили — первая категория расходов. */
    fun feeCategory(d: AppData): String =
        d.categories.firstOrNull { it.id == "other" && !it.income }?.id
            ?: d.categories.firstOrNull { !it.income }?.id.orEmpty()

    private fun round2(v: Double) = kotlin.math.round(v * 100) / 100

    /** Разбор истории при включении: возвращает, сколько сообщений пригодилось. */
    fun handleAll(store: Storage, messages: List<app.kopeechka.finance.data.SmsMessage>, l: Lang): Int =
        messages.sortedBy { it.at }.count { handle(store, it.sender, it.text, it.at, l) != null }

    /**
     * Такое уже есть: та же сумма в тот же день по тому же счёту.
     * Банк присылает дубли, да и человек мог записать покупку руками раньше смски.
     */
    private fun alreadyKnown(d: AppData, p: ParsedSms, accId: String, day: Long): Boolean {
        val signed = if (p.income) p.amount else -p.amount
        val inTxs = d.txs.any { it.acc == accId && it.date == day && sameMoney(it.amount, signed) }
        val inInbox = d.inbox.any { it.accId == accId && it.date == day && sameMoney(it.amount, p.amount) }
        return inTxs || inInbox
    }

    private fun sameMoney(a: Double, b: Double) = kotlin.math.abs(a - b) < 0.01

    /**
     * Категорию берём из памяти: если человек уже сказал, к чему относится этот
     * магазин, второй раз спрашивать незачем.
     */
    private fun guessCategory(d: AppData, p: ParsedSms): String {
        d.merchantCats[p.title.lowercase()]?.let { saved ->
            if (d.categories.any { it.id == saved }) return saved
        }
        val byName = d.categories.firstOrNull { it.income == p.income && p.title.contains(it.name, ignoreCase = true) }
        if (byName != null) return byName.id
        return d.categories.firstOrNull { it.income == p.income }?.id.orEmpty()
    }

    private fun dayOf(at: Long): Long =
        Instant.fromEpochMilliseconds(at).toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toLong()
}
