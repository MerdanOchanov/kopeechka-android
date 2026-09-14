package app.kopeechka.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.AccEdit
import app.kopeechka.finance.AppViewModel
import app.kopeechka.finance.CatEdit
import app.kopeechka.finance.Confirm
import app.kopeechka.finance.CurSheet
import app.kopeechka.finance.CurrencyDraft
import app.kopeechka.finance.Draft
import app.kopeechka.finance.GoalEdit
import app.kopeechka.finance.GoalSheet
import app.kopeechka.finance.Kind
import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.Csv
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.data.Palette
import app.kopeechka.finance.data.Period
import app.kopeechka.finance.net.Ai
import app.kopeechka.finance.ui.theme.T

private val DATASETS = listOf(
    "ops" to "ai.set.ops",
    "budgets" to "ai.set.budgets",
    "accounts" to "ai.set.accounts",
    "goals" to "ai.set.goals",
    "report" to "ai.set.report",
)

private val PROMPTS = listOf("ai.prompt.1", "ai.prompt.2", "ai.prompt.3")

/** Подпись + горизонтальная лента: не растёт вниз, сколько бы счетов или категорий ни было. */
@Composable
private fun Lane(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Kicker(label)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

// ——— Новая операция: помещается в экран целиком ———

@Composable
fun AddOverlay(vm: AppViewModel, c: Calc, d: Draft) {
    val col = T.c
    val l = T.l
    val src = c.acc(d.from)
    val dst = c.acc(d.to)
    val srcCur = src?.cur ?: c.main
    val v = d.amount.toLongOrNull()?.toDouble() ?: 0.0
    val isT = d.kind == Kind.TRANSFER

    Column(Modifier.fillMaxSize().background(col.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    isT -> l.t("add.transfer")
                    d.editId != null -> l.t("add.one")
                    else -> l.t("add.new")
                }.uppercase(),
                style = T.h(15.sp, col.text, 0.12.em),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            GhostButton(l.t("common.cancel"), { vm.draft = null }, size = 13)
        }
        Divider()

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            JoinedSegments(Kind.entries.map { l.t(it.key) }, d.kind.ordinal) { vm.draft = d.copy(kind = Kind.entries[it]) }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(col.n100)
                    .hairline(col.divider)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    when (d.kind) {
                        Kind.TRANSFER -> l.t("add.transferLower")
                        Kind.INCOME -> l.t("add.incomeLower")
                        Kind.EXPENSE -> l.t("add.expenseLower")
                    },
                    style = T.b(12.sp, col.n600),
                    modifier = Modifier.weight(1f).padding(bottom = 5.dp),
                    maxLines = 1,
                )
                if (v > 0 && srcCur != c.main) {
                    Text(
                        l.t("add.approxMain", c.fmtMain(c.toMain(v, srcCur))),
                        style = T.b(10.5.sp, col.n600),
                        modifier = Modifier.padding(bottom = 6.dp),
                        maxLines = 1,
                    )
                }
                Text(
                    if (d.amount.isEmpty()) "0" else Currencies.fmtNumber(v),
                    style = T.h(34.sp, if (d.amount.isEmpty()) col.n400 else col.text, lineHeight = 36.sp),
                    maxLines = 1,
                )
                Text(Currencies.sym(srcCur), style = T.b(16.sp, col.n700), modifier = Modifier.padding(bottom = 4.dp))
            }

            // курс пары правится прямо здесь — удобно, когда купил валюту по своему курсу
            val rateTo = if (isT) (dst?.cur ?: c.main) else c.main
            if (srcCur != rateTo) {
                var rateText by remember(srcCur, rateTo, c.rate(srcCur), c.rate(rateTo)) {
                    mutableStateOf(fmtRate(c.conv(1.0, srcCur, rateTo)))
                }
                Row(
                    Modifier.fillMaxWidth().hairline(col.divider).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(l.t("add.rate"), style = T.b(12.sp, col.n700))
                    Spacer(Modifier.weight(1f))
                    Text("1 ${Currencies.sym(srcCur)} =", style = T.b(13.sp, col.text))
                    Box(Modifier.width(104.dp)) {
                        Field(null, rateText, { rateText = it; vm.setPairRate(srcCur, rateTo, it) }, numeric = true, placeholder = "0")
                    }
                    Text(Currencies.sym(rateTo), style = T.b(13.sp, col.n700))
                }
            }

            Lane(if (isT) l.t("add.fromAcc") else l.t("add.account")) {
                c.d.accounts.forEach { a ->
                    Chip("${a.name} · ${Currencies.sym(a.cur)}", a.id == d.from, { vm.draft = d.copy(from = a.id) })
                }
            }

            if (isT) {
                Lane(l.t("add.toAcc")) {
                    c.d.accounts.forEach { a ->
                        Chip("${a.name} · ${Currencies.sym(a.cur)}", a.id == d.to, { vm.draft = d.copy(to = a.id) })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().hairline(col.divider).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(l.t("add.willGet"), style = T.b(12.sp, col.n700))
                    Spacer(Modifier.weight(1f))
                    Text(if (dst != null) c.fmt(c.conv(v, srcCur, dst.cur), dst.cur) else l.t("common.dash"), style = T.h(16.sp, col.text))
                }
            }

            if (d.kind == Kind.EXPENSE) {
                Lane(l.t("add.category")) {
                    c.d.categories.filter { !it.income }.forEach { cat ->
                        Chip(cat.name, cat.id == d.cat, { vm.draft = d.copy(cat = cat.id) }, code = cat.code, accent = catColor(c, cat.id))
                    }
                }
            }
            if (d.kind == Kind.INCOME) {
                Lane(l.t("add.incomeCategory")) {
                    c.d.categories.filter { it.income }.forEach { cat ->
                        Chip(cat.name, cat.id == d.incomeCat, { vm.draft = d.copy(incomeCat = cat.id) }, code = cat.code, accent = catColor(c, cat.id))
                    }
                }
            }

            Lane(l.t("add.date")) {
                (0..6).forEach { back ->
                    val day = c.todayDay - back
                    Chip(c.dayLabel(day), d.date == day, { vm.draft = d.copy(date = day) })
                }
                if (d.date < c.todayDay - 6) Chip(c.dayLabel(d.date), true, {})
            }

            Field(
                null,
                d.note,
                { vm.draft = d.copy(note = it) },
                placeholder = if (isT) l.t("add.noteTransferHint") else l.t("add.noteHint"),
            )

            Keypad(Modifier.weight(1f), fill = true) { vm.press(it) }
        }

        Divider()
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (d.editId != null) DangerButton(l.t("common.delete"), { vm.askDeleteTx(d.editId) }, Modifier.weight(0.45f))
            PrimaryButton(
                if (isT) l.t("add.saveTransfer") else l.t("common.save"),
                { vm.saveDraft() },
                Modifier.weight(1f),
                enabled = d.amount.isNotEmpty(),
            )
        }
    }
}

