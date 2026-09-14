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
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.net.Ai
import app.kopeechka.finance.net.DriveBackup
import app.kopeechka.finance.ui.theme.T
import java.time.Instant
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

fun fmtRate(v: Double) = when {
    v == v.roundToLong().toDouble() -> v.roundToLong().toString()
    else -> v.toString().replace('.', ',')
}

@Composable
fun SettingsScreen(vm: AppViewModel, c: Calc, onEnableReminder: () -> Unit) {
    val col = T.c
    val s = c.s
    val provider = Ai.provider(s.aiProvider)
    ScreenColumn(gap = 22.dp) {
        Column {
            SectionTitle("Разделы")
            Spacer(Modifier.height(4.dp))
            val n = c.d.accounts.size
            NavRow(Icons.Wallet, "Счета и карты", "$n ${plural(n, "счёт", "счёта", "счетов")} · ${c.fmtMain(c.totalMain)}") { vm.openPage(Page.ACCOUNTS) }
            val nc = c.d.categories.size
            NavRow(Icons.Tags, "Категории и лимиты", "$nc ${plural(nc, "категория", "категории", "категорий")} · цвета и лимиты") { vm.openPage(Page.CATEGORIES) }
            val ng = c.d.goals.size
            NavRow(Icons.Target, "Цели и накопления", if (ng == 0) "Пока нет целей" else "$ng ${plural(ng, "цель", "цели", "целей")}") { vm.openPage(Page.GOALS) }
            val ncur = c.currencies.size
            NavRow(Icons.Rate, "Валюты и курсы", "$ncur ${plural(ncur, "валюта", "валюты", "валют")} · основная ${c.main}") { vm.openPage(Page.CURRENCIES) }
            NavRow(
                Icons.Cloud,
                "Резервная копия",
                if (!s.driveLinked) "Google Диск не подключён"
                else if (s.lastBackupAt > 0) "Google Диск · " + DriveBackup.formatTime(Instant.ofEpochMilli(s.lastBackupAt))
                else "Google Диск подключён",
            ) { vm.openPage(Page.BACKUP) }
            NavRow(Icons.Spark, "ИИ-советник", "${provider.name} · " + if (vm.hasKey(provider.key) || !provider.needsKey) "готов" else "без ключа — офлайн-разбор") { vm.openAdvisor() }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Основная валюта")
            Muted("В ней считаются баланс, бюджеты и отчёты. Счета в других валютах пересчитываются по курсу.", 11.5f, color = col.n700)
            CurrencyGrid(c.currencies, c.main) { vm.setMainCur(it) }
            GhostButton("Добавить валюту", { vm.currencyPicker = true }, size = 12)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Валюта счетов")
            c.d.accounts.forEach { a ->
                val bal = c.balances[a.id] ?: 0.0
                SettingRow(a.name, "${c.fmt(bal, a.cur)} · " + if (a.cur == c.main) "в основной валюте" else "≈ ${c.fmtMain(c.toMain(bal, a.cur))}") {
                    SecondaryButton("${a.cur} ${Currencies.sym(a.cur)}", { vm.curSheet = CurSheet.Acc(a.id) })
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Оформление")
            Segments(listOf("Светлая", "Тёмная"), if (s.dark) 1 else 0, { vm.setDark(it == 1) })
            SettingRow("Показывать копейки", "В суммах, списках и отчётах") {
                Toggle(s.showKopecks) { v -> vm.settings { it.copy(showKopecks = v) } }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Напоминания")
            SettingRow("Напоминать записать расходы", "Каждый вечер в ${s.remindHour}:00") {
                Toggle(s.remind) { on -> if (on) onEnableReminder() else vm.setRemind(false) }
            }
            if (s.remind) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(19, 20, 21, 22).forEach { h -> Chip("$h:00", s.remindHour == h, { vm.setRemindHour(h) }) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Профиль")
            var name by remember { mutableStateOf(s.userName) }
            Field("Как к вам обращаться", name, { name = it; vm.settings { st -> st.copy(userName = it) } }, placeholder = "Например, Алина")
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Данные")
            SecondaryButton("Загрузить демо-данные", { vm.askLoadDemo() }, Modifier.fillMaxWidth(), size = 13, upper = true)
            DangerButton("Очистить все данные", { vm.askClearAll() })
            Muted(
                "Копеечка 1.0 · данные хранятся только на этом телефоне. В интернет уходят лишь резервные копии (в ваш Google Диск) и вопросы ИИ-советнику — когда вы сами их отправляете.",
                10.5f,
            )
        }
    }
}

// ——— Валюты и курсы ———

@Composable
fun CurrenciesPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    ScreenColumn(gap = 16.dp) {
        PageHeader("Валюты и курсы") { vm.page = null }
        Muted(
            "Курс — сколько рублей стоит одна единица валюты. Рубль здесь база, его курс всегда 1. " +
                "Курсы задаются вручную: приложение никуда не ходит за ними и не требует интернета.",
            11.5f,
            color = col.n700,
        )
        Column {
            c.currencies.forEach { code ->
                val info = Currencies.info(code)
                val isBase = code == Currencies.BASE
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
                                    if (code == c.main) add("основная")
                                    if (used > 0) add("используется в $used ${plural(used, "месте", "местах", "местах")}")
                                    if (!Currencies.inCatalog(code)) add("своя")
                                }.joinToString(" · "),
                                style = T.b(11.sp, col.n600),
                            )
                        }
                        if (isBase) {
                            Text("база", style = T.b(11.5.sp, col.n600))
                        } else {
                            RateField(vm, code, c.rate(code))
                        }
                    }
                    if (!isBase && code != c.main && used == 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            GhostButton("Убрать валюту", { vm.removeCurrency(code) }, size = 11)
                        }
                    }
                }
                SoftDivider()
            }
        }
        AddButton("+ Добавить валюту") { vm.currencyPicker = true }
        Muted(
            "Туркменский манат (TMT) уже в списке. Любую другую мировую валюту можно выбрать из каталога, " +
                "а если её там нет — завести свою с собственным кодом и символом.",
            11f,
        )
    }
}

