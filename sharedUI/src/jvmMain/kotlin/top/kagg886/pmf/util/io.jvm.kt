package top.kagg886.pmf.util

import java.io.File
import okio.Path
import okio.Path.Companion.toOkioPath

actual val dataPath: Path by lazy {
    File(System.getProperty("user.home"))
        .resolve(".config")
        .resolve("pmf2")
        .toOkioPath()
}

actual val cachePath: Path by lazy {
    dataPath.resolve("cache")
}
