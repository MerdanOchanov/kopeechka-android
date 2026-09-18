package app.kopeechka.finance

import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.data.SmsMessage
import app.kopeechka.finance.net.DriveApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS-сторона платформенных портов.
 *
 * Сделано: версия приложения, вечернее напоминание и снимок чека.
 * Пока не сделано: системные диалоги файлов (UIDocumentPicker) и вход в Google —
 * для них нужен контроллер и отдельный поток авторизации, это следующий шаг.
 * Приложение при этом полностью рабочее: данные, отчёты, бизнес и долги живут
 * на устройстве, а CSV и Диск честно сообщают, что ещё недоступны.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPlatform(private val storage: IosStorage) : Platform {

    override val version: String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "1.0"

    /** На iPhone приложение приходит из App Store или от разработчика — не с GitHub. */
    override val updatesFromGitHub = false

    override fun openUrl(url: String) {
        val u = platform.Foundation.NSURL.URLWithString(url) ?: return
        platform.UIKit.UIApplication.sharedApplication.openURL(u, emptyMap<Any?, Any?>(), null)
    }

    private val reminderId = "kopeechka.evening"

    override fun syncReminder(on: Boolean, hour: Int) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(reminderId))
        if (!on) return
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { granted, _ ->
            if (!granted) return@requestAuthorizationWithOptions
            val l = Lang.fromSystem()
            val content = UNMutableNotificationContent().apply {
                setTitle(l.t("app.name"))
                setBody(l.t("notify.text"))
            }
            val components = NSCalendar.currentCalendar.components(
                NSCalendarUnitHour or NSCalendarUnitMinute,
                fromDate = NSDate(),
            )
            components.setHour(hour.toLong())
            components.setMinute(0)
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(reminderId, content, trigger),
                null,
            )
        }
    }

    /** Фоновая копия раз в день появится вместе с входом в Google. */
    override fun syncAutoBackup(on: Boolean) = Unit

    /** На iOS фоновых задач по расписанию нет — платежи проводятся при открытии приложения. */
    override fun syncRecurring(on: Boolean) = Unit

    override fun onLanguageChanged(l: Lang) {
        DriveApi.folderName = l.t("backup.folder")
    }

    private val files = IosFiles()

    override suspend fun saveTextFile(suggestedName: String, text: String, mime: String): String? =
        files.save(suggestedName, text)

    override suspend fun openTextFile(): PickedFile? = files.open()

    override fun shareTextFile(name: String, text: String, mime: String) = files.share(name, text)

    // ——— чеки ———

    private val picker = IosImagePicker()

    override val canPickImage = true

    override suspend fun pickImage(source: ImageSource): PickedImage? = picker.pick(source)

    // ——— банковские СМС ———

    /**
     * На iOS этого не будет: Apple не даёт приложениям доступ к сообщениям
     * ни с каким разрешением. Раздел на iPhone просто не показывается.
     */
    override val canReadSms = false

    override suspend fun requestSmsAccess() = false

    override suspend fun readSmsHistory(days: Int): List<SmsMessage> = emptyList()

    // ——— уведомления банков: iOS не даёт читать чужие уведомления ———

    override val canReadPush = false

    override fun pushAccessGranted() = false

    override fun openPushAccessSettings() = Unit

    // ——— обмен напрямую по Wi-Fi ———

    /**
     * iPhone пока только отправляет: подключается к андроиду, который принимает.
     * Серверную часть Ktor под iOS ещё предстоит проверить.
     */
    override val canHostLan = false

    override suspend fun startLanHost(onExchange: suspend (code: String, body: String) -> Pair<Int, String>): String? = null

    override fun stopLanHost() = Unit

    override suspend fun driveToken(): String? = null

    override fun saveBeforeRestore(json: String) = storage.saveBeforeRestore(json)
}
