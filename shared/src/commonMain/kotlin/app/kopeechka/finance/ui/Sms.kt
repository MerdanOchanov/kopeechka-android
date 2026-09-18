package app.kopeechka.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.kopeechka.finance.SmsEdit
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.SmsPresets
import app.kopeechka.finance.ui.theme.T

/** Список правил: от кого приходят банковские СМС и к какому счёту относятся. */
@Composable
fun SmsPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    ScreenColumn(gap = 18.dp) {
        PageHeader(l.t("bank.title")) { vm.page = null }
        Muted(l.t("sms.note"), 11.5f)
        if (c.s.bankPush && !vm.pushAccess()) {
            // переключатель включён, а система доступ не дала — объясняем и ведём в настройки
            Muted(l.t("push.noAccessLong"), 11.5f, color = col.danger)
            SecondaryButton(l.t("push.openSettings"), { vm.openPushSettings() }, Modifier.fillMaxWidth(), size = 13, upper = true)
        }

        SectionTitle(l.t("sms.sources")) { GhostButton(l.t("sms.add"), { vm.openSmsSource(null) }) }
        if (c.d.smsSources.isEmpty()) {
            Muted(l.t("sms.empty"), 12f)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                c.d.smsSources.forEach { src ->
                    val acc = c.acc(src.accId)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(col.surface)
                            .hairline(col.divider)
                            .tap { vm.openSmsSource(src.id) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(src.name, style = T.h(14.sp, if (src.enabled) col.text else col.n500), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    c.d.pushApps[src.sender] ?: src.sender,
                                    src.cardMask.takeIf { it.isNotBlank() }?.let { "•$it" },
                                    acc?.let { "${it.name} · ${Currencies.sym(it.cur)}" },
                                    if (src.auto) l.t("sms.autoShort") else null,
                                ).joinToString(" · "),
                                style = T.b(11.sp, col.n600),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Toggle(src.enabled) { vm.toggleSmsSource(src.id) }
                    }
                }
            }
        }

        SectionTitle(l.t("sms.history"))
        Muted(l.t("sms.historyNote"), 11.5f)
        GhostButton(if (vm.smsBusy) l.t("sms.reading") else l.t("sms.importNow"), { if (!vm.smsBusy) vm.importSmsHistory() }, size = 13)
    }
}

/** Правка одного правила плюс проверка на настоящем тексте. */
@Composable
fun SmsEditOverlay(vm: AppViewModel, c: Calc, e: SmsEdit) {
    val col = T.c
    val l = T.l
    OverlayScreen(
        if (e.id == null) l.t("sms.new") else l.t("sms.one"),
        l.t("common.cancel"),
        { vm.smsEdit = null },
        footer = { PrimaryButton(l.t("common.save"), { vm.saveSmsSource() }) },
    ) {
        if (e.id == null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kicker(l.t("sms.presets"))
                ChipFlow {
                    SmsPresets.TURKMENISTAN.forEach { p -> Chip(p.name, e.name == p.name, { vm.applySmsPreset(p) }) }
                }
                Muted(l.t("sms.presetsNote"), 11f)
            }
        }
        Field(l.t("sms.name"), e.name, { vm.smsEdit = e.copy(name = it) }, placeholder = l.t("sms.nameHint"))
        if (c.d.pushApps.isNotEmpty()) {
            // банки, которые уже присылали уведомления с суммой: выбрать проще, чем вписать пакет
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kicker(l.t("push.apps"))
                ChipFlow {
                    c.d.pushApps.forEach { (pkg, label) ->
                        Chip(label, e.sender == pkg, { vm.smsEdit = e.copy(sender = pkg, name = e.name.ifBlank { label }) })
                    }
                }
            }
        }
        Field(
            l.t("sms.sender"),
            c.d.pushApps[e.sender] ?: e.sender,
            { vm.smsEdit = e.copy(sender = it) },
            placeholder = l.t("sms.senderHint"),
            note = l.t("sms.senderNote"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("sms.acc"))
            ChipFlow {
                c.d.accounts.forEach { a ->
                    Chip("${a.name} · ${Currencies.sym(a.cur)}", a.id == e.accId, { vm.smsEdit = e.copy(accId = a.id) })
                }
            }
            Muted(l.t("sms.accNote"), 11f)
        }
        Field(
            l.t("sms.card"),
            e.cardMask,
            { vm.smsEdit = e.copy(cardMask = it.filter { ch -> ch.isDigit() }.take(4)) },
            numeric = true,
            placeholder = "1234",
            note = l.t("sms.cardNote"),
        )
        Field(l.t("sms.expenseWords"), e.expenseWords, { vm.smsEdit = e.copy(expenseWords = it) }, minLines = 2)
        Field(l.t("sms.incomeWords"), e.incomeWords, { vm.smsEdit = e.copy(incomeWords = it) }, minLines = 2)
        Field(
            l.t("sms.ignoreWords"),
            e.ignoreWords,
            { vm.smsEdit = e.copy(ignoreWords = it) },
            minLines = 2,
            note = l.t("sms.ignoreNote"),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.t("sms.auto"), style = T.b(13.sp, col.text))
                Muted(l.t("sms.autoNote"), 11f)
            }
            Toggle(e.auto) { vm.smsEdit = e.copy(auto = !e.auto) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(l.t("sms.test"))
            Field(null, vm.smsTest, { vm.smsTest = it }, minLines = 3, placeholder = l.t("sms.testHint"))
            val result = vm.smsTestResult()
            if (result.isNotEmpty()) {
                Text(
                    result,
                    style = T.b(12.sp, col.accent),
                    modifier = Modifier.fillMaxWidth().background(col.surface).hairline(col.divider).padding(10.dp),
                )
            }
        }

        if (e.id != null) DangerButton(l.t("sms.delete"), { vm.askDeleteSmsSource(e.id) })
    }
}
