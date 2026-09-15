package app.kopeechka.finance.net

import android.content.Context
import android.content.Intent
import app.kopeechka.finance.data.Lang
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Android-часть резервных копий: вход в Google и получение токена.
 * Сами запросы к Диску живут в общем модуле — [DriveApi], он же работает на iOS.
 */
object DriveBackup {
    const val SCOPE = DriveApi.SCOPE

    /** Имя папки на Диске берётся из языка интерфейса. */
    var folderName: String
        get() = DriveApi.folderName
        set(value) {
            DriveApi.folderName = value
        }

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Если доступ уже выдан — сразу вернёт токен; иначе hasResolution() и PendingIntent для экрана согласия. */
    suspend fun authorize(ctx: Context): AuthorizationResult =
        Identity.getAuthorizationClient(ctx).authorize(request()).await()

    fun resultFromIntent(ctx: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data)

    suspend fun upload(token: String, content: String): String {
        val name = "kopeechka-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".json"
        return DriveApi.upload(token, content, name)
    }

    suspend fun list(token: String) = DriveApi.list(token)
    suspend fun download(token: String, id: String) = DriveApi.download(token, id)
    suspend fun prune(token: String, keep: Int = 10) = DriveApi.prune(token, keep)

    fun formatTime(i: Instant, l: Lang = Lang.RU): String =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale(l.code)).format(i.atZone(ZoneId.systemDefault()))

    /** Время создания копии: Google отдаёт его строкой ISO. */
    fun createdAt(b: RemoteBackup): Instant =
        runCatching { Instant.parse(b.createdIso) }.getOrDefault(Instant.EPOCH)
}
