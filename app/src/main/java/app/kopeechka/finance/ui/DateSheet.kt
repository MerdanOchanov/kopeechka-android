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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.AppViewModel
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.Csv
import app.kopeechka.finance.ui.theme.T
import java.time.LocalDate

/**
 * Выбор даты: месяц-сетка в духе остального приложения плюс поле для ручного ввода.
 * Материаловский календарь сюда не встаёт — у него свои скругления и палитра.
 */
@Composable
fun DateSheet(vm: AppViewModel, c: Calc, selected: Long) {
    val col = T.c
    val l = T.l
    val picked = LocalDate.ofEpochDay(selected)
    var shown by remember { mutableStateOf(picked.withDayOfMonth(1)) }
    var typed by remember { mutableStateOf("") }
    val today = c.today

    BottomSheet({ vm.datePick = null }) {
        Text(l.t("date.pickTitle").uppercase(), style = T.h(13.sp, col.text, 0.12.em))

        // месяц и стрелки
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconSquare(Icons.Back, { shown = shown.minusMonths(1) }, l.t("report.prev"))
            Text(
                (l.months[shown.monthValue - 1] + " " + shown.year).uppercase(),
                style = T.h(15.sp, col.text, 0.08.em),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            IconSquare(Icons.Forward, { shown = shown.plusMonths(1) }, l.t("report.next"))
        }

        // шапка недели
        Row(Modifier.fillMaxWidth()) {
            l.week.forEach { d ->
                Text(
                    d.uppercase(),
                    style = T.b(9.5.sp, col.n600, 0.08.em),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }

        // сетка дней: пустые клетки до первого числа, дальше числа месяца
        val first = shown.withDayOfMonth(1)
        val lead = first.dayOfWeek.value - 1
        val len = shown.lengthOfMonth()
        val cells = List(lead) { null } + (1..len).map { it }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cells.chunked(7).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { day ->
                        if (day == null) {
                            Box(Modifier.weight(1f).height(38.dp))
                        } else {
                            val date = shown.withDayOfMonth(day)
                            val on = date == picked
                            val isToday = date == today
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .background(if (on) col.accent else Color.Transparent)
                                    .hairline(if (on) col.a700 else if (isToday) col.a600 else col.divider)
                                    .tap { vm.pickDate(date.toEpochDay()) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.toString(),
                                    style = T.h(13.sp, if (on) Color.White else col.text),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    repeat(7 - row.size) { Box(Modifier.weight(1f).height(38.dp)) }
                }
            }
        }

        // ручной ввод
        Field(
            null,
            typed,
            { typed = it },
            placeholder = l.t("date.typeHint", today.dayOfMonth, today.monthValue, today.year),
            note = l.t("date.typeNote"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                SecondaryButton(l.t("date.today"), { vm.pickDate(c.todayDay) }, Modifier.fillMaxWidth(), size = 13, upper = true)
            }
            Box(Modifier.weight(1f)) {
                PrimaryButton(l.t("common.ok"), {
                    val day = Csv.parseDate(typed)
                    if (day == null) vm.flash(l.t("date.badDate")) else vm.pickDate(day)
                }, enabled = typed.isNotBlank())
            }
        }
    }
}

/** Кнопка «другая дата» в ленте дат: открывает календарь. */
@Composable
fun OtherDateChip(vm: AppViewModel, target: String) {
    val col = T.c
    Row(
        Modifier
            .hairline(col.divider)
            .tap { vm.datePick = target }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("···", style = T.h(11.sp, col.a700))
        Text(T.l.t("date.other"), style = T.b(12.5.sp, col.a700))
    }
}
