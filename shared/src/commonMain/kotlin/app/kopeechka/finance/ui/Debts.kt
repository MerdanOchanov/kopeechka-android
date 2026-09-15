package app.kopeechka.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.AppViewModel
import app.kopeechka.finance.Page
import app.kopeechka.finance.RepaySheet
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.debtParties
import app.kopeechka.finance.data.Debt
import app.kopeechka.finance.data.DebtKind
import app.kopeechka.finance.data.closedDebts
import app.kopeechka.finance.data.debtCur
import app.kopeechka.finance.data.debtDays
import app.kopeechka.finance.data.debtDone
import app.kopeechka.finance.data.debtGain
import app.kopeechka.finance.data.debtGainSoFar
import app.kopeechka.finance.data.debtLeft
import app.kopeechka.finance.data.debtOverdue
import app.kopeechka.finance.data.debtPaid
import app.kopeechka.finance.data.debtPct
import app.kopeechka.finance.data.debtRatePct
import app.kopeechka.finance.data.debtStats
import app.kopeechka.finance.data.openDebts
import app.kopeechka.finance.ui.theme.T
import kotlin.math.abs

/** Подпись срока: «через 12 дней», «сегодня», «просрочено на 3 дня». */
@Composable
fun dueLabel(c: Calc, d: Debt): Pair<String, Color> {
    val col = T.c
    val l = T.l
    val days = c.debtDays(d) ?: return l.t("debt.noDue") to col.n600
    return when {
        c.debtDone(d) -> l.t("debt.dueWas", c.dayLabel(d.due!!)) to col.n600
        days < 0 -> l.t("debt.overdue", l.n(abs(days).toInt(), "day")) to col.danger
        days == 0L -> l.t("debt.dueToday") to col.danger
        days <= 3 -> l.t("debt.dueIn", l.n(days.toInt(), "day")) to col.a900
        else -> l.t("debt.dueIn", l.n(days.toInt(), "day")) to col.n600
    }
}