// ——— ИИ-советник ———

@Composable
fun AdvisorOverlay(vm: AppViewModel, data: AppData) {
    val col = T.c
    val l = T.l
    val s = data.settings
    val p = Ai.provider(s.aiProvider)
    val payload = remember(data, vm.period, vm.cut, vm.periodOffset) { vm.buildPayload() }
    val range = remember(data, vm.period, vm.periodOffset) { Calc(data, l = l).range(vm.period, vm.periodOffset) }
    OverlayScreen(l.t("ai.title"), l.t("common.close"), { vm.advisorOpen = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("ai.agent"))
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
                            Text(pr.name(l), style = T.b(12.5.sp, st.fg))
                            Text(
                                "${pr.vendor(l)} · ${s.aiModels[pr.key]?.takeIf { it.isNotBlank() } ?: pr.defaultModel}",
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
            vm.apiKeyInput.isNotBlank() -> l.t("ai.keySet", vm.apiKeyInput.length)
            p.needsKey -> l.t("ai.keyNone")
            else -> l.t("ai.keyOptional")
        }
        Field(
            l.t("ai.key", p.name(l)),
            vm.apiKeyInput,
            vm::setApiKey,
            placeholder = p.keyPrefix + "…",
            password = true,
            note = l.t("ai.keyNote", keyStatus),
        )
        Field(l.t("ai.model"), s.aiModels[p.key] ?: "", vm::setModel, placeholder = p.defaultModel, note = l.t("ai.modelNote", p.defaultModel))
        if (p.key == "custom") {
            Field(l.t("ai.endpoint"), s.customEndpoint, vm::setEndpoint, placeholder = "http://192.168.1.10:11434/v1")
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("ai.send"))
            ChipFlow {
                DATASETS.forEach { (k, labelKey) ->
                    val on = k in s.aiSets
                    Chip("${if (on) "■" else "□"} ${l.t(labelKey)}", on, { vm.toggleSet(k) })
                }
            }
            Muted(l.t("ai.payloadNote", payload.lines().size, payload.length / 3, range.title), 10.5f)
        }

        Field(l.t("ai.question"), vm.question, { vm.question = it }, minLines = 3, placeholder = l.t("ai.questionHint"))
        ChipFlow {
            PROMPTS.forEach { q -> SecondaryButton(l.t(q), { vm.question = l.t(q) }, size = 11) }
        }

        PrimaryButton(if (vm.asking) l.t("ai.thinking") else l.t("ai.ask"), { vm.ask() }, enabled = !vm.asking)

        if (vm.answer.isNotEmpty()) {
            Blueprint(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(l.t("ai.answer", vm.answerFrom).uppercase(), style = T.h(11.sp, col.a700, 0.14.em), modifier = Modifier.weight(1f))
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

// ——— Добавление валюты ———

@Composable
fun CurrencyPickerOverlay(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val exclude = c.currencies.toSet()
    val found = Currencies.search(vm.currencyQuery, exclude).take(40)
    val draft = vm.currencyDraft
    OverlayScreen(l.t("cur.addTitle"), l.t("common.close"), {
        vm.currencyPicker = false
        vm.currencyQuery = ""
        vm.currencyDraft = null
    }) {
        Field(null, vm.currencyQuery, { vm.currencyQuery = it }, placeholder = l.t("cur.search"))
        Muted(l.t("cur.rateNote"), 10.5f)

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
                        Text(
                            l.t("cur.approxRate", info.code, fmtRate(Currencies.hintRate(info.code, c.main)), Currencies.sym(c.main)),
                            style = T.b(11.sp, col.n600),
                        )
                    }
                    Text(l.t("common.add"), style = T.h(12.sp, col.a700))
                }
                SoftDivider()
            }
            if (found.isEmpty()) Muted(l.t("cur.notFound"), 12f)
        }

        SectionTitle(l.t("cur.ownTitle"))
        if (draft == null) {
            SecondaryButton(l.t("cur.ownStart"), { vm.currencyDraft = CurrencyDraft() }, Modifier.fillMaxWidth(), size = 13, upper = true)
        } else {
            Field(l.t("cur.code"), draft.code, { vm.currencyDraft = draft.copy(code = it.uppercase()) }, placeholder = l.t("cur.codeHint"))
            Field(l.t("cur.sym"), draft.sym, { vm.currencyDraft = draft.copy(sym = it) }, placeholder = l.t("cur.symHint"))
            Field(l.t("cur.name"), draft.name, { vm.currencyDraft = draft.copy(name = it) }, placeholder = Currencies.info("TMT").name)
            Field(
                l.t("cur.rate", c.main),
                draft.rate,
                { vm.currencyDraft = draft.copy(rate = it) },
                numeric = true,
                placeholder = fmtRate(Currencies.hintRate("TMT", c.main)),
            )
            PrimaryButton(l.t("cur.add").removePrefix("+ "), { vm.saveCustomCurrency() })
        }
    }
}

