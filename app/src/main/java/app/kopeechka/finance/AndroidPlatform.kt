package app.kopeechka.finance

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.net.DriveApi
import app.kopeechka.finance.net.DriveBackup
import app.kopeechka.finance.work.Schedules
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

/** Что показать системе: сохранить файл с таким именем или открыть существующий. */
data class FilePrompt(val kind: String, val name: String)

/**
 * Android-сторона платформенных портов: напоминания, системные диалоги файлов
 * и вход в Google. Activity подписывается на [prompts] и [authRequests]
 * и возвращает результат обратно — общий код об этой кухне не знает.
 */
class AndroidPlatform(private val ctx: Context) : Platform {

    override val version: String = BuildConfig.VERSION_NAME

    val prompts = MutableSharedFlow<FilePrompt>(extraBufferCapacity = 1)
    val authRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)

    private var pendingFile: CompletableDeferred<Uri?>? = null
    private var pendingAuth: CompletableDeferred<String?>? = null

    // ——— напоминания и фоновая копия ———

    override fun syncReminder(on: Boolean, hour: Int) = Schedules.syncReminder(ctx, on, hour)

    override fun syncAutoBackup(on: Boolean) = Schedules.syncAutoBackup(ctx, on)

    override fun onLanguageChanged(l: Lang) {
        DriveApi.folderName = l.t("backup.folder")
        Schedules.ensureChannel(ctx, l)
    }

    // ——— файлы ———

    override suspend fun saveTextFile(suggestedName: String, text: String): String? {
        val uri = ask(FilePrompt("create", suggestedName)) ?: return null
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return nameOf(uri)
    }

    override suspend fun openTextFile(): PickedFile? {
        val uri = ask(FilePrompt("open", "")) ?: return null
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        return PickedFile(nameOf(uri), text)
    }

    private suspend fun ask(prompt: FilePrompt): Uri? {
        val waiter = CompletableDeferred<Uri?>()
        pendingFile = waiter
        prompts.emit(prompt)
        return waiter.await()
    }

    /** Activity отдаёт сюда то, что выбрал человек. */
    fun onFileChosen(uri: Uri?) {
        pendingFile?.complete(uri)
        pendingFile = null
    }

    private fun nameOf(uri: Uri) = uri.lastPathSegment?.substringAfterLast('/') ?: "CSV"

    // ——— Google Диск ———

    override suspend fun driveToken(): String? {
        val r = try {
            DriveBackup.authorize(ctx)
        } catch (e: ApiException) {
            throw IllegalStateException(Lang.of("auto").t("msg.driveAuthError", e.statusCode))
        }
        if (!r.hasResolution()) return r.accessToken
        // доступ ещё не выдан: показываем системный экран согласия и ждём ответа
        val waiter = CompletableDeferred<String?>()
        pendingAuth = waiter
        authRequests.emit(r.pendingIntent!!.intentSender)
        return waiter.await()
    }

    fun onAuthResult(data: Intent?) {
        val token = runCatching { DriveBackup.resultFromIntent(ctx, data).accessToken }.getOrNull()
        pendingAuth?.complete(token)
        pendingAuth = null
    }

    fun onAuthCancelled() {
        pendingAuth?.complete(null)
        pendingAuth = null
    }

    override fun saveBeforeRestore(json: String) {
        runCatching { File(ctx.filesDir, "before-restore.json").writeText(json) }
    }
}
