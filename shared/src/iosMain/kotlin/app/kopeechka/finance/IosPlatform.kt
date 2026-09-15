package app.kopeechka.finance

import app.kopeechka.finance.data.Lang
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
 * Сделано: версия приложения и вечернее напоминание.
 * Пока не сделано: системные диалоги файлов (UIDocumentPicker) и вход в Google —
 * для них нужен контроллер и отдельный поток авторизации, это следующий шаг.
 * Приложение при этом полностью рабочее: данные, отчёты, бизнес и долги живут
 * на устройстве, а CSV и Диск честно сообщают, что ещё недоступны.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPlatform(private val storage: IosStorage) : Platform {

    override val version: String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "1.0"

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

    override fun onLanguageChanged(l: Lang) {
        DriveApi.folderName = l.t("backup.folder")
    }

    override suspend fun saveTextFile(suggestedName: String, text: String): String? =
        throw UnsupportedOperationException("файлы на iOS появятся вместе с UIDocumentPicker")

    override suspend fun openTextFile(): PickedFile? =
        throw UnsupportedOperationException("файлы на iOS появятся вместе с UIDocumentPicker")

    override suspend fun driveToken(): String? = null

    override fun saveBeforeRestore(json: String) = storage.saveBeforeRestore(json)
}
