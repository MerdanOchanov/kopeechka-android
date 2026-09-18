package app.kopeechka.finance.data

import kotlinx.serialization.Serializable

/**
 * Общее пространство: двое ведут одни и те же деньги с разных телефонов.
 *
 * Каждый участник получает числовой слот, с которого начинаются его
 * идентификаторы, — иначе оба телефона независимо выдадут записям одинаковые
 * номера и слияние их перепутает.
 */
@Serializable
data class SyncSpace(
    val id: String,
    val name: String,
    /** Кто я в этом пространстве. */
    val memberId: String,
    val memberName: String,
    /** Мой слот: все мои id начинаются с slot * 10^12. */
    val slot: Int,
    /** Общий секрет из кода приглашения — им шифруются файлы обмена. */
    val secret: String = "",
    val members: List<SyncMember> = emptyList(),
    /** Когда последний раз обменивались, миллисекунды. */
    val syncedAt: Long = 0,
    /** Через что обмениваемся: облако, чужой сервер, сеть. Можно сразу несколько. */
    val links: List<SyncLink> = emptyList(),
)

@Serializable
data class SyncMember(val id: String, val name: String, val slot: Int)

/** Через что обмениваемся. Пароль WebDAV лежит не здесь, а в хранилище ключей. */
@Serializable
data class SyncLink(
    val kind: String,
    /** Адрес папки для WebDAV; для остальных пусто. */
    val url: String = "",
    val login: String = "",
    val enabled: Boolean = true,
)

object SyncKind {
    const val DRIVE = "drive"
    const val WEBDAV = "webdav"
    const val LAN = "lan"
}

/** Снимок для обмена: чьё состояние и когда снято. */
@Serializable
data class SyncSnapshot(
    val spaceId: String,
    val memberId: String,
    val memberName: String,
    val at: Long,
    val data: AppData,
)

/**
 * Слияние двух снимков.
 *
 * Правила простые и, главное, не зависят от порядка: сливать можно сколько
 * угодно раз и в любой последовательности — результат один и тот же.
 *
 *  - записи сопоставляются по id (он уникален благодаря слотам);
 *  - при расхождении побеждает та, у которой [changedAt] больше;
 *  - при равенстве времени — большая по содержимому, чтобы оба телефона
 *    выбрали одинаково, а не каждый своё;
 *  - удаление запоминается «надгробием» в [AppData.deleted], иначе union
 *    вернул бы то, что второй участник уже стёр.
 *
 * Настройки не общие: язык, тема и час напоминания у каждого свои. Исключение —
 * денежная модель (основная валюта, курсы, список валют): если она разойдётся,
 * двое будут видеть разные итоги по одним и тем же операциям.
 */
object Sync {

    /** Сколько храним записи об удалении: полгода с запасом. */
    const val TOMB_LIFETIME = 180L * 24 * 60 * 60 * 1000

    private const val BILLION = 1_000_000_000_000L

    /** С какого номера начинаются идентификаторы участника. */
    fun slotStart(slot: Int): Long = slot * BILLION + 1000

    /**
     * Проставить время изменения и записать удаления.
     *
     * Делается в одном месте — при сохранении, — а не в каждом обработчике:
     * так нельзя забыть отметить запись, а удаления попадают в надгробия сами.
     */
    fun stamp(before: AppData, after: AppData, now: Long): AppData {
        if (before === after) return after
        val tombs = after.deleted.toMutableMap()

        val accounts = stampList(before.accounts, after.accounts, now, tombs, "acc", { it.id }) { it.copy(changedAt = now) }
        val categories = stampList(before.categories, after.categories, now, tombs, "cat", { it.id }) { it.copy(changedAt = now) }
        val txs = stampList(before.txs, after.txs, now, tombs, "tx", { it.id.toString() }) { it.copy(changedAt = now) }
        val goals = stampList(before.goals, after.goals, now, tombs, "goal", { it.id }) { it.copy(changedAt = now) }
        val debts = stampList(before.debts, after.debts, now, tombs, "debt", { it.id.toString() }) { it.copy(changedAt = now) }
        val products = stampList(before.products, after.products, now, tombs, "prod", { it.id }) { it.copy(changedAt = now) }
        val customers = stampList(before.customers, after.customers, now, tombs, "cust", { it.id }) { it.copy(changedAt = now) }
        val orders = stampList(before.orders, after.orders, now, tombs, "order", { it.id.toString() }) { it.copy(changedAt = now) }
        val requests = stampList(before.requests, after.requests, now, tombs, "req", { it.id.toString() }) { it.copy(changedAt = now) }
        val recurring = stampList(before.recurring, after.recurring, now, tombs, "rec", { it.id }) { it.copy(changedAt = now) }

        // новая операция без автора — значит, её записали на этом телефоне;
        // пришедшие при обмене сюда не попадают, они пишутся мимо stamp
        val me = after.space?.memberId.orEmpty()
        val known = before.txs.mapTo(HashSet()) { it.id }
        val authored = if (me.isEmpty()) txs else txs.map { if (it.by.isEmpty() && it.id !in known) it.copy(by = me) else it }

        val moneyChanged = moneyOf(before.settings) != moneyOf(after.settings)
        val settings = if (moneyChanged) after.settings.copy(moneyAt = now) else after.settings

        return after.copy(
            accounts = accounts,
            categories = categories,
            txs = authored,
            goals = goals,
            debts = debts,
            products = products,
            customers = customers,
            orders = orders,
            requests = requests,
            recurring = recurring,
            settings = settings,
            deleted = prune(tombs, now),
        )
    }

