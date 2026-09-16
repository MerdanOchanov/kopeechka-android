package app.kopeechka.finance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kopeechka.finance.data.Account
import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.CAT_COST
import app.kopeechka.finance.data.CAT_GOAL
import app.kopeechka.finance.data.CAT_SALE
import app.kopeechka.finance.data.CAT_TRANSFER
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.Category
import app.kopeechka.finance.data.Csv
import app.kopeechka.finance.data.CurrencyDef
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.CAT_DEBT
import app.kopeechka.finance.data.CAT_DEBT_COST
import app.kopeechka.finance.data.CAT_DEBT_GAIN
import app.kopeechka.finance.data.Customer
import app.kopeechka.finance.data.Debt
import app.kopeechka.finance.data.DebtKind
import app.kopeechka.finance.data.DebtPayment
import app.kopeechka.finance.data.debtCur
import app.kopeechka.finance.data.debtLeft
import app.kopeechka.finance.data.splitPayment
import app.kopeechka.finance.data.Cut
import app.kopeechka.finance.data.Demo
import app.kopeechka.finance.data.decimalString
import app.kopeechka.finance.data.Goal
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.data.Order
import app.kopeechka.finance.data.OrderItem
import app.kopeechka.finance.data.OrderStatus
import app.kopeechka.finance.data.Palette
import app.kopeechka.finance.data.Period
import app.kopeechka.finance.data.Product
import app.kopeechka.finance.data.Settings
import app.kopeechka.finance.data.Tx
import app.kopeechka.finance.data.customerName
import app.kopeechka.finance.data.nextOrderNo
import app.kopeechka.finance.data.orderCost
import app.kopeechka.finance.data.orderCur
import app.kopeechka.finance.data.orderTotal
import app.kopeechka.finance.data.product
import app.kopeechka.finance.net.Ai
import app.kopeechka.finance.net.AiError
import app.kopeechka.finance.net.DriveApi
import app.kopeechka.finance.net.DriveError
import app.kopeechka.finance.net.RemoteBackup
import app.kopeechka.finance.net.formatBackupTime
import kotlinx.datetime.toLocalDateTime
import app.kopeechka.finance.net.backupMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import app.kopeechka.finance.data.today
import app.kopeechka.finance.data.toEpochDay
import app.kopeechka.finance.data.isoString
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class Tab { HOME, OPS, BUDGET, REPORT, SETTINGS }
enum class Page { ACCOUNTS, CATEGORIES, GOALS, BACKUP, CURRENCIES, BUSINESS, PRODUCTS, CUSTOMERS, DEBTS }
enum class Kind(val key: String) {
    EXPENSE("kind.expense"),
    INCOME("kind.income"),
    TRANSFER("kind.transfer"),
    DEBT("kind.debt"),
}

data class Draft(
    val editId: Long? = null,
    val kind: Kind = Kind.EXPENSE,
    val amount: String = "",
    val cat: String = "food",
    val incomeCat: String = "income",
    val from: String = "",
    val to: String = "",
    val note: String = "",
    val date: Long = today().toEpochDay(),
    // ——— долг ———
    /** DebtKind.LENT — дал в долг, DebtKind.BORROWED — взял. */
    val debtKind: String = DebtKind.LENT,
    val party: String = "",
    /** Когда вернуть; null — без срока. */
    val due: Long? = null,
    /** Сколько должно вернуться всего; пусто — столько же, сколько дали. */
    val expected: String = "",
)

sealed interface CurSheet {
    data object Main : CurSheet
    data class Acc(val id: String) : CurSheet
}

data class AccEdit(
    val id: String? = null,
    val name: String = "",
    val type: String = "",
    val mask: String = "",
    val cur: String = "RUB",
    val balance: String = "",
    val inTotal: Boolean = true,
)

data class CatEdit(
    val id: String? = null,
    val name: String = "",
    val code: String = "",
    val limit: String = "",
    val income: Boolean = false,
    val color: String = "",
)

/** Черновик валюты, которой нет в каталоге. */
data class CurrencyDraft(val code: String = "", val sym: String = "", val name: String = "", val rate: String = "")
data class GoalEdit(val id: String? = null, val name: String = "", val target: String = "", val hint: String = "", val cur: String = "RUB")
/** Шторка «отложить на цель». typed — своя сумма из поля; пусто, когда выбран пресет. */
data class GoalSheet(val goalId: String, val amount: Double, val from: String, val typed: String = "")
data class Confirm(val title: String, val text: String, val action: String, val onYes: () -> Unit)

/** Запрос к системному диалогу файлов: "create" — сохранить, "open" — открыть. */
data class FileRequest(val kind: String, val name: String)

// ——— «Дело»: черновики прайса, клиентов и заказов ———

data class ProductEdit(
    val id: String? = null,
    val name: String = "",
    val price: String = "",
    val cost: String = "",
    val unit: String = "",
)

data class CustomerEdit(
    val id: String? = null,
    val name: String = "",
    val contact: String = "",
    val note: String = "",
)

/** Позиция заказа в редакторе: числа держим строками, иначе не набрать «1,5». */
data class ItemDraft(
    val productId: String = "",
    val name: String = "",
    val qty: String = "1",
    val price: String = "",
    val cost: String = "",
)

data class OrderDraft(
    val id: Long? = null,
    val no: String = "",
    val customerId: String = "",
    /** Имя клиента, которого заводим прямо в заказе. */
    val newCustomer: String = "",
    val date: Long = today().toEpochDay(),
    val items: List<ItemDraft> = emptyList(),
    val discount: String = "",
    val extraCost: String = "",
    val cur: String = "",
    val status: String = OrderStatus.NEW,
    val note: String = "",
    /** Открыт выбор позиции из прайса. */
    val picking: Boolean = false,
)

/** Приём оплаты: на какой счёт и списывать ли себестоимость. */
data class PaySheet(val orderId: Long, val acc: String, val writeCost: Boolean = false)

/** Возврат по долгу: сколько и на какой счёт (или с какого). */
data class RepaySheet(val debtId: Long, val amount: String, val acc: String)

