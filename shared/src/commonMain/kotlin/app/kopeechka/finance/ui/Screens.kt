package app.kopeechka.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.AppViewModel
import app.kopeechka.finance.CurSheet
import app.kopeechka.finance.Kind
import app.kopeechka.finance.Page
import app.kopeechka.finance.Tab
import app.kopeechka.finance.data.Bar
import app.kopeechka.finance.data.BudgetRow
import app.kopeechka.finance.data.CAT_TRANSFER
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.biz
import app.kopeechka.finance.data.debtDone
import app.kopeechka.finance.data.bizCompare
import app.kopeechka.finance.data.bizFacts
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.Cut
import app.kopeechka.finance.data.Goal
import app.kopeechka.finance.data.Palette
import app.kopeechka.finance.data.Period
import app.kopeechka.finance.data.Tx
import app.kopeechka.finance.ui.theme.Shades
import app.kopeechka.finance.ui.theme.T
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

fun hexColor(hex: String?, fallback: Color): Color {
    val v = hex?.let { Palette.parse(it) } ?: return fallback
    return Color(v)
}

@Composable
fun catColor(c: Calc, catId: String): Color = hexColor(c.colorOf(catId), T.c.a600)

@Composable
fun ScreenColumn(gap: Dp = 18.dp, top: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = top, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
fun <I> CellGrid(items: List<I>, cols: Int = 3, onClick: ((I) -> Unit)? = null, cell: @Composable ColumnScope.(I) -> Unit) {
    val col = T.c
    Column(Modifier.fillMaxWidth().background(col.divider).hairline(col.divider)) {
        items.chunked(cols).forEachIndexed { r, row ->
            if (r > 0) Spacer(Modifier.height(1.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                row.forEach { item ->
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(col.bg)
                            .then(if (onClick != null) Modifier.tap { onClick(item) } else Modifier)
                            .padding(horizontal = 9.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) { cell(item) }
                }
                repeat(cols - row.size) { Box(Modifier.weight(1f).fillMaxHeight().background(col.bg)) }
            }
        }
    }
}

@Composable
fun TxItem(vm: AppViewModel, c: Calc, t: Tx, topBorder: Boolean = false) {
    val col = T.c
    val l = T.l
    val cat = c.cat(t.cat)
    val a = c.acc(t.acc)
    val cur = a?.cur ?: c.main
    val service = !c.isReal(t)
    val alt = if (cur == c.main) "" else "≈ " + c.fmtMain(c.toMain(abs(t.amount), cur))
    val amount = (if (t.cat == CAT_TRANSFER) "" else if (t.amount > 0) "+" else "−") + c.fmt(abs(t.amount), cur)
    val tone = when {
        service -> col.n600
        t.amount > 0 -> col.a700
        else -> col.text
    }
    val meta = if (t.cat == CAT_TRANSFER) {
        l.t("ops.transferMeta", a?.name ?: "—", c.acc(t.toAcc)?.name ?: "—")
    } else {
        cat.name + " · " + (a?.name ?: "—")
    }
    TxLine(cat.code, t.title, meta, amount, alt, tone, topBorder, catColor(c, t.cat), !service) { vm.openEdit(t) }
}

@Composable
fun budgetTone(b: BudgetRow, c: Calc): Color {
    val col = T.c
    return when {
        b.over -> col.danger
        else -> catColor(c, b.cat.id)
    }
}

/** Горизонтальная колонка столбиков отчёта. */
@Composable
fun BarRow(bars: List<Bar>, height: Dp = 136.dp, fill: Color = T.c.a700, border: Color = T.c.a700, showValues: Boolean = true) {
    val col = T.c
    val mx = bars.maxOfOrNull { it.value } ?: 0.0
    val gap = if (bars.size > 7) 4.dp else 6.dp
    val barHeight = height - if (showValues) 20.dp else 0.dp
    Column {
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
            bars.forEach { b ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Bottom),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showValues) Text(Currencies.short(b.value), style = T.h(9.5.sp, col.n700), maxLines = 1)
                    val frac = if (mx > 0) (b.value / mx).toFloat() else 0f
                    val h = 6.dp + (barHeight - 6.dp) * frac
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(h)
                            .background(if (b.highlight) fill else Color.Transparent)
                            .hairline(border),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Divider()
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            bars.forEach { b ->
                Text(b.label.uppercase(), style = T.b(9.5.sp, col.n600, 0.06.em), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** Карточка-отчёт на главном экране: нажатие открывает нужный разрез в «Отчётах». */
@Composable
fun ReportCard(title: String, hint: String, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val col = T.c
    Blueprint(Modifier.fillMaxWidth().tap(onClick), PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = T.h(13.sp, col.text, 0.1.em), modifier = Modifier.weight(1f))
            Text(hint, style = T.b(10.5.sp, col.n600))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Chevron, null, tint = col.a700, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

// ——— Обзор ———

@Composable
fun HomeScreen(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    ScreenColumn(gap = 20.dp, top = 18.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(col.hero)
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
        ) {
            val hello = c.s.userName.trim()
            Text(
                ((if (hello.isNotEmpty()) "$hello · " else "") + l.t("home.total", Currencies.info(c.main).name)).uppercase(),
                style = T.b(10.sp, col.a300, 0.18.em),
            )
            Spacer(Modifier.height(4.dp))
            Text(c.fmtMain(c.totalMain), style = T.h(38.sp, col.onAccent, (-0.01).em, 40.sp))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(col.hairline))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                HeroStat(l.t("home.income"), c.fmtMain(c.monthIncome))
                HeroStat(l.t("home.expense"), c.fmtMain(c.monthExpense))
                Spacer(Modifier.weight(1f))
                val over = c.freeRaw < 0
                HeroStat(
                    if (over) l.t("home.over") else l.t("home.free"),
                    c.fmtMain(abs(c.freeRaw)),
                    end = true,
                    tone = if (over) OverTone else col.onAccent,
                )
            }
        }

        InboxBar(vm, c)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(l.t("home.accounts")) { GhostButton(l.t("home.transfer"), { vm.openAdd(Kind.TRANSFER) }) }
            CellGrid(c.d.accounts, 3, onClick = { vm.curSheet = CurSheet.Acc(it.id) }) { a ->
                val bal = c.balances[a.id] ?: 0.0
                Text(a.name.uppercase(), style = T.b(9.5.sp, col.n600, 0.12.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    c.fmt(bal, a.cur),
                    style = T.h(15.sp, if (bal < 0) col.danger else if (a.inTotal) col.text else col.n600),
                    maxLines = 1,
                )
                Text(
                    if (a.cur == c.main) a.cur else "${a.cur} ≈ ${c.fmtMain(c.toMain(bal, a.cur))}",
                    style = T.b(9.5.sp, col.a700, 0.1.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (c.s.business) BusinessCard(vm, c)

        if (c.d.debts.any { !c.debtDone(it) }) DebtsCard(vm, c)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(l.t("home.budget")) { Muted(l.t("home.daysLeft", l.n(c.daysLeft, "day"))) }
            val top = c.budgets.sortedByDescending { it.rawPct }.take(3)
            if (top.isEmpty()) Muted(l.t("home.noLimits"), 12f)
            top.forEach { BudgetMini(c, it) }
        }

        // Отчёты прямо на главном: каждая карточка открывает свой разрез
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(l.t("home.reports")) { GhostButton(l.t("common.all"), { vm.goReport(Period.MONTH, Cut.CATS) }) }
            SpendByCategoryCard(vm, c)
            WeekDaysCard(vm, c)
            MonthsCard(vm, c)
            IncomeExpenseCard(vm, c)
        }

        if (c.d.goals.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(l.t("home.goals")) { GhostButton(l.t("common.all"), { vm.openPage(Page.GOALS) }) }
                c.d.goals.take(2).forEach { g -> GoalMini(c, g) { vm.openGoalSheet(g.id) } }
            }
        }

        Column {
            SectionTitle(l.t("home.recent")) { GhostButton(l.t("common.all"), { vm.go(Tab.OPS) }) }
            Spacer(Modifier.height(6.dp))
            val recent = c.d.txs.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.id }).take(4)
            if (recent.isEmpty()) Muted(l.t("home.empty"), 12f)
            recent.forEach { TxItem(vm, c, it) }
        }
    }
}

@Composable
private fun SpendByCategoryCard(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val spent = c.spentBy(c.monthTx).entries.sortedByDescending { it.value }.take(5)
    val total = c.monthExpense
    ReportCard(l.t("home.cats.title"), l.t("home.cats.hint"), { vm.goReport(Period.MONTH, Cut.CATS) }) {
        if (spent.isEmpty()) {
            Muted(l.t("home.cats.empty"), 12f)
            return@ReportCard
        }
        val mx = spent.first().value
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            spent.forEach { (id, v) ->
                val color = catColor(c, id)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(9.dp).background(color))
                        Text(c.cat(id).name, style = T.b(13.sp, col.text), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${(if (total > 0) v / total * 100 else 0.0).roundToInt()}%", style = T.b(11.sp, col.n600))
                        Text(c.fmtMain(v), style = T.h(13.sp, col.text))
                    }
                    Box(Modifier.fillMaxWidth().height(5.dp).background(col.n200)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(if (mx > 0) (v / mx).toFloat() else 0f).background(color))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekDaysCard(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val bars = c.weekDays()
    val todayIdx = c.weekday(c.todayDay)
    val todaySpent = bars.getOrNull(todayIdx)?.value ?: 0.0
    val week = bars.sumOf { it.value }
    val avg = if (todayIdx >= 0) week / (todayIdx + 1) else 0.0
    ReportCard(l.t("home.days.title"), l.t("home.days.hint"), { vm.goReport(Period.WEEK, Cut.DAYS) }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Kicker(l.t("home.days.today"))
                Text(c.fmtMain(todaySpent), style = T.h(26.sp, col.text))
            }
            Text(
                if (todaySpent <= avg) l.t("home.days.below", c.fmtMain(avg - todaySpent)) else l.t("home.days.above", c.fmtMain(todaySpent - avg)),
                style = T.b(11.sp, if (todaySpent <= avg) col.a700 else col.danger),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        BarRow(bars, height = 96.dp, showValues = false)
    }
}

@Composable
private fun MonthsCard(vm: AppViewModel, c: Calc) {
    val l = T.l
    val bars = c.lastMonths(6)
    ReportCard(l.t("home.months.title"), l.t("home.months.hint"), { vm.goReport(Period.YEAR, Cut.CATS) }) {
        BarRow(bars, height = 110.dp)
    }
}

@Composable
private fun IncomeExpenseCard(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val inc = c.monthIncome
    val exp = c.monthExpense
    val saldo = inc - exp
    ReportCard(l.t("home.io.title"), l.t("home.io.hint"), { vm.goReport(Period.MONTH, Cut.IO) }) {
        val sum = if (inc + exp > 0) inc + exp else 1.0
        Row(Modifier.fillMaxWidth().height(22.dp).hairline(col.divider)) {
            Box(Modifier.weight((inc / sum).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(col.a600))
            Box(Modifier.width(1.dp).fillMaxHeight().background(col.bg))
            Box(Modifier.weight((exp / sum).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(col.a900))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Kicker(l.t("home.income"))
                Text(c.fmtMain(inc), style = T.h(15.sp, col.a700))
            }
            Column(Modifier.weight(1f)) {
                Kicker(l.t("home.expense"))
                Text(c.fmtMain(exp), style = T.h(15.sp, col.text))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Kicker(l.t("home.saldo"))
                Text(Currencies.fmtSigned(saldo, c.main, c.s.showKopecks), style = T.h(15.sp, if (saldo < 0) col.danger else col.text))
            }
        }
    }
}

/** Перерасход на тёмной шапке: обычный danger на ней читается плохо. */
private val OverTone = Color(0xFFE8A08F)

@Composable
private fun HeroStat(label: String, value: String, end: Boolean = false, tone: Color = T.c.onAccent) {
    val col = T.c
    Column(horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text(label.uppercase(), style = T.b(10.sp, col.a300, 0.14.em))
        Text(value, style = T.h(17.sp, tone), maxLines = 1)
    }
}

@Composable
private fun BudgetMini(c: Calc, b: BudgetRow) {
    val col = T.c
    val l = T.l
    val tone = budgetTone(b, c)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            CodeBox(b.cat.code, 26.dp, catColor(c, b.cat.id), tinted = true)
            Text(b.cat.name, style = T.b(13.5.sp, col.text), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.fmtMain(b.spent), style = T.h(13.sp, tone))
            Text(l.t("common.from", c.fmtMain(b.limit)), style = T.b(11.5.sp, col.n600))
        }
        ProgressLine(b.pct / 100f, tone)
    }
}

@Composable
fun GoalMini(c: Calc, g: Goal, onClick: () -> Unit) {
    val col = T.c
    val l = T.l
    val pct = if (g.target > 0) (g.saved / g.target * 100).roundToInt() else 0
    Column(Modifier.tap(onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.name, style = T.b(13.5.sp, col.text), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$pct%", style = T.h(13.sp, col.a700))
        }
        ProgressLine(pct / 100f, if (pct >= 100) col.a900 else col.a600)
        Text("${c.fmt(g.saved, g.cur)} ${l.t("common.from", c.fmt(g.target, g.cur))}", style = T.b(11.sp, col.n600))
    }
}

// ——— Операции ———

private sealed interface OpsRow {
    data class Header(val day: Long, val total: Double) : OpsRow
    data class Item(val tx: Tx) : OpsRow
}

@Composable
fun OpsScreen(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val filters = listOf("all" to l.t("ops.all"), "out" to l.t("ops.out"), "in" to l.t("ops.in"), "tr" to l.t("ops.transfers"))
    val f = vm.opsFilter
    val list = c.d.txs.filter { t ->
        when {
            f == "out" -> t.amount < 0 && c.isReal(t)
            f == "in" -> t.amount > 0 && c.isReal(t)
            f == "tr" -> !c.isReal(t)
            f.startsWith("cat:") -> t.cat == f.removePrefix("cat:")
            else -> true
        }
    }.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.id })
    val sum = list.filter { c.isReal(it) }.sumOf { c.txMain(it) }
    val rows = buildList {
        list.groupBy { it.date }.forEach { (day, items) ->
            add(OpsRow.Header(day, items.filter { c.isReal(it) }.sumOf { c.txMain(it) }))
            items.forEach { add(OpsRow.Item(it)) }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                Segments(filters.map { it.second }, filters.indexOfFirst { it.first == f }, { vm.opsFilter = filters[it].first }, size = 10.5f, spacing = 0.06f)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.d.categories.filter { !it.income }.forEach { cat ->
                        val on = f == "cat:${cat.id}"
                        Chip(cat.name, on, { vm.opsFilter = if (on) "all" else "cat:${cat.id}" }, code = cat.code, accent = catColor(c, cat.id))
                    }
                }
                Column {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(l.n(list.size, "op"), style = T.b(11.5.sp, col.n700))
                        Spacer(Modifier.weight(1f))
                        Text(Currencies.fmtSigned(sum, c.main, c.s.showKopecks), style = T.b(11.5.sp, col.n700))
                    }
                    Divider()
                }
                if (list.isEmpty()) Muted(l.t("ops.empty"), 13f)
            }
        }
        items(rows, key = { r -> if (r is OpsRow.Header) "h${r.day}" else "t${(r as OpsRow.Item).tx.id}" }) { r ->
            when (r) {
                is OpsRow.Header -> Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text(c.dayLabel(r.day).uppercase(), style = T.h(11.sp, col.a700, 0.14.em))
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (r.total == 0.0) l.t("common.dash") else Currencies.fmtSigned(r.total, c.main, c.s.showKopecks),
                        style = T.b(11.5.sp, col.n600),
                    )
                }
                is OpsRow.Item -> TxItem(vm, c, r.tx, topBorder = true)
            }
        }
    }
}

