package app.kopeechka.finance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.work.Schedules

/**
 * Приём банковской СМС.
 *
 * Разбор общий ([SmsInbox]), здесь только то, что умеет один Android: достать
 * из системного уведомления отправителя и текст. Длинное сообщение приходит
 * несколькими кусками — их надо склеить, иначе сумма окажется разорванной.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext as? KopeechkaApp ?: return
        val data = app.store.current
        if (!data.settings.sms) return

        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull() ?: return
        val sender = parts.firstOrNull()?.displayOriginatingAddress.orEmpty()
        if (sender.isEmpty()) return
        val text = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        if (text.isBlank()) return

        val l = Lang.of(data.settings.lang)
        val result = runCatching {
            SmsInbox.handle(app.store, sender, text, System.currentTimeMillis(), l)
        }.getOrNull() ?: return

        Schedules.notifyDraft(
            context,
            l,
            l.t(if (result.auto) "sms.notifyAuto" else "sms.notifyText", result.title),
        )
    }
}
