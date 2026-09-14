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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.AccEdit
import app.kopeechka.finance.AppViewModel
import app.kopeechka.finance.CatEdit
import app.kopeechka.finance.Confirm
import app.kopeechka.finance.CurSheet
import app.kopeechka.finance.Draft
import app.kopeechka.finance.GoalEdit
import app.kopeechka.finance.GoalSheet
import app.kopeechka.finance.Kind
import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.CurrencyDraft
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.Palette
import app.kopeechka.finance.net.Ai
import app.kopeechka.finance.ui.theme.T

private val DATASETS = listOf(
    "ops" to "Операции",
    "budgets" to "Бюджеты",
    "accounts" to "Счета и валюты",
    "goals" to "Цели",
    "report" to "Текущий отчёт",
)

private val PROMPTS = listOf(
    "Где я перетрачиваю?",
    "Как накопить 300 000 за полгода?",
    "Стоит ли держать накопления в валюте?",
)

// ——— Новая операция / редактирование ———

@Composable
fun AddOverlay(vm: AppViewModel, c: Calc, d: Draft) {
    val col = T.c
    val src = c.acc(d.from)
    val dst = c.acc(d.to)
    val srcCur = src?.cur ?: c.main
    val v = d.amount.toLongOrNull()?.toDouble() ?: 0.0
    val isT = d.kind == Kind.TRANSFER
    OverlayScreen(
        title = when {
            isT -> "Перевод между счетами"
            d.editId != null -> "Операция"
            else -> "Новая операция"
        },
        action = "Отмена",
        onAction = { vm.draft = null },
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.editId != null) DangerButton("Удалить", { vm.askDeleteTx(d.editId) }, Modifier.weight(0.45f))
                PrimaryButton(if (isT) "Перевести" else "Сохранить", { vm.saveDraft() }, Modifier.weight(1f), enabled = d.amount.isNotEmpty())
            }
        },
    ) {
        JoinedSegments(Kind.entries.map { it.label }, d.kind.ordinal) { vm.draft = d.copy(kind = Kind.entries[it]) }

        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(col.n100)
                    .hairline(col.divider)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    when (d.kind) {
                        Kind.TRANSFER -> "перевод"
                        Kind.INCOME -> "доход"
                        Kind.EXPENSE -> "расход"
                    },
                    style = T.b(13.sp, col.n600),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(
                    if (d.amount.isEmpty()) "0" else Currencies.fmtNumber(v),
                    style = T.h(40.sp, if (d.amount.isEmpty()) col.n400 else col.text, lineHeight = 42.sp),
                )
                Text(Currencies.sym(srcCur), style = T.b(18.sp, col.n700), modifier = Modifier.padding(bottom = 5.dp))
            }
            if (v > 0 && srcCur != c.main) {
                Text(
                    "≈ ${c.fmtMain(c.toMain(v, srcCur))} в основной валюте",
                    style = T.b(11.5.sp, col.n700),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(if (isT) "Откуда" else "Счёт")
            ChipFlow {
                c.d.accounts.forEach { a -> Chip("${a.name} · ${Currencies.sym(a.cur)}", a.id == d.from, { vm.draft = d.copy(from = a.id) }) }
            }
        }

        if (isT) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kicker("Куда")
                ChipFlow {
                    c.d.accounts.forEach { a -> Chip("${a.name} · ${Currencies.sym(a.cur)}", a.id == d.to, { vm.draft = d.copy(to = a.id) }) }
                }
                Blueprint(Modifier.fillMaxWidth(), PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Зачислится", style = T.b(12.sp, col.n700))
                        Spacer(Modifier.weight(1f))
                        Text(if (dst != null) c.fmt(c.conv(v, srcCur, dst.cur), dst.cur) else "—", style = T.h(17.sp, col.text))
                    }
                }
            }
        }

        if (d.kind == Kind.EXPENSE) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kicker("Категория")
                ChipFlow {
                    c.d.categories.filter { !it.income }.forEach { cat ->
                        Chip(cat.name, cat.id == d.cat, { vm.draft = d.copy(cat = cat.id) }, code = cat.code, accent = catColor(c, cat.id))
                    }
                }
            }
        }
        if (d.kind == Kind.INCOME) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kicker("Категория дохода")
                ChipFlow {
                    c.d.categories.filter { it.income }.forEach { cat ->
                        Chip(cat.name, cat.id == d.incomeCat, { vm.draft = d.copy(incomeCat = cat.id) }, code = cat.code, accent = catColor(c, cat.id))
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("Дата")
            ChipFlow {
                (0..2).forEach { back ->
                    val day = c.todayDay - back
                    Chip(c.dayLabel(day), d.date == day, { vm.draft = d.copy(date = day) })
                }
                if (d.date < c.todayDay - 2) Chip(c.dayLabel(d.date), true, {})
            }
        }

        Field(null, d.note, { vm.draft = d.copy(note = it) }, placeholder = if (isT) "Комментарий к переводу" else "Название или комментарий (необязательно)")
        Keypad { vm.press(it) }
    }
}

