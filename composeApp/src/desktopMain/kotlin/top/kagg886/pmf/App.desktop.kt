package top.kagg886.pmf

import coil3.ComponentRegistry
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.*
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import top.kagg886.pmf.backend.Platform
import top.kagg886.pmf.backend.currentPlatform
import top.kagg886.pmf.util.AnimatedSkiaImageDecoder

actual fun openBrowser(link: String) {
    Desktop.getDesktop().browse(URI.create(link))
}

actual fun shareFile(file: Path, name: String, mime: String) {
    Desktop.getDesktop().open(file.toFile())
}

actual suspend fun copyImageToClipboard(bitmap: ByteArray) {
    if (currentPlatform is Platform.Desktop.MacOS) {
        // 在mac上调命令行解决吧，不想写额外的动态库了
        copyImageAsImageToClipboardOnMacOS(bitmap)
        return
    }

    // fallback
    withContext(Dispatchers.IO) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(
            TransferableImage(bitmap),
            null,
        )
    }
}

private suspend fun copyImageAsImageToClipboardOnMacOS(imageBytes: ByteArray) {
    withContext(Dispatchers.IO) {
        val pngFile = File.createTempFile("clipboard_img_", ".png")
        pngFile.writeBytes(imageBytes)

        val tiffFile = File.createTempFile("clipboard_img_", ".tiff")
        if (tiffFile.exists()) tiffFile.delete()

        val sips = ProcessBuilder("sips", "-s", "format", "tiff", pngFile.absolutePath, "--out", tiffFile.absolutePath)
            .redirectErrorStream(true)
            .start()
        val sipsExit = sips.waitFor()
        if (sipsExit != 0) {
            // 清理并抛出或记录错误
            pngFile.delete()
            tiffFile.delete()
            throw RuntimeException("sips transferred failed，exit=$sipsExit")
        }

        val appleScriptCmd = listOf(
            "osascript",
            "-e",
            "set the clipboard to (read (POSIX file \"${tiffFile.absolutePath}\") as TIFF picture)",
        )
        val osascript = ProcessBuilder(appleScriptCmd)
            .redirectErrorStream(true)
            .start()
        val osExit = osascript.waitFor()
        if (osExit != 0) {
            pngFile.delete()
            tiffFile.delete()
            throw RuntimeException("osascript execute failed，exit=$osExit")
        }
        pngFile.delete()
        tiffFile.delete()
    }
}

private object DesktopClipBoardOwner : ClipboardOwner {
    override fun lostOwnership(clipboard: Clipboard?, contents: Transferable?) = Unit
}

private data class TransferableImage(private val image: ByteArray) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor in transferDataFlavors

    override fun getTransferData(flavor: DataFlavor?): Any {
        if (flavor == DataFlavor.imageFlavor) {
            return ImageIO.read(ByteArrayInputStream(image))
        }
        throw UnsupportedFlavorException(flavor)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransferableImage

        return image.contentEquals(other.image)
    }

    override fun hashCode(): Int = image.contentHashCode()
}

actual fun ComponentRegistry.Builder.installGifDecoder() = add(AnimatedSkiaImageDecoder.Factory)