// ——— Бюджет ———

@Composable
fun BudgetScreen(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    ScreenColumn(gap = 18.dp) {
        Blueprint(Modifier.fillMaxWidth()) {
            Kicker(l.t("budget.limit"))
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.fmtMain(c.monthExpense), style = T.h(30.sp, col.text, lineHeight = 30.sp))
                Text(l.t("common.from", c.fmtMain(c.limitTotal)), style = T.b(13.sp, col.n700), modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(8.dp))
            ProgressLine(if (c.limitTotal > 0) (c.monthExpense / c.limitTotal).toFloat() else 0f, col.a700, 9.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (c.monthExpense > c.limitTotal && c.limitTotal > 0) l.t("budget.over", c.fmtMain(c.monthExpense - c.limitTotal))
                else l.t("budget.perDay", c.fmtMain(c.perDay)),
                style = T.b(11.5.sp, col.n700),
            )
        }
        c.budgets.forEach { b ->
            val tone = budgetTone(b, c)
            Column(Modifier.tap { vm.openCatEdit(b.cat) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CodeBox(b.cat.code, 26.dp, catColor(c, b.cat.id), tinted = true)
                    Text(b.cat.name, style = T.b(13.5.sp, col.text), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (b.over) l.t("budget.overCat", c.fmtMain(b.spent - b.limit)) else l.t("budget.rest", c.fmtMain(b.limit - b.spent)),
                        style = T.b(11.5.sp, if (b.over) col.danger else col.n600),
                    )
                }
                ProgressLine(b.pct / 100f, tone)
                Row(Modifier.fillMaxWidth()) {
                    Text("${c.fmtMain(b.spent)} ${l.t("common.from", c.fmtMain(b.limit))}", style = T.b(11.sp, col.n600))
                    Spacer(Modifier.weight(1f))
                    Text("${b.rawPct.roundToInt()}%", style = T.b(11.sp, col.n600))
                }
            }
        }
        val noLimit = c.d.categories.filter { !it.income && it.limitBase <= 0 }
        if (noLimit.isNotEmpty()) Muted(l.t("budget.noLimit", noLimit.joinToString { it.name }), 11f)
        SecondaryButton(l.t("budget.cats"), { vm.openPage(Page.CATEGORIES) }, Modifier.fillMaxWidth(), size = 13, upper = true)
    }
}

