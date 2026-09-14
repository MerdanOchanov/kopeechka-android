package app.kopeechka.finance.net

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import app.kopeechka.finance.data.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class RemoteBackup(val id: String, val name: String, val created: Instant, val size: Long)

class DriveError(val key: String, val args: List<Any?> = emptyList()) : Exception(key) {
    fun text(l: Lang) = l.t(key, *args.toTypedArray())
}

/**
 * Резервные копии в Google Диске через REST API v3.
 * Scope drive.file: приложение видит только файлы, которые само создало,
 * а сами копии лежат в обычной папке «Копеечка — резервные копии» и видны в Диске.
 */
object DriveBackup {
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    /** Имя папки на Диске берётся из языка интерфейса. */
    var folderName: String = "Kopeechka"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    private const val API = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Если доступ уже выдан — сразу вернёт токен; иначе hasResolution() и PendingIntent для экрана согласия. */
    suspend fun authorize(ctx: Context): AuthorizationResult =
        Identity.getAuthorizationClient(ctx).authorize(request()).await()

    fun resultFromIntent(ctx: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data)

    suspend fun upload(token: String, content: String): String = withContext(Dispatchers.IO) {
        val folder = folderId(token, create = true)!!
        val name = "kopeechka-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".json"
        val meta = buildJsonObject {
            put("name", name)
            put("mimeType", "application/json")
            put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(folder)) })
        }
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(meta.toString().toRequestBody(jsonType))
            .addPart(content.toRequestBody(jsonType))
            .build()
        call(token, Request.Builder().url("$UPLOAD?uploadType=multipart&fields=id,name").post(body))
        name
    }

    suspend fun list(token: String): List<RemoteBackup> = withContext(Dispatchers.IO) {
        val folder = folderId(token, create = false) ?: return@withContext emptyList()
        val url = API.toHttpUrl().newBuilder()
            .addQueryParameter("q", "'$folder' in parents and trashed=false")
            .addQueryParameter("orderBy", "createdTime desc")
            .addQueryParameter("fields", "files(id,name,createdTime,size)")
            .addQueryParameter("pageSize", "50")
            .build()
        val obj = call(token, Request.Builder().url(url).get())
        obj["files"]?.jsonArray?.map { f ->
            val o = f.jsonObject
            RemoteBackup(
                id = o["id"]!!.jsonPrimitive.content,
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: "",
                created = runCatching { Instant.parse(o["createdTime"]!!.jsonPrimitive.content) }.getOrDefault(Instant.EPOCH),
                size = o["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0,
            )
        } ?: emptyList()
    }

    suspend fun download(token: String, id: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$API/$id?alt=media").header("Authorization", "Bearer $token").get().build()
        try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw DriveError("drive.err.download", listOf(r.code))
                r.body?.string() ?: throw DriveError("drive.err.empty")
            }
        } catch (e: IOException) {
            throw DriveError("drive.err.offline")
        }
    }

    /** Оставляет только `keep` самых свежих копий, чтобы не засорять Диск. */
    suspend fun prune(token: String, keep: Int = 10) = withContext(Dispatchers.IO) {
        list(token).drop(keep).forEach { f ->
            runCatching {
                http.newCall(Request.Builder().url("$API/${f.id}").header("Authorization", "Bearer $token").delete().build()).execute().close()
            }
        }
    }

    private fun folderId(token: String, create: Boolean): String? {
        val url = API.toHttpUrl().newBuilder()
            .addQueryParameter("q", "name='$folderName' and mimeType='$FOLDER_MIME' and trashed=false")
            .addQueryParameter("fields", "files(id)")
            .build()
        call(token, Request.Builder().url(url).get())["files"]?.jsonArray?.firstOrNull()?.let {
            return it.jsonObject["id"]!!.jsonPrimitive.content
        }
        if (!create) return null
        val meta = buildJsonObject {
            put("name", folderName)
            put("mimeType", FOLDER_MIME)
        }
        return call(token, Request.Builder().url("$API?fields=id").post(meta.toString().toRequestBody(jsonType)))["id"]!!.jsonPrimitive.content
    }

    private fun call(token: String, b: Request.Builder): JsonObject {
        try {
            http.newCall(b.header("Authorization", "Bearer $token").build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    throw when (r.code) {
                        401 -> DriveError("drive.err.expired")
                        403 -> DriveError("drive.err.forbidden")
                        else -> DriveError("drive.err.code", listOf(r.code))
                    }
                }
                return json.parseToJsonElement(text.ifBlank { "{}" }).jsonObject
            }
        } catch (e: IOException) {
            throw DriveError("drive.err.offline")
        }
    }

    fun formatTime(i: Instant, l: Lang = Lang.RU): String =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale(l.code)).format(i.atZone(ZoneId.systemDefault()))
}