// ——— Редакторы ———

@Composable
fun AccEditOverlay(vm: AppViewModel, c: Calc, e: AccEdit) {
    val l = T.l
    val types = listOf("acc.type.card", "acc.type.cash", "acc.type.savings", "acc.type.deposit", "acc.type.other")
    OverlayScreen(
        if (e.id == null) l.t("acc.new") else l.t("acc.one"),
        l.t("common.cancel"),
        { vm.accEdit = null },
        footer = { PrimaryButton(l.t("common.save"), { vm.saveAcc() }) },
    ) {
        Field(l.t("acc.name"), e.name, { vm.accEdit = e.copy(name = it) }, placeholder = l.t("acc.nameHint"))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("acc.type"))
            ChipFlow {
                types.forEach { key ->
                    val label = l.t(key)
                    Chip(label, e.type == label, { vm.accEdit = e.copy(type = label) })
                }
            }
        }
        Field(l.t("acc.mask"), e.mask, { vm.accEdit = e.copy(mask = it) }, placeholder = l.t("acc.maskHint"))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("acc.cur"))
            if (e.id == null) {
                CurrencyGrid(c.currencies, e.cur) { vm.accEdit = e.copy(cur = it) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.cur} ${Currencies.sym(e.cur)}", style = T.h(17.sp, T.c.text))
                    Spacer(Modifier.weight(1f))
                    SecondaryButton(l.t("acc.changeCur"), { vm.curSheet = CurSheet.Acc(e.id) })
                }
            }
        }
        Field(
            l.t("acc.balance", Currencies.sym(e.cur)),
            e.balance,
            { vm.accEdit = e.copy(balance = it) },
            numeric = true,
            placeholder = "0",
            note = if (e.id != null) l.t("acc.balanceNote") else null,
        )
        SettingRow(l.t("acc.inTotalToggle")) { Toggle(e.inTotal) { vm.accEdit = e.copy(inTotal = it) } }
        if (e.id != null) DangerButton(l.t("acc.delete"), { vm.askDeleteAcc(e.id) })
    }
}

