package top.kagg886.pmf

import coil3.ComponentRegistry
import korlibs.ffi.usePointer
import korlibs.memory.bit
import korlibs.memory.startAddressOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.Path
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithBytesNoCopy
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIPasteboard
import top.kagg886.pmf.util.AnimatedSkiaImageDecoder
import top.kagg886.pmf.util.absolutePath

val scope = CoroutineScope(Dispatchers.Main)
actual fun openBrowser(link: String) {
    UIApplication.sharedApplication.openURL(
        url = NSURL.URLWithString(link)!!,
        options = mapOf<Any?, String>(),
        completionHandler = null,
    )
}

actual fun shareFile(file: Path, name: String, mime: String) {
    val url = NSURL.fileURLWithPath(file.absolutePath().toString())

    val controller = UIActivityViewController(
        activityItems = listOf(url),
        applicationActivities = null,
    )

    scope.launch {
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(
            controller,
            true,
            null,
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun copyImageToClipboard(bitmap: ByteArray) {
    val board = UIPasteboard.generalPasteboard

    val image = bitmap.usePinned {
        UIImage.imageWithData(
            data = NSData.dataWithBytes(
                bytes = it.startAddressOf,
                length = bitmap.size.toULong())
        )
    }

    board.image = image
}
actual fun ComponentRegistry.Builder.installGifDecoder() = add(AnimatedSkiaImageDecoder.Factory)
