package app.kopeechka.finance.net

import app.kopeechka.finance.data.Lang
import kotlinx.datetime.toLocalDateTime
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** «14 сен 2026, 10:45» — на языке приложения, без системной локали. */
fun formatBackupTime(epochMillis: Long, l: Lang): String {
    val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val month = l.monthsShort.getOrElse(dt.monthNumber - 1) { "" }
    val hh = dt.hour.toString().padStart(2, '0')
    val mm = dt.minute.toString().padStart(2, '0')
    return "${dt.dayOfMonth} $month ${dt.year}, $hh:$mm"
}

/** Время создания копии: Google отдаёт его строкой ISO. */
fun backupMillis(b: RemoteBackup): Long =
    runCatching { kotlinx.datetime.Instant.parse(b.createdIso).toEpochMilliseconds() }.getOrDefault(0L)

/** Копия на Диске: идентификатор, имя, когда создана (ISO-время от Google) и размер. */
data class RemoteBackup(val id: String, val name: String, val createdIso: String, val size: Long)

class DriveError(val key: String, val args: List<Any?> = emptyList()) : Exception(key) {
    fun text(l: Lang) = l.t(key, *args.toTypedArray())
}

/**
 * Google Диск через REST API v3 — общий код для Android и iOS.
 *
 * Здесь только запросы: токен доступа добывает платформа (на Android —
 * Google Play services, на iOS будет свой вход). Область доступа drive.file:
 * приложение видит лишь файлы, которые само создало.
 */
object DriveApi {
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"

    /** Имя папки на Диске берётся из языка интерфейса. */
    var folderName: String = "Kopeechka"

    /** Подпапка общего пространства: копии и обмен не должны перемешиваться. */
    var sharedFolderName: String = "Kopeechka Together"

