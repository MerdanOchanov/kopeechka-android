package app.kopeechka.finance

import app.kopeechka.finance.data.AppData
import app.kopeechka.finance.data.Lang
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

    // ——— напоминания и фоновая копия ———
    fun syncReminder(on: Boolean, hour: Int)
    fun syncAutoBackup(on: Boolean)

    /** Язык сменился: поправить канал уведомлений и имя папки копий. */
    fun onLanguageChanged(l: Lang)

    // ——— файлы ———

    /** Системный диалог «куда сохранить». Возвращает имя файла либо null, если отменили. */
    suspend fun saveTextFile(suggestedName: String, text: String): String?

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

    // ——— Google Диск ———

    /**
     * Токен доступа к Диску: платформа при необходимости сама показывает экран согласия.
     * null — человек отказался, сообщение об этом уже показано.
     */
    suspend fun driveToken(): String?

    /** Копия текущего состояния рядом с данными — страховка перед восстановлением. */
    fun saveBeforeRestore(json: String)
}
