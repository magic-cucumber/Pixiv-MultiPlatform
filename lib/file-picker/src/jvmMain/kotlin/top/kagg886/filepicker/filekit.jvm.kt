package top.kagg886.filepicker

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath
import top.kagg886.filepicker.internal.NativeFilePicker
import top.kagg886.filepicker.internal.initNativeLib
import top.kagg886.pmf.util.sink
import top.kagg886.pmf.util.source

actual object FilePicker {
    internal val nativeFilePicker by lazy {
        initNativeLib()
        NativeFilePicker()
    }
}

private val isMacOS by lazy { System.getProperty("os.name")?.startsWith("Mac") == true }
private const val MACOS_FILE_DIALOG_FOR_DIRECTORIES = "apple.awt.fileDialogForDirectories"

private inline fun <T> withMacOSDirectoryFileDialog(block: () -> T): T {
    val previous = System.getProperty(MACOS_FILE_DIALOG_FOR_DIRECTORIES)
    System.setProperty(MACOS_FILE_DIALOG_FOR_DIRECTORIES, "true")
    return try {
        block()
    } finally {
        if (previous == null) {
            System.clearProperty(MACOS_FILE_DIALOG_FOR_DIRECTORIES)
        } else {
            System.setProperty(MACOS_FILE_DIALOG_FOR_DIRECTORIES, previous)
        }
    }
}

actual suspend fun FilePicker.openFileSaver(
    suggestedName: String,
    extension: String?,
    directory: Path?,
) = withContext(Dispatchers.Main) {
    val ptr = nativeFilePicker.openFileSaver(suggestedName, extension, directory?.toString())
    withContext(Dispatchers.IO) {
        nativeFilePicker.awaitPointer(ptr)?.toPath()?.sink()
    }
}

actual suspend fun FilePicker.openFilePicker(
    ext: List<String>?,
    title: String?,
    directory: Path?,
) = withContext(Dispatchers.Main) {
    val ptr = nativeFilePicker.openFilePicker(ext?.toTypedArray(), title, directory?.toString())
    withContext(Dispatchers.IO) {
        nativeFilePicker.awaitPointer(ptr)?.toPath()?.source()
    }
}

suspend fun FilePicker.openFolderPicker(
    title: String? = null,
    directory: Path? = null,
) = withContext(Dispatchers.Main) {
    // rfd's macOS folder picker deadlocks/crashes when called from the JVM/AWT event thread.
    if (isMacOS) {
        return@withContext withMacOSDirectoryFileDialog {
            val dialog = FileDialog(null as Frame?, title ?: "", FileDialog.LOAD)
            dialog.isMultipleMode = false
            directory?.toString()?.let { dialog.directory = it }
            try {
                dialog.isVisible = true
                dialog.files?.firstOrNull()?.absolutePath?.toPath()
                    ?: dialog.file?.let { selected ->
                        val parent = dialog.directory
                        if (parent.isNullOrBlank()) {
                            selected.toPath()
                        } else {
                            File(parent, selected).absolutePath.toPath()
                        }
                    }
            } finally {
                dialog.dispose()
            }
        }
    }

    val ptr = nativeFilePicker.openDictionaryPicker(title, directory?.toString())
    withContext(Dispatchers.IO) {
        nativeFilePicker.awaitPointer(ptr)?.toPath()
    }
}
