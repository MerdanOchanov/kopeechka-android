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
import app.kopeechka.finance.data.FiscalQr
import app.kopeechka.finance.data.INBOX_PHOTO
import app.kopeechka.finance.data.INBOX_QR
import app.kopeechka.finance.data.INBOX_PUSH
import app.kopeechka.finance.data.INBOX_SMS
import app.kopeechka.finance.data.InboxItem
import app.kopeechka.finance.data.Receipt
import app.kopeechka.finance.data.ReceiptScan
import app.kopeechka.finance.data.epochDate
import app.kopeechka.finance.data.decimalString
import app.kopeechka.finance.data.Goal
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.data.Order
import app.kopeechka.finance.data.OrderItem
import app.kopeechka.finance.data.OrderStatus
import app.kopeechka.finance.data.Palette
import app.kopeechka.finance.data.Period
import app.kopeechka.finance.data.Product
import app.kopeechka.finance.data.Every
import app.kopeechka.finance.data.RateMode
import app.kopeechka.finance.data.Recurring
import app.kopeechka.finance.data.Recurrings
import app.kopeechka.finance.data.rebaseRates
import app.kopeechka.finance.data.RequestStatus
import app.kopeechka.finance.data.Settings
import app.kopeechka.finance.data.Statement
import app.kopeechka.finance.data.StatementLayout
import app.kopeechka.finance.data.StatementRow
import app.kopeechka.finance.data.SpendRequest
import app.kopeechka.finance.data.needsApproval
import app.kopeechka.finance.data.Sync
import app.kopeechka.finance.data.SyncKind
import app.kopeechka.finance.data.SyncLink
import app.kopeechka.finance.data.SyncMember
import app.kopeechka.finance.data.SyncSnapshot
import app.kopeechka.finance.data.SyncSpace
import app.kopeechka.finance.data.forSync
import app.kopeechka.finance.data.SmsParse
import app.kopeechka.finance.data.SmsPresets
import app.kopeechka.finance.data.SmsSource
import app.kopeechka.finance.data.SmsWords
import app.kopeechka.finance.data.Tx
import app.kopeechka.finance.data.customerName
import app.kopeechka.finance.data.nextOrderNo
import app.kopeechka.finance.data.orderCost
import app.kopeechka.finance.data.orderCur
import app.kopeechka.finance.data.orderTotal
import app.kopeechka.finance.data.product
import app.kopeechka.finance.net.Ai
import app.kopeechka.finance.net.BankRates
import app.kopeechka.finance.net.AiImage
import app.kopeechka.finance.net.AiError
import app.kopeechka.finance.net.DriveApi
import app.kopeechka.finance.net.DriveSync
import app.kopeechka.finance.net.LanSync
import app.kopeechka.finance.net.syncJson
import app.kopeechka.finance.net.SyncError
import app.kopeechka.finance.net.SyncTransport
import app.kopeechka.finance.net.WebDavSync
import app.kopeechka.finance.net.UpdateInfo
import app.kopeechka.finance.net.Updates
import app.kopeechka.finance.net.DriveError
import app.kopeechka.finance.net.RemoteBackup
import app.kopeechka.finance.net.formatBackupTime
import kotlinx.datetime.toLocalDateTime
import app.kopeechka.finance.net.backupMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import app.kopeechka.finance.data.today
import app.kopeechka.finance.data.toEpochDay
import app.kopeechka.finance.data.isoString
import kotlin.math.abs
import kotlin.random.Random
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class Tab { HOME, OPS, BUDGET, REPORT, SETTINGS }
enum class Page { ACCOUNTS, CATEGORIES, GOALS, BACKUP, CURRENCIES, BUSINESS, PRODUCTS, CUSTOMERS, DEBTS, SMS, SYNC, RECURRING }
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
    val shared: Boolean = false,
    val approveFrom: String = "",
)

data class CatEdit(
    val id: String? = null,
    val name: String = "",
    val code: String = "",
    val limit: String = "",
    val income: Boolean = false,
    val color: String = "",
)

/** Выписка банка на разборе: строки файла, где что лежит, в какой счёт писать. */
data class StatementDraft(
    val name: String,
    val rows: List<List<String>>,
    val layout: StatementLayout,
    val accId: String,
)

/** Регулярный платёж в работе: id пустой — новый. Суммы — текстом, как в поле ввода. */
data class RecEdit(
    val id: String? = null,
    val title: String = "",
    val amount: String = "",
    val income: Boolean = false,
    val accId: String = "",
    val cat: String = "",
    val every: String = Every.MONTH,
    val start: Long = 0,
    val total: String = "",
    val auto: Boolean = false,
    val remindDays: Int = 1,
)

/** Настройки WebDAV в работе. Пароль сюда не попадает — он в хранилище ключей. */
data class WebDavEdit(val url: String = "", val login: String = "", val password: String = "")