/** Строка долга в списке. */
@Composable
fun DebtRow(vm: AppViewModel, c: Calc, d: Debt) {
    val col = T.c
    val l = T.l
    val cur = c.debtCur(d)
    val (due, dueColor) = dueLabel(c, d)
    val gain = c.debtGain(d)
    val lent = d.kind == DebtKind.LENT
    Column {
        Column(
            Modifier.fillMaxWidth().tap { vm.openDebt(d.id) }.padding(vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(d.party, style = T.b(14.sp, col.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(due, style = T.b(11.sp, dueColor), maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.fmt(c.debtLeft(d), cur), style = T.h(15.sp, col.text), maxLines = 1)
                    if (gain > 0.005) {
                        Text(
                            l.t(if (lent) "debt.gainShort" else "debt.costShort", c.fmt(gain, cur)),
                            style = T.b(10.5.sp, if (lent) col.a700 else col.danger),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (c.debtPaid(d) > 0) {
                ProgressLine(c.debtPct(d) / 100f, if (c.debtOverdue(d)) col.danger else col.a600, 5.dp)
            }
        }
        SoftDivider()
    }
}

// ——— Страница «Долги» ———

@Composable
fun DebtsPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val stats = c.debtStats()
    val lent = c.openDebts(DebtKind.LENT)
    val borrowed = c.openDebts(DebtKind.BORROWED)
    val closed = c.closedDebts()

    ScreenColumn(gap = 16.dp) {
        PageHeader(l.t("debt.title")) { vm.page = null }

        StatGrid(
            listOf(
                Triple(l.t("debt.owedToMe"), c.fmtMain(stats.lentLeft), col.a700),
                Triple(l.t("debt.iOwe"), c.fmtMain(stats.borrowedLeft), if (stats.borrowedLeft > 0) col.danger else col.text),
                Triple(l.t("debt.willEarn"), c.fmtMain(stats.gain), if (stats.gain > 0) col.a700 else col.n600),
            ),
        )
        if (stats.overdue > 0) {
            Blueprint(Modifier.fillMaxWidth(), PaddingValues(12.dp)) {
                Text(l.t("debt.overdueCount", stats.overdue), style = T.b(13.sp, col.danger))
            }
        }

        Column {
            SectionTitle(l.t("debt.owedToMe")) { Muted(l.n(lent.size, "debt")) }
            Spacer(Modifier.height(6.dp))
            if (lent.isEmpty()) Muted(l.t("debt.noneLent"), 12f)
            lent.forEach { DebtRow(vm, c, it) }
        }

        Column {
            SectionTitle(l.t("debt.iOwe")) { Muted(l.n(borrowed.size, "debt")) }
            Spacer(Modifier.height(6.dp))
            if (borrowed.isEmpty()) Muted(l.t("debt.noneBorrowed"), 12f)
            borrowed.forEach { DebtRow(vm, c, it) }
        }

        if (closed.isNotEmpty()) {
            Column {
                SectionTitle(l.t("debt.closedList")) { Muted(l.n(closed.size, "debt")) }
                Spacer(Modifier.height(6.dp))
                closed.take(10).forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().tap { vm.openDebt(d.id) }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(d.party, style = T.b(13.sp, col.n700), modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            l.t(if (d.kind == DebtKind.LENT) "debt.kind.lent" else "debt.kind.borrowed"),
                            style = T.b(10.5.sp, col.n600),
                        )
                        Text(c.fmt(d.expected, c.debtCur(d)), style = T.h(13.sp, col.n700))
                    }
                    SoftDivider()
                }
            }
        }

        AddButton(l.t("debt.add")) { vm.openAdd(app.kopeechka.finance.Kind.DEBT) }
        Muted(l.t("debt.note"), 10.5f, color = col.n700)
    }
}

// ——— Карточка долга ———

@Composable
fun DebtCardOverlay(vm: AppViewModel, c: Calc, id: Long) {
    val col = T.c
    val l = T.l
    val d = c.d.debts.firstOrNull { it.id == id } ?: return
    val cur = c.debtCur(d)
    val lent = d.kind == DebtKind.LENT
    val (due, dueColor) = dueLabel(c, d)
    val gain = c.debtGain(d)
    val done = c.debtDone(d)

    OverlayScreen(
        d.party,
        l.t("common.close"),
        { vm.debtCard = null },
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!done) {
                    PrimaryButton(l.t(if (lent) "debt.takeBack" else "debt.payBack"), { vm.openRepay(d.id) })
                    SecondaryButton(l.t("debt.closeAction"), { vm.askCloseDebt(d.id) }, Modifier.fillMaxWidth(), size = 13, upper = true)
                } else {
                    SecondaryButton(l.t("debt.reopen"), { vm.reopenDebt(d.id) }, Modifier.fillMaxWidth(), size = 13, upper = true)
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Kicker(l.t(if (lent) "debt.kind.lent" else "debt.kind.borrowed"))
            Text(c.fmt(c.debtLeft(d), cur), style = T.h(34.sp, if (done) col.n600 else col.text), maxLines = 1)
            Text(
                if (done) l.t("debt.settled") else l.t("debt.leftOf", c.fmt(d.expected, cur)),
                style = T.b(12.sp, col.n600),
            )
        }
        ProgressLine(c.debtPct(d) / 100f, if (c.debtOverdue(d)) col.danger else col.a600, 8.dp)

        StatGrid(
            listOf(
                Triple(l.t("debt.principal"), c.fmt(d.principal, cur), col.text),
                Triple(
                    l.t(if (lent) "debt.gain" else "debt.overpay"),
                    c.fmt(gain, cur) + if (d.principal > 0 && gain > 0.005) " · ${c.debtRatePct(d)}%" else "",
                    if (gain <= 0.005) col.n600 else if (lent) col.a700 else col.danger,
                ),
                Triple(l.t("debt.paidBack"), c.fmt(c.debtPaid(d), cur), col.text),
            ),
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().hairline(col.divider).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(l.t("debt.dueLabel"), style = T.b(12.sp, col.n700), modifier = Modifier.weight(1f))
                Text(due, style = T.b(12.5.sp, dueColor))
            }
            Row(Modifier.fillMaxWidth().hairline(col.divider).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(l.t("debt.since"), style = T.b(12.sp, col.n700), modifier = Modifier.weight(1f))
                Text(c.dayLabel(d.date), style = T.b(12.5.sp, col.text))
            }
            if (c.debtGainSoFar(d) > 0.005) {
                Row(Modifier.fillMaxWidth().hairline(col.divider).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(l.t(if (lent) "debt.earned" else "debt.overpaid"), style = T.b(12.sp, col.n700), modifier = Modifier.weight(1f))
                    Text(c.fmt(c.debtGainSoFar(d), cur), style = T.h(13.sp, if (lent) col.a700 else col.danger))
                }
            }
        }

        if (d.note.isNotBlank()) Muted(d.note, 12f, color = col.n700)

        if (d.payments.isNotEmpty()) {
            Column {
                SectionTitle(l.t("debt.payments"))
                Spacer(Modifier.height(4.dp))
                d.payments.sortedByDescending { it.date }.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(c.dayLabel(p.date), style = T.b(12.sp, col.n600), modifier = Modifier.weight(1f), maxLines = 1)
                        Text(c.fmt(p.amount, cur), style = T.h(13.sp, col.text))
                    }
                    SoftDivider()
                }
            }
        }

        DangerButton(l.t("debt.delete"), { vm.askDeleteDebt(d.id) })
    }
}

