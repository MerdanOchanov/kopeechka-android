package app.kopeechka.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.AppViewModel
import app.kopeechka.finance.CurSheet
import app.kopeechka.finance.Page
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.RateMode
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.decimalString
import app.kopeechka.finance.data.debtStats
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.net.Ai
import app.kopeechka.finance.net.backupMillis
import app.kopeechka.finance.net.formatBackupTime
import app.kopeechka.finance.ui.theme.T
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private fun Modifier.dashed(color: Color) = drawBehind {
    drawRect(
        color,
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

@Composable
fun AddButton(text: String, onClick: () -> Unit) {
    val col = T.c
    Box(
        Modifier
            .fillMaxWidth()
            .dashed(col.n400)
            .tap(onClick)
            .padding(13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = T.h(13.sp, col.a700, 0.1.em))
    }
}

/** Сетка валют по четыре в ряд: включённые в настройках. */
@Composable
fun CurrencyGrid(codes: List<String>, selected: String?, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        codes.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { code ->
                    val info = Currencies.info(code)
                    val st = opt(code == selected)
                    Column(
                        Modifier
                            .weight(1f)
                            .background(st.bg)
                            .hairline(st.border)
                            .tap { onPick(code) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(info.sym, style = T.h(17.sp, st.fg), maxLines = 1)
                        Text(code, style = T.b(9.5.sp, st.fg, 0.1.em), maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Курс в поле ввода: без «1.09E-5» и с разделителем по языку. */
fun fmtRate(v: Double): String {
    if (v == v.roundToLong().toDouble()) return v.roundToLong().toString()
    val scale = when {
        abs(v) >= 1 -> 4
        abs(v) >= 0.01 -> 5
        else -> 8
    }
    val s = decimalString(v, scale)
    return if (Currencies.lang.code == "en") s else s.replace('.', ',')
}

@Composable
fun SettingsScreen(vm: AppViewModel, c: Calc, onEnableReminder: () -> Unit) {
    val col = T.c
    val l = T.l
    val s = c.s
    val provider = Ai.provider(s.aiProvider)
    ScreenColumn(gap = 22.dp) {
        Column {
            SectionTitle(l.t("set.sections"))
            Spacer(Modifier.height(4.dp))
            NavRow(Icons.Wallet, l.t("set.accounts"), "${l.n(c.d.accounts.size, "account")} · ${c.fmtMain(c.totalMain)}") { vm.openPage(Page.ACCOUNTS) }
            NavRow(Icons.Tags, l.t("set.categories"), l.t("set.categoriesSub", l.n(c.d.categories.size, "category"))) { vm.openPage(Page.CATEGORIES) }
            NavRow(
                Icons.Target,
                l.t("set.goals"),
                if (c.d.goals.isEmpty()) l.t("set.goalsEmpty") else l.n(c.d.goals.size, "goal"),
            ) { vm.openPage(Page.GOALS) }
            NavRow(Icons.Rate, l.t("set.currencies"), l.t("set.currenciesSub", l.n(c.currencies.size, "currency"), c.main)) { vm.openPage(Page.CURRENCIES) }
            NavRow(
                Icons.Cloud,
                l.t("set.backup"),
                when {
                    !s.driveLinked -> l.t("backup.driveNotLinked")
                    s.lastBackupAt > 0 -> l.t("backup.drive") + " · " + formatBackupTime(s.lastBackupAt, l)
                    else -> l.t("backup.driveLinked")
                },
            ) { vm.openPage(Page.BACKUP) }
            NavRow(
                Icons.Clock,
                l.t("debt.title"),
                if (c.d.debts.isEmpty()) l.t("debt.navEmpty")
                else l.t("debt.navSub", c.fmtMain(c.debtStats().lentLeft), c.fmtMain(c.debtStats().borrowedLeft)),
            ) { vm.openPage(Page.DEBTS) }
            if (s.business) {
                NavRow(
                    Icons.Bag,
                    l.t("biz.title"),
                    l.t("biz.navSub", l.n(c.d.orders.size, "order"), l.n(c.d.customers.size, "customer")),
                ) { vm.openPage(Page.BUSINESS) }
            }
            NavRow(
                Icons.Spark,
                l.t("set.advisor"),
                provider.name(l) + " · " + if (vm.hasKey(provider.key) || !provider.needsKey) l.t("common.ready") else l.t("set.advisorNoKey"),
            ) { vm.openAdvisor() }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(l.t("set.lang"))
            Muted(l.t("set.langNote"), 11.5f, color = col.n700)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Lang.CODES.forEach { code ->
                    val st = opt(s.lang == code)
                    Box(
                        Modifier
                            .weight(1f)
                            .background(st.bg)
                            .hairline(st.border)
                            .tap { vm.setLang(code) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (code == "auto") l.t("set.lang.auto") else Lang.title(code),
                            style = T.b(11.5.sp, st.fg),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(l.t("set.mainCur"))
            Muted(l.t("set.mainCurNote"), 11.5f, color = col.n700)
            CurrencyGrid(c.currencies, c.main) { vm.setMainCur(it) }
            GhostButton(l.t("set.addCur"), { vm.currencyPicker = true }, size = 12)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle(l.t("set.accCur"))
            c.d.accounts.forEach { a ->
                val bal = c.balances[a.id] ?: 0.0
                SettingRow(
                    a.name,
                    "${c.fmt(bal, a.cur)} · " + if (a.cur == c.main) l.t("set.inMainCur") else "≈ ${c.fmtMain(c.toMain(bal, a.cur))}",
                ) {
                    SecondaryButton("${a.cur} ${Currencies.sym(a.cur)}", { vm.curSheet = CurSheet.Acc(a.id) })
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(l.t("biz.title"))
            Muted(l.t("biz.settingsNote"), 11.5f, color = col.n700)
            SettingRow(l.t("biz.enable"), l.t("biz.enableSub")) {
                Toggle(s.business) { vm.setBusiness(it) }
            }
            if (s.business) {
                SecondaryButton(l.t("biz.openPage"), { vm.openPage(Page.BUSINESS) }, Modifier.fillMaxWidth(), size = 13, upper = true)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(l.t("sync.title"))
            Muted(l.t("sync.settingsNote"), 11.5f, color = col.n700)
            SecondaryButton(
                if (c.d.space == null) l.t("sync.openPage") else l.t("sync.openPageOn"),
                { vm.openPage(Page.SYNC) },
                Modifier.fillMaxWidth(),
                size = 13,
                upper = true,
            )
        }

        if (vm.canReadSms) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(l.t("sms.title"))
                Muted(l.t("sms.settingsNote"), 11.5f, color = col.n700)
                SettingRow(l.t("sms.enable"), l.t("sms.enableSub")) {
                    Toggle(s.sms) { vm.setSmsModule(it) }
                }
                if (s.sms) {
                    SecondaryButton(l.t("sms.openPage"), { vm.openPage(Page.SMS) }, Modifier.fillMaxWidth(), size = 13, upper = true)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(l.t("set.money"))
            SettingRow(l.t("set.allowNegative"), l.t("set.allowNegativeSub")) {
                Toggle(s.allowNegative) { v -> vm.settings { it.copy(allowNegative = v) } }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(l.t("set.look"))
            Segments(listOf(l.t("set.light"), l.t("set.dark")), if (s.dark) 1 else 0, { vm.setDark(it == 1) })
            SettingRow(l.t("set.kopecks"), l.t("set.kopecksSub")) {
                Toggle(s.showKopecks) { v -> vm.settings { it.copy(showKopecks = v) } }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(l.t("set.reminders"))
            SettingRow(l.t("set.remind"), l.t("set.remindSub", s.remindHour)) {
                Toggle(s.remind) { on -> if (on) onEnableReminder() else vm.setRemind(false) }
            }
            if (s.remind) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(19, 20, 21, 22).forEach { h -> Chip("$h:00", s.remindHour == h, { vm.setRemindHour(h) }) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(l.t("set.profile"))
            var name by remember { mutableStateOf(s.userName) }
            Field(l.t("set.name"), name, { name = it; vm.settings { st -> st.copy(userName = it) } }, placeholder = l.t("set.nameHint"))
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(l.t("csv.section"))
            SecondaryButton(l.t("csv.export"), { vm.askExportCsv() }, Modifier.fillMaxWidth(), size = 13, upper = true)
            SecondaryButton(l.t("csv.import"), { vm.askImportCsv() }, Modifier.fillMaxWidth(), size = 13, upper = true)
            GhostButton(l.t("csv.template"), { vm.saveTemplate() }, size = 12)
            Muted(l.t("csv.note"), 10.5f, color = col.n700)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(l.t("set.data"))
            SecondaryButton(l.t("set.loadDemo"), { vm.askLoadDemo() }, Modifier.fillMaxWidth(), size = 13, upper = true)
            DangerButton(l.t("set.clearAll"), { vm.askClearAll() })
            Muted(l.t("set.about", vm.version), 10.5f)
        }
    }
}

// ——— Валюты и курсы ———

@Composable
fun CurrenciesPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    ScreenColumn(gap = 16.dp) {
        PageHeader(l.t("cur.title")) { vm.page = null }
        Muted(l.t("cur.note", Currencies.info(c.main).name), 11.5f, color = col.n700)

        // два курса: там, где официальный и рыночный расходятся в разы
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("rate.modeTitle"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(l.t("rate.bank"), c.s.rateMode != RateMode.MARKET, { vm.setRateMode(RateMode.BANK) }, modifier = Modifier.weight(1f))
                Chip(l.t("rate.market"), c.s.rateMode == RateMode.MARKET, { vm.setRateMode(RateMode.MARKET) }, modifier = Modifier.weight(1f))
            }
            Muted(
                if (c.s.rateMode == RateMode.MARKET && c.s.marketRates.isEmpty()) l.t("rate.marketEmpty") else l.t("rate.modeNote"),
                11f,
            )
        }

        Column {
            c.currencies.forEach { code ->
                val info = Currencies.info(code)
                val isBase = code == c.main
                val used = c.d.accounts.count { it.cur == code } + c.d.goals.count { it.cur == code }
                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(34.dp).hairline(col.divider), contentAlignment = Alignment.Center) {
                            Text(info.sym, style = T.h(15.sp, col.a700), maxLines = 1)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(info.name.replaceFirstChar { it.uppercase() }, style = T.b(14.sp, col.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                buildList {
                                    add(code)
                                    if (code == c.main) add(l.t("cur.main"))
                                    if (used > 0) add(l.t("cur.usedIn", l.n(used, "place")))
                                    if (!Currencies.inCatalog(code)) add(l.t("cur.own"))
                                }.joinToString(" · "),
                                style = T.b(11.sp, col.n600),
                            )
                        }
                        if (isBase) {
                            Text(l.t("cur.base"), style = T.b(11.5.sp, col.n600))
                        } else {
                            RateField(vm, code, c.bankRate(code), c.main)
                        }
                    }
                    if (!isBase) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                l.t("rate.marketFor", code),
                                style = T.b(11.5.sp, col.n600),
                                modifier = Modifier.weight(1f),
                            )
                            MarketRateField(vm, code, c.s.marketRates[code], c.main)
                        }
                    }
                    if (!isBase && code != c.main && used == 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            GhostButton(l.t("cur.remove"), { vm.removeCurrency(code) }, size = 11)
                        }
                    }
                }
                SoftDivider()
            }
        }
        AddButton(l.t("cur.add")) { vm.currencyPicker = true }
        Muted(l.t("cur.footer"), 11f)
    }
}

/** Рыночный курс: пустое поле — совпадает с банковским. */
@Composable
private fun MarketRateField(vm: AppViewModel, code: String, rate: Double?, mainCur: String) {
    val col = T.c
    var text by remember(code) { mutableStateOf(rate?.let { fmtRate(it) }.orEmpty()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(104.dp)) {
            Field(null, text, { text = it; vm.setMarketRate(code, it) }, numeric = true, placeholder = T.l.t("rate.same"))
        }
        Text(Currencies.sym(mainCur), style = T.b(14.sp, col.n700))
    }
}

@Composable
private fun RateField(vm: AppViewModel, code: String, rate: Double, mainCur: String) {
    val col = T.c
    var text by remember(code) { mutableStateOf(fmtRate(rate)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(104.dp)) {
            Field(null, text, { text = it; vm.setRate(code, it) }, numeric = true, placeholder = "0")
        }
        Text(Currencies.sym(mainCur), style = T.b(14.sp, col.n700))
    }
}

// ——— Счета ———

@Composable
fun AccountsPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    ScreenColumn(gap = 16.dp) {
        PageHeader(l.t("acc.title")) { vm.page = null }
        Blueprint(Modifier.fillMaxWidth()) {
            Kicker(l.t("acc.sum"))
            Spacer(Modifier.height(3.dp))
            Text(c.fmtMain(c.totalMain), style = T.h(26.sp, if (c.totalMain < 0) col.danger else col.text))
        }
        Column {
            c.d.accounts.forEach { a ->
                val bal = c.balances[a.id] ?: 0.0
                Row(
                    Modifier.fillMaxWidth().tap { vm.openAccEdit(a) }.padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CodeBox(a.name.filter { it.isLetter() }.take(2).uppercase().ifBlank { l.t("acc.short") }, 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = T.b(14.sp, col.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(a.type, a.mask, a.cur).filter { it.isNotBlank() }.joinToString(" · "), style = T.b(11.sp, col.n600))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(c.fmt(bal, a.cur), style = T.h(15.sp, if (bal < 0) col.danger else col.text))
                        Text(
                            if (a.inTotal) l.t("acc.inTotal") else l.t("acc.notInTotal"),
                            style = T.b(11.sp, if (a.inTotal) col.a700 else col.n500),
                            modifier = Modifier.tap { vm.toggleInTotal(a.id) }.padding(top = 2.dp),
                        )
                    }
                }
                SoftDivider()
            }
        }
        AddButton(l.t("acc.add")) { vm.openAccEdit(null) }
        Muted(l.t("acc.note"), 11f)
    }
}

// ——— Категории ———

@Composable
fun CategoriesPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val spent = c.spentBy(c.monthTx)
    val earned = c.monthTx.filter { it.amount > 0 }.groupBy { it.cat }.mapValues { e -> e.value.sumOf { c.txMain(it) } }
    ScreenColumn(gap = 16.dp) {
        PageHeader(l.t("cat.title")) { vm.page = null }
        Muted(l.t("cat.note"), 11f)
        listOf(false, true).forEach { income ->
            Column {
                SectionTitle(if (income) l.t("cat.incomes") else l.t("cat.expenses")) { Muted(l.t("cat.thisMonth")) }
                Spacer(Modifier.height(4.dp))
                c.d.categories.filter { it.income == income }.forEach { cat ->
                    Row(
                        Modifier.fillMaxWidth().tap { vm.openCatEdit(cat) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CodeBox(cat.code, 30.dp, catColor(c, cat.id), tinted = true)
                        Column(Modifier.weight(1f)) {
                            Text(cat.name, style = T.b(14.sp, col.text))
                            if (!income) {
                                Text(
                                    if (cat.limitBase > 0) l.t("cat.limitMonth", c.fmtMain(c.limitMain(cat))) else l.t("cat.noLimit"),
                                    style = T.b(11.sp, col.n600),
                                )
                            }
                        }
                        val v = if (income) earned[cat.id] else spent[cat.id]
                        Text(v?.let { c.fmtMain(it) } ?: l.t("common.dash"), style = T.h(13.sp, col.n700))
                    }
                    SoftDivider()
                }
            }
            AddButton(if (income) l.t("cat.addIncome") else l.t("cat.addExpense")) { vm.openCatEdit(null, income) }
        }
    }
}

// ——— Цели ———

@Composable
fun GoalsPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    ScreenColumn(gap = 18.dp) {
        PageHeader(l.t("goal.title")) { vm.page = null }
        if (c.d.goals.isEmpty()) Muted(l.t("goal.empty"), 12f)
        c.d.goals.forEach { g ->
            val pct = if (g.target > 0) (g.saved / g.target * 100).roundToInt() else 0
            Blueprint(Modifier.fillMaxWidth(), PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.name, style = T.h(17.sp, col.text), modifier = Modifier.weight(1f).tap { vm.openGoalEdit(g) })
                    Text("$pct%", style = T.h(13.sp, col.a700))
                }
                Spacer(Modifier.height(10.dp))
                ProgressLine(pct / 100f, if (pct >= 100) col.a900 else col.a600, 8.dp)
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(c.fmt(g.saved, g.cur), style = T.h(13.sp, col.text))
                    Spacer(Modifier.weight(1f))
                    Text(l.t("goal.target", c.fmt(g.target, g.cur)), style = T.b(12.sp, col.n600))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        g.hint.ifBlank {
                            if (pct >= 100) l.t("goal.done") else l.t("goal.left", c.fmt(max(0.0, g.target - g.saved), g.cur))
                        },
                        style = T.b(11.5.sp, col.n600),
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(l.t("goal.put"), { vm.openGoalSheet(g.id) })
                }
            }
        }
        AddButton(l.t("goal.add")) { vm.openGoalEdit(null) }
        Muted(l.t("goal.note"), 11f)
    }
}

// ——— Резервная копия ———

@Composable
fun BackupPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val s = c.s
    LaunchedEffect(Unit) { if (s.driveLinked && vm.driveList.isEmpty()) vm.refreshBackups() }
    ScreenColumn(gap = 16.dp) {
        PageHeader(l.t("backup.title")) { vm.page = null }
        Blueprint(Modifier.fillMaxWidth()) {
            Kicker(l.t("backup.drive"))
            Spacer(Modifier.height(4.dp))
            Text(if (s.driveLinked) l.t("backup.linked") else l.t("backup.notLinked"), style = T.h(22.sp, col.text))
            Spacer(Modifier.height(2.dp))
            Text(
                if (s.lastBackupAt > 0) l.t("backup.last", formatBackupTime(s.lastBackupAt, l)) else l.t("backup.never"),
                style = T.b(11.5.sp, col.n700),
            )
        }
        Muted(l.t("backup.note"), 11.5f, color = col.n700)
        PrimaryButton(
            when {
                vm.driveBusy -> l.t("common.wait")
                s.driveLinked -> l.t("backup.now")
                else -> l.t("backup.connect")
            },
            { vm.backupNow() },
            enabled = !vm.driveBusy,
        )
        SettingRow(l.t("backup.auto"), if (s.driveLinked) l.t("backup.autoOn") else l.t("backup.autoOff")) {
            Toggle(s.autoBackup) { vm.setAutoBackup(it) }
        }
        SectionTitle(l.t("backup.list")) { GhostButton(l.t("common.refresh"), { vm.refreshBackups() }) }
        if (vm.driveList.isEmpty()) Muted(if (s.driveLinked) l.t("backup.listEmpty") else l.t("backup.listNotLinked"), 12f)
        Column {
            vm.driveList.forEach { b ->
                SettingRow(formatBackupTime(backupMillis(b), l), "${b.name} · ${max(1L, b.size / 1024)} KB") {
                    SecondaryButton(l.t("common.restore"), { vm.askRestore(b) })
                }
            }
        }
        if (s.driveLinked) GhostButton(l.t("backup.unlink"), { vm.unlinkDrive() })
    }
}
