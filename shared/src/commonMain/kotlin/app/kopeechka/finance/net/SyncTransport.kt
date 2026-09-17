package app.kopeechka.finance.net

import app.kopeechka.finance.data.SyncLink
import app.kopeechka.finance.data.SyncSnapshot
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

/**
 * Обмен снимками между участниками общего пространства.
 *
 * Слияние от транспорта не зависит, поэтому облако, чужой сервер и прямая
 * передача по Wi-Fi отличаются только тем, откуда берутся чужие снимки.
 * Каждый участник кладёт один файл `space-<участник>.json` и переписывает его;
 * замков и очередей не нужно — слияние не зависит от порядка.
 */
interface SyncTransport {
    /**
     * Отдать свой снимок и забрать чужие — один заход.
     *
     * У облака это два запроса подряд, у прямой передачи по Wi-Fi — один
     * обмен: телефоны отдают снимки друг другу в одном разговоре.
     */
    suspend fun exchange(mine: SyncSnapshot): List<SyncSnapshot>
}

class SyncError(val key: String, val args: List<Any?> = emptyList()) : Exception(key)

val syncJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

fun snapshotName(memberId: String) = "space-$memberId.json"

/** Свой ли это файл: по имени видно, чей снимок. */
fun memberOfName(name: String): String? =
    name.removePrefix("space-").removeSuffix(".json").takeIf { name.startsWith("space-") && name.endsWith(".json") }

/**
 * Google Диск. Работает, когда двое входят в один аккаунт: область доступа
 * drive.file показывает приложению только то, что оно само создало, поэтому
 * расшаренную папку чужого аккаунта оно не увидит (см. docs/SYNC.md).
 */
class DriveSync(private val token: suspend () -> String?) : SyncTransport {

    override suspend fun exchange(mine: SyncSnapshot): List<SyncSnapshot> {
        val t = token() ?: throw SyncError("sync.err.noAccess")
        val folder = DriveApi.sharedFolderName
        DriveApi.putNamed(t, folder, snapshotName(mine.memberId), syncJson.encodeToString(SyncSnapshot.serializer(), mine))
        return DriveApi.listNamed(t, folder)
            .filter { memberOfName(it.name).let { m -> m != null && m != mine.memberId } }
            .mapNotNull { file ->
                runCatching { syncJson.decodeFromString(SyncSnapshot.serializer(), DriveApi.download(t, file.id)) }
                    .getOrNull()
                    ?.takeIf { it.spaceId == mine.spaceId }
            }
    }
}

/**
 * WebDAV: Яндекс.Диск, Nextcloud, mail.ru и всё, что говорит на этом языке.
 *
 * В отличие от Google Диска здесь у каждого свой аккаунт, а общая папка
 * расшаривается обычными средствами сервиса — никакой проверки приложения
 * проходить не нужно.
 */
class WebDavSync(
    private val link: SyncLink,
    private val password: String,
) : SyncTransport {

    private val base = link.url.trim().trimEnd('/')

    override suspend fun exchange(mine: SyncSnapshot): List<SyncSnapshot> {
        val body = syncJson.encodeToString(SyncSnapshot.serializer(), mine)
        val resp = call(HttpMethod.Put, snapshotName(mine.memberId)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (resp.status.value !in 200..299) throw codeError(resp.status.value)
        return names()
            .filter { memberOfName(it).let { m -> m != null && m != mine.memberId } }
            .mapNotNull { name ->
                runCatching { syncJson.decodeFromString(SyncSnapshot.serializer(), read(name)) }
                    .getOrNull()
                    ?.takeIf { it.spaceId == mine.spaceId }
            }
    }

    /** Проверка настроек: положить и забрать пробный файл. */
    suspend fun check() {
        val probe = "kopeechka-probe.json"
        val resp = call(HttpMethod.Put, probe) {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        if (resp.status.value !in 200..299) throw codeError(resp.status.value)
    }

    private suspend fun names(): List<String> {
        val resp = call(HttpMethod.Propfind, "") { header("Depth", "1") }
        if (resp.status.value !in 200..299) throw codeError(resp.status.value)
        // вместо разбора XML берём имена файлов из href — их формат одинаков у всех серверов
        return HREF.findAll(resp.bodyAsText())
            .map { it.groupValues[1].trimEnd('/').substringAfterLast('/') }
            .filter { it.startsWith("space-") && it.endsWith(".json") }
            .toList()
    }

    private suspend fun read(name: String): String {
        val resp = call(HttpMethod.Get, name)
        if (resp.status.value !in 200..299) throw codeError(resp.status.value)
        return resp.bodyAsText()
    }

    private suspend fun call(
        method: HttpMethod,
        name: String,
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        if (base.isEmpty()) throw SyncError("sync.err.noAddress")
        val url = if (name.isEmpty()) "$base/" else "$base/$name"
        return try {
            http.request(url) {
                this.method = method
                basicAuth(link.login, password)
                configure()
            }
        } catch (e: SyncError) {
            throw e
        } catch (e: Exception) {
            println("Kopeechka/Sync: WebDAV не ответил — $e")
            throw SyncError("sync.err.offline")
        }
    }

    private fun codeError(code: Int): SyncError = when (code) {
        401, 403 -> SyncError("sync.err.login")
        404, 409 -> SyncError("sync.err.folder")
        else -> SyncError("sync.err.code", listOf(code))
    }

    private companion object {
        val HREF = Regex("<[Dd]?:?href>([^<]+)</[Dd]?:?href>")
    }
}

private val HttpMethod.Companion.Propfind get() = HttpMethod("PROPFIND")

/** Заголовок, в котором гость передаёт код с экрана хозяина. */
const val LAN_CODE_HEADER = "X-Kopeechka-Code"

/**
 * Прямой обмен с телефоном в той же сети — без облака и без интернета.
 *
 * Один телефон принимает (показывает адрес и шестизначный код), второй
 * отправляет ему свой снимок и в ответе получает снимок хозяина. Всё за
 * один запрос. Данные идут по домашней сети открытым текстом — поэтому приём
 * работает только пока открыт экран, требует код и закрывается после пяти
 * неверных попыток.
 */
class LanSync(address: String, private val code: String) : SyncTransport {

    private val url = "http://" + address.trim().removePrefix("http://").trimEnd('/') + "/exchange"

    override suspend fun exchange(mine: SyncSnapshot): List<SyncSnapshot> {
        val resp = try {
            http.request(url) {
                method = HttpMethod.Post
                header(LAN_CODE_HEADER, code)
                contentType(ContentType.Application.Json)
                setBody(syncJson.encodeToString(SyncSnapshot.serializer(), mine))
            }
        } catch (e: Exception) {
            println("Kopeechka/Sync: второй телефон не ответил — $e")
            throw SyncError("sync.lan.unreachable")
        }
        when (resp.status.value) {
            in 200..299 -> Unit
            403 -> throw SyncError("sync.lan.wrongCode")
            409 -> throw SyncError("sync.lan.otherSpace")
            else -> throw SyncError("sync.err.code", listOf(resp.status.value))
        }
        val theirs = runCatching { syncJson.decodeFromString(SyncSnapshot.serializer(), resp.bodyAsText()) }.getOrNull()
            ?: throw SyncError("sync.lan.badAnswer")
        return listOf(theirs)
    }
}
