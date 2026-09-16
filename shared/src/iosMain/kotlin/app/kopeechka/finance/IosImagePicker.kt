package app.kopeechka.finance

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.base64EncodedStringWithOptions
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Снимок чека на iOS: камера или галерея через UIImagePickerController.
 *
 * Делегат держится в поле, пока открыт выбор: система на него ссылается слабо,
 * и без этой ссылки объект успевает исчезнуть до ответа человека.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosImagePicker {

    private var keepAlive: PickerDelegate? = null

    suspend fun pick(source: ImageSource): PickedImage? = suspendCancellableCoroutine { cont ->
        val root = rootController()
        val cameraReady = UIImagePickerController.isSourceTypeAvailable(
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
        )
        if (root == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val picker = UIImagePickerController()
        picker.setSourceType(
            if (source == ImageSource.CAMERA && cameraReady) {
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            } else {
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            },
        )
        val delegate = PickerDelegate { image ->
            keepAlive = null
            cont.resume(image?.let { encode(it) })
        }
        keepAlive = delegate
        picker.setDelegate(delegate)
        root.presentViewController(picker, animated = true, completion = null)
    }

    private fun rootController(): UIViewController? =
        UIApplication.sharedApplication.keyWindow?.rootViewController

    /** Ужимаем до 1600 точек по длинной стороне и кодируем в base64 — как на Android. */
    private fun encode(image: UIImage): PickedImage? {
        val scaled = resize(image, MAX_SIDE)
        val data: NSData = UIImageJPEGRepresentation(scaled, 0.8) ?: return null
        return PickedImage(data.base64EncodedStringWithOptions(0u))
    }

    private fun resize(image: UIImage, maxSide: Double): UIImage {
        val w = image.size.useContents { width }
        val h = image.size.useContents { height }
        val longest = maxOf(w, h)
        if (longest <= maxSide) return image
        val k = maxSide / longest
        val size = CGSizeMake(w * k, h * k)
        UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, w * k, h * k))
        val out = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return out ?: image
    }

    private companion object {
        const val MAX_SIDE = 1600.0
    }
}

/** Оба протокола обязательны: UIImagePickerController требует и навигационный. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class PickerDelegate(
    private val onDone: (UIImage?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true) { onDone(image) }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) { onDone(null) }
    }

    override fun navigationController(
        navigationController: UINavigationController,
        willShowViewController: UIViewController,
        animated: Boolean,
    ) = Unit
}
