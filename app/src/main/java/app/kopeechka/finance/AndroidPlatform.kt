package app.kopeechka.finance

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.kopeechka.finance.data.Lang
import app.kopeechka.finance.data.SmsMessage
import app.kopeechka.finance.net.DriveApi
import app.kopeechka.finance.net.DriveBackup
import app.kopeechka.finance.work.Schedules
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
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
    val imagePrompts = MutableSharedFlow<ImageSource>(extraBufferCapacity = 1)
    val permissionRequests = MutableSharedFlow<Array<String>>(extraBufferCapacity = 1)

    private var pendingFile: CompletableDeferred<Uri?>? = null
    private var pendingAuth: CompletableDeferred<String?>? = null
    private var pendingImage: CompletableDeferred<Uri?>? = null
    private var pendingPermission: CompletableDeferred<Boolean>? = null

    /** Куда камера пишет снимок: файл в кэше, отданный системе через FileProvider. */
    private var cameraTarget: Uri? = null

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

    // ——— чеки ———

    override val canPickImage = true

    override suspend fun pickImage(source: ImageSource): PickedImage? {
        val waiter = CompletableDeferred<Uri?>()
        pendingImage = waiter
        imagePrompts.emit(source)
        val uri = waiter.await() ?: return null
        return runCatching { PickedImage(encode(uri)) }.getOrNull()
    }

    /** Activity спрашивает, куда камере писать снимок. */
    fun newCameraTarget(): Uri {
        val dir = File(ctx.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file).also { cameraTarget = it }
    }

    fun onImageChosen(uri: Uri?) {
        pendingImage?.complete(uri)
        pendingImage = null
    }

    /** Камера не возвращает Uri — только «получилось или нет». */
    fun onPhotoTaken(ok: Boolean) {
        pendingImage?.complete(if (ok) cameraTarget else null)
        pendingImage = null
    }

    /**
     * Снимок ужимается до 1600 точек по длинной стороне и кодируется в base64.
     * Полноразмерное фото с телефона — это мегабайты, за которые платит владелец
     * ключа, а мелкий шрифт чека от такого сжатия не страдает.
     */
    private fun encode(uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = generateSequence(1) { it * 2 }.first { longest / it <= MAX_SIDE * 2 }
        }
        val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("снимок не читается")
        val rotated = applyOrientation(uri, raw)
        val scale = MAX_SIDE.toFloat() / maxOf(rotated.width, rotated.height)
        val bmp = if (scale >= 1f) rotated else
            Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt(), (rotated.height * scale).toInt(), true)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Телефон снимает «боком» и пишет поворот в EXIF: без этого текст чека лежит на боку. */
    private fun applyOrientation(uri: Uri, bmp: Bitmap): Bitmap {
        val orientation = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
        }.getOrNull() ?: 1
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bmp
        }
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    // ——— Google Диск ———

    override suspend fun driveToken(): String? {
        val r = try {
            DriveBackup.authorize(ctx)
        } catch (e: ApiException) {
            throw DriveAuthError(e.statusCode)
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

    // ——— банковские СМС ———

    /** В сборке для Google Play чтения СМС нет — см. app/build.gradle.kts. */
    override val canReadSms = BuildConfig.SMS_ENABLED

    override suspend fun requestSmsAccess(): Boolean {
        if (!canReadSms) return false
        if (smsAllowed()) return true
        val waiter = CompletableDeferred<Boolean>()
        pendingPermission = waiter
        permissionRequests.emit(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
        return waiter.await()
    }

    fun onPermissionResult(granted: Boolean) {
        pendingPermission?.complete(granted)
        pendingPermission = null
    }

    private fun smsAllowed() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * Входящие за последние дни. Нужны при включении: правило видно сразу
     * на настоящих сообщениях, а не после ожидания следующего списания.
     */
    override suspend fun readSmsHistory(days: Int): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (!smsAllowed()) return@withContext emptyList()
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val out = mutableListOf<SmsMessage>()
        runCatching {
            ctx.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                "date >= ?",
                arrayOf(since.toString()),
                "date DESC",
            )?.use { cur ->
                while (cur.moveToNext() && out.size < MAX_SMS) {
                    out += SmsMessage(
                        sender = cur.getString(0).orEmpty(),
                        text = cur.getString(1).orEmpty(),
                        at = cur.getLong(2),
                    )
                }
            }
        }
        out
    }

    // ——— обмен напрямую по Wi-Fi ———

    private val lanHost = app.kopeechka.finance.net.LanHost()

    override val canHostLan = true

    override suspend fun startLanHost(onExchange: suspend (code: String, body: String) -> Pair<Int, String>): String? =
        withContext(Dispatchers.IO) { lanHost.start(onExchange) }

    override fun stopLanHost() = lanHost.stop()

    private companion object {
        const val MAX_SIDE = 1600
        const val MAX_SMS = 500
    }
}
