package app.kopeechka.finance

import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.data.SmsMessage
import kotlinx.coroutines.flow.StateFlow

/**
 * Границы между общей логикой и платформой.
 *
 * Всё, что умеет только телефон — файл данных, шифрованные ключи, напоминания,
 * системные диалоги выбора файла и вход в Google — спрятано за этими интерфейсами.
 * Общий код о существовании Android или iOS не знает.
 */

/** Файл данных: единственное состояние приложения. */
interface Storage {
    val data: StateFlow<AppData>
    val current: AppData
    fun update(f: (AppData) -> AppData)
    fun replace(d: AppData)

    /**
     * Записать состояние как есть, без отметок времени.
     * Нужно слиянию: чужие правки приходят со своим временем, и переставлять
     * его на своё — значит объявить их новее всего на свете.
     */
    fun applyMerged(d: AppData)

    /** Состояние в JSON — уходит в резервную копию. */
    fun exportJson(): String

    /** Разбор копии; бросает исключение, если это не копия «Копеечки». */
    fun parseBackup(text: String): AppData
}

/** Ключи ИИ-провайдеров: на Android — Android Keystore, на iOS — Keychain. */
interface SecretStore {
    fun get(name: String): String
    fun put(name: String, value: String)
}

/** Вход в Google не удался: код от Play services (10 — приложение не зарегистрировано). */
class DriveAuthError(val code: Int) : Exception("drive auth failed: $code")

const val MIME_CSV = "text/csv"
const val MIME_JSON = "application/json"

/** Выбранный человеком файл: имя для сообщения и содержимое. */
data class PickedFile(val name: String, val text: String)

/**
 * Снимок чека: содержимое уже сжато и закодировано в base64.
 * Сжатие делает платформа — общий код с пикселями не работает.
 */
data class PickedImage(val base64: String, val mime: String = "image/jpeg")

/** Откуда берём снимок чека. */
enum class ImageSource { CAMERA, GALLERY }

/** Всё платформенное, что нужно экранам. */
interface Platform {
    /** Версия приложения для строки «о программе». */
    val version: String

    /**
     * Ставят ли эту сборку файлом с GitHub. Тогда о новых версиях приложение
     * сообщает само; сборки из Play и App Store обновляет магазин.
     */
    val updatesFromGitHub: Boolean

    /** Открыть ссылку в браузере — например, чтобы скачать новую версию. */
    fun openUrl(url: String)

    // ——— напоминания и фоновая копия ———
    fun syncReminder(on: Boolean, hour: Int)
    fun syncAutoBackup(on: Boolean)

    /** Проверять регулярные платежи раз в день, даже когда приложение закрыто. */
    fun syncRecurring(on: Boolean)

    /** Язык сменился: поправить канал уведомлений и имя папки копий. */
    fun onLanguageChanged(l: Lang)

    // ——— файлы ———

    /**
     * Системный диалог «куда сохранить». Возвращает имя файла либо null, если отменили.
     * [mime] нужен Android: по нему диалог подставляет расширение.
     */
    suspend fun saveTextFile(suggestedName: String, text: String, mime: String = MIME_CSV): String?

    /**
     * Отправить файл через системное «Поделиться» — в мессенджер, почту, на флешку.
     * Там, где Google недоступен, это самый надёжный способ унести копию с телефона.
     */
    fun shareTextFile(name: String, text: String, mime: String)

    /** Системный диалог «что открыть». null, если отменили. */
    suspend fun openTextFile(): PickedFile?

    // ——— чеки ———

    /**
     * Снять чек камерой или выбрать из галереи. Платформа ужимает снимок
     * до разумного размера: за мегапиксели платит тот, у кого ключ ИИ.
     * null — человек отказался или платформа этого не умеет.
     */
    suspend fun pickImage(source: ImageSource): PickedImage?

    /** Умеет ли платформа снимки вообще: на чём не умеет, кнопку не показываем. */
    val canPickImage: Boolean

    // ——— банковские СМС ———

    /**
     * Может ли платформа читать сообщения. На iOS — нет и не будет:
     * Apple не открывает приложениям доступ к СМС.
     */
    val canReadSms: Boolean

    /** Спросить разрешение на чтение сообщений. false — отказали. */
    suspend fun requestSmsAccess(): Boolean

    /** Сообщения за последние дни — чтобы разобрать уже пришедшее. */
    suspend fun readSmsHistory(days: Int): List<SmsMessage>

    /** Умеет ли платформа читать уведомления других приложений. Только Android. */
    val canReadPush: Boolean

    /** Выдан ли доступ к уведомлениям — его включают в системных настройках. */
    fun pushAccessGranted(): Boolean

    /** Открыть системную страницу доступа к уведомлениям. */
    fun openPushAccessSettings()

    // ——— обмен напрямую по Wi-Fi ———

    /** Может ли телефон принимать обмен сам. Отправлять умеют все. */
    val canHostLan: Boolean

    /**
     * Начать приём в локальной сети. [onExchange] получает код из запроса и тело,
     * возвращает код ответа и тело. Результат — «адрес:порт» для экрана или null,
     * если телефон не в сети.
     */
    suspend fun startLanHost(onExchange: suspend (code: String, body: String) -> Pair<Int, String>): String?

    fun stopLanHost()

    // ——— Google Диск ———

    /**
     * Токен доступа к Диску: платформа при необходимости сама показывает экран согласия.
     * null — человек отказался, сообщение об этом уже показано.
     */
    suspend fun driveToken(): String?

    /** Копия текущего состояния рядом с данными — страховка перед восстановлением. */
    fun saveBeforeRestore(json: String)
}