// ——— Возврат ———

@Composable
fun RepaySheetView(vm: AppViewModel, c: Calc, rs: RepaySheet) {
    val col = T.c
    val l = T.l
    val d = c.d.debts.firstOrNull { it.id == rs.debtId } ?: return
    val cur = c.debtCur(d)
    val lent = d.kind == DebtKind.LENT
    val acc = c.acc(rs.acc)
    val accCur = acc?.cur ?: c.main
    val amount = rs.amount.replace(',', '.').toDoubleOrNull() ?: 0.0

    BottomSheet({ vm.repaySheet = null }) {
        Text(l.t(if (lent) "debt.takeBack" else "debt.payBack").uppercase(), style = T.h(13.sp, col.text, 0.12.em))
        Muted(l.t("debt.leftNow", c.fmt(c.debtLeft(d), cur)), 12f)

        Field(
            l.t("debt.amount", Currencies.sym(cur)),
            rs.amount,
            { vm.repaySheet = rs.copy(amount = it) },
            numeric = true,
            placeholder = "0",
        )
        if (amount > 0 && cur != accCur) {
            Muted(l.t("debt.inAccCur", c.fmt(c.conv(amount, cur, accCur), accCur)), 11f)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t(if (lent) "debt.toAcc" else "debt.fromAcc"))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                c.d.accounts.forEach { a ->
                    Chip("${a.name} · ${a.cur}", a.id == rs.acc, { vm.repaySheet = rs.copy(acc = a.id) })
                }
            }
        }
        PrimaryButton(l.t("debt.repayAction"), { vm.confirmRepay() }, enabled = amount > 0)
    }
}

// ——— Карточка на главном ———

@Composable
fun DebtsCard(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val stats = c.debtStats()
    ReportCard(l.t("debt.title"), l.t("debt.cardHint"), { vm.openPage(Page.DEBTS) }) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Kicker(l.t("debt.owedToMe"))
                Text(c.fmtMain(stats.lentLeft), style = T.h(20.sp, col.a700), maxLines = 1)
            }
            Column(Modifier.weight(1f)) {
                Kicker(l.t("debt.iOwe"))
                Text(
                    c.fmtMain(stats.borrowedLeft),
                    style = T.h(20.sp, if (stats.borrowedLeft > 0) col.danger else col.text),
                    maxLines = 1,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Kicker(l.t("debt.willEarn"))
                Text(c.fmtMain(stats.gain), style = T.h(20.sp, col.text), maxLines = 1)
            }
        }
        val soon = stats.soonest
        if (stats.overdue > 0 || soon != null) {
            Spacer(Modifier.height(10.dp))
            if (stats.overdue > 0) {
                Muted(l.t("debt.overdueCount", stats.overdue), 11f, color = col.danger)
            } else if (soon != null) {
                val (due, _) = dueLabel(c, soon)
                Muted(l.t("debt.soonest", soon.party, due), 11f)
            }
        }
    }
}

/** Блок «долг» на экране новой операции: направление, кому, срок и сколько вернуть. */
@Composable
fun DebtFields(vm: AppViewModel, c: Calc, d: app.kopeechka.finance.Draft) {
    val col = T.c
    val l = T.l
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Kicker(l.t("debt.direction"))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DebtKind.ALL.forEach { k ->
                Chip(l.t(DebtKind.key(k)), d.debtKind == k, { vm.draft = d.copy(debtKind = k) })
            }
            c.debtParties().forEach { name ->
                Chip(name, d.party == name, { vm.draft = d.copy(party = name) })
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Field(
                null,
                d.party,
                { vm.draft = d.copy(party = it) },
                placeholder = l.t(if (d.debtKind == DebtKind.LENT) "debt.toWhom" else "debt.fromWhom"),
            )
        }
        Box(Modifier.width(118.dp)) {
            Field(
                null,
                d.expected,
                { vm.draft = d.copy(expected = it) },
                numeric = true,
                placeholder = l.t("debt.expectedHint"),
            )
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .hairline(col.divider)
            .tap { vm.datePick = "due" }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(l.t("debt.dueRow"), style = T.b(12.sp, col.n700), modifier = Modifier.weight(1f))
        Text(
            d.due?.let { c.dayLabel(it) } ?: l.t("debt.setDue"),
            style = T.b(12.5.sp, if (d.due == null) col.a700 else col.text),
        )
    }
}