    /** Слить чужой снимок в свой. */
    fun merge(mine: AppData, theirs: AppData, now: Long): AppData {
        val deleted = (mine.deleted.keys + theirs.deleted.keys).associateWith { k ->
            maxOf(mine.deleted[k] ?: 0, theirs.deleted[k] ?: 0)
        }

        val txs = mergeList(mine.txs, theirs.txs, deleted, "tx", { it.id.toString() }, { it.changedAt })
            .sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.id })

        return mine.copy(
            accounts = mergeList(mine.accounts, theirs.accounts, deleted, "acc", { it.id }, { it.changedAt }),
            categories = mergeList(mine.categories, theirs.categories, deleted, "cat", { it.id }, { it.changedAt }),
            txs = txs,
            goals = mergeList(mine.goals, theirs.goals, deleted, "goal", { it.id }, { it.changedAt }),
            debts = mergeList(mine.debts, theirs.debts, deleted, "debt", { it.id.toString() }, { it.changedAt }),
            products = mergeList(mine.products, theirs.products, deleted, "prod", { it.id }, { it.changedAt }),
            customers = mergeList(mine.customers, theirs.customers, deleted, "cust", { it.id }, { it.changedAt }),
            orders = mergeList(mine.orders, theirs.orders, deleted, "order", { it.id.toString() }, { it.changedAt }),
            requests = mergeList(mine.requests, theirs.requests, deleted, "req", { it.id.toString() }, { it.changedAt }),
            recurring = mergeList(mine.recurring, theirs.recurring, deleted, "rec", { it.id }, { it.changedAt }),
            // чужой счётчик тоже двигаем: слоты разные, но пусть номера не отстают
            nextId = maxOf(mine.nextId, theirs.nextId),
            settings = mergeMoney(mine.settings, theirs.settings),
            deleted = prune(deleted.toMutableMap(), now),
        )
    }

    /**
     * Похоже на уже записанное: та же сумма в тот же день по тому же счёту.
     * Двое ходят в один магазин, и каждый может записать покупку сам.
     */
    fun duplicates(d: AppData): List<Pair<Tx, Tx>> {
        val out = mutableListOf<Pair<Tx, Tx>>()
        val byKey = mutableMapOf<String, Tx>()
        d.txs.filter { it.cat != CAT_TRANSFER }.forEach { t ->
            val key = "${t.acc}|${t.date}|${(t.amount * 100).toLong()}"
            val seen = byKey[key]
            if (seen == null) byKey[key] = t else if (seen.by != t.by) out += seen to t
        }
        return out
    }

    // ——— внутреннее ———

    /** Денежная модель — единственная часть настроек, общая для двоих. */
    private fun moneyOf(s: Settings) = listOf(s.mainCur, s.rates, s.marketRates, s.currencyCodes, s.customCurrencies)

    private fun mergeMoney(mine: Settings, theirs: Settings): Settings =
        if (theirs.moneyAt > mine.moneyAt) {
            mine.copy(
                mainCur = theirs.mainCur,
                rates = theirs.rates,
                marketRates = theirs.marketRates,
                currencyCodes = theirs.currencyCodes,
                customCurrencies = theirs.customCurrencies,
                moneyAt = theirs.moneyAt,
            )
        } else {
            mine
        }

    private inline fun <T> stampList(
        before: List<T>,
        after: List<T>,
        now: Long,
        tombs: MutableMap<String, Long>,
        prefix: String,
        key: (T) -> String,
        stampOne: (T) -> T,
    ): List<T> {
        val old = before.associateBy(key)
        val result = after.map { item ->
            val prev = old[key(item)]
            // сравниваем без отметки времени: иначе любая запись выглядела бы изменённой
            if (prev == null || stampOne(prev) != stampOne(item)) stampOne(item) else item
        }
        val alive = after.mapTo(HashSet()) { key(it) }
        before.forEach { if (key(it) !in alive) tombs["$prefix:${key(it)}"] = now }
        return result
    }

    private inline fun <T> mergeList(
        mine: List<T>,
        theirs: List<T>,
        deleted: Map<String, Long>,
        prefix: String,
        key: (T) -> String,
        changedAt: (T) -> Long,
    ): List<T> {
        val out = LinkedHashMap<String, T>(mine.size + theirs.size)
        mine.forEach { out[key(it)] = it }
        theirs.forEach { other ->
            val k = key(other)
            val own = out[k]
            out[k] = when {
                own == null -> other
                changedAt(other) > changedAt(own) -> other
                changedAt(other) < changedAt(own) -> own
                // одна и та же миллисекунда: выбираем одинаково на обоих телефонах
                other.toString() > own.toString() -> other
                else -> own
            }
        }
        return out.entries
            .filter { (k, v) ->
                val tomb = deleted["$prefix:$k"] ?: 0L
                // запись живёт, если её не удаляли или она новее удаления
                tomb == 0L || tomb < changedAt(v)
            }
            .map { it.value }
    }

    private fun prune(tombs: MutableMap<String, Long>, now: Long): Map<String, Long> {
        if (tombs.isEmpty()) return tombs
        val edge = now - TOMB_LIFETIME
        return tombs.filterValues { it > edge }
    }
}