class AppViewModel(
    private val store: Storage,
    private val secure: SecretStore,
    private val platform: Platform,
) : ViewModel() {
    val data: StateFlow<AppData> = store.data

    var tab by mutableStateOf(Tab.HOME)
    var page by mutableStateOf<Page?>(null)
    var draft by mutableStateOf<Draft?>(null)
    var curSheet by mutableStateOf<CurSheet?>(null)
    var accEdit by mutableStateOf<AccEdit?>(null)
    var catEdit by mutableStateOf<CatEdit?>(null)
    var goalEdit by mutableStateOf<GoalEdit?>(null)
    var goalSheet by mutableStateOf<GoalSheet?>(null)
    var confirm by mutableStateOf<Confirm?>(null)
    var advisorOpen by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    var onbStep by mutableStateOf(0)

    /** Открытый календарь: "tx" — дата операции, "order" — дата заказа, "due" — срок возврата. */
    var datePick by mutableStateOf<String?>(null)

    /** Дата, которую сейчас показывает календарь. */
    fun pickedDate(): Long = when (datePick) {
        "order" -> orderDraft?.date
        "due" -> draft?.due ?: draft?.date?.plus(30)
        else -> draft?.date
    } ?: today().toEpochDay()

    fun pickDate(day: Long) {
        when (datePick) {
            "order" -> orderDraft = orderDraft?.copy(date = day)
            "due" -> draft = draft?.copy(due = day)
            "tx" -> draft = draft?.copy(date = day)
        }
        datePick = null
    }

    var debtCard by mutableStateOf<Long?>(null)
    var repaySheet by mutableStateOf<RepaySheet?>(null)

    var orderDraft by mutableStateOf<OrderDraft?>(null)
    var productEdit by mutableStateOf<ProductEdit?>(null)
    var customerEdit by mutableStateOf<CustomerEdit?>(null)
    var paySheet by mutableStateOf<PaySheet?>(null)

    /** Фильтр списка заказов: "all", "open", "paid". */
    var orderFilter by mutableStateOf("all")

    /** Период на странице «Дело» — отдельный от отчётов. */
    var bizPeriod by mutableStateOf(Period.MONTH)
    var bizOffset by mutableStateOf(0)

    var opsFilter by mutableStateOf("all")
    var period by mutableStateOf(Period.MONTH)
    var cut by mutableStateOf(Cut.CATS)

    /** 0 — текущий период, −1 — предыдущий и так далее. */
    var periodOffset by mutableStateOf(0)

    /** Фильтры отчёта: null — без ограничения. */
    var filterAcc by mutableStateOf<String?>(null)
    var filterCat by mutableStateOf<String?>(null)
    var filtersOpen by mutableStateOf(false)

    val hasFilters get() = filterAcc != null || filterCat != null

    fun resetFilters() {
        filterAcc = null
        filterCat = null
    }

    /** Данные, суженные фильтрами отчёта: остальные экраны считают по полным. */
    fun filtered(d: AppData): AppData {
        if (!hasFilters) return d
        return d.copy(
            txs = d.txs.filter { t ->
                (filterAcc == null || t.acc == filterAcc || t.toAcc == filterAcc) &&
                    (filterCat == null || t.cat == filterCat)
            },
        )
    }
    var currencyPicker by mutableStateOf(false)
    var currencyQuery by mutableStateOf("")
    var currencyDraft by mutableStateOf<CurrencyDraft?>(null)

    var question by mutableStateOf("")
    var asking by mutableStateOf(false)
    var answer by mutableStateOf("")
    var answerFrom by mutableStateOf("")
    var answerMeta by mutableStateOf("")
    var apiKeyInput by mutableStateOf("")

    var driveBusy by mutableStateOf(false)
    var driveList by mutableStateOf<List<RemoteBackup>>(emptyList())

    val calc: Calc get() = Calc(store.current)

    /** Версия сборки — для строки «о программе». */
    val version: String get() = platform.version

    /** Язык интерфейса: из настроек либо системный. */
    val l: Lang get() = Lang.of(store.current.settings.lang)

    init {
        val s = store.current.settings
        platform.onLanguageChanged(l)
        platform.syncReminder(s.remind, s.remindHour)
        platform.syncAutoBackup(s.autoBackup && s.driveLinked)
    }

    // ——— общее ———

    private var toastJob: Job? = null
    fun flash(msg: String) {
        toast = msg
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(2600)
            toast = null
        }
    }

    private fun say(key: String, vararg args: Any?) = flash(l.t(key, *args))

    fun settings(f: (Settings) -> Settings) = store.update { it.copy(settings = f(it.settings)) }

    /** Системная кнопка «назад»: закрывает верхний слой. false — слоёв нет, можно выходить. */
    fun back(): Boolean {
        when {
            confirm != null -> confirm = null
            datePick != null -> datePick = null
            repaySheet != null -> repaySheet = null
            debtCard != null -> debtCard = null
            paySheet != null -> paySheet = null
            orderDraft?.picking == true -> orderDraft = orderDraft?.copy(picking = false)
            orderDraft != null -> orderDraft = null
            productEdit != null -> productEdit = null
            customerEdit != null -> customerEdit = null
            goalSheet != null -> goalSheet = null
            curSheet != null -> curSheet = null
            accEdit != null -> accEdit = null
            catEdit != null -> catEdit = null
            goalEdit != null -> goalEdit = null
            draft != null -> draft = null
            csvPreview != null -> csvPreview = null
            csvExportSheet -> csvExportSheet = false
            currencyDraft != null -> currencyDraft = null
            currencyPicker -> currencyPicker = false
            advisorOpen -> advisorOpen = false
            page != null -> page = null
            tab != Tab.HOME -> tab = Tab.HOME
            else -> return false
        }
        return true
    }

    fun go(t: Tab) {
        tab = t
        page = null
    }

    fun openPage(p: Page) {
        tab = Tab.SETTINGS
        page = p
    }

    /** Валюта, выбранная на последнем шаге заставки; пусто — ещё не выбирали. */
    var onbCur by mutableStateOf("")

    fun onbCurrency(): String = onbCur.ifBlank { store.current.settings.mainCur }

    /** Ответ на шаге заставки «ведёте своё дело». */
    var onbBusiness by mutableStateOf(false)

    /** Конец заставки: демо-данные в выбранной валюте, она же становится базой курсов. */
    fun finishOnboarding() {
        val cur = onbCurrency()
        val keep = store.current.settings
        val demo = Demo.create(l, cur, today(), onbBusiness)
        store.replace(
            demo.copy(
                settings = keep.copy(
                    onboarded = true,
                    mainCur = cur,
                    rates = demo.settings.rates,
                    currencyCodes = demo.settings.currencyCodes,
                    business = onbBusiness,
                ),
            ),
        )
    }

    /** Последний шаг заставки: без примеров, один пустой счёт в выбранной валюте. */
    fun startClean() {
        val cur = onbCurrency()
        val base = Demo.empty(
            store.current.settings.copy(onboarded = true, mainCur = cur, rates = Demo.rates(cur), business = onbBusiness),
        )
        store.replace(if (onbBusiness) base.copy(categories = base.categories + Demo.bizCategories(l)) else base)
    }

    fun setDark(on: Boolean) = settings { it.copy(dark = on) }

    /** Смена языка: интерфейс, названия валют, папка копий и канал уведомлений. */
    fun setLang(code: String) {
        settings { it.copy(lang = code) }
        val nl = l
        Currencies.setLang(nl)
        platform.onLanguageChanged(nl)
        flash(nl.t("set.lang") + ": " + Lang.title(code))
    }

    // ——— операции ———

    fun openAdd(kind: Kind = Kind.EXPENSE) {
        val d = store.current
        val first = d.accounts.firstOrNull()?.id ?: ""
        val second = d.accounts.firstOrNull { it.id != first }?.id ?: first
        draft = Draft(
            kind = kind,
            cat = d.categories.firstOrNull { !it.income }?.id ?: "other",
            incomeCat = d.categories.firstOrNull { it.income }?.id ?: "income",
            from = first,
            to = second,
        )
        toast = null
    }

    fun openEdit(t: Tx) {
        if (t.cat == CAT_DEBT) {
            val debt = store.current.debts.firstOrNull { it.txId == t.id || it.payments.any { p -> p.txId == t.id } }
            if (debt != null) {
                debtCard = debt.id
                return
            }
        }
        if (t.cat == CAT_GOAL) {
            confirm = Confirm(
                l.t("msg.goalContribution"),
                l.t("msg.deleteContribution", t.title),
                l.t("common.delete"),
            ) { deleteTx(t.id) }
            return
        }
        val d = store.current
        val c = calc
        val kind = when {
            t.cat == CAT_TRANSFER -> Kind.TRANSFER
            t.amount > 0 -> Kind.INCOME
            else -> Kind.EXPENSE
        }
        val defExp = d.categories.firstOrNull { !it.income }?.id ?: "other"
        val defInc = d.categories.firstOrNull { it.income }?.id ?: "income"
        draft = Draft(
            editId = t.id,
            kind = kind,
            amount = abs(t.amount).roundToLong().toString(),
            cat = if (kind == Kind.EXPENSE) t.cat else defExp,
            incomeCat = if (kind == Kind.INCOME) t.cat else defInc,
            from = t.acc,
            to = t.toAcc ?: (d.accounts.firstOrNull { it.id != t.acc }?.id ?: t.acc),
            note = when {
                kind == Kind.TRANSFER -> t.note
                t.title == c.cat(t.cat).name -> ""
                else -> t.title
            },
            date = t.date,
        )
    }

    fun press(k: String) {
        val d = draft ?: return
        val a = d.amount
        val n = when {
            k == "⌫" -> a.dropLast(1)
            k == "000" -> if (a.isNotEmpty() && a.length <= 6) a + "000" else a
            a.length >= 9 -> a
            k == "0" && a.isEmpty() -> a
            else -> a + k
        }
        draft = d.copy(amount = n)
    }

    fun saveDraft() {
        val d = draft ?: return
        val v = d.amount.toLongOrNull()?.toDouble() ?: 0.0
        if (v <= 0) return say("msg.enterAmount")
        val c = calc
        val src = c.acc(d.from) ?: return say("msg.noAccount")
        if (d.kind == Kind.DEBT) return saveDebtDraft(d, v, src)
        val id = d.editId ?: store.current.nextId
        // сколько эта же операция уже списывала со счёта — при правке её нужно вернуть
        val old = d.editId?.let { eid -> store.current.txs.firstOrNull { it.id == eid && it.acc == src.id }?.amount } ?: 0.0
        val tx = when (d.kind) {
            Kind.TRANSFER -> {
                val dst = c.acc(d.to) ?: return say("msg.pickToAcc")
                if (src.id == dst.id) return say("msg.sameAccounts")
                if (!c.s.allowNegative && c.balance(src) - old < v) return say("msg.notEnough", src.name)
                val got = c.conv(v, src.cur, dst.cur)
                Tx(id, d.date, l.t("msg.transferTitle", src.name, dst.name), CAT_TRANSFER, src.id, -v, d.note, dst.id, got)
            }
            Kind.DEBT -> return
            Kind.INCOME -> Tx(id, d.date, d.note.trim().ifBlank { c.cat(d.incomeCat).name }, d.incomeCat, src.id, v)
            Kind.EXPENSE -> {
                if (!c.s.allowNegative && c.balance(src) - old < v) {
                    return say("msg.notEnoughHint", src.name, c.fmt(v - (c.balance(src) - old), src.cur))
                }
                Tx(id, d.date, d.note.trim().ifBlank { c.cat(d.cat).name }, d.cat, src.id, -v)
            }
        }
        store.update { s ->
            if (d.editId != null) s.copy(txs = s.txs.map { if (it.id == d.editId) tx else it })
            else s.copy(txs = listOf(tx) + s.txs, nextId = s.nextId + 1)
        }
        draft = null
        when (d.kind) {
            Kind.DEBT -> Unit
            Kind.TRANSFER -> say("msg.transferDone", c.fmt(v, src.cur), c.fmt(tx.toAmount ?: 0.0, c.accCur(tx.toAcc)))
            Kind.INCOME -> say("msg.incomeDone", c.fmt(v, src.cur), c.cat(tx.cat).name)
            Kind.EXPENSE -> if (d.editId != null) say("msg.changed", c.fmt(v, src.cur), c.cat(tx.cat).name)
            else say("msg.expenseDone", c.fmt(v, src.cur), c.cat(tx.cat).name)
        }
    }

    fun askDeleteTx(id: Long) {
        confirm = Confirm(l.t("msg.deleteTx"), l.t("msg.deleteTxText"), l.t("common.delete")) { deleteTx(id) }
    }

    private fun deleteTx(id: Long) {
        store.update { s ->
            val t = s.txs.firstOrNull { it.id == id } ?: return@update s
            var goals = s.goals
            if (t.cat == CAT_GOAL && t.goal != null) {
                val c = Calc(s)
                goals = goals.map { g ->
                    if (g.id == t.goal) g.copy(saved = max(0.0, g.saved - c.conv(-t.amount, c.accCur(t.acc), g.cur))) else g
                }
            }
            // операция могла быть частью долга — убираем её и оттуда
            val debts = s.debts.map { dbt ->
                when {
                    dbt.txId == id -> dbt.copy(txId = null)
                    dbt.payments.any { p -> p.txId == id || p.extraTxId == id } ->
                        dbt.copy(payments = dbt.payments.filterNot { p -> p.txId == id || p.extraTxId == id })
                    else -> dbt
                }
            }
            // операция могла быть создана оплатой заказа — снимаем с него отметку об оплате
            val orders = s.orders.map { o ->
                when (id) {
                    o.incomeTxId -> o.copy(status = OrderStatus.DONE, incomeTxId = null)
                    o.costTxId -> o.copy(costTxId = null)
                    else -> o
                }
            }
            s.copy(txs = s.txs.filterNot { it.id == id }, goals = goals, orders = orders, debts = debts)
        }
        draft = null
        say("msg.txDeleted")
    }

    // ——— валюты ———

    /**
     * Смена основной валюты. Она же база курсов, поэтому все курсы пересчитываются
     * относительно новой валюты, а лимиты категорий переводятся в неё.
     * Остатки счетов и суммы операций не трогаем — они хранятся в валютах счетов.
     */
    fun setMainCur(code: String) {
        curSheet = null
        if (code == store.current.settings.mainCur) return
        confirm = Confirm(
            l.t("msg.mainCurTitle"),
            l.t("msg.mainCurText", code + " (" + Currencies.sym(code) + ")"),
            l.t("msg.mainCurAction"),
        ) { applyMainCur(code) }
    }

    private fun applyMainCur(code: String) {
        store.update { s ->
            val c = Calc(s)
            val div = c.rate(code).takeIf { it > 0 } ?: 1.0
            val old = s.settings.mainCur
            val rates = s.settings.rates.mapValues { (_, v) -> v / div } +
                (code to 1.0) +
                (old to c.rate(old) / div)
            s.copy(
                categories = s.categories.map { it.copy(limitBase = it.limitBase / div) },
                // цены прайса тоже хранятся в основной валюте; суммы заказов — в своих
                products = s.products.map { it.copy(price = it.price / div, cost = it.cost / div) },
                settings = s.settings.copy(
                    mainCur = code,
                    rates = rates,
                    currencyCodes = (listOf(code) + s.settings.currencyCodes).distinct(),
                ),
            )
        }
        say("msg.mainCurSet", Currencies.info(code).name)
    }

    fun setAccCur(accId: String, code: String) {
        val a = calc.acc(accId) ?: return
        store.update { s ->
            val c = Calc(s)
            if (a.cur == code) return@update s
            val k = c.rate(a.cur) / c.rate(code)
            s.copy(
                accounts = s.accounts.map { if (it.id == accId) it.copy(cur = code, initial = it.initial * k) else it },
                txs = s.txs.map { t ->
                    var n = t
                    if (t.acc == accId) n = n.copy(amount = t.amount * k)
                    if (t.toAcc == accId) n = n.copy(toAmount = (t.toAmount ?: 0.0) * k)
                    n
                },
            )
        }
        curSheet = null
        accEdit = accEdit?.let { e ->
            if (e.id != accId) e
            else e.copy(cur = code, balance = calc.acc(accId)?.let { calc.balance(it).roundToLong().toString() } ?: e.balance)
        }
        say("msg.accCurSet", a.name, Currencies.info(code).inName)
    }

    fun setRate(code: String, text: String) {
        val v = text.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: return
        if (v <= 0) return
        settings { it.copy(rates = it.rates + (code to v)) }
    }

    /**
     * Курс пары прямо в операции: «1 from = x to».
     * Меняется курс небазовой валюты, доллар остаётся базой.
     */
    fun setPairRate(from: String, to: String, text: String) {
        val x = text.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: return
        if (x <= 0 || from == to) return
        val c = calc
        when {
            from != c.main -> settings { it.copy(rates = it.rates + (from to x * c.rate(to))) }
            to != c.main -> settings { it.copy(rates = it.rates + (to to c.rate(from) / x)) }
        }
    }

    /** Добавить валюту из каталога. */
    fun addCurrency(code: String) {
        val c = code.trim().uppercase()
        if (c.isBlank()) return
        settings { s ->
            s.copy(
                currencyCodes = (s.currencyCodes + c).distinct(),
                rates = if (s.rates.containsKey(c)) s.rates else s.rates + (c to Currencies.hintRate(c, s.mainCur)),
            )
        }
        currencyPicker = false
        currencyQuery = ""
        say("msg.curAdded", Currencies.info(c).name)
    }

    /** Добавить валюту, которой нет в каталоге. */
    fun saveCustomCurrency() {
        val dft = currencyDraft ?: return
        val code = dft.code.trim().uppercase()
        if (code.length !in 2..6) return say("msg.curCodeLen")
        if (calc.currencies.contains(code)) return say("msg.curExists")
        val rate = dft.rate.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: 0.0
        if (rate <= 0) return say("msg.curRate", store.current.settings.mainCur)
        val name = dft.name.trim().ifBlank { code }
        val def = CurrencyDef(code, dft.sym.trim().ifBlank { code }, name, name)
        settings { s ->
            s.copy(
                currencyCodes = (s.currencyCodes + code).distinct(),
                customCurrencies = s.customCurrencies.filterNot { it.code == code } + def,
                rates = s.rates + (code to rate),
            )
        }
        currencyDraft = null
        currencyPicker = false
        say("msg.curAddedCode", code)
    }

    fun removeCurrency(code: String) {
        val d = store.current
        if (d.settings.mainCur == code) return say("msg.curBase", Currencies.info(code).name)
        if (d.accounts.any { it.cur == code }) return say("msg.curUsedAcc")
        if (d.goals.any { it.cur == code }) return say("msg.curUsedGoal")
        settings { s ->
            s.copy(
                currencyCodes = s.currencyCodes.filterNot { it == code },
                customCurrencies = s.customCurrencies.filterNot { it.code == code },
            )
        }
        say("msg.curRemoved", code)
    }

    // ——— периоды отчётов ———

    fun selectPeriod(p: Period) {
        period = p
        periodOffset = 0
    }

    fun shiftPeriod(delta: Int) {
        val n = periodOffset + delta
        if (n <= 0) periodOffset = n
    }

    /** Переход из карточки на главном в нужный отчёт: фильтры при этом сбрасываются. */
    fun goReport(p: Period, c: Cut, offset: Int = 0) {
        period = p
        cut = c
        periodOffset = offset
        resetFilters()
        page = null
        tab = Tab.REPORT
    }

    // ——— счета ———

    fun openAccEdit(a: Account?) {
        accEdit = if (a == null) AccEdit(cur = store.current.settings.mainCur, type = l.t("acc.type.card"))
        else AccEdit(a.id, a.name, a.type, a.mask, a.cur, calc.balance(a).roundToLong().toString(), a.inTotal)
    }

    fun saveAcc() {
        val e = accEdit ?: return
        val name = e.name.trim()
        if (name.isEmpty()) return say("msg.enterAccName")
        val bal = e.balance.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: 0.0
        store.update { s ->
            if (e.id == null) {
                s.copy(accounts = s.accounts + Account("a${s.nextId}", name, e.type, e.mask.trim(), e.cur, bal, e.inTotal), nextId = s.nextId + 1)
            } else {
                val c = Calc(s)
                s.copy(accounts = s.accounts.map {
                    if (it.id == e.id) it.copy(name = name, type = e.type, mask = e.mask.trim(), initial = bal - (c.balance(it) - it.initial), inTotal = e.inTotal) else it
                })
            }
        }
        accEdit = null
        if (e.id == null) say("msg.accAdded", name) else say("msg.accSaved")
    }

    fun toggleInTotal(id: String) = store.update { s -> s.copy(accounts = s.accounts.map { if (it.id == id) it.copy(inTotal = !it.inTotal) else it }) }

    fun askDeleteAcc(id: String) {
        val d = store.current
        val a = d.accounts.firstOrNull { it.id == id } ?: return
        if (d.accounts.size <= 1) return say("msg.needOneAccount")
        val n = d.txs.count { it.acc == id || it.toAcc == id }
        confirm = Confirm(
            l.t("msg.deleteAccTitle"),
            if (n > 0) l.t("msg.deleteAccWithTx", a.name, l.n(n, "op")) else l.t("msg.deleteAccPlain", a.name),
            l.t("common.delete"),
        ) {
            store.update { s -> s.copy(accounts = s.accounts.filterNot { it.id == id }, txs = s.txs.filterNot { it.acc == id || it.toAcc == id }) }
            accEdit = null
            say("msg.accDeleted")
        }
    }

    // ——— категории ———

    fun openCatEdit(c: Category?, income: Boolean = false) {
        val cc = calc
        catEdit = if (c == null) CatEdit(income = income, color = Palette.HEXES.first())
        else CatEdit(c.id, c.name, c.code, if (c.limitBase > 0) cc.limitMain(c).roundToLong().toString() else "", c.income, cc.colorOf(c.id))
    }

    fun saveCat() {
        val e = catEdit ?: return
        val name = e.name.trim()
        if (name.isEmpty()) return say("msg.enterCatName")
        val code = e.code.trim().ifBlank { name.filter { it.isLetter() }.take(2) }.uppercase().take(2).ifBlank { "??" }
        val c = calc
        // лимит вводится и хранится в основной валюте
        val limitBase = e.limit.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: 0.0
        store.update { s ->
            if (e.id == null) {
                s.copy(
                    categories = s.categories + Category("c${s.nextId}", code, name, if (e.income) 0.0 else limitBase, e.income, e.color),
                    nextId = s.nextId + 1,
                )
            } else {
                s.copy(
                    categories = s.categories.map {
                        if (it.id == e.id) it.copy(name = name, code = code, limitBase = if (it.income) 0.0 else limitBase, color = e.color) else it
                    },
                )
            }
        }
        catEdit = null
        if (e.id == null) say("msg.catCreated", name) else say("msg.catSaved")
    }

    fun askDeleteCat(id: String) {
        val d = store.current
        val cat = d.categories.firstOrNull { it.id == id } ?: return
        val sameKind = d.categories.filter { it.income == cat.income && it.id != id }
        val target = sameKind.firstOrNull { it.id == "other" } ?: sameKind.firstOrNull()
            ?: return say(if (cat.income) "msg.lastCatIncome" else "msg.lastCatExpense")
        val n = d.txs.count { it.cat == id }
        confirm = Confirm(
            l.t("msg.deleteCatTitle"),
            if (n > 0) l.t("msg.deleteCatMove", l.n(n, "op"), cat.name, target.name) else l.t("msg.deleteCatPlain", cat.name),
            l.t("common.delete"),
        ) {
            store.update { s -> s.copy(categories = s.categories.filterNot { it.id == id }, txs = s.txs.map { if (it.cat == id) it.copy(cat = target.id) else it }) }
            catEdit = null
            say("msg.catDeleted")
        }
    }

    // ——— цели ———

    fun openGoalEdit(g: Goal?) {
        goalEdit = if (g == null) GoalEdit(cur = store.current.settings.mainCur)
        else GoalEdit(g.id, g.name, g.target.roundToLong().toString(), g.hint, g.cur)
    }

    fun saveGoal() {
        val e = goalEdit ?: return
        val name = e.name.trim()
        val target = e.target.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: 0.0
        if (name.isEmpty() || target <= 0) return say("msg.enterGoal")
        store.update { s ->
            if (e.id == null) s.copy(goals = s.goals + Goal("g${s.nextId}", name, target, 0.0, e.cur, e.hint.trim()), nextId = s.nextId + 1)
            else s.copy(goals = s.goals.map { if (it.id == e.id) it.copy(name = name, target = target, hint = e.hint.trim()) else it })
        }
        goalEdit = null
        if (e.id == null) say("msg.goalCreated", name) else say("msg.goalSaved")
    }

    fun askDeleteGoal(id: String) {
        val g = store.current.goals.firstOrNull { it.id == id } ?: return
        confirm = Confirm(l.t("msg.deleteGoalTitle"), l.t("msg.deleteGoalText", g.name), l.t("common.delete")) {
            store.update { s -> s.copy(goals = s.goals.filterNot { it.id == id }) }
            goalEdit = null
            say("msg.goalDeleted")
        }
    }

    fun goalPresets(cur: String): List<Double> = when (cur) {
        "USD", "EUR", "GBP" -> listOf(10.0, 50.0, 100.0, 250.0)
        "KZT", "UZS", "IDR", "VND" -> listOf(5000.0, 10000.0, 25000.0, 50000.0)
        "TMT" -> listOf(50.0, 100.0, 250.0, 500.0)
        else -> listOf(1000.0, 5000.0, 10000.0, 25000.0)
    }

    fun openGoalSheet(goalId: String) {
        val g = store.current.goals.firstOrNull { it.id == goalId } ?: return
        val from = store.current.accounts.firstOrNull { it.cur == g.cur }?.id ?: store.current.accounts.firstOrNull()?.id ?: return
        goalSheet = GoalSheet(goalId, goalPresets(g.cur)[1], from)
    }

    /** Выбран готовый вариант — поле своей суммы очищается. */
    fun setGoalPreset(amount: Double) {
        goalSheet = goalSheet?.copy(amount = amount, typed = "")
    }

    /** Ввод своей суммы: пресеты перестают быть выбранными. */
    fun setGoalTyped(text: String) {
        goalSheet = goalSheet?.copy(amount = num(text), typed = text)
    }

    /** Сколько осталось до цели; ноль — цель уже достигнута. */
    fun goalLeft(g: Goal): Double = (g.target - g.saved).coerceAtLeast(0.0)

    fun contribute() {
        val gs = goalSheet ?: return
        if (gs.amount <= 0) return
        val c = calc
        val g = store.current.goals.firstOrNull { it.id == gs.goalId } ?: return
        val a = c.acc(gs.from) ?: return
        val debit = c.conv(gs.amount, g.cur, a.cur)
        if (!c.s.allowNegative && c.balance(a) < debit) return say("msg.notEnoughGoal", a.name)
        store.update { s ->
            s.copy(
                txs = listOf(Tx(s.nextId, c.todayDay, l.t("msg.goalTitle", g.name), CAT_GOAL, a.id, -debit, goal = g.id)) + s.txs,
                nextId = s.nextId + 1,
                goals = s.goals.map { if (it.id == g.id) it.copy(saved = it.saved + gs.amount) else it },
            )
        }
        goalSheet = null
        say("msg.goalDone", c.fmt(gs.amount, g.cur), g.name)
    }

    // ——— настройки и данные ———

    fun setRemind(on: Boolean) {
        settings { it.copy(remind = on) }
        platform.syncReminder(on, store.current.settings.remindHour)
        if (on) say("msg.remindSet", store.current.settings.remindHour)
    }

    fun setRemindHour(h: Int) {
        settings { it.copy(remindHour = h) }
        platform.syncReminder(store.current.settings.remind, h)
    }

    fun setAutoBackup(on: Boolean) {
        settings { it.copy(autoBackup = on) }
        platform.syncAutoBackup(on && store.current.settings.driveLinked)
    }

    fun askLoadDemo() {
        confirm = Confirm(l.t("msg.demoTitle"), l.t("msg.demoText"), l.t("msg.demoReplace")) {
            val s = store.current.settings
            // Демо приходит в валюте, которой человек уже пользуется: основная валюта
            // и курсы остаются прежними, иначе база курсов разошлась бы с mainCur.
            val demo = Demo.create(l, s.mainCur)
            store.replace(
                demo.copy(
                    settings = s.copy(
                        onboarded = true,
                        rates = demo.settings.rates + s.rates + (s.mainCur to 1.0),
                        currencyCodes = (listOf(s.mainCur) + s.currencyCodes).distinct(),
                    ),
                ),
            )
            say("msg.demoLoaded")
        }
    }

    fun askClearAll() {
        confirm = Confirm(l.t("msg.clearTitle"), l.t("msg.clearText"), l.t("msg.clearAction")) {
            store.replace(Demo.empty(store.current.settings))
            say("msg.cleared")
        }
    }

    // ——— CSV ———

    var csvExportSheet by mutableStateOf(false)
    var csvPreview by mutableStateOf<Csv.Preview?>(null)

    fun askExportCsv() {
        csvExportSheet = true
    }

    /** scope: WEEK / MONTH / QUARTER / YEAR из [Period] либо "all". */
    fun exportCsv(scope: String) {
        csvExportSheet = false
        val r = if (scope == "all") null else calc.range(Period.valueOf(scope), 0).let { it.from to it.to }
        val d = store.current
        val count = d.txs.count { t -> r == null || (t.date >= r.first && t.date <= r.second) }
        saveFile("kopeechka-${today().isoString()}.csv", Csv.export(d, r?.first, r?.second)) { name ->
            say("csv.exported", l.n(count, "op"), name)
        }
    }

    fun saveTemplate() {
        saveFile("kopeechka-template.csv", Csv.template(store.current, l)) { name ->
            say("csv.templateSaved", name)
        }
    }

    fun askImportCsv() {
        viewModelScope.launch {
            val picked = try {
                platform.openTextFile()
            } catch (e: Exception) {
                return@launch say("csv.fileError")
            } ?: return@launch
            val p = Csv.parse(picked.text, store.current, l)
            if (p.rows.isEmpty() && p.errors.isEmpty()) say("csv.nothing") else csvPreview = p
        }
    }

    /** Сохранение через системный диалог: платформа сама спросит, куда положить файл. */
    private fun saveFile(name: String, text: String, done: (String) -> Unit) {
        viewModelScope.launch {
            val saved = try {
                platform.saveTextFile(name, text)
            } catch (e: Exception) {
                return@launch say("csv.fileError")
            }
            if (saved != null) done(saved)
        }
    }

    fun applyImport() {
        val p = csvPreview ?: return
        store.update { Csv.apply(it, p, l) }
        csvPreview = null
        say("csv.imported", l.n(p.rows.size, "op"))
    }

    // ——— Долги ———

    /** Категории заработка на долге и переплаты — создаются, когда впервые понадобятся. */
    private fun withDebtCats(s: AppData): List<Category> =
        s.categories + Demo.debtCategories(l).filterNot { b -> s.categories.any { it.id == b.id } }

    fun openDebt(id: Long) {
        debtCard = id
    }

    /** Создание долга с экрана новой операции. */
    private fun saveDebtDraft(d: Draft, v: Double, src: Account) {
        val c = calc
        val lent = d.debtKind == DebtKind.LENT
        if (lent && !c.s.allowNegative && c.balance(src) < v) {
            return say("msg.notEnoughHint", src.name, c.fmt(v - c.balance(src), src.cur))
        }
        val party = d.party.trim().ifBlank { l.t("debt.noParty") }
        val expected = num(d.expected).takeIf { it > 0 } ?: v
        val title = l.t(if (lent) "debt.txLent" else "debt.txBorrowed", party)
        store.update { s ->
            var nextId = s.nextId
            val txId = nextId++
            val debtId = nextId++
            val tx = Tx(txId, d.date, title, CAT_DEBT, src.id, if (lent) -v else v, d.note.trim())
            val debt = Debt(
                id = debtId,
                kind = d.debtKind,
                party = party,
                principal = v,
                expected = expected,
                cur = src.cur,
                date = d.date,
                due = d.due,
                acc = src.id,
                txId = txId,
                note = d.note.trim(),
            )
            s.copy(txs = listOf(tx) + s.txs, debts = s.debts + debt, nextId = nextId)
        }
        draft = null
        say(if (lent) "debt.lentDone" else "debt.borrowedDone", c.fmt(v, src.cur), party)
    }

    /** Возврат: по умолчанию весь остаток и тот же счёт. */
    fun openRepay(debtId: Long) {
        val c = calc
        val debt = store.current.debts.firstOrNull { it.id == debtId } ?: return
        val acc = c.acc(debt.acc)?.id ?: store.current.accounts.firstOrNull()?.id ?: return say("msg.noAccount")
        repaySheet = RepaySheet(debtId, numText(c.debtLeft(debt)), acc)
    }

    /**
     * Платёж по долгу. Сначала закрывается тело (служебная категория, отчёты не трогает),
     * остаток — настоящий доход по данному в долг либо расход по взятому.
     */
    fun confirmRepay() {
        val rs = repaySheet ?: return
        val c = calc
        val debt = store.current.debts.firstOrNull { it.id == rs.debtId } ?: return
        val acc = c.acc(rs.acc) ?: return say("msg.noAccount")
        val amount = num(rs.amount)
        if (amount <= 0) return say("msg.enterAmount")
        val cur = c.debtCur(debt)
        val lent = debt.kind == DebtKind.LENT
        val inAcc = c.conv(amount, cur, acc.cur)
        if (!lent && !c.s.allowNegative && c.balance(acc) < inAcc) {
            return say("msg.notEnoughHint", acc.name, c.fmt(inAcc - c.balance(acc), acc.cur))
        }
        val (body, extra) = c.splitPayment(debt, amount)
        val day = c.todayDay
        store.update { s ->
            var nextId = s.nextId
            val add = mutableListOf<Tx>()
            var bodyTxId: Long? = null
            if (body > 0) {
                val id = nextId++
                bodyTxId = id
                val sum = c.conv(body, cur, acc.cur)
                add += Tx(id, day, l.t("debt.txBack", debt.party), CAT_DEBT, acc.id, if (lent) sum else -sum)
            }
            var extraTxId: Long? = null
            if (extra > 0.005) {
                val id = nextId++
                extraTxId = id
                val sum = c.conv(extra, cur, acc.cur)
                add += Tx(
                    id,
                    day,
                    l.t(if (lent) "debt.txGain" else "debt.txCost", debt.party),
                    if (lent) CAT_DEBT_GAIN else CAT_DEBT_COST,
                    acc.id,
                    if (lent) sum else -sum,
                )
            }
            val payment = DebtPayment(nextId++, day, amount, bodyTxId, extraTxId)
            s.copy(
                categories = if (extra > 0.005) withDebtCats(s) else s.categories,
                txs = add + s.txs,
                debts = s.debts.map { if (it.id == debt.id) it.copy(payments = it.payments + payment) else it },
                nextId = nextId,
            )
        }
        repaySheet = null
        val after = calc
        val left = after.d.debts.firstOrNull { it.id == debt.id }?.let { after.debtLeft(it) } ?: 0.0
        if (left <= 0.005) {
            debtCard = null
            say("debt.closedDone", debt.party)
        } else {
            say("debt.paidPart", c.fmt(amount, cur), c.fmt(left, cur))
        }
    }

    /** Закрыть без денег: простили, списали, договорились. */
    fun askCloseDebt(id: Long) {
        val c = calc
        val debt = store.current.debts.firstOrNull { it.id == id } ?: return
        confirm = Confirm(
            l.t("debt.closeTitle"),
            l.t("debt.closeText", c.fmt(c.debtLeft(debt), c.debtCur(debt))),
            l.t("debt.closeAction"),
        ) {
            store.update { s -> s.copy(debts = s.debts.map { if (it.id == id) it.copy(closed = true) else it }) }
            debtCard = null
            say("debt.closed")
        }
    }

    fun reopenDebt(id: Long) {
        store.update { s -> s.copy(debts = s.debts.map { if (it.id == id) it.copy(closed = false) else it }) }
    }

    fun askDeleteDebt(id: Long) {
        val debt = store.current.debts.firstOrNull { it.id == id } ?: return
        confirm = Confirm(l.t("debt.deleteTitle"), l.t("debt.deleteText", debt.party), l.t("common.delete")) {
            store.update { s ->
                val drop = (listOf(debt.txId) + debt.payments.flatMap { listOf(it.txId, it.extraTxId) }).filterNotNull().toSet()
                s.copy(debts = s.debts.filterNot { it.id == id }, txs = s.txs.filterNot { it.id in drop })
            }
            debtCard = null
            repaySheet = null
            say("debt.deleted")
        }
    }

    // ——— «Дело»: прайс, клиенты, заказы ———

    /** Число из поля ввода: «1 200,50», «1.5», «1,5» — всё одно. */
    private fun num(text: String): Double {
        val t = text.trim().replace(" ", "").replace(" ", "").replace(',', '.')
        val i = t.lastIndexOf('.')
        val clean = if (i < 0) t else t.substring(0, i).replace(".", "") + "." + t.substring(i + 1)
        return clean.toDoubleOrNull() ?: 0.0
    }

    /** Число обратно в поле: целое — без хвоста, дробное — с разделителем языка. */
    private fun numText(v: Double): String {
        if (v == v.roundToLong().toDouble()) return v.roundToLong().toString()
        val s = decimalString(v, 2)
        return if (l.code == "en") s else s.replace('.', ',')
    }

    /** Категории «Продажи» и «Себестоимость» — без них оплате некуда записаться. */
    private fun withBizCats(s: AppData): List<Category> =
        s.categories + Demo.bizCategories(l).filterNot { b -> s.categories.any { it.id == b.id } }

    fun setBusiness(on: Boolean) {
        store.update { s -> s.copy(categories = if (on) withBizCats(s) else s.categories, settings = s.settings.copy(business = on)) }
        if (!on && page in listOf(Page.BUSINESS, Page.PRODUCTS, Page.CUSTOMERS)) page = null
        if (!on && cut.biz) cut = Cut.CATS
        say(if (on) "biz.turnedOn" else "biz.turnedOff")
    }

    /** Пример дела: прайс, клиенты и заказы — чтобы было с чем разобраться. */
    fun loadBizDemo() {
        val d = store.current
        val acc = d.accounts.firstOrNull()?.id ?: return say("msg.noAccount")
        val biz = Demo.bizData(l, d.settings.mainCur, today(), acc)
        store.update { s ->
            s.copy(
                categories = withBizCats(s),
                products = s.products + biz.products.filterNot { p -> s.products.any { it.id == p.id } },
                customers = s.customers + biz.customers.filterNot { p -> s.customers.any { it.id == p.id } },
                orders = s.orders + biz.orders.filterNot { p -> s.orders.any { it.id == p.id } },
                txs = biz.txs.filterNot { t -> s.txs.any { it.id == t.id } } + s.txs,
            )
        }
        say("biz.demoLoaded")
    }

    fun selectBizPeriod(p: Period) {
        bizPeriod = p
        bizOffset = 0
    }

    fun shiftBizPeriod(delta: Int) {
        val n = bizOffset + delta
        if (n <= 0) bizOffset = n
    }

    // прайс

    fun openProduct(p: Product?) {
        productEdit = if (p == null) ProductEdit()
        else ProductEdit(p.id, p.name, numText(p.price), if (p.cost > 0) numText(p.cost) else "", p.unit)
    }

    fun saveProduct() {
        val e = productEdit ?: return
        val name = e.name.trim()
        if (name.isEmpty()) return say("biz.needName")
        store.update { s ->
            if (e.id == null) {
                s.copy(
                    products = s.products + Product("p${s.nextId}", name, num(e.price), num(e.cost), e.unit.trim()),
                    nextId = s.nextId + 1,
                )
            } else {
                s.copy(
                    products = s.products.map {
                        if (it.id == e.id) it.copy(name = name, price = num(e.price), cost = num(e.cost), unit = e.unit.trim()) else it
                    },
                )
            }
        }
        productEdit = null
        say(if (e.id == null) "biz.productAdded" else "biz.productSaved", name)
    }

    fun askDeleteProduct(id: String) {
        val p = store.current.products.firstOrNull { it.id == id } ?: return
        confirm = Confirm(l.t("biz.deleteProductTitle"), l.t("biz.deleteProductText", p.name), l.t("common.delete")) {
            store.update { s -> s.copy(products = s.products.filterNot { it.id == id }) }
            productEdit = null
            say("biz.productDeleted")
        }
    }

    // клиенты

    fun openCustomer(c: Customer?) {
        customerEdit = if (c == null) CustomerEdit() else CustomerEdit(c.id, c.name, c.contact, c.note)
    }

    fun saveCustomer() {
        val e = customerEdit ?: return
        val name = e.name.trim()
        if (name.isEmpty()) return say("biz.needName")
        store.update { s ->
            if (e.id == null) {
                s.copy(
                    customers = s.customers + Customer("cl${s.nextId}", name, e.contact.trim(), e.note.trim()),
                    nextId = s.nextId + 1,
                )
            } else {
                s.copy(
                    customers = s.customers.map {
                        if (it.id == e.id) it.copy(name = name, contact = e.contact.trim(), note = e.note.trim()) else it
                    },
                )
            }
        }
        customerEdit = null
        say(if (e.id == null) "biz.customerAdded" else "biz.customerSaved", name)
    }

    fun askDeleteCustomer(id: String) {
        val d = store.current
        val cst = d.customers.firstOrNull { it.id == id } ?: return
        val n = d.orders.count { it.customerId == id }
        confirm = Confirm(
            l.t("biz.deleteCustomerTitle"),
            if (n > 0) l.t("biz.deleteCustomerOrders", cst.name, l.n(n, "order")) else l.t("biz.deleteCustomerPlain", cst.name),
            l.t("common.delete"),
        ) {
            store.update { s ->
                s.copy(
                    customers = s.customers.filterNot { it.id == id },
                    orders = s.orders.map { if (it.customerId == id) it.copy(customerId = "") else it },
                )
            }
            customerEdit = null
            say("biz.customerDeleted")
        }
    }

    // заказы

    fun openOrder(o: Order?) {
        val d = store.current
        val c = calc
        orderDraft = if (o == null) {
            OrderDraft(
                no = c.nextOrderNo(),
                customerId = d.customers.firstOrNull()?.id.orEmpty(),
                date = c.todayDay,
                cur = d.settings.mainCur,
            )
        } else {
            OrderDraft(
                id = o.id,
                no = o.no,
                customerId = o.customerId,
                date = o.date,
                items = o.items.map { ItemDraft(it.productId, it.name, numText(it.qty), numText(it.price), if (it.cost > 0) numText(it.cost) else "") },
                discount = if (o.discount > 0) numText(o.discount) else "",
                extraCost = if (o.extraCost > 0) numText(o.extraCost) else "",
                cur = c.orderCur(o),
                status = o.status,
                note = o.note,
            )
        }
    }

    /** Повторить заказ клиента: те же позиции, сегодняшняя дата, новый номер. */
    fun repeatOrder(o: Order) {
        val c = calc
        orderDraft = OrderDraft(
            no = c.nextOrderNo(),
            customerId = o.customerId,
            date = c.todayDay,
            items = o.items.map { ItemDraft(it.productId, it.name, numText(it.qty), numText(it.price), if (it.cost > 0) numText(it.cost) else "") },
            cur = c.orderCur(o),
            note = o.note,
        )
    }

    fun addItem(p: Product?) {
        val e = orderDraft ?: return
        val c = calc
        val cur = e.cur.ifBlank { c.main }
        val item = if (p == null) {
            ItemDraft(name = "", qty = "1", price = "", cost = "")
        } else {
            // цены прайса лежат в основной валюте: в заказ другой валюты переводим по курсу
            ItemDraft(p.id, p.name, "1", numText(c.conv(p.price, c.main, cur)), if (p.cost > 0) numText(c.conv(p.cost, c.main, cur)) else "")
        }
        orderDraft = e.copy(items = e.items + item, picking = false)
    }

    fun setItem(index: Int, item: ItemDraft) {
        val e = orderDraft ?: return
        orderDraft = e.copy(items = e.items.mapIndexed { i, old -> if (i == index) item else old })
    }

    fun removeItem(index: Int) {
        val e = orderDraft ?: return
        orderDraft = e.copy(items = e.items.filterIndexed { i, _ -> i != index })
    }

    /** Сумма и себестоимость черновика — для подписи в редакторе. */
    fun draftTotal(e: OrderDraft): Double =
        (e.items.sumOf { num(it.qty) * num(it.price) } - num(e.discount)).coerceAtLeast(0.0)

    fun draftCost(e: OrderDraft): Double = e.items.sumOf { num(it.qty) * num(it.cost) } + num(e.extraCost)

    fun saveOrder() {
        val e = orderDraft ?: return
        val items = e.items.mapNotNull { i ->
            val name = i.name.trim().ifBlank { calc.product(i.productId)?.name.orEmpty() }
            val qty = num(i.qty)
            if (name.isBlank() || qty <= 0) null else OrderItem(i.productId, name, qty, num(i.price), num(i.cost))
        }
        if (items.isEmpty()) return say("biz.needItem")
        val newName = e.newCustomer.trim()
        var savedId: Long? = null
        store.update { s ->
            var nextId = s.nextId
            var customers = s.customers
            var cid = e.customerId
            if (newName.isNotBlank()) {
                cid = "cl$nextId"
                customers = customers + Customer(cid, newName)
                nextId++
            }
            val old = e.id?.let { id -> s.orders.firstOrNull { it.id == id } }
            val id = e.id ?: nextId.also { nextId++ }
            savedId = id
            val order = Order(
                id = id,
                no = e.no.trim().ifBlank { Calc(s, l = l).nextOrderNo() },
                customerId = cid,
                date = e.date,
                items = items,
                discount = num(e.discount),
                extraCost = num(e.extraCost),
                cur = e.cur.ifBlank { s.settings.mainCur },
                // оплату ставит только приём оплаты, редактор её не выдаёт
                status = if (old?.status == OrderStatus.PAID) OrderStatus.PAID else e.status,
                incomeTxId = old?.incomeTxId,
                costTxId = old?.costTxId,
                note = e.note.trim(),
            )
            s.copy(
                customers = customers,
                orders = if (e.id != null) s.orders.map { if (it.id == e.id) order else it } else s.orders + order,
                nextId = nextId,
            )
        }
        orderDraft = null
        say(if (e.id == null) "biz.orderAdded" else "biz.orderSaved", e.no)
    }

    fun setOrderStatus(id: Long, status: String) {
        if (status == OrderStatus.PAID) return openPay(id)
        store.update { s -> s.copy(orders = s.orders.map { if (it.id == id) it.copy(status = status) else it }) }
    }

    fun askDeleteOrder(id: Long) {
        val o = store.current.orders.firstOrNull { it.id == id } ?: return
        val paid = o.incomeTxId != null
        confirm = Confirm(
            l.t("biz.deleteOrderTitle"),
            if (paid) l.t("biz.deleteOrderPaid", o.no) else l.t("biz.deleteOrderText", o.no),
            l.t("common.delete"),
        ) {
            store.update { s ->
                val drop = listOfNotNull(o.incomeTxId, o.costTxId).toSet()
                s.copy(orders = s.orders.filterNot { it.id == id }, txs = s.txs.filterNot { it.id in drop })
            }
            orderDraft = null
            say("biz.orderDeleted")
        }
    }

    // оплата

    fun openPay(orderId: Long) {
        val d = store.current
        val c = calc
        val o = d.orders.firstOrNull { it.id == orderId } ?: return
        if (c.orderTotal(o) <= 0) return say("biz.needItem")
        val cur = c.orderCur(o)
        val acc = d.accounts.firstOrNull { it.cur == cur }?.id ?: d.accounts.firstOrNull()?.id ?: return say("msg.noAccount")
        paySheet = PaySheet(orderId, acc)
    }

    /**
     * Оплата заказа: обычная операция дохода в категории «Продажи» — выручка сразу
     * попадает в баланс и во все прежние отчёты. Дата операции — дата заказа,
     * чтобы «Дело» и «Отчёты» показывали её в одном и том же периоде.
     */
    fun confirmPay() {
        val ps = paySheet ?: return
        val c = calc
        val o = store.current.orders.firstOrNull { it.id == ps.orderId } ?: return
        val acc = c.acc(ps.acc) ?: return say("msg.noAccount")
        val cur = c.orderCur(o)
        val total = c.conv(c.orderTotal(o), cur, acc.cur)
        if (total <= 0) return say("biz.needItem")
        val cost = c.conv(c.orderCost(o), cur, acc.cur)
        val who = c.customerName(o.customerId)
        store.update { s ->
            var nextId = s.nextId
            val add = mutableListOf<Tx>()
            val incomeId = nextId++
            add += Tx(incomeId, o.date, l.t("biz.txTitle", o.no), CAT_SALE, acc.id, total, note = who)
            var costId: Long? = null
            if (ps.writeCost && cost > 0) {
                val cid = nextId++
                costId = cid
                add += Tx(cid, o.date, l.t("biz.txCostTitle", o.no), CAT_COST, acc.id, -cost, note = who)
            }
            val drop = listOfNotNull(o.incomeTxId, o.costTxId).toSet()
            s.copy(
                categories = withBizCats(s),
                txs = add + s.txs.filterNot { it.id in drop },
                orders = s.orders.map {
                    if (it.id == o.id) it.copy(status = OrderStatus.PAID, incomeTxId = incomeId, costTxId = costId) else it
                },
                nextId = nextId,
            )
        }
        paySheet = null
        orderDraft = null
        say("biz.paid", c.fmt(total, acc.cur), acc.name)
    }

    /** Отменить оплату: убираем созданные операции, заказ возвращается в «выполнен». */
    fun unpay(orderId: Long) {
        val o = store.current.orders.firstOrNull { it.id == orderId } ?: return
        confirm = Confirm(l.t("biz.unpayTitle"), l.t("biz.unpayText", o.no), l.t("biz.unpayAction")) {
            store.update { s ->
                val drop = listOfNotNull(o.incomeTxId, o.costTxId).toSet()
                s.copy(
                    txs = s.txs.filterNot { it.id in drop },
                    orders = s.orders.map {
                        if (it.id == orderId) it.copy(status = OrderStatus.DONE, incomeTxId = null, costTxId = null) else it
                    },
                )
            }
            say("biz.unpaid")
        }
    }

    fun exportOrders() {
        val r = calc.range(bizPeriod, bizOffset)
        val count = store.current.orders.count { o -> o.date >= r.from && o.date <= r.to }
        saveFile("kopeechka-orders-${today().isoString()}.csv", Csv.exportOrders(store.current, l, r.from, r.to)) { name ->
            say("csv.exported", l.n(count, "order"), name)
        }
    }

    /** «2026-09-15_10-45-03» в имени файла копии. */
    private fun backupFileStamp(): String {
        val now = kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        fun two(v: Int) = v.toString().padStart(2, '0')
        return "${now.year}-${two(now.monthNumber)}-${two(now.dayOfMonth)}_" +
            "${two(now.hour)}-${two(now.minute)}-${two(now.second)}"
    }

    // ——— ИИ-советник ———

    private fun keyName(p: String) = "key_$p"

    fun openAdvisor(withReport: Boolean = false) {
        advisorOpen = true
        apiKeyInput = secure.get(keyName(store.current.settings.aiProvider))
        if (withReport) {
            settings { it.copy(aiSets = it.aiSets + "report") }
            if (question.isBlank()) question = l.t("ai.reportQuestion")
        }
    }

    fun setProvider(p: String) {
        settings { it.copy(aiProvider = p) }
        apiKeyInput = secure.get(keyName(p))
        answer = ""
    }

    fun setApiKey(v: String) {
        apiKeyInput = v
        secure.put(keyName(store.current.settings.aiProvider), v)
    }

    fun setModel(v: String) = settings { it.copy(aiModels = it.aiModels + (it.aiProvider to v.trim())) }
    fun setEndpoint(v: String) = settings { it.copy(customEndpoint = v.trim()) }
    fun toggleSet(k: String) = settings { it.copy(aiSets = if (k in it.aiSets) it.aiSets - k else it.aiSets + k) }

    fun modelFor(s: Settings) = s.aiModels[s.aiProvider]?.takeIf { it.isNotBlank() } ?: Ai.provider(s.aiProvider).defaultModel

    fun hasKey(provider: String) = secure.get(keyName(provider)).isNotBlank()

    fun buildPayload(): String {
        val d = store.current
        val c = Calc(d)
        val s = d.settings
        val parts = mutableListOf<String>()
        val r = c.range(period, periodOffset)
        if ("accounts" in s.aiSets) {
            parts += l.t("ai.payload.accounts", c.main) + "\n" + d.accounts.joinToString("\n") { a ->
                val b = c.balances[a.id] ?: 0.0
                "- ${a.name}: ${c.fmt(b, a.cur)}" + (if (a.cur == c.main) "" else " ≈ ${c.fmtMain(c.toMain(b, a.cur))}")
            }
        }
        if ("ops" in s.aiSets) {
            val within = d.txs.filter { it.date in r.from..r.to }.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.id })
            parts += l.t("ai.payload.ops", r.note, r.title, within.size) + "\n" + within.take(80).joinToString("\n") { t ->
                "- ${c.dayLabel(t.date)} · ${t.title} · ${c.cat(t.cat).name} · ${Currencies.fmtSigned(t.amount, c.accCur(t.acc))}"
            }
        }
        if ("budgets" in s.aiSets) {
            parts += l.t("ai.payload.budgets", l.n(c.daysLeft, "day")) + "\n" +
                c.budgets.joinToString("\n") { "- ${it.cat.name}: ${c.fmtMain(it.spent)} / ${c.fmtMain(it.limit)}" }
        }
        if ("goals" in s.aiSets && d.goals.isNotEmpty()) {
            parts += l.t("ai.payload.goals") + "\n" + d.goals.joinToString("\n") { "- ${it.name}: ${c.fmt(it.saved, it.cur)} / ${c.fmt(it.target, it.cur)}" }
        }
        if ("report" in s.aiSets) {
            parts += l.t("ai.payload.report", r.title, l.t(cut.titleKey).lowercase()) + "\n" +
                c.breakdown(r, cut).joinToString("\n") { "- ${it.name}: ${c.fmtMain(it.value)} (${it.pct})" }
            c.compare(period, periodOffset)?.let { (prev, delta) ->
                parts += l.t("ai.payload.compare", c.fmtMain(prev), delta.roundToInt())
            }
        }
        return parts.joinToString("\n\n")
    }

    fun localAdvice(): String {
        val c = calc
        val r = c.range(period, periodOffset)
        val sp = c.spentBy(c.txIn(r)).entries.sortedByDescending { it.value }
        val total = sp.sumOf { it.value }
        val top = sp.getOrNull(0)
        val second = sp.getOrNull(1)
        val lines = mutableListOf(l.t("advice.intro", r.note, r.title, c.fmtMain(total)))
        if (top != null && total > 0) {
            lines += l.t("advice.top", c.cat(top.key).name, c.fmtMain(top.value), (top.value / total * 100).roundToInt()) +
                (second?.let { l.t("advice.second", c.cat(it.key).name, c.fmtMain(it.value)) } ?: "")
        }
        val over = c.budgets.filter { it.over }
        lines += if (over.isNotEmpty()) {
            l.t("advice.over", over.joinToString { "${it.cat.name} (+${c.fmtMain(it.spent - it.limit)})" })
        } else {
            l.t("advice.ok")
        }
        lines += l.t("advice.cut", top?.let { c.cat(it.key).name } ?: l.t("advice.bigItem"), c.fmtMain((top?.value ?: 0.0) * 0.15))
        lines += if (c.d.accounts.map { it.cur }.distinct().size > 1) l.t("advice.multiCur") else l.t("advice.save")
        return lines.joinToString("\n")
    }

    private fun stripMd(t: String) = t
        .replace(Regex("```[\\s\\S]*?```"), "")
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("(^|[^*])\\*([^*\\n]+)\\*"), "$1$2")
        .replace(Regex("(?m)^\\s*[-•]\\s+"), "— ")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    fun ask() {
        if (asking) return
        if (question.isBlank()) return say("msg.askQuestion")
        val s = store.current.settings
        if (s.aiSets.isEmpty()) return say("msg.pickData")
        val lang = l
        val payload = buildPayload()
        val prompt = lang.t("ai.systemPrompt", payload, question)
        val p = Ai.provider(s.aiProvider)
        val model = modelFor(s)
        val key = secure.get(keyName(p.key))
        asking = true
        answer = ""
        answerMeta = "≈ ${prompt.length / 3} tokens"
        viewModelScope.launch {
            try {
                if (p.needsKey && key.isBlank()) {
                    delay(500)
                    answer = localAdvice()
                    answerFrom = lang.t("ai.offline")
                } else {
                    answer = stripMd(Ai.ask(p.key, model, key, s.customEndpoint, prompt))
                    answerFrom = "${p.name(lang)} · $model"
                }
            } catch (e: AiError) {
                answer = e.text(lang) + lang.t("ai.offlineAfterError") + localAdvice()
                answerFrom = lang.t("ai.errorFrom")
            } catch (e: Exception) {
                answer = (e.message ?: "") + lang.t("ai.offlineAfterError") + localAdvice()
                answerFrom = lang.t("ai.errorFrom")
            } finally {
                asking = false
            }
        }
    }

    // ——— Google Диск ———

    private fun driveAction(action: suspend (String) -> Unit) {
        if (driveBusy) return
        driveBusy = true
        viewModelScope.launch {
            try {
                val token = platform.driveToken()
                if (token == null) {
                    driveBusy = false
                    return@launch
                }
                runDrive(token, action)
            } catch (e: DriveAuthError) {
                driveBusy = false
                say("msg.driveAuthError", e.code)
            } catch (e: DriveError) {
                driveBusy = false
                say("msg.driveError", e.text(l))
            } catch (e: Exception) {
                driveBusy = false
                say("msg.driveError", e.message ?: "")
            }
        }
    }

    private suspend fun runDrive(token: String, action: suspend (String) -> Unit) {
        try {
            action(token)
            if (!store.current.settings.driveLinked) {
                settings { it.copy(driveLinked = true) }
                platform.syncAutoBackup(store.current.settings.autoBackup)
            }
        } catch (e: DriveError) {
            say("msg.driveError", e.text(l))
        } catch (e: Exception) {
            say("msg.driveError", e.message ?: "")
        } finally {
            driveBusy = false
        }
    }

    fun backupNow() = driveAction { t ->
        val name = DriveApi.upload(t, store.exportJson(), "kopeechka-" + backupFileStamp() + ".json")
        DriveApi.prune(t, 10)
        settings { it.copy(lastBackupAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()) }
        driveList = DriveApi.list(t)
        say("msg.backupSaved", name)
    }

    fun refreshBackups() = driveAction { t -> driveList = DriveApi.list(t) }

    fun askRestore(b: RemoteBackup) {
        confirm = Confirm(
            l.t("msg.restoreTitle"),
            l.t("msg.restoreText", formatBackupTime(backupMillis(b), l)),
            l.t("common.restore"),
        ) {
            driveAction { t ->
                val restored = store.parseBackup(DriveApi.download(t, b.id))
                platform.saveBeforeRestore(store.exportJson())
                val keep = store.current.settings
                store.replace(
                    restored.copy(
                        settings = restored.settings.copy(
                            onboarded = true,
                            driveLinked = true,
                            autoBackup = keep.autoBackup,
                            lastBackupAt = keep.lastBackupAt,
                        ),
                    ),
                )
                say("msg.restored")
            }
        }
    }

    fun unlinkDrive() {
        settings { it.copy(driveLinked = false, autoBackup = false) }
        platform.syncAutoBackup(false)
        driveList = emptyList()
        say("msg.driveUnlinked")
    }
}
