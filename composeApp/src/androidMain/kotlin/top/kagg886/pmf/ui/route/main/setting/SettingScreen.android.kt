package top.kagg886.pmf.ui.route.main.setting

import top.kagg886.filepicker.FilePicker
import top.kagg886.filepicker.openFolderPicker

actual suspend fun getDownloadRootPath(): String? = FilePicker.openFolderPicker()?.toString()
