package app.kopeechka.finance

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.kopeechka.finance.data.Account
import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.CAT_GOAL
import app.kopeechka.finance.data.CAT_TRANSFER
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.Category
import app.kopeechka.finance.data.Csv
import app.kopeechka.finance.data.CurrencyDef
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.Cut
import app.kopeechka.finance.data.Demo
import app.kopeechka.finance.data.Goal
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.data.Palette
import app.kopeechka.finance.data.Period
import app.kopeechka.finance.data.Settings
import app.kopeechka.finance.data.Tx
import app.kopeechka.finance.net.Ai
import app.kopeechka.finance.net.AiError
import app.kopeechka.finance.net.DriveBackup
import app.kopeechka.finance.net.DriveError
import app.kopeechka.finance.net.RemoteBackup
import app.kopeechka.finance.work.Schedules
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class Tab { HOME, OPS, BUDGET, REPORT, SETTINGS }
enum class Page { ACCOUNTS, CATEGORIES, GOALS, BACKUP, CURRENCIES }
enum class Kind(val key: String) { EXPENSE("kind.expense"), INCOME("kind.income"), TRANSFER("kind.transfer") }

data class Draft(
    val editId: Long? = null,
    val kind: Kind = Kind.EXPENSE,
    val amount: String = "",
    val cat: String = "food",
    val incomeCat: String = "income",
    val from: String = "",
    val to: String = "",
    val note: String = "",
    val date: Long = LocalDate.now().toEpochDay(),
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
data class GoalSheet(val goalId: String, val amount: Double, val from: String)
data class Confirm(val title: String, val text: String, val action: String, val onYes: () -> Unit)

/** Запрос к системному диалогу файлов: "create" — сохранить, "open" — открыть. */
data class FileRequest(val kind: String, val name: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val host = app as KopeechkaApp
    private val store = host.store
    private val secure = host.secure
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

    var opsFilter by mutableStateOf("all")
    var period by mutableStateOf(Period.MONTH)
    var cut by mutableStateOf(Cut.CATS)

    /** 0 — текущий период, −1 — предыдущий и так далее. */
    var periodOffset by mutableStateOf(0)
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
    val authRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    private var pendingDrive: (suspend (String) -> Unit)? = null

    val calc: Calc get() = Calc(store.current)

    /** Язык интерфейса: из настроек либо системный. */
    val l: Lang get() = Lang.of(store.current.settings.lang)
    private val ctx get() = getApplication<Application>()

    init {
        val s = store.current.settings
        DriveBackup.folderName = l.t("backup.folder")
        Schedules.syncReminder(ctx, s.remind, s.remindHour)
        Schedules.syncAutoBackup(ctx, s.autoBackup && s.driveLinked)
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

    /** Конец заставки: демо-данные в выбранной валюте, она же становится базой курсов. */
    fun finishOnboarding() {
        val cur = onbCurrency()
        val keep = store.current.settings
        val demo = Demo.create(l, cur)
        store.replace(
            demo.copy(
                settings = keep.copy(
                    onboarded = true,
                    mainCur = cur,
                    rates = demo.settings.rates,
                    currencyCodes = demo.settings.currencyCodes,
                ),
            ),
        )
    }

    /** Последний шаг заставки: без примеров, один пустой счёт в выбранной валюте. */
    fun startClean() {
        val cur = onbCurrency()
        store.replace(
            Demo.empty(store.current.settings.copy(onboarded = true, mainCur = cur, rates = Demo.rates(cur))),
        )
    }

    fun setDark(on: Boolean) = settings { it.copy(dark = on) }

    /** Смена языка: интерфейс, названия валют, папка копий и канал уведомлений. */
    fun setLang(code: String) {
        settings { it.copy(lang = code) }
        val nl = l
        Currencies.setLang(nl)
        DriveBackup.folderName = nl.t("backup.folder")
        Schedules.ensureChannel(ctx, nl)
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
        val id = d.editId ?: store.current.nextId
        val tx = when (d.kind) {
            Kind.TRANSFER -> {
                val dst = c.acc(d.to) ?: return say("msg.pickToAcc")
                if (src.id == dst.id) return say("msg.sameAccounts")
                val old = d.editId?.let { eid -> store.current.txs.firstOrNull { it.id == eid && it.acc == src.id }?.amount } ?: 0.0
                if (c.balance(src) - old < v) return say("msg.notEnough", src.name)
                val got = c.conv(v, src.cur, dst.cur)
                Tx(id, d.date, l.t("msg.transferTitle", src.name, dst.name), CAT_TRANSFER, src.id, -v, d.note, dst.id, got)
            }
            Kind.INCOME -> Tx(id, d.date, d.note.trim().ifBlank { c.cat(d.incomeCat).name }, d.incomeCat, src.id, v)
            Kind.EXPENSE -> Tx(id, d.date, d.note.trim().ifBlank { c.cat(d.cat).name }, d.cat, src.id, -v)
        }
        store.update { s ->
            if (d.editId != null) s.copy(txs = s.txs.map { if (it.id == d.editId) tx else it })
            else s.copy(txs = listOf(tx) + s.txs, nextId = s.nextId + 1)
        }
        draft = null
        when (d.kind) {
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
            s.copy(txs = s.txs.filterNot { it.id == id }, goals = goals)
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

    /** Переход из карточки на главном в нужный отчёт. */
    fun goReport(p: Period, c: Cut, offset: Int = 0) {
        period = p
        cut = c
        periodOffset = offset
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

    fun contribute() {
        val gs = goalSheet ?: return
        val c = calc
        val g = store.current.goals.firstOrNull { it.id == gs.goalId } ?: return
        val a = c.acc(gs.from) ?: return
        val debit = c.conv(gs.amount, g.cur, a.cur)
        if (c.balance(a) < debit) return say("msg.notEnoughGoal", a.name)
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
        Schedules.syncReminder(ctx, on, store.current.settings.remindHour)
        if (on) say("msg.remindSet", store.current.settings.remindHour)
    }

    fun setRemindHour(h: Int) {
        settings { it.copy(remindHour = h) }
        Schedules.syncReminder(ctx, store.current.settings.remind, h)
    }

    fun setAutoBackup(on: Boolean) {
        settings { it.copy(autoBackup = on) }
        Schedules.syncAutoBackup(ctx, on && store.current.settings.driveLinked)
    }

    fun askLoadDemo() {
        confirm = Confirm(l.t("msg.demoTitle"), l.t("msg.demoText"), l.t("msg.demoReplace")) {
            val demo = Demo.create(l)
            store.replace(demo.copy(settings = store.current.settings.copy(onboarded = true, mainCur = demo.settings.mainCur)))
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

    /** Запрос к системе: "create" — выбрать, куда сохранить, "open" — что открыть. */
    val fileRequests = MutableSharedFlow<FileRequest>(extraBufferCapacity = 1)
    var csvExportSheet by mutableStateOf(false)
    var csvPreview by mutableStateOf<Csv.Preview?>(null)
    private var pendingFileKind = ""
    private var pendingExportRange: Pair<Long, Long>? = null

    fun askExportCsv() {
        csvExportSheet = true
    }

    /** scope: WEEK / MONTH / QUARTER / YEAR из [Period] либо "all". */
    fun exportCsv(scope: String) {
        csvExportSheet = false
        pendingExportRange = if (scope == "all") null else {
            val r = calc.range(Period.valueOf(scope), 0)
            r.from to r.to
        }
        pendingFileKind = "export"
        viewModelScope.launch { fileRequests.emit(FileRequest("create", "kopeechka-${LocalDate.now()}.csv")) }
    }

    fun saveTemplate() {
        pendingFileKind = "template"
        viewModelScope.launch { fileRequests.emit(FileRequest("create", "kopeechka-template.csv")) }
    }

    fun askImportCsv() {
        pendingFileKind = "import"
        viewModelScope.launch { fileRequests.emit(FileRequest("open", "")) }
    }

    /** Пользователь выбрал файл: пишем выгрузку или читаем загрузку. */
    fun onFileChosen(uri: Uri) {
        val resolver = ctx.contentResolver
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "CSV"
        try {
            when (pendingFileKind) {
                "export" -> {
                    val r = pendingExportRange
                    val d = store.current
                    val count = d.txs.count { t -> r == null || (t.date >= r.first && t.date <= r.second) }
                    resolver.openOutputStream(uri)?.use { it.write(Csv.export(d, r?.first, r?.second).toByteArray(Charsets.UTF_8)) }
                    say("csv.exported", l.n(count, "op"), name)
                }
                "template" -> {
                    resolver.openOutputStream(uri)?.use { it.write(Csv.template(store.current, l).toByteArray(Charsets.UTF_8)) }
                    say("csv.templateSaved", name)
                }
                "import" -> {
                    val text = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    val p = Csv.parse(text, store.current, l)
                    if (p.rows.isEmpty() && p.errors.isEmpty()) say("csv.nothing") else csvPreview = p
                }
            }
        } catch (e: Exception) {
            say("csv.fileError")
        }
        pendingFileKind = ""
    }

    fun applyImport() {
        val p = csvPreview ?: return
        store.update { Csv.apply(it, p, l) }
        csvPreview = null
        say("csv.imported", l.n(p.rows.size, "op"))
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
                val r = DriveBackup.authorize(ctx)
                val token = r.accessToken
                if (r.hasResolution()) {
                    pendingDrive = action
                    authRequests.emit(r.pendingIntent!!.intentSender)
                    return@launch
                }
                if (token == null) throw IllegalStateException(l.t("msg.noToken"))
                runDrive(token, action)
            } catch (e: ApiException) {
                driveBusy = false
                say("msg.driveAuthError", e.statusCode)
            } catch (e: DriveError) {
                driveBusy = false
                say("msg.driveError", e.text(l))
            } catch (e: Exception) {
                driveBusy = false
                say("msg.driveError", e.message ?: "")
            }
        }
    }

    fun onAuthResult(data: Intent?) {
        val action = pendingDrive
        pendingDrive = null
        viewModelScope.launch {
            try {
                val token = DriveBackup.resultFromIntent(ctx, data).accessToken
                if (action != null && token != null) runDrive(token, action) else driveBusy = false
            } catch (e: ApiException) {
                driveBusy = false
                say("msg.driveDenied")
            }
        }
    }

    fun onAuthCancelled() {
        pendingDrive = null
        driveBusy = false
        say("msg.driveCancelled")
    }

    private suspend fun runDrive(token: String, action: suspend (String) -> Unit) {
        try {
            action(token)
            if (!store.current.settings.driveLinked) {
                settings { it.copy(driveLinked = true) }
                Schedules.syncAutoBackup(ctx, store.current.settings.autoBackup)
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
        val name = DriveBackup.upload(t, store.exportJson())
        DriveBackup.prune(t, 10)
        settings { it.copy(lastBackupAt = System.currentTimeMillis()) }
        driveList = DriveBackup.list(t)
        say("msg.backupSaved", name)
    }

    fun refreshBackups() = driveAction { t -> driveList = DriveBackup.list(t) }

    fun askRestore(b: RemoteBackup) {
        confirm = Confirm(
            l.t("msg.restoreTitle"),
            l.t("msg.restoreText", DriveBackup.formatTime(b.created, l)),
            l.t("common.restore"),
        ) {
            driveAction { t ->
                val restored = store.parseBackup(DriveBackup.download(t, b.id))
                File(ctx.filesDir, "before-restore.json").writeText(store.exportJson())
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
        Schedules.syncAutoBackup(ctx, false)
        driveList = emptyList()
        say("msg.driveUnlinked")
    }
}
