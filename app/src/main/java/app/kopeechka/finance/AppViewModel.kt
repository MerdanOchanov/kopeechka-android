package app.kopeechka.finance

import android.app.Application
import android.content.Intent
import android.content.IntentSender
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
import app.kopeechka.finance.data.CurrencyDef
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.Cut
import app.kopeechka.finance.data.Demo
import app.kopeechka.finance.data.Goal
import app.kopeechka.finance.data.Palette
import app.kopeechka.finance.data.Period
import app.kopeechka.finance.data.Settings
import app.kopeechka.finance.data.Tx
import app.kopeechka.finance.net.Ai
import app.kopeechka.finance.net.AiError
import app.kopeechka.finance.net.DriveBackup
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
enum class Kind(val label: String) { EXPENSE("Расход"), INCOME("Доход"), TRANSFER("Перевод") }

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
    val type: String = "Карта",
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
    private val ctx get() = getApplication<Application>()

    init {
        val s = store.current.settings
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

    fun finishOnboarding() = settings { it.copy(onboarded = true) }

    /** Последний шаг онбординга: убрать демо-данные и начать с одного пустого счёта. */
    fun startClean() {
        store.replace(Demo.empty(store.current.settings.copy(onboarded = true)))
    }
    fun setDark(on: Boolean) = settings { it.copy(dark = on) }

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
            confirm = Confirm("Взнос на цель", "Удалить «${t.title}»? Деньги вернутся на счёт, а прогресс цели уменьшится.", "Удалить") { deleteTx(t.id) }
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
        if (v <= 0) return flash("Введите сумму")
        val c = calc
        val src = c.acc(d.from) ?: return flash("Сначала добавьте счёт")
        val id = d.editId ?: store.current.nextId
        val tx = when (d.kind) {
            Kind.TRANSFER -> {
                val dst = c.acc(d.to) ?: return flash("Выберите счёт зачисления")
                if (src.id == dst.id) return flash("Выберите разные счета")
                val old = d.editId?.let { eid -> store.current.txs.firstOrNull { it.id == eid && it.acc == src.id }?.amount } ?: 0.0
                if (c.balance(src) - old < v) return flash("На «${src.name}» недостаточно средств")
                val got = c.conv(v, src.cur, dst.cur)
                Tx(id, d.date, "Перевод: ${src.name} → ${dst.name}", CAT_TRANSFER, src.id, -v, d.note, dst.id, got)
            }
            Kind.INCOME -> Tx(id, d.date, d.note.trim().ifBlank { c.cat(d.incomeCat).name }, d.incomeCat, src.id, v)
            Kind.EXPENSE -> Tx(id, d.date, d.note.trim().ifBlank { c.cat(d.cat).name }, d.cat, src.id, -v)
        }
        store.update { s ->
            if (d.editId != null) s.copy(txs = s.txs.map { if (it.id == d.editId) tx else it })
            else s.copy(txs = listOf(tx) + s.txs, nextId = s.nextId + 1)
        }
        draft = null
        flash(
            when (d.kind) {
                Kind.TRANSFER -> "Перевод ${c.fmt(v, src.cur)} → ${c.fmt(tx.toAmount ?: 0.0, c.accCur(tx.toAcc))}"
                Kind.INCOME -> "Доход ${c.fmt(v, src.cur)} · ${c.cat(tx.cat).name}"
                Kind.EXPENSE -> (if (d.editId != null) "Изменено: " else "Расход ") + "${c.fmt(v, src.cur)} · ${c.cat(tx.cat).name}"
            },
        )
    }

    fun askDeleteTx(id: Long) {
        confirm = Confirm("Удалить операцию", "Операция исчезнет из истории, балансы пересчитаются.", "Удалить") { deleteTx(id) }
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
        flash("Операция удалена")
    }

    // ——— валюты ———

    fun setMainCur(code: String) {
        settings { it.copy(mainCur = code) }
        curSheet = null
        flash("Основная валюта — ${Currencies.info(code).name}")
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
        flash("«${a.name}» теперь в ${Currencies.info(code).inName}")
    }

    fun setRate(code: String, text: String) {
        val v = text.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: return
        if (v <= 0) return
        settings { it.copy(rates = it.rates + (code to v)) }
    }

    /** Добавить валюту из каталога. */
    fun addCurrency(code: String) {
        val c = code.trim().uppercase()
        if (c.isBlank()) return
        settings { s ->
            s.copy(
                currencyCodes = (s.currencyCodes + c).distinct(),
                rates = if (s.rates.containsKey(c)) s.rates else s.rates + (c to Currencies.defaultRate(c)),
            )
        }
        currencyPicker = false
        currencyQuery = ""
        flash("Добавлены ${Currencies.info(c).name} — проверьте курс")
    }

    /** Добавить валюту, которой нет в каталоге. */
    fun saveCustomCurrency() {
        val dft = currencyDraft ?: return
        val code = dft.code.trim().uppercase()
        if (code.length !in 2..6) return flash("Код валюты — от 2 до 6 символов")
        if (calc.currencies.contains(code)) return flash("Такая валюта уже добавлена")
        val rate = dft.rate.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: 0.0
        if (rate <= 0) return flash("Укажите курс в рублях")
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
        flash("Валюта $code добавлена")
    }

    fun removeCurrency(code: String) {
        if (code == Currencies.BASE) return flash("Рубль — база курсов, его убрать нельзя")
        val d = store.current
        if (d.settings.mainCur == code) return flash("Это основная валюта приложения")
        if (d.accounts.any { it.cur == code }) return flash("Валюта используется на счёте")
        if (d.goals.any { it.cur == code }) return flash("Валюта используется в цели")
        settings { s ->
            s.copy(
                currencyCodes = s.currencyCodes.filterNot { it == code },
                customCurrencies = s.customCurrencies.filterNot { it.code == code },
            )
        }
        flash("Валюта $code убрана")
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
        accEdit = if (a == null) AccEdit(cur = store.current.settings.mainCur)
        else AccEdit(a.id, a.name, a.type, a.mask, a.cur, calc.balance(a).roundToLong().toString(), a.inTotal)
    }

    fun saveAcc() {
        val e = accEdit ?: return
        val name = e.name.trim()
        if (name.isEmpty()) return flash("Введите название счёта")
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
        flash(if (e.id == null) "Счёт «$name» добавлен" else "Счёт сохранён")
    }

    fun toggleInTotal(id: String) = store.update { s -> s.copy(accounts = s.accounts.map { if (it.id == id) it.copy(inTotal = !it.inTotal) else it }) }

    fun askDeleteAcc(id: String) {
        val d = store.current
        val a = d.accounts.firstOrNull { it.id == id } ?: return
        if (d.accounts.size <= 1) return flash("Нужен хотя бы один счёт")
        val n = d.txs.count { it.acc == id || it.toAcc == id }
        confirm = Confirm(
            "Удалить счёт",
            if (n > 0) "Вместе со счётом «${a.name}» удалятся $n операций по нему." else "Удалить счёт «${a.name}»?",
            "Удалить",
        ) {
            store.update { s -> s.copy(accounts = s.accounts.filterNot { it.id == id }, txs = s.txs.filterNot { it.acc == id || it.toAcc == id }) }
            accEdit = null
            flash("Счёт удалён")
        }
    }

    // ——— категории ———

    fun openCatEdit(c: Category?, income: Boolean = false) {
        val cc = calc
        catEdit = if (c == null) CatEdit(income = income, color = Palette.HEXES.first())
        else CatEdit(c.id, c.name, c.code, if (c.limitRub > 0) cc.limitMain(c).roundToLong().toString() else "", c.income, cc.colorOf(c.id))
    }

    fun saveCat() {
        val e = catEdit ?: return
        val name = e.name.trim()
        if (name.isEmpty()) return flash("Введите название категории")
        val code = e.code.trim().ifBlank { name.filter { it.isLetter() }.take(2) }.uppercase().take(2).ifBlank { "??" }
        val c = calc
        val limitRub = c.conv(e.limit.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: 0.0, c.main, "RUB")
        store.update { s ->
            if (e.id == null) {
                s.copy(
                    categories = s.categories + Category("c${s.nextId}", code, name, if (e.income) 0.0 else limitRub, e.income, e.color),
                    nextId = s.nextId + 1,
                )
            } else {
                s.copy(
                    categories = s.categories.map {
                        if (it.id == e.id) it.copy(name = name, code = code, limitRub = if (it.income) 0.0 else limitRub, color = e.color) else it
                    },
                )
            }
        }
        catEdit = null
        flash(if (e.id == null) "Категория «$name» создана" else "Категория сохранена")
    }

    fun askDeleteCat(id: String) {
        val d = store.current
        val cat = d.categories.firstOrNull { it.id == id } ?: return
        val sameKind = d.categories.filter { it.income == cat.income && it.id != id }
        val target = sameKind.firstOrNull { it.id == "other" } ?: sameKind.firstOrNull()
            ?: return flash("Это последняя категория ${if (cat.income) "доходов" else "расходов"}")
        val n = d.txs.count { it.cat == id }
        confirm = Confirm(
            "Удалить категорию",
            if (n > 0) "$n операций из «${cat.name}» перейдут в «${target.name}»." else "Удалить «${cat.name}»?",
            "Удалить",
        ) {
            store.update { s -> s.copy(categories = s.categories.filterNot { it.id == id }, txs = s.txs.map { if (it.cat == id) it.copy(cat = target.id) else it }) }
            catEdit = null
            flash("Категория удалена")
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
        if (name.isEmpty() || target <= 0) return flash("Укажите название и сумму цели")
        store.update { s ->
            if (e.id == null) s.copy(goals = s.goals + Goal("g${s.nextId}", name, target, 0.0, e.cur, e.hint.trim()), nextId = s.nextId + 1)
            else s.copy(goals = s.goals.map { if (it.id == e.id) it.copy(name = name, target = target, hint = e.hint.trim()) else it })
        }
        goalEdit = null
        flash(if (e.id == null) "Цель «$name» создана" else "Цель сохранена")
    }

    fun askDeleteGoal(id: String) {
        val g = store.current.goals.firstOrNull { it.id == id } ?: return
        confirm = Confirm("Удалить цель", "Цель «${g.name}» исчезнет. Операции взносов останутся в истории.", "Удалить") {
            store.update { s -> s.copy(goals = s.goals.filterNot { it.id == id }) }
            goalEdit = null
            flash("Цель удалена")
        }
    }

    fun goalPresets(cur: String): List<Double> = when (cur) {
        "USD", "EUR" -> listOf(10.0, 50.0, 100.0, 250.0)
        "KZT" -> listOf(5000.0, 10000.0, 25000.0, 50000.0)
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
        if (c.balance(a) < debit) return flash("На «${a.name}» не хватает средств")
        store.update { s ->
            s.copy(
                txs = listOf(Tx(s.nextId, c.todayDay, "На цель: ${g.name}", CAT_GOAL, a.id, -debit, goal = g.id)) + s.txs,
                nextId = s.nextId + 1,
                goals = s.goals.map { if (it.id == g.id) it.copy(saved = it.saved + gs.amount) else it },
            )
        }
        goalSheet = null
        flash("Отложено ${c.fmt(gs.amount, g.cur)} на «${g.name}»")
    }

    // ——— настройки и данные ———

    fun setRemind(on: Boolean) {
        settings { it.copy(remind = on) }
        Schedules.syncReminder(ctx, on, store.current.settings.remindHour)
        if (on) flash("Напомню в ${store.current.settings.remindHour}:00")
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
        confirm = Confirm("Демо-данные", "Текущие операции, счета и цели заменятся примерами. Сначала можно сделать резервную копию.", "Заменить") {
            store.replace(Demo.create().copy(settings = store.current.settings.copy(onboarded = true)))
            flash("Загружены демо-данные")
        }
    }

    fun askClearAll() {
        confirm = Confirm("Очистить данные", "Удалятся все операции, счета, цели и свои категории. Настройки останутся.", "Очистить") {
            store.replace(Demo.empty(store.current.settings))
            flash("Данные очищены")
        }
    }

    // ——— ИИ-советник ———

    private fun keyName(p: String) = "key_$p"

    fun openAdvisor(withReport: Boolean = false) {
        advisorOpen = true
        apiKeyInput = secure.get(keyName(store.current.settings.aiProvider))
        if (withReport) {
            settings { it.copy(aiSets = it.aiSets + "report") }
            if (question.isBlank()) question = "Объясни этот отчёт и скажи, где я перетрачиваю."
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
        if ("accounts" in s.aiSets) {
            parts += "СЧЕТА (основная валюта ${c.main}):\n" + d.accounts.joinToString("\n") { a ->
                val b = c.balances[a.id] ?: 0.0
                "- ${a.name}: ${c.fmt(b, a.cur)}" + (if (a.cur == c.main) "" else " ≈ ${c.fmtMain(c.toMain(b, a.cur))}")
            }
        }
        val r = c.range(period, periodOffset)
        if ("ops" in s.aiSets) {
            val within = d.txs.filter { it.date in r.from..r.to }.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.id })
            parts += "ОПЕРАЦИИ ${r.note} (${r.title}), всего ${within.size}:\n" + within.take(80).joinToString("\n") { t ->
                "- ${c.dayLabel(t.date)} · ${t.title} · ${c.cat(t.cat).name} · ${Currencies.fmtSigned(t.amount, c.accCur(t.acc))}"
            }
        }
        if ("budgets" in s.aiSets) {
            parts += "БЮДЖЕТЫ месяца (до конца месяца ${c.daysLeft} дн.):\n" + c.budgets.joinToString("\n") { "- ${it.cat.name}: ${c.fmtMain(it.spent)} из ${c.fmtMain(it.limit)}" }
        }
        if ("goals" in s.aiSets && d.goals.isNotEmpty()) {
            parts += "ЦЕЛИ:\n" + d.goals.joinToString("\n") { "- ${it.name}: ${c.fmt(it.saved, it.cur)} из ${c.fmt(it.target, it.cur)}" }
        }
        if ("report" in s.aiSets) {
            parts += "ОТЧЁТ (${r.title}, ${cut.title.lowercase()}):\n" + c.breakdown(r, cut).joinToString("\n") { "- ${it.name}: ${c.fmtMain(it.value)} (${it.pct})" }
            c.compare(period, periodOffset)?.let { (prev, delta) ->
                parts += "СРАВНЕНИЕ: за тот же отрезок прошлого периода ${c.fmtMain(prev)}, изменение ${delta.roundToInt()}%."
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
        val lines = mutableListOf("Разбор ${r.note} (${r.title}), всего расходов ${c.fmtMain(total)}.")
        if (top != null && total > 0) {
            lines += "1. Основная статья — «${c.cat(top.key).name}»: ${c.fmtMain(top.value)}, это ${(top.value / total * 100).roundToInt()}% всех трат." +
                (second?.let { " Вторая — «${c.cat(it.key).name}» (${c.fmtMain(it.value)})." } ?: "")
        }
        val over = c.budgets.filter { it.over }
        lines += "2. " + if (over.isNotEmpty()) {
            "Превышены лимиты: " + over.joinToString { "${it.cat.name} (+${c.fmtMain(it.spent - it.limit)})" } + ". Верните их в рамки в первую очередь."
        } else {
            "Лимиты месяца соблюдены — можно поднять цель по накоплениям."
        }
        lines += "3. Если срезать «${top?.let { c.cat(it.key).name } ?: "крупную статью"}» на 15%, освободится примерно ${c.fmtMain((top?.value ?: 0.0) * 0.15)}. Это разумный первый шаг."
        lines += "4. " + if (c.d.accounts.map { it.cur }.distinct().size > 1) {
            "Мультивалютность: держите подушку в той валюте, в которой тратите, чтобы не терять на конвертации."
        } else {
            "Откладывайте фиксированную сумму сразу после зарплаты — так цели растут без усилий."
        }
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
        if (question.isBlank()) return flash("Напишите вопрос агенту")
        val s = store.current.settings
        if (s.aiSets.isEmpty()) return flash("Выберите данные для отправки")
        val payload = buildPayload()
        val prompt = "Ты финансовый советник. Данные пользователя:\n\n$payload\n\nВопрос: $question\n\n" +
            "Ответь по-русски, коротко, 3-5 пунктов с конкретными суммами. Только простой текст: без Markdown, без заголовков, " +
            "без звёздочек и решёток. Пункты нумеруй как «1.», «2.»."
        val p = Ai.provider(s.aiProvider)
        val model = modelFor(s)
        val key = secure.get(keyName(p.key))
        asking = true
        answer = ""
        answerMeta = "≈ ${prompt.length / 3} токенов"
        viewModelScope.launch {
            try {
                if (p.needsKey && key.isBlank()) {
                    delay(500)
                    answer = localAdvice()
                    answerFrom = "офлайн-разбор"
                } else {
                    answer = stripMd(Ai.ask(p.key, model, key, s.customEndpoint, prompt))
                    answerFrom = "${p.name} · $model"
                }
            } catch (e: AiError) {
                answer = (e.message ?: "Ошибка") + "\n\nПока — офлайн-разбор:\n" + localAdvice()
                answerFrom = "ошибка запроса"
            } catch (e: Exception) {
                answer = "Не удалось получить ответ: ${e.message}\n\nПока — офлайн-разбор:\n" + localAdvice()
                answerFrom = "ошибка запроса"
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
                if (token == null) throw IllegalStateException("Google не выдал токен")
                runDrive(token, action)
            } catch (e: ApiException) {
                driveBusy = false
                flash("Google: ошибка авторизации (${e.statusCode}). Проверьте OAuth-клиент в Google Cloud.")
            } catch (e: Exception) {
                driveBusy = false
                flash("Google Диск: ${e.message}")
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
                flash("Доступ к Google Диску не выдан")
            }
        }
    }

    fun onAuthCancelled() {
        pendingDrive = null
        driveBusy = false
        flash("Подключение Google Диска отменено")
    }

    private suspend fun runDrive(token: String, action: suspend (String) -> Unit) {
        try {
            action(token)
            if (!store.current.settings.driveLinked) {
                settings { it.copy(driveLinked = true) }
                Schedules.syncAutoBackup(ctx, store.current.settings.autoBackup)
            }
        } catch (e: Exception) {
            flash("Google Диск: ${e.message}")
        } finally {
            driveBusy = false
        }
    }

    fun backupNow() = driveAction { t ->
        val name = DriveBackup.upload(t, store.exportJson())
        DriveBackup.prune(t, 10)
        settings { it.copy(lastBackupAt = System.currentTimeMillis()) }
        driveList = DriveBackup.list(t)
        flash("Копия сохранена: $name")
    }

    fun refreshBackups() = driveAction { t -> driveList = DriveBackup.list(t) }

    fun askRestore(b: RemoteBackup) {
        confirm = Confirm(
            "Восстановить копию",
            "Все текущие данные заменятся копией от ${DriveBackup.formatTime(b.created)}. Текущее состояние сохранится на телефоне в before-restore.json.",
            "Восстановить",
        ) {
            driveAction { t ->
                val restored = store.parseBackup(DriveBackup.download(t, b.id))
                File(ctx.filesDir, "before-restore.json").writeText(store.exportJson())
                val keep = store.current.settings
                store.replace(restored.copy(settings = restored.settings.copy(onboarded = true, driveLinked = true, autoBackup = keep.autoBackup, lastBackupAt = keep.lastBackupAt)))
                flash("Данные восстановлены из копии")
            }
        }
    }

    fun unlinkDrive() {
        settings { it.copy(driveLinked = false, autoBackup = false) }
        Schedules.syncAutoBackup(ctx, false)
        driveList = emptyList()
        flash("Google Диск отключён в приложении")
    }
}