@Composable
private fun RateField(vm: AppViewModel, code: String, rate: Double) {
    val col = T.c
    var text by remember(code) { mutableStateOf(fmtRate(rate)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(104.dp)) {
            Field(null, text, { text = it; vm.setRate(code, it) }, numeric = true, placeholder = "0")
        }
        Text("₽", style = T.b(14.sp, col.n700))
    }
}

// ——— Счета ———

@Composable
fun AccountsPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    ScreenColumn(gap = 16.dp) {
        PageHeader("Счета и карты") { vm.page = null }
        Blueprint(Modifier.fillMaxWidth()) {
            Kicker("Сумма по счетам в общем балансе")
            Spacer(Modifier.height(3.dp))
            Text(c.fmtMain(c.totalMain), style = T.h(26.sp, col.text))
        }
        Column {
            c.d.accounts.forEach { a ->
                val bal = c.balances[a.id] ?: 0.0
                Row(
                    Modifier.fillMaxWidth().tap { vm.openAccEdit(a) }.padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CodeBox(a.name.filter { it.isLetter() }.take(2).uppercase().ifBlank { "СЧ" }, 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = T.b(14.sp, col.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(a.type, a.mask, a.cur).filter { it.isNotBlank() }.joinToString(" · "), style = T.b(11.sp, col.n600))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(c.fmt(bal, a.cur), style = T.h(15.sp, col.text))
                        Text(
                            if (a.inTotal) "в общем балансе" else "не учитывается",
                            style = T.b(11.sp, if (a.inTotal) col.a700 else col.n500),
                            modifier = Modifier.tap { vm.toggleInTotal(a.id) }.padding(top = 2.dp),
                        )
                    }
                }
                SoftDivider()
            }
        }
        AddButton("+ Добавить счёт") { vm.openAccEdit(null) }
        Muted("Нажмите на счёт, чтобы изменить название, баланс или валюту. «В общем балансе» — учитывать ли счёт в сумме на главном экране.", 11f)
    }
}

// ——— Категории ———

