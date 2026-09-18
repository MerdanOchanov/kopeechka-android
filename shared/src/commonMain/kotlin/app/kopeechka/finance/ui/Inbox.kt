package app.kopeechka.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.AppViewModel
import app.kopeechka.finance.ImageSource
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.Currencies
import app.kopeechka.finance.data.INBOX_PUSH
import app.kopeechka.finance.data.INBOX_QR
import app.kopeechka.finance.data.INBOX_RECURRING
import app.kopeechka.finance.data.INBOX_SMS
import app.kopeechka.finance.data.InboxItem
import app.kopeechka.finance.ui.theme.T

/**
 * «На проверку» — то, о чём приложение догадалось само: чек с фотографии
 * или банковская СМС. Пока человек не подтвердил, в деньгах этого нет.
 */
@Composable
fun InboxOverlay(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val items = c.d.inbox
    OverlayScreen(l.t("inbox.title"), l.t("common.close"), { vm.inboxOpen = false }) {
        if (items.isEmpty()) {
            Muted(l.t("inbox.empty"), 12f)
        } else {
            Muted(l.t("inbox.note"), 11f)
            items.forEach { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(col.surface)
                        .hairline(col.divider)
                        .tap { vm.inboxEdit = item }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.title.ifBlank { l.t("inbox.receipt") },
                            style = T.h(14.sp, col.text),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            c.fmt(item.amount, item.cur.ifBlank { c.main }),
                            style = T.h(14.sp, if (item.income) col.accent else col.text),
                            maxLines = 1,
                        )
                    }
                    Text(
                        listOf(
                            l.t(
                                when (item.source) {
                                    INBOX_SMS -> "inbox.fromSms"
                                    INBOX_PUSH -> "inbox.fromPush"
                                    INBOX_RECURRING -> "inbox.fromRecurring"
                                    INBOX_QR -> "inbox.fromQr"
                                    else -> "inbox.fromPhoto"
                                },
                            ),
                            c.dayLabel(item.date),
                            c.cat(item.cat).name,
                        ).joinToString(" · "),
                        style = T.b(11.sp, col.n600),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Проверка и правка одного черновика перед записью. */
@Composable
fun InboxEditSheet(vm: AppViewModel, c: Calc, item: InboxItem) {
    val col = T.c
    val l = T.l
    val cur = item.cur.ifBlank { c.accCur(item.accId) }
    BottomSheet({ vm.inboxEdit = null }) {
        Text(l.t("inbox.check").uppercase(), style = T.h(13.sp, col.text, 0.12.em))
        Field(l.t("inbox.what"), item.title, { t -> vm.editInbox { it.copy(title = t) } })
        Field(
            l.t("inbox.sum", Currencies.sym(cur)),
            if (item.amount > 0) vm.numText(item.amount) else "",
            { t -> vm.editInbox { it.copy(amount = vm.numOf(t)) } },
            numeric = true,
            placeholder = "0",
        )
        Kicker(l.t("inbox.toAcc"))
        ChipFlow {
            c.d.accounts.forEach { a ->
                Chip("${a.name} · ${Currencies.sym(a.cur)}", a.id == item.accId, { vm.editInbox { it.copy(accId = a.id) } })
            }
        }
        if (item.cash) {
            // снятие наличных: куда легли деньги — или это всё-таки трата
            Kicker(l.t("sms.cashTo"))
            ChipFlow {
                c.d.accounts.filter { it.id != item.accId }.forEach { a ->
                    Chip("${a.name} · ${Currencies.sym(a.cur)}", a.id == item.toAcc, { vm.editInbox { it.copy(toAcc = a.id) } })
                }
                Chip(l.t("sms.cashAsExpense"), item.toAcc.isBlank(), { vm.editInbox { it.copy(toAcc = "") } })
            }
            Field(
                l.t("sms.fee", Currencies.sym(cur)),
                if (item.fee > 0) vm.numText(item.fee) else "",
                { t -> vm.editInbox { it.copy(fee = vm.numOf(t)) } },
                numeric = true,
                placeholder = "0",
            )
        }
        if (!item.cash || item.toAcc.isBlank()) {
            Kicker(l.t("inbox.cat"))
            ChipFlow {
                c.d.categories.filter { it.income == item.income }.forEach { cat ->
                    Chip(cat.name, cat.id == item.cat, { vm.editInbox { it.copy(cat = cat.id) } })
                }
            }
        }
        if (item.raw.isNotBlank()) {
            Kicker(l.t("inbox.raw"))
            Text(item.raw, style = T.b(11.sp, col.n600), maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
        PrimaryButton(l.t("inbox.accept"), { vm.acceptInbox() }, enabled = item.amount > 0)
        DangerButton(l.t("inbox.drop"), { vm.dropInbox(item.id) })
    }
}

/** Откуда взять снимок чека. */
@Composable
fun ScanSourceSheet(vm: AppViewModel) {
    val col = T.c
    val l = T.l
    BottomSheet({ vm.scanSheet = false }) {
        Text(l.t("inbox.scanTitle").uppercase(), style = T.h(13.sp, col.text, 0.12.em))

        // QR-код: без интернета и без ключа — годится везде, где на чеке он есть
        Kicker(l.t("qr.title"))
        Muted(l.t("qr.note"), 11f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(l.t("inbox.camera"), { vm.scanSheet = false; vm.scanQr(ImageSource.CAMERA) }, Modifier.weight(1f))
            SecondaryButton(l.t("inbox.gallery"), { vm.scanSheet = false; vm.scanQr(ImageSource.GALLERY) }, Modifier.weight(1f), size = 12, upper = true)
        }

        Kicker(l.t("qr.aiTitle"))
        if (vm.canScan()) {
            Muted(l.t("inbox.scanNote"), 11f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(l.t("inbox.camera"), { vm.scanSheet = false; vm.scanReceipt(ImageSource.CAMERA) }, Modifier.weight(1f), size = 12, upper = true)
                SecondaryButton(l.t("inbox.gallery"), { vm.scanSheet = false; vm.scanReceipt(ImageSource.GALLERY) }, Modifier.weight(1f), size = 12, upper = true)
            }
        } else {
            Muted(l.t("qr.aiNoKey"), 11f)
        }
    }
}

/**
 * Полоски на главной: сколько черновиков ждёт и «чек по фото».
 * Одна колонка, а не два соседа: иначе ScreenColumn ставит лишний отступ между ними.
 */
@Composable
fun InboxBar(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val waiting = c.d.inbox.size
    val scan = vm.canScan() || vm.canScanQr
    val update = vm.update
    if (waiting == 0 && !scan && update == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (update != null) {
            BarRow(l.t("upd.bar", update.version), l.t("upd.download"), strong = true) { vm.downloadUpdate() }
        }
        if (waiting > 0) {
            BarRow(
                l.t("inbox.waiting", l.n(waiting, "draft")),
                "→",
                strong = true,
            ) { vm.inboxOpen = true }
        }
        if (scan) {
            BarRow(
                if (vm.scanning) l.t("inbox.scanning") else l.t("inbox.scan"),
                if (vm.scanning) "" else l.t("inbox.scanHint"),
                strong = false,
            ) { if (!vm.scanning) vm.scanSheet = true }
        }
    }
}

/** Строка-кнопка во всю ширину: подпись слева, подсказка справа. */
@Composable
private fun BarRow(title: String, hint: String, strong: Boolean, onClick: () -> Unit) {
    val col = T.c
    Row(
        Modifier
            .fillMaxWidth()
            .background(col.surface)
            .hairline(if (strong) col.accent else col.divider)
            .tap(onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = T.h(11.sp, if (strong) col.accent else col.a700, 0.1.em), maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (hint.isNotEmpty()) Text(hint, style = T.b(11.sp, col.n600), maxLines = 1)
    }
}
