package top.kagg886.filepicker

import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.temporaryDirectory
import platform.Foundation.writeToURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import top.kagg886.pmf.util.sink
import top.kagg886.pmf.util.source

actual object FilePicker

@OptIn(ExperimentalForeignApi::class)
actual suspend fun FilePicker.openFileSaver(
    suggestedName: String,
    extension: String?,
    directory: Path?,
): Sink? {
    val target = withContext(Dispatchers.Main) {
        val viewController = UIApplication.sharedApplication.topMostViewController() ?: return@withContext null

        val name = suggestedName.replace("/", ":").ifBlank { "Untitled" }
        val normalizedExtension = extension?.trim()?.trimStart('.')?.takeIf { it.isNotEmpty() }
        val fileName = if (normalizedExtension == null || name.endsWith(".$normalizedExtension", ignoreCase = true)) {
            name
        } else {
            "$name.$normalizedExtension"
        }

        val fileManager = NSFileManager.defaultManager
        val fileComponents = fileManager.temporaryDirectory.pathComponents?.plus(fileName)
            ?: throw IllegalStateException("Failed to get temporary directory")
        val fileUrl =
            NSURL.fileURLWithPathComponents(fileComponents) ?: throw IllegalStateException("Failed to create file URL")

        // UIDocumentPicker export mode needs a real source file; the picked target is removed later so callers still get an empty save path.
        val emptyData = NSData()
        if (!emptyData.writeToURL(fileUrl, true)) {
            throw IllegalStateException("Failed to write to file URL")
        }

        suspendCancellableCoroutine<NSURL?> { continuation ->
            val delegate = DocumentPickerDelegate(
                onFilesPicked = onFilesPicked@{ urls ->
                    val file = urls.firstOrNull()
                    if (file == null) {
                        continuation.resumeIfActive(null)
                        return@onFilesPicked
                    }

                    file.startAccessingSecurityScopedResource()
                    NSFileManager.defaultManager.removeItemAtURL(file, null)
                    file.stopAccessingSecurityScopedResource()
                    continuation.resumeIfActive(file)
                },
                onPickerCancelled = {
                    continuation.resumeIfActive(null)
                },
            )

            val pickerController = UIDocumentPickerViewController(
                forExportingURLs = listOf(fileUrl),
            )
            directory?.let { pickerController.directoryURL = NSURL.fileURLWithPath(it.toString()) }
            pickerController.delegate = delegate

            viewController.presentViewController(
                pickerController,
                animated = true,
                completion = null,
            )
        }
    } ?: return null

    return SecurityScopedSink(target)
}

actual suspend fun FilePicker.openFilePicker(
    ext: List<String>?,
    title: String?,
    directory: Path?,
): Source? {
    val file = withContext(Dispatchers.Main) {
        val viewController = UIApplication.sharedApplication.topMostViewController() ?: return@withContext null

        val contentTypes =
            ext?.mapNotNull { UTType.typeWithFilenameExtension(it.trimStart('.')) }?.ifEmpty { null } ?: listOf(
                UTTypeItem,
            )

        suspendCancellableCoroutine<List<NSURL>?> { continuation ->
            val delegate = DocumentPickerDelegate(
                onFilesPicked = { urls -> continuation.resumeIfActive(urls) },
                onPickerCancelled = { continuation.resumeIfActive(null) },
            )

            val pickerController = UIDocumentPickerViewController(forOpeningContentTypes = contentTypes)
            title?.let { pickerController.title = it }
            directory?.let { pickerController.directoryURL = NSURL.fileURLWithPath(it.toString()) }
            pickerController.allowsMultipleSelection = false
            pickerController.delegate = delegate

            viewController.presentViewController(
                pickerController,
                animated = true,
                completion = null,
            )
        }
    }?.firstOrNull() ?: return null

    return SecurityScopedSource(file)
}

private class DocumentPickerDelegate(
    private val onFilesPicked: (List<NSURL>) -> Unit,
    private val onPickerCancelled: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        onFilesPicked(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPickerCancelled()
    }
}

private class SecurityScopedSource(
    private val url: NSURL,
) : Source {
    private val accessed = url.startAccessingSecurityScopedResource()
    private val delegate =
        url.path?.toPath()?.source() ?: throw IllegalStateException("Selected file has no local path")

    override fun read(sink: okio.Buffer, byteCount: Long): Long = delegate.read(sink, byteCount)

    override fun timeout() = delegate.timeout()

    override fun close() {
        try {
            delegate.close()
        } finally {
            if (accessed) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }
}

private class SecurityScopedSink(
    private val url: NSURL,
) : Sink {
    private val accessed = url.startAccessingSecurityScopedResource()
    private val delegate = url.path?.toPath()?.sink() ?: throw IllegalStateException("Selected file has no local path")

    override fun write(source: okio.Buffer, byteCount: Long) = delegate.write(source, byteCount)

    override fun flush() = delegate.flush()

    override fun timeout() = delegate.timeout()

    override fun close() {
        try {
            delegate.close()
        } finally {
            if (accessed) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }
}

private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) {
        resume(value)
    }
}

private fun UIApplication.topMostViewController(): UIViewController? {
    val keyWindow = connectedScenes.filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }?.keyWindow

    var topController = keyWindow?.rootViewController
    while (topController?.presentedViewController != null) {
        topController = topController.presentedViewController
    }

    return topController
}
