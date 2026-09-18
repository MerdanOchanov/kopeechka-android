package app.kopeechka.finance.net

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Свежая версия на GitHub: номер, прямая ссылка на APK и страница выпуска. */
data class UpdateInfo(val version: String, val apkUrl: String, val pageUrl: String, val notes: String)

/**
 * Проверка обновлений для сборки, которую ставят файлом.
 *
 * Такие люди не узнают о новой версии сами — Play им её не принесёт. Спрашиваем
 * GitHub о последнем выпуске и сравниваем номер с установленным.
 */
object Updates {
    private const val LATEST = "https://api.github.com/repos/MerdanOchanov/kopeechka-android/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    /** Новее установленной — вернёт её, иначе null. Без сети — тоже null: не беда. */
    suspend fun check(installed: String): UpdateInfo? {
        val info = runCatching { latest() }.getOrNull() ?: return null
        return info.takeIf { isNewer(it.version, installed) }
    }

    private suspend fun latest(): UpdateInfo? {
        val text = http.get(LATEST) { header("Accept", "application/vnd.github+json") }.bodyAsText()
        val o = json.parseToJsonElement(text).jsonObject
        val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val apk = o["assets"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull.orEmpty().endsWith(".apk") }
            ?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
        val page = o["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val notes = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return UpdateInfo(tag.removePrefix("v"), apk ?: page, page, notes)
    }

    /** «1.10» новее «1.9»: сравниваем числами по частям, а не строкой. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").split('.', '-').mapNotNull { it.toIntOrNull() }
}