// ——— ИИ-советник ———

@Composable
fun AdvisorOverlay(vm: AppViewModel, data: AppData) {
    val col = T.c
    val s = data.settings
    val p = Ai.provider(s.aiProvider)
    val payload = remember(data, vm.period, vm.cut) { vm.buildPayload() }
    OverlayScreen("ИИ-советник", "Закрыть", { vm.advisorOpen = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("Агент")
            Ai.PROVIDERS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { pr ->
                        val st = opt(pr.key == p.key)
                        Column(
                            Modifier
                                .weight(1f)
                                .background(st.bg)
                                .hairline(st.border)
                                .tap { vm.setProvider(pr.key) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                        ) {
                            Text(pr.name, style = T.b(12.5.sp, st.fg))
                            Text(
                                "${pr.vendor} · ${s.aiModels[pr.key]?.takeIf { it.isNotBlank() } ?: pr.defaultModel}",
                                style = T.b(10.sp, col.n600),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        val keyStatus = when {
            vm.apiKeyInput.isNotBlank() -> "Ключ введён (${vm.apiKeyInput.length} символов)"
            p.needsKey -> "Ключ не задан — ответ соберёт офлайн-разбор"
            else -> "Ключ не обязателен"
        }
        Field("API-ключ ${p.name}", vm.apiKeyInput, vm::setApiKey, placeholder = p.keyPrefix + "…", password = true, note = "$keyStatus · шифруется и хранится только на телефоне")
        Field("Модель", s.aiModels[p.key] ?: "", vm::setModel, placeholder = p.defaultModel, note = "Пусто — по умолчанию ${p.defaultModel}")
        if (p.key == "custom") {
            Field("Адрес endpoint (OpenAI-совместимый)", s.customEndpoint, vm::setEndpoint, placeholder = "http://192.168.1.10:11434/v1")
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("Что отправить агенту")
            ChipFlow {
                DATASETS.forEach { (k, label) ->
                    val on = k in s.aiSets
                    Chip("${if (on) "■" else "□"} $label", on, { vm.toggleSet(k) })
                }
            }
            Muted(
                "В запрос уйдёт ${payload.lines().size} строк данных, примерно ${payload.length / 3} токенов. " +
                    "Период — как на экране отчётов (${Calc(data).range(vm.period, vm.periodOffset).title}).",
                10.5f,
            )
        }

        Field("Вопрос", vm.question, { vm.question = it }, minLines = 3, placeholder = "Например: где я перетрачиваю и на чём реально сэкономить?")
        ChipFlow {
            PROMPTS.forEach { q -> SecondaryButton(q, { vm.question = q }, size = 11) }
        }

        PrimaryButton(if (vm.asking) "Агент думает…" else "Отправить агенту", { vm.ask() }, enabled = !vm.asking)

        if (vm.answer.isNotEmpty()) {
            Blueprint(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Ответ · ${vm.answerFrom}".uppercase(), style = T.h(11.sp, col.a700, 0.14.em), modifier = Modifier.weight(1f))
                    Text(vm.answerMeta, style = T.b(10.5.sp, col.n600))
                }
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(vm.answer, style = T.b(13.sp, col.text, lineHeight = 20.sp))
                }
            }
        }
    }
}

// ——— Редакторы ———

@Composable
fun AccEditOverlay(vm: AppViewModel, c: Calc, e: AccEdit) {
    OverlayScreen(
        if (e.id == null) "Новый счёт" else "Счёт",
        "Отмена",
        { vm.accEdit = null },
        footer = { PrimaryButton("Сохранить", { vm.saveAcc() }) },
    ) {
        Field("Название", e.name, { vm.accEdit = e.copy(name = it) }, placeholder = "Например, Карта · 4417")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("Тип")
            ChipFlow {
                listOf("Карта", "Кошелёк", "Накопления", "Вклад", "Другое").forEach { t -> Chip(t, e.type == t, { vm.accEdit = e.copy(type = t) }) }
            }
        }
        Field("Пометка", e.mask, { vm.accEdit = e.copy(mask = it) }, placeholder = "•• 4417 или «вклад»")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("Валюта")
            if (e.id == null) {
                CurrencyGrid(c.currencies, e.cur) { vm.accEdit = e.copy(cur = it) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.cur} ${Currencies.sym(e.cur)}", style = T.h(17.sp, T.c.text))
                    Spacer(Modifier.weight(1f))
                    SecondaryButton("Сменить валюту", { vm.curSheet = CurSheet.Acc(e.id) })
                }
            }
        }
        Field(
            "Текущий баланс, ${Currencies.sym(e.cur)}",
            e.balance,
            { vm.accEdit = e.copy(balance = it) },
            numeric = true,
            placeholder = "0",
            note = if (e.id != null) "Разница уйдёт в стартовый остаток — история операций не меняется." else null,
        )
        SettingRow("Учитывать в общем балансе") { Toggle(e.inTotal) { vm.accEdit = e.copy(inTotal = it) } }
        if (e.id != null) DangerButton("Удалить счёт", { vm.askDeleteAcc(e.id) })
    }
}