// ——— Отчёты ———

/** Фильтры отчёта: один счёт и одна категория. Свёрнуты, пока не понадобятся. */
@Composable
private fun ReportFilters(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val accName = vm.filterAcc?.let { id -> c.acc(id)?.name } ?: l.t("report.filterAllAccs")
    val catName = vm.filterCat?.let { id -> c.cat(id).name } ?: l.t("report.filterAllCats")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .hairline(if (vm.hasFilters) col.a600 else col.divider)
                    .tap { vm.filtersOpen = !vm.filtersOpen }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Text(
                    if (vm.hasFilters) "$accName · $catName" else l.t("report.filters"),
                    style = T.b(12.sp, if (vm.hasFilters) col.text else col.n600),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (vm.hasFilters) GhostButton(l.t("report.filtersReset"), { vm.resetFilters() }, size = 11)
        }
        if (vm.filtersOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Kicker(l.t("report.filterAcc"))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip(l.t("report.filterAllAccs"), vm.filterAcc == null, { vm.filterAcc = null })
                    c.d.accounts.forEach { a ->
                        Chip(a.name, vm.filterAcc == a.id, { vm.filterAcc = if (vm.filterAcc == a.id) null else a.id })
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Kicker(l.t("report.filterCat"))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip(l.t("report.filterAllCats"), vm.filterCat == null, { vm.filterCat = null })
                    c.d.categories.forEach { cat ->
                        Chip(
                            cat.name,
                            vm.filterCat == cat.id,
                            { vm.filterCat = if (vm.filterCat == cat.id) null else cat.id },
                            code = cat.code,
                            accent = catColor(c, cat.id),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportScreen(vm: AppViewModel, full: Calc) {
    val col = T.c
    val l = T.l
    val p = vm.period
    val cut = vm.cut
    // фильтры сужают данные только этого экрана: остальные считают по полным.
    // В разрезах «Бизнеса» фильтры по счетам и категориям не применяются
    val fc = remember(full.d, vm.filterAcc, vm.filterCat) { Calc(vm.filtered(full.d), full.today, full.l) }
    val c = if (cut.biz) full else fc
    val r = c.range(p, vm.periodOffset)
    val exp = c.expenseIn(r)
    val inc = c.incomeIn(r)
    val bars = c.series(r, p, cut)
    val slices = c.breakdown(r, cut)
    val cmp = if (cut.biz) c.bizCompare(p, vm.periodOffset) else c.compare(p, vm.periodOffset)

    ScreenColumn(gap = 18.dp) {
        Segments(Period.entries.map { l.t(it.key) }, p.ordinal, { vm.selectPeriod(Period.entries[it]) })

        // навигация по периодам: стрелки, заголовок, возврат к текущему
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconSquare(Icons.Back, { vm.shiftPeriod(-1) }, l.t("report.prev"))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(r.title.uppercase(), style = T.h(15.sp, col.text, 0.08.em), maxLines = 1)
                Text(
                    if (vm.periodOffset == 0) l.t("report.current") else l.t("report.backN", l.n(-vm.periodOffset, "period")),
                    style = T.b(10.5.sp, col.n600),
                )
            }
            Box(Modifier.alpha(if (vm.periodOffset < 0) 1f else 0.35f)) {
                IconSquare(Icons.Forward, { if (vm.periodOffset < 0) vm.shiftPeriod(1) }, l.t("report.next"))
            }
        }
        if (vm.periodOffset != 0) {
            SecondaryButton(l.t("report.returnCurrent"), { vm.periodOffset = 0 }, Modifier.fillMaxWidth(), size = 12)
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val cuts = Cut.entries.filter { !it.biz || full.s.business }
            cuts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { k ->
                        val st = opt(k == cut)
                        Box(
                            Modifier
                                .weight(1f)
                                .background(st.bg)
                                .hairline(st.border)
                                .tap { vm.cut = k }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                        ) { Text(l.t(k.key), style = T.b(12.sp, st.fg), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // фильтры: счёт и категория. В разрезах «Дела» они не при чём — там свои данные
        if (!cut.biz) ReportFilters(vm, full)

        StatGrid(
            listOf(
                Triple(l.t("report.expenses"), c.fmtMain(exp), col.text),
                Triple(l.t("report.incomes"), c.fmtMain(inc), col.a700),
                Triple(l.t("report.saldo"), Currencies.fmtSigned(inc - exp, c.main, c.s.showKopecks), if (inc - exp < 0) col.danger else col.text),
            ),
        )

        if (cut.biz) BizCutStats(c, cut, c.biz(r))

        // сравнение с прошлым периодом
        Row(
            Modifier.fillMaxWidth().hairline(col.divider).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cmp == null) {
                Muted(l.t("report.noCompare"), 12f)
            } else {
                val (prev, delta) = cmp
                val up = delta > 0
                val good = if (cut.biz) up else !up
                Column(Modifier.weight(1f)) {
                    Kicker(l.t(if (cut.biz) "biz.compare" else "report.compare"))
                    Text(
                        (if (up) "+" else "−") + "${abs(delta).roundToInt()}%",
                        style = T.h(17.sp, if (good) col.a700 else col.danger),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Muted(l.t("report.was"), 10.5f)
                    Text(c.fmtMain(prev), style = T.h(13.sp, col.n700))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(l.t(cut.titleKey)) { Muted(r.note) }
            BarRow(bars)

            val shown = slices.filter { it.value > 0 && it.pct != "—" }
            val total = shown.sumOf { it.value }
            fun sliceColor(i: Int, hex: String?): Color = hexColor(hex, Shades[min(i, Shades.lastIndex)])
            Row(Modifier.fillMaxWidth().height(26.dp).hairline(col.divider)) {
                if (total > 0) shown.forEachIndexed { i, s ->
                    Box(Modifier.weight((s.value / total).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(sliceColor(i, s.color)))
                    if (i < shown.lastIndex) Box(Modifier.width(1.dp).fillMaxHeight().background(col.bg))
                }
            }
            Column {
                slices.forEachIndexed { i, s ->
                    val idx = shown.indexOf(s).coerceAtLeast(0)
                    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(11.dp).background(if (s.pct == "—") col.n300 else sliceColor(idx, s.color)).hairline(col.divider))
                        Text(s.name, style = T.b(13.sp, col.text), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.pct, style = T.b(11.5.sp, col.n600))
                        Text(
                            if (cut == Cut.IO && i == 2) Currencies.fmtSigned(s.value, c.main, c.s.showKopecks) else c.fmtMain(s.value),
                            style = T.h(13.sp, col.text),
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(min = 86.dp),
                        )
                    }
                    SoftDivider()
                }
                if (slices.isEmpty()) Muted(l.t("report.empty"), 12f)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle(l.t("report.dynamics"))
            (if (cut.biz) c.bizFacts(r) else c.facts(r)).forEach { (label, value) ->
                Row(
                    Modifier.fillMaxWidth().hairline(col.divider).padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = T.b(13.sp, col.n700), modifier = Modifier.weight(1f))
                    Text(value, style = T.h(14.sp, col.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        SecondaryButton(l.t("report.askAi"), { vm.openAdvisor(true) }, Modifier.fillMaxWidth(), size = 13, upper = true)
    }
}