    private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    private const val API = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Загрузка в два шага: сначала метаданные (имя и папка), потом содержимое.
     * multipart/related Google принимал молча, теряя имя файла, — так надёжнее.
     */
    suspend fun upload(token: String, content: String, name: String): String {
        val folder = folderId(token, create = true)!!
        val meta = buildJsonObject {
            put("name", name)
            put("mimeType", "application/json")
            put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(folder)) })
        }
        val created = parse(
            check(
                send(token, HttpMethod.Post, "$API?fields=id") {
                    contentType(ContentType.Application.Json)
                    setBody(meta.toString())
                },
            ),
        )
        val id = created["id"]!!.jsonPrimitive.content
        check(
            send(token, HttpMethod.Patch, "$UPLOAD/$id?uploadType=media") {
                contentType(ContentType.Application.Json)
                setBody(content)
            },
        )
        return name
    }

    suspend fun list(token: String): List<RemoteBackup> {
        val folder = folderId(token, create = false) ?: return emptyList()
        val obj = parse(
            check(
                send(token, HttpMethod.Get, API) {
                    parameter("q", "'$folder' in parents and trashed=false")
                    parameter("orderBy", "createdTime desc")
                    parameter("fields", "files(id,name,createdTime,size)")
                    parameter("pageSize", "50")
                },
            ),
        )
        return obj["files"]?.jsonArray?.map { f ->
            val o = f.jsonObject
            RemoteBackup(
                id = o["id"]!!.jsonPrimitive.content,
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: "",
                createdIso = o["createdTime"]?.jsonPrimitive?.contentOrNull ?: "",
                size = o["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0,
            )
        } ?: emptyList()
    }

    suspend fun download(token: String, id: String): String {
        val resp = send(token, HttpMethod.Get, "$API/$id?alt=media")
        if (resp.status.value !in 200..299) throw DriveError("drive.err.download", listOf(resp.status.value))
        return resp.bodyAsText().ifBlank { throw DriveError("drive.err.empty") }
    }

    /** Оставляет только `keep` самых свежих копий, чтобы не засорять Диск. */
    suspend fun prune(token: String, keep: Int = 10) {
        list(token).drop(keep).forEach { f ->
            runCatching {
                send(token, HttpMethod.Delete, "$API/${f.id}")
            }
        }
    }

    /**
     * Положить файл с этим именем, заменив прежний.
     *
     * Для обмена между двумя телефонами это главное отличие от копий: у каждого
     * участника один файл, который переписывается, а не копится десятками.
     */
    suspend fun putNamed(token: String, folder: String, name: String, content: String): String {
        val dir = folderId(token, create = true, name = folder)!!
        val id = findId(token, dir, name) ?: run {
            val meta = buildJsonObject {
                put("name", name)
                put("mimeType", "application/json")
                put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(dir)) })
            }
            val created = parse(
                check(
                    send(token, HttpMethod.Post, "$API?fields=id") {
                        contentType(ContentType.Application.Json)
                        setBody(meta.toString())
                    },
                ),
            )
            created["id"]!!.jsonPrimitive.content
        }
        check(
            send(token, HttpMethod.Patch, "$UPLOAD/$id?uploadType=media") {
                contentType(ContentType.Application.Json)
                setBody(content)
            },
        )
        return id
    }

    /** Файлы в подпапке: имя и идентификатор. Для обмена нужны чужие снимки. */
    suspend fun listNamed(token: String, folder: String): List<RemoteBackup> {
        val dir = folderId(token, create = false, name = folder) ?: return emptyList()
        val obj = parse(
            check(
                send(token, HttpMethod.Get, API) {
                    parameter("q", "'$dir' in parents and trashed=false")
                    parameter("fields", "files(id,name,modifiedTime,size)")
                    parameter("pageSize", "50")
                },
            ),
        )
        return obj["files"]?.jsonArray?.map { f ->
            val o = f.jsonObject
            RemoteBackup(
                id = o["id"]!!.jsonPrimitive.content,
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: "",
                createdIso = o["modifiedTime"]?.jsonPrimitive?.contentOrNull ?: "",
                size = o["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0,
            )
        } ?: emptyList()
    }

    private suspend fun findId(token: String, dir: String, name: String): String? {
        val obj = parse(
            check(
                send(token, HttpMethod.Get, API) {
                    parameter("q", "'$dir' in parents and name='$name' and trashed=false")
                    parameter("fields", "files(id)")
                },
            ),
        )
        return obj["files"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    private suspend fun folderId(token: String, create: Boolean, name: String = folderName): String? {
        val found = parse(
            check(
                send(token, HttpMethod.Get, API) {
                    parameter("q", "name='$name' and mimeType='$FOLDER_MIME' and trashed=false")
                    parameter("fields", "files(id)")
                },
            ),
        )
        found["files"]?.jsonArray?.firstOrNull()?.let { return it.jsonObject["id"]!!.jsonPrimitive.content }
        if (!create) return null
        val meta = buildJsonObject {
            put("name", name)
            put("mimeType", FOLDER_MIME)
        }
        val made = parse(
            check(
                send(token, HttpMethod.Post, "$API?fields=id") {
                    contentType(ContentType.Application.Json)
                    setBody(meta.toString())
                },
            ),
        )
        return made["id"]!!.jsonPrimitive.content
    }

    private suspend fun send(
        token: String,
        method: HttpMethod,
        urlString: String,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse =
        try {
            http.request(urlString) {
                this.method = method
                header("Authorization", "Bearer $token")
                configure()
            }
        } catch (e: DriveError) {
            throw e
        } catch (e: Exception) {
            // причина видна в логе: без неё «нет связи» ничего не объясняет
            println("Kopeechka/Drive: запрос не прошёл — $e")
            throw DriveError("drive.err.offline")
        }

    private fun check(resp: HttpResponse): HttpResponse {
        val code = resp.status.value
        if (code !in 200..299) {
            throw when (code) {
                401 -> DriveError("drive.err.expired")
                403 -> DriveError("drive.err.forbidden")
                else -> DriveError("drive.err.code", listOf(code))
            }
        }
        return resp
    }

    private suspend fun parse(resp: HttpResponse): JsonObject =
        runCatching { json.parseToJsonElement(resp.bodyAsText().ifBlank { "{}" }).jsonObject }
            .getOrElse { throw DriveError("drive.err.code", listOf(resp.status.value)) }
}
