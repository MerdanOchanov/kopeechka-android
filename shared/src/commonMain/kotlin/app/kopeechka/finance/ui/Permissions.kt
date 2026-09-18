package app.kopeechka.finance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kopeechka.finance.AppViewModel
import app.kopeechka.finance.ui.theme.T
import kotlinx.coroutines.delay

/**
 * Разрешения первого запуска. Каждое — с объяснением, зачем оно нужно:
 * Google Play требует показать это до системного окна, а человеку так
 * проще согласиться. Отказ ничего не ломает — всё включается и позже.
 */
@Composable
fun PermissionsOverlay(vm: AppViewModel) {
    val l = T.l
    // доступ к уведомлениям выдают в настройках телефона — узнаём о нём, опрашивая раз в секунду
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val notify = tick >= 0 && vm.notificationsGranted()
    val sms = tick >= 0 && vm.smsGranted()
    val push = tick >= 0 && vm.pushAccess()
    OverlayScreen(
        l.t("perm.title"),
        l.t("perm.done"),
        { vm.finishPerms() },
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!notify || (vm.canReadSms && !sms)) {
                    PrimaryButton(l.t("perm.all"), { vm.askAllPerms() }, Modifier.fillMaxWidth())
                }
                SecondaryButton(l.t("perm.done"), { vm.finishPerms() }, Modifier.fillMaxWidth(), size = 13, upper = true)
            }
        },
    ) {
        Muted(l.t("perm.intro"), 12f)
        PermRow(l.t("perm.notify"), l.t("perm.notifySub"), notify, l.t("perm.allow")) { vm.askNotifications() }
        if (vm.canReadSms) {
            PermRow(l.t("perm.sms"), l.t("perm.smsSub"), sms, l.t("perm.allow")) { vm.askSms() }
        }
        if (vm.canReadPush) {
            PermRow(l.t("perm.push"), l.t("perm.pushSub"), push, l.t("perm.openSettings")) { vm.openPushSettings() }
        }
        Muted(l.t("perm.later"), 11f)
    }
}

@Composable
private fun PermRow(title: String, sub: String, granted: Boolean, action: String, onAsk: () -> Unit) {
    val col = T.c
    Row(
        Modifier.fillMaxWidth().hairline(col.divider).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = T.b(14.sp, col.text))
            Muted(sub, 11f)
        }
        if (granted) {
            Text(T.l.t("perm.granted"), style = T.b(12.sp, col.accent))
        } else {
            SecondaryButton(action, onAsk, size = 11, upper = true)
        }
    }
}

