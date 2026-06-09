package top.kagg886.filepicker

import java.io.File
import javax.swing.JFileChooser
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
    // rfd native folder picker can crash on macOS in JVM desktop runtime.
    // Use Swing directory chooser as a stable fallback.
    if (isMacOS) {
        val chooser = directory?.toString()?.let(::File)?.let(::JFileChooser) ?: JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        if (!title.isNullOrBlank()) {
            chooser.dialogTitle = title
        }
        val result = chooser.showOpenDialog(null)
        return@withContext if (result == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.absolutePath?.toPath()
        } else {
            null
        }
    }

    val ptr = nativeFilePicker.openDictionaryPicker(title, directory?.toString())
    withContext(Dispatchers.IO) {
        nativeFilePicker.awaitPointer(ptr)?.toPath()
    }
}