@Composable
fun CatEditOverlay(vm: AppViewModel, c: Calc, e: CatEdit) {
    val l = T.l
    OverlayScreen(
        when {
            e.id != null -> l.t("cat.one")
            e.income -> l.t("cat.newIncome")
            else -> l.t("cat.newExpense")
        },
        l.t("common.cancel"),
        { vm.catEdit = null },
        footer = { PrimaryButton(l.t("common.save"), { vm.saveCat() }) },
    ) {
        Field(
            l.t("cat.name"),
            e.name,
            { vm.catEdit = e.copy(name = it) },
            placeholder = if (e.income) l.t("cat.nameHintIncome") else l.t("cat.nameHintExpense"),
        )
        Field(
            l.t("cat.code"),
            e.code,
            { vm.catEdit = e.copy(code = it.take(2).uppercase()) },
            placeholder = e.name.filter { it.isLetter() }.take(2).uppercase().ifBlank { l.t("cat.codeHint") },
            note = l.t("cat.codeNote"),
        )
        if (!e.income) {
            Field(l.t("cat.limit", Currencies.sym(c.main)), e.limit, { vm.catEdit = e.copy(limit = it) }, numeric = true, placeholder = l.t("cat.limitHint"))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("cat.color"))
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
            Muted(l.t("cat.colorNote", Palette.COLORS.firstOrNull { it.hex.equals(e.color, true) }?.name(l) ?: l.t("cat.colorOwn")), 10.5f)
        }
        if (e.id != null) DangerButton(l.t("cat.delete"), { vm.askDeleteCat(e.id) })
    }
}

@Composable
fun GoalEditOverlay(vm: AppViewModel, e: GoalEdit) {
    val l = T.l
    OverlayScreen(
        if (e.id == null) l.t("goal.new") else l.t("goal.one"),
        l.t("common.cancel"),
        { vm.goalEdit = null },
        footer = { PrimaryButton(l.t("common.save"), { vm.saveGoal() }) },
    ) {
        Field(l.t("goal.name"), e.name, { vm.goalEdit = e.copy(name = it) }, placeholder = l.t("goal.nameHint"))
        Field(l.t("goal.amount", Currencies.sym(e.cur)), e.target, { vm.goalEdit = e.copy(target = it) }, numeric = true, placeholder = "0")
        if (e.id == null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kicker(l.t("goal.cur"))
                CurrencyGrid(vm.calc.currencies, e.cur) { vm.goalEdit = e.copy(cur = it) }
            }
        }
        Field(l.t("goal.hint"), e.hint, { vm.goalEdit = e.copy(hint = it) }, placeholder = l.t("goal.hintHint"))
        if (e.id != null) DangerButton(l.t("goal.delete"), { vm.askDeleteGoal(e.id) })
    }
}

// ——— Нижние листы ———

