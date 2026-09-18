package app.kopeechka.finance

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.data.SmsParse
import app.kopeechka.finance.work.Schedules

/**
 * Уведомления банковских приложений.
 *
 * Банки в России и Казахстане почти перестали слать смски — Сбер, Т-Банк,
 * Kaspi сообщают о покупках пушами. Разбор тот же, что у СМС ([SmsInbox]):
 * «отправитель» здесь — имя пакета приложения.
 *
 * Чтобы в правиле можно было выбрать банк из списка, а не вписывать имя
 * пакета, запоминаем приложения, которые присылали уведомления с суммой.
 * Только их: остальные уведомления не читаются и никуда не записываются.
 */
class BankNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = applicationContext as? KopeechkaApp ?: return
        val data = app.store.current
        if (!data.settings.bankPush) return
        if (sbn.packageName == packageName) return

        val text = textOf(sbn.notification)
        if (text.isBlank() || SmsParse.findAmount(text) == null) return

        remember(app, sbn.packageName)

        val l = Lang.of(data.settings.lang)
        val result = runCatching {
            SmsInbox.handle(app.store, sbn.packageName, text, sbn.postTime, l, fromPush = true)
        }.getOrNull() ?: return

        Schedules.notifyDraft(this, l, l.t(if (result.auto) "sms.notifyAuto" else "sms.notifyText", result.title))
    }

    private companion object {
        val KNOWN_BANKS = mapOf(
            "ru.sberbankmobile" to "СберБанк",
            "com.idamob.tinkoff.android" to "Т-Банк",
            "ru.alfabank.mobile.android" to "Альфа-Банк",
            "ru.vtb24.mobilebanking.android" to "ВТБ",
            "kz.kaspi.mobile" to "Kaspi.kz",
        )
    }

    /** Заголовок и текст, в том числе развёрнутый: сумма бывает только в длинном варианте. */
    private fun textOf(n: Notification): String {
        val e = n.extras ?: return ""
        return listOfNotNull(
            e.getCharSequence(Notification.EXTRA_TITLE),
            e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT),
        ).joinToString(". ")
    }

    private fun remember(app: KopeechkaApp, pkg: String) {
        // Android 11+ может не показать нам чужое приложение — тогда берём известное имя банка
        val label = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull() ?: KNOWN_BANKS[pkg] ?: pkg
        val known = app.store.current.pushApps
        if (known[pkg] == label) return
        app.store.update { it.copy(pushApps = (it.pushApps + (pkg to label)).entries.toList().takeLast(30).associate { e -> e.key to e.value }) }
    }
}
