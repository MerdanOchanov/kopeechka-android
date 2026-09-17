package app.kopeechka.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.AppViewModel
import app.kopeechka.finance.WebDavEdit
import app.kopeechka.finance.data.Calc
import app.kopeechka.finance.data.SyncKind
import app.kopeechka.finance.net.formatBackupTime
import app.kopeechka.finance.ui.theme.T

/**
 * «Вместе»: двое ведут одни деньги с разных телефонов.
 *
 * Пока пространства нет — экран объясняет, что это, и предлагает завести его
 * или войти по коду. Дальше показывает код приглашения, участников и способы
 * обмена.
 */
@Composable
fun SyncPage(vm: AppViewModel, c: Calc) {
    val col = T.c
    val l = T.l
    val space = c.d.space
    // ушли с экрана — приём закрывается: открытый порт без присмотра не нужен
    DisposableEffect(Unit) { onDispose { vm.stopLanHost() } }
    ScreenColumn(gap = 18.dp) {
        PageHeader(l.t("sync.title")) { vm.page = null }

        if (space == null) {
            Muted(l.t("sync.intro"), 11.5f)
            var name by remember { mutableStateOf(c.s.userName) }
            Field(l.t("sync.myName"), name, { name = it }, placeholder = l.t("sync.myNameHint"))
            PrimaryButton(l.t("sync.create"), { vm.createSpace(name) })

            SectionTitle(l.t("sync.joinTitle"))
            Muted(l.t("sync.joinNote"), 11.5f)
            Field(l.t("sync.code"), vm.joinCode, { vm.joinCode = it }, placeholder = "7F3A-9C2B")
            SecondaryButton(l.t("sync.join"), { vm.joinSpace(name) }, Modifier.fillMaxWidth(), size = 13, upper = true)
            return@ScreenColumn
        }

        // код приглашения
        Column(
            Modifier
                .fillMaxWidth()
                .background(col.hero)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(l.t("sync.codeTitle").uppercase(), style = T.b(10.sp, col.a300, 0.18.em))
            Text(vm.inviteCode(), style = T.h(30.sp, col.onAccent, 0.08.em))
            Text(l.t("sync.codeNote"), style = T.b(11.sp, col.a300))
        }

        SectionTitle(l.t("sync.members"))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            space.members.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().background(col.surface).hairline(col.divider).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(m.name, style = T.b(13.sp, col.text), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (m.id == space.memberId) Muted(l.t("sync.you"), 11f)
                }
            }
        }

        SectionTitle(l.t("sync.how"))
        Muted(l.t("sync.howNote"), 11.5f)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val drive = space.links.firstOrNull { it.kind == SyncKind.DRIVE }
            SettingRow(l.t("sync.drive"), l.t("sync.driveSub")) {
                Toggle(drive?.enabled == true) { vm.toggleLink(SyncKind.DRIVE) }
            }
            val dav = space.links.firstOrNull { it.kind == SyncKind.WEBDAV }
            SettingRow(l.t("sync.webdav"), dav?.url?.takeIf { it.isNotBlank() } ?: l.t("sync.webdavSub")) {
                Toggle(dav?.enabled == true) { vm.toggleLink(SyncKind.WEBDAV) }
            }
            SecondaryButton(l.t("sync.webdavSetup"), { vm.openWebDav() }, Modifier.fillMaxWidth(), size = 13, upper = true)
        }

        SectionTitle(l.t("sync.exchange")) {
            if (space.syncedAt > 0) Muted(formatBackupTime(space.syncedAt, l))
        }
        PrimaryButton(if (vm.syncBusy) l.t("sync.working") else l.t("sync.now"), { vm.syncNow() }, enabled = !vm.syncBusy)

        SectionTitle(l.t("sync.lan.title"))
        Muted(l.t("sync.lan.note"), 11.5f)
        val hosting = vm.lanHostAddress
        if (hosting != null) {
            Column(
                Modifier.fillMaxWidth().background(col.hero).padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(l.t("sync.lan.address").uppercase(), style = T.b(10.sp, col.a300, 0.18.em))
                Text(hosting, style = T.h(22.sp, col.onAccent, 0.04.em))
                Text(l.t("sync.lan.codeLabel").uppercase(), style = T.b(10.sp, col.a300, 0.18.em))
                Text(vm.lanHostCode, style = T.h(30.sp, col.onAccent, 0.2.em))
                Text(l.t("sync.lan.hostNote"), style = T.b(11.sp, col.a300))
            }
            SecondaryButton(l.t("sync.lan.stop"), { vm.stopLanHost() }, Modifier.fillMaxWidth(), size = 13, upper = true)
        } else {
            if (vm.canHostLan) {
                SecondaryButton(l.t("sync.lan.host"), { vm.startLanHost() }, Modifier.fillMaxWidth(), size = 13, upper = true)
            }
            Field(l.t("sync.lan.address"), vm.lanAddress, { vm.lanAddress = it }, placeholder = "192.168.1.5:8733")
            Field(l.t("sync.lan.codeLabel"), vm.lanCode, { vm.lanCode = it }, numeric = true, placeholder = "000000")
            PrimaryButton(
                if (vm.syncBusy) l.t("sync.working") else l.t("sync.lan.go"),
                { vm.syncLan() },
                enabled = !vm.syncBusy,
            )
        }

        val dupes = vm.duplicatePairs()
        if (dupes.isNotEmpty()) {
            SectionTitle(l.t("sync.dupes"))
            Muted(l.t("sync.dupesNote"), 11.5f)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                dupes.take(10).forEach { (a, b) ->
                    Column(
                        Modifier.fillMaxWidth().background(col.surface).hairline(col.danger.copy(alpha = 0.4f))
                            .tap { vm.openEdit(b) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "${a.title} · ${c.fmt(kotlin.math.abs(a.amount), c.accCur(a.acc))}",
                            style = T.b(13.sp, col.text),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Muted(l.t("sync.dupeLine", c.dayLabel(a.date)), 11f)
                    }
                }
            }
        }

        DangerButton(l.t("sync.leave"), { vm.leaveSpace() })
    }
}

/** Адрес и вход на сервер WebDAV: Яндекс.Диск, Nextcloud и подобные. */
@Composable
fun WebDavOverlay(vm: AppViewModel, e: WebDavEdit) {
    val l = T.l
    OverlayScreen(
        l.t("sync.webdav"),
        l.t("common.cancel"),
        { vm.webdavEdit = null },
        footer = { PrimaryButton(l.t("common.save"), { vm.saveWebDav() }) },
    ) {
        Muted(l.t("sync.webdavNote"), 11.5f)
        Field(
            l.t("sync.webdavUrl"),
            e.url,
            { vm.webdavEdit = e.copy(url = it) },
            placeholder = "https://webdav.yandex.ru/kopeechka",
            note = l.t("sync.webdavUrlNote"),
        )
        Field(l.t("sync.webdavLogin"), e.login, { vm.webdavEdit = e.copy(login = it) })
        Field(
            l.t("sync.webdavPassword"),
            e.password,
            { vm.webdavEdit = e.copy(password = it) },
            password = true,
            note = l.t("sync.webdavPasswordNote"),
        )
    }
}
