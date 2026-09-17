package app.kopeechka.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.RequestStatus
import app.kopeechka.finance.data.SpendRequest
import app.kopeechka.finance.net.formatBackupTime
import app.kopeechka.finance.ui.theme.T

/** Полоска на главной: сколько заявок ждёт моего решения и сколько моих висит. */
@Composable
fun RequestsBar(vm: AppViewModel, c: Calc) {
    if (c.d.space == null) return
    val col = T.c
    val l = T.l
    val forMe = vm.requestsForMe().size
    val mine = vm.myRequests().count { it.status == RequestStatus.PENDING }
    if (forMe == 0 && mine == 0) return
    Row(
        Modifier
            .fillMaxWidth()
            .background(col.surface)
            .hairline(if (forMe > 0) col.accent else col.divider)
            .tap { vm.requestsOpen = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (if (forMe > 0) l.t("req.barForMe", forMe) else l.t("req.barMine", mine)).uppercase(),
            style = T.h(11.sp, if (forMe > 0) col.accent else col.n700, 0.1.em),
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text("→", style = T.h(13.sp, col.accent))
    }
}

/** Заявки: чужие — с кнопками решения, свои — со статусом и отменой. */
@Composable
fun RequestsOverlay(vm: AppViewModel, c: Calc) {
    val l = T.l
    OverlayScreen(l.t("req.title"), l.t("common.close"), { vm.requestsOpen = false }) {
        Muted(l.t("req.note"), 11.5f)

        val forMe = vm.requestsForMe()
        SectionTitle(l.t("req.forMe"))
        if (forMe.isEmpty()) {
            Muted(l.t("req.forMeEmpty"), 12f)
        } else {
            forMe.forEach { r ->
                RequestCard(c, r) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(l.t("req.approve"), { vm.approveRequest(r.id) }, Modifier.weight(1f))
                        SecondaryButton(l.t("req.decline"), { vm.declineRequest(r.id) }, Modifier.weight(1f), size = 13, upper = true)
                    }
                }
            }
        }

        val mine = vm.myRequests()
        SectionTitle(l.t("req.mine"))
        if (mine.isEmpty()) {
            Muted(l.t("req.mineEmpty"), 12f)
        } else {
            mine.forEach { r ->
                RequestCard(c, r) {
                    if (r.status == RequestStatus.PENDING) {
                        GhostButton(l.t("req.cancel"), { vm.cancelRequest(r.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(c: Calc, r: SpendRequest, actions: @Composable () -> Unit) {
    val col = T.c
    val l = T.l
    val acc = c.acc(r.accId)
    Column(
        Modifier.fillMaxWidth().background(col.surface).hairline(col.divider).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.title, style = T.h(14.sp, col.text), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.fmt(r.amount, acc?.cur ?: c.main), style = T.h(14.sp, col.text), maxLines = 1)
        }
        Text(
            listOfNotNull(
                r.byName.takeIf { it.isNotBlank() },
                acc?.name,
                c.cat(r.cat).name,
                if (r.at > 0) formatBackupTime(r.at, l) else null,
            ).joinToString(" · "),
            style = T.b(11.sp, col.n600),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val status = when (r.status) {
            RequestStatus.APPROVED -> l.t("req.st.approved", r.decidedByName)
            RequestStatus.DECLINED -> l.t("req.st.declined", r.decidedByName)
            RequestStatus.CANCELLED -> l.t("req.st.cancelled")
            else -> l.t("req.st.pending")
        }
        Text(
            status,
            style = T.b(11.sp, if (r.status == RequestStatus.DECLINED) col.danger else col.accent),
        )
        actions()
    }
}
