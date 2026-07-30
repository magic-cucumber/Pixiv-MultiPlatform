package top.kagg886.pmf.ui.login

import top.kagg886.pmf.util.cachePath
import top.kagg886.pmf.util.dataPath
import top.kagg886.wvbridge.config.WebViewPlatformConfig

actual fun createWebViewPlatformConfig(): WebViewPlatformConfig = WebViewPlatformConfig(
    windowSetting = WebViewPlatformConfig.Windows(dataDir = dataPath.resolve("webview").toString()),
    linuxSetting = WebViewPlatformConfig.Linux(dataDir = dataPath.resolve("linux").toString(), cacheDir = cachePath.resolve("pmf2-cache").toString()),
)
