package app.kopeechka.finance

import app.kopeechka.finance.data.AppData
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
        store.update { s ->
            if (src.auto) {
                s.copy(
                    txs = listOf(
                        Tx(
                            id = s.nextId,
                            date = day,
                            title = parsed.title,
                            cat = cat,
                            acc = target.id,
                            amount = if (parsed.income) parsed.amount else -parsed.amount,
                            note = l.t("sms.noteAuto", src.name),
                        ),
                    ) + s.txs,
                    nextId = s.nextId + 1,
                )
            } else {
                s.copy(
                    inbox = listOf(
                        InboxItem(
                            id = s.nextId,
                            source = if (fromPush) INBOX_PUSH else INBOX_SMS,
                            at = at,
                            date = day,
                            title = parsed.title,
                            amount = parsed.amount,
                            cur = parsed.cur,
                            income = parsed.income,
                            accId = target.id,
                            cat = cat,
                            raw = text.take(300),
                        ),
                    ) + s.inbox,
                    nextId = s.nextId + 1,
                )
            }
        }
        return SmsResult(parsed.title, src.auto)
    }

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
