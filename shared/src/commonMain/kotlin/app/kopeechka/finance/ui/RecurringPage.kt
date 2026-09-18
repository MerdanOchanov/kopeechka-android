package app.kopeechka.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.AppViewModel
import app.kopeechka.finance.RecEdit
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.Every
import app.kopeechka.finance.data.Recurring
import app.kopeechka.finance.data.leftAmount
import app.kopeechka.finance.data.next
import app.kopeechka.finance.ui.theme.T

/** Регулярные платежи и рассрочки: что, когда и сколько осталось. */
@Composable
fun RecurringPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    ScreenColumn(gap = 16.dp) {
        PageHeader(l.t("rec.title")) { vm.page = null }
        Muted(l.t("rec.note"), 11.5f, color = col.n700)
        SectionTitle(l.t("rec.list")) { GhostButton(l.t("rec.add"), { vm.openRecurring(null) }) }
        if (c.d.recurring.isEmpty()) {
            Muted(l.t("rec.empty"), 12f)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                c.d.recurring.sortedBy { it.next ?: Long.MAX_VALUE }.forEach { r -> RecurringRow(vm, c, r) }
            }
        }
    }
}

@Composable
private fun RecurringRow(vm: AppViewModel, c: Calc, r: Recurring) {
    val col = T.c
    val l = T.l
    val cur = c.accCur(r.accId)
    val next = r.next
    Column(
        Modifier
            .fillMaxWidth()
            .background(col.surface)
            .hairline(col.divider)
            .tap { vm.openRecurring(r.id) }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                r.title,
                style = T.h(14.sp, if (r.active && next != null) col.text else col.n500),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (r.income) "+" else "−") + c.fmt(r.amount, cur),
                style = T.h(14.sp, if (r.income) col.accent else col.text),
                maxLines = 1,
            )
        }
        Text(
            listOfNotNull(
                l.t("rec.every." + r.every),
                when {
                    !r.active -> l.t("rec.paused")
                    next == null -> l.t("rec.finished")
                    else -> l.t("rec.nextOn", c.dayLabel(next))
                },
                c.acc(r.accId)?.name,
            ).joinToString(" · "),
            style = T.b(11.sp, col.n600),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (r.total > 0) {
            // полоса выплаты рассрочки: видно, сколько позади и сколько впереди
            Box(Modifier.fillMaxWidth().height(4.dp).background(col.divider)) {
                Box(Modifier.fillMaxWidth(r.done.toFloat() / r.total).height(4.dp).background(col.accent))
            }
            Text(
                l.t("rec.progress", r.done, r.total, c.fmt(r.leftAmount ?: 0.0, cur)),
                style = T.b(11.sp, col.n700),
            )
        }
    }
}

/** Новый платёж или правка существующего. */
@Composable
fun RecurringOverlay(vm: AppViewModel, c: Calc, e: RecEdit) {
    val col = T.c
    val l = T.l
    val cur = c.accCur(e.accId)
    OverlayScreen(
        if (e.id == null) l.t("rec.new") else l.t("rec.one"),
        l.t("common.cancel"),
        { vm.recEdit = null },
        footer = { PrimaryButton(l.t("common.save"), { vm.saveRecurring() }) },
    ) {
        Field(l.t("rec.name"), e.title, { vm.recEdit = e.copy(title = it) }, placeholder = l.t("rec.nameHint"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(l.t("kind.expense"), !e.income, { vm.recEdit = e.copy(income = false) }, modifier = Modifier.weight(1f))
            Chip(l.t("kind.income"), e.income, { vm.recEdit = e.copy(income = true) }, modifier = Modifier.weight(1f))
        }
        Field(l.t("rec.amount", Currencies.sym(cur)), e.amount, { vm.recEdit = e.copy(amount = it) }, numeric = true, placeholder = "0")

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("rec.every"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Every.ALL.forEach { ev ->
                    Chip(l.t("rec.every." + ev), e.every == ev, { vm.recEdit = e.copy(every = ev) }, modifier = Modifier.weight(1f))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("rec.first"))
            SecondaryButton(c.dayLabel(e.start), { vm.datePick = "rec" }, Modifier.fillMaxWidth(), size = 13)
        }

        Field(
            l.t("rec.total"),
            e.total,
            { vm.recEdit = e.copy(total = it.filter { ch -> ch.isDigit() }.take(3)) },
            numeric = true,
            placeholder = "0",
            note = l.t("rec.totalNote"),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("inbox.toAcc"))
            ChipFlow {
                c.d.accounts.forEach { a -> Chip("${a.name} · ${Currencies.sym(a.cur)}", a.id == e.accId, { vm.recEdit = e.copy(accId = a.id) }) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("inbox.cat"))
            ChipFlow {
                c.d.categories.filter { it.income == e.income }.forEach { cat ->
                    Chip(cat.name, cat.id == e.cat, { vm.recEdit = e.copy(cat = cat.id) })
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("rec.remind"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 1, 3, 7).forEach { d ->
                    Chip(l.t("rec.remind.$d"), e.remindDays == d, { vm.recEdit = e.copy(remindDays = d) }, modifier = Modifier.weight(1f))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.t("rec.auto"), style = T.b(13.sp, col.text))
                Muted(l.t("rec.autoNote"), 11f)
            }
            Toggle(e.auto) { vm.recEdit = e.copy(auto = !e.auto) }
        }

        if (e.id != null) {
            val r = c.d.recurring.firstOrNull { it.id == e.id }
            if (r != null) {
                GhostButton(if (r.active) l.t("rec.pause") else l.t("rec.resume"), { vm.toggleRecurring(r.id) }, size = 13)
            }
            DangerButton(l.t("rec.delete"), { vm.askDeleteRecurring(e.id) })
        }
    }
}

/** На главной: платежи на ближайшую неделю — чтобы деньги к ним не потратились. */
@Composable
fun UpcomingPayments(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val soon = vm.upcomingPayments(7)
    if (soon.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(l.t("rec.soon")) { GhostButton(l.t("rec.all"), { vm.openPage(app.kopeechka.finance.Page.RECURRING) }) }
        soon.take(4).forEach { (r, day) ->
            Row(
                Modifier.fillMaxWidth().tap { vm.openRecurring(r.id) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.dayLabel(day), style = T.b(12.sp, col.n600), modifier = Modifier.padding(end = 10.dp))
                Text(r.title, style = T.b(13.sp, col.text), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (r.income) "+" else "−") + c.fmt(r.amount, c.accCur(r.accId)),
                    style = T.h(13.sp, if (r.income) col.accent else col.text, 0.02.em),
                )
            }
        }
    }
}