/** Правило чтения СМС в работе: id пустой — правило новое. */
data class SmsEdit(
    val id: String? = null,
    val name: String = "",
    val sender: String = "",
    val accId: String = "",
    val expenseWords: String = SmsWords.EXPENSE,
    val incomeWords: String = SmsWords.INCOME,
    val ignoreWords: String = SmsWords.IGNORE,
    val auto: Boolean = false,
    val cardMask: String = "",
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

    /** Идёт распознавание чека. */
    var scanning by mutableStateOf(false)
        private set

    /** Открыт список «на проверку». */
    var inboxOpen by mutableStateOf(false)

    /** Открыт выбор «камера или галерея». */
    var scanSheet by mutableStateOf(false)

    /** Правило чтения СМС, которое сейчас правят. */
    var smsEdit by mutableStateOf<SmsEdit?>(null)

    /** Текст для проверки правила: видно, что приложение из него достаёт. */
    var smsTest by mutableStateOf("")

    /** Идёт разбор истории сообщений. */
    var smsBusy by mutableStateOf(false)
        private set

    /** Идёт обмен с другим участником. */
    var syncBusy by mutableStateOf(false)
        private set

    /** Код приглашения, который вводят при присоединении. */
    var joinCode by mutableStateOf("")

    /** Настройки WebDAV открыты. */
    var webdavEdit by mutableStateOf<WebDavEdit?>(null)

    /** Открыт список заявок. */
    var requestsOpen by mutableStateOf(false)

    /** Регулярный платёж, который сейчас правят. */
    var recEdit by mutableStateOf<RecEdit?>(null)

    /** Выписка банка, открытая на разбор. */
    var statement by mutableStateOf<StatementDraft?>(null)

    /** Вышла версия новее установленной — показываем полоску на главной. */
    var update by mutableStateOf<UpdateInfo?>(null)
        private set

    var checkingUpdates by mutableStateOf(false)
        private set

    /** Адрес, по которому этот телефон сейчас принимает обмен; null — не принимает. */
    var lanHostAddress by mutableStateOf<String?>(null)
        private set

    /** Код, который гость вводит у себя. */
    var lanHostCode by mutableStateOf("")
        private set

    /** Что ввёл гость: адрес второго телефона и код с его экрана. */
    var lanAddress by mutableStateOf("")
    var lanCode by mutableStateOf("")

    private var lanFails = 0

    /** Черновик, который сейчас правят перед записью. */
    var inboxEdit by mutableStateOf<InboxItem?>(null)
    var toast by mutableStateOf<String?>(null)
    var onbStep by mutableStateOf(0)

    /** Открытый календарь: "tx" — дата операции, "order" — дата заказа, "due" — срок возврата. */
    var datePick by mutableStateOf<String?>(null)

    /** Дата, которую сейчас показывает календарь. */
    fun pickedDate(): Long = when (datePick) {
        "order" -> orderDraft?.date
        "due" -> draft?.due ?: draft?.date?.plus(30)
        "rec" -> recEdit?.start
        else -> draft?.date
    } ?: today().toEpochDay()

    fun pickDate(day: Long) {
        when (datePick) {
            "order" -> orderDraft = orderDraft?.copy(date = day)
            "due" -> draft = draft?.copy(due = day)
            "tx" -> draft = draft?.copy(date = day)
            "rec" -> recEdit = recEdit?.copy(start = day)
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
        settleRequests()
        if (platform.updatesFromGitHub) checkUpdates(manual = false)
        runRecurring()
        platform.syncRecurring(store.current.recurring.isNotEmpty())
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
            inboxEdit != null -> inboxEdit = null
            statement != null -> statement = null
            recEdit != null && datePick == null -> recEdit = null
            requestsOpen -> requestsOpen = false
            webdavEdit != null -> webdavEdit = null
            smsEdit != null -> smsEdit = null
            scanSheet -> scanSheet = false
            inboxOpen -> inboxOpen = false
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
                // с общего счёта от порога — не операция, а просьба ко второму участнику
                if (d.editId == null && store.current.needsApproval(src.id, v)) {
                    return askApproval(d, v, src)
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
            val div = Calc(s).bankRate(code).takeIf { it > 0 } ?: 1.0
            val (rates, market) = rebaseRates(s.settings, code)
            s.copy(
                categories = s.categories.map { it.copy(limitBase = it.limitBase / div) },
                // цены прайса тоже хранятся в основной валюте; суммы заказов — в своих
                products = s.products.map { it.copy(price = it.price / div, cost = it.cost / div) },
                settings = s.settings.copy(
                    mainCur = code,
                    rates = rates,
                    marketRates = market,
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

    /** Рыночный курс. Пустое поле — рынок совпадает с банком. */
    fun setMarketRate(code: String, text: String) {
        val clean = text.replace(',', '.').replace(" ", "")
        if (clean.isEmpty()) return settings { it.copy(marketRates = it.marketRates - code) }
        val v = clean.toDoubleOrNull() ?: return
        if (v <= 0) return
        settings { it.copy(marketRates = it.marketRates + (code to v)) }
    }

    fun setRateMode(mode: String) = settings { it.copy(rateMode = mode) }

    /** Идёт загрузка официальных курсов. */
    var ratesBusy by mutableStateOf(false)
        private set

    /**
     * Банковские курсы из центробанка. Рыночные не трогаем: их не публикует
     * никто, и там, где они расходятся с официальными, человек знает их сам.
     */
    fun updateBankRates(source: BankRates.Source) {
        if (ratesBusy) return
        ratesBusy = true
        viewModelScope.launch {
            try {
                val raw = BankRates.fetch(source)
                val s = store.current.settings
                val fresh = BankRates.toMain(raw, s.mainCur, calc.currencies)
                    ?: return@launch say("rates.noMain", s.mainCur, l.t("rates.src." + source.name))
                if (fresh.isEmpty()) return@launch say("rates.nothing")
                val now = Clock.System.now().toEpochMilliseconds()
                settings { it.copy(rates = it.rates + fresh, ratesAt = now, ratesSource = source.name) }
                say("rates.updated", fresh.size)
            } catch (e: SyncError) {
                say(e.key)
            } catch (e: Exception) {
                say("rates.err.offline")
            } finally {
                ratesBusy = false
            }
        }
    }

    /**
     * Курс пары прямо в операции: «1 from = x to».
     * Меняется курс небазовой валюты, доллар остаётся базой.
     */
    fun setPairRate(from: String, to: String, text: String) {
        val x = text.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: return
        if (x <= 0 || from == to) return
        val c = calc
        val market = c.byMarket
        fun put(code: String, v: Double) = settings {
            if (market) it.copy(marketRates = it.marketRates + (code to v)) else it.copy(rates = it.rates + (code to v))
        }
        when {
            from != c.main -> put(from, x * c.rate(to))
            to != c.main -> put(to, c.rate(from) / x)
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
                marketRates = s.marketRates - code,
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
        else AccEdit(
            a.id, a.name, a.type, a.mask, a.cur, calc.balance(a).roundToLong().toString(), a.inTotal,
            a.shared, if (a.approveFrom > 0) numText(a.approveFrom) else "",
        )
    }

    /** Тип «Золото» сам переключает счёт на граммы; уход с него — обратно на основную валюту. */
    fun pickAccType(key: String) {
        val e = accEdit ?: return
        val gold = key == "acc.type.gold"
        val cur = when {
            e.id != null -> e.cur
            gold -> Currencies.GOLD
            e.cur == Currencies.GOLD -> store.current.settings.mainCur
            else -> e.cur
        }
        accEdit = e.copy(type = l.t(key), cur = cur)
    }

    fun saveAcc() {
        val e = accEdit ?: return
        val name = e.name.trim()
        if (name.isEmpty()) return say("msg.enterAccName")
        val bal = e.balance.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: 0.0
        if (e.id == null && e.cur !in store.current.settings.currencyCodes) {
            // золото включается само: без курса граммы не попадут в общий итог
            settings { s ->
                s.copy(
                    currencyCodes = (s.currencyCodes + e.cur).distinct(),
                    rates = if (s.rates.containsKey(e.cur)) s.rates else s.rates + (e.cur to Currencies.hintRate(e.cur, s.mainCur)),
                )
            }
        }
        store.update { s ->
            if (e.id == null) {
                s.copy(
                    accounts = s.accounts + Account(
                        "a${s.nextId}", name, e.type, e.mask.trim(), e.cur, bal, e.inTotal,
                        shared = e.shared, approveFrom = num(e.approveFrom),
                    ),
                    nextId = s.nextId + 1,
                )
            } else {
                val c = Calc(s)
                s.copy(accounts = s.accounts.map {
                    if (it.id == e.id) {
                        it.copy(
                            name = name, type = e.type, mask = e.mask.trim(), initial = bal - (c.balance(it) - it.initial),
                            inTotal = e.inTotal, shared = e.shared, approveFrom = num(e.approveFrom),
                        )
                    } else {
                        it
                    }
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
    private fun saveFile(name: String, text: String, mime: String = MIME_CSV, done: (String) -> Unit) {
        viewModelScope.launch {
            val saved = try {
                platform.saveTextFile(name, text, mime)
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
    /** Число из поля ввода: пробелы и запятая допускаются. */
    fun numOf(text: String): Double = num(text)

    private fun num(text: String): Double {
        val t = text.trim().replace(" ", "").replace(" ", "").replace(',', '.')
        val i = t.lastIndexOf('.')
        val clean = if (i < 0) t else t.substring(0, i).replace(".", "") + "." + t.substring(i + 1)
        return clean.toDoubleOrNull() ?: 0.0
    }

    /** Число обратно в поле: целое — без хвоста, дробное — с разделителем языка. */
    /** Число в текст для поля ввода. */
    fun numText(v: Double): String {
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
        val now = kotlin.time.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        fun two(v: Int) = v.toString().padStart(2, '0')
        return "${now.year}-${two(now.monthNumber)}-${two(now.dayOfMonth)}_" +
            "${two(now.hour)}-${two(now.minute)}-${two(now.second)}"
    }

    // ——— ИИ-советник ———

    private fun keyName(p: String) = "key_$p"

    /** Пароль WebDAV живёт там же, где ключи ИИ: в Keystore или Keychain. */
    private val WEBDAV_KEY = "webdav_password"

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

    // ——— чеки и черновики ———

    /**
     * Чек читает та же модель, что и советует по бюджету, поэтому кнопка
     * появляется только при готовом ключе: без него распознавать нечем.
     */
    fun canScan(): Boolean {
        val p = Ai.provider(store.current.settings.aiProvider)
        return platform.canPickImage && (!p.needsKey || hasKey(p.key))
    }

    /** QR работает без ключа ИИ и без интернета — нужна только камера. */
    val canScanQr get() = platform.canPickImage

    /**
     * Чек по QR-коду: дата, сумма и тип операции прямо из кода. Счёт — первый
     * в основной валюте: российский чек всегда в рублях, казахстанский в тенге.
     */
    fun scanQr(source: ImageSource) {
        viewModelScope.launch {
            val text = runCatching { platform.scanQr(source) }.getOrNull() ?: return@launch say("qr.notFound")
            val r = FiscalQr.parse(text) ?: return@launch say("qr.notReceipt")
            val c = calc
            val d = store.current
            val acc = d.accounts.firstOrNull { it.cur == c.main }?.id ?: d.accounts.firstOrNull()?.id.orEmpty()
            val cat = d.categories.firstOrNull { it.income == r.income }?.id.orEmpty()
            store.update { s ->
                s.copy(
                    inbox = listOf(
                        InboxItem(
                            id = s.nextId,
                            source = INBOX_QR,
                            at = Clock.System.now().toEpochMilliseconds(),
                            date = r.date ?: c.todayDay,
                            title = l.t("inbox.receipt"),
                            amount = r.amount,
                            cur = c.accCur(acc),
                            income = r.income,
                            accId = acc,
                            cat = cat,
                            raw = text.take(300),
                        ),
                    ) + s.inbox,
                    nextId = s.nextId + 1,
                )
            }
            inboxOpen = true
            inboxEdit = store.current.inbox.firstOrNull()
            if (r.amount <= 0) say("qr.noSum")
        }
    }

    fun scanReceipt(source: ImageSource) {
        if (scanning) return
        val s = store.current.settings
        val p = Ai.provider(s.aiProvider)
        val key = secure.get(keyName(p.key))
        if (p.needsKey && key.isBlank()) return say("msg.receiptNoKey", p.name(l))
        viewModelScope.launch {
            val img = runCatching { platform.pickImage(source) }.getOrNull() ?: return@launch
            scanning = true
            try {
                val c = calc
                val cats = store.current.categories.filter { !it.income }.joinToString(", ") { it.name }
                val prompt = l.t("ai.receiptPrompt", cats, c.main, epochDate(c.todayDay).isoString())
                val answer = Ai.ask(p.key, modelFor(s), key, s.customEndpoint, prompt, listOf(AiImage(img.base64, img.mime)))
                val scan = Receipt.parse(answer)
                if (scan == null) say("msg.receiptUnreadable") else addFromReceipt(scan)
            } catch (e: AiError) {
                flash(e.text(l))
            } catch (e: Exception) {
                flash(e.message ?: l.t("msg.receiptUnreadable"))
            } finally {
                scanning = false
            }
        }
    }

    private fun addFromReceipt(r: ReceiptScan) {
        val c = calc
        val cat = store.current.categories.firstOrNull { !it.income && it.name.equals(r.cat, ignoreCase = true) }?.id
            ?: store.current.categories.firstOrNull { !it.income }?.id.orEmpty()
        // счёт угадываем по валюте чека: в поездке это почти всегда правильный ответ
        val acc = store.current.accounts.firstOrNull { it.cur == r.cur }?.id
            ?: store.current.accounts.firstOrNull()?.id.orEmpty()
        store.update { s ->
            s.copy(
                inbox = listOf(
                    InboxItem(
                        id = s.nextId,
                        source = INBOX_PHOTO,
                        at = Clock.System.now().toEpochMilliseconds(),
                        date = r.date ?: c.todayDay,
                        title = r.merchant.ifBlank { l.t("inbox.receipt") },
                        amount = r.total,
                        cur = r.cur.ifBlank { c.accCur(acc) },
                        accId = acc,
                        cat = cat,
                        raw = r.items.joinToString("\n"),
                    ),
                ) + s.inbox,
                nextId = s.nextId + 1,
            )
        }
        inboxOpen = true
        inboxEdit = store.current.inbox.firstOrNull()
    }

    fun editInbox(f: (InboxItem) -> InboxItem) {
        inboxEdit = inboxEdit?.let(f)
    }

    /** Подтверждённый черновик превращается в обычную операцию. */
    fun acceptInbox() {
        val item = inboxEdit ?: return
        val c = calc
        if (item.amount <= 0) return say("msg.amountNeeded")
        val a = c.acc(item.accId) ?: return say("msg.pickAccount")
        val amount = c.conv(item.amount, item.cur.ifBlank { a.cur }, a.cur)
        if (!c.s.allowNegative && !item.income && c.balance(a) < amount) return say("msg.notEnough", a.name)
        store.update { s ->
            s.copy(
                txs = listOf(
                    Tx(
                        id = s.nextId,
                        date = item.date,
                        title = item.title.ifBlank { l.t("inbox.receipt") },
                        cat = item.cat,
                        acc = a.id,
                        amount = if (item.income) amount else -amount,
                        note = item.note,
                    ),
                ) + s.txs,
                nextId = s.nextId + 1,
                inbox = s.inbox.filterNot { it.id == item.id },
                // в следующий раз тот же магазин попадёт в ту же категорию сам
                merchantCats = if ((item.source == INBOX_SMS || item.source == INBOX_PUSH) && item.title.isNotBlank()) {
                    s.merchantCats + (item.title.lowercase() to item.cat)
                } else {
                    s.merchantCats
                },
            )
        }
        inboxEdit = null
        if (store.current.inbox.isEmpty()) inboxOpen = false
        say("msg.inboxAccepted", c.fmt(amount, a.cur))
    }

    fun dropInbox(id: Long) {
        store.update { s -> s.copy(inbox = s.inbox.filterNot { it.id == id }) }
        inboxEdit = null
        if (store.current.inbox.isEmpty()) inboxOpen = false
    }

    // ——— банковские СМС ———

    val canReadSms get() = platform.canReadSms

    val canReadPush get() = platform.canReadPush

    /** Разрешение выдают в системных настройках — спрашиваем при каждом показе экрана. */
    fun pushAccess() = platform.pushAccessGranted()

    /**
     * Включение ведёт в системные настройки доступа к уведомлениям: иначе
     * переключатель был бы включён, а читать было бы нечего.
     */
    fun setPushModule(on: Boolean) {
        settings { it.copy(bankPush = on) }
        if (on && !platform.pushAccessGranted()) {
            say("push.grant")
            platform.openPushAccessSettings()
        }
    }

    fun openPushSettings() = platform.openPushAccessSettings()

    /**
     * Включение спрашивает разрешение и сразу разбирает историю: иначе человек
     * настроит правило и будет ждать следующего списания, чтобы понять, работает ли.
     */
    fun setSmsModule(on: Boolean) {
        if (!on) {
            settings { it.copy(sms = false) }
            return
        }
        viewModelScope.launch {
            val granted = runCatching { platform.requestSmsAccess() }.getOrDefault(false)
            if (!granted) return@launch say("sms.denied")
            settings { it.copy(sms = true) }
            say("sms.on")
        }
    }

    fun openSmsSource(id: String?) {
        val src = store.current.smsSources.firstOrNull { it.id == id }
        smsEdit = if (src == null) {
            SmsEdit(accId = store.current.accounts.firstOrNull()?.id.orEmpty())
        } else {
            SmsEdit(src.id, src.name, src.sender, src.accId, src.expenseWords, src.incomeWords, src.ignoreWords, src.auto, src.cardMask)
        }
        smsTest = ""
    }

    /** Заполнить новое правило шаблоном банка: название и слова, отправителя человек впишет сам. */
    fun applySmsPreset(p: SmsPresets.Preset) {
        smsEdit = smsEdit?.copy(name = p.name, expenseWords = p.expense, incomeWords = p.income, ignoreWords = p.ignore)
    }

    fun saveSmsSource() {
        val e = smsEdit ?: return
        val name = e.name.trim()
        val sender = e.sender.trim()
        if (name.isEmpty()) return say("sms.needName")
        if (sender.isEmpty()) return say("sms.needSender")
        if (e.accId.isEmpty()) return say("msg.pickAccount")
        store.update { s ->
            val src = SmsSource(
                id = e.id ?: "sms${s.nextId}",
                name = name,
                sender = sender,
                accId = e.accId,
                expenseWords = e.expenseWords.trim(),
                incomeWords = e.incomeWords.trim(),
                ignoreWords = e.ignoreWords.trim(),
                auto = e.auto,
                cardMask = e.cardMask.filter { it.isDigit() }.takeLast(4),
            )
            s.copy(
                smsSources = if (e.id == null) s.smsSources + src else s.smsSources.map { if (it.id == e.id) src else it },
                nextId = if (e.id == null) s.nextId + 1 else s.nextId,
            )
        }
        smsEdit = null
        say("sms.saved")
    }

    fun askDeleteSmsSource(id: String) {
        val src = store.current.smsSources.firstOrNull { it.id == id } ?: return
        confirm = Confirm(l.t("sms.deleteTitle"), l.t("sms.deleteText", src.name), l.t("common.delete")) {
            store.update { s -> s.copy(smsSources = s.smsSources.filterNot { it.id == id }) }
            smsEdit = null
            say("sms.deleted")
        }
    }

    fun toggleSmsSource(id: String) = store.update { s ->
        s.copy(smsSources = s.smsSources.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
    }

    /** Что правило вытащит из вставленного текста — проверка без ожидания смски. */
    fun smsTestResult(): String {
        val e = smsEdit ?: return ""
        if (smsTest.isBlank()) return ""
        val c = calc
        val src = SmsSource("test", e.name, e.sender, e.accId, cardMask = e.cardMask, expenseWords = e.expenseWords, incomeWords = e.incomeWords, ignoreWords = e.ignoreWords)
        if (src.cardMask.isNotBlank() && !SmsParse.hasCard(smsTest, src.cardMask)) return l.t("sms.testOtherCard", src.cardMask)
        val p = SmsParse.parse(smsTest, src, c.accCur(e.accId)) ?: return l.t("sms.testNothing")
        val kind = l.t(if (p.income) "kind.income" else "kind.expense")
        val mask = if (p.mask.isEmpty()) "" else " · ${l.t("sms.testMask", p.mask)}"
        return l.t("sms.testResult", kind, c.fmt(p.amount, p.cur), p.title) + mask
    }

    /** Разобрать входящие за последние дни. */
    fun importSmsHistory(days: Int = 90) {
        if (smsBusy) return
        smsBusy = true
        viewModelScope.launch {
            try {
                val granted = runCatching { platform.requestSmsAccess() }.getOrDefault(false)
                if (!granted) return@launch say("sms.denied")
                val messages = platform.readSmsHistory(days)
                val added = SmsInbox.handleAll(store, messages, l)
                if (added > 0) inboxOpen = true
                say("sms.imported", added)
            } finally {
                smsBusy = false
            }
        }
    }

    // ——— общее пространство ———

    val space get() = store.current.space

    /** Код приглашения в читаемом виде: «7F3A-9C2B». */
    fun inviteCode(): String {
        val id = space?.id ?: return ""
        return if (id.length > 4) id.substring(0, 4) + "-" + id.substring(4) else id
    }

    /**
     * Завести общее пространство. Слот выбирается случайно: договариваться о нём
     * двум телефонам негде, а разойтись они должны наверняка — от этого зависит,
     * не столкнутся ли номера записей.
     */
    fun createSpace(name: String) {
        if (store.current.space != null) return
        start(randomCode(8), name)
        say("sync.created")
    }

    /** Присоединиться по коду от второго участника. */
    fun joinSpace(name: String) {
        if (store.current.space != null) return
        val id = joinCode.trim().uppercase().replace("-", "").replace(" ", "")
        if (id.length < 6) return say("sync.err.badCode")
        start(id, name)
        joinCode = ""
        say("sync.joined")
    }

    private fun start(spaceId: String, name: String) {
        val slot = Random.nextInt(1, 900_000)
        val meId = randomCode(6)
        val meName = name.trim().ifBlank { store.current.settings.userName.trim().ifBlank { l.t("sync.meDefault") } }
        store.update { s ->
            s.copy(
                space = SyncSpace(
                    id = spaceId,
                    name = l.t("sync.spaceName"),
                    memberId = meId,
                    memberName = meName,
                    slot = slot,
                    members = listOf(SyncMember(meId, meName, slot)),
                    links = listOf(SyncLink(SyncKind.DRIVE, enabled = s.settings.driveLinked)),
                ),
                // с этого номера начинаются мои записи — чужие сюда не попадут
                nextId = maxOf(s.nextId, Sync.slotStart(slot)),
                // платежи без автора теперь мои: иначе их провели бы оба телефона
                recurring = s.recurring.map { if (it.by.isEmpty()) it.copy(by = meId) else it },
            )
        }
    }

    fun leaveSpace() {
        if (store.current.space == null) return
        confirm = Confirm(l.t("sync.leaveTitle"), l.t("sync.leaveText"), l.t("sync.leave")) {
            // данные остаются: уходит только связь с чужим телефоном
            store.update { it.copy(space = null) }
            say("sync.left")
        }
    }

    fun toggleLink(kind: String) = store.update { s ->
        val sp = s.space ?: return@update s
        val has = sp.links.any { it.kind == kind }
        s.copy(
            space = sp.copy(
                links = if (has) sp.links.map { if (it.kind == kind) it.copy(enabled = !it.enabled) else it }
                else sp.links + SyncLink(kind),
            ),
        )
    }

    fun openWebDav() {
        val link = store.current.space?.links?.firstOrNull { it.kind == SyncKind.WEBDAV }
        webdavEdit = WebDavEdit(link?.url.orEmpty(), link?.login.orEmpty(), secure.get(WEBDAV_KEY))
    }

    fun saveWebDav() {
        val e = webdavEdit ?: return
        if (e.url.isBlank()) return say("sync.err.noAddress")
        secure.put(WEBDAV_KEY, e.password)
        store.update { s ->
            val sp = s.space ?: return@update s
            val link = SyncLink(SyncKind.WEBDAV, e.url.trim().trimEnd('/'), e.login.trim())
            s.copy(
                space = sp.copy(
                    links = if (sp.links.any { it.kind == SyncKind.WEBDAV }) {
                        sp.links.map { if (it.kind == SyncKind.WEBDAV) link else it }
                    } else {
                        sp.links + link
                    },
                ),
            )
        }
        webdavEdit = null
        say("sync.webdavSaved")
    }

    /**
     * Обмен через облако: по каждому включённому способу отдаём свой снимок
     * и забираем чужие. Снимок собирается заново перед каждым способом —
     * так во второй уходит уже то, что пришло из первого.
     */
    fun syncNow() {
        val sp = store.current.space ?: return
        if (syncBusy) return
        val transports = transportsFor(sp)
        if (transports.isEmpty()) return say("sync.err.noLink")
        runExchange(transports)
    }

    private fun runExchange(transports: List<SyncTransport>) {
        syncBusy = true
        viewModelScope.launch {
            try {
                var received = 0
                transports.forEach { t ->
                    val theirs = t.exchange(ownSnapshot() ?: return@launch)
                    theirs.forEach { absorb(it) }
                    received += theirs.size
                }
                markSynced()
                settleRequests()
                say(if (received > 0) "sync.done" else "sync.doneAlone", received)
            } catch (e: SyncError) {
                say(e.key, *e.args.toTypedArray())
            } catch (e: DriveAuthError) {
                say("msg.driveAuthError", e.code)
            } catch (e: Exception) {
                flash(e.message ?: l.t("sync.err.offline"))
            } finally {
                syncBusy = false
            }
        }
    }

    /** Мой снимок для обмена: без черновиков, правил СМС и личных мелочей. */
    private fun ownSnapshot(): SyncSnapshot? {
        val sp = store.current.space ?: return null
        val now = Clock.System.now().toEpochMilliseconds()
        return SyncSnapshot(sp.id, sp.memberId, sp.memberName, now, store.current.forSync())
    }

    /**
     * Принять чужой снимок: слить данные и запомнить участника. Пишется без
     * отметок времени — у чужих правок своё время, и оно должно сохраниться.
     */
    private fun absorb(snap: SyncSnapshot) {
        val now = Clock.System.now().toEpochMilliseconds()
        val merged = Sync.merge(store.current, snap.data, now)
        val sp = merged.space ?: return
        val members = if (sp.members.any { it.id == snap.memberId }) {
            sp.members.map { if (it.id == snap.memberId) it.copy(name = snap.memberName) else it }
        } else {
            sp.members + SyncMember(snap.memberId, snap.memberName, 0)
        }
        store.applyMerged(merged.copy(space = sp.copy(members = members)))
    }

    private fun markSynced() {
        val now = Clock.System.now().toEpochMilliseconds()
        store.update { s -> s.copy(space = s.space?.copy(syncedAt = now)) }
    }

    // ——— заявки на расход ———

    private fun askApproval(d: Draft, amount: Double, acc: Account) {
        val sp = store.current.space ?: return
        val c = calc
        store.update { s ->
            s.copy(
                requests = s.requests + SpendRequest(
                    id = s.nextId,
                    by = sp.memberId,
                    byName = sp.memberName,
                    accId = acc.id,
                    amount = amount,
                    cat = d.cat,
                    title = d.note.trim().ifBlank { c.cat(d.cat).name },
                    date = d.date,
                    at = Clock.System.now().toEpochMilliseconds(),
                ),
                nextId = s.nextId + 1,
            )
        }
        draft = null
        say("req.sent", c.fmt(amount, acc.cur))
    }

    /** Ждут моего решения: чужие заявки без ответа. */
    fun requestsForMe(): List<SpendRequest> {
        val me = store.current.space?.memberId ?: return emptyList()
        return store.current.requests.filter { it.status == RequestStatus.PENDING && it.by != me }.sortedByDescending { it.at }
    }

    /** Мои заявки: последние сверху. */
    fun myRequests(): List<SpendRequest> {
        val me = store.current.space?.memberId ?: return emptyList()
        return store.current.requests.filter { it.by == me }.sortedByDescending { it.at }.take(30)
    }

    fun approveRequest(id: Long) = decide(id, RequestStatus.APPROVED, "req.approved")

    fun declineRequest(id: Long) = decide(id, RequestStatus.DECLINED, "req.declined")

    private fun decide(id: Long, status: String, message: String) {
        val sp = store.current.space ?: return
        val req = store.current.requests.firstOrNull { it.id == id } ?: return
        // своё решать нельзя: смысл заявки в согласии другого человека
        if (req.by == sp.memberId || req.status != RequestStatus.PENDING) return
        val now = Clock.System.now().toEpochMilliseconds()
        store.update { s ->
            s.copy(
                requests = s.requests.map {
                    if (it.id == id) it.copy(status = status, decidedBy = sp.memberId, decidedByName = sp.memberName, decidedAt = now) else it
                },
            )
        }
        say(message, req.byName)
    }

    fun cancelRequest(id: Long) {
        val sp = store.current.space ?: return
        store.update { s ->
            s.copy(
                requests = s.requests.map {
                    if (it.id == id && it.by == sp.memberId && it.status == RequestStatus.PENDING) it.copy(status = RequestStatus.CANCELLED) else it
                },
            )
        }
        say("req.cancelled")
    }

    /**
     * Одобренные мои заявки превращаются в операции. Делает это только автор
     * заявки: так на одну покупку не появится двух операций у двух людей.
     */
    private fun settleRequests() {
        val me = store.current.space?.memberId ?: return
        val ready = store.current.requests.filter { it.by == me && it.status == RequestStatus.APPROVED && it.txId == null }
        if (ready.isEmpty()) return
        store.update { s ->
            var next = s.nextId
            val made = mutableMapOf<Long, Tx>()
            ready.forEach { r ->
                made[r.id] = Tx(
                    id = next++,
                    date = r.date,
                    title = r.title,
                    cat = r.cat,
                    acc = r.accId,
                    amount = -r.amount,
                    note = l.t("req.txNote", r.decidedByName),
                    by = me,
                )
            }
            s.copy(
                txs = made.values.toList() + s.txs,
                requests = s.requests.map { r -> made[r.id]?.let { r.copy(txId = it.id) } ?: r },
                nextId = next,
            )
        }
        say("req.settled", ready.size)
    }

    // ——— обмен напрямую по Wi-Fi ———

    val canHostLan get() = platform.canHostLan

    /**
     * Принимать обмен с телефона рядом. Порт открыт, только пока открыт этот
     * режим; вход — по шестизначному коду с экрана, после пяти неверных попыток
     * приём закрывается сам: код короткий, и перебирать его нельзя давать.
     */
    fun startLanHost() {
        if (store.current.space == null || lanHostAddress != null) return
        val code = (1..6).map { Random.nextInt(10) }.joinToString("")
        lanFails = 0
        viewModelScope.launch {
            val address = runCatching {
                // сервер отвечает из своего потока, а состояние экрана меняется только в главном
                platform.startLanHost { given, body -> withContext(Dispatchers.Main) { handleLan(code, given, body) } }
            }.getOrNull()
            if (address == null) return@launch say("sync.lan.noNetwork")
            lanHostCode = code
            lanHostAddress = address
        }
    }

    fun stopLanHost() {
        platform.stopLanHost()
        lanHostAddress = null
        lanHostCode = ""
    }

    /** Один разговор с гостем: проверить код, слить его снимок, отдать свой. */
    private suspend fun handleLan(expected: String, given: String, body: String): Pair<Int, String> {
        if (given != expected) {
            lanFails++
            if (lanFails >= 5) {
                stopLanHost()
                say("sync.lan.tooMany")
            }
            return 403 to "{}"
        }
        val snap = runCatching { syncJson.decodeFromString(SyncSnapshot.serializer(), body) }.getOrNull()
            ?: return 400 to "{}"
        val mine = store.current.space ?: return 409 to "{}"
        if (snap.spaceId != mine.id) return 409 to "{}"
        absorb(snap)
        markSynced()
        settleRequests()
        say("sync.lan.received", snap.memberName)
        val out = ownSnapshot() ?: return 409 to "{}"
        return 200 to syncJson.encodeToString(SyncSnapshot.serializer(), out)
    }

    /** Гостевая сторона: обменяться с телефоном, который сейчас принимает. */
    fun syncLan() {
        if (store.current.space == null || syncBusy) return
        val address = lanAddress.trim()
        val code = lanCode.trim()
        if (address.isEmpty()) return say("sync.lan.needAddress")
        if (code.length != 6) return say("sync.lan.needCode")
        runExchange(listOf(LanSync(address, code)))
    }

    override fun onCleared() {
        platform.stopLanHost()
        super.onCleared()
    }

    private fun transportsFor(sp: SyncSpace): List<SyncTransport> = sp.links.filter { it.enabled }.mapNotNull { link ->
        when (link.kind) {
            SyncKind.DRIVE -> DriveSync { platform.driveToken() }
            SyncKind.WEBDAV -> if (link.url.isBlank()) null else WebDavSync(link, secure.get(WEBDAV_KEY))
            else -> null
        }
    }

    /** Возможные дубли после обмена: двое записали одну покупку. */
    fun duplicatePairs() = Sync.duplicates(store.current)

    private fun randomCode(len: Int): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..len).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
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
        settings { it.copy(lastBackupAt = kotlin.time.Clock.System.now().toEpochMilliseconds()) }
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
                applyRestored(store.parseBackup(DriveApi.download(t, b.id)), driveLinked = true)
            }
        }
    }

    /**
     * Заменить данные восстановленной копией. Перед этим текущее состояние
     * ложится рядом страховочным файлом, а личные настройки копии — связь
     * с Диском, автокопия — берутся с этого телефона, а не из копии.
     */
    private fun applyRestored(restored: AppData, driveLinked: Boolean) {
        platform.saveBeforeRestore(store.exportJson())
        val keep = store.current.settings
        store.replace(
            restored.copy(
                settings = restored.settings.copy(
                    onboarded = true,
                    driveLinked = driveLinked,
                    autoBackup = keep.autoBackup,
                    lastBackupAt = keep.lastBackupAt,
                ),
                // черновики и общее пространство живут на телефоне, а не в копии
                inbox = store.current.inbox,
                space = store.current.space,
            ),
        )
        say("msg.restored")
    }

    // ——— выписка банка ———

    fun openStatement() {
        viewModelScope.launch {
            val file = runCatching { platform.openTextFile() }.getOrNull() ?: return@launch
            val rows = Statement.split(file.text)
            if (rows.size < 2) return@launch say("stmt.empty", file.name)
            val d = store.current
            statement = StatementDraft(
                name = file.name,
                rows = rows,
                layout = Statement.guess(rows.first()),
                accId = d.accounts.firstOrNull { it.cur == calc.main }?.id ?: d.accounts.firstOrNull()?.id.orEmpty(),
            )
        }
    }

    /** Строки, которые получатся из выписки при нынешней раскладке колонок. */
    fun statementRows(): List<StatementRow> {
        val s = statement ?: return emptyList()
        if (!s.layout.ready) return emptyList()
        return Statement.parse(s.rows, s.layout, calc.accCur(s.accId))
    }

    /** Такое уже записано: та же дата, сумма и счёт — повторный импорт не удваивает. */
    fun statementDupes(rows: List<StatementRow>): Int {
        val s = statement ?: return 0
        val known = store.current.txs.filter { it.acc == s.accId }.map { it.date to (it.amount * 100).roundToLong() }.toHashSet()
        return rows.count { (it.date to (it.amount * 100).roundToLong()) in known }
    }

    fun applyStatement() {
        val s = statement ?: return
        val rows = statementRows()
        if (rows.isEmpty()) return say("stmt.nothing")
        val d = store.current
        val known = d.txs.filter { it.acc == s.accId }.map { it.date to (it.amount * 100).roundToLong() }.toHashSet()
        val fresh = rows.filter { (it.date to (it.amount * 100).roundToLong()) !in known }
        val me = d.space?.memberId.orEmpty()
        val exp = d.categories.firstOrNull { !it.income }?.id.orEmpty()
        val inc = d.categories.firstOrNull { it.income }?.id.orEmpty()
        store.update { st ->
            var next = st.nextId
            val txs = fresh.map { r ->
                val income = r.amount > 0
                // категория: сначала память о магазине, потом совпадение с названием категории
                val cat = st.merchantCats[r.text.lowercase()]
                    ?: st.categories.firstOrNull { it.income == income && r.text.contains(it.name, ignoreCase = true) }?.id
                    ?: if (income) inc else exp
                Tx(
                    id = next++,
                    date = r.date,
                    title = r.text.ifBlank { l.t(if (income) "kind.income" else "kind.expense") },
                    cat = cat,
                    acc = s.accId,
                    amount = r.amount,
                    by = me,
                )
            }
            st.copy(txs = (txs + st.txs).sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.id }), nextId = next)
        }
        statement = null
        say("stmt.done", fresh.size, rows.size - fresh.size)
    }

    // ——— регулярные платежи ———

    /**
     * Провести наступившие платежи. Вызывается при запуске: на iOS фоновых задач
     * нет, а на Android это страховка, если фоновая задача не успела.
     */
    fun runRecurring() {
        val me = store.current.space?.memberId.orEmpty()
        var made = emptyList<String>()
        store.update { cur ->
            val run = Recurrings.due(cur, calc.todayDay, me) { r, n -> l.t("rec.installment", r.title, n, r.total) }
            made = run.made
            run.data
        }
        if (made.isNotEmpty()) say("rec.made", made.size)
    }

    fun openRecurring(id: String?) {
        val d = store.current
        val r = d.recurring.firstOrNull { it.id == id }
        recEdit = if (r == null) {
            RecEdit(
                accId = d.accounts.firstOrNull()?.id.orEmpty(),
                cat = d.categories.firstOrNull { !it.income }?.id.orEmpty(),
                start = calc.todayDay,
            )
        } else {
            RecEdit(
                id = r.id, title = r.title, amount = numText(r.amount), income = r.income, accId = r.accId, cat = r.cat,
                every = r.every, start = r.start, total = if (r.total > 0) r.total.toString() else "",
                auto = r.auto, remindDays = r.remindDays,
            )
        }
    }

    fun saveRecurring() {
        val e = recEdit ?: return
        val title = e.title.trim()
        val amount = num(e.amount)
        if (title.isEmpty()) return say("rec.needTitle")
        if (amount <= 0) return say("msg.amountNeeded")
        if (e.accId.isEmpty()) return say("msg.pickAccount")
        val total = e.total.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
        val me = store.current.space?.memberId.orEmpty()
        store.update { s ->
            val old = s.recurring.firstOrNull { it.id == e.id }
            val r = Recurring(
                id = e.id ?: "rec${s.nextId}",
                title = title,
                amount = amount,
                income = e.income,
                accId = e.accId,
                cat = e.cat,
                every = e.every,
                start = e.start,
                total = total,
                // смена даты первого платежа не должна заново проводить уже проведённые
                done = old?.done ?: 0,
                auto = e.auto,
                remindDays = e.remindDays,
                remindedFor = old?.remindedFor ?: 0,
                by = old?.by ?: me,
            )
            s.copy(
                recurring = if (old == null) s.recurring + r else s.recurring.map { if (it.id == r.id) r else it },
                nextId = if (old == null) s.nextId + 1 else s.nextId,
            )
        }
        recEdit = null
        platform.syncRecurring(true)
        runRecurring()
        say("rec.saved")
    }

    fun askDeleteRecurring(id: String) {
        val r = store.current.recurring.firstOrNull { it.id == id } ?: return
        confirm = Confirm(l.t("rec.deleteTitle"), l.t("rec.deleteText", r.title), l.t("common.delete")) {
            store.update { s -> s.copy(recurring = s.recurring.filterNot { it.id == id }) }
            recEdit = null
            platform.syncRecurring(store.current.recurring.isNotEmpty())
            say("rec.deleted")
        }
    }

    fun toggleRecurring(id: String) = store.update { s ->
        s.copy(recurring = s.recurring.map { if (it.id == id) it.copy(active = !it.active) else it })
    }

    fun upcomingPayments(days: Int = 7) = Recurrings.upcoming(store.current, calc.todayDay, days)

    // ——— обновления ———

    val canCheckUpdates get() = platform.updatesFromGitHub

    /** При запуске молча, по кнопке — с ответом даже если обновлять нечего. */
    fun checkUpdates(manual: Boolean) {
        if (checkingUpdates) return
        checkingUpdates = true
        viewModelScope.launch {
            try {
                update = Updates.check(platform.version)
                if (manual) say(if (update == null) "upd.latest" else "upd.found", update?.version ?: platform.version)
            } finally {
                checkingUpdates = false
            }
        }
    }

    fun downloadUpdate() {
        update?.let { platform.openUrl(it.apkUrl) }
    }

    fun dismissUpdate() {
        update = null
    }

    // ——— копия в файл ———

    private fun backupName() = "kopeechka-backup-${today().isoString()}.json"

    /** Сохранить копию файлом — туда, куда укажет человек. */
    fun saveBackupFile() = saveFile(backupName(), store.exportJson(), MIME_JSON) { name -> say("file.saved", name) }

    /** Отправить копию в мессенджер: в Туркменистане это надёжнее облака. */
    fun shareBackupFile() {
        runCatching { platform.shareTextFile(backupName(), store.exportJson(), MIME_JSON) }
            .onFailure { say("csv.fileError") }
    }

    fun restoreFromFile() {
        viewModelScope.launch {
            val file = runCatching { platform.openTextFile() }.getOrNull() ?: return@launch
            val restored = runCatching { store.parseBackup(file.text) }.getOrNull()
                ?: return@launch say("file.notBackup", file.name)
            confirm = Confirm(
                l.t("msg.restoreTitle"),
                l.t("file.restoreText", file.name, l.n(restored.txs.size, "op")),
                l.t("common.restore"),
            ) { applyRestored(restored, driveLinked = store.current.settings.driveLinked) }
        }
    }

    fun unlinkDrive() {
        settings { it.copy(driveLinked = false, autoBackup = false) }
        platform.syncAutoBackup(false)
        driveList = emptyList()
        say("msg.driveUnlinked")
    }
}
