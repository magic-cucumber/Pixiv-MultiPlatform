package top.kagg886.pmf.util

import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory

public actual val dataPath: Path by lazy {
    // 需要在 Signing & Capabilities 里配置 App Group。
    NSFileManager.defaultManager
        .containerURLForSecurityApplicationGroupIdentifier("group.top.kagg886.pmf.iosApp.shared")!!
        .path!!
        .toPath()
}

public actual val cachePath: Path by lazy {
    NSTemporaryDirectory().toPath()
}