@Composable
fun CatEditOverlay(vm: AppViewModel, c: Calc, e: CatEdit) {
    OverlayScreen(
        when {
            e.id != null -> "Категория"
            e.income -> "Категория дохода"
            else -> "Категория расходов"
        },
        "Отмена",
        { vm.catEdit = null },
        footer = { PrimaryButton("Сохранить", { vm.saveCat() }) },
    ) {
        Field("Название", e.name, { vm.catEdit = e.copy(name = it) }, placeholder = if (e.income) "Например, Кэшбэк" else "Например, Питомцы")
        Field(
            "Код (две буквы)",
            e.code,
            { vm.catEdit = e.copy(code = it.take(2).uppercase()) },
            placeholder = e.name.filter { it.isLetter() }.take(2).uppercase().ifBlank { "ПТ" },
            note = "Показывается в квадратике рядом с операцией",
        )
        if (!e.income) {
            Field("Лимит в месяц, ${Currencies.sym(c.main)}", e.limit, { vm.catEdit = e.copy(limit = it) }, numeric = true, placeholder = "0 — без лимита")
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("Цвет категории")
            ChipFlow {
                Palette.COLORS.forEach { sw ->
                    val on = e.color.equals(sw.hex, ignoreCase = true)
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(hexColor(sw.hex, T.c.a600))
                            .hairline(if (on) T.c.text else T.c.divider, if (on) 2.dp else 1.dp)
                            .tap { vm.catEdit = e.copy(color = sw.hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Text("✓", style = T.h(15.sp, Color.White))
                    }
                }
            }
            Muted("Выбранный цвет: ${Palette.COLORS.firstOrNull { it.hex.equals(e.color, true) }?.name ?: "свой"} · виден в бюджете, отчётах и списке операций", 10.5f)
        }
        if (e.id != null) DangerButton("Удалить категорию", { vm.askDeleteCat(e.id) })
    }
}

@Composable
fun GoalEditOverlay(vm: AppViewModel, e: GoalEdit) {
    OverlayScreen(
        if (e.id == null) "Новая цель" else "Цель",
        "Отмена",
        { vm.goalEdit = null },
        footer = { PrimaryButton("Сохранить", { vm.saveGoal() }) },
    ) {
        Field("Название", e.name, { vm.goalEdit = e.copy(name = it) }, placeholder = "Например, Отпуск в Грузии")
        Field("Сколько нужно, ${Currencies.sym(e.cur)}", e.target, { vm.goalEdit = e.copy(target = it) }, numeric = true, placeholder = "0")
        if (e.id == null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kicker("Валюта цели")
                CurrencyGrid(vm.calc.currencies, e.cur) { vm.goalEdit = e.copy(cur = it) }
            }
        }
        Field("Заметка", e.hint, { vm.goalEdit = e.copy(hint = it) }, placeholder = "Например, хочу поехать в мае")
        if (e.id != null) DangerButton("Удалить цель", { vm.askDeleteGoal(e.id) })
    }
}

// ——— Добавление валюты ———