@Composable
fun CategoriesPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val spent = c.spentBy(c.monthTx)
    val earned = c.monthTx.filter { it.amount > 0 }.groupBy { it.cat }.mapValues { e -> e.value.sumOf { c.txMain(it) } }
    ScreenColumn(gap = 16.dp) {
        PageHeader("Категории и лимиты") { vm.page = null }
        Muted("Нажмите на категорию, чтобы задать лимит и выбрать цвет — он используется в бюджете, отчётах и списках.", 11f)
        listOf(false, true).forEach { income ->
            Column {
                SectionTitle(if (income) "Доходы" else "Расходы") { Muted("за этот месяц") }
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
                            if (!income) Text(if (cat.limitRub > 0) "лимит ${c.fmtMain(c.limitMain(cat))} в месяц" else "без лимита", style = T.b(11.sp, col.n600))
                        }
                        val v = if (income) earned[cat.id] else spent[cat.id]
                        Text(v?.let { c.fmtMain(it) } ?: "—", style = T.h(13.sp, col.n700))
                    }
                    SoftDivider()
                }
            }
            AddButton(if (income) "+ Категория доходов" else "+ Категория расходов") { vm.openCatEdit(null, income) }
        }
    }
}

// ——— Цели ———

@Composable
fun GoalsPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    ScreenColumn(gap = 18.dp) {
        PageHeader("Цели и накопления") { vm.page = null }
        if (c.d.goals.isEmpty()) Muted("Целей пока нет. Добавьте первую — например, «Отпуск» или «Резервный фонд».", 12f)
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
                    Text("цель ${c.fmt(g.target, g.cur)}", style = T.b(12.sp, col.n600))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        g.hint.ifBlank { if (pct >= 100) "Цель достигнута" else "осталось ${c.fmt(max(0.0, g.target - g.saved), g.cur)}" },
                        style = T.b(11.5.sp, col.n600),
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton("Отложить", { vm.openGoalSheet(g.id) })
                }
            }
        }
        AddButton("+ Новая цель") { vm.openGoalEdit(null) }
        Muted("«Отложить» списывает сумму с выбранного счёта и добавляет её к цели. Нажмите на название цели, чтобы изменить её.", 11f)
    }
}

// ——— Резервная копия ———

@Composable
fun BackupPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val s = c.s
    LaunchedEffect(Unit) { if (s.driveLinked && vm.driveList.isEmpty()) vm.refreshBackups() }
    ScreenColumn(gap = 16.dp) {
        PageHeader("Резервная копия") { vm.page = null }
        Blueprint(Modifier.fillMaxWidth()) {
            Kicker("Google Диск")
            Spacer(Modifier.height(4.dp))
            Text(if (s.driveLinked) "Подключён" else "Не подключён", style = T.h(22.sp, col.text))
            Spacer(Modifier.height(2.dp))
            Text(
                if (s.lastBackupAt > 0) "Последняя копия: " + DriveBackup.formatTime(Instant.ofEpochMilli(s.lastBackupAt)) else "Копий ещё не было",
                style = T.b(11.5.sp, col.n700),
            )
        }
        Muted(
            "Копия — один JSON-файл со всеми счетами, операциями, категориями, целями и настройками. API-ключи ИИ в копию не попадают. " +
                "Файлы лежат в папке «Копеечка — резервные копии» на вашем Google Диске, хранятся 10 последних.",
            11.5f,
            color = col.n700,
        )
        PrimaryButton(
            when {
                vm.driveBusy -> "Подождите…"
                s.driveLinked -> "Сохранить копию сейчас"
                else -> "Подключить Диск и сохранить"
            },
            { vm.backupNow() },
            enabled = !vm.driveBusy,
        )
        SettingRow("Автоматически раз в день", if (s.driveLinked) "В фоне, по Wi-Fi" else "Сработает после подключения Диска") {
            Toggle(s.autoBackup) { vm.setAutoBackup(it) }
        }
        SectionTitle("Копии на Диске") { GhostButton("Обновить", { vm.refreshBackups() }) }
        if (vm.driveList.isEmpty()) Muted(if (s.driveLinked) "Копий пока нет или список ещё загружается" else "Подключите Диск, чтобы увидеть копии", 12f)
        Column {
            vm.driveList.forEach { b ->
                SettingRow(DriveBackup.formatTime(b.created), "${b.name} · ${max(1L, b.size / 1024)} КБ") {
                    SecondaryButton("Восстановить", { vm.askRestore(b) })
                }
            }
        }
        if (s.driveLinked) GhostButton("Отключить Диск в приложении", { vm.unlinkDrive() })
    }
}
