package app.kopeechka.finance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.AppViewModel
import app.kopeechka.finance.StatementDraft
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.StatementLayout
import app.kopeechka.finance.ui.theme.T

/**
 * Выписка банка: какие колонки что значат, в какой счёт писать и что получится.
 * Колонки приложение угадывает по заголовкам; если не угадало — человек
 * поправит тапом по названию колонки.
 */
@Composable
fun StatementOverlay(vm: AppViewModel, c: Calc, st: StatementDraft) {
    val col = T.c
    val l = T.l
    val header = st.rows.first()
    val rows = vm.statementRows()
    val dupes = vm.statementDupes(rows)
    OverlayScreen(
        l.t("stmt.title"),
        l.t("common.cancel"),
        { vm.statement = null },
        footer = {
            PrimaryButton(
                if (rows.isEmpty()) l.t("stmt.pickColumns") else l.t("stmt.apply", rows.size - dupes),
                { vm.applyStatement() },
                enabled = rows.size - dupes > 0,
            )
        },
    ) {
        Muted(l.t("stmt.file", st.name, st.rows.size - 1), 11.5f, color = col.n700)

        ColumnPick(l.t("stmt.col.date"), header, st.layout.date) { vm.statement = st.copy(layout = st.layout.copy(date = it)) }
        ColumnPick(l.t("stmt.col.amount"), header, st.layout.amount) {
            vm.statement = st.copy(layout = st.layout.copy(amount = it))
        }
        if (st.layout.amount < 0) {
            ColumnPick(l.t("stmt.col.debit"), header, st.layout.debit) { vm.statement = st.copy(layout = st.layout.copy(debit = it)) }
            ColumnPick(l.t("stmt.col.credit"), header, st.layout.credit) { vm.statement = st.copy(layout = st.layout.copy(credit = it)) }
        }
        ColumnPick(l.t("stmt.col.text"), header, st.layout.text) { vm.statement = st.copy(layout = st.layout.copy(text = it)) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("inbox.toAcc"))
            ChipFlow {
                c.d.accounts.forEach { a -> Chip("${a.name} · ${Currencies.sym(a.cur)}", a.id == st.accId, { vm.statement = st.copy(accId = a.id) }) }
            }
        }

        if (rows.isNotEmpty()) {
            Kicker(l.t("stmt.preview"))
            if (dupes > 0) Muted(l.t("stmt.dupes", dupes), 11f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.take(8).forEach { r ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(c.dayLabel(r.date), style = T.b(11.5.sp, col.n600), modifier = Modifier.weight(0.3f), maxLines = 1)
                        Text(r.text, style = T.b(11.5.sp, col.text), modifier = Modifier.weight(0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.fmt(r.amount, c.accCur(st.accId)), style = T.b(11.5.sp, if (r.amount > 0) col.accent else col.text), modifier = Modifier.weight(0.25f), maxLines = 1)
                    }
                }
            }
        } else {
            Muted(l.t("stmt.needColumns"), 11.5f, color = col.danger)
        }
    }
}

/** Выбор колонки: чипы с заголовками файла, повторный тап снимает выбор. */
@Composable
private fun ColumnPick(title: String, header: List<String>, current: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Kicker(title)
        ChipFlow {
            header.forEachIndexed { i, name ->
                Chip(name.ifBlank { "#${i + 1}" }.take(24), i == current, { onPick(if (i == current) -1 else i) })
            }
        }
    }
}