@Composable
fun CurrencyPickerOverlay(vm: AppViewModel, c: Calc) {
    val col = T.c
    val exclude = c.currencies.toSet()
    val found = Currencies.search(vm.currencyQuery, exclude).take(40)
    val draft = vm.currencyDraft
    OverlayScreen("Добавить валюту", "Закрыть", {
        vm.currencyPicker = false
        vm.currencyQuery = ""
        vm.currencyDraft = null
    }) {
        Field(null, vm.currencyQuery, { vm.currencyQuery = it }, placeholder = "Поиск: код или название (например, TMT или манат)")
        Muted("Курс подставится ориентировочный — проверьте и поправьте его в списке валют.", 10.5f)

        Column {
            found.forEach { info ->
                Row(
                    Modifier.fillMaxWidth().tap { vm.addCurrency(info.code) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(34.dp).hairline(col.divider), contentAlignment = Alignment.Center) {
                        Text(info.sym, style = T.h(15.sp, col.a700), maxLines = 1)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(info.name.replaceFirstChar { it.uppercase() }, style = T.b(14.sp, col.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${info.code} · ориентировочно ${fmtRate(Currencies.defaultRate(info.code))} ₽", style = T.b(11.sp, col.n600))
                    }
                    Text("Добавить", style = T.h(12.sp, col.a700))
                }
                SoftDivider()
            }
            if (found.isEmpty()) Muted("В каталоге ничего не нашлось — заведите свою валюту ниже", 12f)
        }

        SectionTitle("Своя валюта")
        if (draft == null) {
            SecondaryButton("Завести валюту вручную", { vm.currencyDraft = CurrencyDraft() }, Modifier.fillMaxWidth(), size = 13, upper = true)
        } else {
            Field("Код", draft.code, { vm.currencyDraft = draft.copy(code = it.uppercase()) }, placeholder = "Например, TMT")
            Field("Символ", draft.sym, { vm.currencyDraft = draft.copy(sym = it) }, placeholder = "m, ₼, $…")
            Field("Название", draft.name, { vm.currencyDraft = draft.copy(name = it) }, placeholder = "туркменские манаты")
            Field("Курс: сколько рублей за единицу", draft.rate, { vm.currencyDraft = draft.copy(rate = it) }, numeric = true, placeholder = "26,3")
            PrimaryButton("Добавить валюту", { vm.saveCustomCurrency() })
        }
    }
}

// ——— Нижние листы ———

@Composable
fun CurrencySheet(vm: AppViewModel, c: Calc, sheet: CurSheet) {
    val col = T.c
    BottomSheet({ vm.curSheet = null }) {
        val title = when (sheet) {
            CurSheet.Main -> "Основная валюта приложения"
            is CurSheet.Acc -> "Валюта счёта «${c.acc(sheet.id)?.name ?: ""}»"
        }
        Text(title.uppercase(), style = T.h(13.sp, col.text, 0.12.em))
        val selected = when (sheet) {
            CurSheet.Main -> c.main
            is CurSheet.Acc -> c.acc(sheet.id)?.cur
        }
        CurrencyGrid(c.currencies, selected) { code ->
            when (sheet) {
                CurSheet.Main -> vm.setMainCur(code)
                is CurSheet.Acc -> vm.setAccCur(sheet.id, code)
            }
        }
        Muted(
            if (sheet is CurSheet.Acc) "Баланс и операции счёта пересчитаются по курсу из настроек." else "Отчёты и бюджеты пересчитаются в выбранную валюту.",
            11f,
        )
    }
}

@Composable
fun GoalContributeSheet(vm: AppViewModel, c: Calc, gs: GoalSheet) {
    val col = T.c
    val g = c.d.goals.firstOrNull { it.id == gs.goalId } ?: return
    BottomSheet({ vm.goalSheet = null }) {
        Text("Отложить на цель".uppercase(), style = T.h(13.sp, col.text, 0.12.em))
        Text("${g.name} · ${c.fmt(g.saved, g.cur)} из ${c.fmt(g.target, g.cur)}", style = T.b(12.sp, col.n700))
        Kicker("Сумма")
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            vm.goalPresets(g.cur).forEach { a ->
                val st = opt(a == gs.amount)
                Box(
                    Modifier
                        .weight(1f)
                        .background(st.bg)
                        .hairline(st.border)
                        .tap { vm.goalSheet = gs.copy(amount = a) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(c.fmt(a, g.cur), style = T.h(12.sp, st.fg), maxLines = 1) }
            }
        }
        Kicker("Со счёта")
        ChipFlow {
            c.d.accounts.forEach { a -> Chip("${a.name} · ${Currencies.sym(a.cur)}", a.id == gs.from, { vm.goalSheet = gs.copy(from = a.id) }) }
        }
        PrimaryButton("Отложить ${c.fmt(gs.amount, g.cur)}", { vm.contribute() })
    }
}

@Composable
fun ConfirmSheet(vm: AppViewModel, cf: Confirm) {
    val col = T.c
    BottomSheet({ vm.confirm = null }) {
        Text(cf.title.uppercase(), style = T.h(13.sp, col.text, 0.12.em))
        Text(cf.text, style = T.b(13.sp, col.n700, lineHeight = 19.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .hairline(col.divider)
                    .tap { vm.confirm = null }
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("ОТМЕНА", style = T.h(13.sp, col.text, 0.1.em)) }
            DangerButton(cf.action, {
                vm.confirm = null
                cf.onYes()
            }, Modifier.weight(1f))
        }
    }
}
