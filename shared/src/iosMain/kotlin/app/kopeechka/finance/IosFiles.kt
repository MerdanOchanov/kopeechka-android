package app.kopeechka.finance

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSWindowsCP1251StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeCommaSeparatedText
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Файлы на iOS: сохранить, открыть и отправить через системные окна.
 *
 * Делегат держится в поле, пока окно открыто: система ссылается на него
 * слабо, и без этой ссылки он исчезает до ответа человека.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosFiles {

    private var keepAlive: DocumentPickerDelegate? = null

    /** Сохранить через «Файлы». Возвращает имя или null, если человек передумал. */
    suspend fun save(name: String, text: String): String? {
        val url = writeTemp(name, text) ?: return null
        val picked = present { UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true) }
        return picked?.lastPathComponent
    }

    /** Открыть файл из «Файлов» и прочитать его текстом. */
    suspend fun open(): PickedFile? {
        val types = listOf(UTTypeJSON, UTTypeCommaSeparatedText, UTTypePlainText, UTTypeData)
        val url = present { UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true) } ?: return null
        // UTF-8, а если файл не в ней — windows-1251: так выгружают выписки многие банки
        val text = NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null)
            ?: NSString.stringWithContentsOfURL(url, NSWindowsCP1251StringEncoding, null)
            ?: return null
        return PickedFile(url.lastPathComponent ?: "file", text)
    }

    /** Отправить файл в мессенджер или почту — окно «Поделиться». */
    fun share(name: String, text: String) {
        val url = writeTemp(name, text) ?: return
        val root = rootController() ?: return
        val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        root.presentViewController(sheet, animated = true, completion = null)
    }

    private suspend fun present(make: () -> UIDocumentPickerViewController): NSURL? =
        suspendCancellableCoroutine { cont ->
            val root = rootController()
            if (root == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val picker = make()
            val delegate = DocumentPickerDelegate { url ->
                keepAlive = null
                cont.resume(url)
            }
            keepAlive = delegate
            picker.delegate = delegate
            root.presentViewController(picker, animated = true, completion = null)
        }

    private fun writeTemp(name: String, text: String): NSURL? {
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + name)
        val ok = NSString.create(string = text).writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        return if (ok) url else null
    }

    private fun rootController(): UIViewController? =
        UIApplication.sharedApplication.keyWindow?.rootViewController
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class DocumentPickerDelegate(
    private val onDone: (NSURL?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onDone(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onDone(null)
    }
}