@Composable
fun CurrencySheet(vm: AppViewModel, c: Calc, sheet: CurSheet) {
    val col = T.c
    val l = T.l
    BottomSheet({ vm.curSheet = null }) {
        val title = when (sheet) {
            CurSheet.Main -> l.t("cur.sheetMain")
            is CurSheet.Acc -> l.t("cur.sheetAcc", c.acc(sheet.id)?.name ?: "")
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
        Muted(if (sheet is CurSheet.Acc) l.t("cur.sheetAccNote") else l.t("cur.sheetMainNote"), 11f)
    }
}

@Composable
fun GoalContributeSheet(vm: AppViewModel, c: Calc, gs: GoalSheet) {
    val col = T.c
    val l = T.l
    val g = c.d.goals.firstOrNull { it.id == gs.goalId } ?: return
    BottomSheet({ vm.goalSheet = null }) {
        Text(l.t("goal.sheetTitle").uppercase(), style = T.h(13.sp, col.text, 0.12.em))
        Text(l.t("goal.sheetSub", g.name, c.fmt(g.saved, g.cur), c.fmt(g.target, g.cur)), style = T.b(12.sp, col.n700))
        Kicker(l.t("goal.sum"))
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
        Kicker(l.t("goal.fromAcc"))
        ChipFlow {
            c.d.accounts.forEach { a -> Chip("${a.name} · ${Currencies.sym(a.cur)}", a.id == gs.from, { vm.goalSheet = gs.copy(from = a.id) }) }
        }
        PrimaryButton(l.t("goal.putSum", c.fmt(gs.amount, g.cur)), { vm.contribute() })
    }
}

/** Выбор периода выгрузки в CSV. */
@Composable
fun CsvExportSheet(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    BottomSheet({ vm.csvExportSheet = false }) {
        Text(l.t("csv.periodTitle").uppercase(), style = T.h(13.sp, col.text, 0.12.em))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Period.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { p ->
                        Column(
                            Modifier
                                .weight(1f)
                                .hairline(col.divider)
                                .tap { vm.exportCsv(p.name) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                        ) {
                            Text(l.t(p.key), style = T.b(13.sp, col.text))
                            Text(c.range(p, 0).title, style = T.b(10.5.sp, col.n600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .hairline(col.divider)
                    .tap { vm.exportCsv("all") }
                    .padding(horizontal = 10.dp, vertical = 11.dp),
            ) {
                Text(l.t("csv.all"), style = T.b(13.sp, col.text))
            }
        }
        Muted(l.t("csv.note"), 10.5f)
    }
}

/** Что нашлось в файле перед загрузкой. */
@Composable
fun CsvImportSheet(vm: AppViewModel, p: Csv.Preview) {
    val col = T.c
    val l = T.l
    BottomSheet({ vm.csvPreview = null }) {
        Text(l.t("csv.importTitle").uppercase(), style = T.h(13.sp, col.text, 0.12.em))
        Text(l.t("csv.found", p.rows.size), style = T.h(17.sp, col.text))
        if (p.errors.isNotEmpty()) {
            Text(l.t("csv.errorsFound", p.errors.size), style = T.b(12.sp, col.danger))
            p.errors.take(4).forEach { Muted(it, 11f) }
        }
        if (p.skipped > 0) Muted(l.t("csv.skipped", p.skipped), 11f)
        if (p.newAccounts.isNotEmpty()) Muted(l.t("csv.newAccounts", p.newAccounts.joinToString(", ")), 11f)
        if (p.newCats.isNotEmpty()) Muted(l.t("csv.newCats", p.newCats.joinToString(", ")), 11f)
        PrimaryButton(l.t("csv.apply", l.n(p.rows.size, "op")), { vm.applyImport() }, enabled = p.rows.isNotEmpty())
    }
}

@Composable
fun ConfirmSheet(vm: AppViewModel, cf: Confirm) {
    val col = T.c
    val l = T.l
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
            ) { Text(l.t("common.cancelUpper"), style = T.h(13.sp, col.text, 0.1.em)) }
            DangerButton(cf.action, {
                vm.confirm = null
                cf.onYes()
            }, Modifier.weight(1f))
        }
    }
}
