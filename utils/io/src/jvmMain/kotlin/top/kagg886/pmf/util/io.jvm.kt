package top.kagg886.pmf.util

import java.io.File
import okio.Path
import okio.Path.Companion.toOkioPath

private val root by lazy {
    File(System.getProperty("user.home"))
        .resolve(".config")
        .resolve("pmf2")
        .toOkioPath()
}

public actual val dataPath: Path
    get() = root / "data"

public actual val cachePath: Path
    get() = root / "cache"
