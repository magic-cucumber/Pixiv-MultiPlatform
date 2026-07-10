package top.kagg886.pmf.ui.route.login.v2

import top.kagg886.pmf.backend.cachePath
import top.kagg886.pmf.util.absolutePath
import top.kagg886.wvbridge.config.WebViewPlatformConfig

private val webviewPath by lazy {
    cachePath.resolve("webview").absolutePath()
}

actual fun defaultPlatformConfig(): WebViewPlatformConfig = WebViewPlatformConfig(
    windowSetting = WebViewPlatformConfig.Windows(
        dataDir = webviewPath.toString(),
    ),
    linuxSetting = WebViewPlatformConfig.Linux(
        dataDir = webviewPath.resolve("data").toString(),
        cacheDir = webviewPath.resolve("cache").toString(),
    ),
)
